<script setup lang="ts">
import UiLoading from '@/components/ui/UiLoading.vue';
import UiDetailHeader from '@/components/ui/UiDetailHeader.vue';
import UiTabs from '@/components/ui/UiTabs.vue';
import UiButton from '@/components/ui/UiButton.vue';
import UiInput from '@/components/ui/UiInput.vue';
import UiTextarea from '@/components/ui/UiTextarea.vue';
import UiEmptyState from '@/components/ui/UiEmptyState.vue';
import type { PhotoType, ProgressPhoto } from '@fit-trace/shared';
import dayjs from 'dayjs';
import { ToastPlugin } from 'tdesign-mobile-vue';
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
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

const filters = [{ value: 'ALL', label: '全部' }, ...photoTypes];
</script>
<template>
  <main class="view-page ui-page photos-page">
    <UiDetailHeader
      title="身材照片"
      subtitle="按月份回看身体的变化"
      action-label="添加"
      @action="fileInput?.click()"
    />
    <UiTabs
      :model-value="activeType"
      :options="filters"
      label="按照片角度筛选"
      @update:model-value="activeType = $event as PhotoType | 'ALL'"
    />
    <input
      ref="fileInput"
      class="visually-hidden"
      type="file"
      accept="image/jpeg,image/png,image/webp"
      @change="selectPhoto"
    />
    <UiLoading
      class="page-loading"
      :class="{ 'page-loading--active': loading }"
      :loading="loading"
      text="正在整理照片"
    >
      <div v-if="loadError" class="photo-error" role="alert">
        <span>{{ loadError }}</span
        ><UiButton kind="text" @click="load()">重试</UiButton>
      </div>
      <UiEmptyState
        v-if="!loading && !loadError && !photos.length"
        kind="photo"
        title="留下第一张身材照片"
        description="尽量保持相同光线、距离和拍摄角度"
        action-label="添加照片"
        @action="fileInput?.click()"
      />
      <div v-else class="photo-groups">
        <section v-for="group in groups" :key="group.month">
          <div class="group-heading">
            <h2>{{ group.month }}</h2>
            <span>{{ group.items.length }} 张</span>
          </div>
          <div class="photo-grid">
            <button
              v-for="photo in group.items"
              :key="photo.id"
              class="photo-card"
              type="button"
              @click="router.push(`/photos/${photo.id}`)"
            >
              <img :src="photo.imageUrl" :alt="`${labels[photo.type]}照片`" loading="lazy" /><span
                >{{ dayjs(photo.recordedAt).format('M月D日') }} · {{ labels[photo.type] }}</span
              >
              <p v-if="photo.note">{{ photo.note }}</p>
            </button>
          </div>
        </section>
      </div>
      <UiButton
        v-if="photos.length < total && !loading"
        class="photo-more"
        kind="secondary"
        block
        :loading="loadingMore"
        @click="load(true)"
        >加载更多照片</UiButton
      >
    </UiLoading>
    <dialog
      ref="dialog"
      class="photo-dialog ui-page ui-form"
      aria-labelledby="photo-dialog-title"
      @cancel.prevent="cancel"
    >
      <header>
        <h2 id="photo-dialog-title">添加照片</h2>
        <UiButton kind="text" :disabled="uploading" @click="cancel">取消</UiButton>
      </header>
      <p class="field-hint">记录真实的身体变化</p>
      <img v-if="preview" class="photo-preview" :src="preview" alt="待保存照片预览" />
      <form @submit.prevent="savePhoto">
        <fieldset :disabled="uploading">
          <legend>拍摄类型</legend>
          <UiTabs
            :model-value="uploadType"
            :options="photoTypes"
            label="拍摄类型"
            :disabled="uploading"
            @update:model-value="uploadType = $event as PhotoType"
          />
          <label class="field-block"
            ><span class="field-label">记录日期</span
            ><UiInput
              v-model="recordedAt"
              type="date"
              aria-label="照片记录日期"
              :max="dayjs().format('YYYY-MM-DD')"
              required
          /></label>
          <label class="field-block"
            ><span class="field-label">备注 · 选填</span
            ><UiTextarea
              v-model="note"
              aria-label="照片备注"
              maxlength="500"
              placeholder="例如：晨起、训练后"
          /></label>
        </fieldset>
        <UiButton block :loading="uploading" :disabled="uploading" type="submit">保存照片</UiButton>
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
  gap: 28px;
}
.group-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 16px;
  h2 {
    margin: 0;
    font-size: 19px;
    font-weight: 600;
  }
  span {
    color: var(--ui-text-secondary);
    font-size: 12px;
  }
}
.photo-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px 16px;
}
.photo-card {
  display: grid;
  min-width: 0;
  padding: 0;
  text-align: left;
  border: 0;
  background: transparent;
  color: var(--ui-text-primary);
  cursor: pointer;
  img {
    width: 100%;
    aspect-ratio: 3/4;
    object-fit: cover;
    border-radius: 20px;
    background: var(--color-surface-muted);
  }
  > span {
    display: block;
    margin-top: 10px;
    font-size: 13px;
  }
  p {
    margin: 6px 0 0;
    color: var(--ui-text-secondary);
    font-size: 12px;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
}
.photo-more {
  margin-top: 24px;
}
.photo-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
}
.photo-dialog {
  width: min(560px, 100%);
  max-width: 100%;
  height: 100dvh;
  max-height: 100dvh;
  margin: 0 auto;
  padding: 24px 24px calc(24px + var(--app-safe-area-bottom));
  overflow: auto;
  border: 0;
  border-radius: 0;
  background: var(--ui-background);
  color: var(--ui-text-primary);
  box-sizing: border-box;
  &::backdrop {
    background: rgb(0 0 0 / 40%);
  }
  header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    h2 {
      margin: 0;
      font-size: 26px;
      font-weight: 600;
    }
  }
  .field-hint {
    margin: 12px 0 28px;
  }
  fieldset {
    min-width: 0;
    margin: 28px 0 32px;
    padding: 0;
    border: 0;
  }
  legend {
    margin-bottom: 12px;
    font-size: 13px;
    color: var(--ui-text-secondary);
  }
}
.photo-preview {
  display: block;
  width: 100%;
  max-height: 30dvh;
  object-fit: contain;
  background: var(--color-surface-muted);
  border-radius: 20px;
}
@media (min-width: 600px) {
  .photo-dialog {
    height: auto;
    max-height: 90dvh;
    margin: auto;
    border-radius: 28px;
  }
}
</style>
