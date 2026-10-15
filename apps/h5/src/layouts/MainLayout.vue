<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { Button } from 'tdesign-mobile-vue';
import UiIcon, { type UiIconName } from '@/components/ui/UiIcon.vue';
import UiFeatureTour from '@/components/ui/UiFeatureTour.vue';
import { useRoute, useRouter } from 'vue-router';

const route = useRoute();
const router = useRouter();
const TAB_ROOTS = ['/dashboard', '/trends', '/archive', '/profile'];
/** 四个主 tab 做缓存：切回来时内容还在，只在后台静默刷新 */
const CACHED_VIEWS = ['DashboardView', 'TrendsView', 'ArchiveView', 'ProfileView'];
const navigation = [
  { path: '/dashboard', label: '首页', icon: 'home' as UiIconName },
  { path: '/trends', label: '趋势', icon: 'trends' as UiIconName },
  { path: '/archive', label: '档案', icon: 'archive' as UiIconName },
  { path: '/profile', label: '我的', icon: 'user' as UiIconName },
];

const activePath = computed(() => {
  if (route.path.startsWith('/meals')) return '/dashboard';
  if (route.path.startsWith('/workouts') || route.path.startsWith('/photos')) return '/dashboard';
  if (route.path === '/body/create' || route.path.includes('/edit')) return '/dashboard';
  if (/^\/body\/[^/]+$/.test(route.path)) return '/archive';
  if (route.path.startsWith('/body/history') || route.path.startsWith('/archive'))
    return '/archive';
  if (route.path.startsWith('/record')) return '/dashboard';
  return navigation.find((item) => route.path.startsWith(item.path))?.path ?? '/dashboard';
});
/** 底部标签栏只用于四个主页面，记录操作由各页的入口承担。 */
const showChrome = computed(() => route.meta.tabBar === true);

/**
 * 同级 tab 之间瞬切（苹果的标签栏就是瞬切，只有高亮在动）；
 * 进入子页面（详情、表单）才做方向性转场，暗示层级关系。
 */
const transitionName = ref('route-push');
let lastTab: string | null = TAB_ROOTS.includes(route.path) ? route.path : null;
watch(
  () => route.path,
  (path) => {
    const tab = TAB_ROOTS.includes(path) ? path : null;
    transitionName.value = tab && lastTab && tab !== lastTab ? 'route-none' : 'route-push';
    lastTab = tab;
  },
  { flush: 'pre' },
);

async function navigate(path: string): Promise<void> {
  if (route.path !== path) await router.push(path);
}
</script>

<template>
  <div class="app-shell" :class="{ 'app-shell--with-nav': showChrome }">
    <div class="app-shell__content">
      <RouterView v-slot="{ Component }">
        <Transition :name="transitionName" mode="out-in">
          <KeepAlive :include="CACHED_VIEWS">
            <component :is="Component" />
          </KeepAlive>
        </Transition>
      </RouterView>
    </div>
    <nav v-if="showChrome" class="bottom-nav" aria-label="主导航" data-tour="navigation">
      <Button
        v-for="item in navigation"
        :key="item.path"
        class="bottom-nav__item"
        :class="{ active: activePath === item.path }"
        :aria-current="activePath === item.path ? 'page' : undefined"
        variant="text"
        size="large"
        @click="navigate(item.path)"
      >
        <span class="bottom-nav__icon" aria-hidden="true"><UiIcon :name="item.icon" /></span>
        <span>{{ item.label }}</span>
      </Button>
    </nav>
    <UiFeatureTour />
  </div>
</template>

<style scoped lang="scss">
.app-shell {
  --app-content-bottom-space: calc(var(--spacing-lg) + var(--app-safe-area-bottom));

  width: min(100%, 560px);
  min-height: 100vh;
  min-height: 100dvh;
  margin: 0 auto;
  padding: var(--app-safe-area-top) var(--app-safe-area-right) 0 var(--app-safe-area-left);
  background: var(--color-background);
  box-shadow: 0 0 0 1px var(--color-border);

  &--with-nav {
    --color-background: var(--ui-background);
    --color-text-primary: var(--ui-text-primary);
    --color-text-secondary: var(--ui-text-secondary);
    --color-text-tertiary: var(--ui-text-tertiary);
    --color-primary: var(--ui-primary);
    --color-accent-text: var(--ui-primary);
    --color-primary-light: var(--ui-primary-light);
    --color-border: var(--ui-border);
    --td-brand-color: var(--ui-primary);
    --app-content-bottom-space: var(--bottom-nav-space);
  }

  &__content {
    min-height: calc(100vh - var(--app-safe-area-top));
    min-height: calc(100dvh - var(--app-safe-area-top));
    /* 与页面最小高度共用预留空间，避免空页面被底部控件撑出滚动。 */
    padding-bottom: var(--app-content-bottom-space);
  }
}

.bottom-nav {
  position: fixed;
  z-index: 20;
  left: 50%;
  bottom: 0;
  display: grid;
  width: min(100%, 560px);
  grid-template-columns: repeat(4, minmax(0, 1fr));
  padding: 6px 8px calc(6px + var(--app-safe-area-bottom));
  background: var(--color-surface-translucent);
  border-top: 0;
  transform: translateX(-50%);
  backdrop-filter: blur(16px);

  &__item.t-button {
    min-width: 0;
    height: 56px;
    padding: 4px;
    color: var(--color-text-tertiary);
    background: transparent;
    border: 0;
    border-radius: 8px;

    :deep(.t-button__content) {
      display: flex;
      flex-direction: column;
      gap: 4px;
      font-size: 12px;
      font-weight: 400;
    }

    &.active {
      color: var(--color-accent-text);
      :deep(.t-button__content) {
        font-weight: 600;
      }
    }
  }

  &__icon {
    display: grid;
    width: 24px;
    height: 24px;
    place-items: center;
    font-size: 22px;
    line-height: 1;
  }
}
</style>
