export interface TrainingClock {
  pausedAt: number | null;
  pausedMs: number;
  restUntil: number;
  restPausedSeconds: number | null;
}
export function newTrainingClock(): TrainingClock {
  return { pausedAt: null, pausedMs: 0, restUntil: 0, restPausedSeconds: null };
}
export function elapsedMinutes(startedAt: string, clock: TrainingClock, now: number): number {
  return Math.max(
    1,
    Math.round(((clock.pausedAt ?? now) - Date.parse(startedAt) - clock.pausedMs) / 60000),
  );
}
export function restSeconds(clock: TrainingClock, now: number): number {
  return (
    clock.restPausedSeconds ??
    Math.max(0, Math.ceil((clock.restUntil - (clock.pausedAt ?? now)) / 1000))
  );
}
export function toggleTrainingPause(clock: TrainingClock, now: number): TrainingClock {
  if (clock.pausedAt === null) return { ...clock, pausedAt: now };
  const gap = Math.max(0, now - clock.pausedAt);
  return {
    ...clock,
    pausedAt: null,
    pausedMs: clock.pausedMs + gap,
    restUntil: clock.restUntil ? clock.restUntil + gap : 0,
  };
}
export function setRest(clock: TrainingClock, seconds: number, now: number): TrainingClock {
  return {
    ...clock,
    restUntil: seconds > 0 ? (clock.pausedAt ?? now) + seconds * 1000 : 0,
    restPausedSeconds: null,
  };
}
export function toggleRestPause(clock: TrainingClock, now: number): TrainingClock {
  return clock.restPausedSeconds === null
    ? { ...clock, restPausedSeconds: restSeconds(clock, now) }
    : setRest(clock, clock.restPausedSeconds, now);
}
