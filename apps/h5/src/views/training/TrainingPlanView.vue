<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ActionSheet, ToastPlugin } from 'tdesign-mobile-vue';
import type { TrainingPlanDay, TrainingTemplate } from '@fit-trace/shared';
import { getTrainingPlan, getTrainingTemplates, saveTrainingPlan } from '@/api/training';
import { showRequestError } from '@/utils/request-error';
import UiDetailHeader from '@/components/ui/UiDetailHeader.vue';
import UiButton from '@/components/ui/UiButton.vue';
const days = ref<TrainingPlanDay[]>([]);
const templates = ref<TrainingTemplate[]>([]);
const loading = ref(false);
const error = ref(false);
const saving = ref(false);
const selectedDay = ref<number | null>(null);
const week = [
  { day: 1, label: '周一' },
  { day: 2, label: '周二' },
  { day: 3, label: '周三' },
  { day: 4, label: '周四' },
  { day: 5, label: '周五' },
  { day: 6, label: '周六' },
  { day: 0, label: '周日' },
];
async function load() {
  loading.value = true;
  error.value = false;
  try {
    [days.value, templates.value] = await Promise.all([getTrainingPlan(), getTrainingTemplates()]);
  } catch (e) {
    error.value = true;
    showRequestError(e, '计划加载失败');
  } finally {
    loading.value = false;
  }
}
function label(day: number) {
  const id = days.value.find((d) => d.weekday === day)?.templateId;
  return id ? (templates.value.find((t) => t.id === id)?.name ?? '模板不存在，请重新选择') : '休息';
}
function choose(_selected: unknown, index: number) {
  const day = days.value.find((d) => d.weekday === selectedDay.value);
  if (day) day.templateId = index === 0 ? null : templates.value[index - 1].id;
  selectedDay.value = null;
}
async function save() {
  if (saving.value || loading.value || error.value) return;
  saving.value = true;
  try {
    await saveTrainingPlan(days.value);
    ToastPlugin.success('每周计划已保存');
  } catch (e) {
    showRequestError(e, '计划保存失败，请重试');
  } finally {
    saving.value = false;
  }
}
onMounted(load);
</script>
<template>
  <main class="view-page ui-page plan-view">
    <UiDetailHeader title="训练计划" subtitle="安排每周训练，也可以随时调整" />
    <p v-if="loading">正在读取计划…</p>
    <div v-else-if="error">计划加载失败<UiButton kind="text" @click="load">重试</UiButton></div>
    <template v-else
      ><section v-for="day in week" :key="day.day" class="plan-day">
        <h2>{{ day.label }}</h2>
        <UiButton kind="text" @click="selectedDay = day.day">{{ label(day.day) }} ›</UiButton>
      </section>
      <UiButton block :loading="saving" @click="save">保存计划</UiButton></template
    ><ActionSheet
      :visible="selectedDay !== null"
      :items="['休息', ...templates.map((t) => t.name)]"
      cancel-text="取消"
      @update:visible="!$event && (selectedDay = null)"
      @selected="choose"
    />
  </main>
</template>
<style scoped lang="scss">
.plan-view {
  display: grid;
  gap: 12px;
}
.plan-day {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  background: var(--color-surface);
  padding: 12px 16px;
  border-radius: 16px;
  h2 {
    font-size: 16px;
    margin: 0;
  }
  :deep(.ui-button) {
    max-width: 75%;
  }
}
</style>
