import client from './client';
import type { ApiResponse, LocationData, TrajectoryPoint } from '../types';

export async function getLatestLocation(params: {
  elderId?: string;
  deviceId?: string;
}): Promise<LocationData> {
  const res = await client.get<ApiResponse<LocationData>>('/api/v1/location/latest', { params });
  return res.data.data;
}

export async function getTrajectory(params: {
  elderId?: string;
  deviceId?: string;
  start?: string;
  end?: string;
}): Promise<{ total: number; page: number; pageSize: number; list: TrajectoryPoint[] }> {
  const res = await client.get<ApiResponse<any>>('/api/v1/location/trajectory', { params });
  return res.data.data;
}
