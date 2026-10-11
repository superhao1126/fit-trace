<script setup lang="ts">
import UiDetailHeader from '@/components/ui/UiDetailHeader.vue';
import UiTabs from '@/components/ui/UiTabs.vue';
import UiButton from '@/components/ui/UiButton.vue';
import UiInput from '@/components/ui/UiInput.vue';
import UiMetricCard from '@/components/ui/UiMetricCard.vue';
import UiDateSheet from '@/components/ui/UiDateSheet.vue';
import UiIcon from '@/components/ui/UiIcon.vue';
import { showRequestError } from '@/utils/request-error';
import type { FitnessGoalType } from '@fit-trace/shared';
import dayjs from 'dayjs';
import { Loading, ToastPlugin } from 'tdesign-mobile-vue';
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

const dateVisible = ref(false);
const goalTabs = goalOptions.map((item) => ({ value: item.value, label: item.title }));
</script>
<template>
  <main class="view-page ui-page ui-form goal-page">
    <UiDetailHeader title="我的目标" subtitle="找到适合自己的节奏" />
    <Loading
      class="page-loading"
      :class="{ 'page-loading--active': loading }"
      :loading="loading"
      text="正在读取身体数据"
    >
      <section class="goal-section">
        <h2>你现在更关注什么？</h2>
        <UiTabs
          :model-value="goalType"
          :options="goalTabs"
          label="健身目标"
          @update:model-value="goalType = $event as FitnessGoalType"
        />
        <p class="field-hint">{{ selectedGoal?.description }}</p>
      </section>
      <UiMetricCard
        kind="body"
        label="当前体重"
        :value="currentWeight || '尚未记录'"
        :unit="currentWeight ? 'kg' : ''"
        ><UiButton class="goal-record-link" kind="text" @click="router.push('/body/create')"
          >{{ currentWeight ? '记录新的体重' : '先记录一次体重' }}<UiIcon name="right" /></UiButton
      ></UiMetricCard>
      <label class="field-block target-weight"
        ><span class="field-label">目标体重</span
        ><UiInput
          v-model="targetWeight"
          type="number"
          inputmode="decimal"
          suffix="kg"
          aria-label="目标体重"
          placeholder="填写目标体重"
      /></label>
      <div class="form-meta">
        <span class="field-label">目标日期 · 选填</span
        ><button type="button" @click="dateVisible = true">
          {{ targetDate ? dayjs(targetDate).format('YYYY年M月D日') : '选择目标日期'
          }}<UiIcon name="right" /></button
        ><UiButton v-if="targetDate" kind="text" @click="targetDate = ''">清除日期</UiButton
        ><small class="field-hint">日期只是节奏参考，不影响日常记录</small>
      </div>
      <UiButton class="form-submit" block :loading="submitting" @click="submit"
        >保存目标</UiButton
      ></Loading
    >
    <UiDateSheet
      v-model:visible="dateVisible"
      v-model="targetDate"
      :min-date="dayjs().format('YYYY-MM-DD')"
      :max-date="dayjs().add(20, 'year').format('YYYY-MM-DD')"
    />
  </main>
</template>
<style scoped lang="scss">
.goal-section {
  margin-bottom: 28px;
  h2 {
    margin: 0 0 16px;
    font-size: 16px;
    font-weight: 600;
  }
  :deep(.ui-tabs) {
    margin-bottom: 12px;
  }
}
.goal-record-link {
  display: flex;
  margin-left: auto;
}
.target-weight {
  margin-top: 28px;
}
.form-meta .field-hint {
  display: block;
  margin-top: 12px;
}
.form-submit {
  margin-top: 44px;
}
</style>
