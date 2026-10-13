<script setup lang="ts">
import UiLoading from '@/components/ui/UiLoading.vue';
import { recordDateTime } from '@/utils/record-date';
import UiDetailHeader from '@/components/ui/UiDetailHeader.vue';
import UiButton from '@/components/ui/UiButton.vue';
import UiInput from '@/components/ui/UiInput.vue';
import UiTextarea from '@/components/ui/UiTextarea.vue';
import UiIcon from '@/components/ui/UiIcon.vue';
import UiDateTimePicker from '@/components/ui/UiDateTimePicker.vue';
import { showRequestError } from '@/utils/request-error';
import type { BodyRecord } from '@fit-trace/shared';
import dayjs from 'dayjs';
import { ToastPlugin } from 'tdesign-mobile-vue';
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  createBodyRecord,
  getBodyRecord,
  getLatestBodyRecord,
  updateBodyRecord,
  type BodyRecordInput,
} from '@/api/body-records';

const route = useRoute();
const router = useRouter();
const recordId = computed(() => (typeof route.params.id === 'string' ? route.params.id : null));
const isEdit = computed(() => Boolean(recordId.value));
const loading = ref(Boolean(recordId.value));
const submitting = ref(false);
const datePickerVisible = ref(false);
const weightInput = ref<HTMLInputElement | null>(null);
const previousRecord = ref<BodyRecord | null>(null);
const formData = reactive({
  weight: '' as number | string,
  bodyFat: '' as number | string,
  waist: '' as number | string,
  chest: '' as number | string,
  hip: '' as number | string,
  recordedAt: recordDateTime(route.query.date),
  note: '',
});
const recordedAtDisplay = computed(() => dayjs(formData.recordedAt).format('YYYY-MM-DD HH:mm'));
const previousHint = computed(() => {
  const record = previousRecord.value;
  if (!record) return '还没有历史记录，这是第一条';
  const days = dayjs().startOf('day').diff(dayjs(record.recordedAt).startOf('day'), 'day');
  const when = days <= 0 ? '今天' : days === 1 ? '昨天' : `${days} 天前`;
  const diff = Number(formData.weight) - record.weight;
  const base = `上次 ${record.weight} kg · ${when}`;
  if (formData.weight === '' || !Number.isFinite(Number(formData.weight))) return base;
  const change = diff === 0 ? '与上次持平' : `较上次 ${diff > 0 ? '+' : ''}${diff.toFixed(1)} kg`;
  return `${base} · ${change}`;
});

function optionalNumber(value: number | string): number | undefined {
  if (value === '') return undefined;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function confirmRecordedAt(value: string | number): void {
  formData.recordedAt = dayjs(value).format('YYYY-MM-DD HH:mm:ss');
  datePickerVisible.value = false;
}

async function submit(): Promise<void> {
  if (submitting.value) return;
  const weight = optionalNumber(formData.weight);
  if (weight === undefined || weight <= 0) {
    ToastPlugin.warning('请输入正确的体重');
    return;
  }
  const bodyFat = optionalNumber(formData.bodyFat);
  if (bodyFat !== undefined && (bodyFat < 0 || bodyFat > 100)) {
    ToastPlugin.warning('体脂率需要在 0 到 100 之间');
    return;
  }

  const input: BodyRecordInput = {
    weight,
    bodyFat,
    waist: optionalNumber(formData.waist),
    chest: optionalNumber(formData.chest),
    hip: optionalNumber(formData.hip),
    recordedAt: dayjs(formData.recordedAt).toISOString(),
    note: formData.note.trim() || undefined,
  };

  submitting.value = true;
  try {
    if (recordId.value) {
      await updateBodyRecord(recordId.value, input);
      ToastPlugin.success('身体数据已更新');
      await router.replace(`/body/${recordId.value}`);
    } else {
      const created = await createBodyRecord(input);
      ToastPlugin.success('身体数据已保存');
      await router.replace(
        route.query.returnTo === '/dashboard'
          ? { path: '/dashboard', query: { date: dayjs(formData.recordedAt).format('YYYY-MM-DD') } }
          : `/body/${created.id}`,
      );
    }
  } catch (error) {
    showRequestError(error, '保存失败，请稍后重试');
  } finally {
    submitting.value = false;
  }
}

onMounted(async () => {
  if (!recordId.value) {
    weightInput.value?.focus();
    try {
      previousRecord.value = await getLatestBodyRecord();
    } catch {
      previousRecord.value = null;
    }
    return;
  }

  try {
    const record = await getBodyRecord(recordId.value);
    Object.assign(formData, {
      weight: record.weight,
      bodyFat: record.bodyFat ?? '',
      waist: record.waist ?? '',
      chest: record.chest ?? '',
      hip: record.hip ?? '',
      recordedAt: dayjs(record.recordedAt).format('YYYY-MM-DD HH:mm:ss'),
      note: record.note ?? '',
    });
  } catch {
    await router.replace('/body/history');
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <main class="view-page ui-page ui-form body-form-page">
    <UiDetailHeader
      :title="isEdit ? '编辑身体数据' : '记体重'"
      subtitle="体重必填，其他数据可稍后补充"
    />
    <UiLoading
      class="page-loading"
      :class="{ 'page-loading--active': loading }"
      :loading="loading"
      text="正在读取记录"
    >
      <section class="weight-entry">
        <span class="field-label">体重</span>
        <div>
          <input
            ref="weightInput"
            v-model="formData.weight"
            :style="{
              width: formData.weight === '' ? '5em' : `${String(formData.weight).length + 0.5}ch`,
            }"
            type="number"
            inputmode="decimal"
            step="0.1"
            min="0"
            aria-label="体重（kg）"
            placeholder="填写体重"
            @keydown.enter.prevent="submit"
          /><span>kg</span>
        </div>
        <small v-if="!isEdit" class="field-hint">{{ previousHint }}</small>
      </section>
      <div class="form-meta">
        <span class="field-label">记录时间</span
        ><button type="button" @click="datePickerVisible = true">
          {{ recordedAtDisplay }}<UiIcon name="calendar" />
        </button>
      </div>
      <details class="extra-fields" :open="isEdit">
        <summary>更多指标与备注<span>选填</span><UiIcon name="right" /></summary>
        <div class="optional-grid">
          <label class="field-block"
            ><span class="field-label">体脂率</span
            ><UiInput
              v-model="formData.bodyFat"
              type="number"
              inputmode="decimal"
              suffix="%"
              placeholder="选填"
              aria-label="体脂率" /></label
          ><label class="field-block"
            ><span class="field-label">腰围</span
            ><UiInput
              v-model="formData.waist"
              type="number"
              inputmode="decimal"
              suffix="cm"
              placeholder="选填"
              aria-label="腰围" /></label
          ><label class="field-block"
            ><span class="field-label">胸围</span
            ><UiInput
              v-model="formData.chest"
              type="number"
              inputmode="decimal"
              suffix="cm"
              placeholder="选填"
              aria-label="胸围" /></label
          ><label class="field-block"
            ><span class="field-label">臀围</span
            ><UiInput
              v-model="formData.hip"
              type="number"
              inputmode="decimal"
              suffix="cm"
              placeholder="选填"
              aria-label="臀围"
          /></label>
        </div>
        <label class="field-block"
          ><span class="field-label">备注</span
          ><UiTextarea
            v-model="formData.note"
            :maxlength="500"
            :autosize="{ minRows: 3, maxRows: 6 }"
            placeholder="例如：晨起空腹、训练后等"
            aria-label="备注"
        /></label>
      </details>
      <UiButton class="form-submit" block :loading="submitting" @click="submit">{{
        isEdit ? '保存修改' : '保存记录'
      }}</UiButton> </UiLoading
    ><UiDateTimePicker
      v-model:visible="datePickerVisible"
      :value="formData.recordedAt"
      @confirm="confirmRecordedAt"
    />
  </main>
</template>
<style scoped lang="scss">
.weight-entry {
  display: grid;
  gap: 16px;
  margin-bottom: 28px;
  padding: 20px;
  background: var(--color-surface);
  border-radius: 20px;
  > div {
    display: flex;
    align-items: baseline;
    gap: 12px;
    padding: 6px 0;
  }
  input {
    max-width: calc(100% - 32px);
    min-width: 0;
    padding: 0;
    border: 0;
    background: transparent;
    color: var(--ui-text-primary);
    font-size: 48px;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
    appearance: textfield;
    outline: none;
    &::placeholder {
      font-size: 24px;
      color: var(--ui-text-tertiary);
    }
    &::-webkit-inner-spin-button,
    &::-webkit-outer-spin-button {
      appearance: none;
    }
  }
  span:not(.field-label) {
    color: var(--ui-text-secondary);
    font-size: 16px;
  }
}
</style>
