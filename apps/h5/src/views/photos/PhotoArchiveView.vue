<script setup lang="ts">
import type { PhotoType, ProgressPhoto } from '@fit-trace/shared';
import dayjs from 'dayjs';
import { Button, Loading, ToastPlugin } from 'tdesign-mobile-vue';
import { ImageIcon } from 'tdesign-icons-vue-next';
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import EmptyState from '@/components/EmptyState.vue';
import RecordDetailHeader from '@/components/RecordDetailHeader.vue';
import { createProgressPhoto, getProgressPhotos } from '@/api/progress-photos';
import { uploadImage } from '@/api/uploads';
import { showRequestError } from '@/utils/request-error';
import { recordDateTime } from '@/utils/record-date';
const route = useRoute();
const router = useRouter();
const photoTypes: Array<{ value: PhotoType; label: string }> = [
  { value: 'FRONT', label: '正面' },
  { value: 'SIDE', label: '侧面' },
  { value: 'BACK', label: '背面' },
  { value: 'OTHER', label: '其他' },
];
const labels = { FRONT: '正面', SIDE: '侧面', BACK: '背面', OTHER: '其他' } as const;
const photos = ref<ProgressPhoto[]>([]);
const loading = ref(true);
const loadingMore = ref(false);
const uploading = ref(false);
const loadError = ref('');
const activeType = ref<PhotoType | 'ALL'>('ALL');
const uploadType = ref<PhotoType>('FRONT');
const recordedAt = ref(dayjs(recordDateTime(route.query.date)).format('YYYY-MM-DD'));
const note = ref('');
const fileInput = ref<HTMLInputElement | null>(null);
const dialog = ref<HTMLDialogElement | null>(null);
const file = ref<File | null>(null);
const preview = ref('');
const uploadedUrl = ref('');
const page = ref(1);
const total = ref(0);
const groups = computed(() => {
  const grouped = new Map<string, ProgressPhoto[]>();
  for (const photo of photos.value) {
    const month = dayjs(photo.recordedAt).format('YYYY年M月');
    grouped.set(month, [...(grouped.get(month) ?? []), photo]);
  }
  return [...grouped].map(([month, items]) => ({ month, items }));
});
let requestId = 0;
async function load(more = false) {
  const request = ++requestId;
  if (more) loadingMore.value = true;
  else loading.value = true;
  loadError.value = '';
  const nextPage = more ? page.value + 1 : 1;
  try {
    const result = await getProgressPhotos({
      page: nextPage,
      pageSize: 20,
      ...(activeType.value === 'ALL' ? {} : { type: activeType.value }),
    });
    if (request !== requestId) return;
    photos.value = more ? [...photos.value, ...result.data] : result.data;
    total.value = result.meta.total;
    page.value = nextPage;
  } catch {
    if (request === requestId) loadError.value = '照片加载失败，请重试';
  } finally {
    if (request === requestId) {
      loading.value = false;
      loadingMore.value = false;
    }
  }
}
function clearSelection() {
  if (preview.value) URL.revokeObjectURL(preview.value);
  preview.value = '';
  uploadedUrl.value = '';
  file.value = null;
}
function cancel() {
  if (uploading.value) return;
  dialog.value?.close();
  clearSelection();
}
function selectPhoto(event: Event) {
  const input = event.target as HTMLInputElement;
  const selected = input.files?.[0];
  input.value = '';
  if (!selected) return;
  clearSelection();
  file.value = selected;
  preview.value = URL.createObjectURL(selected);
  uploadType.value = activeType.value === 'ALL' ? 'FRONT' : activeType.value;
  recordedAt.value = dayjs(recordDateTime(route.query.date)).format('YYYY-MM-DD');
  note.value = '';
  dialog.value?.showModal();
}
async function savePhoto() {
  if (!file.value || uploading.value) return;
  if (
    !/^\d{4}-\d{2}-\d{2}$/.test(recordedAt.value) ||
    dayjs(recordedAt.value).format('YYYY-MM-DD') !== recordedAt.value ||
    recordedAt.value > dayjs().format('YYYY-MM-DD')
  ) {
    ToastPlugin.warning('请选择今天或过去的记录日期');
    return;
  }
  uploading.value = true;
  try {
    if (!uploadedUrl.value) uploadedUrl.value = (await uploadImage(file.value)).url;
    await createProgressPhoto({
      type: uploadType.value,
      imageUrl: uploadedUrl.value,
      recordedAt: dayjs(recordDateTime(recordedAt.value)).toISOString(),
      note: note.value.trim() || undefined,
    });
    dialog.value?.close();
    clearSelection();
    ToastPlugin.success('照片已保存');
    if (route.query.returnTo === '/dashboard') {
      await router.replace({ path: '/dashboard', query: { date: recordedAt.value } });
    } else {
      await load();
    }
  } catch (error) {
    showRequestError(error, '照片保存失败，请重试');
  } finally {
    uploading.value = false;
  }
}
watch(activeType, () => void load());
onMounted(() => void load());
onBeforeUnmount(clearSelection);
</script>
<template>
  <main class="view-page photos-page">
    <RecordDetailHeader
      title="身材照片"
      subtitle="同角度、同光线，留下不同阶段的变化"
      action-label="添加"
      @action="fileInput?.click()"
    />
    <div class="filter-tabs" aria-label="按照片角度筛选">
      <button type="button" :class="{ active: activeType === 'ALL' }" @click="activeType = 'ALL'">
        全部</button
      ><button
        v-for="item in photoTypes"
        :key="item.value"
        type="button"
        :class="{ active: activeType === item.value }"
        @click="activeType = item.value"
      >
        {{ item.label }}
      </button>
    </div>
    <input
      ref="fileInput"
      class="visually-hidden"
      type="file"
      accept="image/jpeg,image/png,image/webp"
      @change="selectPhoto"
    />
    <Loading
      class="page-loading"
      :class="{ 'page-loading--active': loading }"
      :loading="loading"
      text="正在整理照片"
    >
      <div v-if="loadError" class="photo-error" role="alert">
        <span>{{ loadError }}</span
        ><Button variant="text" size="small" @click="load()">重试</Button>
      </div>
      <EmptyState
        v-if="!loading && !loadError && !photos.length"
        title="还没有这个角度的照片"
        description="添加一张照片，为以后对比留下起点"
        action-label="添加照片"
        @action="fileInput?.click()"
        ><template #image><ImageIcon /></template
      ></EmptyState>
      <div v-else class="photo-groups">
        <section v-for="group in groups" :key="group.month">
          <h2>{{ group.month }}</h2>
          <div class="photo-grid">
            <button
              v-for="photo in group.items"
              :key="photo.id"
              class="photo-card"
              type="button"
              @click="router.push(`/photos/${photo.id}`)"
            >
              <img :src="photo.imageUrl" :alt="`${labels[photo.type]}照片`" loading="lazy" /><span
                ><strong>{{ labels[photo.type] }}</strong
                ><small>{{ dayjs(photo.recordedAt).format('M月D日') }}</small></span
              >
              <p v-if="photo.note">{{ photo.note }}</p>
            </button>
          </div>
        </section>
      </div>
      <Button
        v-if="photos.length < total && !loading"
        class="photo-more"
        variant="outline"
        block
        :loading="loadingMore"
        @click="load(true)"
        >加载更多照片</Button
      >
    </Loading>
    <dialog
      ref="dialog"
      class="photo-dialog"
      aria-labelledby="photo-dialog-title"
      @cancel.prevent="cancel"
    >
      <header>
        <h2 id="photo-dialog-title">确认照片</h2>
        <button type="button" :disabled="uploading" @click="cancel">取消</button>
      </header>
      <img v-if="preview" class="photo-preview" :src="preview" alt="待保存照片预览" />
      <form @submit.prevent="savePhoto">
        <fieldset :disabled="uploading">
          <legend>拍摄角度</legend>
          <div class="filter-tabs">
            <button
              v-for="item in photoTypes"
              :key="item.value"
              type="button"
              :class="{ active: uploadType === item.value }"
              :aria-pressed="uploadType === item.value"
              @click="uploadType = item.value"
            >
              {{ item.label }}
            </button>
          </div>
          <label
            >记录日期<input
              v-model="recordedAt"
              type="date"
              :max="dayjs().format('YYYY-MM-DD')"
              required /></label
          ><label
            >备注（选填）<input v-model="note" maxlength="500" placeholder="例如：晨起、训练后"
          /></label>
        </fieldset>
        <Button theme="primary" block :loading="uploading" type="submit">保存照片</Button>
      </form>
    </dialog>
  </main>
</template>
<style scoped lang="scss">
.visually-hidden {
  display: none;
}
.photo-groups {
  display: grid;
  gap: 22px;
  h2 {
    margin: 0 0 10px;
    font-size: 0.9375rem;
    font-weight: 500;
  }
}
.photo-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.photo-card {
  display: grid;
  min-width: 0;
  padding: 0;
  text-align: left;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-surface);
  color: var(--color-text-primary);
  img {
    width: 100%;
    aspect-ratio: 3/4;
    object-fit: cover;
  }
  > span {
    display: flex;
    justify-content: space-between;
    gap: 6px;
    padding: 10px;
  }
  strong,
  small {
    font-size: 0.75rem;
    font-weight: 400;
  }
  small {
    color: var(--color-text-secondary);
  }
  p {
    margin: 0;
    padding: 0 10px 10px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 0.75rem;
    color: var(--color-text-secondary);
  }
}
.photo-more {
  margin-top: 18px;
}
.photo-error {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.8125rem;
}
.photo-dialog {
  width: min(420px, calc(100% - 24px));
  max-height: 90dvh;
  overflow: auto;
  padding: 16px;
  color: var(--color-text-primary);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  &::backdrop {
    background: rgb(0 0 0 / 40%);
  }
  header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
    h2 {
      font-size: 1rem;
      margin: 0;
    }
    button {
      min-height: 40px;
      padding: 0;
      background: transparent;
      border: 0;
      color: var(--color-text-secondary);
      font-size: 0.8125rem;
    }
  }
  fieldset {
    min-width: 0;
    padding: 0;
    border: 0;
    margin: 12px 0;
  }
  legend {
    font-size: 0.8125rem;
    margin-bottom: 8px;
  }
  label {
    display: grid;
    gap: 7px;
    margin-bottom: 12px;
    font-size: 0.8125rem;
  }
  input {
    min-width: 0;
    width: 100%;
    height: 42px;
    padding: 8px;
    color: var(--color-text-primary);
    background: var(--color-surface-muted);
    border: 1px solid var(--color-border);
    border-radius: 6px;
  }
}
.photo-preview {
  display: block;
  width: 100%;
  max-height: 27dvh;
  object-fit: contain;
  background: var(--color-surface-muted);
  border-radius: 6px;
}
</style>
