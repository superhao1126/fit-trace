import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  elapsedMinutes,
  newTrainingClock,
  restSeconds,
  setRest,
  toggleRestPause,
  toggleTrainingPause,
} from '../src/utils/training-clock.ts';
import { readTrainingDraft } from '../src/utils/training-draft.ts';

test('training pause freezes elapsed and rest across reload, resume deducts paused time', () => {
  const start = Date.parse('2026-10-14T04:00:00Z');
  let clock = setRest(newTrainingClock(), 60, start + 120000);
  clock = toggleTrainingPause(clock, start + 130000);
  assert.equal(restSeconds(clock, start + 600000), 50);
  assert.equal(elapsedMinutes(new Date(start).toISOString(), clock, start + 600000), 2);
  const draft = readTrainingDraft(
    {
      getItem: () =>
        JSON.stringify({
          name: '训练',
          startedAt: new Date(start).toISOString(),
          exercises: [],
          duration: '',
          clock,
          autoRest: false,
        }),
    },
    'key',
  );
  assert.deepEqual(draft.clock, clock);
  assert.equal(draft.autoRest, false);
  clock = toggleTrainingPause(draft.clock, start + 600000);
  assert.equal(restSeconds(clock, start + 610000), 40);
  assert.equal(elapsedMinutes(new Date(start).toISOString(), clock, start + 660000), 3);
});
test('rest can pause independently; expiry follows wall time and skip fully clears pause', () => {
  let clock = setRest(newTrainingClock(), 60, 100000);
  clock = toggleRestPause(clock, 110000);
  assert.equal(restSeconds(clock, 300000), 50);
  clock = toggleRestPause(clock, 300000);
  assert.equal(restSeconds(clock, 360000), 0);
  clock = toggleRestPause(setRest(clock, 60, 400000), 410000);
  clock = setRest(clock, 0, 420000);
  assert.equal(restSeconds(clock, 420000), 0);
  assert.equal(clock.restPausedSeconds, null);
});
