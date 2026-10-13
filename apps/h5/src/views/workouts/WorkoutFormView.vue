<script setup lang="ts">
import UiLoading from '@/components/ui/UiLoading.vue';
import { recordDateTime } from '@/utils/record-date';
import UiDetailHeader from '@/components/ui/UiDetailHeader.vue';
import UiButton from '@/components/ui/UiButton.vue';
import UiInput from '@/components/ui/UiInput.vue';
import UiTextarea from '@/components/ui/UiTextarea.vue';
import UiIcon from '@/components/ui/UiIcon.vue';
import UiTabs from '@/components/ui/UiTabs.vue';
import UiDateTimePicker from '@/components/ui/UiDateTimePicker.vue';
import UiRecordRow from '@/components/ui/UiRecordRow.vue';
import { showRequestError } from '@/utils/request-error';
import type { WorkoutRecord, WorkoutType } from '@fit-trace/shared';
import dayjs from 'dayjs';
import { ToastPlugin } from 'tdesign-mobile-vue';
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  createWorkout,
  getWorkout,
  getWorkouts,
  updateWorkout,
  type WorkoutInput,
} from '@/api/workouts';
import SportIcon from '@/components/SportIcon.vue';

const workoutTypes: Array<{ value: WorkoutType; label: string }> = [
  { value: 'STRENGTH', label: '力量' },
  { value: 'CARDIO', label: '有氧' },
  { value: 'RUNNING', label: '跑步' },
  { value: 'CYCLING', label: '骑行' },
  { value: 'SWIMMING', label: '游泳' },
  { value: 'OTHER', label: '其他' },
];
const durationPresets = [15, 30, 45];

const route = useRoute();
const router = useRouter();
const workoutId = computed(() => (typeof route.params.id === 'string' ? route.params.id : null));
const copyFromId = computed(() =>
  typeof route.query.copyFrom === 'string' ? route.query.copyFrom : null,
);
const isEdit = computed(() => Boolean(workoutId.value));
const loading = ref(Boolean(workoutId.value || copyFromId.value));
const submitting = ref(false);
const datePickerVisible = ref(false);
const recentWorkouts = ref<WorkoutRecord[]>([]);
const templateSource = ref('');
const formData = reactive({
  type: 'STRENGTH' as WorkoutType,
  name: '',
  startedAt: recordDateTime(route.query.date),
  durationMinutes: '' as number | string,
  calories: '' as number | string,
  note: '',
});
const startedAtDisplay = computed(() => dayjs(formData.startedAt).format('YYYY-MM-DD HH:mm'));
const selectedType = computed(
  () => workoutTypes.find((item) => item.value === formData.type) ?? workoutTypes[0],
);
const latestWorkout = computed(() => recentWorkouts.value[0] ?? null);

function confirmStartedAt(value: string | number): void {
  formData.startedAt = dayjs(value).format('YYYY-MM-DD HH:mm:ss');
  datePickerVisible.value = false;
}

function optionalNumber(value: number | string): number | undefined {
  if (value === '') return undefined;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function applyWorkoutTemplate(workout: WorkoutRecord, announce = true): void {
  formData.type = workout.type;
  formData.name = workout.name;
  formData.durationMinutes = workout.durationMinutes;
  formData.calories = workout.calories ?? '';
  formData.note = workout.note ?? '';
  templateSource.value = `${dayjs(workout.startedAt).format('M月D日')} · ${workout.name}`;
  if (announce) ToastPlugin.success('已带入上次训练，可继续修改');
}

async function submit(): Promise<void> {
  if (submitting.value) return;
  if (!formData.name.trim()) {
    ToastPlugin.warning('请输入训练名称');
    return;
  }
  const duration = optionalNumber(formData.durationMinutes);
  if (duration === undefined || duration <= 0) {
    ToastPlugin.warning('请填写训练时长');
    return;
  }

  const input: WorkoutInput = {
    type: formData.type,
    name: formData.name.trim(),
    startedAt: dayjs(formData.startedAt).toISOString(),
    durationMinutes: Math.round(duration),
    calories: optionalNumber(formData.calories),
    note: formData.note.trim() || undefined,
  };

  submitting.value = true;
  try {
    if (workoutId.value) {
      await updateWorkout(workoutId.value, input);
      ToastPlugin.success('训练记录已更新');
      await router.replace(`/workouts/${workoutId.value}`);
    } else {
      const created = await createWorkout(input);
      ToastPlugin.success('训练记录已保存');
      await router.replace(
        route.query.returnTo === '/dashboard'
          ? { path: '/dashboard', query: { date: dayjs(formData.startedAt).format('YYYY-MM-DD') } }
          : `/workouts/${created.id}`,
      );
    }
  } catch (error) {
    showRequestError(error, '保存失败，请稍后重试');
  } finally {
    submitting.value = false;
  }
}

onMounted(async () => {
  try {
    if (workoutId.value) {
      const workout = await getWorkout(workoutId.value);
      formData.type = workout.type;
      formData.name = workout.name;
      formData.startedAt = dayjs(workout.startedAt).format('YYYY-MM-DD HH:mm:ss');
      formData.durationMinutes = workout.durationMinutes;
      formData.calories = workout.calories ?? '';
      formData.note = workout.note ?? '';
      return;
    }

    const result = await getWorkouts({ page: 1, pageSize: 8 });
    recentWorkouts.value = result.data;
    if (copyFromId.value) {
      const source =
        result.data.find((workout) => workout.id === copyFromId.value) ??
        (await getWorkout(copyFromId.value));
      applyWorkoutTemplate(source, false);
    }
  } catch {
    if (workoutId.value || copyFromId.value) await router.replace('/workouts');
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <main class="view-page ui-page ui-form workout-form-page">
    <UiDetailHeader :title="isEdit ? '编辑训练记录' : '记训练'" subtitle="记录训练内容与时长" />
    <UiLoading
      class="page-loading"
      :class="{ 'page-loading--active': loading }"
      :loading="loading"
      text="正在读取训练记录"
    >
      <div v-if="!isEdit && latestWorkout" class="workout-reuse">
        <UiRecordRow
          kind="workout"
          :workout-type="latestWorkout.type"
          title="复用上次训练"
          :subtitle="`${latestWorkout.name} · ${latestWorkout.durationMinutes} 分钟`"
          value="带入"
          @click="applyWorkoutTemplate(latestWorkout)"
        />
        <p v-if="templateSource">已带入 {{ templateSource }}，记录时间保持当前选择</p>
      </div>
      <div class="field-block">
        <span class="field-label">训练类型</span
        ><UiTabs
          :model-value="formData.type"
          :options="workoutTypes"
          tone="workout"
          :columns="3"
          label="训练类型"
          @update:model-value="formData.type = $event as WorkoutType"
          ><template #option="{ option }"
            ><span class="sport-option"
              ><SportIcon :type="option.value as WorkoutType" />{{ option.label }}</span
            ></template
          ></UiTabs
        >
      </div>
      <label class="field-block"
        ><span class="field-label">训练名称</span
        ><UiInput
          v-model="formData.name"
          :maxlength="60"
          :placeholder="`例如：${selectedType.label}训练`"
          aria-label="训练名称"
      /></label>
      <label class="field-block"
        ><span class="field-label">训练时长</span
        ><UiInput
          v-model="formData.durationMinutes"
          type="number"
          inputmode="numeric"
          suffix="分钟"
          placeholder="45"
          aria-label="训练时长"
      /></label>
      <UiTabs
        :model-value="Number(formData.durationMinutes)"
        :options="durationPresets.map((value) => ({ value, label: `${value} 分钟` }))"
        label="快捷训练时长"
        @update:model-value="formData.durationMinutes = Number($event)"
      />
      <div class="form-meta">
        <span class="field-label">开始时间</span
        ><button type="button" @click="datePickerVisible = true">
          {{ startedAtDisplay }}<UiIcon name="right" />
        </button>
      </div>
      <details
        class="extra-fields"
        :open="isEdit || formData.calories !== '' || Boolean(formData.note)"
      >
        <summary>热量与备注<span>选填</span><UiIcon name="right" /></summary>
        <label class="field-block"
          ><span class="field-label">消耗热量</span
          ><UiInput
            v-model="formData.calories"
            type="number"
            inputmode="decimal"
            suffix="kcal"
            placeholder="例如 320"
            aria-label="消耗热量" /></label
        ><label class="field-block"
          ><span class="field-label">备注</span
          ><UiTextarea
            v-model="formData.note"
            :maxlength="500"
            :autosize="{ minRows: 3, maxRows: 6 }"
            placeholder="例如：状态不错、完成 5.2 公里"
            aria-label="备注"
        /></label>
      </details>
      <UiButton class="form-submit" block :loading="submitting" @click="submit">{{
        isEdit ? '保存修改' : '保存训练记录'
      }}</UiButton> </UiLoading
    ><UiDateTimePicker
      v-model:visible="datePickerVisible"
      :value="formData.startedAt"
      title="选择开始时间"
      @confirm="confirmStartedAt"
    />
  </main>
</template>
<style scoped lang="scss">
.sport-option {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  svg {
    width: 18px;
    height: 18px;
  }
}
.workout-reuse {
  margin-bottom: 24px;
  p {
    margin: 10px 0;
    color: var(--ui-text-secondary);
    font-size: 12px;
  }
}
.field-block .ui-tabs {
  margin-bottom: 0;
}
</style>
