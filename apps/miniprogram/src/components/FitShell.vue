<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app';
import { session } from '../session';
import FitTabBar from './FitTabBar.vue';
const props = defineProps<{
  title?: string;
  brand?: boolean;
  back?: boolean;
  tab?: string;
  guest?: boolean;
}>();
onShow(() => {
  const pages = getCurrentPages();
  if (
    !session.value &&
    pages[pages.length - 1]?.route !== 'pages/index/index' &&
    pages[pages.length - 1]?.route !== 'pages/agreement/index'
  ) {
    uni.reLaunch({ url: '/pages/index/index' });
  }
});
function goBack() {
  if (getCurrentPages().length > 1) uni.navigateBack();
  else uni.reLaunch({ url: '/pages/index/index' });
}
</script>
<template>
  <view class="fit-shell" :class="{ 'fit-brand': props.brand, 'with-tabs': props.tab }">
    <t-navbar :fixed="false" :safe-area-inset-top="true" :title="props.back ? props.title : ''">
      <template #left>
        <view v-if="props.back" class="nav-back" role="button" aria-label="返回" @tap="goBack">
          <t-icon name="chevron-left" size="24px" />
        </view>
        <text v-else class="nav-title">{{ props.title || '循形Fitness' }}</text>
      </template>
    </t-navbar>
    <view class="fit-content"><slot /></view>
    <FitTabBar v-if="props.tab && !props.guest" :active="props.tab" />
    <slot name="overlay" />
  </view>
</template>
