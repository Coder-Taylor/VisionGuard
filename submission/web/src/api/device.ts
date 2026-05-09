import client from './client';
import type { ApiResponse, SearchDeviceData } from '../types';

export async function searchDevice(deviceId: string): Promise<SearchDeviceData> {
  const res = await client.get<ApiResponse<SearchDeviceData>>(`/api/v1/device/${deviceId}/search`);
  return res.data.data;
}

export async function initiateBinding(deviceId: string, elderId: string): Promise<void> {
  await client.post('/api/v1/binding/initiate', { deviceId, elderId });
}

export async function unbindDevice(deviceId: string): Promise<void> {
  await client.post('/api/v1/binding/unbind', { deviceId });
}
