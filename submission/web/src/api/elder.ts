import client from './client';
import type { ApiResponse, PaginatedData, ElderData, ElderDetailData, DashboardData, CreateElderReq, UpdateElderReq } from '../types';

export async function getDashboard(): Promise<DashboardData> {
  const res = await client.get<ApiResponse<DashboardData>>('/api/v1/dashboard');
  return res.data.data;
}

export async function listMyElders(): Promise<ElderData[]> {
  const res = await client.get<ApiResponse<PaginatedData<ElderData>>>('/api/v1/elders');
  return res.data.data.list;
}

export async function getElderDetail(elderId: string): Promise<ElderDetailData> {
  const res = await client.get<ApiResponse<ElderDetailData>>(`/api/v1/elder/${elderId}`);
  return res.data.data;
}

export async function createElder(req: CreateElderReq): Promise<void> {
  await client.post('/api/v1/elder', req);
}

export async function updateElder(elderId: string, req: UpdateElderReq): Promise<void> {
  await client.put(`/api/v1/elder/${elderId}`, req);
}

export async function deleteElder(elderId: string): Promise<void> {
  await client.delete(`/api/v1/elder/${elderId}`);
}
