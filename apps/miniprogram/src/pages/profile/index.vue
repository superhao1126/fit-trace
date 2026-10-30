<script setup lang="ts">
import { ref } from 'vue';
import { session, logout, notify } from '../../session';
import FitShell from '../../components/FitShell.vue';
const busy = ref(false),
  confirmExit = ref(false),
  about = ref(false);
const nav = (path: string) => uni.navigateTo({ url: '/pages/' + path + '/index' });
async function exit() {
  if (busy.value) return;
  busy.value = true;
  try {
    await logout();
    uni.reLaunch({ url: '/pages/index/index' });
  } catch (e) {
    notify(e);
  } finally {
    busy.value = false;
    confirmExit.value = false;
  }
}
</script>
<template>
  <FitShell brand title="我的" tab="profile">
    <view class="card account-card"
      ><view class="row"
        ><image
          class="avatar"
          :src="session?.user.avatarUrl || '/static/brand/sprout.svg'"
          mode="aspectFit"
        /><view class="flex-1"
          ><view class="heading">{{ session?.user.nickname || '循形用户' }}</view
          ><view class="caption account-email">{{
            session?.user.email || '请重新使用邮箱登录'
          }}</view></view
        ></view
      ></view
    >
    <view class="card menu-card"
      ><t-cell title="训练历史" arrow :bordered="false" @click="nav('history')" /><t-cell
        title="每周计划"
        arrow
        :bordered="false"
        @click="nav('plan')" /><t-cell
        title="体重记录"
        arrow
        :bordered="false"
        @click="nav('body')" /><t-cell
        title="用户协议"
        arrow
        :bordered="false"
        @click="nav('agreement')" /><t-cell
        title="隐私政策"
        arrow
        :bordered="false"
        @click="nav('agreement')" /><t-cell
        title="关于循形Fitness"
        note="0.1.0"
        arrow
        :bordered="false"
        @click="about = true"
    /></view>
    <view class="spaced profile-exit"
      ><t-button
        variant="outline"
        shape="round"
        size="large"
        block
        :loading="busy"
        @click="confirmExit = true"
        >退出登录</t-button
      ></view
    >
    <template #overlay
      ><t-dialog
        :visible="confirmExit"
        title="退出当前账号？"
        content="记录会保留在账号中，本机训练草稿也会保留。"
        :confirm-btn="{ content: '退出登录', loading: busy, disabled: busy }"
        cancel-btn="取消"
        @confirm="exit"
        @close="!busy && (confirmExit = false)" /><t-dialog
        :visible="about"
        title="循形Fitness"
        content="版本 0.1.0"
        confirm-btn="知道了"
        @confirm="about = false"
        @close="about = false"
    /></template>
  </FitShell>
</template>
<style scoped>
.profile-exit {
  margin-top: 12px;
}
.account-card {
  padding: 16px 20px;
}
.account-email {
  margin-top: 6px;
  word-break: break-all;
}
.avatar {
  width: 54px;
  height: 56px;
  flex-shrink: 0;
}
.menu-card {
  --td-cell-height: 40px;
  --td-cell-vertical-padding: 8px;
  padding: 4px 20px;
}
</style>
