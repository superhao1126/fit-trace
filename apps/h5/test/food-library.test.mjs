/* global structuredClone */
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { foodLibrary, foodCalories } from '../src/data/food-library.ts';
import { completedTraining, trainingValidation } from '../src/utils/training-validation.ts';
test('food calories convert actual grams and reject invalid portions', () => {
  const rice = foodLibrary.find((f) => f.id === '168878');
  assert.equal(foodCalories(rice, 150), 195);
  assert.equal(foodCalories(rice, 0.5), 1);
  for (const grams of [0, -1, NaN, Infinity, 10001]) assert.throws(() => foodCalories(rice, grams));
  assert.equal(new Set(foodLibrary.map((f) => f.id)).size, foodLibrary.length);
  for (const food of foodLibrary)
    assert.match(food.sourceUrl, /^https:\/\/fdc\.nal\.usda\.gov\/food-details\/\d+\/nutrients$/);
});
const sample = [
  {
    exerciseId: 'squat',
    name: '徒手深蹲',
    unit: 'reps',
    restSeconds: 60,
    sets: [
      { reps: 12, weightKg: 0, completed: true },
      { reps: 10, weightKg: 0, completed: false },
    ],
  },
  {
    exerciseId: 'plank',
    name: '平板支撑',
    unit: 'seconds',
    restSeconds: 30,
    sets: [{ reps: 20, weightKg: 0, completed: false }],
  },
];
test('completion excludes unperformed sets and does not mutate the draft', () => {
  const copy = structuredClone(sample);
  const result = completedTraining(sample);
  assert.equal(result.length, 1);
  assert.equal(result[0].sets.length, 1);
  assert.deepEqual(sample, copy);
  result[0].sets[0].reps = 8;
  assert.equal(sample[0].sets[0].reps, 12);
});
test('training rejects missing exercises, fractional repetitions and invalid weights', () => {
  assert.equal(trainingValidation(sample), null);
  assert.ok(trainingValidation([]));
  for (const change of [
    { reps: 0 },
    { reps: 1.5 },
    { weightKg: -1 },
    { weightKg: NaN },
    { weightKg: 1.234 },
  ]) {
    const x = structuredClone(sample);
    Object.assign(x[0].sets[0], change);
    assert.ok(trainingValidation(x));
  }
  const x = structuredClone(sample);
  x[0].restSeconds = 601;
  assert.ok(trainingValidation(x));
});
