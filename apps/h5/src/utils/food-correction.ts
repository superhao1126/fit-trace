interface CorrectableFood {
  name: string;
  amount: string;
  calories: string | number;
  aiGenerated: boolean;
}

/** Changing an AI dish invalidates the portion and energy estimated for the old dish. */
export function correctFoodName(food: CorrectableFood, name: string): void {
  if (food.aiGenerated && food.name.trim() !== name.trim()) {
    food.amount = '';
    food.calories = '';
    food.aiGenerated = false;
  }
  food.name = name;
}

/** Do not overwrite edits made while the estimate request was in flight. */
export function applyFoodEstimate(
  food: CorrectableFood,
  snapshot: Pick<CorrectableFood, 'name' | 'amount' | 'calories'>,
  estimate: { estimatedAmount: string | null; estimatedCalories: number | null },
): boolean {
  if (
    food.name !== snapshot.name ||
    food.amount !== snapshot.amount ||
    food.calories !== snapshot.calories ||
    !estimate.estimatedAmount?.trim() ||
    estimate.estimatedCalories === null ||
    !Number.isFinite(estimate.estimatedCalories) ||
    estimate.estimatedCalories < 0
  )
    return false;
  food.amount = estimate.estimatedAmount;
  food.calories = estimate.estimatedCalories;
  food.aiGenerated = true;
  return true;
}
