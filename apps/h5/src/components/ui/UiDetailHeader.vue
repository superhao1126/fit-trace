<script setup lang="ts">
import { useRouter } from 'vue-router';
import UiButton from './UiButton.vue';
import UiIcon from './UiIcon.vue';
defineProps<{ title: string; subtitle?: string; actionLabel?: string; actionLoading?: boolean }>();
defineEmits<{ action: [] }>();
const router = useRouter();
</script>
<template>
  <header class="ui-detail-header">
    <div>
      <UiButton kind="text" class="back" aria-label="返回" @click="router.back()"
        ><UiIcon name="left"
      /></UiButton>
      <h1>{{ title }}</h1>
      <UiButton v-if="actionLabel" kind="text" :loading="actionLoading" @click="$emit('action')">{{
        actionLabel
      }}</UiButton>
    </div>
    <p v-if="subtitle">{{ subtitle }}</p>
  </header>
</template>
<style scoped lang="scss">
.ui-detail-header {
  padding: 32px 0 28px;
  > div {
    display: flex;
    align-items: center;
    gap: 12px;
    min-height: 44px;
  }
  .back {
    width: 32px;
    flex: none;
    color: var(--ui-text-primary);
  }
  h1 {
    margin: 0;
    flex: 1;
    min-width: 0;
    font-size: 26px;
    line-height: 1.3;
    font-weight: 600;
  }
  p {
    margin: 18px 0 0;
    color: var(--ui-text-secondary);
    font-size: 13px;
    line-height: 1.6;
  }
}
@media (max-width: 360px) {
  .ui-detail-header h1 {
    font-size: 23px;
  }
}
</style>
