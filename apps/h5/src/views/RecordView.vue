<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router';
import UiDetailHeader from '@/components/ui/UiDetailHeader.vue';
import UiRecordRow from '@/components/ui/UiRecordRow.vue';
const router = useRouter();
const route = useRoute();
function openRecord(path: string) {
  void router.push({ path, query: { date: route.query.date } });
}
const entries = [
  {
    title: '身体数据',
    description: '体重、体脂和身体围度',
    kind: 'body' as const,
    path: '/body/create',
    image: '/images/tools/scale.webp',
  },
  {
    title: '饮食记录',
    description: '记录每一餐和食物',
    kind: 'meal' as const,
    path: '/meals',
    image: '/images/foods/rice.webp',
  },
  {
    title: '运动工具',
    description: '动作库、模板、计划与训练记录',
    kind: 'workout' as const,
    path: '/training',
    image: '/images/tools/dumbbells.webp',
  },
  {
    title: '身材照片',
    description: '留下不同阶段的变化',
    kind: 'photo' as const,
    path: '/photos',
    image: '/images/tools/camera.webp',
  },
];
</script>

<template>
  <main class="view-page ui-page">
    <UiDetailHeader
      title="添加记录"
      :subtitle="route.query.date ? `为 ${route.query.date} 添加记录` : '选择想要记录的内容'"
    />
    <div class="entry-list">
      <UiRecordRow
        v-for="entry in entries"
        :key="entry.path"
        :kind="entry.kind"
        :title="entry.title"
        :image="entry.image"
        :subtitle="entry.description"
        @click="openRecord(entry.path)"
      />
    </div>
  </main>
</template>
<style scoped>
.entry-list {
  display: grid;
  gap: 16px;
  margin-top: 8px;
}
</style>
