import assert from 'node:assert/strict';
import { test } from 'node:test';
import { draftKey, restRemaining, validateDraft, readDraft } from '../src/draft.ts';
test('rest timer corrects elapsed background time and clamps expiry', () => {
  assert.equal(restRemaining(65000, 1000), 64);
  assert.equal(restRemaining(65000, 64001), 1);
  assert.equal(restRemaining(65000, 90000), 0);
  assert.equal(restRemaining(null, 1000), 0);
});
test('draft storage isolates accounts and validates completion and numeric entries', () => {
  assert.notEqual(draftKey('alice'), draftKey('bob'));
  const d = {
    owner: 'alice',
    name: '训练',
    startedAt: new Date().toISOString(),
    restEndsAt: null,
    exercises: [
      {
        exerciseId: 'plank',
        unit: 'seconds',
        restSeconds: 60,
        sets: [{ reps: 30, weightKg: 0, completed: false }],
      },
    ],
  };
  assert.ok(validateDraft(d));
  d.exercises[0].sets[0].completed = true;
  assert.equal(validateDraft(d), null);
  d.exercises[0].sets[0].reps = NaN;
  assert.ok(validateDraft(d));
});

test('persisted draft rejects cross-account and malformed nested content', () => {
  const raw = {
    owner: 'alice',
    name: '训练',
    startedAt: new Date().toISOString(),
    exercises: [],
    restEndsAt: null,
  };
  assert.equal(readDraft(raw, 'alice'), raw);
  assert.equal(readDraft(raw, 'bob'), null);
  assert.equal(readDraft({ ...raw, exercises: [{ sets: null }] }, 'alice'), null);
  assert.equal(readDraft({ ...raw, startedAt: 'invalid' }, 'alice'), null);
});
