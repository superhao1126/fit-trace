import { Capacitor, SystemBars, SystemBarsStyle } from '@capacitor/core';
import { App } from '@capacitor/app';
import { watch } from 'vue';
import { resolvedTheme } from './theme';
import router from '@/router';
import { authSession } from '@/api/session';

export function setupNativeShell(): void {
  if (!Capacitor.isNativePlatform()) return;
  document.documentElement.classList.add('native-app');
  document.addEventListener('contextmenu', (event) => {
    if (
      !(event.target instanceof Element) ||
      !event.target.closest('input, textarea, [contenteditable="true"]')
    ) {
      event.preventDefault();
    }
  });
  document.addEventListener('dragstart', (event) => event.preventDefault());
  if (Capacitor.getPlatform() !== 'android') return;

  void App.addListener('appStateChange', ({ isActive }) => {
    if (isActive) void authSession.restore();
  }).catch((error: unknown) => console.warn('应用会话恢复初始化失败', error));

  void App.addListener('backButton', ({ canGoBack }) => {
    if (canGoBack) {
      router.back();
    } else {
      void App.minimizeApp().catch((error: unknown) => console.warn('应用返回失败', error));
    }
  }).catch((error: unknown) => console.warn('系统返回键初始化失败', error));

  watch(
    resolvedTheme,
    (theme) => {
      void SystemBars.setStyle({
        style: theme === 'dark' ? SystemBarsStyle.Dark : SystemBarsStyle.Light,
      }).catch((error: unknown) => console.warn('系统栏主题设置失败', error));
    },
    { immediate: true },
  );
}
