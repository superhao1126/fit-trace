import type { TrainingExercise, WorkoutType } from '@fit-trace/shared';
export interface Draft {
  owner: string;
  name: string;
  startedAt: string;
  exercises: TrainingExercise[];
  restEndsAt: number | null;
  workoutId?: string;
  durationMinutes?: number;
  note?: string;
  type?: WorkoutType;
  clientRequestId?: string;
}
export function draftKey(owner: string) {
  return `fit-trace:training:v1:${owner}`;
}
export function restRemaining(endsAt: number | null, now = Date.now()) {
  return endsAt === null ? 0 : Math.max(0, Math.ceil((endsAt - now) / 1000));
}
export function validateDraft(draft: Draft): string | null {
  if (!draft.name.trim() || draft.name.length > 60) return '请输入 1–60 字训练名称';
  if (!draft.exercises.length || draft.exercises.length > 20) return '请选择 1–20 个动作';
  for (const exercise of draft.exercises) {
    if (
      !Number.isInteger(exercise.restSeconds) ||
      exercise.restSeconds < 0 ||
      exercise.restSeconds > 600
    )
      return '休息时间需为 0–600 秒';
    if (!exercise.sets.length || exercise.sets.length > 20) return '每个动作需要 1–20 组';
    for (const set of exercise.sets) {
      if (
        !Number.isInteger(set.reps) ||
        set.reps < 1 ||
        set.reps > 3600 ||
        !Number.isFinite(set.weightKg) ||
        set.weightKg < 0 ||
        set.weightKg > 1000 ||
        Math.abs(Math.round(set.weightKg * 100) - set.weightKg * 100) > 0.00001
      )
        return '请检查次数/秒数和重量（最多两位小数）';
    }
  }
  if (
    draft.workoutId &&
    (!Number.isInteger(draft.durationMinutes) ||
      draft.durationMinutes! < 1 ||
      draft.durationMinutes! > 1440)
  )
    return '时长需为 1–1440 分钟';
  if (!draft.exercises.some((e) => e.sets.some((s) => s.completed))) return '请至少完成一组再保存';
  return null;
}

/** Validate persisted state before rendering nested inputs; preserve the raw key for explicit discard. */
export function readDraft(raw: unknown, owner: string): Draft | null {
  if (!raw || typeof raw !== 'object') return null;
  const d = raw as Draft;
  if (
    d.owner !== owner ||
    typeof d.name !== 'string' ||
    !Number.isFinite(Date.parse(d.startedAt)) ||
    !Array.isArray(d.exercises) ||
    d.exercises.length > 20 ||
    !(d.restEndsAt === null || Number.isFinite(d.restEndsAt))
  )
    return null;
  if (
    d.exercises.some(
      (e) =>
        !e ||
        typeof e.exerciseId !== 'string' ||
        typeof e.name !== 'string' ||
        !['reps', 'seconds'].includes(e.unit) ||
        !Array.isArray(e.sets) ||
        e.sets.length > 20 ||
        e.sets.some(
          (s) =>
            !s ||
            typeof s.completed !== 'boolean' ||
            !['number', 'string'].includes(typeof s.reps) ||
            !['number', 'string'].includes(typeof s.weightKg),
        ),
    )
  )
    return null;
  return d;
}
