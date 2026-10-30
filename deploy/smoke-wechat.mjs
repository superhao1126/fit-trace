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
const prisma = new PrismaClient();
const base = 'http://127.0.0.1:3100/api/v1';
let user, token;
async function call(path, method = 'GET', body) {
  const response = await fetch(base + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: 'Bearer ' + token } : {}),
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    signal: AbortSignal.timeout(15000),
  });
  const result = await response.json();
  assert.ok(
    response.ok && result.code === 'OK',
    method + ' ' + path + ' status=' + response.status + ' code=' + result.code,
  );
  return result.data;
}
try {
  assert.equal(process.env.WECHAT_APP_ID, 'wx43ad89de416aa70d');
  assert.ok(process.env.WECHAT_APP_SECRET);
  user = await prisma.user.create({
    data: { hasPassword: false, nickname: '部署验收临时微信模型账号' },
  });
  const users = new UsersService(prisma);
  const sessions = new AuthSessionService(
    prisma,
    users,
    new JwtService({ secret: process.env.JWT_SECRET, signOptions: { expiresIn: 180 } }),
  );
  const session = await sessions.create(await users.findPublicById(user.id), 0);
  token = session.accessToken;
  const proof = await prisma.authSession.findFirst({ where: { userId: user.id } });
  assert.equal(proof.emailVerifiedAt, null);
  assert.equal((await call('/auth/me')).email, null);
  const templates = await call('/training/templates');
  assert.ok(templates.length >= 10);
  const exercise = templates[0].exercises[0];
  exercise.sets = [{ reps: 10, weightKg: 12.5, completed: true }];
  const draft = {
    type: 'STRENGTH',
    name: '临时微信训练验收',
    durationMinutes: 10,
    exercises: [exercise],
    clientRequestId: 'deployment-' + randomBytes(12).toString('hex'),
  };
  const saved = await call('/workouts', 'POST', draft);
  const retried = await call('/workouts', 'POST', draft);
  assert.equal(saved.id, retried.id);
  assert.equal(
    (await call('/workouts/' + saved.id, 'PUT', { durationMinutes: 12 })).durationMinutes,
    12,
  );
  assert.equal(
    (await call('/training/exercises/' + exercise.exerciseId + '/previous')).sets[0].weightKg,
    12.5,
  );
  const body = await call('/body-records', 'POST', { weight: 72 });
  assert.equal((await call('/body-records/' + body.id, 'PUT', { weight: 71.5 })).weight, 71.5);
  const refreshed = await call('/auth/refresh', 'POST', { refreshToken: session.refreshToken });
  assert.equal(refreshed.user.email, null);
  token = refreshed.accessToken;
  await call('/auth/logout', 'POST', { refreshToken: refreshed.refreshToken });
  assert.equal(
    (await fetch(base + '/auth/me', { headers: { Authorization: 'Bearer ' + token } })).status,
    401,
  );
  console.log(
    'PASS: server WeChat config present; nullable-email session, no email verification, training idempotency, PUT updates, previous exercise, refresh and logout.',
  );
} finally {
  if (user) await prisma.user.delete({ where: { id: user.id } });
  await prisma.$disconnect();
  console.log('Temporary WeChat-model user and records removed.');
}
