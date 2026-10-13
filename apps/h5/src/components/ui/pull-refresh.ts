export type PullRefreshPhase = 'idle' | 'tracking' | 'pulling' | 'armed';

export const MINIMUM_REFRESH_DURATION = 600;

/** Data updates immediately; only completion of the indicator waits. */
export async function refreshWithMinimumDuration(
  refresh: () => Promise<void>,
  timing = {
    now: () => performance.now(),
    wait: (duration: number) => new Promise<void>((resolve) => setTimeout(resolve, duration)),
  },
): Promise<void> {
  const startedAt = timing.now();
  try {
    await refresh();
  } finally {
    const remaining = MINIMUM_REFRESH_DURATION - (timing.now() - startedAt);
    if (remaining > 0) await timing.wait(remaining);
  }
}

/** The page stays still; only the indicator follows a damped vertical gesture. */
export class PullRefreshGesture {
  phase: PullRefreshPhase = 'idle';
  distance = 0;
  readonly threshold = 72;
  readonly maxDistance = 88;
  private x = 0;
  private y = 0;

  start(x: number, y: number, eligible: boolean): void {
    this.cancel();
    if (!eligible) return;
    this.x = x;
    this.y = y;
    this.phase = 'tracking';
  }

  move(x: number, y: number, eligible: boolean): boolean {
    if (this.phase === 'idle') return false;
    if (!eligible) {
      this.cancel();
      return false;
    }
    const dx = Math.abs(x - this.x);
    const dy = y - this.y;
    if (this.phase === 'tracking') {
      if (Math.max(dx, Math.abs(dy)) < 8) return false;
      // Once a gesture belongs to horizontal navigation, never steal it later.
      if (dy <= 0 || dx >= dy) {
        this.cancel();
        return false;
      }
    }
    this.distance = Math.min(this.maxDistance, Math.max(0, dy * 0.5));
    this.phase = this.distance >= this.threshold ? 'armed' : 'pulling';
    return true;
  }

  end(): boolean {
    const refresh = this.phase === 'armed';
    this.cancel();
    return refresh;
  }

  cancel(): void {
    this.phase = 'idle';
    this.distance = 0;
  }
}
