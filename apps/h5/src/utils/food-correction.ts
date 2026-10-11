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
