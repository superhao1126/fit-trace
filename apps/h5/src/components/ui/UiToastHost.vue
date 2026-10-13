<script setup lang="ts">
import { uiToast } from './toast';
const { notice } = uiToast;
</script>
<template>
  <Teleport to="body">
    <Transition name="ui-toast" appear>
      <div
        v-if="notice"
        class="ui-toast"
        :class="`ui-toast--${notice.kind}`"
        :role="notice.kind === 'error' ? 'alert' : 'status'"
        :aria-live="notice.kind === 'error' ? 'assertive' : 'polite'"
        aria-atomic="true"
      >
        <span class="ui-toast__icon">
          <svg
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <path v-if="notice.kind === 'success'" d="m5 12 4 4L19 6" />
            <template v-else>
              <path d="M12 6v7" />
              <path d="M12 17v.01" />
            </template>
          </svg>
        </span>
        <span class="ui-toast__message">{{ notice.message }}</span>
      </div>
    </Transition>
  </Teleport>
</template>
<style scoped lang="scss">
.ui-toast {
  --notice-color: var(--ui-primary);
  --notice-background: var(--ui-primary-light);
  position: fixed;
  z-index: 30010;
  top: calc(var(--app-safe-area-top, 0px) + 64px);
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: flex-start;
  gap: 12px;
  width: min(480px, calc(100% - 32px));
  padding: 14px;
  border: 1px solid var(--ui-border);
  border-radius: 16px;
  background: var(--color-surface);
  color: var(--ui-text-primary);
  box-shadow: 0 8px 28px rgb(0 0 0 / 12%);
  pointer-events: none;
  &--warning {
    --notice-color: var(--ui-warning-text);
    --notice-background: var(--ui-warning-background);
  }
  &--error {
    --notice-color: var(--color-danger-text);
    --notice-background: var(--ui-error-background);
  }
}
.ui-toast__icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  border-radius: 50%;
  color: var(--notice-color);
  background: var(--notice-background);
}
.ui-toast__message {
  align-self: center;
  min-width: 0;
  font-size: 14px;
  line-height: 22px;
  white-space: pre-line;
  overflow-wrap: anywhere;
}
.ui-toast-enter-active,
.ui-toast-leave-active {
  transition:
    opacity 140ms ease,
    transform 140ms ease;
}
.ui-toast-enter-from,
.ui-toast-leave-to {
  opacity: 0;
  transform: translate(-50%, -6px);
}
@media (prefers-reduced-motion: reduce) {
  .ui-toast-enter-active,
  .ui-toast-leave-active {
    transition: none;
  }
}
</style>
