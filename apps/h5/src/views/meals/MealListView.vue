<script setup lang="ts">
import UiDetailHeader from '@/components/ui/UiDetailHeader.vue';
import UiButton from '@/components/ui/UiButton.vue';
import UiRecordRow from '@/components/ui/UiRecordRow.vue';
import UiTabs from '@/components/ui/UiTabs.vue';
import UiEmptyState from '@/components/ui/UiEmptyState.vue';
import { showRequestError } from '@/utils/request-error';
import type { MealRecord, MealType } from '@fit-trace/shared';
import dayjs from 'dayjs';
import { DialogPlugin, Loading, ToastPlugin } from 'tdesign-mobile-vue';
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { deleteMeal, getMeals } from '@/api/meals';

const mealLabels: Record<MealType, string> = {
  BREAKFAST: '早餐',
  LUNCH: '午餐',
  DINNER: '晚餐',
  SNACK: '加餐',
};
const filters: Array<{ value: MealType | 'ALL'; label: string }> = [
  { value: 'ALL', label: '全部' },
  { value: 'BREAKFAST', label: '早餐' },
  { value: 'LUNCH', label: '午餐' },
  { value: 'DINNER', label: '晚餐' },
  { value: 'SNACK', label: '加餐' },
];
const router = useRouter();
const activeType = ref<MealType | 'ALL'>('ALL');
const meals = ref<MealRecord[]>([]);
const loading = ref(true);
const loadError = ref('');
const loadingMore = ref(false);
const page = ref(1);
const total = ref(0);
const pageSize = 20;
const groupedMeals = computed(() => {
  const groups = new Map<string, MealRecord[]>();
  for (const meal of meals.value) {
    const key = dayjs(meal.recordedAt).format('YYYY-MM-DD');
    groups.set(key, [...(groups.get(key) ?? []), meal]);
  }
  return [...groups.entries()].map(([date, items]) => ({ date, items }));
});

function dateTitle(date: string): string {
  const value = dayjs(date);
  if (value.isSame(dayjs(), 'day')) return '今天';
  if (value.isSame(dayjs().subtract(1, 'day'), 'day')) return '昨天';
  return value.format('M月D日');
}
async function load(reset = false): Promise<void> {
  loadError.value = '';
  if (reset) {
    page.value = 1;
    meals.value = [];
  }
  const result = await getMeals({
    page: page.value,
    pageSize,
    ...(activeType.value === 'ALL' ? {} : { type: activeType.value }),
  });
  meals.value.push(...result.data);
  total.value = result.meta.total;
}
async function loadMore(): Promise<void> {
  loadingMore.value = true;
  page.value += 1;
  try {
    await load();
  } catch (error) {
    page.value -= 1;
    showRequestError(error, '加载更多饮食记录失败');
  } finally {
    loadingMore.value = false;
  }
}
async function selectType(type: MealType | 'ALL'): Promise<void> {
  if (type === activeType.value) return;
  activeType.value = type;
  loading.value = true;
  try {
    await load(true);
  } catch (error) {
    loadError.value = '饮食记录加载失败，请重试';
    showRequestError(error, '饮食记录加载失败');
  } finally {
    loading.value = false;
  }
}
function confirmDelete(meal: MealRecord): void {
  let deleting = false;
  const dialog = DialogPlugin.confirm({
    title: '删除这条饮食记录？',
    content: `${dayjs(meal.recordedAt).format('M月D日 HH:mm')} · ${mealLabels[meal.type]}，删除后无法恢复`,
    confirmBtn: { content: '删除', theme: 'danger' },
    cancelBtn: '取消',
    onConfirm: async () => {
      if (deleting) return;
      deleting = true;
      dialog.update({ confirmBtn: { content: '删除中…', theme: 'danger', loading: true } });
      try {
        await deleteMeal(meal.id);
        meals.value = meals.value.filter((item) => item.id !== meal.id);
        total.value -= 1;
        ToastPlugin.success('饮食记录已删除');
        dialog.destroy();
      } catch (error) {
        showRequestError(error, '删除失败，请稍后重试');
        deleting = false;
        dialog.update({ confirmBtn: { content: '删除', theme: 'danger' } });
      }
    },
  });
}
onMounted(async () => {
  try {
    await load();
  } catch (error) {
    loadError.value = '饮食记录加载失败，请重试';
    showRequestError(error, '饮食记录加载失败');
  } finally {
    loading.value = false;
  }
});
async function retry(): Promise<void> {
  loading.value = true;
  try {
    await load(true);
  } catch (error) {
    loadError.value = '饮食记录加载失败，请重试';
    showRequestError(error, '饮食记录加载失败');
  } finally {
    loading.value = false;
  }
}
</script>
<template>
  <main class="view-page ui-page meals-page">
    <UiDetailHeader
      title="饮食记录"
      subtitle="每一餐，都留下清晰的记录"
      action-label="新记录"
      @action="router.push('/meals/create')"
    />
    <UiTabs
      :model-value="activeType"
      :options="filters"
      :disabled="loading"
      label="按餐次筛选"
      @update:model-value="selectType($event as MealType | 'ALL')"
    />
    <Loading
      class="page-loading"
      :class="{ 'page-loading--active': loading }"
      :loading="loading"
      text="正在读取饮食记录"
    >
      <UiEmptyState
        v-if="!loading && loadError"
        :title="loadError"
        description="请检查网络后再试"
        action-label="重新加载"
        @action="retry"
      />
      <UiEmptyState
        v-else-if="!loading && !meals.length"
        kind="meal"
        title="还没有饮食记录"
        description="从今天的一餐开始"
        action-label="记录第一餐"
        @action="router.push('/meals/create')"
      />
      <div v-else class="diary-list">
        <section v-for="group in groupedMeals" :key="group.date">
          <div class="day-heading">
            <h2>{{ dateTitle(group.date) }}</h2>
            <span>{{ dayjs(group.date).format('YYYY年M月D日') }}</span>
          </div>
          <div class="history-list">
            <article v-for="meal in group.items" :key="meal.id" class="history-card">
              <UiRecordRow
                kind="meal"
                :image="meal.imageUrl || undefined"
                :title="mealLabels[meal.type]"
                :subtitle="`${dayjs(meal.recordedAt).format('HH:mm')} · ${meal.foods.map((food) => food.name).join('、')}`"
                :value="meal.totalCalories ?? '未记热量'"
                :unit="meal.totalCalories !== null ? 'kcal' : ''"
                @click="router.push(`/meals/${meal.id}`)"
              />
              <p v-if="meal.foods.some((food) => food.aiGenerated)" class="history-note">
                含 AI 识别食物，已确认保存
              </p>
              <p v-if="meal.note" class="history-note">{{ meal.note }}</p>
              <div class="history-actions">
                <UiButton
                  kind="text"
                  @click="router.push({ path: '/meals/create', query: { copyFrom: meal.id } })"
                  >再记一次</UiButton
                ><UiButton kind="text" @click="router.push(`/meals/${meal.id}/edit`)">编辑</UiButton
                ><UiButton kind="danger" @click="confirmDelete(meal)">删除</UiButton>
              </div>
            </article>
          </div>
        </section>
      </div>
      <UiButton
        v-if="meals.length < total"
        class="load-more"
        kind="secondary"
        block
        :loading="loadingMore"
        @click="loadMore"
        >加载更多</UiButton
      ></Loading
    >
  </main>
</template>
<style scoped lang="scss">
.history-list {
  display: grid;
  gap: 12px;
}
.history-card {
  overflow: hidden;
  background: var(--color-surface);
  border-radius: 20px;
}
.history-note {
  margin: 0;
  padding: 0 16px 12px;
  color: var(--ui-text-secondary);
  font-size: 12px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.history-actions {
  display: flex;
  justify-content: flex-end;
  gap: 20px;
  padding: 0 16px 8px;
}
.load-more {
  margin-top: 24px;
}
.diary-list {
  display: grid;
  gap: 28px;
}
.day-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  h2 {
    margin: 0;
    font-size: 18px;
  }
  span {
    color: var(--ui-text-secondary);
    font-size: 12px;
  }
}
</style>
