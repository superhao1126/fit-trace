<script setup lang="ts">
import UiBrandMark from './UiBrandMark.vue';
defineOptions({ inheritAttrs: false });
withDefaults(defineProps<{ loading?: boolean; text?: string }>(), {
  loading: true,
  text: '正在加载',
});
</script>
<template>
  <div v-bind="$attrs" class="ui-loading" :aria-busy="loading">
    <div v-if="loading" class="ui-loading__status" role="status" aria-live="polite">
      <UiBrandMark :size="40" />
      <span>{{ text }}</span>
    </div>
    <div v-show="!loading" class="ui-loading__content"><slot /></div>
  </div>
</template>
<style scoped lang="scss">
.ui-loading {
  width: 100%;
  min-width: 0;
  color: var(--ui-text-primary);
}
.ui-loading__content {
  display: contents;
}
.ui-loading__status {
  position: fixed;
  top: 50%;
  left: 50%;
  z-index: 30;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  max-width: calc(100% - 48px);
  color: var(--ui-primary);
  span {
    color: var(--ui-text-secondary);
    font-size: 13px;
    line-height: 1.5;
    text-align: center;
  }
}
</style>
