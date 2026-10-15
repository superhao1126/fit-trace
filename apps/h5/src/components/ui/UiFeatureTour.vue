<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import UiButton from './UiButton.vue';
import {
  finishTour,
  placeTourCard,
  shouldShowTour,
  tourModuleForPath,
  tourSteps,
  type TourModule,
  type TourRect,
  type TourStep,
  type TourStatus,
} from '@/utils/feature-tour';

const auth = useAuthStore();
const route = useRoute();
const dialog = ref<HTMLDialogElement | null>(null);
const card = ref<HTMLElement | null>(null);
const open = ref(false);
const positioned = ref(false);
const index = ref(0);
const steps = ref<TourStep[]>([]);
const step = computed(() => steps.value[index.value]);
const focus = ref<TourRect>({ left: 0, top: 0, width: 0, height: 0 });
const viewport = ref({ width: window.innerWidth, height: window.innerHeight });
const placement = ref({ left: 16, top: 16, arrow: 30, placement: 'bottom' as 'top' | 'bottom' });
const maxHeight = ref(400);
let ownerId = '';
let module: TourModule | null = null;
let generation = 0;
let timer: ReturnType<typeof setTimeout> | undefined;
let animation = 0;
let targetObserver: ResizeObserver | undefined;
let contentObserver: MutationObserver | undefined;
let previousOverflow = '';
let previousFocus: HTMLElement | null = null;

function findTarget(item: TourStep): HTMLElement | undefined {
  return Array.from(
    document.querySelectorAll<HTMLElement>(`.app-shell [data-tour="${item.target}"]`),
  ).find((element) => {
    const bounds = element.getBoundingClientRect();
    return (
      bounds.width > 0 && bounds.height > 0 && getComputedStyle(element).visibility !== 'hidden'
    );
  });
}

function stop(): void {
  generation++;
  clearTimeout(timer);
  cancelAnimationFrame(animation);
  targetObserver?.disconnect();
  contentObserver?.disconnect();
  if (open.value) {
    open.value = false;
    dialog.value?.close();
    document.body.style.overflow = previousOverflow;
    if (previousFocus?.isConnected) previousFocus.focus({ preventScroll: true });
  }
  positioned.value = false;
}

function dismiss(status: TourStatus = 'skipped'): void {
  if (module && ownerId && open.value) finishTour(localStorage, ownerId, module, status);
  stop();
}

function position(): void {
  if (!open.value || !step.value || !card.value) return;
  const element = findTarget(step.value);
  if (!element) {
    void move(1);
    return;
  }
  const bounds = element.getBoundingClientRect();
  const visual = window.visualViewport;
  const shell = document.querySelector('.app-shell');
  const safeTop = shell ? Number.parseFloat(getComputedStyle(shell).paddingTop) || 0 : 0;
  const safeBottom = shell ? Number.parseFloat(getComputedStyle(shell).paddingBottom) || 0 : 0;
  const visible: TourRect = {
    left: visual?.offsetLeft ?? 0,
    top: (visual?.offsetTop ?? 0) + safeTop,
    width: visual?.width ?? window.innerWidth,
    height: Math.max(0, (visual?.height ?? window.innerHeight) - safeTop - safeBottom),
  };
  viewport.value = { width: window.innerWidth, height: window.innerHeight };
  maxHeight.value = Math.max(0, visible.height - 32);
  focus.value = {
    left: Math.max(0, bounds.left - 6),
    top: Math.max(0, bounds.top - 6),
    width: Math.min(window.innerWidth, bounds.right + 6) - Math.max(0, bounds.left - 6),
    height: Math.min(window.innerHeight, bounds.bottom + 6) - Math.max(0, bounds.top - 6),
  };
  placement.value = placeTourCard(
    { left: bounds.left, top: bounds.top, width: bounds.width, height: bounds.height },
    { width: card.value.offsetWidth, height: Math.min(card.value.offsetHeight, maxHeight.value) },
    visible,
    step.value.placement,
  );
  positioned.value = true;
}

function schedulePosition(): void {
  cancelAnimationFrame(animation);
  animation = requestAnimationFrame(position);
}

async function reveal(): Promise<void> {
  const element = step.value && findTarget(step.value);
  if (!element) return;
  positioned.value = false;
  const request = generation;
  element.scrollIntoView({ block: 'center', behavior: 'instant' });
  targetObserver?.disconnect();
  targetObserver?.observe(element);
  if (card.value) targetObserver?.observe(card.value);
  await nextTick();
  if (request !== generation || !open.value) return;
  position();
  schedulePosition();
}

async function move(direction: number): Promise<void> {
  let next = index.value + direction;
  while (next >= 0 && next < steps.value.length && !findTarget(steps.value[next]))
    next += direction;
  if (next < 0) return;
  if (next >= steps.value.length) {
    dismiss('completed');
    return;
  }
  index.value = next;
  await reveal();
}

async function start(): Promise<void> {
  // 路由或账号变化时，不把旧引导留在新页面上。
  if (open.value && module && ownerId) finishTour(localStorage, ownerId, module, 'skipped');
  stop();
  const nextModule = tourModuleForPath(route.path);
  const user = auth.user;
  if (!nextModule || !user || !shouldShowTour(localStorage, user.id, user.createdAt, nextModule))
    return;
  ownerId = user.id;
  module = nextModule;
  const request = generation;
  const candidates = tourSteps[nextModule];
  const deadline = Date.now() + 5000;
  function waitForPage(): void {
    if (request !== generation) return;
    if (document.querySelector('dialog[open], .t-popup--visible')) {
      if (Date.now() < deadline) timer = setTimeout(waitForPage, 200);
      return;
    }
    // 先等首个目标，避免接口尚未返回时只显示底部导航的介绍。
    if (!findTarget(candidates[0]) && Date.now() < deadline) {
      timer = setTimeout(waitForPage, 200);
      return;
    }
    steps.value = candidates.filter((item) => findTarget(item));
    if (!steps.value.length) return;
    index.value = 0;
    previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    previousFocus?.blur();
    previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    open.value = true;
    dialog.value?.showModal();
    const shell = document.querySelector('.app-shell__content');
    if (shell) contentObserver?.observe(shell, { childList: true, subtree: true });
    void reveal();
  }
  await nextTick();
  if (request === generation) timer = setTimeout(waitForPage, 250);
}

function keydown(event: KeyboardEvent): void {
  if (event.key === 'ArrowRight') {
    event.preventDefault();
    void move(1);
  } else if (event.key === 'ArrowLeft') {
    event.preventDefault();
    void move(-1);
  }
}

watch(() => [auth.user?.id, route.fullPath], start);
onMounted(() => {
  targetObserver = new ResizeObserver(schedulePosition);
  contentObserver = new MutationObserver(schedulePosition);
  window.addEventListener('resize', schedulePosition);
  window.addEventListener('scroll', schedulePosition, true);
  window.visualViewport?.addEventListener('resize', schedulePosition);
  window.visualViewport?.addEventListener('scroll', schedulePosition);
  void start();
});
onBeforeUnmount(() => {
  stop();
  window.removeEventListener('resize', schedulePosition);
  window.removeEventListener('scroll', schedulePosition, true);
  window.visualViewport?.removeEventListener('resize', schedulePosition);
  window.visualViewport?.removeEventListener('scroll', schedulePosition);
});
</script>

<template>
  <Teleport to="body">
    <dialog
      ref="dialog"
      class="feature-tour"
      data-feature-tour
      aria-labelledby="feature-tour-title"
      aria-describedby="feature-tour-description"
      @cancel.prevent="dismiss()"
      @keydown="keydown"
    >
      <template v-if="open && step">
        <svg
          class="feature-tour__mask"
          :viewBox="`0 0 ${viewport.width} ${viewport.height}`"
          preserveAspectRatio="none"
          aria-hidden="true"
        >
          <defs>
            <mask id="feature-tour-mask">
              <rect width="100%" height="100%" fill="white" />
              <rect
                :x="focus.left"
                :y="focus.top"
                :width="Math.max(0, focus.width)"
                :height="Math.max(0, focus.height)"
                rx="14"
                fill="black"
              />
            </mask>
          </defs>
          <rect
            width="100%"
            height="100%"
            fill="#0a120e"
            fill-opacity="0.56"
            mask="url(#feature-tour-mask)"
          />
          <rect
            :x="focus.left"
            :y="focus.top"
            :width="Math.max(0, focus.width)"
            :height="Math.max(0, focus.height)"
            rx="14"
            fill="none"
            stroke="white"
            stroke-opacity="0.65"
            stroke-width="1.5"
          />
        </svg>
        <section
          ref="card"
          class="feature-tour__card"
          :class="{ 'feature-tour__card--below': placement.placement === 'bottom' }"
          :style="{
            left: `${placement.left}px`,
            top: `${placement.top}px`,
            maxHeight: `${maxHeight}px`,
            visibility: positioned ? 'visible' : 'hidden',
            '--tour-arrow': `${placement.arrow}px`,
          }"
        >
          <button
            class="feature-tour__close"
            type="button"
            aria-label="关闭引导"
            @click="dismiss()"
          >
            ×
          </button>
          <div class="feature-tour__body" aria-live="polite" aria-atomic="true">
            <h2 id="feature-tour-title">{{ step.title }}</h2>
            <p id="feature-tour-description">{{ step.description }}</p>
          </div>
          <footer>
            <span class="feature-tour__count">{{ index + 1 }} / {{ steps.length }}</span>
            <UiButton kind="text" @click="dismiss()">跳过</UiButton>
            <UiButton v-if="index > 0" kind="text" @click="move(-1)">上一步</UiButton>
            <UiButton @click="move(1)">{{
              index === steps.length - 1 ? '知道了' : '下一步'
            }}</UiButton>
          </footer>
        </section>
      </template>
    </dialog>
  </Teleport>
</template>

<style scoped lang="scss">
.feature-tour {
  position: fixed;
  inset: 0;
  width: 100%;
  height: 100%;
  max-width: none;
  max-height: none;
  padding: 0;
  margin: 0;
  overflow: hidden;
  color: var(--ui-text-primary);
  background: transparent;
  border: 0;
  outline: none;
  &::backdrop {
    background: transparent;
  }
  &__mask {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    pointer-events: none;
  }
  &__card {
    position: absolute;
    display: flex;
    flex-direction: column;
    width: min(320px, calc(100vw - 32px));
    padding: 20px;
    background: var(--color-surface);
    border-radius: 16px;
    box-shadow: 0 8px 32px rgb(0 0 0 / 12%);
    &::after {
      position: absolute;
      bottom: -5px;
      left: var(--tour-arrow);
      width: 11px;
      height: 11px;
      content: '';
      background: var(--color-surface);
      transform: translateX(-50%) rotate(45deg);
    }
    &--below::after {
      top: -5px;
      bottom: auto;
    }
  }
  &__body {
    min-height: 0;
    overflow: auto;
  }
  h2 {
    padding-right: 16px;
    margin: 0 0 10px;
    font-size: 16px;
    line-height: 1.5;
    font-weight: 600;
  }
  p {
    margin: 0;
    font-size: 13px;
    line-height: 1.8;
    color: var(--color-text-secondary);
  }
  &__close {
    position: absolute;
    top: 3px;
    right: 3px;
    width: 44px;
    height: 44px;
    padding: 0;
    font: inherit;
    font-size: 24px;
    color: var(--color-text-tertiary);
    cursor: pointer;
    background: transparent;
    border: 0;
  }
  footer {
    display: flex;
    flex-shrink: 0;
    align-items: center;
    gap: 8px;
    margin-top: 16px;
  }
  &__count {
    margin-right: auto;
    font-size: 12px;
    color: var(--color-text-tertiary);
    white-space: nowrap;
  }
  footer :deep(.ui-button.t-button) {
    min-width: 44px;
    height: 44px;
    padding: 0 8px;
    font-size: 12px;
    border-radius: 9px;
  }
  footer :deep(.ui-button--text.t-button) {
    color: var(--color-text-secondary);
  }
  &__close:focus-visible {
    outline: 2px solid var(--ui-primary);
    outline-offset: -6px;
    border-radius: 12px;
  }
}
</style>
