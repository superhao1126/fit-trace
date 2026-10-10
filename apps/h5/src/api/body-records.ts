import type {
  ApiListResponse,
  ApiResponse,
  BodyRecord,
  BodyTrendData,
  BodyTrendDays,
} from '@fit-trace/shared';
import { http } from './http';

export interface BodyRecordInput {
  weight: number;
  bodyFat?: number;
  waist?: number;
  chest?: number;
  hip?: number;
  recordedAt?: string;
  note?: string;
}

export interface BodyRecordListParams {
  page?: number;
  pageSize?: number;
  from?: string;
  to?: string;
  recordedAtOrder?: 'asc' | 'desc';
}

export async function createBodyRecord(input: BodyRecordInput): Promise<BodyRecord> {
  const response = await http.post<ApiResponse<BodyRecord>>('/body-records', input);
  return response.data.data;
}

export async function getBodyRecords(
  params: BodyRecordListParams = {},
): Promise<ApiListResponse<BodyRecord>> {
  const response = await http.get<ApiListResponse<BodyRecord>>('/body-records', { params });
  return response.data;
}

export async function getLatestBodyRecord(): Promise<BodyRecord | null> {
  const response = await http.get<ApiResponse<BodyRecord | null>>('/body-records/latest');
  return response.data.data;
}

export async function getBodyTrends(days: BodyTrendDays): Promise<BodyTrendData> {
  const response = await http.get<ApiResponse<BodyTrendData>>('/body-records/trends', {
    params: { days },
  });
  return response.data.data;
}

export async function getBodyRecord(id: string): Promise<BodyRecord> {
  const response = await http.get<ApiResponse<BodyRecord>>(`/body-records/${id}`);
  return response.data.data;
}

export async function updateBodyRecord(
  id: string,
  input: Partial<BodyRecordInput>,
): Promise<BodyRecord> {
  const response = await http.patch<ApiResponse<BodyRecord>>(`/body-records/${id}`, input);
  return response.data.data;
}

export async function deleteBodyRecord(id: string): Promise<void> {
  await http.delete(`/body-records/${id}`);
}
