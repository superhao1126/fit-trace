<script setup lang="ts">
import dayjs from 'dayjs';
import { Popup } from 'tdesign-mobile-vue';
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
const timeInput = ref<HTMLInputElement | null>(null);
const timePanel = ref<HTMLElement | null>(null);
let opener: HTMLElement | null = null;
watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      opener = document.activeElement instanceof HTMLElement ? document.activeElement : null;
      const current = dayjs(props.value);
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
  const controls = timePanel.value?.querySelectorAll<HTMLElement>('button:not([disabled]), input');
  if (!controls?.length) return;
  const first = controls[0],
    last = controls[controls.length - 1];
  if (event.shiftKey && document.activeElement === first) {
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
  void nextTick(() => timeInput.value?.focus());
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
      :aria-label="title"
      @keydown.esc="close"
      @keydown="trapFocus"
    >
      <header>
        <h2>{{ title }}</h2>
        <UiButton kind="text" aria-label="关闭时间选择" @click="close"
          ><UiIcon name="close"
        /></UiButton>
      </header>
      <UiButton kind="text" @click="back"
        >{{ dayjs(date).format('YYYY年M月D日') }} · 更换日期</UiButton
      ><label
        >选择时间<input
          ref="timeInput"
          v-model="time"
          type="time"
          aria-label="记录时分"
          step="60"
          required
          @keydown.enter="confirm"
      /></label>
      <p>记录发生的当地时间</p>
      <UiButton block :disabled="!time" @click="confirm">确定时间</UiButton>
    </section></Popup
  >
</template>
<style scoped lang="scss">
.ui-time-sheet {
  max-width: 560px;
  margin: auto;
  padding: 24px 24px calc(24px + var(--app-safe-area-bottom));
  border-radius: 28px 28px 0 0;
  background: var(--color-surface);
  color: var(--ui-text-primary);
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
  label {
    display: grid;
    gap: 12px;
    margin: 20px 0;
    color: var(--ui-text-secondary);
    font-size: 13px;
  }
  input {
    width: 100%;
    min-width: 0;
    min-height: 72px;
    padding: 12px 16px;
    background: var(--ui-background);
    color: var(--ui-text-primary);
    border: 0;
    border-radius: 16px;
    font-family: inherit;
    font-size: 32px;
    text-align: center;
    box-sizing: border-box;
  }
  p {
    margin: 0 0 28px;
    color: var(--ui-text-secondary);
    font-size: 12px;
  }
}
</style>
