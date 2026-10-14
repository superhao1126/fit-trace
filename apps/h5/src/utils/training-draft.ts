import type { TrainingExercise } from '@fit-trace/shared';
import { newTrainingClock, type TrainingClock } from './training-clock.ts';

export interface TrainingDraft {
  name: string;
  startedAt: string;
  exercises: TrainingExercise[];
  duration: string | number;
  clock?: TrainingClock;
  autoRest?: boolean;
}
export function trainingDraftKey(userId: string, templateId = ''): string {
  return `fit-trace:training-draft:${userId}:${templateId || 'free'}`;
}
export function readTrainingDraft(
  storage: Pick<Storage, 'getItem'>,
  key: string,
): TrainingDraft | null {
  try {
    const value = JSON.parse(storage.getItem(key) ?? 'null');
    if (
      !value ||
      typeof value.name !== 'string' ||
      value.name.length > 60 ||
      typeof value.startedAt !== 'string' ||
      !Number.isFinite(Date.parse(value.startedAt)) ||
      !Array.isArray(value.exercises) ||
      value.exercises.length > 20 ||
      !value.exercises.every(
        (e: TrainingExercise) =>
          e &&
          typeof e.exerciseId === 'string' &&
          typeof e.name === 'string' &&
          ['reps', 'seconds'].includes(e.unit) &&
          Number.isFinite(e.restSeconds) &&
          Array.isArray(e.sets) &&
          e.sets.length <= 20 &&
          e.sets.every(
            (s) =>
              s &&
              typeof s.completed === 'boolean' &&
              typeof s.reps === 'number' &&
              Number.isFinite(s.reps) &&
              typeof s.weightKg === 'number' &&
              Number.isFinite(s.weightKg),
          ),
      )
    )
      return null;
    return {
      name: value.name,
      startedAt: value.startedAt,
      exercises: value.exercises,
      duration:
        typeof value.duration === 'number' || typeof value.duration === 'string'
          ? value.duration
          : '',
      autoRest: value.autoRest !== false,
      clock:
        value.clock &&
        (value.clock.pausedAt === null ||
          (Number.isFinite(value.clock.pausedAt) &&
            value.clock.pausedAt >= Date.parse(value.startedAt))) &&
        Number.isFinite(value.clock.pausedMs) &&
        value.clock.pausedMs >= 0 &&
        Number.isFinite(value.clock.restUntil) &&
        value.clock.restUntil >= 0 &&
        (value.clock.restPausedSeconds === null ||
          (Number.isFinite(value.clock.restPausedSeconds) &&
            value.clock.restPausedSeconds >= 0 &&
            value.clock.restPausedSeconds <= 600))
          ? value.clock
          : newTrainingClock(),
    };
  } catch {
    return null;
  }
}
export function latestTrainingDraft(
  storage: Storage,
  userId: string,
): { templateId: string; draft: TrainingDraft } | null {
  const prefix = `fit-trace:training-draft:${userId}:`;
  let latest: { templateId: string; draft: TrainingDraft } | null = null;
  try {
    for (let i = 0; i < storage.length; i++) {
      const key = storage.key(i);
      if (!key?.startsWith(prefix)) continue;
      const draft = readTrainingDraft(storage, key);
      if (!draft || !draft.exercises.length) continue;
      if (!latest || Date.parse(draft.startedAt) > Date.parse(latest.draft.startedAt)) {
        const suffix = key.slice(prefix.length);
        latest = { templateId: suffix === 'free' ? '' : suffix, draft };
      }
    }
  } catch {
    return null;
  }
  return latest;
}
