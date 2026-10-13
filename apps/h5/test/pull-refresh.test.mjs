import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  PullRefreshGesture,
  refreshWithMinimumDuration,
} from '../src/components/ui/pull-refresh.ts';

test('short pulls cancel, armed release triggers only once', () => {
  const gesture = new PullRefreshGesture();
  gesture.start(100, 100, true);
  assert.equal(gesture.move(102, 200, true), true);
  assert.equal(gesture.end(), false);
  gesture.start(100, 100, true);
  gesture.move(101, 244, true);
  assert.equal(gesture.end(), true);
  assert.equal(gesture.end(), false);
});

test('horizontal navigation and upward scrolling cannot become a refresh later', () => {
  for (const [x, y] of [
    [140, 105],
    [100, 80],
  ]) {
    const gesture = new PullRefreshGesture();
    gesture.start(100, 100, true);
    assert.equal(gesture.move(x, y, true), false);
    assert.equal(gesture.move(100, 400, true), false);
    assert.equal(gesture.end(), false);
  }
});

test('reversing an armed pull below threshold cancels refresh', () => {
  const gesture = new PullRefreshGesture();
  gesture.start(100, 100, true);
  gesture.move(100, 400, true);
  assert.equal(gesture.phase, 'armed');
  gesture.move(100, 150, true);
  assert.equal(gesture.phase, 'pulling');
  assert.equal(gesture.end(), false);
});

test('leaving the top, opening a modal or becoming busy cancels the gesture', () => {
  const gesture = new PullRefreshGesture();
  gesture.start(100, 100, false);
  assert.equal(gesture.move(100, 400, true), false);
  gesture.start(100, 100, true);
  gesture.move(100, 400, true);
  assert.equal(gesture.move(100, 450, false), false);
  assert.equal(gesture.end(), false);
});

test('indicator travel is damped and bounded; touchcancel never triggers refresh', () => {
  const gesture = new PullRefreshGesture();
  gesture.start(100, 100, true);
  gesture.move(100, 120, true);
  assert.equal(gesture.distance, 10);
  gesture.move(100, 1000, true);
  assert.equal(gesture.distance, 88);
  gesture.cancel();
  assert.equal(gesture.end(), false);
  assert.equal(gesture.distance, 0);
});

test('fast refresh updates data immediately while the indicator holds until 600ms', async () => {
  let now = 0;
  let data = 'old';
  let complete = false;
  let release;
  const waits = [];
  const refresh = refreshWithMinimumDuration(
    async () => {
      now = 80;
      data = 'new';
    },
    {
      now: () => now,
      wait: (duration) => {
        waits.push(duration);
        return new Promise((resolve) => {
          release = resolve;
        });
      },
    },
  ).then(() => {
    complete = true;
  });
  await Promise.resolve();
  assert.equal(data, 'new');
  assert.equal(complete, false);
  assert.deepEqual(waits, [520]);
  release();
  await refresh;
  assert.equal(complete, true);
});

test('slow refresh waits for the real request without adding another 600ms', async () => {
  let now = 0;
  let done;
  let complete = false;
  const request = new Promise((resolve) => {
    done = resolve;
  });
  const refresh = refreshWithMinimumDuration(() => request, {
    now: () => now,
    wait: () => {
      throw new Error('slow requests must not add a wait');
    },
  }).then(() => {
    complete = true;
  });
  await Promise.resolve();
  now = 1000;
  assert.equal(complete, false);
  done();
  await refresh;
  assert.equal(complete, true);
});

test('fast failure respects the minimum time and preserves the original error', async () => {
  let now = 0;
  const failure = new Error('offline');
  const waits = [];
  await assert.rejects(
    refreshWithMinimumDuration(
      async () => {
        now = 30;
        throw failure;
      },
      {
        now: () => now,
        wait: async (duration) => {
          waits.push(duration);
        },
      },
    ),
    (error) => error === failure,
  );
  assert.deepEqual(waits, [570]);
});
