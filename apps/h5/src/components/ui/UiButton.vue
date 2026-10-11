<script setup lang="ts">
import { Button } from 'tdesign-mobile-vue';
withDefaults(
  defineProps<{
    kind?: 'primary' | 'secondary' | 'text' | 'icon' | 'danger';
    loading?: boolean;
    disabled?: boolean;
    block?: boolean;
    type?: 'button' | 'submit' | 'reset';
  }>(),
  { kind: 'primary', type: 'button' },
);
</script>
<template>
  <Button
    class="ui-button"
    :class="`ui-button--${kind}`"
    :variant="kind === 'primary' ? 'base' : 'text'"
    :theme="kind === 'primary' ? 'primary' : 'default'"
    :type="type"
    :loading="loading"
    :disabled="disabled"
    :block="block"
    ><slot
  /></Button>
</template>
<style scoped lang="scss">
.ui-button.t-button {
  min-width: 0;
  height: 48px;
  padding: 0 18px;
  border: 0;
  border-radius: var(--ui-radius-button);
  font-size: 15px;
  font-weight: 600;
  --td-button-primary-bg-color: var(--ui-primary);
  --td-button-primary-border-color: var(--ui-primary);
  --td-button-primary-color: var(--color-on-primary);
  :deep(.t-button__content) {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
  }
  &:disabled {
    opacity: 0.4;
  }
  &::after {
    border: 0;
    border-radius: inherit;
  }
  &:focus-visible {
    outline: 2px solid var(--ui-primary);
    outline-offset: 3px;
  }
  &.ui-button--secondary {
    :deep(.t-button__content) {
      width: 100%;
    }
    background: var(--color-surface);
    color: var(--ui-text-primary);
  }
  &.ui-button--danger {
    height: 44px;
    padding: 0;
    background: transparent;
    color: var(--color-danger);
    font-size: 14px;
    font-weight: 400;
  }
  &.ui-button--text {
    height: 44px;
    padding: 0;
    color: var(--ui-primary);
    font-size: 13px;
    font-weight: 400;
  }
  &.ui-button--icon {
    width: 44px;
    height: 44px;
    padding: 0;
    color: var(--ui-primary);
    background: var(--color-surface);
    border-radius: 15px;
  }
}
</style>
