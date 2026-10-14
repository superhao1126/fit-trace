<script setup lang="ts">
import { useRouter } from 'vue-router';
import TrainingTodayCard from '@/components/TrainingTodayCard.vue';
import UiDetailHeader from '@/components/ui/UiDetailHeader.vue';
import UiRecordRow from '@/components/ui/UiRecordRow.vue';
import UiButton from '@/components/ui/UiButton.vue';
const router = useRouter();
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
    <TrainingTodayCard />
    <UiRecordRow
      v-for="entry in entries"
      :key="entry.path"
      kind="workout"
      :title="entry.title"
      :image="entry.image"
      :subtitle="entry.subtitle"
      @click="router.push(entry.path)"
    />
    <UiButton block kind="secondary" @click="router.push('/workouts')">查看训练记录</UiButton>
  </main>
</template>
<style scoped lang="scss">
.training-tools {
  display: grid;
  gap: 16px;
}
</style>
