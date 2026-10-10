<script setup lang="ts">
defineProps<{ title: string; subtitle?: string }>();
</script>
<template>
  <header class="ui-page-header">
    <div>
      <h1>{{ title }}</h1>
      <p v-if="subtitle">{{ subtitle }}</p>
    </div>
    <slot name="action" />
  </header>
</template>
<style scoped lang="scss">
.ui-page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 32px 0 24px;
  h1 {
    margin: 0;
    font-size: 30px;
    font-weight: 600;
    line-height: 1.3;
    letter-spacing: -0.03em;
  }
  p {
    margin: 8px 0 0;
    color: var(--ui-text-secondary);
    font-size: 13px;
    line-height: 1.6;
  }
}
</style>
