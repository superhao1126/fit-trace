import { shallowRef } from 'vue';

export type ToastKind = 'success' | 'warning' | 'error' | 'info';
export type ToastInput = string | { message: string; duration?: number };
export type ToastNotice = { id: number; kind: ToastKind; message: string };
type Timer = ReturnType<typeof setTimeout>;
type Scheduler = {
  schedule: (callback: () => void, delay: number) => Timer;
  cancel: (timer: Timer) => void;
};

export function createToastController(
  scheduler: Scheduler = {
    schedule: (callback, delay) => setTimeout(callback, delay),
    cancel: (timer) => clearTimeout(timer),
  },
) {
  const notice = shallowRef<ToastNotice | null>(null);
  let sequence = 0;
  let timer: Timer | undefined;
  function stopTimer() {
    if (timer !== undefined) scheduler.cancel(timer);
    timer = undefined;
  }
  function clear() {
    stopTimer();
    notice.value = null;
  }
  function show(kind: ToastKind, input: ToastInput) {
    const options = typeof input === 'string' ? { message: input } : input;
    const message = options.message.trim();
    if (!message) return;
    stopTimer();
    const id = ++sequence;
    notice.value = { id, kind, message };
    const baseDuration = kind === 'error' ? 4000 : kind === 'warning' ? 3200 : 2200;
    const duration =
      options.duration ?? Math.min(8000, Math.max(baseDuration, message.length * 70 + 1200));
    if (duration > 0) {
      timer = scheduler.schedule(() => {
        // 已进入事件队列的旧定时器也不能关闭后来出现的提示。
        if (notice.value?.id === id) clear();
      }, duration);
    }
  }
  return {
    notice,
    clear,
    success: (input: ToastInput) => show('success', input),
    warning: (input: ToastInput) => show('warning', input),
    error: (input: ToastInput) => show('error', input),
    info: (input: ToastInput) => show('info', input),
  };
}

export const uiToast = createToastController();
