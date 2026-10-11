<script setup lang="ts">
import { ref, watch } from 'vue';
import UiTabs from '@/components/ui/UiTabs.vue';
import { getThemeMode, setThemeMode } from '@/utils/theme';
const mode = ref(getThemeMode());
const options = [
  { value: 'system', label: '跟随系统' },
  { value: 'light', label: '浅色' },
  { value: 'dark', label: '深色' },
];
watch(mode, setThemeMode);
</script>
<template>
  <section class="display-settings" aria-labelledby="display-mode-title">
    <h2 id="display-mode-title">显示模式</h2>
    <UiTabs
      :model-value="mode"
      :options="options"
      label="显示模式"
      @update:model-value="mode = $event as typeof mode"
    />
    <p>选择后立即生效，自动保存在当前设备</p>
  </section>
</template>
<style scoped lang="scss">
.display-settings {
  margin: 32px 0;
  h2 {
    margin: 0 0 12px;
    color: var(--ui-text-secondary);
    font-size: 13px;
    font-weight: 400;
  }
  p {
    margin: 0;
    color: var(--ui-text-tertiary);
    font-size: 12px;
    line-height: 1.7;
  }
}
</style>
