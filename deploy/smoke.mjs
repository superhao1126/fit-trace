import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { createRequire } from 'node:module';
import { rm } from 'node:fs/promises';
import { resolve } from 'node:path';

const require = createRequire(new URL('../apps/api/package.json', import.meta.url));
const { PrismaClient } = require('@prisma/client');
const { hash } = require('bcryptjs');
const { config } = createRequire(require.resolve('@nestjs/config'))('dotenv');
config({ path: new URL('../.env', import.meta.url), quiet: true });
const prisma = new PrismaClient();
const base = process.env.SMOKE_API_URL ?? 'http://127.0.0.1:3100/api/v1';
const password = randomBytes(24).toString('hex');
const email = `deploy-${randomBytes(12).toString('hex')}@example.invalid`;
let user;
let uploaded;
let token;

async function request(path, method = 'GET', body) {
  const response = await fetch(`${base}${path}`, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body instanceof FormData ? {} : { 'Content-Type': 'application/json' }),
    },
    ...(body === undefined ? {} : { body: body instanceof FormData ? body : JSON.stringify(body) }),
    signal: AbortSignal.timeout(15000),
  });
  const payload = await response.json();
  assert.ok(
    response.ok && payload.code === 'OK',
    `${method} ${path}: HTTP ${response.status}, code=${payload.code}`,
  );
  return payload.data;
}

try {
  assert.equal((await request('/health')).status, 'ok');
  user = await prisma.user.create({
    data: { email, passwordHash: await hash(password, 10), nickname: '部署验收临时账号' },
  });
  const session = await request('/auth/login', 'POST', { email, password });
  token = session.accessToken;
  assert.equal((await request('/auth/me')).id, user.id);
  const body = await request('/body-records', 'POST', { weight: 72 });
  assert.equal((await request(`/body-records/${body.id}`)).weight, 72);
  const meal = await request('/meals', 'POST', {
    type: 'LUNCH',
    foods: [{ name: '部署验收食物', amount: '100 g', calories: 100 }],
  });
  await request(`/meals/${meal.id}`);
  const workout = await request('/workouts', 'POST', {
    type: 'RUNNING',
    name: '部署验收训练',
    durationMinutes: 10,
  });
  await request(`/workouts/${workout.id}`);
  assert.equal((await request('/training/plan/state')).configured, false);
  const favorite = await request('/meals/favorites', 'POST', {
    name: '部署验收常用餐',
    foods: [{ name: '零热量', calories: 0 }, { name: '未知热量' }],
  });
  assert.equal(favorite.foods[0].calories, 0);
  assert.equal(favorite.foods[1].calories, null);
  assert.equal((await request('/meals/favorites')).length, 1);
  await request(`/meals/${meal.id}`, 'PATCH', { foods: [{ name: '修改后的原餐', calories: 200 }] });
  assert.deepEqual((await request('/meals/favorites'))[0].foods, favorite.foods);
  await request(`/meals/favorites/${favorite.id}`, 'DELETE');
  assert.equal((await request('/meals/favorites')).length, 0);
  const presets = await request('/training/templates');
  assert.equal(presets.filter((value) => value.preset).length, 3);
  const template = await request('/training/templates', 'POST', {
    name: '部署验收模板',
    durationMinutes: 20,
    exercises: presets[0].exercises,
  });
  const edited = await request(`/training/templates/${template.id}`, 'PATCH', {
    name: '部署验收模板编辑',
    durationMinutes: 25,
    exercises: template.exercises,
  });
  assert.equal(edited.name, '部署验收模板编辑');
  const days = Array.from({ length: 7 }, (_, weekday) => ({
    weekday,
    templateId: weekday === 1 ? template.id : null,
  }));
  await request('/training/plan', 'PUT', { days });
  assert.deepEqual(await request('/training/plan'), days);
  assert.equal((await request('/training/plan/state')).configured, true);
  const exercise = structuredClone(template.exercises[0]);
  exercise.sets = [{ reps: 12, weightKg: 0, completed: true }];
  const strength = await request('/workouts', 'POST', {
    type: 'STRENGTH',
    name: '部署验收逐组训练',
    durationMinutes: 10,
    exercises: [exercise],
  });
  assert.deepEqual((await request(`/workouts/${strength.id}`)).exercises, [exercise]);
  assert.deepEqual(
    (await request(`/training/exercises/${exercise.exerciseId}/previous`)).sets,
    exercise.sets,
  );
  assert.equal((await request('/dashboard?tzOffset=480')).latestBodyRecord.id, body.id);

  const image = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aL1cAAAAASUVORK5CYII=',
    'base64',
  );
  const form = new FormData();
  form.append('file', new Blob([image], { type: 'image/png' }), 'deployment-smoke.png');
  uploaded = await request('/uploads', 'POST', form);
  assert.ok(uploaded.url.startsWith('https://fittrace.idoit.icu/uploads/'));
  assert.equal((await fetch(new URL(`/uploads/${uploaded.key}`, base))).status, 200);
  const photo = await request('/progress-photos', 'POST', {
    type: 'OTHER',
    imageUrl: uploaded.url,
  });
  await request(`/progress-photos/${photo.id}`);
  const refreshed = await request('/auth/refresh', 'POST', { refreshToken: session.refreshToken });
  assert.equal(refreshed.user.id, user.id);
  token = refreshed.accessToken;
  await request('/auth/logout', 'POST', { refreshToken: refreshed.refreshToken });
  assert.equal(
    (await fetch(`${base}/auth/me`, { headers: { Authorization: `Bearer ${token}` } })).status,
    401,
  );
  console.log(
    'PASS: 数据库、登录、四类记录、常用餐快照、训练计划状态、上次成绩、首页、图片、续期及退出。',
  );
} finally {
  if (uploaded) {
    const directory = process.env.STORAGE_LOCAL_DIR;
    assert.ok(directory && uploaded.key.includes(`/${user.id}/`));
    await rm(resolve(directory, uploaded.key), { force: true });
  }
  if (user) await prisma.user.delete({ where: { id: user.id } });
  await prisma.$disconnect();
  console.log('临时账号、记录及上传文件已清理。');
}
