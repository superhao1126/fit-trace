import type { TrainingSet } from '@fit-trace/shared';
export type EditableTrainingSet = TrainingSet & { edited?: boolean };
export function carryPreviousSets(
  current: EditableTrainingSet[],
  previous: TrainingSet[],
): TrainingSet[] {
  return current.map((set, i) =>
    set.completed || set.edited || !previous[i]
      ? set
      : { ...set, reps: previous[i].reps, weightKg: previous[i].weightKg, completed: false },
  );
}
