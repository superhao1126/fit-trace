<script setup lang="ts">
const props = defineProps<{ loading?: boolean; error?: string; empty?: string }>();
defineEmits<{ retry: [] }>();
</script>
<template>
  <view v-if="props.loading" class="card state-card">
    <t-loading theme="circular" size="24px" text="正在加载…" />
  </view>
  <view v-else-if="props.error" class="card state-card">
    <view class="state-title">暂时没能加载</view>
    <view class="sub">{{ props.error }}</view>
    <t-button theme="primary" variant="light" shape="round" size="small" @click="$emit('retry')"
      >重新加载</t-button
    >
  </view>
  <view v-else-if="props.empty" class="card state-card">
    <image class="empty-sprout" src="/static/brand/sprout.svg" mode="aspectFit" />
    <view class="state-title">{{ props.empty }}</view>
  </view>
</template>
