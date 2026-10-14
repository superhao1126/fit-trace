import 'reflect-metadata';
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { plainToInstance } from 'class-transformer';
import { validate } from 'class-validator';
import mealsModule from '../dist/meals/meals.service.js';
import trainingModule from '../dist/training/training.service.js';
import favoriteDto from '../dist/meals/dto/meal-favorite.dto.js';
const { MealsService } = mealsModule;
const { TrainingService } = trainingModule;
const foods = [{ name: ' 米饭 ', amount: ' 100g ', calories: 0 }, { name: '鸡胸肉' }];
test('favorites validate names and nested food values', async () => {
  assert.equal(
    (await validate(plainToInstance(favoriteDto.MealFavoriteDto, { name: '早餐', foods }))).length,
    0,
  );
  for (const input of [
    { name: ' ', foods },
    { name: '餐', foods: [] },
    { name: '餐', foods: [{ name: '米饭', calories: -1 }] },
    { name: '餐', foods: [{ name: '米饭', calories: 1.5 }] },
  ])
    assert.ok((await validate(plainToInstance(favoriteDto.MealFavoriteDto, input))).length);
});
test('favorites are independent owner snapshots and retain zero versus unknown calories', async () => {
  let saved;
  const service = new MealsService({
    mealFavorite: {
      create: async ({ data }) => {
        saved = data;
        return { id: 'favorite', ...data, createdAt: new Date('2026-10-13') };
      },
    },
  });
  const result = await service.favorite('owner', { name: ' 午餐 ', foods });
  assert.equal(saved.userId, 'owner');
  assert.equal(result.name, '午餐');
  assert.equal(result.foods[0].calories, 0);
  assert.equal(result.foods[1].calories, null);
  foods[0].name = '原记录修改';
  assert.equal(result.foods[0].name, '米饭');
  await assert.rejects(service.favorite('owner', { name: '餐', foods: [{ name: ' ' }] }), {
    status: 400,
  });
});
test('favorite list and removal scope every query to the owner', async () => {
  let listQuery, deleteQuery;
  const service = new MealsService({
    mealFavorite: {
      findMany: async (q) => {
        listQuery = q;
        return [];
      },
      deleteMany: async (q) => {
        deleteQuery = q;
        return { count: 0 };
      },
    },
  });
  assert.deepEqual(await service.favorites('owner'), []);
  assert.deepEqual(listQuery.where, { userId: 'owner' });
  await assert.rejects(service.removeFavorite('owner', 'foreign'), { status: 404 });
  assert.deepEqual(deleteQuery.where, { id: 'foreign', userId: 'owner' });
});
test('plan state distinguishes an absent plan from a deliberately saved rest week', async () => {
  let present = false;
  const service = new TrainingService({
    trainingPlan: {
      findUnique: async ({ where }) => {
        assert.deepEqual(where, { userId: 'owner' });
        return present
          ? { days: Array.from({ length: 7 }, (_, weekday) => ({ weekday, templateId: null })) }
          : null;
      },
    },
  });
  const missing = await service.planState('owner');
  assert.equal(missing.configured, false);
  assert.equal(missing.days.length, 7);
  present = true;
  assert.equal((await service.planState('owner')).configured, true);
});
const exercise = {
  exerciseId: 'squat',
  unit: 'reps',
  sets: [
    { reps: 10, weightKg: 0, completed: true },
    { reps: 8, weightKg: 20, completed: false },
  ],
};
test('previous performance skips incomplete, incompatible and legacy entries', async () => {
  let query;
  const service = new TrainingService({
    workoutRecord: {
      findMany: async (q) => {
        query = q;
        return [
          { startedAt: new Date('2026-10-12'), exercises: [{ ...exercise, unit: 'seconds' }] },
          {
            startedAt: new Date('2026-10-11'),
            exercises: [{ ...exercise, sets: [{ reps: 8, weightKg: 20, completed: false }] }],
          },
          {
            startedAt: new Date('2026-10-10'),
            exercises: [{ ...exercise, sets: [{ reps: 0, weightKg: 0, completed: true }] }],
          },
          { startedAt: new Date('2026-10-09'), exercises: [exercise] },
        ];
      },
    },
  });
  const result = await service.previous('owner', 'squat');
  assert.equal(query.where.userId, 'owner');
  assert.ok(query.where.startedAt.lte instanceof Date);
  assert.deepEqual(query.where.exercises, { array_contains: [{ exerciseId: 'squat' }] });
  assert.deepEqual(result.sets, [{ reps: 10, weightKg: 0, completed: true }]);
  assert.equal(result.startedAt, '2026-10-09T00:00:00.000Z');
});
test('previous performance continues beyond a full page of unusable records', async () => {
  const skips = [];
  const service = new TrainingService({
    workoutRecord: {
      findMany: async (q) => {
        skips.push(q.skip);
        return q.skip === 0
          ? Array.from({ length: 20 }, () => ({ startedAt: new Date('2026-10-12'), exercises: [] }))
          : [{ startedAt: new Date('2026-10-01'), exercises: [exercise] }];
      },
    },
  });
  assert.ok(await service.previous('owner', 'squat'));
  assert.deepEqual(skips, [0, 20]);
});
test('unknown exercise IDs are rejected without querying records; empty history returns null', async () => {
  let reads = 0;
  const service = new TrainingService({
    workoutRecord: {
      findMany: async () => {
        reads++;
        return [];
      },
    },
  });
  await assert.rejects(service.previous('owner', 'foreign'), { status: 400 });
  assert.equal(reads, 0);
  assert.equal(await service.previous('owner', 'squat'), null);
});
