<script setup lang="ts">
import RecordDetailHeader from '@/components/RecordDetailHeader.vue';
import { showRequestError } from '@/utils/request-error';
import type { FitnessGoalType } from '@fit-trace/shared';
import dayjs from 'dayjs';
import { Button, Input, Loading, ToastPlugin } from 'tdesign-mobile-vue';
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { getLatestBodyRecord } from '@/api/body-records';
import { updateFitnessGoal } from '@/api/users';
import { useAuthStore } from '@/stores/auth';

const goalOptions: Array<{
  value: FitnessGoalType;
  title: string;
  description: string;
}> = [
  { value: 'LOSE_FAT', title: '减脂', description: '让体重和围度稳步下降' },
  { value: 'GAIN_MUSCLE', title: '增肌', description: '关注体重、训练与身体变化' },
  { value: 'MAINTAIN', title: '保持', description: '维持当前状态和记录习惯' },
];

const router = useRouter();
const auth = useAuthStore();
const loading = ref(true);
const submitting = ref(false);
const currentWeight = ref<number | string>('');
const goalType = ref<FitnessGoalType>(auth.user?.goal?.type ?? 'LOSE_FAT');
const targetWeight = ref<number | string>(auth.user?.goal?.targetWeight ?? '');
const targetDate = ref(
  auth.user?.goal?.targetDate ? dayjs(auth.user.goal.targetDate).format('YYYY-MM-DD') : '',
);
const selectedGoal = computed(() => goalOptions.find((item) => item.value === goalType.value));

watch(goalType, (value) => {
  if (value === 'MAINTAIN' && currentWeight.value !== '') {
    targetWeight.value = currentWeight.value;
  }
});

async function submit(): Promise<void> {
  if (submitting.value) return;
  const current = Number(currentWeight.value);
  const target = Number(targetWeight.value);
  if (!Number.isFinite(current) || current <= 0) {
    ToastPlugin.warning('请先记录一次体重');
    return;
  }
  if (!Number.isFinite(target) || target <= 0) {
    ToastPlugin.warning('请输入正确的目标体重');
    return;
  }
  if (goalType.value === 'LOSE_FAT' && target >= current) {
    ToastPlugin.warning('减脂目标应低于当前体重');
    return;
  }
  if (goalType.value === 'GAIN_MUSCLE' && target <= current) {
    ToastPlugin.warning('增肌目标应高于当前体重');
    return;
  }
  if (targetDate.value && dayjs(targetDate.value).isBefore(dayjs(), 'day')) {
    ToastPlugin.warning('目标日期不能早于今天');
    return;
  }

  submitting.value = true;
  try {
    const user = await updateFitnessGoal({
      type: goalType.value,
      targetWeight: target,
      ...(targetDate.value
        ? { targetDate: dayjs(targetDate.value).endOf('day').toISOString() }
        : {}),
    });
    auth.updateUser(user);
    ToastPlugin.success('目标已保存');
    await router.replace('/dashboard');
  } catch (error) {
    showRequestError(error, '保存失败，请稍后重试');
  } finally {
    submitting.value = false;
  }
}

onMounted(async () => {
  try {
    const latest = await getLatestBodyRecord();
    if (latest) currentWeight.value = latest.weight;
  } catch (error) {
    showRequestError(error, '当前体重读取失败，请稍后重试');
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <main class="view-page record-form goal-page">
    <RecordDetailHeader title="我的目标" subtitle="选择适合自己的目标和节奏" />

    <Loading
      class="page-loading"
      :class="{ 'page-loading--active': loading }"
      :loading="loading"
      text="正在读取身体数据"
    >
      <section class="goal-section">
        <div class="section-heading">
          <h2>你现在更关注什么？</h2>
          <span>之后可以随时调整</span>
        </div>
        <div class="goal-options">
          <button
            v-for="item in goalOptions"
            :key="item.value"
            type="button"
            :class="{ active: goalType === item.value }"
            @click="goalType = item.value"
          >
            <strong>{{ item.title }}</strong>
            <span>{{ item.description }}</span>
          </button>
        </div>
      </section>

      <section class="goal-section">
        <div class="section-heading">
          <h2>体重目标</h2>
          <span>{{ selectedGoal?.title }}</span>
        </div>
        <div class="surface-card weight-settings">
          <label>
            <span>当前体重</span>
            <strong class="goal-current"
              >{{ currentWeight || '尚未记录' }}<small v-if="currentWeight"> kg</small></strong
            >
            <button type="button" class="goal-record-link" @click="router.push('/body/create')">
              {{ currentWeight ? '记录新的体重' : '先记录一次体重' }}
            </button>
          </label>
          <label>
            <span>目标体重</span>
            <Input v-model="targetWeight" type="number" inputmode="decimal" suffix="kg" />
          </label>
          <label>
            <span>目标日期（选填）</span>
            <input v-model="targetDate" class="date-input" type="date" />
            <small>日期只是节奏参考，不会影响记录功能</small>
          </label>
        </div>
      </section>

      <Button theme="primary" size="large" block :loading="submitting" @click="submit">
        保存目标
      </Button>
    </Loading>
  </main>
</template>

<style scoped lang="scss">
.goal-current {
  font-size: 1.125rem;
  font-weight: 500;
}
.goal-record-link {
  min-height: 40px;
  padding: 0;
  text-align: left;
  background: transparent;
  border: 0;
  color: var(--color-accent-text);
  font-size: 0.8125rem;
}
.goal-section {
  margin-bottom: 26px;
}

.section-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 11px;

  h2 {
    margin: 0;
    font-size: 1rem;
  }

  span {
    color: var(--color-text-tertiary);
    font-size: 0.75rem;
  }
}

.goal-options {
  display: grid;
  gap: 8px;

  button {
    display: grid;
    gap: 4px;
    padding: 14px 15px;
    color: var(--color-text-primary);
    text-align: left;
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: var(--border-radius-md);

    &.active {
      background: var(--color-primary-light);
      border-color: var(--color-primary-border);
      box-shadow: inset 3px 0 0 var(--color-primary);
    }

    strong {
      font-size: 0.9375rem;
    }

    span {
      color: var(--color-text-secondary);
      font-size: 0.8rem;
    }
  }
}

.weight-settings {
  label {
    display: grid;
    gap: 8px;
    padding: 15px;

    + label {
      border-top: 1px solid var(--color-border);
    }

    > span {
      font-size: 0.82rem;
      font-weight: 500;
    }

    small {
      color: var(--color-text-tertiary);
      font-size: 0.72rem;
      line-height: 1.5;
    }
  }
}

.date-input {
  width: 100%;
  min-height: 44px;
  padding: 0 12px;
  color: var(--color-text-primary);
  background: var(--color-surface-muted);
  border: 0;
  border-radius: var(--border-radius-sm);
}
</style>
