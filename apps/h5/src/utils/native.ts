import { Capacitor, SystemBars, SystemBarsStyle } from '@capacitor/core';
import { App } from '@capacitor/app';
import { watch } from 'vue';
import { brandSplashVisible } from './brand-splash';
import { resolvedTheme } from './theme';
import router from '@/router';
import { authSession } from '@/api/session';

export function setupNativeShell(): void {
  if (!Capacitor.isNativePlatform()) return;
  document.documentElement.classList.add('native-app', 'brand-launching');
  brandSplashVisible.value = true;
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', '#26735b');
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
    const tour = document.querySelector<HTMLDialogElement>('dialog[data-feature-tour][open]');
    if (tour) {
      tour.dispatchEvent(new Event('cancel', { cancelable: true }));
      return;
    }
    if (canGoBack) {
      router.back();
    } else {
      void App.minimizeApp().catch((error: unknown) => console.warn('应用返回失败', error));
    }
  }).catch((error: unknown) => console.warn('系统返回键初始化失败', error));

  watch(
    [resolvedTheme, brandSplashVisible],
    ([theme, launching]) => {
      void SystemBars.setStyle({
        style: launching || theme === 'dark' ? SystemBarsStyle.Dark : SystemBarsStyle.Light,
      }).catch((error: unknown) => console.warn('系统栏主题设置失败', error));
    },
    { immediate: true },
  );
}
