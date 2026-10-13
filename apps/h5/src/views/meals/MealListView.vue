<script setup lang="ts">
import UiLoading from '@/components/ui/UiLoading.vue';
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import dayjs from 'dayjs';
import type { MealRecord, MealType } from '@fit-trace/shared';
import UiDetailHeader from '@/components/ui/UiDetailHeader.vue';
import UiButton from '@/components/ui/UiButton.vue';
import UiRecordRow from '@/components/ui/UiRecordRow.vue';
import UiEmptyState from '@/components/ui/UiEmptyState.vue';
import UiDateSheet from '@/components/ui/UiDateSheet.vue';
import DiaryDateStrip from '@/components/DiaryDateStrip.vue';
import { getMeals } from '@/api/meals';
import { dateRange, recordDateTime } from '@/utils/record-date';
import { showRequestError } from '@/utils/request-error';
const route = useRoute();
const router = useRouter();
const date = ref(dayjs(recordDateTime(route.query.date)).format('YYYY-MM-DD'));
const calendarVisible = ref(false);
const meals = ref<MealRecord[]>([]);
const loading = ref(false);
const errorMessage = ref('');
const mealTypes: Array<{ value: MealType; label: string }> = [
  { value: 'BREAKFAST', label: '早餐' },
  { value: 'LUNCH', label: '午餐' },
  { value: 'DINNER', label: '晚餐' },
  { value: 'SNACK', label: '加餐' },
];
const sections = computed(() =>
  mealTypes.map((type) => ({ ...type, items: meals.value.filter((m) => m.type === type.value) })),
);
const knownCalories = computed(() =>
  meals.value.flatMap((m) => m.foods.flatMap((f) => (f.calories === null ? [] : [f.calories]))),
);
const totalCalories = computed(() =>
  knownCalories.value.length ? knownCalories.value.reduce((a, b) => a + b, 0) : null,
);
const recordedMeals = computed(() => sections.value.filter((s) => s.items.length).length);
let requestVersion = 0;
async function load() {
  const version = ++requestVersion;
  loading.value = true;
  errorMessage.value = '';
  const range = dateRange(date.value);
  try {
    let page = 1;
    const items: MealRecord[] = [];
    let total = 0;
    do {
      const result = await getMeals({ ...range, page, pageSize: 100 });
      if (version !== requestVersion) return;
      items.push(...result.data);
      total = result.meta.total;
      if (!result.data.length) break;
      page += 1;
    } while (items.length < total);
    meals.value = items;
  } catch (error) {
    if (version !== requestVersion) return;
    errorMessage.value = '饮食记录加载失败';
    showRequestError(error, '饮食记录加载失败');
  } finally {
    if (version === requestVersion) loading.value = false;
  }
}
function add(type?: MealType, mode?: string) {
  router.push({
    path: '/meals/create',
    query: {
      date: date.value,
      returnTo: '/meals',
      ...(type ? { type } : {}),
      ...(mode ? { mode } : {}),
    },
  });
}
watch(
  () => route.query.date,
  (value) => {
    const selected = dayjs(recordDateTime(value)).format('YYYY-MM-DD');
    if (selected !== date.value) date.value = selected;
  },
);
watch(
  date,
  (value) => {
    void router.replace({ path: '/meals', query: { date: value } });
    void load();
  },
  { immediate: true },
);
</script>
<template>
  <main class="view-page ui-page meal-diary">
    <UiDetailHeader
      title="饮食记录"
      :subtitle="dayjs(date).format('YYYY年M月D日')"
      action-label="选日期"
      @action="calendarVisible = true"
    />
    <DiaryDateStrip v-model="date" />
    <UiDateSheet v-model:visible="calendarVisible" v-model="date" />
    <UiLoading
      class="page-loading"
      :class="{ 'page-loading--active': loading }"
      :loading="loading"
      text="正在读取饮食记录"
    >
      <UiEmptyState
        v-if="!loading && errorMessage"
        :title="errorMessage"
        description="请检查网络后再试"
        action-label="重新加载"
        @action="load"
      />
      <template v-else-if="!loading">
        <section class="intake-summary">
          <span>当日摄入</span><strong>{{ totalCalories ?? '—' }} <small>kcal</small></strong>
          <p>
            已记录 {{ recordedMeals }} 餐 ·
            {{ totalCalories === null ? '暂无热量数据' : '仅统计已填写热量的食物' }}
          </p>
        </section>
        <section v-for="section in sections" :key="section.value" class="meal-section">
          <header>
            <h2>{{ section.label }}</h2>
            <UiButton kind="text" @click="add(section.value)">＋ 添加食物</UiButton>
          </header>
          <p v-if="!section.items.length" class="empty-meal">尚未记录</p>
          <UiRecordRow
            v-for="meal in section.items"
            :key="meal.id"
            kind="meal"
            :image="meal.imageUrl || undefined"
            :title="meal.foods.map((food) => food.name).join('、')"
            :subtitle="`${dayjs(meal.recordedAt).format('HH:mm')} · ${meal.foods.length} 种食物`"
            :value="meal.totalCalories ?? '未记热量'"
            :unit="meal.totalCalories === null ? '' : 'kcal'"
            @click="router.push(`/meals/${meal.id}`)"
          />
        </section>
        <UiButton block @click="add(undefined, 'photo')">AI 拍照识别</UiButton>
      </template>
    </UiLoading>
  </main>
</template>
<style scoped lang="scss">
.intake-summary {
  padding: 20px;
  background: var(--color-surface);
  border-radius: var(--ui-radius-card);
  margin-bottom: 16px;
  span {
    font-size: 13px;
    color: var(--ui-text-secondary);
  }
  strong {
    display: block;
    font-size: 30px;
    margin-top: 8px;
  }
  small {
    font-size: 13px;
    font-weight: 400;
  }
  p {
    font-size: 12px;
    color: var(--ui-text-secondary);
    margin: 8px 0 0;
  }
}
.meal-section {
  background: var(--color-surface);
  border-radius: var(--ui-radius-card);
  margin-bottom: 12px;
  overflow: hidden;
  header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 16px;
    gap: 8px;
  }
  h2 {
    font-size: 17px;
    margin: 0;
  }
  .empty-meal {
    margin: 0;
    padding: 0 16px 20px;
    color: var(--ui-text-secondary);
    font-size: 13px;
  }
}
</style>
