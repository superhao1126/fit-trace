<script setup lang="ts">
import { Input } from 'tdesign-mobile-vue';
defineProps<{ modelValue?: string | number }>();
defineEmits<{ 'update:modelValue': [value: string | number] }>();
</script>
<template>
  <Input
    class="ui-input"
    borderless
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    ><template v-if="$slots['suffix-icon']" #suffix-icon><slot name="suffix-icon" /></template
  ></Input>
</template>
<style scoped lang="scss">
.ui-input.t-input {
  width: 100%;
  min-height: 54px;
  padding: 12px 16px;
  background: var(--color-surface);
  border: 0;
  border-radius: 14px;
  box-shadow: none;
  --td-input-placeholder-color: var(--ui-text-tertiary);
  --td-input-text-color: var(--ui-text-primary);
  --td-input-label-color: var(--ui-text-secondary);
  :deep(input) {
    font-size: 16px;
    min-width: 0;
  }
  :deep(.t-input__suffix) {
    color: var(--ui-text-secondary);
    font-size: 13px;
  }
  &:focus-within {
    box-shadow: 0 0 0 1.5px var(--ui-primary);
  }
}
</style>
