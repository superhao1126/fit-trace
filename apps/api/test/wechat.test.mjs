import 'reflect-metadata';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { test } from 'node:test';
const require = createRequire(import.meta.url);
const { WechatService } = require('../dist/auth/wechat.service.js');
const originalFetch = globalThis.fetch;
function fixture({
  existing,
  binding,
  source,
  conflict = false,
  account = null,
  changed = true,
} = {}) {
  const calls = {
    creates: [],
    identities: [],
    consumed: 0,
    sessions: [],
    reassigned: [],
    revoked: [],
  };
  const tx = {
    $queryRaw: async () => [],
    wechatIdentity: {
      findUnique: async () => existing ?? null,
      create: async ({ data }) => calls.identities.push(data),
      update: async ({ data }) => calls.reassigned.push(data),
    },
    wechatBindingCode: {
      findUnique: async () => binding ?? null,
      updateMany: async () => ({ count: conflict ? 0 : ++calls.consumed }),
      create: async ({ data }) => calls.creates.push(data),
    },
    authSession: {
      findUnique: async () => source ?? null,
      updateMany: async ({ data }) => {
        calls.revoked.push(data);
        return { count: 1 };
      },
    },
    user: {
      findUnique: async () => account,
      updateMany: async () => ({ count: changed ? 1 : 0 }),
      create: async ({ data }) => {
        calls.creates.push(data);
        return { id: 'wx-user', tokenVersion: 0 };
      },
    },
  };
  const prisma = { ...tx, $transaction: async (fn) => fn(tx) };
  const service = new WechatService(
    prisma,
    { get: (name) => (name === 'WECHAT_APP_ID' ? 'test-app' : 'test-secret') },
    { findPublicById: async (id) => ({ id, email: null }) },
    {
      create: async (...args) => {
        calls.sessions.push(args);
        return { user: args[0] };
      },
    },
    { limit: async () => {} },
  );
  return { service, calls };
}
const reason = (code) => (error) => error.getResponse().code === code;
test('code2Session creates a pure WeChat user without email/password or email proof', async () => {
  globalThis.fetch = async (url) => {
    assert.equal(new URL(url).searchParams.get('js_code'), 'fresh-code');
    return { ok: true, json: async () => ({ openid: 'openid' }) };
  };
  try {
    const f = fixture();
    await f.service.login('fresh-code', 'ip');
    assert.deepEqual(f.calls.creates, [{ hasPassword: false }]);
    assert.equal(f.calls.identities[0].unionId, null);
    assert.equal(f.calls.identities[0].appId, 'test-app');
    assert.equal(f.calls.sessions[0][3], false);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
test('invalid code or missing configuration never issues a session', async () => {
  globalThis.fetch = async () => ({ ok: true, json: async () => ({ errcode: 40029 }) });
  try {
    const f = fixture();
    await assert.rejects(f.service.login('bad', 'ip'), reason('WECHAT_CODE_INVALID'));
    assert.equal(f.calls.sessions.length, 0);
    const disabled = new WechatService(
      {},
      { get: () => undefined },
      {},
      {},
      { limit: async () => {} },
    );
    await assert.rejects(disabled.login('x', 'ip'), reason('WECHAT_NOT_CONFIGURED'));
  } finally {
    globalThis.fetch = originalFetch;
  }
});
const binding = () => ({
  id: 'bind',
  userId: 'old',
  sessionId: 'sid',
  tokenVersion: 2,
  expiresAt: new Date(Date.now() + 60000),
  consumedAt: null,
});
const source = () => ({
  userId: 'old',
  revokedAt: null,
  emailVerifiedAt: new Date(),
  tokenVersion: 2,
  user: { id: 'old', email: 'old@example.com', tokenVersion: 2 },
});
test('binding requires both identities, consumes once, and never moves existing data', async () => {
  globalThis.fetch = async () => ({
    ok: true,
    json: async () => ({ openid: 'openid', unionid: 'union' }),
  });
  try {
    const f = fixture({ binding: binding(), source: source() });
    await f.service.login('code', 'ip', 'binding');
    assert.equal(f.calls.identities[0].userId, 'old');
    assert.equal(f.calls.consumed, 1);
    assert.equal(f.calls.creates.length, 0);
    assert.equal(f.calls.sessions[0][3], false);
    for (const options of [
      { binding: { ...binding(), consumedAt: new Date() }, source: source() },
      { binding: { ...binding(), expiresAt: new Date(0) }, source: source() },
      { binding: binding(), source: { ...source(), revokedAt: new Date() } },
      { binding: binding(), source: { ...source(), user: { ...source().user, tokenVersion: 3 } } },
      {
        binding: binding(),
        source: source(),
        existing: { userId: 'different', user: { id: 'different', tokenVersion: 0 } },
      },
      { binding: binding(), source: source(), conflict: true },
    ]) {
      const rejected = fixture(options);
      await assert.rejects(rejected.service.login('code', 'ip', 'binding'));
      assert.equal(rejected.calls.sessions.length, 0);
      assert.equal(rejected.calls.identities.length, 0);
    }
  } finally {
    globalThis.fetch = originalFetch;
  }
});
test('only a verified active email session can generate a digest-backed binding code', async () => {
  const f = fixture({ source: source() });
  const result = await f.service.bindingCode({ sub: 'old', sid: 'sid', ver: 2 });
  assert.match(result.code, /^[a-f0-9]{32}$/);
  assert.notEqual(f.calls.creates[0].codeHash, result.code);
  assert.ok(Date.parse(result.expiresAt) > Date.now());
  for (const invalid of [
    null,
    { ...source(), emailVerifiedAt: null },
    { ...source(), userId: 'someone-else' },
  ]) {
    const f = fixture({ source: invalid });
    await assert.rejects(
      f.service.bindingCode({ sub: 'old', sid: 'sid' }),
      reason('EMAIL_VERIFICATION_REQUIRED'),
    );
  }
});

test('pure WeChat users cannot enter email password management', async () => {
  const { AuthService } = require('../dist/auth/auth.service.js');
  const service = new AuthService(
    { findById: async () => ({ id: 'wx', email: null, passwordHash: null, tokenVersion: 0 }) },
    {},
    {},
    {},
  );
  await assert.rejects(
    service.sendPasswordCode({ sub: 'wx' }, 'ip'),
    reason('EMAIL_ACCOUNT_REQUIRED'),
  );
});

test('workout retry key is account scoped and returns the first persisted snapshot', async () => {
  const { WorkoutsService } = require('../dist/workouts/workouts.service.js');
  const rows = new Map();
  const service = new WorkoutsService({
    workoutRecord: {
      upsert: async ({ where, create }) => {
        const { userId, clientRequestId } = where.userId_clientRequestId;
        const key = userId + ':' + clientRequestId;
        if (!rows.has(key))
          rows.set(key, { ...create, id: key, createdAt: new Date(), updatedAt: new Date() });
        return rows.get(key);
      },
    },
  });
  const input = {
    clientRequestId: 'retry-key',
    name: 'First',
    type: 'STRENGTH',
    durationMinutes: 10,
  };
  const first = await service.create('alice', input),
    retry = await service.create('alice', { ...input, name: 'Changed' }),
    other = await service.create('bob', input);
  assert.equal(first.id, retry.id);
  assert.equal(retry.name, 'First');
  assert.notEqual(other.id, first.id);
  assert.equal(rows.size, 2);
});

test('HTTP WeChat login validates consent and rejects client identity; binding generation requires an authenticated session', async () => {
  const { Module, ValidationPipe } = require('@nestjs/common');
  const { NestFactory } = require('@nestjs/core');
  const { JwtService } = require('@nestjs/jwt');
  const { WechatController } = require('../dist/auth/wechat.controller.js');
  const { JwtAuthGuard } = require('../dist/common/guards/jwt-auth.guard.js');
  const { PrismaService } = require('../dist/prisma/prisma.service.js');
  const { HttpExceptionFilter } = require('../dist/common/filters/http-exception.filter.js');
  const { ResponseInterceptor } = require('../dist/common/interceptors/response.interceptor.js');
  const calls = [];
  const service = {
    login: async (...args) => {
      calls.push(args);
      return { user: { id: 'wx', email: null } };
    },
    bindingCode: async (payload) => ({ code: 'a'.repeat(32), userId: payload.sub }),
  };
  const jwt = new JwtService({ secret: 'test-only-secret' });
  class TestModule {}
  Module({
    controllers: [WechatController],
    providers: [
      { provide: WechatService, useValue: service },
      { provide: JwtService, useValue: jwt },
      {
        provide: PrismaService,
        useValue: {
          user: { findUnique: async () => ({ tokenVersion: 0 }) },
          authSession: {
            findUnique: async () => ({ userId: 'owner', tokenVersion: 0, revokedAt: null }),
          },
        },
      },
      JwtAuthGuard,
    ],
  })(TestModule);
  const app = await NestFactory.create(TestModule, { logger: false, abortOnError: false });
  app.useGlobalPipes(
    new ValidationPipe({ transform: true, whitelist: true, forbidNonWhitelisted: true }),
  );
  app.useGlobalFilters(new HttpExceptionFilter());
  app.useGlobalInterceptors(new ResponseInterceptor());
  try {
    await app.listen(0, '127.0.0.1');
    const base = await app.getUrl();
    const post = (path, body, token) =>
      globalThis.fetch(base + '/auth/wechat/' + path, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: 'Bearer ' + token } : {}),
        },
        body: JSON.stringify(body),
      });
    for (const bad of [
      { code: 'x', acceptedTerms: false },
      { code: 'x' },
      { code: 'x', acceptedTerms: 'true' },
      { code: 'x', acceptedTerms: true, userId: 'victim' },
      { code: 'x', acceptedTerms: true, openId: 'forged' },
      { code: 'x', acceptedTerms: true, bindingCode: '123' },
    ])
      assert.equal((await post('login', bad)).status, 400);
    assert.equal(calls.length, 0);
    const response = await post('login', { code: 'wx-code', acceptedTerms: true });
    assert.equal(response.status, 201);
    assert.equal((await response.json()).data.user.email, null);
    assert.equal(calls[0][0], 'wx-code');
    assert.equal((await post('binding-code', {})).status, 401);
    const token = await jwt.signAsync({ sub: 'owner', ver: 0, sid: 'sid' });
    const bindingResponse = await post('binding-code', {}, token);
    assert.equal(bindingResponse.status, 201);
    assert.equal((await bindingResponse.json()).data.userId, 'owner');
  } finally {
    await app.close();
  }
});

function emptyWechatAccount() {
  return {
    id: 'empty',
    tokenVersion: 0,
    updatedAt: new Date(),
    email: null,
    passwordHash: null,
    hasPassword: false,
    nickname: null,
    avatarUrl: null,
    gender: 'UNSPECIFIED',
    goalType: null,
    goalStartWeight: null,
    targetWeight: null,
    targetDate: null,
    goalStartedAt: null,
    trainingPlan: null,
    _count: {
      wechatIdentities: 1,
      deviceCredentials: 0,
      bodyRecords: 0,
      mealRecords: 0,
      mealFavorites: 0,
      workoutRecords: 0,
      progressPhotos: 0,
      trainingTemplates: 0,
    },
  };
}
test('fresh WeChat proof plus a valid binding code can reassign only an empty account and revokes its sessions', async () => {
  globalThis.fetch = async () => ({ ok: true, json: async () => ({ openid: 'openid' }) });
  try {
    const f = fixture({
      existing: { id: 'identity', userId: 'empty', user: { id: 'empty', tokenVersion: 0 } },
      binding: binding(),
      source: source(),
      account: emptyWechatAccount(),
    });
    const result = await f.service.login('fresh-code', 'ip', 'binding');
    assert.equal(result.user.id, 'old');
    assert.deepEqual(f.calls.reassigned, [{ userId: 'old' }]);
    assert.equal(f.calls.revoked.length, 1);
    assert.equal(f.calls.consumed, 1);
    assert.equal(f.calls.creates.length, 0);
    assert.equal(f.calls.sessions[0][3], false);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
test('reassignment rejects data, profiles, another login identity, changed account, or invalid source proof', async () => {
  globalThis.fetch = async () => ({ ok: true, json: async () => ({ openid: 'openid' }) });
  try {
    const empty = emptyWechatAccount();
    for (const account of [
      { ...empty, email: 'different@example.com' },
      { ...empty, passwordHash: 'hash' },
      { ...empty, hasPassword: true },
      { ...empty, nickname: '自定义资料' },
      { ...empty, gender: 'MALE' },
      { ...empty, trainingPlan: { id: 'plan' } },
      ...[
        'bodyRecords',
        'mealRecords',
        'mealFavorites',
        'workoutRecords',
        'progressPhotos',
        'trainingTemplates',
        'deviceCredentials',
      ].map((key) => ({ ...empty, _count: { ...empty._count, [key]: 1 } })),
      { ...empty, _count: { ...empty._count, wechatIdentities: 2 } },
    ]) {
      const f = fixture({
        existing: { id: 'identity', userId: 'empty', user: { id: 'empty', tokenVersion: 0 } },
        binding: binding(),
        source: source(),
        account,
      });
      await assert.rejects(
        f.service.login('fresh-code', 'ip', 'binding'),
        reason('WECHAT_ALREADY_BOUND'),
      );
      assert.equal(f.calls.reassigned.length, 0);
      assert.equal(f.calls.revoked.length, 0);
      assert.equal(f.calls.consumed, 0);
    }
    const f = fixture({
      existing: { id: 'identity', userId: 'empty', user: { id: 'empty', tokenVersion: 0 } },
      binding: binding(),
      source: source(),
      account: empty,
      changed: false,
    });
    await assert.rejects(
      f.service.login('fresh-code', 'ip', 'binding'),
      reason('WECHAT_BINDING_CONFLICT'),
    );
    assert.equal(f.calls.reassigned.length, 0);
    const invalid = fixture({
      existing: { id: 'identity', userId: 'empty', user: { id: 'empty', tokenVersion: 0 } },
      binding: binding(),
      source: { ...source(), revokedAt: new Date() },
      account: empty,
    });
    await assert.rejects(invalid.service.login('fresh-code', 'ip', 'binding'));
    assert.equal(invalid.calls.reassigned.length, 0);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
