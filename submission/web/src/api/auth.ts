import client from './client';
import type { ApiResponse, AuthData, LoginReq, RegisterReq, ChangePasswordReq, UserProfileData, UpdateProfileReq } from '../types';

export async function login(req: LoginReq): Promise<AuthData> {
  const res = await client.post('/api/v1/auth/login', req);
  // 后端返回 flat JSON（对齐 Android LoginRawResponse）
  return res.data as unknown as AuthData;
}

export async function register(req: RegisterReq): Promise<ApiResponse<null>> {
  const res = await client.post('/api/v1/auth/register', req);
  return res.data;
}

export async function refreshToken(token: string): Promise<AuthData> {
  const res = await client.post('/api/v1/auth/refresh', { refresh_token: token });
  return res.data as unknown as AuthData;
}

export async function logout(refreshToken: string): Promise<void> {
  await client.post('/api/v1/auth/logout', { refresh_token: refreshToken });
}

export async function changePassword(req: ChangePasswordReq): Promise<void> {
  await client.post('/api/v1/auth/change-password', req);
}

export async function getProfile(): Promise<UserProfileData> {
  const res = await client.get<ApiResponse<UserProfileData>>('/api/v1/user/profile');
  return res.data.data;
}

export async function updateProfile(req: UpdateProfileReq): Promise<void> {
  await client.put('/api/v1/user/profile', req);
}
