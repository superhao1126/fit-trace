/* global structuredClone */
import 'reflect-metadata';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { test } from 'node:test';
const require = createRequire(import.meta.url);
const { AuthSecurityService } = require('../dist/auth/auth-security.service.js');
const { EmailVerificationService } = require('../dist/auth/email-verification.service.js');
const { HttpExceptionFilter } = require('../dist/common/filters/http-exception.filter.js');

function fixture() {
  let state = { limits: new Map(), code: null };
  let queue = Promise.resolve();
  let failWrite = false;
  const prisma = {
    $queryRaw: async (_, key, expiresAt, now) => {
      const current = state.limits.get(key);
      const row =
        !current || current.expiresAt <= now
          ? { count: 1, expiresAt }
          : { ...current, count: current.count + 1 };
      state.limits.set(key, row);
      return [{ ...row }];
    },
    authRateLimit: {
      updateMany: async ({ where }) => {
        const row = state.limits.get(where.key);
        if (row && row.count > 0 && +row.expiresAt === +where.expiresAt) {
          row.count--;
          return { count: 1 };
        }
        return { count: 0 };
      },
    },
    user: { findUnique: async () => null },
    emailVerification: {
      upsert: async ({ create }) => {
        if (failWrite) throw new Error('database write failed');
        state.code = { id: 'code', ...create };
        return { ...state.code };
      },
      deleteMany: async ({ where }) => {
        if (state.code?.codeHash === where.codeHash) state.code = null;
      },
    },
    $transaction: (action) => {
      const result = queue.then(async () => {
        const previous = structuredClone(state);
        try {
          return await action(prisma);
        } catch (error) {
          state = previous;
          throw error;
        }
      });
      queue = result.catch(() => undefined);
      return result;
    },
  };
  const security = new AuthSecurityService(prisma, { get: () => 'test-only-secret' });
  security.cleanup = async () => {};
  const sent = [];
  const mail = { assertConfigured() {}, sendCode: async (...args) => sent.push(args) };
  const service = new EmailVerificationService(prisma, security, mail, { verify: async () => {} });
  return {
    prisma,
    security,
    service,
    mail,
    sent,
    state: () => state,
    failWrite: (value) => {
      failWrite = value;
    },
    send: (email = 'a@example.invalid', ip = 'test-ip') => service.send(email, 'LOGIN', ip),
  };
}

test('database failure rolls back every sending quota and permits immediate retry', async () => {
  const f = fixture();
  f.failWrite(true);
  await assert.rejects(f.send(), /database write failed/);
  assert.equal(f.state().limits.size, 0);
  f.failWrite(false);
  await f.send();
  assert.equal(f.sent.length, 1);
  assert.deepEqual(
    [...f.state().limits.values()].map((row) => row.count),
    [1, 1, 1],
  );
});

test('SMTP failure refunds quota, removes failed code and permits immediate retry', async () => {
  const f = fixture();
  f.mail.sendCode = async () => {
    throw new Error('SMTP failed');
  };
  await assert.rejects(f.send(), /SMTP failed/);
  assert.equal(f.state().code, null);
  assert.deepEqual(
    [...f.state().limits.values()].map((row) => row.count),
    [0, 0, 0],
  );
  f.mail.sendCode = async (...args) => f.sent.push(args);
  await f.send();
  assert.equal(f.sent.length, 1);
});

test('concurrent sends accept one request; rejected retries do not consume quota', async () => {
  const f = fixture();
  const results = await Promise.allSettled([f.send(), f.send(), f.send()]);
  assert.equal(results.filter((row) => row.status === 'fulfilled').length, 1);
  for (const { reason } of results.filter((row) => row.status === 'rejected')) {
    assert.equal(reason.getStatus(), 429);
    assert.match(reason.getResponse().message, /60 秒/);
    assert.ok(reason.getResponse().retryAfterSeconds > 0);
    assert.ok(reason.getResponse().retryAfterSeconds <= 60);
  }
  assert.equal(f.sent.length, 1);
  assert.deepEqual(
    [...f.state().limits.values()].map((row) => row.count),
    [1, 1, 1],
  );
});

test('email hourly quota stays at five; blocked requests do not consume network quota', async () => {
  const f = fixture();
  for (let i = 0; i < 5; i++) {
    await f.send();
    f.state().limits.get(f.security.key('email-code-minute', 'a@example.invalid:LOGIN')).expiresAt =
      new Date(0);
  }
  await assert.rejects(f.send(), (error) => {
    assert.match(error.getResponse().message, /该邮箱/);
    return error.getStatus() === 429;
  });
  assert.equal(
    f.state().limits.get(f.security.key('email-code-hour', 'a@example.invalid')).count,
    5,
  );
  assert.equal(f.state().limits.get(f.security.key('email-code-ip', 'test-ip')).count, 5);
});

test('network quota stays at ten across emails', async () => {
  const f = fixture();
  for (let i = 0; i < 10; i++) await f.send(`user${i}@example.invalid`);
  await assert.rejects(f.send('blocked@example.invalid'), (error) => {
    assert.match(error.getResponse().message, /当前网络/);
    return error.getStatus() === 429;
  });
  assert.equal(f.state().limits.get(f.security.key('email-code-ip', 'test-ip')).count, 10);
  assert.equal(
    f.state().limits.has(f.security.key('email-code-hour', 'blocked@example.invalid')),
    false,
  );
});

test('refund never decrements a newer window', async () => {
  const f = fixture();
  const reservations = await f.prisma.$transaction((tx) =>
    f.security.reserveEmailCode('a@example.invalid', 'LOGIN', 'test-ip', tx),
  );
  const row = f.state().limits.get(reservations[0].key);
  row.expiresAt = new Date(+row.expiresAt + 60000);
  await f.prisma.$transaction((tx) => f.security.releaseEmailCode(reservations, tx));
  assert.equal(row.count, 1);
  assert.deepEqual(
    [...f.state().limits.values()].map((row) => row.count),
    [1, 0, 0],
  );
});

test('429 exposes remaining cooldown without changing the shared response envelope', async () => {
  const f = fixture();
  await f.send();
  let status;
  let body;
  const headers = {};
  const response = {
    setHeader: (key, value) => {
      headers[key] = value;
    },
    status: (value) => {
      status = value;
      return response;
    },
    json: (value) => {
      body = value;
    },
  };
  try {
    await f.send();
    assert.fail('expected rate limit');
  } catch (error) {
    new HttpExceptionFilter().catch(error, {
      switchToHttp: () => ({ getResponse: () => response }),
    });
  }
  assert.equal(status, 429);
  assert.ok(Number(headers['Retry-After']) > 0 && Number(headers['Retry-After']) <= 60);
  assert.equal(body.code, 'AUTH_RATE_LIMITED');
  assert.deepEqual(Object.keys(body).sort(), ['code', 'data', 'message', 'meta']);
});

test('miniprogram login skips captcha but shares server email quotas', async () => {
  const f = fixture();
  f.service.captcha = {
    verify: async () => assert.fail('Mini program must not call Aliyun captcha'),
  };
  await f.service.sendMiniprogramLoginCode('mini@example.invalid', 'mini-ip');
  assert.equal(f.sent.length, 1);
  assert.equal(f.sent[0][1], 'LOGIN');
  await assert.rejects(f.service.sendMiniprogramLoginCode('mini@example.invalid', 'mini-ip'));
  assert.equal(f.sent.length, 1);
});
