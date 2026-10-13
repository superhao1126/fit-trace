<script setup lang="ts">
import UiLoading from '@/components/ui/UiLoading.vue';
import UiDetailHeader from '@/components/ui/UiDetailHeader.vue';
import UiButton from '@/components/ui/UiButton.vue';
import UiRecordRow from '@/components/ui/UiRecordRow.vue';
import UiTabs from '@/components/ui/UiTabs.vue';
import UiEmptyState from '@/components/ui/UiEmptyState.vue';
import { showRequestError } from '@/utils/request-error';
import type { WorkoutRecord, WorkoutType } from '@fit-trace/shared';
import dayjs from 'dayjs';
import { DialogPlugin, ToastPlugin } from 'tdesign-mobile-vue';
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { deleteWorkout, getWorkouts } from '@/api/workouts';

const workoutTypes: Array<{ value: WorkoutType; label: string }> = [
  { value: 'STRENGTH', label: '力量' },
  { value: 'CARDIO', label: '有氧' },
  { value: 'RUNNING', label: '跑步' },
  { value: 'CYCLING', label: '骑行' },
  { value: 'SWIMMING', label: '游泳' },
  { value: 'OTHER', label: '其他' },
];
const workoutMeta = Object.fromEntries(workoutTypes.map((item) => [item.value, item])) as Record<
  WorkoutType,
  { value: WorkoutType; label: string }
>;
const filters: Array<{ value: WorkoutType | 'ALL'; label: string }> = [
  { value: 'ALL', label: '全部' },
  ...workoutTypes.map((item) => ({ value: item.value, label: item.label })),
];

const router = useRouter();
const workouts = ref<WorkoutRecord[]>([]);
const loading = ref(true);
const loadError = ref('');
const loadingMore = ref(false);
const page = ref(1);
const total = ref(0);
const activeType = ref<WorkoutType | 'ALL'>('ALL');
const pageSize = 20;

async function load(reset = false): Promise<void> {
  loadError.value = '';
  if (reset) {
    page.value = 1;
    workouts.value = [];
  }
  const result = await getWorkouts({
    page: page.value,
    pageSize,
    ...(activeType.value === 'ALL' ? {} : { type: activeType.value }),
  });
  workouts.value.push(...result.data);
  total.value = result.meta.total;
}

async function selectType(type: WorkoutType | 'ALL'): Promise<void> {
  if (type === activeType.value) return;
  activeType.value = type;
  loading.value = true;
  try {
    await load(true);
  } catch (error) {
    loadError.value = '训练记录加载失败，请重试';
    showRequestError(error, '训练记录加载失败');
  } finally {
    loading.value = false;
  }
}

async function loadMore(): Promise<void> {
  loadingMore.value = true;
  page.value += 1;
  try {
    await load();
  } catch (error) {
    page.value -= 1;
    showRequestError(error, '加载更多记录失败');
  } finally {
    loadingMore.value = false;
  }
}

function dateLabel(value: string): string {
  const date = dayjs(value);
  if (date.isSame(dayjs(), 'day')) return `今天 ${date.format('HH:mm')}`;
  if (date.isSame(dayjs().subtract(1, 'day'), 'day')) return `昨天 ${date.format('HH:mm')}`;
  return date.format('M月D日 HH:mm');
}

function confirmDelete(workout: WorkoutRecord): void {
  let deleting = false;
  const dialog = DialogPlugin.confirm({
    title: '删除这条训练记录？',
    content: `${dayjs(workout.startedAt).format('YYYY年M月D日')} · ${workout.name}，删除后无法恢复`,
    confirmBtn: { content: '删除', theme: 'danger' },
    cancelBtn: '取消',
    onConfirm: async () => {
      if (deleting) return;
      deleting = true;
      dialog.update({ confirmBtn: { content: '删除中…', theme: 'danger', loading: true } });
      try {
        await deleteWorkout(workout.id);
        workouts.value = workouts.value.filter((item) => item.id !== workout.id);
        total.value -= 1;
        ToastPlugin.success('训练记录已删除');
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
    loadError.value = '训练记录加载失败，请重试';
    showRequestError(error, '训练记录加载失败');
  } finally {
    loading.value = false;
  }
});
async function retry(): Promise<void> {
  loading.value = true;
  try {
    await load(true);
  } catch (error) {
    loadError.value = '训练记录加载失败，请重试';
    showRequestError(error, '训练记录加载失败');
  } finally {
    loading.value = false;
  }
}
</script>
<template>
  <main class="view-page ui-page workouts-page">
    <UiDetailHeader
      title="训练记录"
      subtitle="留下每一次训练，回看自己的节奏"
      action-label="新记录"
      @action="router.push('/workouts/create')"
    />
    <UiTabs
      :model-value="activeType"
      :options="filters"
      :disabled="loading"
      :columns="4"
      label="按训练类型筛选"
      @update:model-value="selectType($event as WorkoutType | 'ALL')"
    />
    <UiLoading
      class="page-loading"
      :class="{ 'page-loading--active': loading }"
      :loading="loading"
      text="正在读取训练记录"
    >
      <UiEmptyState
        v-if="!loading && loadError"
        :title="loadError"
        description="请检查网络后再试"
        action-label="重新加载"
        @action="retry"
      />
      <UiEmptyState
        v-else-if="!loading && !workouts.length"
        kind="workout"
        title="还没有训练记录"
        description="完成一次训练，留下今天的进步"
        action-label="记训练"
        @action="router.push('/workouts/create')"
      />
      <div v-else class="history-list">
        <article v-for="workout in workouts" :key="workout.id" class="history-card">
          <UiRecordRow
            kind="workout"
            :workout-type="workout.type"
            :title="workout.name"
            :subtitle="`${dateLabel(workout.startedAt)} · ${workoutMeta[workout.type].label}`"
            :value="workout.durationMinutes"
            unit="分钟"
            @click="router.push(`/workouts/${workout.id}`)"
          />
          <p v-if="workout.calories !== null" class="history-note">
            消耗热量 {{ workout.calories }} kcal
          </p>
          <p v-if="workout.note" class="history-note">{{ workout.note }}</p>
          <div class="history-actions">
            <UiButton
              kind="text"
              @click="router.push({ path: '/workouts/create', query: { copyFrom: workout.id } })"
              >再练一次</UiButton
            ><UiButton kind="text" @click="router.push(`/workouts/${workout.id}/edit`)"
              >编辑</UiButton
            ><UiButton kind="danger" @click="confirmDelete(workout)">删除</UiButton>
          </div>
        </article>
      </div>
      <UiButton
        v-if="workouts.length < total"
        class="load-more"
        kind="secondary"
        block
        :loading="loadingMore"
        @click="loadMore"
        >加载更多</UiButton
      ></UiLoading
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
</style>
