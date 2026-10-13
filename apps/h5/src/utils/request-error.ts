import axios from 'axios';
import { uiToast as ToastPlugin } from '../components/ui/toast';

const notifiedErrors = new WeakSet<object>();
let lastMessage = '';
let lastNotifiedAt = 0;

/** 请求层已提示的错误，页面只处理状态恢复，避免重复弹出。 */
export function showRequestError(error: unknown, fallback = '请求失败，请稍后重试'): void {
  if (axios.isCancel(error)) return;
  if (typeof error === 'object' && error !== null) {
    if (notifiedErrors.has(error)) return;
    notifiedErrors.add(error);
  }

  let message = fallback;
  if (axios.isAxiosError(error)) {
    const serverMessage: unknown = error.response?.data?.message;
    if (typeof serverMessage === 'string' && serverMessage.trim()) {
      message = serverMessage;
    } else if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') {
      message = '请求超时，请稍后重试';
    } else if (!error.response) {
      message = '网络连接异常，请检查网络后重试';
    }
  } else if (error instanceof Error && error.message) {
    message = error.message;
  }

  const now = Date.now();
  if (message === lastMessage && now - lastNotifiedAt < 2000) return;
  lastMessage = message;
  lastNotifiedAt = now;
  ToastPlugin.error(message);
}
