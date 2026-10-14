import type { ApiResponse, FoodRecognitionResult } from '@fit-trace/shared';
import { http } from './http';

export async function analyzeMealText(description: string): Promise<FoodRecognitionResult> {
  const response = await http.post<ApiResponse<FoodRecognitionResult>>(
    '/ai/food-text',
    { description },
    { timeout: 60_000 },
  );
  return response.data.data;
}

export async function recognizeFood(
  imageUrl: string,
  hint?: string,
): Promise<FoodRecognitionResult> {
  const response = await http.post<ApiResponse<FoodRecognitionResult>>(
    '/ai/food-recognition',
    { imageUrl, ...(hint ? { hint } : {}) },
    { timeout: 60_000 },
  );
  return response.data.data;
}
