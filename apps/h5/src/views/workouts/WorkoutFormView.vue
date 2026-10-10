<script setup lang="ts">
import { recordDateTime } from '@/utils/record-date';
import RecordDetailHeader from '@/components/RecordDetailHeader.vue';
import { showRequestError } from '@/utils/request-error';
import type { WorkoutRecord, WorkoutType } from '@fit-trace/shared';
import dayjs from 'dayjs';
import {
  Button,
  DateTimePicker,
  Input,
  Loading,
  Popup,
  Textarea,
  ToastPlugin,
} from 'tdesign-mobile-vue';
import { CalendarIcon, ChevronRightIcon } from 'tdesign-icons-vue-next';
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
const durationPresets = [20, 30, 45, 60, 90];

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
  <main class="view-page record-form workout-form-page">
    <RecordDetailHeader :title="isEdit ? '编辑训练记录' : '记训练'" subtitle="记录训练内容和时长" />

    <Loading
      class="page-loading"
      :class="{ 'page-loading--active': loading }"
      :loading="loading"
      text="正在读取训练记录"
    >
      <section class="surface-card workout-form-card">
        <div v-if="!isEdit && latestWorkout" class="quick-start field-block">
          <div>
            <span class="field-label">快速开始</span>
            <small>带入训练内容，开始时间仍使用现在</small>
          </div>
          <button type="button" @click="applyWorkoutTemplate(latestWorkout)">
            <span>
              <strong>复用上次训练</strong>
              <small> {{ latestWorkout.name }} · {{ latestWorkout.durationMinutes }} 分钟 </small>
            </span>
            <span>{{ templateSource || '带入' }}</span>
          </button>
        </div>

        <div class="field-block">
          <span class="field-label">训练类型</span>
          <div class="workout-type-grid">
            <button
              v-for="item in workoutTypes"
              :key="item.value"
              type="button"
              :class="{ active: formData.type === item.value }"
              :aria-pressed="formData.type === item.value"
              @click="formData.type = item.value"
            >
              <span class="workout-type-grid__icon"><SportIcon :type="item.value" /></span>
              <strong>{{ item.label }}</strong>
            </button>
          </div>
        </div>

        <div class="field-block">
          <span class="field-label">训练名称</span>
          <Input
            v-model="formData.name"
            :maxlength="60"
            :placeholder="`例如：${selectedType.label}训练`"
          />
        </div>

        <div class="field-block">
          <span class="field-label">训练时长</span>
          <Input
            v-model="formData.durationMinutes"
            type="number"
            inputmode="numeric"
            suffix="分钟"
            placeholder="45"
          />
          <div class="preset-row">
            <button
              v-for="preset in durationPresets"
              :key="preset"
              type="button"
              :class="{ active: Number(formData.durationMinutes) === preset }"
              @click="formData.durationMinutes = preset"
            >
              {{ preset }} 分钟
            </button>
          </div>
        </div>

        <button class="form-meta" type="button" @click="datePickerVisible = true">
          <span>开始时间</span><span>{{ startedAtDisplay }} <CalendarIcon /></span>
        </button>
        <details
          class="workout-extra"
          :open="isEdit || formData.calories !== '' || Boolean(formData.note)"
        >
          <summary>热量与备注 <span>选填</span><ChevronRightIcon class="expand-icon" /></summary>
          <div class="field-block">
            <span class="field-label">消耗热量（可选）</span>
            <Input v-model="formData.calories" type="number" suffix="kcal" placeholder="例如 320" />
          </div>

          <div class="field-block note-field">
            <span class="field-label">备注</span>
            <Textarea
              v-model="formData.note"
              :maxlength="500"
              :autosize="{ minRows: 3, maxRows: 6 }"
              placeholder="例如：状态不错、组间休息偏长等"
            />
          </div>
        </details>
        <Button theme="primary" size="large" block :loading="submitting" @click="submit">
          {{ isEdit ? '保存修改' : '保存训练记录' }}
        </Button>
      </section>
    </Loading>

    <Popup v-model="datePickerVisible" placement="bottom">
      <DateTimePicker
        :value="formData.startedAt"
        title="选择开始时间"
        :mode="['date', 'minute']"
        format="YYYY-MM-DD HH:mm:ss"
        @confirm="confirmStartedAt"
        @cancel="datePickerVisible = false"
      />
    </Popup>
  </main>
</template>

<style scoped lang="scss">
.workout-form-card {
  width: 100%;
  padding: 18px;
}

.field-block {
  display: grid;
  gap: 8px;
  margin-bottom: 18px;
}

.field-label {
  color: var(--color-text-secondary);
  font-size: 0.875rem;
  font-weight: 500;
}

.quick-start {
  padding-bottom: 20px;
  border-bottom: 1px solid var(--color-border);

  > div {
    display: grid;
    gap: 3px;

    small {
      color: var(--color-text-tertiary);
      font-size: 0.7rem;
    }
  }

  > button {
    display: flex;
    width: 100%;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 12px;
    color: var(--color-text-primary);
    text-align: left;
    background: var(--color-surface-muted);
    border: 0;
    border-radius: 10px;

    > span:first-child {
      display: grid;
      min-width: 0;
      gap: 3px;
    }

    strong {
      font-size: 0.85rem;
    }

    small {
      color: var(--color-text-secondary);
      font-size: 0.72rem;
    }

    > span:last-child {
      flex: none;
      color: var(--color-accent-text);
      font-size: 0.7rem;
      font-weight: 500;
    }
  }
}

.workout-type-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
  button {
    display: flex;
    min-width: 0;
    min-height: 40px;
    align-items: center;
    justify-content: center;
    gap: 6px;
    padding: 8px 4px;
    color: var(--color-text-secondary);
    background: var(--color-surface-muted);
    border: 1px solid transparent;
    border-radius: 6px;
    strong {
      font-size: 0.8125rem;
      font-weight: 400;
    }
    &.active {
      color: var(--color-accent-text);
      background: var(--color-primary-light);
      border-color: var(--color-primary-border);
    }
  }
  &__icon {
    display: flex;
    font-size: 1rem;
  }
}

.preset-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  button {
    min-height: 36px;
    padding: 8px 10px;
    color: var(--color-text-secondary);
    background: transparent;
    border: 1px solid var(--color-border);
    border-radius: 6px;
    font-size: 0.75rem;
    &.active {
      color: var(--color-accent-text);
      background: var(--color-primary-light);
      border-color: var(--color-primary-border);
    }
  }
}

.note-field {
  margin-bottom: 22px;
}

@media (max-width: 360px) {
  .workout-type-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
.form-meta {
  display: flex;
  width: 100%;
  min-height: 44px;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 0;
  color: var(--color-text-secondary);
  background: transparent;
  border: 0;
  font-size: 0.75rem;
  > span:last-child {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}
.workout-extra {
  margin: 4px 0 18px;
  summary {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 14px 0;
    color: var(--color-text-secondary);
    font-size: 0.8125rem;
    span {
      color: var(--color-text-tertiary);
      font-size: 0.75rem;
    }
  }
}
</style>
