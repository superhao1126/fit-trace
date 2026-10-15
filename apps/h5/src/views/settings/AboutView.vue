<script setup lang="ts">
import UiIcon from '@/components/ui/UiIcon.vue';
import { version } from '../../../package.json';
import UiDetailHeader from '@/components/ui/UiDetailHeader.vue';
</script>

<template>
  <main class="view-page ui-page about-page">
    <UiDetailHeader title="关于循形" />
    <section class="about-brand" aria-label="循形 FitTrace">
      <img src="/brand/app-icon-512.png" width="76" height="76" alt="循形应用图标" />
      <h2>循形</h2>
      <span class="brand-en">FitTrace</span>
      <p>记录日常，看见改变</p>
    </section>
    <section class="settings-section" aria-label="应用信息">
      <div class="settings-list">
        <div class="settings-row">
          <span>当前版本</span><span class="settings-row__value">v{{ version }}</span>
        </div>
        <RouterLink to="/settings/agreement" class="settings-row"
          ><span class="settings-row__body">用户协议</span
          ><UiIcon name="right" class="settings-row__chevron"
        /></RouterLink>
      </div>
      <p class="about-description">
        循形陪你记录身体、饮食、训练与身材变化，用清晰的记录和趋势，找到适合自己的节奏
      </p>
    </section>
  </main>
</template>

<style scoped lang="scss">
@use '@/styles/settings';
.about-brand {
  display: grid;
  justify-items: center;
  padding: 32px 0 48px;
  img {
    border-radius: 18px;
  }
  h2 {
    margin: 16px 0 0;
    font-size: 1.25rem;
    font-weight: 600;
    span {
      margin-left: 6px;
      color: var(--color-text-secondary);
      font-size: 0.875rem;
      font-weight: 400;
    }
  }
  p {
    margin: 8px 0 0;
    color: var(--color-text-secondary);
    font-size: 0.8125rem;
  }
}
.brand-en {
  margin-top: 8px;
  color: var(--ui-text-secondary);
  font-size: 13px;
}
.about-description {
  margin: 20px 12px 0;
  color: var(--color-text-secondary);
  font-size: 0.8125rem;
  line-height: 1.85;
  text-align: center;
}
</style>
