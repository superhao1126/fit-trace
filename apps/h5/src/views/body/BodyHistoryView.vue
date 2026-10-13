<script setup lang="ts">
import UiLoading from '@/components/ui/UiLoading.vue';
import UiDetailHeader from '@/components/ui/UiDetailHeader.vue';
import UiButton from '@/components/ui/UiButton.vue';
import UiRecordRow from '@/components/ui/UiRecordRow.vue';
import UiEmptyState from '@/components/ui/UiEmptyState.vue';
import { showRequestError } from '@/utils/request-error';
import type { BodyRecord } from '@fit-trace/shared';
import dayjs from 'dayjs';
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { getBodyRecords } from '@/api/body-records';

const router = useRouter();
const records = ref<BodyRecord[]>([]);
const loading = ref(true);
const loadError = ref('');
const loadingMore = ref(false);
const page = ref(1);
const total = ref(0);
const pageSize = 20;

async function load(reset = false): Promise<void> {
  loadError.value = '';
  if (reset) {
    page.value = 1;
    records.value = [];
  }
  const result = await getBodyRecords({
    page: page.value,
    pageSize,
    recordedAtOrder: 'desc',
  });
  records.value.push(...result.data);
  total.value = result.meta.total;
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

onMounted(async () => {
  try {
    await load();
  } catch (error) {
    loadError.value = '身体记录加载失败，请重试';
    showRequestError(error, '身体记录加载失败');
  } finally {
    loading.value = false;
  }
});
async function retry(): Promise<void> {
  loading.value = true;
  try {
    await load(true);
  } catch (error) {
    loadError.value = '身体记录加载失败，请重试';
    showRequestError(error, '身体记录加载失败');
  } finally {
    loading.value = false;
  }
}
</script>
<template>
  <main class="view-page ui-page history-page">
    <UiDetailHeader
      title="身体记录"
      subtitle="体重与围度的每一次变化"
      action-label="新记录"
      @action="router.push('/body/create')"
    />
    <UiLoading
      class="page-loading"
      :class="{ 'page-loading--active': loading }"
      :loading="loading"
      text="正在读取历史记录"
    >
      <UiEmptyState
        v-if="!loading && loadError"
        :title="loadError"
        description="请检查网络后再试"
        action-label="重新加载"
        @action="retry"
      />
      <UiEmptyState
        v-else-if="!loading && !records.length"
        kind="body"
        title="记录第一组身体数据"
        description="从一次体重记录开始，看见身体的变化"
        action-label="记体重"
        @action="router.push('/body/create')"
      />
      <div v-else class="history-list">
        <article v-for="record in records" :key="record.id" class="history-card">
          <UiRecordRow
            kind="body"
            :title="`${record.weight} kg`"
            :subtitle="dayjs(record.recordedAt).format('YYYY年M月D日 HH:mm')"
            :value="dayjs(record.recordedAt).isSame(dayjs(), 'day') ? '今天' : undefined"
            @click="router.push(`/body/${record.id}`)"
          />
          <p
            v-if="
              [record.bodyFat, record.waist, record.chest, record.hip].some(
                (value) => value !== null,
              )
            "
            class="history-metrics"
          >
            <span v-if="record.bodyFat !== null">体脂 {{ record.bodyFat }}%</span
            ><span v-if="record.waist !== null">腰围 {{ record.waist }} cm</span
            ><span v-if="record.chest !== null">胸围 {{ record.chest }} cm</span
            ><span v-if="record.hip !== null">臀围 {{ record.hip }} cm</span>
          </p>
          <p v-if="record.note" class="history-note">{{ record.note }}</p>
        </article>
      </div>
      <UiButton
        v-if="records.length < total"
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
.load-more {
  margin-top: 24px;
}
.history-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 14px;
  margin: 0;
  padding: 0 16px 12px 70px;
  color: var(--ui-text-secondary);
  font-size: 12px;
  line-height: 1.7;
}
</style>
