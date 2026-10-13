<script setup lang="ts">
import { confirmDialog } from '@/components/ui/confirm';
import UiLoading from '@/components/ui/UiLoading.vue';
import { showRequestError } from '@/utils/request-error';
import type { MealRecord, MealType } from '@fit-trace/shared';
import dayjs from 'dayjs';
import { ToastPlugin } from 'tdesign-mobile-vue';
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { deleteMeal, getMeal } from '@/api/meals';
import UiDetailHeader from '@/components/ui/UiDetailHeader.vue';
import UiButton from '@/components/ui/UiButton.vue';
import UiMetricCard from '@/components/ui/UiMetricCard.vue';
import UiInfoCard from '@/components/ui/UiInfoCard.vue';
import UiRecordRow from '@/components/ui/UiRecordRow.vue';

const mealLabels: Record<MealType, string> = {
  BREAKFAST: '早餐',
  LUNCH: '午餐',
  DINNER: '晚餐',
  SNACK: '加餐',
};

const route = useRoute();
const router = useRouter();
const mealId = computed(() => (typeof route.params.id === 'string' ? route.params.id : ''));
const loading = ref(true);
const meal = ref<MealRecord | null>(null);
const recordedAtText = computed(() =>
  meal.value ? dayjs(meal.value.recordedAt).format('YYYY年M月D日 HH:mm') : '',
);
const aiCount = computed(() => meal.value?.foods.filter((food) => food.aiGenerated).length ?? 0);

function confirmDelete(): void {
  const target = meal.value;
  if (!target) return;
  let deleting = false;
  const dialog = confirmDialog({
    title: '删除这条饮食记录？',
    content: `${dayjs(target.recordedAt).format('YYYY年M月D日')} · ${mealLabels[target.type]}，删除后无法恢复`,
    confirmBtn: { content: '删除', theme: 'danger' },
    cancelBtn: '取消',
    onConfirm: async () => {
      if (deleting) return;
      deleting = true;
      dialog.update({ confirmBtn: { content: '删除中…', theme: 'danger', loading: true } });
      try {
        await deleteMeal(target.id);
        ToastPlugin.success('饮食记录已删除');
        dialog.destroy();
        await router.replace('/meals');
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
    meal.value = await getMeal(mealId.value);
  } catch (error) {
    showRequestError(error, '记录不存在或已被删除');
    await router.replace('/meals');
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <main class="view-page ui-page">
    <UiDetailHeader
      title="饮食记录"
      :subtitle="recordedAtText"
      action-label="编辑"
      @action="router.push(`/meals/${mealId}/edit`)"
    /><UiLoading
      class="page-loading"
      :class="{ 'page-loading--active': loading }"
      :loading="loading"
      text="正在读取记录"
      ><template v-if="meal"
        ><UiMetricCard
          kind="meal"
          :label="mealLabels[meal.type]"
          :value="meal.totalCalories ?? '—'"
          unit="kcal"
          :hint="meal.totalCalories === null ? '这一餐没有记录热量' : '按已记录食物合计'"
        /><img v-if="meal.imageUrl" class="meal-photo" :src="meal.imageUrl" alt="餐食照片" />
        <section class="food-section">
          <div class="section-heading">
            <h2>食物明细</h2>
            <span
              >{{ meal.foods.length }} 项<template v-if="aiCount">
                · {{ aiCount }} 项来自识别</template
              ></span
            >
          </div>
          <div class="food-list">
            <UiRecordRow
              v-for="food in meal.foods"
              :key="food.id"
              kind="meal"
              :title="food.name"
              :subtitle="`${food.amount || '份量未记'}${food.aiGenerated ? ' · 已核对识别' : ''}`"
              :value="food.calories ?? '—'"
              unit="kcal"
              informational
            />
          </div>
        </section>
        <UiInfoCard :items="[{ label: '备注', value: meal.note || '无' }]" />
        <div class="ui-detail-actions">
          <UiButton kind="danger" @click="confirmDelete">删除这条记录</UiButton>
        </div></template
      ></UiLoading
    >
  </main>
</template>
<style scoped lang="scss">
.meal-photo {
  width: 100%;
  max-height: 260px;
  margin-top: 20px;
  object-fit: cover;
  border-radius: 20px;
}
.food-section {
  margin-top: 28px;
}
.section-heading > span {
  font-size: 12px;
  color: var(--ui-text-secondary);
}
.food-list {
  display: grid;
  gap: 10px;
}
</style>
