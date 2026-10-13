<script setup lang="ts">
import { computed, useId } from 'vue';
import UiInput from './UiInput.vue';
defineOptions({ inheritAttrs: false });
const props = withDefaults(
  defineProps<{
    modelValue?: string | number;
    unit?: string;
    min?: number;
    max?: number;
    step?: number;
    required?: boolean;
    disabled?: boolean;
  }>(),
  { modelValue: '', unit: '', min: undefined, max: undefined, step: 1 },
);
const emit = defineEmits<{ 'update:modelValue': [value: string | number] }>();
const errorId = useId();
const error = computed(() => {
  if (props.modelValue === '') return '';
  const value = Number(props.modelValue);
  if (!Number.isFinite(value)) return '请输入有效数字';
  if (props.min !== undefined && value < props.min)
    return `不能小于 ${props.min}${props.unit ? ` ${props.unit}` : ''}`;
  if (props.max !== undefined && value > props.max)
    return `不能超过 ${props.max}${props.unit ? ` ${props.unit}` : ''}`;
  if (props.step === 1 && !Number.isInteger(value)) return '请输入整数';
  return '';
});
</script>
<template>
  <div class="ui-number-input" :class="{ 'ui-number-input--invalid': error }">
    <UiInput
      v-bind="$attrs"
      :model-value="modelValue"
      type="number"
      :inputmode="step < 1 ? 'decimal' : 'numeric'"
      :min="min"
      :max="max"
      :step="step"
      :required="required"
      :disabled="disabled"
      :aria-invalid="Boolean(error)"
      :aria-describedby="error ? errorId : $attrs['aria-describedby']"
      @update:model-value="emit('update:modelValue', $event)"
    >
      <template v-if="unit" #suffix-icon
        ><span class="ui-number-input__unit">{{ unit }}</span></template
      >
    </UiInput>
    <small v-if="error" :id="errorId" role="status">{{ error }}</small>
  </div>
</template>
<style scoped lang="scss">
.ui-number-input {
  min-width: 0;
  width: 100%;
}
.ui-number-input__unit {
  color: var(--ui-text-secondary);
  font-size: 13px;
  font-weight: 400;
  white-space: nowrap;
}
.ui-number-input small {
  display: block;
  margin: 6px 0 0;
  color: var(--color-danger);
  font-size: 12px;
  line-height: 1.5;
}
.ui-number-input--invalid :deep(.ui-input) {
  box-shadow: inset 0 0 0 1px var(--color-danger);
}
</style>
