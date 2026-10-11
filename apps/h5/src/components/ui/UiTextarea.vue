<script setup lang="ts">
import { Textarea } from 'tdesign-mobile-vue';
defineProps<{ modelValue?: string }>();
defineEmits<{ 'update:modelValue': [value: string] }>();
</script>
<template>
  <Textarea
    class="ui-textarea"
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
  />
</template>
<style scoped lang="scss">
.ui-textarea.t-textarea {
  padding: 16px;
  background: var(--color-surface);
  border: 0;
  border-radius: 20px;
  --td-textarea-text-color: var(--ui-text-primary);
  --td-textarea-placeholder-color: var(--ui-text-tertiary);
  :deep(textarea) {
    font-size: 15px;
    line-height: 1.7;
  }
  &:focus-within {
    box-shadow: 0 0 0 1.5px var(--ui-primary);
  }
}
</style>
