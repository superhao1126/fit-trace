import assert from 'node:assert/strict';
import { test } from 'node:test';
import { mealDraftKey, readMealDraft } from '../src/utils/meal-draft.ts';
import {
  latestTrainingDraft,
  readTrainingDraft,
  trainingDraftKey,
} from '../src/utils/training-draft.ts';
import { carryPreviousSets } from '../src/utils/previous-training.ts';
import { completedTraining } from '../src/utils/training-validation.ts';
function storage(values = {}) {
  const map = new Map(Object.entries(values));
  return {
    get length() {
      return map.size;
    },
    key: (i) => [...map.keys()][i] ?? null,
    getItem: (key) => map.get(key) ?? null,
  };
}
const draft = {
  type: 'LUNCH',
  recordedAt: '2026-10-13T04:00:00Z',
  note: '',
  foods: [{ key: 1, name: '鸡胸肉', amount: '', calories: '', aiGenerated: false }],
  imageUrl: '',
  suggestions: [
    { key: 2, name: '米饭', amount: '100g', calories: 0, aiGenerated: true, selected: true },
  ],
  recognitionProvider: 'mock',
  entryMode: 'photo',
  savedAt: '2026-10-13T04:01:00Z',
};
test('meal draft restores incomplete fields, unknown values and unconfirmed AI edits', () => {
  const key = mealDraftKey('one', null);
  const s = storage({ [key]: JSON.stringify(draft) });
  assert.deepEqual(readMealDraft(s, key), draft);
  assert.equal(readMealDraft(s, mealDraftKey('two', null)), null);
  assert.equal(readMealDraft(s, mealDraftKey('one', 'edit-id')), null);
});
test('broken drafts and unavailable storage are safely ignored', () => {
  assert.equal(readMealDraft(storage({ bad: '{' }), 'bad'), null);
  assert.equal(
    readMealDraft(storage({ bad: JSON.stringify({ ...draft, recordedAt: 'not a date' }) }), 'bad'),
    null,
  );
  assert.equal(
    readMealDraft(
      {
        getItem() {
          throw Error('blocked');
        },
      },
      'bad',
    ),
    null,
  );
  assert.equal(readTrainingDraft(storage({ bad: '{}' }), 'bad'), null);
});
const training = {
  name: '训练',
  startedAt: '2026-10-13T05:00:00Z',
  duration: '',
  exercises: [
    {
      exerciseId: 'squat',
      name: '深蹲',
      unit: 'reps',
      restSeconds: 60,
      sets: [{ reps: 0, weightKg: 0, completed: false }],
    },
  ],
};
test('training drafts retain partially entered sets and home finds only current owner drafts', () => {
  const s = storage({
    [trainingDraftKey('one', 'preset')]: JSON.stringify(training),
    [trainingDraftKey('two')]: JSON.stringify({ ...training, startedAt: '2026-10-13T06:00:00Z' }),
  });
  assert.equal(
    readTrainingDraft(s, trainingDraftKey('one', 'preset')).exercises[0].sets[0].reps,
    0,
  );
  assert.equal(latestTrainingDraft(s, 'one').templateId, 'preset');
  assert.equal(latestTrainingDraft(s, 'missing'), null);
  assert.equal(latestTrainingDraft(s, 'two').templateId, '');
});
test('previous values preserve completed or edited sets and never mark a new set complete', () => {
  const current = [
    { reps: 10, weightKg: 0, completed: false },
    { reps: 8, weightKg: 24, completed: false, edited: true },
    { reps: 9, weightKg: 20, completed: true },
    { reps: 12, weightKg: 5, completed: false },
  ];
  const previous = current
    .slice(0, 3)
    .map((s) => ({ ...s, reps: 11, weightKg: 30, completed: true }));
  const result = carryPreviousSets(current, previous);
  assert.deepEqual(result[0], { reps: 11, weightKg: 30, completed: false });
  for (const i of [1, 2, 3]) assert.equal(result[i], current[i]);
  assert.equal(current[0].weightKg, 0);
  assert.deepEqual(
    completedTraining([
      { ...training.exercises[0], sets: [{ ...result[0], completed: true, edited: true }] },
    ])[0].sets,
    [{ reps: 11, weightKg: 30, completed: true }],
  );
});
