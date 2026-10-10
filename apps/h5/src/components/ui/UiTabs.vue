<script setup lang="ts">
import { Button } from 'tdesign-mobile-vue';
withDefaults(
  defineProps<{
    modelValue: string | number;
    options: Array<{ value: string | number; label: string }>;
    label: string;
    tone?: 'brand' | 'body';
  }>(),
  { tone: 'brand' },
);
defineEmits<{ 'update:modelValue': [value: string | number] }>();
</script>
<template>
  <div class="ui-tabs" :class="`ui-tabs--${tone}`" role="group" :aria-label="label">
    <Button
      v-for="option in options"
      :key="option.value"
      variant="text"
      class="ui-tabs__item"
      :class="{ active: modelValue === option.value }"
      :aria-pressed="modelValue === option.value"
      @click="$emit('update:modelValue', option.value)"
      >{{ option.label }}</Button
    >
  </div>
</template>
<style scoped lang="scss">
.ui-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
}
.ui-tabs__item.t-button {
  min-width: 0;
  flex: 1;
  height: 40px;
  padding: 0 4px;
  border: 0;
  border-radius: 12px;
  background: var(--color-surface);
  color: var(--ui-text-secondary);
  font-size: 13px;
  &:focus-visible {
    outline: 2px solid var(--ui-primary);
    outline-offset: 2px;
  }
  &.active {
    background: var(--ui-primary);
    color: var(--color-on-primary);
  }
}
.ui-tabs--body .ui-tabs__item.t-button {
  background: transparent;
  &.active {
    background: var(--ui-body-foreground);
    color: var(--color-surface);
  }
}
</style>
