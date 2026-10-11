<script setup lang="ts">
defineProps<{ items: Array<{ label: string; value: string | number }> }>();
</script>
<template>
  <dl class="ui-info-card">
    <div v-for="item in items" :key="item.label">
      <dt>{{ item.label }}</dt>
      <dd>{{ item.value }}</dd>
    </div>
  </dl>
</template>
<style scoped lang="scss">
.ui-info-card {
  margin: 20px 0 0;
  padding: 8px 16px;
  background: var(--color-surface);
  border-radius: 20px;
  > div {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 16px;
    padding: 18px 0;
    + div {
      border-top: 1px solid var(--ui-border);
    }
  }
  dt {
    flex: none;
    color: var(--ui-text-secondary);
    font-size: 13px;
  }
  dd {
    margin: 0;
    min-width: 0;
    text-align: right;
    color: var(--ui-text-primary);
    font-size: 14px;
    line-height: 1.6;
    overflow-wrap: anywhere;
    white-space: pre-wrap;
  }
}
</style>
