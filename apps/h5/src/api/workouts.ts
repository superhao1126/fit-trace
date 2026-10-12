import type { ApiListResponse, ApiResponse, WorkoutRecord, WorkoutType } from '@fit-trace/shared';
import { http } from './http';
import type { TrainingExercise } from '@fit-trace/shared';

export interface WorkoutInput {
  exercises?: TrainingExercise[];
  type: WorkoutType;
  name: string;
  startedAt?: string;
  durationMinutes: number;
  calories?: number;
  note?: string;
}

export interface WorkoutListParams {
  page?: number;
  pageSize?: number;
  from?: string;
  to?: string;
  type?: WorkoutType;
}

export async function getWorkouts(
  params: WorkoutListParams = {},
): Promise<ApiListResponse<WorkoutRecord>> {
  const response = await http.get<ApiListResponse<WorkoutRecord>>('/workouts', { params });
  return response.data;
}

export async function getWorkout(id: string): Promise<WorkoutRecord> {
  const response = await http.get<ApiResponse<WorkoutRecord>>(`/workouts/${id}`);
  return response.data.data;
}

export async function createWorkout(input: WorkoutInput): Promise<WorkoutRecord> {
  const response = await http.post<ApiResponse<WorkoutRecord>>('/workouts', input);
  return response.data.data;
}

export async function updateWorkout(
  id: string,
  input: Partial<WorkoutInput>,
): Promise<WorkoutRecord> {
  const response = await http.patch<ApiResponse<WorkoutRecord>>(`/workouts/${id}`, input);
  return response.data.data;
}

export async function deleteWorkout(id: string): Promise<void> {
  await http.delete(`/workouts/${id}`);
}
