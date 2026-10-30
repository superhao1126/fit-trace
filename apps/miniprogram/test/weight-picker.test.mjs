import assert from 'node:assert/strict';
import { test } from 'node:test';
import { toWeightParts, normalizeWeightParts, fromWeightParts } from '../src/weight-picker.ts';

test('weight wheels preserve every allowed hundredth on reopening an existing record', () => {
  for (let cents = 100; cents <= 50000; cents++) {
    const weight = cents / 100;
    assert.equal(fromWeightParts(toWeightParts(weight)), weight);
  }
});

test('500 kg excludes fractional values and wheel values stay within the API range', () => {
  assert.deepEqual(normalizeWeightParts([500, 99]), [500, 0]);
  assert.deepEqual(normalizeWeightParts(['71', '8']), [71, 8]);
  assert.equal(fromWeightParts([71, 8]), 71.08);
  assert.deepEqual(toWeightParts(0), [1, 0]);
  assert.deepEqual(toWeightParts(501), [500, 0]);
  assert.deepEqual(toWeightParts(), [70, 0]);
});
