<script setup lang="ts">
import dayjs from 'dayjs';
import { Picker, Popup } from 'tdesign-mobile-vue';
import { computed, nextTick, ref, watch } from 'vue';
import UiButton from './UiButton.vue';
import UiIcon from './UiIcon.vue';
const props = withDefaults(
  defineProps<{
    visible: boolean;
    modelValue: string;
    selection?: 'date' | 'month';
    minDate?: string;
    maxDate?: string;
  }>(),
  { selection: 'date', minDate: undefined, maxDate: undefined },
);
const emit = defineEmits<{
  'update:visible': [visible: boolean];
  'update:modelValue': [value: string];
}>();
const today = dayjs().format('YYYY-MM-DD');
const viewed = ref(dayjs().startOf('month'));
const draft = ref(today);
const jumpVisible = ref(false);
const panel = ref<HTMLElement | null>(null);
const jumpPanel = ref<HTMLElement | null>(null);
const jumpValues = ref<string[]>([]);
const jumpColumn = ref(0);
let opener: HTMLElement | null = null;
const maximum = computed(() => props.maxDate || today);
const years = computed(() => {
  const newest = dayjs(maximum.value).year();
  const oldest = props.minDate ? dayjs(props.minDate).year() : 1900;
  return Array.from({ length: newest - oldest + 1 }, (_, i) => newest - i);
});
function unavailable(value: string) {
  const length = props.selection === 'month' ? 7 : 10;
  return (
    value > maximum.value.slice(0, length) ||
    Boolean(props.minDate && value < props.minDate.slice(0, length))
  );
}
const cells = computed(() => {
  const start = viewed.value.subtract((viewed.value.day() + 6) % 7, 'day');
  return Array.from({ length: 42 }, (_, i) => {
    const d = start.add(i, 'day');
    return {
      value: d.format('YYYY-MM-DD'),
      day: d.date(),
      outside: d.month() !== viewed.value.month(),
    };
  });
});
watch(
  () => props.visible,
  async (visible) => {
    if (visible) {
      opener = document.activeElement instanceof HTMLElement ? document.activeElement : null;
      draft.value = props.modelValue || (props.selection === 'month' ? today.slice(0, 7) : today);
      if (unavailable(draft.value)) draft.value = props.minDate || maximum.value;
      viewed.value = dayjs(draft.value.length === 7 ? `${draft.value}-01` : draft.value).startOf(
        'month',
      );
      jumpVisible.value = false;
      await nextTick();
      panel.value?.focus();
    } else {
      opener?.focus();
    }
  },
);
function close() {
  emit('update:visible', false);
}
function move(amount: number) {
  const next = viewed.value.add(amount, props.selection === 'month' ? 'year' : 'month');
  if (
    next.format('YYYY-MM') <= maximum.value.slice(0, 7) &&
    (!props.minDate || next.endOf('month').format('YYYY-MM-DD') >= props.minDate)
  )
    viewed.value = next;
}
function choose(value: string) {
  if (!unavailable(value)) {
    draft.value = value;
    viewed.value = dayjs(value.length === 7 ? `${value}-01` : value).startOf('month');
  }
}
function confirm() {
  emit('update:modelValue', draft.value);
  close();
}
const jumpMonths = computed(() => {
  const year = Number(jumpValues.value[0] || viewed.value.year());
  const first =
    props.minDate && dayjs(props.minDate).year() === year ? dayjs(props.minDate).month() : 0;
  const last = dayjs(maximum.value).year() === year ? dayjs(maximum.value).month() : 11;
  return Array.from({ length: Math.max(0, last - first + 1) }, (_, i) => {
    const month = first + i;
    return { label: `${month + 1} 月`, value: String(month) };
  });
});
const jumpColumns = computed(() => {
  const yearColumn = years.value.map((year) => ({ label: `${year} 年`, value: String(year) }));
  return props.selection === 'month' ? [yearColumn] : [yearColumn, jumpMonths.value];
});
function pickJump(values: (string | number)[]) {
  const year = String(values[0] ?? viewed.value.year());
  if (!years.value.includes(Number(year))) return;
  jumpValues.value = [year];
  if (props.selection === 'date') {
    const months = jumpMonths.value;
    const month = Math.min(
      Number(months.at(-1)?.value ?? 11),
      Math.max(Number(months[0]?.value ?? 0), Number(values[1] ?? viewed.value.month())),
    );
    jumpValues.value = [year, String(month)];
  }
}
async function openJump() {
  pickJump([viewed.value.year(), viewed.value.month()]);
  jumpColumn.value = 0;
  jumpVisible.value = true;
  await nextTick();
  jumpPanel.value?.focus();
}
async function returnToCalendar() {
  jumpVisible.value = false;
  await nextTick();
  panel.value?.querySelector<HTMLElement>('[data-year-jump-trigger]')?.focus();
}
function confirmJump() {
  const year = Number(jumpValues.value[0]);
  const month = props.selection === 'date' ? Number(jumpValues.value[1]) : viewed.value.month();
  // Preserve the browsed month where possible, clamping at the date boundaries.
  const value = dayjs(`${year}-${String(month + 1).padStart(2, '0')}-01`);
  const first = props.minDate ? dayjs(props.minDate).startOf('month') : dayjs('1900-01-01');
  const last = dayjs(maximum.value).startOf('month');
  viewed.value = value.isBefore(first) ? first : value.isAfter(last) ? last : value;
  void returnToCalendar();
}
function stepJump(column: number, amount: number) {
  const options = jumpColumns.value[column];
  if (!options?.length) return;
  const index = options.findIndex((option) => option.value === jumpValues.value[column]);
  const next = Math.min(options.length - 1, Math.max(0, index + amount));
  const values = [...jumpValues.value];
  values[column] = options[next].value;
  pickJump(values);
}
function scrollJump(event: WheelEvent) {
  if (!event.deltaY || !(event.currentTarget instanceof HTMLElement)) return;
  const bounds = event.currentTarget.getBoundingClientRect();
  const column =
    props.selection === 'date' && event.clientX > bounds.left + bounds.width / 2 ? 1 : 0;
  stepJump(column, Math.sign(event.deltaY));
}
function keyJump(event: KeyboardEvent) {
  if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
    jumpColumn.value = event.key === 'ArrowRight' && props.selection === 'date' ? 1 : 0;
  } else if (event.key === 'ArrowUp' || event.key === 'ArrowDown') {
    stepJump(jumpColumn.value, event.key === 'ArrowDown' ? 1 : -1);
  } else return;
  event.preventDefault();
}
function trapFocus(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    if (jumpVisible.value) void returnToCalendar();
    else close();
    return;
  }
  if (event.key !== 'Tab' || !panel.value) return;
  const controls = [
    ...panel.value.querySelectorAll<HTMLElement>('button:not(:disabled), [tabindex="0"]'),
  ].filter((el) => el.getClientRects().length);
  const first = controls[0],
    last = controls[controls.length - 1];
  if (
    event.shiftKey &&
    (document.activeElement === first || document.activeElement === panel.value)
  ) {
    event.preventDefault();
    last?.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first?.focus();
  }
}
</script>
<template>
  <Popup
    :visible="visible"
    placement="bottom"
    :z-index="1000"
    @update:visible="$emit('update:visible', $event)"
  >
    <section
      ref="panel"
      class="ui-date-sheet"
      role="dialog"
      aria-modal="true"
      :aria-label="
        jumpVisible
          ? selection === 'date'
            ? '选择年月'
            : '选择年份'
          : selection === 'date'
            ? '选择日期'
            : '选择月份'
      "
      tabindex="-1"
      @keydown="trapFocus"
    >
      <div class="ui-date-sheet__handle" />
      <header>
        <h2>
          {{
            jumpVisible
              ? selection === 'date'
                ? '选择年月'
                : '选择年份'
              : selection === 'date'
                ? '选择日期'
                : '选择月份'
          }}
        </h2>
        <UiButton kind="text" aria-label="关闭日期选择" @click="close"
          ><UiIcon name="close"
        /></UiButton>
      </header>
      <div v-if="!jumpVisible" class="month-heading">
        <UiButton
          kind="icon"
          :aria-label="selection === 'date' ? '上个月' : '上一年'"
          :disabled="
            Boolean(
              minDate &&
              viewed.format(selection === 'date' ? 'YYYY-MM' : 'YYYY') <=
                minDate.slice(0, selection === 'date' ? 7 : 4),
            )
          "
          @click="move(-1)"
          ><UiIcon name="left" /></UiButton
        ><UiButton
          kind="text"
          data-year-jump-trigger
          :aria-label="selection === 'date' ? '选择年月' : '选择年份'"
          @click="openJump"
          ><span class="year-trigger"
            ><span :key="viewed.year()" class="calendar-period-label">{{
              viewed.format(selection === 'date' ? 'YYYY年M月' : 'YYYY年')
            }}</span
            ><UiIcon name="right" class="year-trigger__chevron" /></span></UiButton
        ><UiButton
          kind="icon"
          :aria-label="selection === 'date' ? '下个月' : '下一年'"
          :disabled="
            viewed.format(selection === 'date' ? 'YYYY-MM' : 'YYYY') >=
            maximum.slice(0, selection === 'date' ? 7 : 4)
          "
          @click="move(1)"
          ><UiIcon name="right"
        /></UiButton>
      </div>
      <div v-if="jumpVisible" class="date-jump">
        <div
          ref="jumpPanel"
          class="date-jump__wheel"
          tabindex="0"
          role="group"
          :aria-label="
            selection === 'month'
              ? '上下滚动选择年份'
              : `选择年月，上下调整${jumpColumn === 0 ? '年份' : '月份'}，左右切换`
          "
          @wheel.prevent="scrollJump"
          @keydown="keyJump"
        >
          <Picker
            :value="jumpValues"
            :columns="jumpColumns"
            :cancel-btn="false"
            :confirm-btn="false"
            @pick="pickJump"
          />
        </div>
        <p aria-live="polite">
          已选 {{ jumpValues[0] }} 年{{
            selection === 'date' ? ` ${Number(jumpValues[1]) + 1} 月` : ''
          }}
        </p>
      </div>
      <template v-if="!jumpVisible && selection === 'date'"
        ><div class="week-labels">
          <span v-for="label in ['一', '二', '三', '四', '五', '六', '日']" :key="label">{{
            label
          }}</span>
        </div>
        <div class="date-grid">
          <button
            v-for="cell in cells"
            :key="cell.value"
            type="button"
            :aria-label="cell.value"
            :aria-pressed="draft === cell.value"
            :disabled="unavailable(cell.value)"
            :class="{
              selected: draft === cell.value,
              outside: cell.outside,
              today: cell.value === today,
            }"
            @click="choose(cell.value)"
          >
            {{ cell.day }}
          </button>
        </div></template
      >
      <div v-else-if="!jumpVisible" :key="viewed.year()" class="month-grid">
        <button
          v-for="month in 12"
          :key="month"
          type="button"
          :aria-pressed="draft === `${viewed.year()}-${String(month).padStart(2, '0')}`"
          :disabled="unavailable(`${viewed.year()}-${String(month).padStart(2, '0')}`)"
          :class="{ selected: draft === `${viewed.year()}-${String(month).padStart(2, '0')}` }"
          @click="choose(`${viewed.year()}-${String(month).padStart(2, '0')}`)"
        >
          {{ month }} 月
        </button>
      </div>
      <footer v-if="jumpVisible">
        <UiButton kind="secondary" @click="returnToCalendar">返回</UiButton>
        <UiButton :disabled="!jumpValues.length" @click="confirmJump"
          >确定{{ selection === 'date' ? '年月' : '年份' }}</UiButton
        >
      </footer>
      <footer v-else>
        <UiButton
          kind="secondary"
          @click="choose(selection === 'date' ? today : today.slice(0, 7))"
          >{{ selection === 'date' ? '回到今天' : '本月' }}</UiButton
        ><UiButton @click="confirm">确定{{ selection === 'date' ? '日期' : '月份' }}</UiButton>
      </footer>
    </section></Popup
  >
</template>
<style scoped lang="scss">
.ui-date-sheet {
  width: min(100%, 560px);
  margin: 0 auto;
  padding: 10px 24px calc(24px + var(--app-safe-area-bottom));
  background: var(--color-surface);
  border-radius: 28px 28px 0 0;
  color: var(--ui-text-primary);
  outline: none;
  --td-brand-color: var(--ui-primary);
}
.ui-date-sheet__handle {
  width: 36px;
  height: 4px;
  margin: 0 auto 12px;
  background: var(--ui-border);
  border-radius: 4px;
}
header,
.month-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  h2 {
    margin: 0;
    font-size: 20px;
    font-weight: 600;
  }
}
.month-heading {
  margin: 20px 0 12px;
}
.week-labels,
.date-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  text-align: center;
  gap: 4px;
}
.week-labels {
  padding: 8px 0 12px;
  color: var(--ui-text-secondary);
  font-size: 12px;
}
.date-grid button,
.month-grid button {
  min-width: 0;
  min-height: 40px;
  padding: 0;
  color: var(--ui-text-primary);
  background: transparent;
  border: 0;
  border-radius: 12px;
  font-size: 15px;
  cursor: pointer;
  &.outside {
    color: var(--ui-text-tertiary);
  }
  &:disabled {
    opacity: 0.3;
    cursor: default;
  }
  &.today {
    box-shadow: inset 0 0 0 1px var(--ui-primary);
  }
  &.selected {
    color: var(--color-on-primary);
    background: var(--ui-primary);
  }
  &:focus-visible {
    outline: 2px solid var(--ui-primary);
    outline-offset: 1px;
  }
}
.month-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}
.calendar-period-label {
  display: inline-block;
}
.calendar-period-label,
.month-grid {
  animation: calendar-browse 220ms var(--ease-standard);
}
@keyframes calendar-browse {
  from {
    opacity: 0.45;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
@media (prefers-reduced-motion: reduce) {
  .calendar-period-label,
  .month-grid {
    animation: none;
  }
}
.year-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
}
.year-trigger__chevron {
  width: 16px;
  height: 16px;
  transform: rotate(90deg);
}
.date-jump {
  margin-top: 16px;
  p {
    margin: 8px 0 0;
    color: var(--ui-text-secondary);
    font-size: 12px;
  }
  :deep(.t-picker) {
    --td-picker-bg-color: var(--color-surface);
    --td-picker-item-color: var(--ui-text-tertiary);
    --td-picker-item-active-color: var(--ui-primary);
    --td-picker-indicator-bg-color: var(--ui-primary-light);
    --td-picker-indicator-border-radius: 12px;
    --td-picker-item-height: 44px;
    --td-picker-group-height: 220px;
    --td-bg-color-container: var(--color-surface);
  }
  :deep(.t-picker__toolbar) {
    display: none;
  }
  :deep(.t-picker-item__item) {
    font-size: 19px;
    font-variant-numeric: tabular-nums;
  }
  :deep(.t-picker-item__item--active) {
    font-weight: 600;
  }
}
.date-jump__wheel {
  border-radius: 12px;
  outline: none;
  &:focus-visible {
    outline: 2px solid var(--ui-primary);
    outline-offset: 2px;
  }
}
footer {
  display: grid;
  grid-template-columns: 1fr 1.5fr;
  gap: 12px;
  margin-top: 24px;
}
@media (max-height: 650px) {
  .ui-date-sheet {
    padding-top: 6px;
    max-height: 90dvh;
    overflow-y: auto;
  }
  .month-heading {
    margin-top: 8px;
  }
  .date-grid button {
    min-height: 34px;
  }
  footer {
    margin-top: 16px;
  }
}
</style>
