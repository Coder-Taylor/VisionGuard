import client from './client';
import type { ApiResponse, PaginatedData, NotificationData } from '../types';

export async function listNotifications(params: {
  page?: number;
  pageSize?: number;
  readStatus?: string;
} = {}): Promise<PaginatedData<NotificationData>> {
  const res = await client.get<ApiResponse<PaginatedData<NotificationData>>>('/api/v1/notifications', { params });
  return res.data.data;
}

export async function markAsRead(messageIds: string[]): Promise<void> {
  await client.put('/api/v1/notifications/read', { message_ids: messageIds });
}

export async function markAllRead(): Promise<void> {
  await client.put('/api/v1/notifications/read-all');
}
