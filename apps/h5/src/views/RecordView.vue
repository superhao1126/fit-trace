<script setup lang="ts">
import {
  ActivityIcon,
  CameraIcon,
  ChevronRightIcon,
  ForkIcon,
  MeasurementIcon,
} from 'tdesign-icons-vue-next';
import { useRoute, useRouter } from 'vue-router';
import RecordDetailHeader from '@/components/RecordDetailHeader.vue';

const router = useRouter();
const route = useRoute();
function openRecord(path: string) {
  void router.push({ path, query: { date: route.query.date } });
}
const entries = [
  {
    title: '身体数据',
    description: '体重、体脂和身体围度',
    icon: MeasurementIcon,
    path: '/body/create',
  },
  {
    title: '饮食记录',
    description: '记录每一餐和食物',
    icon: ForkIcon,
    path: '/meals/create',
  },
  {
    title: '训练记录',
    description: '训练类型、时长与消耗',
    icon: ActivityIcon,
    path: '/workouts/create',
  },
  {
    title: '身材照片',
    description: '留下不同阶段的变化',
    icon: CameraIcon,
    path: '/photos',
  },
];
</script>

<template>
  <main class="view-page">
    <RecordDetailHeader
      title="添加记录"
      :subtitle="route.query.date ? `为 ${route.query.date} 添加记录` : '选择想要记录的内容'"
    />

    <div class="entry-list">
      <button
        v-for="entry in entries"
        :key="entry.title"
        class="surface-card entry-card"
        type="button"
        @click="openRecord(entry.path)"
      >
        <div class="entry-card__symbol"><component :is="entry.icon" /></div>
        <div class="entry-card__content">
          <div class="entry-card__title">
            <strong>{{ entry.title }}</strong>
          </div>
          <span>{{ entry.description }}</span>
        </div>
        <ChevronRightIcon />
      </button>
    </div>
  </main>
</template>

<style scoped lang="scss">
.entry-list {
  display: grid;
  gap: 10px;
  margin-bottom: var(--spacing-md);
}

.entry-card {
  display: flex;
  width: 100%;
  text-align: left;
  color: var(--color-text-primary);
  align-items: center;
  gap: 12px;
  padding: 16px;

  &__symbol {
    display: grid;
    width: 36px;
    height: 36px;
    flex: none;
    place-items: center;
    color: var(--color-text-primary);
    font-size: 1.25rem;
    background: var(--color-primary-light);
    border-radius: 10px;
  }

  &__content {
    display: flex;
    min-width: 0;
    flex: 1;
    flex-direction: column;
    gap: 4px;

    > span {
      color: var(--color-text-secondary);
      font-size: 0.8125rem;
    }
  }

  &__title {
    display: flex;
    align-items: center;
    gap: 7px;
  }
}
</style>
