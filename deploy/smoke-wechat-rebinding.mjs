import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { createRequire } from 'node:module';

const require = createRequire(new URL('../apps/api/package.json', import.meta.url));
const { config } = createRequire(require.resolve('@nestjs/config'))('dotenv');
config({ path: new URL('../.env', import.meta.url), quiet: true });
const { PrismaClient } = require('@prisma/client');
const { JwtService } = require('@nestjs/jwt');
const { UsersService } = require('./dist/users/users.service.js');
const { AuthSessionService } = require('./dist/auth/auth-session.service.js');
const { WechatService } = require('./dist/auth/wechat.service.js');
const prisma = new PrismaClient();
const jwt = new JwtService({ secret: process.env.JWT_SECRET, signOptions: { expiresIn: 180 } });
const users = new UsersService(prisma);
const sessions = new AuthSessionService(prisma, users, jwt);
const appId = 'deployment-test-' + randomBytes(12).toString('hex');
const service = new WechatService(
  prisma,
  { get: (key) => (key === 'WECHAT_APP_ID' ? appId : 'test-secret') },
  users,
  sessions,
  { limit: async () => {} },
);
const ids = [];
const nativeFetch = globalThis.fetch;
async function user(data = {}) {
  const row = await prisma.user.create({ data: { hasPassword: false, ...data } });
  ids.push(row.id);
  return row;
}
async function prove(openId, bindingCode) {
  // Only synthetic identities for this deployment test namespace; never a real WeChat user.
  globalThis.fetch = async (url) => {
    assert.ok(String(url).startsWith('https://api.weixin.qq.com/sns/jscode2session?'));
    return { ok: true, json: async () => ({ openid: openId }) };
  };
  try {
    return await service.login('synthetic-deployment-code', 'deployment-test', bindingCode);
  } finally {
    globalThis.fetch = nativeFetch;
  }
}
try {
  const target = await user({
    email: `binding-${randomBytes(12).toString('hex')}@example.invalid`,
  });
  const sourceSession = await sessions.create(
    await users.findPublicById(target.id),
    0,
    prisma,
    true,
  );
  const payload = jwt.decode(sourceSession.accessToken);
  const empty = await user();
  const oldSession = await sessions.create(await users.findPublicById(empty.id), 0);
  const oldSid = jwt.decode(oldSession.accessToken).sid;
  const identity = await prisma.wechatIdentity.create({
    data: { appId, openId: 'empty', userId: empty.id },
  });
  const code = await service.bindingCode(payload);
  const linked = await prove('empty', code.code);
  assert.equal(linked.user.id, target.id);
  assert.equal(
    (await prisma.wechatIdentity.findUnique({ where: { id: identity.id } })).userId,
    target.id,
  );
  assert.ok(await prisma.user.findUnique({ where: { id: empty.id } }));
  assert.ok((await prisma.authSession.findUnique({ where: { id: oldSid } })).revokedAt);
  assert.equal(
    (await prisma.authSession.findUnique({ where: { id: jwt.decode(linked.accessToken).sid } }))
      .emailVerifiedAt,
    null,
  );

  const populated = await user();
  const body = await prisma.bodyRecord.create({
    data: { userId: populated.id, weight: 70, recordedAt: new Date() },
  });
  const populatedIdentity = await prisma.wechatIdentity.create({
    data: { appId, openId: 'populated', userId: populated.id },
  });
  const deniedCode = await service.bindingCode(payload);
  await assert.rejects(
    prove('populated', deniedCode.code),
    (e) => e.getResponse().code === 'WECHAT_ALREADY_BOUND',
  );
  assert.equal(
    (await prisma.wechatIdentity.findUnique({ where: { id: populatedIdentity.id } })).userId,
    populated.id,
  );
  assert.equal(
    (await prisma.bodyRecord.findUnique({ where: { id: body.id } })).userId,
    populated.id,
  );

  // Target already has this AppID: unique-key failure must roll back revocation and token changes.
  const competing = await user();
  const competingSession = await sessions.create(await users.findPublicById(competing.id), 0);
  await prisma.wechatIdentity.create({
    data: { appId, openId: 'competing', userId: competing.id },
  });
  const conflictingCode = await service.bindingCode(payload);
  await assert.rejects(
    prove('competing', conflictingCode.code),
    (e) => e.getResponse().code === 'WECHAT_BINDING_CONFLICT',
  );
  assert.equal((await prisma.user.findUnique({ where: { id: competing.id } })).tokenVersion, 0);
  assert.equal(
    (
      await prisma.authSession.findUnique({
        where: { id: jwt.decode(competingSession.accessToken).sid },
      })
    ).revokedAt,
    null,
  );
  console.log(
    'PASS: empty-account reassignment, retained source account, revoked old session, no email proof upgrade, populated-account rejection and transaction rollback.',
  );
} finally {
  globalThis.fetch = nativeFetch;
  for (const id of ids.reverse()) await prisma.user.delete({ where: { id } });
  await prisma.$disconnect();
  console.log('All synthetic deployment accounts and records removed.');
}
