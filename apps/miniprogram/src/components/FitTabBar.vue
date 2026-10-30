<script setup lang="ts">
const props = defineProps<{ active: string }>();
const tabs = [
  { value: 'today', label: '今日', icon: 'home', path: 'index' },
  { value: 'history', label: '记录', icon: 'file-paste', path: 'history' },
  { value: 'body', label: '体重', icon: 'chart-bubble', path: 'body' },
  { value: 'profile', label: '我的', icon: 'user', path: 'profile' },
];
function change({ value }: { value: string | number }) {
  const tab = tabs.find((t) => t.value === value);
  if (tab) uni.reLaunch({ url: '/pages/' + tab.path + '/index' });
}
</script>
<template>
  <t-tab-bar
    :value="props.active"
    :fixed="true"
    :bordered="false"
    :split="false"
    :safe-area-inset-bottom="true"
    :z-index="100"
    @change="change"
  >
    <t-tab-bar-item v-for="tab in tabs" :key="tab.value" :value="tab.value" :icon="tab.icon">{{
      tab.label
    }}</t-tab-bar-item>
  </t-tab-bar>
</template>
