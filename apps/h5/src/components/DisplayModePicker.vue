<script setup lang="ts">
import { ref, watch } from 'vue';
import { DesktopIcon, ModeDarkIcon, ModeLightIcon } from 'tdesign-icons-vue-next';
import { getThemeMode, setThemeMode } from '@/utils/theme';

const mode = ref(getThemeMode());
const options = [
  { value: 'system', title: '跟随系统', description: '自动适配设备的显示模式', icon: DesktopIcon },
  { value: 'light', title: '浅色模式', description: '清晰明亮，适合日间使用', icon: ModeLightIcon },
  {
    value: 'dark',
    title: '深色模式',
    description: '降低画面亮度，适合夜间使用',
    icon: ModeDarkIcon,
  },
] as const;
watch(mode, setThemeMode);
</script>

<template>
  <div class="display-settings">
    <section class="settings-section" aria-labelledby="display-mode-title">
      <h2 id="display-mode-title">显示模式</h2>
      <div class="settings-list" role="group" aria-labelledby="display-mode-title">
        <label
          v-for="item in options"
          :key="item.value"
          class="settings-row mode-row"
          :class="{ selected: mode === item.value }"
        >
          <component :is="item.icon" aria-hidden="true" />
          <span class="settings-row__body"
            >{{ item.title
            }}<span class="settings-row__description">{{ item.description }}</span></span
          >
          <input
            v-model="mode"
            type="radio"
            name="theme-mode"
            :value="item.value"
            :aria-label="item.title"
          />
        </label>
      </div>
      <p class="settings-hint">选择后立即生效，自动保存在当前设备</p>
    </section>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/settings';
.mode-row {
  cursor: pointer;
  input {
    width: 18px;
    height: 18px;
    margin: 0;
    flex: none;
    accent-color: var(--color-accent-text);
  }
  &.selected {
    background: var(--color-primary-light);
    > svg {
      color: var(--color-accent-text);
    }
  }
}
</style>
