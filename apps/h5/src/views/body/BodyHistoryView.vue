<script setup lang="ts">
import EmptyState from '@/components/EmptyState.vue';
import { showRequestError } from '@/utils/request-error';
import type { BodyRecord } from '@fit-trace/shared';
import dayjs from 'dayjs';
import { Button, Loading, Tag } from 'tdesign-mobile-vue';
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { getBodyRecords } from '@/api/body-records';
import RecordDetailHeader from '@/components/RecordDetailHeader.vue';

const router = useRouter();
const records = ref<BodyRecord[]>([]);
const loading = ref(true);
const loadingMore = ref(false);
const page = ref(1);
const total = ref(0);
const pageSize = 20;

async function load(reset = false): Promise<void> {
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
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <main class="view-page history-page">
    <RecordDetailHeader
      title="身体数据"
      subtitle="每一次记录，都会让变化更清晰"
      action-label="新记录"
      @action="router.push('/body/create')"
    />

    <Loading
      class="page-loading"
      :class="{ 'page-loading--active': loading }"
      :loading="loading"
      text="正在读取历史记录"
    >
      <EmptyState
        v-if="!loading && records.length === 0"
        class="empty-state"
        title="还没有身体数据"
        description="从今天的体重开始，建立你的第一条身体轨迹"
      >
        <template #action>
          <Button theme="primary" @click="router.push('/body/create')">记录第一条数据</Button>
        </template>
      </EmptyState>

      <div v-else class="record-list">
        <section
          v-for="record in records"
          :key="record.id"
          class="surface-card record-card"
          role="button"
          tabindex="0"
          @click="router.push(`/body/${record.id}`)"
          @keydown.enter="router.push(`/body/${record.id}`)"
        >
          <div class="record-card__heading">
            <div>
              <strong>{{ record.weight }} <small>kg</small></strong>
              <span>{{ dayjs(record.recordedAt).format('YYYY年M月D日 HH:mm') }}</span>
            </div>
            <Tag
              v-if="dayjs(record.recordedAt).isSame(dayjs(), 'day')"
              theme="success"
              variant="light"
            >
              今天
            </Tag>
          </div>

          <div
            v-if="
              record.bodyFat !== null ||
              record.waist !== null ||
              record.chest !== null ||
              record.hip !== null
            "
            class="record-card__metrics"
          >
            <div v-if="record.bodyFat !== null">
              <span>体脂率</span
              ><strong>{{ record.bodyFat ?? '—' }}{{ record.bodyFat !== null ? '%' : '' }}</strong>
            </div>
            <div v-if="record.waist !== null">
              <span>腰围</span
              ><strong>{{ record.waist ?? '—' }}{{ record.waist !== null ? ' cm' : '' }}</strong>
            </div>
            <div v-if="record.chest !== null">
              <span>胸围</span
              ><strong>{{ record.chest ?? '—' }}{{ record.chest !== null ? ' cm' : '' }}</strong>
            </div>
            <div v-if="record.hip !== null">
              <span>臀围</span
              ><strong>{{ record.hip ?? '—' }}{{ record.hip !== null ? ' cm' : '' }}</strong>
            </div>
          </div>

          <p v-if="record.note" class="record-card__note">{{ record.note }}</p>
        </section>
      </div>

      <Button
        v-if="records.length < total"
        class="load-more"
        variant="outline"
        block
        :loading="loadingMore"
        @click="loadMore"
      >
        加载更多
      </Button>
    </Loading>
  </main>
</template>

<style scoped lang="scss">
.record-list {
  position: relative;
  display: grid;
  width: 100%;
  min-width: 0;
  gap: 12px;

  &::before {
    position: absolute;
    top: 18px;
    bottom: 18px;
    left: 5px;
    width: 2px;
    background: var(--color-border);
    content: '';
  }
}

.record-card {
  position: relative;
  width: auto;
  min-width: 0;
  margin-left: 17px;
  padding: 17px;
  cursor: pointer;
  transition:
    background-color var(--duration-fast) var(--ease-standard),
    transform var(--duration-fast) var(--ease-standard);

  &:active {
    background: var(--color-surface-muted);
    transform: scale(0.995);
  }

  &::before {
    position: absolute;
    top: 25px;
    left: -18px;
    width: 10px;
    height: 10px;
    background: var(--color-primary);
    border: 3px solid var(--color-background);
    border-radius: 50%;
    box-shadow: 0 0 0 2px var(--color-ink);
    content: '';
  }

  &__heading {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;

    > div {
      display: flex;
      flex-direction: column;
      gap: 3px;
    }

    strong {
      color: var(--color-text-primary);
      font-size: 1.65rem;
      font-variant-numeric: tabular-nums;

      small {
        font-size: 0.875rem;
      }
    }

    span {
      color: var(--color-text-tertiary);
      font-size: 0.875rem;
    }
  }

  &__metrics {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 6px;
    margin-top: 17px;
    padding: 12px;
    background: var(--color-surface-muted);
    border-radius: 10px;

    div {
      display: flex;
      min-width: 0;
      flex-direction: column;
      gap: 4px;
    }

    span {
      color: var(--color-text-tertiary);
      font-size: 0.75rem;
    }

    strong {
      font-size: 0.875rem;
    }
  }

  &__note {
    margin: 12px 0 0;
    color: var(--color-text-secondary);
    font-size: 0.875rem;
    line-height: 1.6;
  }

  &__actions {
    display: flex;
    justify-content: flex-end;
    gap: 3px;
    margin: 8px -8px -8px 0;
  }
}

.load-more {
  margin-top: var(--spacing-md);
}

@media (max-width: 380px) {
  .record-card__metrics {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
