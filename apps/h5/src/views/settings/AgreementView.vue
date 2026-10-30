<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import UiDetailHeader from '@/components/ui/UiDetailHeader.vue';
import PolicyContent from '@/components/PolicyContent.vue';

const route = useRoute();
const kind = computed(() => (route.meta.policyKind === 'privacy' ? 'privacy' : 'agreement'));
</script>

<template>
  <main
    class="view-page ui-page agreement-page"
    :class="{ 'public-policy-page': route.meta.publicPolicy }"
  >
    <UiDetailHeader
      :title="kind === 'agreement' ? '用户协议' : '隐私政策'"
      :subtitle="
        kind === 'agreement' ? '循形Fitness · 基础使用条款' : '循形Fitness · 信息使用说明'
      "
    />
    <section class="policy-card"><PolicyContent :kind="kind" /></section>
  </main>
</template>

<style scoped lang="scss">
.policy-card {
  padding: 24px 20px;
  background: var(--color-surface);
  border-radius: 20px;
  :deep(.settings-document) {
    font-size: 14px;
    line-height: 1.9;
  }
  :deep(.settings-document > section) {
    margin-bottom: 32px;
  }
  :deep(.settings-document > section:last-child) {
    margin-bottom: 0;
  }
  :deep(h2) {
    font-size: 16px;
    margin-bottom: 12px;
  }
}
.public-policy-page {
  width: min(100%, 560px);
  min-height: 100dvh;
  margin: 0 auto;
  padding-top: var(--app-safe-area-top);
  padding-bottom: calc(24px + var(--app-safe-area-bottom));
}
</style>
