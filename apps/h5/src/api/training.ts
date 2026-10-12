import type { ApiResponse, TrainingTemplate, TrainingPlanDay } from '@fit-trace/shared';
import { http } from './http';
export async function getTrainingTemplates() {
  return (await http.get<ApiResponse<TrainingTemplate[]>>('/training/templates')).data.data;
}
export async function saveTrainingTemplate(template: Omit<TrainingTemplate, 'id'>, id?: string) {
  return (
    id
      ? await http.patch<ApiResponse<TrainingTemplate>>(`/training/templates/${id}`, template)
      : await http.post<ApiResponse<TrainingTemplate>>('/training/templates', template)
  ).data.data;
}
export async function getTrainingPlan() {
  return (await http.get<ApiResponse<TrainingPlanDay[]>>('/training/plan')).data.data;
}
export async function saveTrainingPlan(days: TrainingPlanDay[]) {
  return (await http.put<ApiResponse<TrainingPlanDay[]>>('/training/plan', { days })).data.data;
}
