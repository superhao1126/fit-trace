import assert from 'node:assert/strict';
import { test } from 'node:test';
import axios, { AxiosError } from 'axios';
import { AuthSessionManager } from '../src/utils/auth-session.ts';
import { installSessionInterceptor } from '../src/api/session-interceptor.ts';
import { installResponseHandlers } from '../src/api/response-handler.ts';

const result = (token = 'access', expiresAt = Date.now() + 900_000, id = 'owner') => ({
  accessToken: token,
  accessTokenExpiresAt: new Date(expiresAt).toISOString(),
  refreshToken: `refresh-${id}`,
  user: { id, email: `${id}@example.com` },
});
const unauthorized = (config) =>
  new AxiosError('401', undefined, config, undefined, {
    config,
    status: 401,
    data: { code: 'UNAUTHORIZED' },
    headers: {},
    statusText: '',
  });
function fixture(transport = {}) {
  const data = new Map();
  const storage = {
    getItem: (key) => data.get(key) ?? null,
    setItem: (key, value) => data.set(key, value),
    removeItem: (key) => data.delete(key),
  };
  const defaults = {
    refresh: async () => result('renewed'),
    upgrade: async () => result('upgraded'),
    revoke: async () => {},
  };
  const manager = new AuthSessionManager(storage, { ...defaults, ...transport });
  return { storage, manager, data, transport: { ...defaults, ...transport } };
}

test('reopening restores the last account; expired access renews and no password is stored', async () => {
  const f = fixture();
  f.manager.save(result('old', 0));
  const reopened = new AuthSessionManager(f.storage, f.transport);
  assert.equal(reopened.snapshot.user.id, 'owner');
  await reopened.restore();
  assert.equal(reopened.snapshot.accessToken, 'renewed');
  assert.equal(JSON.stringify([...f.data]).includes('password'), false);
});

test('profile edits survive reopening without changing session credentials or another account', () => {
  const f = fixture();
  const original = result();
  f.manager.save(original);
  const user = {
    ...original.user,
    nickname: '新昵称',
    gender: 'FEMALE',
    avatarUrl: 'http://localhost/uploads/avatar.jpg',
  };
  f.manager.updateUser(user);
  const reopened = new AuthSessionManager(f.storage, f.transport);
  assert.deepEqual(reopened.snapshot.user, user);
  assert.equal(reopened.snapshot.accessToken, original.accessToken);
  assert.equal(reopened.snapshot.refreshToken, original.refreshToken);
  f.manager.updateUser({ ...user, id: 'other', nickname: '其他账号' });
  assert.deepEqual(f.manager.snapshot.user, user);
});

test('password management routes carry and renew the current access token', async () => {
  const f = fixture();
  f.manager.save(result('expired', 0));
  const client = axios.create({
    adapter: async (config) => {
      assert.equal(config.headers.Authorization, 'Bearer renewed');
      return { config, status: 200, headers: {}, data: { code: 'OK', data: null }, statusText: '' };
    },
  });
  installSessionInterceptor(client, f.manager);
  await client.patch('/auth/password', {
    currentPassword: 'old-password',
    password: 'new-password',
  });
  await client.post('/auth/password/email-code', {});
  await client.post('/auth/password/verify-code', { emailCode: '123456' });
  await client.patch('/auth/password/email', { resetToken: 'grant', password: 'new-password' });
  await client.patch('/auth/password/session', {
    currentPassword: 'old-password',
    password: 'new-password',
  });
});

test('password replacement credentials persist and an earlier renewal cannot restore old credentials', async () => {
  let complete;
  const f = fixture({
    refresh: () =>
      new Promise((resolve) => {
        complete = resolve;
      }),
  });
  f.manager.save(result('old', 0));
  const pending = f.manager.accessToken();
  const updated = { ...result('new-password-session'), refreshToken: 'new-device-credential' };
  updated.user.hasPassword = true;
  f.manager.save(updated);
  complete(result('old-renewed'));
  assert.equal(await pending, null);
  const reopened = new AuthSessionManager(f.storage, f.transport);
  assert.equal(reopened.snapshot.accessToken, updated.accessToken);
  assert.equal(reopened.snapshot.refreshToken, updated.refreshToken);
  assert.equal(reopened.snapshot.user.hasPassword, true);
});

test('temporary network failure retains the account; revoked credential clears it', async () => {
  const offline = fixture({
    refresh: async () => {
      throw new Error('offline');
    },
  });
  offline.manager.save(result('old', 0));
  await offline.manager.restore();
  assert.equal(offline.manager.snapshot.user.id, 'owner');
  const invalid = fixture({
    refresh: async () => {
      throw unauthorized();
    },
  });
  invalid.manager.save(result('old', 0));
  await invalid.manager.restore();
  assert.equal(invalid.manager.snapshot, null);
  assert.equal(invalid.data.has('fit-trace:session'), false);
});

test('parallel requests renew once and continue with the new bearer', async () => {
  let refreshes = 0;
  const f = fixture({
    refresh: async () => {
      refreshes++;
      return result('renewed');
    },
  });
  f.manager.save(result('expired', 0));
  const headers = [];
  const client = axios.create({
    adapter: async (config) => {
      headers.push(config.headers.Authorization);
      return { config, status: 200, headers: {}, data: { code: 'OK' }, statusText: '' };
    },
  });
  installSessionInterceptor(client, f.manager);
  await Promise.all(Array.from({ length: 8 }, () => client.get('/dashboard')));
  assert.equal(refreshes, 1);
  assert.deepEqual(new Set(headers), new Set(['Bearer renewed']));
});

test('401 retries once silently and concurrent late 401 uses the renewed token', async () => {
  let refreshes = 0;
  const f = fixture({
    refresh: async () => {
      refreshes++;
      return result('renewed');
    },
  });
  f.manager.save(result('old'));
  const notices = [];
  const client = axios.create({
    adapter: async (config) => {
      if (config.headers.Authorization === 'Bearer old') throw unauthorized(config);
      return { config, status: 200, headers: {}, data: { code: 'OK' }, statusText: '' };
    },
  });
  installSessionInterceptor(client, f.manager);
  installResponseHandlers(client, {
    notify: (e) => notices.push(e),
    onUnauthorized: () => assert.fail('unexpected logout'),
  });
  await Promise.all(Array.from({ length: 5 }, () => client.get('/dashboard')));
  assert.equal(refreshes, 1);
  assert.equal(notices.length, 0);
});

test('login failures bypass renewal and do not erase an existing account', async () => {
  const f = fixture({ refresh: async () => assert.fail('unexpected refresh') });
  f.manager.save(result());
  const client = axios.create({
    adapter: async (config) => {
      throw unauthorized(config);
    },
  });
  installSessionInterceptor(client, f.manager);
  await assert.rejects(
    client.post('/auth/login', { email: 'another@example.com', password: 'wrong' }),
  );
  assert.equal(f.manager.snapshot.user.id, 'owner');
});

test('repeated 401 stops after one retry and reports the error only once', async () => {
  let attempts = 0;
  let notices = 0;
  let redirects = 0;
  const f = fixture();
  f.manager.save(result('old'));
  const client = axios.create({
    adapter: async (config) => {
      attempts++;
      throw unauthorized(config);
    },
  });
  installSessionInterceptor(client, f.manager);
  installResponseHandlers(client, { notify: () => notices++, onUnauthorized: () => redirects++ });
  await assert.rejects(client.get('/dashboard'));
  assert.equal(attempts, 2);
  assert.equal(notices, 1);
  assert.equal(redirects, 1);
  assert.equal(f.manager.snapshot, null);
});

test('network error during renewal rejects the request without logging out', async () => {
  const f = fixture({
    refresh: async () => {
      throw new AxiosError('offline', AxiosError.ERR_NETWORK);
    },
  });
  f.manager.save(result('old', 0));
  let notices = 0;
  let redirects = 0;
  const client = axios.create({ adapter: async () => assert.fail('request should await renewal') });
  installSessionInterceptor(client, f.manager);
  installResponseHandlers(client, { notify: () => notices++, onUnauthorized: () => redirects++ });
  await assert.rejects(client.get('/dashboard'), /offline/);
  assert.equal(notices, 1);
  assert.equal(redirects, 0);
  assert.equal(f.manager.snapshot.user.id, 'owner');
});

test('logout and switching account during renewal cannot resurrect the previous account', async () => {
  for (const switchAccount of [false, true]) {
    let complete;
    const f = fixture({
      refresh: () =>
        new Promise((resolve) => {
          complete = resolve;
        }),
    });
    f.manager.save(result('old', 0));
    const pending = f.manager.accessToken();
    f.manager.logout();
    if (switchAccount) f.manager.save(result('new-account', Date.now() + 900_000, 'other'));
    complete(result('old-account-renewed'));
    assert.equal(await pending, null);
    assert.equal(f.manager.snapshot?.user.id ?? null, switchAccount ? 'other' : null);
  }
});

test('offline logout clears local login and retries revocation after reconnect/reopen', async () => {
  let online = false;
  let revoked;
  const f = fixture({
    revoke: async (token) => {
      if (!online) throw new Error('offline');
      revoked = token;
    },
  });
  f.manager.save(result());
  f.manager.logout();
  assert.equal(f.manager.snapshot, null);
  assert.equal(f.data.has('fit-trace:session'), false);
  await f.manager.flushLogouts();
  const reopened = new AuthSessionManager(f.storage, f.transport);
  assert.equal(reopened.snapshot, null);
  online = true;
  await reopened.flushLogouts();
  assert.equal(revoked, 'refresh-owner');
  assert.equal(f.data.has('fit-trace:pending-logouts'), false);
});

test('valid legacy token upgrades silently and another tab logout invalidates pending renewal', async () => {
  const f = fixture();
  f.storage.setItem('fit-trace:access-token', 'legacy');
  const manager = new AuthSessionManager(f.storage, f.transport);
  await manager.restore();
  assert.equal(manager.snapshot.accessToken, 'upgraded');
  assert.equal(f.data.has('fit-trace:access-token'), false);
  f.storage.removeItem('fit-trace:session');
  manager.syncFromStorage();
  assert.equal(manager.snapshot, null);
});

test('WeChat binding requests carry and renew the logged-in account token', async () => {
  let refreshes = 0;
  const f = fixture({
    refresh: async () => {
      refreshes++;
      return result('binding-renewed');
    },
  });
  f.manager.save(result('expired', 0));
  const client = axios.create({
    adapter: async (config) => {
      assert.equal(config.headers.Authorization, 'Bearer binding-renewed');
      return {
        config,
        status: 200,
        headers: {},
        data: { code: 'OK', data: { code: 'binding-code' } },
        statusText: '',
      };
    },
  });
  installSessionInterceptor(client, f.manager);
  const response = await client.post('/auth/wechat/binding-code');
  assert.equal(response.data.data.code, 'binding-code');
  await client.post('/auth/wechat/binding-code?source=settings');
  assert.equal(refreshes, 1);
});

test('unknown protected auth routes keep authentication, while WeChat login stays public', async () => {
  const f = fixture();
  f.manager.save(result('current'));
  const seen = [];
  const client = axios.create({
    adapter: async (config) => {
      seen.push(config.headers.Authorization);
      return { config, status: 200, headers: {}, data: { code: 'OK' }, statusText: '' };
    },
  });
  installSessionInterceptor(client, f.manager);
  await client.post('/auth/wechat/login', { code: 'new-wechat-code' });
  await client.post('/auth/session');
  await client.post('/auth/future-protected-action');
  assert.deepEqual(seen, [undefined, 'Bearer current', 'Bearer current']);
});
