import axios, { CanceledError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios';
import { type AuthSessionManager, isUnauthorized } from '../utils/auth-session.ts';

interface SessionRequest extends InternalAxiosRequestConfig {
  sessionGeneration?: number;
  sessionRetried?: boolean;
}

function publicAuthRequest(config: SessionRequest): boolean {
  // Only explicitly public endpoints bypass authentication. New /auth routes
  // (such as WeChat account binding) must carry the current account's token.
  const path = (config.url ?? '').split('?')[0].replace(/\/$/, '');
  return new Set([
    '/auth/register',
    '/auth/login',
    '/auth/login/email',
    '/auth/email-code',
    '/auth/email-captcha/config',
    '/auth/refresh',
    '/auth/logout',
    '/auth/captcha',
    '/auth/reset-password',
    '/auth/reset-password/verify-code',
    '/auth/wechat/login',
    '/auth/miniprogram/email-code',
    '/auth/miniprogram/login',
    '/auth/device-credentials/login-options',
    '/auth/device-credentials/login-verify',
  ]).has(path);
}

export function installSessionInterceptor(
  client: AxiosInstance,
  session: AuthSessionManager,
): void {
  client.interceptors.request.use(async (config: SessionRequest) => {
    if (publicAuthRequest(config)) return config;
    const generation = config.sessionGeneration ?? session.generation;
    config.sessionGeneration = generation;
    if (generation !== session.generation) throw new CanceledError('账号已变更');
    const hadSession = Boolean(session.snapshot);
    const token = await session.accessToken();
    if (generation !== session.generation && session.snapshot)
      throw new CanceledError('账号已变更');
    if (hadSession && !token) throw new CanceledError('登录会话已结束');
    if (token) config.headers.Authorization = `Bearer ${token}`;
    else delete config.headers.Authorization;
    return config;
  });

  async function recover(error: unknown) {
    if (!axios.isAxiosError(error) || !isUnauthorized(error)) throw error;
    const config = error.config as SessionRequest | undefined;
    if (!config || publicAuthRequest(config)) throw error;
    if (config.sessionGeneration !== session.generation) {
      throw new CanceledError('账号已变更');
    }
    if (config.sessionRetried || !session.snapshot) {
      session.clear();
      throw error;
    }
    config.sessionRetried = true;
    const sentToken = config.headers.Authorization;
    const currentToken = session.snapshot.accessToken;
    // 并发请求的旧 401 到达时，可以直接使用已经续好的令牌。
    const token =
      sentToken !== `Bearer ${currentToken}`
        ? await session.accessToken()
        : await session.accessToken(true);
    if (config.sessionGeneration !== session.generation) {
      throw new CanceledError('账号已变更');
    }
    if (!token) throw new CanceledError('登录会话已结束');
    return client.request(config);
  }

  client.interceptors.response.use((response) => {
    if (response.data?.code === 'UNAUTHORIZED') {
      return recover(
        new axios.AxiosError('登录失效', undefined, response.config, undefined, response),
      );
    }
    return response;
  }, recover);
}
