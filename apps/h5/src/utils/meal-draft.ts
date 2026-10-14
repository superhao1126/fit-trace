import type { MealType } from '@fit-trace/shared';
export interface DraftFood {
  key: number;
  name: string;
  amount: string;
  calories: string | number;
  aiGenerated: boolean;
  sourceImageUrl?: string;
  sourceName?: string;
}
export interface MealDraft {
  type: MealType;
  recordedAt: string;
  note: string;
  foods: DraftFood[];
  imageUrl: string;
  suggestions: (DraftFood & { selected: boolean })[];
  recognitionProvider?: string;
  entryMode: string;
  savedAt: string;
  description?: string;
}
export function mealDraftKey(userId: string, mealId: string | null): string {
  return `fit-trace:meal-draft:${userId}:${mealId || 'new'}`;
}
function validFood(food: DraftFood): boolean {
  return Boolean(
    food &&
    Number.isInteger(food.key) &&
    typeof food.name === 'string' &&
    typeof food.amount === 'string' &&
    (typeof food.calories === 'string' || typeof food.calories === 'number') &&
    typeof food.aiGenerated === 'boolean' &&
    (food.sourceName === undefined || typeof food.sourceName === 'string') &&
    (food.sourceImageUrl === undefined || typeof food.sourceImageUrl === 'string'),
  );
}
export function readMealDraft(storage: Pick<Storage, 'getItem'>, key: string): MealDraft | null {
  try {
    const value = JSON.parse(storage.getItem(key) ?? 'null');
    if (
      !value ||
      !['BREAKFAST', 'LUNCH', 'DINNER', 'SNACK'].includes(value.type) ||
      typeof value.recordedAt !== 'string' ||
      !Number.isFinite(Date.parse(value.recordedAt)) ||
      typeof value.savedAt !== 'string' ||
      !Number.isFinite(Date.parse(value.savedAt)) ||
      typeof value.note !== 'string' ||
      typeof value.imageUrl !== 'string' ||
      !Array.isArray(value.foods) ||
      value.foods.length > 50 ||
      !value.foods.every(validFood) ||
      !Array.isArray(value.suggestions) ||
      value.suggestions.length > 50 ||
      !value.suggestions.every(
        (food: DraftFood & { selected: boolean }) =>
          validFood(food) && typeof food.selected === 'boolean',
      )
    )
      return null;
    return value as MealDraft;
  } catch {
    return null;
  }
}
