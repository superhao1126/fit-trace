<script setup lang="ts">
import dayjs from 'dayjs';
import { Picker, Popup } from 'tdesign-mobile-vue';
import { nextTick, ref, watch } from 'vue';
import UiDateSheet from './UiDateSheet.vue';
import UiButton from './UiButton.vue';
import UiIcon from './UiIcon.vue';
const props = withDefaults(defineProps<{ visible: boolean; value: string; title?: string }>(), {
  title: '选择记录时间',
});
const emit = defineEmits<{ 'update:visible': [visible: boolean]; confirm: [value: string] }>();
const dateVisible = ref(false),
  timeVisible = ref(false),
  date = ref(''),
  time = ref('');
const timeValues = ref<string[]>([]);
const timeColumns = [24, 60].map((length) =>
  Array.from({ length }, (_, index) => {
    const value = String(index).padStart(2, '0');
    return { label: value, value };
  }),
);
function pickTime(values: (string | number)[]) {
  time.value = values.join(':');
}
function scrollTime(event: WheelEvent) {
  if (!event.deltaY || !(event.currentTarget instanceof HTMLElement)) return;
  const bounds = event.currentTarget.getBoundingClientRect();
  const column = event.clientX < bounds.left + bounds.width / 2 ? 0 : 1;
  const values = time.value.split(':');
  const maximum = column === 0 ? 23 : 59;
  values[column] = String(
    Math.min(maximum, Math.max(0, Number(values[column]) + Math.sign(event.deltaY))),
  ).padStart(2, '0');
  timeValues.value = values;
  pickTime(values);
}
const timePanel = ref<HTMLElement | null>(null);
let opener: HTMLElement | null = null;
watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      opener = document.activeElement instanceof HTMLElement ? document.activeElement : null;
      const parsed = dayjs(props.value);
      const current = parsed.isValid() ? parsed : dayjs();
      date.value = current.format('YYYY-MM-DD');
      time.value = current.format('HH:mm');
      dateVisible.value = true;
    } else {
      dateVisible.value = false;
      timeVisible.value = false;
      opener?.focus();
    }
  },
);
function trapFocus(event: KeyboardEvent) {
  if (event.key !== 'Tab') return;
  const controls = timePanel.value?.querySelectorAll<HTMLElement>(
    'button:not([disabled]), [tabindex="0"]',
  );
  if (!controls?.length) return;
  const first = controls[0],
    last = controls[controls.length - 1];
  if (
    event.shiftKey &&
    (document.activeElement === first || document.activeElement === timePanel.value)
  ) {
    event.preventDefault();
    last?.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first?.focus();
  }
}
function selectDate(value: string) {
  date.value = value;
  dateVisible.value = false;
  timeVisible.value = true;
  timeValues.value = time.value.split(':');
  void nextTick(() => timePanel.value?.focus());
}
function close() {
  emit('update:visible', false);
}
function confirm() {
  if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(time.value)) return;
  emit('confirm', `${date.value} ${time.value}:00`);
  close();
}
function dateVisibility(visible: boolean) {
  dateVisible.value = visible;
  if (!visible && !timeVisible.value) close();
}
function timeVisibility(visible: boolean) {
  timeVisible.value = visible;
  if (!visible && !dateVisible.value) close();
}
function back() {
  dateVisible.value = true;
  timeVisible.value = false;
}
</script>
<template>
  <UiDateSheet
    :visible="dateVisible"
    :model-value="date"
    @update:model-value="selectDate"
    @update:visible="dateVisibility"
  /><Popup
    :visible="timeVisible"
    placement="bottom"
    :z-index="1000"
    @update:visible="timeVisibility"
    ><section
      ref="timePanel"
      class="ui-time-sheet"
      role="dialog"
      aria-modal="true"
      tabindex="-1"
      :aria-label="title"
      @keydown.esc="close"
      @keydown="trapFocus"
    >
      <div class="ui-time-sheet__handle" />
      <header>
        <h2>{{ title }}</h2>
        <UiButton kind="text" aria-label="关闭时间选择" @click="close"
          ><UiIcon name="close"
        /></UiButton>
      </header>
      <UiButton kind="text" @click="back"
        >{{ dayjs(date).format('YYYY年M月D日') }} · 更换日期</UiButton
      >
      <div class="time-labels" aria-hidden="true"><span>小时</span><span>分钟</span></div>
      <Picker
        v-if="timeVisible"
        :value="timeValues"
        :columns="timeColumns"
        :cancel-btn="false"
        :confirm-btn="false"
        aria-label="上下滑动选择小时和分钟"
        @pick="pickTime"
        @wheel.prevent="scrollTime"
      />
      <p aria-live="polite">已选 {{ time }} · 24 小时制</p>
      <UiButton block :disabled="!time" @click="confirm">确定时间</UiButton>
    </section></Popup
  >
</template>
<style scoped lang="scss">
.ui-time-sheet {
  max-width: 560px;
  margin: auto;
  padding: 10px 24px calc(24px + var(--app-safe-area-bottom));
  border-radius: 28px 28px 0 0;
  background: var(--color-surface);
  color: var(--ui-text-primary);
  outline: none;
  header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    h2 {
      margin: 0;
      font-size: 20px;
      font-weight: 600;
    }
  }
  .time-labels {
    display: grid;
    grid-template-columns: 1fr 1fr;
    margin-top: 20px;
    text-align: center;
    color: var(--ui-text-secondary);
    font-size: 13px;
  }
  :deep(.t-picker) {
    --td-picker-bg-color: var(--color-surface);
    --td-picker-item-color: var(--ui-text-tertiary);
    --td-picker-item-active-color: var(--ui-text-primary);
    --td-picker-indicator-bg-color: var(--ui-primary-light);
    --td-picker-indicator-border-radius: 12px;
    --td-picker-item-height: 44px;
    --td-picker-group-height: 220px;
    --td-bg-color-container: var(--color-surface);
    margin: 8px 0 16px;
  }
  :deep(.t-picker__toolbar) {
    display: none;
  }
  :deep(.t-picker-item__item) {
    font-size: 24px;
    font-variant-numeric: tabular-nums;
  }
  :deep(.t-picker-item__item--active) {
    font-weight: 600;
  }
  p {
    margin: 0 0 28px;
    color: var(--ui-text-secondary);
    font-size: 12px;
  }
}

.ui-time-sheet__handle {
  width: 36px;
  height: 4px;
  margin: 0 auto 12px;
  background: var(--ui-border);
  border-radius: 4px;
}
</style>
