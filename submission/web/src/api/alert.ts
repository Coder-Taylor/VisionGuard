import client from './client';
import type { ApiResponse, PaginatedData, AlertData, AlertDetailData, AlertStatisticsData, UpdateAlertStatusReq } from '../types';

interface ListAlertsParams {
  elderId?: string;
  alertType?: string;
  page?: number;
  pageSize?: number;
}

export async function listAlerts(params: ListAlertsParams = {}): Promise<PaginatedData<AlertData>> {
  const res = await client.get<ApiResponse<PaginatedData<AlertData>>>('/api/v1/alerts', { params });
  return res.data.data;
}

export async function getAlertDetail(alertId: string): Promise<AlertDetailData> {
  const res = await client.get<ApiResponse<AlertDetailData>>(`/api/v1/alert/${alertId}`);
  return res.data.data;
}

export async function updateAlertStatus(alertId: string, req: UpdateAlertStatusReq): Promise<void> {
  await client.put(`/api/v1/alert/${alertId}/status`, req);
}

export async function resolveAlert(alertId: string, resolution: string): Promise<void> {
  await client.post(`/api/v1/alert/${alertId}/resolve`, { resolution });
}

export async function getAlertStatistics(): Promise<AlertStatisticsData> {
  const res = await client.get<ApiResponse<AlertStatisticsData>>('/api/v1/alert/statistics');
  return res.data.data;
}
