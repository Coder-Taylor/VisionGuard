import axios, { AxiosError } from 'axios';
import type { ApiResponse, AuthData } from '../types';

const BASE_URL = import.meta.env.DEV ? '/vg' : 'http://47.94.146.53/vg';

const client = axios.create({
  baseURL: BASE_URL,
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
});

// Token 存储
const TOKEN_KEY = 'vg_access_token';
const REFRESH_KEY = 'vg_refresh_token';

export function getAccessToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setTokens(access: string, refresh: string) {
  localStorage.setItem(TOKEN_KEY, access);
  localStorage.setItem(REFRESH_KEY, refresh);
}

export function clearTokens() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REFRESH_KEY);
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_KEY);
}

// 请求拦截器 — 自动附带 JWT
client.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 响应拦截器 — 401 自动刷新
let isRefreshing = false;
let refreshSubscribers: ((token: string) => void)[] = [];

function subscribeTokenRefresh(cb: (token: string) => void) {
  refreshSubscribers.push(cb);
}

function onTokenRefreshed(token: string) {
  refreshSubscribers.forEach((cb) => cb(token));
  refreshSubscribers = [];
}

client.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as any;
    if (!originalRequest || error.response?.status !== 401 || originalRequest._retry) {
      return Promise.reject(error);
    }

    // 不拦截 refresh 自身/login 的 401
    const url = originalRequest.url || '';
    if (url.includes('/auth/refresh') || url.includes('/auth/login')) {
      return Promise.reject(error);
    }

    if (!isRefreshing) {
      isRefreshing = true;
      try {
        const refreshToken = getRefreshToken();
        if (!refreshToken) throw new Error('no refresh token');

        const res = await axios.post<ApiResponse<AuthData>>(
          `${BASE_URL}/api/v1/auth/refresh`,
          { refresh_token: refreshToken },
        );

        // 后端 refresh 返回 flat JSON（对齐 Android LoginRawResponse）
        const data = res.data as unknown as AuthData;
        const newAccess = data.accessToken || (data as any).access_token;
        const newRefresh = data.refreshToken || (data as any).refresh_token;

        if (newAccess) {
          setTokens(newAccess, newRefresh || refreshToken);
          onTokenRefreshed(newAccess);
          originalRequest.headers.Authorization = `Bearer ${newAccess}`;
          return client(originalRequest);
        }
      } catch {
        clearTokens();
        window.location.href = '/login';
        return Promise.reject(error);
      } finally {
        isRefreshing = false;
      }
    }

    // 并发请求排队等刷新
    return new Promise((resolve) => {
      subscribeTokenRefresh((token: string) => {
        originalRequest.headers.Authorization = `Bearer ${token}`;
        resolve(client(originalRequest));
      });
    });
  },
);

export default client;
