<script setup lang="ts">
import { Button } from 'tdesign-mobile-vue';
withDefaults(
  defineProps<{
    modelValue: string | number;
    options: ReadonlyArray<{ value: string | number; label: string }>;
    label: string;
    columns?: number;
    disabled?: boolean;
    tone?: 'brand' | 'body' | 'meal' | 'workout';
  }>(),
  { tone: 'brand', columns: undefined },
);
defineEmits<{ 'update:modelValue': [value: string | number] }>();
</script>
<template>
  <div
    class="ui-tabs"
    :class="`ui-tabs--${tone}`"
    :style="
      columns
        ? { display: 'grid', gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` }
        : undefined
    "
    role="group"
    :aria-label="label"
  >
    <Button
      v-for="option in options"
      :key="option.value"
      variant="text"
      class="ui-tabs__item"
      :class="{ active: modelValue === option.value }"
      :aria-pressed="modelValue === option.value"
      :disabled="disabled"
      @click="$emit('update:modelValue', option.value)"
      ><slot name="option" :option="option">{{ option.label }}</slot></Button
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

<style scoped lang="scss">
.ui-tabs--meal .ui-tabs__item.active {
  background: var(--ui-meal-foreground);
  color: var(--color-on-primary);
}
.ui-tabs--workout .ui-tabs__item {
  height: 50px;
}
.ui-tabs--workout .ui-tabs__item.active {
  background: var(--ui-workout-background);
  color: var(--ui-workout-foreground);
}
</style>
