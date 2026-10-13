<script setup lang="ts">
import {
  computed,
  onActivated,
  onBeforeUnmount,
  onDeactivated,
  onMounted,
  reactive,
  ref,
  watch,
} from 'vue';
import UiBrandMark from './UiBrandMark.vue';
import { PullRefreshGesture } from './pull-refresh';
import { uiToast } from './toast';

const props = withDefaults(defineProps<{ refresh: () => Promise<void>; disabled?: boolean }>(), {
  disabled: false,
});
const root = ref<HTMLElement | null>(null);
const gesture = reactive(new PullRefreshGesture());
const refreshing = ref(false);
const visible = ref(false);
const offset = ref(0);
const active = ref(true);
let touchId: number | null = null;
let attached = false;
const progress = computed(() => Math.min(1, gesture.distance / gesture.threshold));
const status = computed(() =>
  refreshing.value ? '正在刷新' : gesture.phase === 'armed' ? '松手刷新' : '下拉刷新',
);

function canRefresh(): boolean {
  return (
    active.value &&
    !props.disabled &&
    !refreshing.value &&
    ![...document.querySelectorAll('[aria-modal="true"], dialog[open]')].some(
      (panel) => panel.getClientRects().length > 0,
    )
  );
}

function eligible(): boolean {
  return canRefresh() && (document.scrollingElement?.scrollTop ?? window.scrollY) <= 0;
}

function excluded(target: EventTarget | null): boolean {
  if (!(target instanceof Element)) return true;
  if (
    target.closest('input, textarea, select, [contenteditable="true"], [data-pull-refresh-ignore]')
  )
    return true;
  for (
    let element: Element | null = target;
    element && element !== root.value;
    element = element.parentElement
  ) {
    const style = getComputedStyle(element);
    if (
      (element.scrollWidth > element.clientWidth && /auto|scroll/.test(style.overflowX)) ||
      (element.scrollHeight > element.clientHeight && /auto|scroll/.test(style.overflowY))
    )
      return true;
  }
  return false;
}

function settle(): void {
  gesture.cancel();
  touchId = null;
  offset.value = 0;
  visible.value = false;
}

function start(event: TouchEvent): void {
  if (refreshing.value) return;
  if (event.touches.length !== 1 || !eligible() || excluded(event.target)) {
    settle();
    return;
  }
  const touch = event.touches[0];
  touchId = touch.identifier;
  gesture.start(touch.clientX, touch.clientY, true);
}

function move(event: TouchEvent): void {
  if (touchId === null) return;
  const touch = [...event.touches].find((item) => item.identifier === touchId);
  if (event.touches.length !== 1 || !touch || !event.cancelable) {
    settle();
    return;
  }
  if (!gesture.move(touch.clientX, touch.clientY, eligible())) {
    if (gesture.phase === 'idle') settle();
    return;
  }
  event.preventDefault();
  offset.value = gesture.distance;
  visible.value = gesture.distance > 0;
}

async function runRefresh(fromKeyboard = false): Promise<void> {
  if (!canRefresh() || (!fromKeyboard && !eligible())) {
    settle();
    return;
  }
  refreshing.value = true;
  visible.value = true;
  offset.value = 56;
  try {
    await props.refresh();
  } catch {
    if (active.value) uiToast.error('刷新失败，当前内容已保留，请稍后重试');
  } finally {
    refreshing.value = false;
    settle();
  }
}

function end(): void {
  if (touchId === null) return;
  touchId = null;
  const shouldRefresh = gesture.end();
  if (shouldRefresh) void runRefresh();
  else settle();
}

function cancel(): void {
  if (!refreshing.value) settle();
}

function attach(): void {
  active.value = true;
  if (attached || !root.value) return;
  root.value.addEventListener('touchstart', start, { passive: true });
  root.value.addEventListener('touchmove', move, { passive: false });
  root.value.addEventListener('touchend', end);
  root.value.addEventListener('touchcancel', cancel);
  attached = true;
}
function detach(): void {
  active.value = false;
  root.value?.removeEventListener('touchstart', start);
  root.value?.removeEventListener('touchmove', move);
  root.value?.removeEventListener('touchend', end);
  root.value?.removeEventListener('touchcancel', cancel);
  attached = false;
  settle();
}
watch(
  () => props.disabled,
  (disabled) => {
    if (disabled && !refreshing.value) settle();
  },
);
onMounted(attach);
onActivated(attach);
onDeactivated(detach);
onBeforeUnmount(detach);
</script>

<template>
  <div ref="root" class="ui-pull-refresh" :aria-busy="refreshing">
    <slot />
    <button
      class="ui-pull-refresh__keyboard"
      :disabled="disabled || refreshing"
      @click="runRefresh(true)"
    >
      刷新当前页面
    </button>
    <div class="ui-pull-refresh__position">
      <div
        class="ui-pull-refresh__indicator"
        :class="{
          'is-visible': visible,
          'is-pulling': gesture.phase === 'pulling' || gesture.phase === 'armed',
          'is-refreshing': refreshing,
        }"
        :style="{ transform: `translateY(${offset}px)` }"
        aria-hidden="true"
      >
        <svg class="ui-pull-refresh__ring" width="40" height="40" viewBox="0 0 40 40" fill="none">
          <circle cx="20" cy="20" r="18" stroke="var(--ui-primary-light)" stroke-width="2" />
          <circle
            cx="20"
            cy="20"
            r="18"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            pathLength="100"
            :stroke-dasharray="`${refreshing ? 76 : Math.max(1, progress * 100)} 100`"
          />
        </svg>
        <UiBrandMark :size="22" :animated="refreshing" />
      </div>
    </div>
    <span class="ui-pull-refresh__status" role="status" aria-live="polite">{{
      visible ? status : ''
    }}</span>
  </div>
</template>

<style scoped lang="scss">
.ui-pull-refresh {
  position: relative;
}
.ui-pull-refresh__position {
  position: absolute;
  z-index: 25;
  top: 64px;
  left: 50%;
  width: 48px;
  margin-left: -24px;
  pointer-events: none;
}
.ui-pull-refresh__indicator {
  display: grid;
  width: 48px;
  height: 48px;
  place-items: center;
  border: 1px solid var(--ui-border);
  border-radius: 50%;
  background: var(--color-surface);
  color: var(--ui-primary);
  box-shadow: 0 3px 10px rgb(16 40 28 / 12%);
  opacity: 0;
  transition:
    transform 180ms var(--ease-standard),
    opacity 180ms var(--ease-standard);
  &.is-visible {
    opacity: 1;
  }
  &.is-pulling {
    transition: opacity 140ms var(--ease-standard);
  }
}
.ui-pull-refresh__ring {
  position: absolute;
  transform: rotate(-90deg);
}
.is-refreshing .ui-pull-refresh__ring {
  animation: pull-refresh-ring 900ms linear infinite;
}
.ui-pull-refresh__status,
.ui-pull-refresh__keyboard {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}
.ui-pull-refresh__keyboard:focus-visible {
  z-index: 26;
  top: 64px;
  left: 50%;
  width: auto;
  height: auto;
  padding: 8px 12px;
  border: 1px solid var(--ui-primary);
  border-radius: 12px;
  background: var(--color-surface);
  color: var(--ui-primary);
  clip-path: none;
  transform: translateX(-50%);
}
@keyframes pull-refresh-ring {
  to {
    transform: rotate(270deg);
  }
}
@media (prefers-reduced-motion: reduce) {
  .ui-pull-refresh__indicator {
    transition: none;
  }
  .is-refreshing .ui-pull-refresh__ring {
    animation: none;
  }
}
</style>

<style lang="scss">
/* Prevent the browser's own refresh from competing with the active page gesture. */
html:has(.ui-pull-refresh) {
  overscroll-behavior-y: contain;
}
</style>
