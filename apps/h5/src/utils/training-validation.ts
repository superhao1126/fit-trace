import type { TrainingExercise } from '@fit-trace/shared';
export function trainingValidation(exercises: TrainingExercise[]): string | null {
  if (!exercises.length || exercises.length > 20) return '请选择 1 至 20 个训练动作';
  for (const exercise of exercises) {
    if (
      !Number.isInteger(exercise.restSeconds) ||
      exercise.restSeconds < 0 ||
      exercise.restSeconds > 600
    )
      return '休息时间需为 0 至 600 秒的整数';
    if (!exercise.sets.length || exercise.sets.length > 20) return '每个动作需有 1 至 20 组';
    for (const set of exercise.sets) {
      if (!Number.isInteger(set.reps) || set.reps < 1 || set.reps > 3600)
        return '次数或秒数需为 1 至 3600 的整数';
      if (
        !Number.isFinite(set.weightKg) ||
        set.weightKg < 0 ||
        set.weightKg > 1000 ||
        Math.abs(set.weightKg * 100 - Math.round(set.weightKg * 100)) > 0.000001
      )
        return '重量需为 0 至 1000 kg，最多两位小数';
    }
  }
  return null;
}
export function completedTraining(exercises: TrainingExercise[]): TrainingExercise[] {
  return exercises
    .map((e) => ({
      ...e,
      sets: e.sets
        .filter((s) => s.completed)
        .map((s) => ({ reps: s.reps, weightKg: s.weightKg, completed: s.completed })),
    }))
    .filter((e) => e.sets.length);
}
