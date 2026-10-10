<script setup lang="ts">
import dayjs from 'dayjs';
import { Popup } from 'tdesign-mobile-vue';
import { computed, nextTick, ref, watch } from 'vue';
import UiButton from './UiButton.vue';
import UiIcon from './UiIcon.vue';
const props = withDefaults(
  defineProps<{ visible: boolean; modelValue: string; selection?: 'date' | 'month' }>(),
  { selection: 'date' },
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
let opener: HTMLElement | null = null;
const years = Array.from({ length: dayjs().year() - 1899 }, (_, i) => dayjs().year() - i);
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
  if (next.format('YYYY-MM') <= today.slice(0, 7)) viewed.value = next;
}
function choose(value: string) {
  if (value <= (props.selection === 'month' ? today.slice(0, 7) : today)) {
    draft.value = value;
    if (props.selection === 'date') viewed.value = dayjs(value).startOf('month');
  }
}
function confirm() {
  emit('update:modelValue', draft.value);
  close();
}
function jump(year: number, month = viewed.value.month()) {
  viewed.value = dayjs(`${year}-${String(month + 1).padStart(2, '0')}-01`);
}
function trapFocus(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    close();
    return;
  }
  if (event.key !== 'Tab' || !panel.value) return;
  const controls = [
    ...panel.value.querySelectorAll<HTMLElement>('button:not(:disabled), select, [tabindex="0"]'),
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
      :aria-label="selection === 'date' ? '选择日期' : '选择月份'"
      tabindex="-1"
      @keydown="trapFocus"
    >
      <div class="ui-date-sheet__handle" />
      <header>
        <h2>{{ selection === 'date' ? '选择日期' : '选择月份' }}</h2>
        <UiButton kind="text" aria-label="关闭日期选择" @click="close"
          ><UiIcon name="close"
        /></UiButton>
      </header>
      <div class="month-heading">
        <UiButton
          kind="icon"
          :aria-label="selection === 'date' ? '上个月' : '上一年'"
          @click="move(-1)"
          ><UiIcon name="left" /></UiButton
        ><UiButton kind="text" @click="jumpVisible = !jumpVisible">{{
          viewed.format(selection === 'date' ? 'YYYY年M月' : 'YYYY年')
        }}</UiButton
        ><UiButton
          kind="icon"
          :aria-label="selection === 'date' ? '下个月' : '下一年'"
          :disabled="
            viewed.format(selection === 'date' ? 'YYYY-MM' : 'YYYY') >=
            today.slice(0, selection === 'date' ? 7 : 4)
          "
          @click="move(1)"
          ><UiIcon name="right"
        /></UiButton>
      </div>
      <div v-if="jumpVisible" class="date-jump">
        <select
          aria-label="选择年份"
          :value="viewed.year()"
          @change="jump(Number(($event.target as HTMLSelectElement).value))"
        >
          <option v-for="year in years" :key="year" :value="year">{{ year }} 年</option></select
        ><select
          v-if="selection === 'date'"
          aria-label="选择月份"
          :value="viewed.month()"
          @change="jump(viewed.year(), Number(($event.target as HTMLSelectElement).value))"
        >
          <option v-for="month in 12" :key="month" :value="month - 1">{{ month }} 月</option>
        </select>
      </div>
      <template v-if="selection === 'date'"
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
            :disabled="cell.value > today"
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
      <div v-else class="month-grid">
        <button
          v-for="month in 12"
          :key="month"
          type="button"
          :aria-pressed="draft === `${viewed.year()}-${String(month).padStart(2, '0')}`"
          :disabled="`${viewed.year()}-${String(month).padStart(2, '0')}` > today.slice(0, 7)"
          :class="{ selected: draft === `${viewed.year()}-${String(month).padStart(2, '0')}` }"
          @click="choose(`${viewed.year()}-${String(month).padStart(2, '0')}`)"
        >
          {{ month }} 月
        </button>
      </div>
      <footer>
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
.date-jump {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
  select {
    flex: 1;
    min-width: 0;
    height: 40px;
    padding: 0 12px;
    border: 1px solid var(--ui-border);
    border-radius: 12px;
    color: var(--ui-text-primary);
    background: var(--color-surface);
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
