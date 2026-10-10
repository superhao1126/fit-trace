import assert from 'node:assert/strict';
import { test } from 'node:test';
import service from '../dist/insights/insights.service.js';
function fixture() {
  const bodies = [
    { weight: 70, recordedAt: new Date('2026-10-09T17:00:00Z') },
    { weight: 69, recordedAt: new Date('2026-10-10T01:00:00Z') },
  ];
  const meals = [
    { type: 'DINNER', foods: [{ calories: null }], recordedAt: new Date('2026-10-09T17:00:00Z') },
    { type: 'BREAKFAST', foods: [{ calories: 0 }], recordedAt: new Date('2026-10-10T01:00:00Z') },
  ];
  const db = {
    bodyRecord: { findMany: async () => bodies },
    mealRecord: { findMany: async () => meals },
    workoutRecord: { findMany: async () => [] },
    progressPhoto: { findMany: async () => [] },
  };
  return new service.InsightsService(db);
}
test('local-day grouping matches meal summary; a single day has unknown change, real zero stays zero', async (t) => {
  t.mock.timers.enable({ apis: ['Date'], now: new Date('2026-10-10T02:00:00Z') });
  const east = await fixture().overview('owner', 7, 480);
  assert.equal(east.meals.recordedDays, 1);
  assert.equal(east.series.at(-1).mealCount, 2);
  assert.equal(east.weight.change, null);
  assert.equal(east.weight.current, 69);
  assert.equal(east.calories.average, 0);
  const utc = await fixture().overview('owner', 7, 0);
  assert.equal(utc.meals.recordedDays, 2);
  assert.equal(utc.weight.change, -1);
});
