<script setup lang="ts">
import { recordDateTime } from '@/utils/record-date';
import RecordDetailHeader from '@/components/RecordDetailHeader.vue';
import { showRequestError } from '@/utils/request-error';
import type { BodyRecord } from '@fit-trace/shared';
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
  <main class="view-page record-form body-form-page">
    <RecordDetailHeader
      :title="isEdit ? '编辑身体数据' : '记体重'"
      subtitle="体重是必填项，其他数据可以稍后补充"
    />

    <Loading
      class="page-loading"
      :class="{ 'page-loading--active': loading }"
      :loading="loading"
      text="正在读取记录"
    >
      <section class="surface-card body-form-card">
        <div class="field-block">
          <span class="field-label">体重</span>
          <div class="weight-field">
            <input
              ref="weightInput"
              v-model="formData.weight"
              class="weight-input"
              type="number"
              inputmode="decimal"
              step="0.1"
              min="0"
              aria-label="体重（kg）"
              placeholder="填写今天的体重"
              @keydown.enter.prevent="submit"
            /><span>kg</span>
          </div>
          <small v-if="!isEdit" class="field-hint">{{ previousHint }}</small>
        </div>

        <div class="field-block">
          <span class="field-label">记录时间</span>
          <Input :model-value="recordedAtDisplay" readonly @click="datePickerVisible = true">
            <template #suffix-icon><CalendarIcon /></template>
          </Input>
        </div>

        <details class="more-fields" :open="isEdit">
          <summary>
            更多指标与备注 <span>选填</span><ChevronRightIcon class="expand-icon" />
          </summary>
          <div class="field-block">
            <span class="field-label">身体围度（可选）</span>
            <div class="metric-grid">
              <Input v-model="formData.bodyFat" type="number" suffix="%" placeholder="体脂率" />
              <Input v-model="formData.waist" type="number" suffix="cm" placeholder="腰围" />
              <Input v-model="formData.chest" type="number" suffix="cm" placeholder="胸围" />
              <Input v-model="formData.hip" type="number" suffix="cm" placeholder="臀围" />
            </div>
          </div>

          <div class="field-block note-field">
            <span class="field-label">备注</span>
            <Textarea
              v-model="formData.note"
              :maxlength="500"
              :autosize="{ minRows: 3, maxRows: 6 }"
              placeholder="例如：晨起空腹、训练后等"
            />
          </div>
        </details>

        <Button theme="primary" size="large" block :loading="submitting" @click="submit">
          {{ isEdit ? '保存修改' : '保存记录' }}
        </Button>
      </section>
    </Loading>

    <Popup v-model="datePickerVisible" placement="bottom">
      <DateTimePicker
        :value="formData.recordedAt"
        title="选择记录时间"
        :mode="['date', 'minute']"
        format="YYYY-MM-DD HH:mm:ss"
        @confirm="confirmRecordedAt"
        @cancel="datePickerVisible = false"
      />
    </Popup>
  </main>
</template>

<style scoped lang="scss">
.body-form-card {
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

.field-hint {
  color: var(--color-text-tertiary);
  font-size: 0.75rem;
}

.weight-field {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 20px 0;
  border-bottom: 1px solid var(--color-border);
  input {
    min-width: 0;
    width: 100%;
    padding: 0;
    border: 0;
    outline: none;
    color: var(--color-text-primary);
    background: transparent;
    font-size: 2.75rem;
    font-weight: 500;
    font-variant-numeric: tabular-nums;
  }
  input::-webkit-inner-spin-button,
  input::-webkit-outer-spin-button {
    -webkit-appearance: none;
    margin: 0;
  }
  input {
    appearance: textfield;
  }
  input::placeholder {
    color: var(--color-text-tertiary);
    font-size: 1.125rem;
  }
  &:focus-within {
    border-color: var(--color-accent-text);
  }
  span {
    color: var(--color-text-secondary);
  }
}
.more-fields {
  margin-bottom: 20px;
  summary {
    display: flex;
    justify-content: space-between;
    cursor: pointer;
    padding: 14px 0;
    color: var(--color-text-secondary);
    font-size: 0.875rem;
  }
  summary span {
    font-size: 0.75rem;
  }
  &[open] summary {
    margin-bottom: 10px;
  }
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;

  :deep(.t-input) {
    width: 100%;
  }
}

.note-field {
  margin-bottom: 22px;
}

@media (max-width: 360px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
