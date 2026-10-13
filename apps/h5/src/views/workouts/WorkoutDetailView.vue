<script setup lang="ts">
import { confirmDialog } from '@/components/ui/confirm';
import UiLoading from '@/components/ui/UiLoading.vue';
import { showRequestError } from '@/utils/request-error';
import type { WorkoutRecord, WorkoutType } from '@fit-trace/shared';
import dayjs from 'dayjs';
import { uiToast as ToastPlugin } from '@/components/ui/toast';
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { deleteWorkout, getWorkout } from '@/api/workouts';
import UiDetailHeader from '@/components/ui/UiDetailHeader.vue';
import UiButton from '@/components/ui/UiButton.vue';
import UiMetricCard from '@/components/ui/UiMetricCard.vue';
import UiInfoCard from '@/components/ui/UiInfoCard.vue';

const workoutLabels: Record<WorkoutType, string> = {
  STRENGTH: '力量',
  CARDIO: '有氧',
  RUNNING: '跑步',
  CYCLING: '骑行',
  SWIMMING: '游泳',
  OTHER: '其他',
};

const route = useRoute();
const router = useRouter();
const workoutId = computed(() => (typeof route.params.id === 'string' ? route.params.id : ''));
const loading = ref(true);
const workout = ref<WorkoutRecord | null>(null);
const startedAtText = computed(() =>
  workout.value ? dayjs(workout.value.startedAt).format('YYYY年M月D日 HH:mm') : '',
);

function confirmDelete(): void {
  const target = workout.value;
  if (!target) return;
  let deleting = false;
  const dialog = confirmDialog({
    title: '删除这条训练记录？',
    content: `${dayjs(target.startedAt).format('YYYY年M月D日')} · ${target.name}，删除后无法恢复`,
    confirmBtn: { content: '删除', theme: 'danger' },
    cancelBtn: '取消',
    onConfirm: async () => {
      if (deleting) return;
      deleting = true;
      dialog.update({ confirmBtn: { content: '删除中…', theme: 'danger', loading: true } });
      try {
        await deleteWorkout(target.id);
        ToastPlugin.success('训练记录已删除');
        dialog.destroy();
        await router.replace('/workouts');
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
    workout.value = await getWorkout(workoutId.value);
  } catch (error) {
    showRequestError(error, '记录不存在或已被删除');
    await router.replace('/workouts');
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <main class="view-page ui-page">
    <UiDetailHeader
      title="训练记录"
      :subtitle="startedAtText"
      action-label="编辑"
      @action="router.push(`/workouts/${workoutId}/edit`)"
    /><UiLoading
      class="page-loading"
      :class="{ 'page-loading--active': loading }"
      :loading="loading"
      text="正在读取记录"
      ><template v-if="workout"
        ><UiMetricCard
          kind="workout"
          :workout-type="workout.type"
          :label="workout.name"
          :value="workout.durationMinutes"
          unit="分钟"
          :hint="workout.calories === null ? '未记录消耗热量' : `消耗约 ${workout.calories} kcal`"
        /><UiInfoCard
          :items="[
            { label: '开始时间', value: startedAtText },
            { label: '训练类型', value: workoutLabels[workout.type] },
            {
              label: '消耗热量',
              value: workout.calories === null ? '未记录' : `${workout.calories} kcal`,
            },
            { label: '备注', value: workout.note || '无' },
          ]"
        />
        <section v-if="workout.exercises?.length" class="recorded-exercises">
          <article
            v-for="(exercise, index) in workout.exercises"
            :key="`${exercise.exerciseId}-${index}`"
          >
            <h2>{{ exercise.name }}</h2>
            <p v-for="(set, setIndex) in exercise.sets" :key="setIndex">
              第 {{ setIndex + 1 }} 组 · {{ set.reps }}
              {{ exercise.unit === 'seconds' ? '秒' : '次' }} · {{ set.weightKg }} kg
            </p>
          </article>
        </section>
        <div class="ui-detail-actions">
          <UiButton kind="danger" @click="confirmDelete">删除这条记录</UiButton>
        </div></template
      ></UiLoading
    >
  </main>
</template>
<style scoped lang="scss">
.recorded-exercises {
  display: grid;
  gap: 12px;
  margin-top: 16px;
  article {
    padding: 16px;
    background: var(--color-surface);
    border-radius: var(--ui-radius-card);
  }
  h2 {
    font-size: 16px;
    margin: 0 0 12px;
  }
  p {
    font-size: 13px;
    color: var(--ui-text-secondary);
  }
}
</style>
