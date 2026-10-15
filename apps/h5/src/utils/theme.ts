import { ref } from 'vue';

export type ThemeMode = 'system' | 'light' | 'dark';

const STORAGE_KEY = 'fittrace-theme';
const DARK_QUERY = '(prefers-color-scheme: dark)';

/** 当前实际生效的主题：图表等画在 canvas 上的内容需要跟着重绘 */
export const resolvedTheme = ref<'light' | 'dark'>('light');

export function getThemeMode(): ThemeMode {
  const stored = localStorage.getItem(STORAGE_KEY);
  return stored === 'light' || stored === 'dark' ? stored : 'system';
}

export function applyTheme(mode: ThemeMode = getThemeMode()): void {
  const prefersDark = window.matchMedia(DARK_QUERY).matches;
  const resolved = mode === 'system' ? (prefersDark ? 'dark' : 'light') : mode;
  document.documentElement.dataset.theme = resolved;
  resolvedTheme.value = resolved;
  document
    .querySelector('meta[name="theme-color"]')
    ?.setAttribute('content', resolved === 'dark' ? '#14181a' : '#f7f8f6');
}

export function setThemeMode(mode: ThemeMode): void {
  localStorage.setItem(STORAGE_KEY, mode);
  applyTheme(mode);
}

/** 跟随系统时，系统切换深浅色要实时同步 */
export function watchSystemTheme(): void {
  window.matchMedia(DARK_QUERY).addEventListener('change', () => {
    if (getThemeMode() === 'system') applyTheme('system');
  });
}
