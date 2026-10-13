<script setup lang="ts">
import { confirmDialog } from '@/components/ui/confirm';
import UiLoading from '@/components/ui/UiLoading.vue';
import UiInfoCard from '@/components/ui/UiInfoCard.vue';
import UiButton from '@/components/ui/UiButton.vue';
import { showRequestError } from '@/utils/request-error';
import type { PhotoType, ProgressPhoto } from '@fit-trace/shared';
import dayjs from 'dayjs';
import { uiToast as ToastPlugin } from '@/components/ui/toast';
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { deleteProgressPhoto, getProgressPhoto } from '@/api/progress-photos';
import UiDetailHeader from '@/components/ui/UiDetailHeader.vue';

const photoLabels: Record<PhotoType, string> = {
  FRONT: '正面',
  SIDE: '侧面',
  BACK: '背面',
  OTHER: '其他',
};

const route = useRoute();
const router = useRouter();
const photoId = computed(() => (typeof route.params.id === 'string' ? route.params.id : ''));
const loading = ref(true);
const photo = ref<ProgressPhoto | null>(null);
const recordedAtText = computed(() =>
  photo.value ? dayjs(photo.value.recordedAt).format('YYYY年M月D日') : '',
);

function confirmDelete(): void {
  const target = photo.value;
  if (!target) return;
  let deleting = false;
  const dialog = confirmDialog({
    title: '删除这张照片？',
    content: `${recordedAtText.value} · ${photoLabels[target.type]}，删除后无法恢复`,
    confirmBtn: { content: '删除', theme: 'danger' },
    cancelBtn: '取消',
    onConfirm: async () => {
      if (deleting) return;
      deleting = true;
      dialog.update({ confirmBtn: { content: '删除中…', theme: 'danger', loading: true } });
      try {
        await deleteProgressPhoto(target.id);
        ToastPlugin.success('照片已删除');
        dialog.destroy();
        await router.replace('/photos');
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
    photo.value = await getProgressPhoto(photoId.value);
  } catch (error) {
    showRequestError(error, '照片不存在或已被删除');
    await router.replace('/photos');
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <main class="view-page ui-page detail-page">
    <UiDetailHeader
      title="身材照片"
      :subtitle="photo ? `${recordedAtText} · ${photoLabels[photo.type]}` : ''"
    />

    <UiLoading
      class="page-loading"
      :class="{ 'page-loading--active': loading }"
      :loading="loading"
      text="正在读取照片"
    >
      <template v-if="photo">
        <img class="photo-full" :src="photo.imageUrl" :alt="`${photoLabels[photo.type]}照片`" />

        <UiInfoCard
          :items="[
            { label: '拍摄类型', value: photoLabels[photo.type] },
            { label: '记录日期', value: dayjs(photo.recordedAt).format('YYYY年M月D日 HH:mm') },
            { label: '备注', value: photo.note || '无' },
          ]"
        />
        <div class="ui-detail-actions">
          <UiButton kind="danger" @click="confirmDelete">删除这张照片</UiButton>
        </div>
      </template>
    </UiLoading>
  </main>
</template>

<style scoped lang="scss">
.photo-full {
  display: block;
  width: 100%;
  max-height: 60dvh;
  object-fit: contain;
  background: var(--color-surface-muted);
  border-radius: 20px;
}
</style>
