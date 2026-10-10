<script setup lang="ts">
import dayjs from 'dayjs';
import { computed } from 'vue';
const props = defineProps<{ modelValue: string }>();
const emit = defineEmits<{ 'update:modelValue': [date: string] }>();
const today = computed(() => dayjs().format('YYYY-MM-DD'));
const days = computed(() => {
  const selected = dayjs(props.modelValue);
  const start = selected.subtract((selected.day() + 6) % 7, 'day');
  return Array.from({ length: 7 }, (_, i) => ({
    date: start.add(i, 'day').format('YYYY-MM-DD'),
    label: ['一', '二', '三', '四', '五', '六', '日'][i],
    number: start.add(i, 'day').date(),
  }));
});
let touchStart: { x: number; y: number } | null = null;
function startTouch(event: TouchEvent) {
  touchStart = { x: event.touches[0].clientX, y: event.touches[0].clientY };
}
function swipe(event: TouchEvent) {
  if (!touchStart) return;
  const touch = event.changedTouches[0];
  const dx = touch.clientX - touchStart.x,
    dy = touch.clientY - touchStart.y;
  if (Math.abs(dx) > 50 && Math.abs(dx) > Math.abs(dy) * 1.5) moveWeek(dx < 0 ? 7 : -7);
  touchStart = null;
}
function moveWeek(offset: number) {
  const date = dayjs(props.modelValue).add(offset, 'day').format('YYYY-MM-DD');
  emit('update:modelValue', date > today.value ? today.value : date);
}
</script>
<template>
  <section
    class="diary-date"
    aria-label="日记日期"
    @touchstart.passive="startTouch"
    @touchend.passive="swipe"
  >
    <div class="diary-date__week">
      <button
        v-for="day in days"
        :key="day.date"
        type="button"
        :disabled="day.date > today"
        :aria-label="`${day.date}${day.date === today ? ' 今天' : ''}`"
        :aria-pressed="day.date === modelValue"
        :class="{ selected: day.date === modelValue, today: day.date === today }"
        @click="$emit('update:modelValue', day.date)"
        @keydown.left.prevent="moveWeek(-7)"
        @keydown.right.prevent="moveWeek(7)"
      >
        <span>{{ day.label }}</span
        ><strong>{{ day.number }}</strong>
      </button>
    </div>
  </section>
</template>
<style scoped lang="scss">
.diary-date {
  margin: 4px 0 24px;
  touch-action: pan-y;
}
.diary-date__week {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 6px;
  button {
    display: grid;
    align-content: center;
    justify-items: center;
    gap: 8px;
    min-width: 0;
    height: 64px;
    padding: 6px 0;
    border: 0;
    border-radius: 20px;
    background: transparent;
    color: var(--ui-text-primary);
    cursor: pointer;
    &:disabled {
      opacity: 0.3;
      cursor: default;
    }
    &:focus-visible {
      outline: 2px solid var(--ui-primary);
      outline-offset: 2px;
    }
    &.selected {
      background: var(--ui-primary);
      color: var(--color-on-primary);
      span {
        color: inherit;
      }
    }
  }
  span {
    color: var(--ui-text-secondary);
    font-size: 12px;
  }
  strong {
    font-size: 19px;
    font-weight: 600;
    line-height: 1.2;
  }
}
</style>
