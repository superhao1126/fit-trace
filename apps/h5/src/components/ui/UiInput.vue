<script setup lang="ts">
import { Input } from 'tdesign-mobile-vue';
import { useAttrs, type Directive } from 'vue';
defineOptions({ inheritAttrs: false });
const attrs = useAttrs();
const nativeKeys = [
  'id',
  'aria-label',
  'aria-invalid',
  'aria-describedby',
  'min',
  'max',
  'step',
  'maxlength',
  'autocomplete',
  'required',
];
function wrapperAttrs() {
  return Object.fromEntries(Object.entries(attrs).filter(([key]) => !nativeKeys.includes(key)));
}
function applyControlAttrs(el: HTMLElement, values: Record<string, unknown>) {
  const control = el.querySelector('input');
  if (!control) return;
  for (const key of nativeKeys) {
    const value = values[key];
    if (value === undefined || value === null || (key === 'required' && value === false))
      control.removeAttribute(key);
    else control.setAttribute(key, String(value));
  }
}
const vControlAttrs: Directive<HTMLElement, Record<string, unknown>> = {
  mounted: (el, binding) => applyControlAttrs(el, binding.value),
  updated: (el, binding) => applyControlAttrs(el, binding.value),
};
defineProps<{ modelValue?: string | number }>();
defineEmits<{ 'update:modelValue': [value: string | number] }>();
</script>
<template>
  <input
    v-if="attrs.type === 'date'"
    v-bind="attrs"
    class="ui-input ui-native-input"
    :value="modelValue"
    @input="$emit('update:modelValue', ($event.target as HTMLInputElement).value)"
  />
  <Input
    v-else
    v-bind="wrapperAttrs()"
    v-control-attrs="{ ...attrs }"
    class="ui-input"
    borderless
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    ><template v-if="$slots['suffix-icon']" #suffix-icon><slot name="suffix-icon" /></template
  ></Input>
</template>
<style scoped lang="scss">
.ui-input.t-input,
.ui-native-input {
  width: 100%;
  box-sizing: border-box;
  color: var(--ui-text-primary);
  font-family: inherit;
  font-size: 16px;
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
    letter-spacing: normal;
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
