import assert from 'node:assert/strict';
import { test } from 'node:test';
import { correctFoodName } from '../src/utils/food-correction.ts';

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
