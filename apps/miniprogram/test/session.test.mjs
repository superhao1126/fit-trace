import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { runInNewContext } from 'node:vm';
import { test } from 'node:test';
import ts from 'typescript';
const require = createRequire(import.meta.url);
const source = ts.transpileModule(
  readFileSync(new URL('../src/session.ts', import.meta.url), 'utf8').replace(
    'import.meta.env.VITE_API_BASE_URL',
    "'https://api.example/api/v1'",
  ),
  { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } },
).outputText;
const auth = (token = 'access', expiry = 0) => ({
  accessToken: token,
  refreshToken: 'refresh',
  accessTokenExpiresAt: new Date(expiry).toISOString(),
  user: { id: 'alice' },
});
function setup(cached, send) {
  const storage = new Map([['fit-trace:miniprogram:session:v2', cached]]),
    calls = [];
  const exports = {};
  const uni = {
    getStorageSync: (k) => storage.get(k),
    setStorageSync: (k, v) => storage.set(k, v),
    removeStorageSync: (k) => storage.delete(k),
    request: async (options) => {
      calls.push(options);
      return send(options);
    },
    login: async () => ({ code: 'fresh-code' }),
  };
  runInNewContext(source, { exports, require, uni, Date, Error, Promise, Number });
  return { ...exports, calls, storage };
}
const ok = (data) => ({ statusCode: 200, data: { code: 'OK', data } });
test('cold restore serializes concurrent refresh and persists renewed credentials', async () => {
  let complete;
  const f = setup(
    auth(),
    () =>
      new Promise((resolve) => {
        complete = resolve;
      }),
  );
  const a = f.restore(),
    b = f.restore();
  assert.equal(f.calls.length, 1);
  complete(ok(auth('renewed', Date.now() + 60000)));
  await Promise.all([a, b]);
  assert.equal(f.session.value.accessToken, 'renewed');
  assert.equal(f.storage.get('fit-trace:miniprogram:session:v2').accessToken, 'renewed');
});
test('offline refresh preserves session; revoked refresh clears session', async () => {
  const offline = setup(auth(), async () => {
    throw new Error('offline');
  });
  await assert.rejects(offline.restore());
  assert.equal(offline.session.value.user.id, 'alice');
  const revoked = setup(auth(), async () => ({ statusCode: 401, data: { message: 'revoked' } }));
  await assert.rejects(revoked.restore());
  assert.equal(revoked.session.value, null);
});
test('401 renews once then replays request with fresh bearer token', async () => {
  const f = setup(auth('old', Date.now() + 60000), async (options) =>
    options.url.endsWith('/auth/refresh')
      ? ok(auth('new', Date.now() + 60000))
      : options.header.Authorization === 'Bearer old'
        ? { statusCode: 401, data: { message: 'expired' } }
        : ok('record'),
  );
  assert.equal(await f.request('/workouts'), 'record');
  assert.equal(f.calls.length, 3);
  assert.equal(f.calls[2].header.Authorization, 'Bearer new');
});
test('logout rejects an older refresh result and never restores the old account', async () => {
  let complete;
  const f = setup(auth(), (options) =>
    options.url.endsWith('/auth/logout')
      ? ok(null)
      : new Promise((resolve) => {
          complete = resolve;
        }),
  );
  const pending = f.restore();
  await f.logout();
  complete(ok(auth('stale', Date.now() + 60000)));
  await pending;
  assert.equal(f.session.value, null);
  assert.equal(f.storage.size, 0);
});
test('malformed persisted session fails closed', () => {
  for (const value of [null, 'broken', {}, { accessToken: 'x', user: { id: 'alice' } }])
    assert.equal(setup(value, () => ok(null)).session.value, null);
});

test('email login reuses the server account without wx.login or a binding code', async () => {
  const f = setup(null, (options) =>
    options.url.endsWith('/email-code')
      ? ok({ retryAfterSeconds: 60, expiresInSeconds: 600 })
      : ok(auth('email-session', Date.now() + 60000)),
  );
  await f.sendEmailCode(' Alice@Example.com ');
  assert.equal(f.calls[0].url, 'https://api.example/api/v1/auth/miniprogram/email-code');
  assert.deepEqual(JSON.parse(JSON.stringify(f.calls[0].data)), { email: 'alice@example.com' });
  await f.login(' Alice@Example.com ', '123456');
  assert.equal(f.calls[1].url, 'https://api.example/api/v1/auth/miniprogram/login');
  assert.deepEqual(JSON.parse(JSON.stringify(f.calls[1].data)), {
    email: 'alice@example.com',
    emailCode: '123456',
    acceptedTerms: true,
  });
  assert.equal(f.session.value.accessToken, 'email-session');
});
