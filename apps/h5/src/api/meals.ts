import type {
  ApiListResponse,
  ApiResponse,
  MealFavorite,
  MealRecord,
  MealType,
} from '@fit-trace/shared';
import { http } from './http';

export interface FoodItemInput {
  name: string;
  amount?: string;
  calories?: number;
  aiGenerated?: boolean;
}

export interface MealInput {
  type: MealType;
  recordedAt?: string;
  note?: string;
  imageUrl?: string;
  foods: FoodItemInput[];
}

export async function getMeals(
  params: {
    page?: number;
    pageSize?: number;
    type?: MealType;
    from?: string;
    to?: string;
  } = {},
): Promise<ApiListResponse<MealRecord>> {
  const response = await http.get<ApiListResponse<MealRecord>>('/meals', { params });
  return response.data;
}

export async function getMeal(id: string): Promise<MealRecord> {
  const response = await http.get<ApiResponse<MealRecord>>(`/meals/${id}`);
  return response.data.data;
}

export async function createMeal(input: MealInput): Promise<MealRecord> {
  const response = await http.post<ApiResponse<MealRecord>>('/meals', input);
  return response.data.data;
}

export async function updateMeal(id: string, input: MealInput): Promise<MealRecord> {
  const response = await http.patch<ApiResponse<MealRecord>>(`/meals/${id}`, input);
  return response.data.data;
}

export async function deleteMeal(id: string): Promise<void> {
  await http.delete(`/meals/${id}`);
}

export async function getMealFavorites(): Promise<MealFavorite[]> {
  return (await http.get<ApiResponse<MealFavorite[]>>('/meals/favorites')).data.data;
}
export async function createMealFavorite(
  name: string,
  foods: FoodItemInput[],
): Promise<MealFavorite> {
  return (await http.post<ApiResponse<MealFavorite>>('/meals/favorites', { name, foods })).data
    .data;
}
export async function deleteMealFavorite(id: string): Promise<void> {
  await http.delete(`/meals/favorites/${id}`);
}
