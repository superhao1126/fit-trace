import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  finishTour,
  placeTourCard,
  resetTours,
  shouldShowTour,
  tourModuleForPath,
} from '../src/utils/feature-tour.ts';

function storage() {
  const data = new Map();
  return { getItem: (key) => data.get(key) ?? null, setItem: (key, value) => data.set(key, value) };
}
const recent = '2026-10-15T08:00:00+08:00';

test('tour state is isolated by account and module; explicit replay enables existing accounts', () => {
  const db = storage();
  assert.equal(shouldShowTour(db, 'new', recent, 'home'), true);
  finishTour(db, 'new', 'home', 'skipped');
  assert.equal(shouldShowTour(db, 'new', recent, 'home'), false);
  assert.equal(shouldShowTour(db, 'other', recent, 'home'), true);
  assert.equal(shouldShowTour(db, 'new', recent, 'training'), true);
  finishTour(db, 'new', 'training', 'completed');
  assert.equal(shouldShowTour(db, 'new', recent, 'training'), false);
  assert.equal(shouldShowTour(db, 'existing', '2026-10-14T08:00:00+08:00', 'home'), false);
  resetTours(db, 'existing');
  assert.equal(shouldShowTour(db, 'existing', '2026-10-14T08:00:00+08:00', 'home'), true);
  resetTours(db, 'new');
  assert.equal(shouldShowTour(db, 'new', recent, 'training'), true);
  assert.equal(shouldShowTour(db, '', recent, 'home'), false);
});

test('corrupt or inaccessible storage cannot break records or closing a tour', () => {
  const corrupt = { getItem: () => '[', setItem: () => {} };
  assert.equal(shouldShowTour(corrupt, 'owner', recent, 'meal'), true);
  const blocked = {
    getItem: () => {
      throw Error('blocked');
    },
    setItem: () => {
      throw Error('quota');
    },
  };
  assert.doesNotThrow(() => finishTour(blocked, 'owner', 'meal', 'skipped'));
  assert.doesNotThrow(() => resetTours(blocked, 'owner'));
});

test('automatic tours exclude editing existing records and arbitrary routes', () => {
  assert.equal(tourModuleForPath('/dashboard'), 'home');
  assert.equal(tourModuleForPath('/training/session'), 'training');
  assert.equal(tourModuleForPath('/training/templates'), 'templates');
  assert.equal(tourModuleForPath('/meals/create'), 'meal');
  assert.equal(tourModuleForPath('/meals/record/edit'), null);
  assert.equal(tourModuleForPath('/training/templates/create'), null);
});

test('card flips above a low target and stays within a 320px viewport', () => {
  const result = placeTourCard(
    { left: 260, top: 650, width: 44, height: 44 },
    { width: 288, height: 180 },
    { left: 0, top: 0, width: 320, height: 740 },
    'bottom',
  );
  assert.equal(result.placement, 'top');
  assert.equal(result.left, 16);
  assert.ok(result.top + 180 < 650);
  assert.ok(result.arrow >= 22 && result.arrow <= 266);
});

test('visual viewport offsets and keyboard height bound the card', () => {
  const result = placeTourCard(
    { left: 10, top: 310, width: 340, height: 48 },
    { width: 320, height: 180 },
    { left: 0, top: 100, width: 390, height: 360 },
    'bottom',
  );
  assert.ok(result.left >= 16);
  assert.ok(result.top >= 116 && result.top + 180 <= 444);
});
