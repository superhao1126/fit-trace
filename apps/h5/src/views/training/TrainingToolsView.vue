<script setup lang="ts">
import { onMounted, ref, computed } from 'vue';
import { useRouter } from 'vue-router';
import dayjs from 'dayjs';
import type { TrainingTemplate } from '@fit-trace/shared';
import UiDetailHeader from '@/components/ui/UiDetailHeader.vue';
import UiRecordRow from '@/components/ui/UiRecordRow.vue';
import UiButton from '@/components/ui/UiButton.vue';
import { getTrainingPlan, getTrainingTemplates } from '@/api/training';
const router = useRouter();
const today = ref<TrainingTemplate | null>(null);
const planState = ref<'loading' | 'ready' | 'error'>('loading');
const summary = computed(() =>
  today.value
    ? `${today.value.exercises.length} 个动作 · 预计 ${today.value.durationMinutes} 分钟`
    : '今天安排为休息日',
);
async function load() {
  planState.value = 'loading';
  try {
    const [plan, templates] = await Promise.all([getTrainingPlan(), getTrainingTemplates()]);
    today.value =
      templates.find((t) => t.id === plan.find((d) => d.weekday === dayjs().day())?.templateId) ??
      null;
    planState.value = 'ready';
  } catch {
    planState.value = 'error';
  }
}
onMounted(load);
const entries = [
  {
    title: '动作库',
    subtitle: '按部位和器械筛选，查看动作要点',
    image: '/images/tools/dumbbells.webp',
    path: '/training/exercises',
  },
  {
    title: '训练模板',
    subtitle: '组合动作，设置组数、次数和休息',
    image: '/images/tools/template.webp',
    path: '/training/templates',
  },
  {
    title: '我的计划',
    subtitle: '按周安排训练与休息日',
    image: '/images/tools/calendar.webp',
    path: '/training/plan',
  },
];
</script>
<template>
  <main class="view-page ui-page training-tools">
    <UiDetailHeader title="运动工具" subtitle="从动作到计划，记录每次训练" />
    <section class="training-card">
      <h2>今日计划</h2>
      <p>
        {{
          planState === 'loading'
            ? '正在读取计划…'
            : planState === 'error'
              ? '计划加载失败，请重试'
              : (today?.name ?? '休息日')
        }}
      </p>
      <small v-if="planState === 'ready'">{{ summary }}</small
      ><UiButton v-if="planState === 'error'" kind="text" @click="load">重新加载</UiButton
      ><UiButton
        v-else-if="today"
        kind="text"
        @click="
          router.push({
            path: '/training/session',
            query: { template: today.id },
          })
        "
        >开始训练</UiButton
      ><UiButton v-else kind="text" @click="router.push('/training/plan')">安排计划</UiButton>
    </section>
    <UiRecordRow
      v-for="entry in entries"
      :key="entry.path"
      kind="workout"
      :title="entry.title"
      :image="entry.image"
      :subtitle="entry.subtitle"
      @click="router.push(entry.path)"
    />
    <UiButton block @click="router.push('/training/session')">自由训练</UiButton>
    <UiButton block kind="secondary" @click="router.push('/workouts')">查看训练记录</UiButton>
  </main>
</template>
<style scoped lang="scss">
.training-tools {
  display: grid;
  gap: 16px;
}
.training-card {
  background: var(--color-surface);
  border-radius: var(--ui-radius-card);
  padding: 20px;
  h2 {
    font-size: 17px;
    margin: 0;
  }
  p {
    font-size: 16px;
    margin: 12px 0 8px;
  }
  small {
    display: block;
    color: var(--ui-text-secondary);
    font-size: 12px;
  }
}
</style>
