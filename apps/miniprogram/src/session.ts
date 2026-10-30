import { ref } from 'vue';
import type { AuthResult, ApiResponse, EmailCodeResult } from '@fit-trace/shared';
const key = 'fit-trace:miniprogram:session:v2';
function readSession(): AuthResult | null {
  const cached = uni.getStorageSync(key);
  if (
    !cached ||
    typeof cached !== 'object' ||
    typeof cached.accessToken !== 'string' ||
    typeof cached.refreshToken !== 'string' ||
    typeof cached.user?.id !== 'string' ||
    !Number.isFinite(Date.parse(cached.accessTokenExpiresAt))
  )
    return null;
  return cached;
}
export const session = ref<AuthResult | null>(readSession());
let refreshing: Promise<void> | null = null;
let generation = 0;
export class RequestError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
  }
}
async function send<T>(
  path: string,
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  data?: unknown,
  token?: string,
): Promise<T> {
  const base = import.meta.env.VITE_API_BASE_URL;
  if (!base || !base.startsWith('https://')) throw new Error('请配置 HTTPS API 域名');
  const result = await uni.request({
    url: base.replace(/\/$/, '') + path,
    method,
    data: data as Record<string, unknown>,
    timeout: 15000,
    header: token ? { Authorization: `Bearer ${token}` } : {},
  });
  const body = result.data as ApiResponse<T>;
  if (result.statusCode < 200 || result.statusCode >= 300)
    throw new RequestError(body.message || '请求失败，请重试', result.statusCode);
  return body.data;
}
function store(result: AuthResult | null) {
  session.value = result;
  if (result) uni.setStorageSync(key, result);
  else uni.removeStorageSync(key);
}
export async function restore(force = false): Promise<void> {
  if (!session.value) return;
  if (!force && Date.parse(session.value.accessTokenExpiresAt) > Date.now() + 30000) return;
  if (refreshing) return refreshing;
  const current = generation;
  const token = session.value.refreshToken;
  refreshing = send<AuthResult>('/auth/refresh', 'POST', { refreshToken: token })
    .then((result) => {
      if (current === generation) store(result);
    })
    .catch((error) => {
      if (error instanceof RequestError && error.status === 401 && current === generation) {
        generation++;
        store(null);
      }
      throw error;
    })
    .finally(() => {
      refreshing = null;
    });
  return refreshing;
}
export async function request<T>(
  path: string,
  method: 'GET' | 'POST' | 'PUT' | 'DELETE' = 'GET',
  data?: unknown,
): Promise<T> {
  await restore();
  const current = generation;
  const owner = session.value?.user.id;
  if (!owner) throw new Error('请先登录');
  try {
    const result = await send<T>(path, method, data, session.value!.accessToken);
    if (current !== generation) throw new Error('账号已切换，请重试');
    return result;
  } catch (error) {
    if (!(error instanceof RequestError) || error.status !== 401 || current !== generation)
      throw error;
    await restore(true);
    if (current !== generation || !session.value) throw new Error('请重新登录');
    const result = await send<T>(path, method, data, session.value.accessToken);
    if (current !== generation) throw new Error('账号已切换，请重试');
    return result;
  }
}
export async function sendEmailCode(email: string): Promise<EmailCodeResult> {
  return send<EmailCodeResult>('/auth/miniprogram/email-code', 'POST', {
    email: email.trim().toLowerCase(),
  });
}
export async function login(email: string, emailCode: string) {
  const current = ++generation;
  const result = await send<AuthResult>('/auth/miniprogram/login', 'POST', {
    email: email.trim().toLowerCase(),
    emailCode,
    acceptedTerms: true,
  });
  if (current === generation) store(result);
}
export async function logout() {
  if (session.value)
    await send('/auth/logout', 'POST', { refreshToken: session.value.refreshToken });
  generation++;
  store(null);
}
export function notify(error: unknown) {
  uni.showToast({
    title: error instanceof Error ? error.message : '操作失败，请重试',
    icon: 'none',
  });
}
