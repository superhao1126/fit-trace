import assert from 'node:assert/strict';
import { test } from 'node:test';
import { weekDays, monthRange, weightChart, completedSets, clock } from '../src/presentation.ts';
test('week dates cross month and year boundaries without invented days', () => {
  const days = weekDays(new Date(2026, 9, 30));
  assert.deepEqual(
    days.map((d) => d.date),
    [26, 27, 28, 29, 30, 31, 1],
  );
  assert.deepEqual(
    days.filter((d) => d.today).map((d) => d.weekday),
    [5],
  );
  assert.equal(days[6].timestamp, new Date(2026, 10, 1).getTime());
  assert.equal(days[6].fullLabel, '11月1日 · 周日');
  assert.equal(
    weekDays(new Date(2026, 10, 2)).some((d) =>
      days.some((previous) => previous.timestamp === d.timestamp),
    ),
    false,
  );
  assert.deepEqual(
    weekDays(new Date(2027, 0, 1)).map((d) => d.date),
    [28, 29, 30, 31, 1, 2, 3],
  );
  assert.equal(weekDays(new Date(2027, 0, 1))[0].timestamp, new Date(2026, 11, 28).getTime());
});
test('month query uses local midnight and an exclusive next-month boundary', () => {
  const query = new URLSearchParams(monthRange('2026-12').slice(1));
  assert.equal(query.get('from'), new Date(2026, 11, 1).toISOString());
  assert.equal(query.get('to'), new Date(2027, 0, 1).toISOString());
});
test('weight chart requires real observations and handles flat, increasing and decreasing series', () => {
  const observations = (weights) => weights.map((weight) => ({ weight }));
  assert.deepEqual(weightChart([]), { segments: [], points: [] });
  assert.deepEqual(weightChart(observations([72])), { segments: [], points: [] });
  for (const weights of [
    [72, 72],
    [71, 72, 73],
    [73, 72, 71],
  ]) {
    const chart = weightChart(observations(weights));
    assert.equal(chart.segments.length, weights.length - 1);
    assert.ok(chart.points.every((p) => Number.isFinite(p.x) && Number.isFinite(p.y)));
    assert.ok(chart.segments.every((s) => Number.isFinite(s.angle) && s.length > 0));
    assert.equal(chart.points[0].x, 4);
    assert.equal(chart.points.at(-1).x, 296);
  }
  assert.equal(weightChart(observations(Array(20).fill(72))).points.length, 7);
});
test('completed counts ignore planned sets and wall time formatting clamps expiry', () => {
  assert.equal(completedSets({}), 0);
  assert.equal(
    completedSets({
      exercises: [
        { sets: [{ completed: true }, { completed: false }] },
        { sets: [{ completed: true }] },
      ],
    }),
    2,
  );
  assert.equal(clock(-1), '00:00');
  assert.equal(clock(48), '00:48');
  assert.equal(clock(3661), '61:01');
});
