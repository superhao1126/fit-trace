<script setup lang="ts">
import dayjs from 'dayjs';
import { computed } from 'vue';
import { ChevronLeftIcon, ChevronRightIcon } from 'tdesign-icons-vue-next';
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
function moveWeek(offset: number) {
  const date = dayjs(props.modelValue).add(offset, 'day').format('YYYY-MM-DD');
  select(date > today.value ? today.value : date);
}
function select(date: string) {
  if (
    /^\d{4}-\d{2}-\d{2}$/.test(date) &&
    dayjs(date).format('YYYY-MM-DD') === date &&
    date <= today.value
  )
    emit('update:modelValue', date);
}
</script>
<template>
  <section class="diary-date" aria-label="日记日期">
    <div class="diary-date__heading">
      <button type="button" aria-label="上一周" @click="moveWeek(-7)">
        <ChevronLeftIcon />
      </button>
      <label
        ><span>{{ dayjs(modelValue).format('YYYY年M月') }}</span
        ><input
          type="date"
          aria-label="选择日记日期"
          :value="modelValue"
          :max="today"
          @change="select(($event.target as HTMLInputElement).value)"
      /></label>
      <button v-if="modelValue !== today" type="button" class="today-link" @click="select(today)">
        今天
      </button>
      <button
        type="button"
        aria-label="下一周"
        :disabled="days[6].date >= today"
        @click="moveWeek(7)"
      >
        <ChevronRightIcon />
      </button>
    </div>
    <div class="diary-date__week">
      <button
        v-for="day in days"
        :key="day.date"
        type="button"
        :disabled="day.date > today"
        :aria-label="`${day.date}${day.date === today ? ' 今天' : ''}`"
        :aria-pressed="day.date === modelValue"
        :class="{ selected: day.date === modelValue }"
        @click="select(day.date)"
      >
        <span>{{ day.label }}</span
        ><strong>{{ day.number }}</strong
        ><i :class="{ today: day.date === today }" />
      </button>
    </div>
  </section>
</template>
<style scoped lang="scss">
.diary-date {
  margin-bottom: 16px;
}
.diary-date__heading {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  button {
    display: grid;
    place-items: center;
    min-width: 36px;
    height: 36px;
    border: 0;
    border-radius: 6px;
    background: transparent;
    color: var(--color-text-secondary);
  }
  button:disabled {
    opacity: 0.3;
  }
  label {
    position: relative;
    flex: 1;
    font-size: 0.8125rem;
    color: var(--color-text-secondary);
    input {
      position: absolute;
      inset: 0;
      opacity: 0;
      width: 100%;
      cursor: pointer;
    }
  }
  .today-link {
    color: var(--color-accent-text);
    font-size: 0.75rem;
  }
}
.diary-date__week {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 4px;
  button {
    display: grid;
    place-items: center;
    gap: 5px;
    min-height: 66px;
    padding: 7px 0 5px;
    color: var(--color-text-primary);
    border: 1px solid transparent;
    background: transparent;
    border-radius: 8px;
  }
  span {
    color: var(--color-text-secondary);
    font-size: 0.6875rem;
  }
  strong {
    font-size: 0.9375rem;
    font-weight: 500;
  }
  i {
    width: 3px;
    height: 3px;
    border-radius: 50%;
    &.today {
      background: var(--color-accent-text);
    }
  }
  .selected {
    background: var(--color-primary-light);
    border-color: var(--color-primary-border);
    strong {
      color: var(--color-accent-text);
    }
  }
  button:disabled {
    opacity: 0.3;
  }
}
</style>
