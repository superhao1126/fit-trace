import assert from 'node:assert/strict';
import { test } from 'node:test';
import { applyFoodEstimate, correctFoodName } from '../src/utils/food-correction.ts';

test('correcting one AI dish invalidates its old estimates and preserves other dishes', () => {
  const foods = [
    { name: '红烧肉', amount: '150 g', calories: 450, aiGenerated: true },
    { name: '黄瓜炒肉丁', amount: '100 g', calories: 180, aiGenerated: true },
  ];
  const untouched = { ...foods[1] };
  correctFoodName(foods[0], '大排');
  assert.deepEqual(foods[0], { name: '大排', amount: '', calories: '', aiGenerated: false });
  assert.deepEqual(foods[1], untouched);
});

test('whitespace-only edits preserve AI estimates', () => {
  const food = { name: '大排', amount: '1 块', calories: 300, aiGenerated: true };
  correctFoodName(food, ' 大排 ');
  assert.equal(food.calories, 300);
  assert.equal(food.amount, '1 块');
  assert.equal(food.aiGenerated, true);
});

test('manual nutrition values survive subsequent edits after a correction', () => {
  const food = { name: '红烧肉', amount: '150 g', calories: 450, aiGenerated: true };
  correctFoodName(food, '大');
  food.amount = '1 块';
  food.calories = '320';
  correctFoodName(food, '大排');
  assert.deepEqual(food, { name: '大排', amount: '1 块', calories: '320', aiGenerated: false });
});

test('single-dish estimate updates only that dish and becomes an AI estimate', () => {
  const food = { name: '茄子', amount: '', calories: '', aiGenerated: false };
  const other = { name: '米饭', amount: '1 碗', calories: 200, aiGenerated: true };
  assert.equal(
    applyFoodEstimate(food, { ...food }, { estimatedAmount: '150g', estimatedCalories: 180 }),
    true,
  );
  assert.deepEqual(food, { name: '茄子', amount: '150g', calories: 180, aiGenerated: true });
  assert.equal(other.calories, 200);
  correctFoodName(food, '土豆');
  assert.equal(food.calories, '');
});

test('late estimates preserve changed name, portion and calories', () => {
  for (const change of [{ name: '土豆' }, { amount: '200g' }, { calories: 100 }]) {
    const food = { name: '茄子', amount: '', calories: '', aiGenerated: false };
    const snapshot = { ...food };
    Object.assign(food, change);
    const expected = { ...food };
    assert.equal(
      applyFoodEstimate(food, snapshot, { estimatedAmount: '150g', estimatedCalories: 180 }),
      false,
    );
    assert.deepEqual(food, expected);
  }
});

test('incomplete or invalid estimates preserve the existing values', () => {
  for (const estimate of [
    { estimatedAmount: null, estimatedCalories: 180 },
    { estimatedAmount: '150g', estimatedCalories: null },
    { estimatedAmount: '150g', estimatedCalories: -1 },
  ]) {
    const food = { name: '茄子', amount: '', calories: '', aiGenerated: false };
    const snapshot = { ...food };
    assert.equal(applyFoodEstimate(food, snapshot, estimate), false);
    assert.deepEqual(food, snapshot);
  }
});
