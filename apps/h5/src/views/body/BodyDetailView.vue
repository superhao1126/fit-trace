<script setup lang="ts">
import { showRequestError } from '@/utils/request-error';
import type { BodyRecord } from '@fit-trace/shared';
import dayjs from 'dayjs';
import { DialogPlugin, Loading, ToastPlugin } from 'tdesign-mobile-vue';
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { deleteBodyRecord, getBodyRecord } from '@/api/body-records';
import UiDetailHeader from '@/components/ui/UiDetailHeader.vue';
import UiButton from '@/components/ui/UiButton.vue';
import UiMetricCard from '@/components/ui/UiMetricCard.vue';
import UiInfoCard from '@/components/ui/UiInfoCard.vue';

const route = useRoute();
const router = useRouter();
const recordId = computed(() => (typeof route.params.id === 'string' ? route.params.id : ''));
const loading = ref(true);
const record = ref<BodyRecord | null>(null);
const recordedAtText = computed(() =>
  record.value ? dayjs(record.value.recordedAt).format('YYYY年M月D日 HH:mm') : '',
);
const metrics = computed(() => [
  { label: '体脂率', value: record.value?.bodyFat, unit: '%' },
  { label: '腰围', value: record.value?.waist, unit: 'cm' },
  { label: '胸围', value: record.value?.chest, unit: 'cm' },
  { label: '臀围', value: record.value?.hip, unit: 'cm' },
]);

function confirmDelete(): void {
  const target = record.value;
  if (!target) return;
  let deleting = false;
  const dialog = DialogPlugin.confirm({
    title: '删除这条记录？',
    content: `${dayjs(target.recordedAt).format('YYYY年M月D日')} · ${target.weight} kg，删除后无法恢复`,
    confirmBtn: { content: '删除', theme: 'danger' },
    cancelBtn: '取消',
    onConfirm: async () => {
      if (deleting) return;
      deleting = true;
      dialog.update({ confirmBtn: { content: '删除中…', theme: 'danger', loading: true } });
      try {
        await deleteBodyRecord(target.id);
        ToastPlugin.success('记录已删除');
        dialog.destroy();
        await router.replace('/body/history');
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
    record.value = await getBodyRecord(recordId.value);
  } catch (error) {
    showRequestError(error, '记录不存在或已被删除');
    await router.replace('/body/history');
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <main class="view-page ui-page">
    <UiDetailHeader
      title="身体数据"
      :subtitle="recordedAtText"
      action-label="编辑"
      @action="router.push(`/body/${recordId}/edit`)"
    /><Loading
      class="page-loading"
      :class="{ 'page-loading--active': loading }"
      :loading="loading"
      text="正在读取记录"
      ><template v-if="record"
        ><UiMetricCard kind="body" label="体重" :value="record.weight" unit="kg"
          ><div class="body-secondary">
            <div v-for="item in metrics.slice(0, 2)" :key="item.label">
              <span>{{ item.label }}</span
              ><strong
                >{{ item.value ?? '—' }}<small>{{ item.unit }}</small></strong
              >
            </div>
          </div></UiMetricCard
        ><UiInfoCard
          :items="[
            ...metrics.slice(2).map((item) => ({
              label: item.label,
              value: item.value == null ? '未记录' : `${item.value} ${item.unit}`,
            })),
            { label: '记录时间', value: recordedAtText },
            { label: '备注', value: record.note || '无' },
          ]"
        />
        <div class="ui-detail-actions">
          <UiButton kind="danger" @click="confirmDelete">删除这条记录</UiButton>
        </div></template
      ></Loading
    >
  </main>
</template>
<style scoped lang="scss">
.body-secondary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  border-top: 1px solid var(--ui-border);
  margin-top: 24px;
  padding-top: 20px;
  > div {
    display: grid;
    gap: 8px;
    + div {
      border-left: 1px solid var(--ui-border);
      padding-left: 16px;
    }
  }
  span {
    font-size: 13px;
    color: var(--ui-text-secondary);
  }
  strong {
    font-size: 24px;
    font-weight: 600;
    small {
      margin-left: 8px;
      font-size: 13px;
      font-weight: 400;
      color: var(--ui-text-secondary);
    }
  }
}
</style>
