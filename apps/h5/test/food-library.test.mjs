/* global structuredClone */
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { access } from 'node:fs/promises';
import { URL } from 'node:url';
import {
  foodLibrary,
  foodCalories,
  foodCategories,
  searchFoods,
} from '../src/data/food-library.ts';
import { completedTraining, trainingValidation } from '../src/utils/training-validation.ts';
test('food calories convert actual grams and reject invalid portions', () => {
  const rice = foodLibrary.find((f) => f.id === '168878');
  assert.equal(foodCalories(rice, 150), 195);
  assert.equal(foodCalories(rice, 0.5), 1);
  for (const grams of [0, -1, NaN, Infinity, 10001]) assert.throws(() => foodCalories(rice, grams));
  assert.equal(new Set(foodLibrary.map((f) => f.id)).size, foodLibrary.length);
  for (const food of foodLibrary) {
    assert.equal(new URL(food.sourceUrl).hostname, 'fdc.nal.usda.gov');
    assert.match(food.ndbNumber, /^\d{5}$/);
  }
});
test('expanded catalog has valid provenance, nutrients and bundled food images', async () => {
  assert.ok(foodLibrary.length >= 140);
  assert.equal(foodCategories.length, 7);
  for (const food of foodLibrary) {
    assert.ok(food.sourceDescription);
    assert.ok(
      Number.isFinite(food.kcalPer100g) && food.kcalPer100g >= 0 && food.kcalPer100g <= 900,
    );
    for (const nutrient of [food.proteinPer100g, food.fatPer100g, food.carbsPer100g])
      assert.ok(Number.isFinite(nutrient) && nutrient >= 0 && nutrient <= 100);
    await access(new URL(`../public${food.imageUrl}`, import.meta.url));
  }
  assert.equal(new Set(foodLibrary.map((food) => food.imageUrl)).size, 64);
});
test('aliases, category filtering and existing account favorites remain usable', () => {
  assert.ok(searchFoods('西红柿').every((food) => food.name.includes('番茄')));
  assert.ok(searchFoods('西红柿').length);
  assert.ok(searchFoods('番茄 生').every((food) => food.name.includes('生')));
  assert.ok(searchFoods('', '水产').every((food) => food.category === '水产'));
  assert.deepEqual(
    searchFoods('', '常用', ['168878']).map((food) => food.id),
    ['168878'],
  );
  assert.equal(searchFoods('不存在的食物').length, 0);
  assert.equal(searchFoods('', '常用', []).length, 0);
  const rice = foodLibrary.find((food) => food.id === '168878');
  const raw = foodLibrary.find((food) => food.name === '大米（生）');
  assert.ok(raw.kcalPer100g > rice.kcalPer100g * 2);
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
