import assert from 'node:assert/strict';
import { test } from 'node:test';
import ranges from '../dist/common/dto/record-range-query.dto.js';
import bodies from '../dist/body-records/body-records.service.js';
import meals from '../dist/meals/meals.service.js';
import workouts from '../dist/workouts/workouts.service.js';
import photos from '../dist/progress-photos/progress-photos.service.js';
const query = { page: 2, pageSize: 20, from: '2026-09-30T16:00:00Z', to: '2026-10-31T16:00:00Z' };
test('date ranges are exclusive at end and reject invalid/reversed bounds', () => {
  assert.equal(ranges.recordRange({}), undefined);
  assert.deepEqual(ranges.recordRange(query), {
    gte: new Date(query.from),
    lt: new Date(query.to),
  });
  for (const value of [
    { from: 'bad' },
    { ...query, to: query.from },
    { ...query, from: query.to, to: query.from },
  ])
    assert.throws(() => ranges.recordRange(value), { status: 400 });
});
for (const [Service, model, field] of [
  [bodies.BodyRecordsService, 'bodyRecord', 'recordedAt'],
  [meals.MealsService, 'mealRecord', 'recordedAt'],
  [workouts.WorkoutsService, 'workoutRecord', 'startedAt'],
  [photos.ProgressPhotosService, 'progressPhoto', 'recordedAt'],
]) {
  test(`${model} count and list use same owner/range with pagination`, async () => {
    const calls = [];
    const db = {
      [model]: {
        findMany: (args) => {
          calls.push(args);
          return Promise.resolve([]);
        },
        count: (args) => {
          calls.push(args);
          return Promise.resolve(42);
        },
      },
      $transaction: (values) => Promise.all(values),
    };
    const result = await new Service(db).list('owner', query);
    assert.deepEqual(calls[0].where, calls[1].where);
    assert.equal(calls[0].where.userId, 'owner');
    assert.deepEqual(calls[0].where[field], ranges.recordRange(query));
    assert.equal(calls[0].skip, 20);
    assert.equal(calls[0].take, 20);
    assert.equal(result.meta.total, 42);
  });
}
