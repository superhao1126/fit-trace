import type { ApiResponse, DashboardOverview } from '@fit-trace/shared';
import { http } from './http';
import { getLocalTimezoneOffset } from './insights';

export async function getDashboard(date?: string): Promise<DashboardOverview> {
  const response = await http.get<ApiResponse<DashboardOverview>>('/dashboard', {
    params: { tzOffset: getLocalTimezoneOffset(), date },
  });
  return response.data.data;
}
