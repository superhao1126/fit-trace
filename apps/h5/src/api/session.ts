import axios from 'axios';
import type { ApiResponse, AuthResult } from '@fit-trace/shared';
import { AuthSessionManager } from '@/utils/auth-session';

export const apiBaseURL = import.meta.env.VITE_API_BASE_URL ?? 'https://fitness.rungos.cn/api/v1';
// 续期独立发送，避免 401 拦截器递归调用自己。
const client = axios.create({ baseURL: apiBaseURL, timeout: 10_000 });

export const authSession = new AuthSessionManager(localStorage, {
  async refresh(refreshToken) {
    return (await client.post<ApiResponse<AuthResult>>('/auth/refresh', { refreshToken })).data
      .data;
  },
  async upgrade(accessToken) {
    return (
      await client.post<ApiResponse<AuthResult>>('/auth/session', null, {
        headers: { Authorization: `Bearer ${accessToken}` },
      })
    ).data.data;
  },
  async revoke(refreshToken) {
    await client.post('/auth/logout', { refreshToken });
  },
});
