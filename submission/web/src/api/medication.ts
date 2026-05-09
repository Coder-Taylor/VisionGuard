import client from './client';
import type { ApiResponse, MedicationPlanData, CreateMedicationPlanReq } from '../types';

export async function listPlans(elderId: string): Promise<MedicationPlanData[]> {
  const res = await client.get<ApiResponse<MedicationPlanData[]>>(`/api/v1/medication/plans/${elderId}`);
  return res.data.data;
}

export async function createPlan(req: CreateMedicationPlanReq): Promise<void> {
  await client.post('/api/v1/medication/plan', req);
}

export async function deletePlan(planId: string): Promise<void> {
  await client.delete(`/api/v1/medication/plan/${planId}`);
}
