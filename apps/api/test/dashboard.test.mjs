import assert from 'node:assert/strict';
import { test } from 'node:test';
import service from '../dist/dashboard/dashboard.service.js';
const { DashboardService } = service;

function fixture(records = {}) {
  const query = (rows, args, field = 'recordedAt') => {
    const range = args.where[field];
    const result = rows
      .filter(
        (row) =>
          row.userId === args.where.userId &&
          row[field] <= range.lte &&
          (!range.gte || row[field] >= range.gte),
      )
      .sort((a, b) => b[field] - a[field]);
    return args.take ? result.slice(0, args.take) : result;
  };
  const model = (rows = [], field) => ({
    findMany: async (args) => query(rows, args, field),
    findFirst: async (args) => query(rows, args, field)[0] ?? null,
    aggregate: async (args) => {
      const values = query(rows, args, field);
      return {
        _count: values.length,
        _sum: {
          durationMinutes: values.reduce((sum, row) => sum + row.durationMinutes, 0),
        },
      };
    },
  });
  const db = {
    bodyRecord: model(records.bodies),
    mealRecord: model(records.meals),
    workoutRecord: model(records.workouts, 'startedAt'),
    progressPhoto: model(records.photos),
  };
  return new DashboardService({ $transaction: async (callback) => callback(db) });
}

test('empty dashboard keeps unknown calories and weight change null', async (t) => {
  t.mock.timers.enable({ apis: ['Date'], now: new Date('2026-09-22T02:00:00Z') });
  const result = await fixture().overview('owner', 480);
  assert.equal(result.date, '2026-09-22');
  assert.equal(result.latestBodyRecord, null);
  assert.equal(result.today.calories, null);
  assert.equal(result.bodyChanges.weight, null);
  assert.equal(result.today.mealCount, 0);
  assert.equal(result.today.workoutMinutes, 0);
});

test('local midnight, ownership, future exclusion and totals beyond 30 records', async (t) => {
  t.mock.timers.enable({ apis: ['Date'], now: new Date('2026-09-22T02:00:00Z') });
  const meal = (id, at, calories, userId = 'owner') => ({
    id,
    userId,
    type: 'BREAKFAST',
    recordedAt: new Date(at),
    foods: [{ name: '测试食物', calories }],
  });
  const meals = Array.from({ length: 35 }, (_, i) => meal(String(i), '2026-09-22T01:00:00Z', 10));
  meals.push(meal('midnight', '2026-09-21T16:00:00Z', 0));
  meals.push(meal('yesterday', '2026-09-21T15:59:59Z', 1000));
  meals.push(meal('future', '2026-09-22T03:00:00Z', 1000));
  meals.push(meal('other', '2026-09-22T01:00:00Z', 1000, 'other'));
  const bodies = [
    { id: 'today', userId: 'owner', weight: 70.1, recordedAt: new Date('2026-09-21T16:00:00Z') },
    { id: 'previous', userId: 'owner', weight: 70.4, recordedAt: new Date('2026-09-20T16:00:00Z') },
  ];
  const workouts = Array.from({ length: 32 }, (_, i) => ({
    id: String(i),
    userId: 'owner',
    type: 'RUNNING',
    durationMinutes: 5,
    startedAt: new Date('2026-09-22T01:00:00Z'),
  }));
  const result = await fixture({ meals, bodies, workouts }).overview('owner', 480);
  assert.equal(result.today.mealCount, 36);
  assert.equal(result.today.calories, 350);
  assert.equal(result.today.mealPreview.length, 4);
  assert.equal(result.today.workoutCount, 32);
  assert.equal(result.today.workoutMinutes, 160);
  assert.equal(result.today.bodyRecord.id, 'today');
  assert.equal(result.bodyChanges.weight, -0.3);
  assert.deepEqual(result.today.meals, {
    BREAKFAST: true,
    LUNCH: false,
    DINNER: false,
    SNACK: false,
  });
  const west = await fixture({ bodies }).overview('owner', -420);
  assert.equal(west.date, '2026-09-21');
  assert.equal(west.today.bodyRecord.id, 'today');
});

test('zero calories remain zero; unknown values do not become zero', async (t) => {
  t.mock.timers.enable({ apis: ['Date'], now: new Date('2026-09-22T02:00:00Z') });
  const meals = [
    {
      id: 'meal',
      userId: 'owner',
      type: 'LUNCH',
      recordedAt: new Date('2026-09-22T01:00:00Z'),
      foods: [{ name: '水', calories: null }],
    },
  ];
  const service = fixture({ meals });
  assert.equal((await service.overview('owner', 480)).today.calories, null);
  meals[0].foods[0].calories = 0;
  assert.equal((await service.overview('owner', 480)).today.calories, 0);
});

test('seven local days use last daily weight and exclude old, future and other-user data', async (t) => {
  t.mock.timers.enable({ apis: ['Date'], now: new Date('2026-09-22T02:00:00Z') });
  const body = (at, weight, userId = 'owner') => ({ recordedAt: new Date(at), weight, userId });
  const bodies = [
    body('2026-09-15T15:59:59Z', 100),
    body('2026-09-15T16:00:00Z', 80),
    body('2026-09-16T01:00:00Z', 72),
    body('2026-09-22T01:00:00Z', 71.25),
    body('2026-09-22T03:00:00Z', 99),
    body('2026-09-22T01:30:00Z', 99, 'other'),
  ];
  const meals = bodies.map((row) => ({ ...row, foods: [] }));
  const workouts = bodies.map((row) => ({
    ...row,
    startedAt: row.recordedAt,
    durationMinutes: 10,
  }));
  const result = await fixture({ bodies, meals, workouts }).overview('owner', 480);
  assert.deepEqual(result.recentTrend, {
    days: 7,
    from: '2026-09-16',
    to: '2026-09-22',
    weightChange: -0.75,
    bodyRecordedDays: 2,
    mealRecordedDays: 2,
    workoutCount: 3,
    workoutMinutes: 30,
  });
  const west = await fixture({ bodies }).overview('owner', -420);
  assert.equal(west.recentTrend.from, '2026-09-15');
  assert.equal(west.recentTrend.to, '2026-09-21');
  assert.equal(west.recentTrend.weightChange, -0.75);
});

test('insufficient daily weights stay unknown; equal weights on two days mean unchanged', async (t) => {
  t.mock.timers.enable({ apis: ['Date'], now: new Date('2026-09-22T02:00:00Z') });
  assert.equal((await fixture().overview('owner', 480)).recentTrend.weightChange, null);
  const bodies = [
    { userId: 'owner', recordedAt: new Date('2026-09-22T01:00:00Z'), weight: 70 },
    { userId: 'owner', recordedAt: new Date('2026-09-22T00:00:00Z'), weight: 71 },
  ];
  const singleDay = (await fixture({ bodies }).overview('owner', 480)).recentTrend;
  assert.equal(singleDay.bodyRecordedDays, 1);
  assert.equal(singleDay.weightChange, null);
  bodies.push({ userId: 'owner', recordedAt: new Date('2026-09-20T01:00:00Z'), weight: 70 });
  assert.equal((await fixture({ bodies }).overview('owner', 480)).recentTrend.weightChange, 0);
});

test('past diary ends before next local midnight and includes bounded previews', async (t) => {
  t.mock.timers.enable({ apis: ['Date'], now: new Date('2026-09-22T02:00:00Z') });
  const photo = (id, at, userId = 'owner') => ({
    id,
    userId,
    type: 'FRONT',
    imageUrl: '/test.jpg',
    recordedAt: new Date(at),
  });
  const photos = [
    photo('start', '2026-09-19T16:00:00Z'),
    photo('end', '2026-09-20T15:59:59.999Z'),
    photo('next', '2026-09-20T16:00:00Z'),
    photo('other', '2026-09-20T01:00:00Z', 'other'),
  ];
  const bodies = [
    { id: 'past', userId: 'owner', weight: 70, recordedAt: new Date('2026-09-20T01:00:00Z') },
    { id: 'today', userId: 'owner', weight: 69, recordedAt: new Date('2026-09-22T01:00:00Z') },
  ];
  const workouts = [
    {
      id: 'run',
      userId: 'owner',
      name: '晨跑',
      type: 'RUNNING',
      durationMinutes: 30,
      startedAt: new Date('2026-09-20T01:00:00Z'),
    },
  ];
  const result = await fixture({ bodies, photos, workouts }).overview('owner', 480, '2026-09-20');
  assert.equal(result.date, '2026-09-20');
  assert.equal(result.latestBodyRecord.id, 'past');
  assert.equal(result.today.photoCount, 2);
  assert.deepEqual(
    result.today.photoPreview.map((p) => p.id),
    ['end', 'start'],
  );
  assert.equal(result.today.workoutPreview[0].name, '晨跑');
  assert.equal(result.recentTrend.to, '2026-09-20');
  for (const date of ['2026-09-23', '2026-02-30', 'invalid'])
    await assert.rejects(fixture().overview('owner', 480, date), { status: 400 });
});
