import 'reflect-metadata';
import { Buffer } from 'node:buffer';
import assert from 'node:assert/strict';
import { generateKeyPairSync, sign } from 'node:crypto';
import { createRequire } from 'node:module';
import { test } from 'node:test';
const require = createRequire(import.meta.url);
const { DeviceCredentialService } = require('../dist/auth/device-credential.service.js');
const denied = (e) => e.getResponse?.().code === 'DEVICE_CREDENTIAL_INVALID';
function fixture() {
  const challenges = new Map(),
    credentials = new Map();
  const user = { id: 'owner', email: 'owner@example.com', tokenVersion: 0 };
  const current = {
    id: 'session',
    userId: user.id,
    tokenVersion: 0,
    revokedAt: null,
    emailVerifiedAt: new Date(),
  };
  let audience = 'test-audience';
  let tail = Promise.resolve(),
    issued = 0,
    verifiedCode = false;
  const matches = (row, where) =>
    Object.entries(where).every(([key, value]) =>
      value && typeof value === 'object' && !(value instanceof Date)
        ? !value.gt || row[key] > value.gt
        : row[key] === value,
    );
  const tx = {
    $queryRaw: async () => [{ tokenVersion: user.tokenVersion }],
    authSession: {
      findUnique: async () => current,
      update: async ({ data }) => Object.assign(current, data),
    },
    deviceAuthChallenge: {
      create: async ({ data }) => {
        const row = { consumedAt: null, ...data };
        challenges.set(data.id, row);
        return row;
      },
      findUnique: async ({ where }) => challenges.get(where.id),
      updateMany: async ({ where, data }) => {
        let count = 0;
        for (const row of challenges.values())
          if (matches(row, where)) {
            Object.assign(row, data);
            count++;
          }
        return { count };
      },
    },
    deviceCredential: {
      create: async ({ data }) => {
        const row = { createdAt: new Date(), revokedAt: null, lastUsedAt: null, ...data };
        credentials.set(data.id, row);
        return row;
      },
      findUnique: async ({ where }) => {
        const row = credentials.get(where.id);
        return row ? { ...row, user } : null;
      },
      update: async ({ where, data }) => Object.assign(credentials.get(where.id), data),
      updateMany: async ({ where, data }) => {
        let count = 0;
        for (const row of credentials.values())
          if (matches(row, where)) {
            Object.assign(row, data);
            count++;
          }
        return { count };
      },
      findMany: async ({ where }) => [...credentials.values()].filter((row) => matches(row, where)),
    },
  };
  const prisma = {
    ...tx,
    $transaction: (action) => {
      const pending = tail.then(() => action(tx));
      tail = pending.catch(() => {});
      return pending;
    },
  };
  const service = new DeviceCredentialService(
    prisma,
    {
      create: async () => ({
        accessToken: `new-${++issued}`,
        refreshToken: `fresh-${issued}`,
        user,
      }),
    },
    { findPublicById: async () => user },
    { limit: async () => {} },
    {
      withCode: async (email, purpose, code, action) => {
        assert.equal(email, user.email);
        assert.equal(purpose, 'LOGIN');
        assert.equal(code, '123456');
        verifiedCode = true;
        return prisma.$transaction(action);
      },
    },
    { get: () => audience },
  );
  const pair = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  const publicKey = pair.publicKey.export({ type: 'spki', format: 'der' }).toString('base64');
  const payload = { sub: user.id, email: user.email, ver: 0, sid: 'session' };
  const proof = (challenge) => ({
    challengeId: challenge.challengeId,
    signature: sign('sha256', Buffer.from(challenge.payload), pair.privateKey).toString(
      'base64url',
    ),
  });
  const options = (extra) =>
    service.registrationOptions(payload, {
      publicKey,
      scope: 'android-prod',
      deviceName: 'Test Android',
      ...extra,
    });
  const enroll = async () => {
    const challenge = await options();
    const credential = await service.registrationVerify(payload, proof(challenge));
    return { challenge, credential };
  };
  const login = (id) => service.loginOptions({ credentialId: id, scope: 'android-prod' }, 'ip');
  return {
    service,
    setAudience: (value) => {
      audience = value;
    },
    user,
    current,
    pair,
    payload,
    proof,
    options,
    enroll,
    login,
    challenges,
    credentials,
    issued: () => issued,
    verifiedCode: () => verifiedCode,
  };
}

test('email proof is required for stale/password sessions and client timestamps cannot grant it', async () => {
  const f = fixture();
  f.current.emailVerifiedAt = null;
  await assert.rejects(f.options(), (e) => e.getResponse().code === 'EMAIL_VERIFICATION_REQUIRED');
  await f.options({ emailCode: '123456' });
  assert.equal(f.verifiedCode(), true);
});
test('registration binds exact key, session, account, purpose and scope; retry is idempotent', async () => {
  const f = fixture();
  const { challenge, credential } = await f.enroll();
  assert.equal(
    (await f.service.registrationVerify(f.payload, f.proof(challenge))).id,
    credential.id,
  );
  assert.equal(f.credentials.size, 1);
  await assert.rejects(
    f.service.registrationVerify({ ...f.payload, sub: 'other' }, f.proof(challenge)),
    denied,
  );
  assert.equal(JSON.parse(challenge.payload).audience, 'test-audience');
  assert.equal(JSON.parse(challenge.payload).scope, 'android-prod');
});
test('ordinary logout retains device credential and creates a completely new session', async () => {
  const f = fixture();
  const { credential } = await f.enroll();
  f.current.revokedAt = new Date();
  const challenge = await f.login(credential.id);
  const session = await f.service.loginVerify(f.proof(challenge), 'ip');
  assert.equal(session.refreshToken, 'fresh-1');
  assert.equal(f.credentials.get(credential.id).revokedAt, null);
  await assert.rejects(f.service.loginVerify(f.proof(challenge), 'ip'), denied);
});
test('two concurrent login verifications consume a challenge exactly once', async () => {
  const f = fixture();
  const { credential } = await f.enroll();
  const challenge = await f.login(credential.id);
  const results = await Promise.allSettled([
    f.service.loginVerify(f.proof(challenge), 'ip'),
    f.service.loginVerify(f.proof(challenge), 'ip'),
  ]);
  assert.equal(results.filter((x) => x.status === 'fulfilled').length, 1);
  assert.equal(f.issued(), 1);
});
test('wrong signature, expired challenge and registration-as-login fail without issuing sessions', async () => {
  const f = fixture();
  const { credential, challenge: registration } = await f.enroll();
  await assert.rejects(f.service.loginVerify(f.proof(registration), 'ip'), denied);
  const challenge = await f.login(credential.id);
  const wrong = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  await assert.rejects(
    f.service.loginVerify(
      {
        challengeId: challenge.challengeId,
        signature: sign('sha256', Buffer.from(challenge.payload), wrong.privateKey).toString(
          'base64url',
        ),
      },
      'ip',
    ),
    denied,
  );
  f.challenges.get(challenge.challengeId).expiresAt = new Date(0);
  await assert.rejects(f.service.loginVerify(f.proof(challenge), 'ip'), denied);
  assert.equal(f.issued(), 0);
});
test('revocation and password security version changes reject even previously issued challenges', async () => {
  const f = fixture();
  const { credential } = await f.enroll();
  const challenge = await f.login(credential.id);
  await f.service.revoke(f.payload, credential.id);
  await assert.rejects(f.service.loginVerify(f.proof(challenge), 'ip'), denied);
  const g = fixture();
  const registered = await g.enroll();
  const other = await g.login(registered.credential.id);
  g.user.tokenVersion++;
  await assert.rejects(g.service.loginVerify(g.proof(other), 'ip'), denied);
});
test('cross environment and unknown credential options return indistinguishable non-login challenges', async () => {
  const f = fixture();
  const { credential } = await f.enroll();
  const challenge = await f.service.loginOptions(
    { credentialId: credential.id, scope: 'android-dev' },
    'ip',
  );
  await assert.rejects(f.service.loginVerify(f.proof(challenge), 'ip'), denied);
  const unknown = await f.login('00000000-0000-4000-8000-000000000001');
  assert.deepEqual(Object.keys(unknown), Object.keys(challenge));
});
test('another account cannot revoke a binding or finish registration', async () => {
  const f = fixture();
  const { credential, challenge } = await f.enroll();
  await assert.rejects(f.service.revoke({ ...f.payload, sub: 'other' }, credential.id), denied);
  await assert.rejects(
    f.service.registrationVerify({ ...f.payload, sid: 'other-session' }, f.proof(challenge)),
    denied,
  );
  assert.equal(f.credentials.get(credential.id).revokedAt, null);
});
test('only P-256 SPKI public keys may register', async () => {
  const f = fixture();
  const pair = generateKeyPairSync('rsa', { modulusLength: 2048 });
  await assert.rejects(
    f.options({
      publicKey: pair.publicKey.export({ type: 'spki', format: 'der' }).toString('base64'),
    }),
    denied,
  );
});

test('closing a pending registration prevents an in-flight verify from activating it later', async () => {
  const f = fixture();
  const challenge = await f.options();
  await f.service.revoke(f.payload, challenge.credentialId);
  await assert.rejects(f.service.registrationVerify(f.payload, f.proof(challenge)), denied);
  assert.equal(f.credentials.size, 0);
});

test('shared database does not allow consuming a challenge in another deployment audience', async () => {
  const f = fixture();
  const { credential } = await f.enroll();
  const challenge = await f.login(credential.id);
  f.setAudience('another-deployment');
  await assert.rejects(f.service.loginVerify(f.proof(challenge), 'ip'), denied);
  assert.equal(f.issued(), 0);
});

test('successful email proof remains recent after cancelling the system registration prompt', async () => {
  const f = fixture();
  f.current.emailVerifiedAt = null;
  await f.options({ emailCode: '123456' });
  const retry = await f.options();
  assert.ok(retry.challengeId);
  assert.ok(f.current.emailVerifiedAt instanceof Date);
});
