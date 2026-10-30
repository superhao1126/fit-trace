import assert from 'node:assert/strict';
import { createHmac, randomBytes, randomInt } from 'node:crypto';
import { createRequire } from 'node:module';

const require = createRequire(new URL('../apps/api/package.json', import.meta.url));
const { config } = createRequire(require.resolve('@nestjs/config'))('dotenv');
config({ path: new URL('../.env', import.meta.url), quiet: true });
const { PrismaClient } = require('@prisma/client');
const prisma = new PrismaClient();
const base = process.env.SMOKE_API_URL ?? 'http://127.0.0.1:3100/api/v1';
const email = `mini-email-${randomBytes(12).toString('hex')}@example.invalid`;
const code = randomInt(0, 1000000).toString().padStart(6, '0');
const digest = (value) => createHmac('sha256', process.env.JWT_SECRET).update(value).digest('hex');
let user, token;

async function call(path, method = 'GET', body, expected = 200) {
  const response = await fetch(base + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    signal: AbortSignal.timeout(15000),
  });
  assert.equal(response.status, expected, `${method} ${path}`);
  const result = await response.json();
  if (expected < 400) assert.equal(result.code, 'OK');
  return result.data;
}

try {
  // Invalid input exercises the public route without sending an email.
  await call('/auth/miniprogram/email-code', 'POST', { email: 'invalid' }, 400);
  user = await prisma.user.create({
    data: { email, hasPassword: false, nickname: '小程序邮箱部署验收临时账号' },
  });
  const record = await prisma.bodyRecord.create({
    data: { userId: user.id, weight: 72, recordedAt: new Date() },
  });
  // Seed only a disposable account's proof; real users, SMTP and Aliyun are untouched.
  await prisma.emailVerification.create({
    data: {
      email,
      purpose: 'LOGIN',
      codeHash: digest(`email:${email}:LOGIN:${code}`),
      expiresAt: new Date(Date.now() + 600000),
    },
  });
  const body = { email: ` ${email.toUpperCase()} `, emailCode: code, acceptedTerms: true };
  const session = await call('/auth/miniprogram/login', 'POST', body, 201);
  assert.equal(session.user.id, user.id);
  assert.equal(session.user.email, email);
  token = session.accessToken;
  assert.equal((await call('/auth/me')).id, user.id);
  assert.equal((await call(`/body-records/${record.id}`)).weight, 72);
  const stored = await prisma.authSession.findFirst({ where: { userId: user.id } });
  assert.ok(stored.emailVerifiedAt);
  await call('/auth/miniprogram/login', 'POST', body, 400);
  const refreshed = await call(
    '/auth/refresh',
    'POST',
    { refreshToken: session.refreshToken },
    201,
  );
  assert.equal(refreshed.user.id, user.id);
  token = refreshed.accessToken;
  await call('/auth/logout', 'POST', { refreshToken: refreshed.refreshToken }, 201);
  await call('/auth/me', 'GET', undefined, 401);
  console.log(
    'PASS: mini email route, normalized existing-account login, original records, verified session, single-use code, refresh and logout; no email sent.',
  );
} finally {
  await prisma.emailVerification.deleteMany({ where: { email } });
  await prisma.authRateLimit.deleteMany({
    where: { key: `email-login-email:${digest(email)}` },
  });
  if (user) await prisma.user.delete({ where: { id: user.id } });
  await prisma.$disconnect();
  console.log('Temporary mini email user, proof and records removed.');
}
