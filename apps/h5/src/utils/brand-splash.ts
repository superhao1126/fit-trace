import { ref } from 'vue';
import { applyTheme } from './theme';

export const brandSplashVisible = ref(
  document.documentElement.classList.contains('brand-launching'),
);

/** 首个路由渲染完成后移除静态开屏，不设置最短展示时间。 */
export function finishBrandLaunch(): void {
  requestAnimationFrame(() => {
    document.getElementById('brand-splash')?.remove();
    document.documentElement.classList.remove('brand-launching');
    brandSplashVisible.value = false;
    applyTheme();
  });
}
