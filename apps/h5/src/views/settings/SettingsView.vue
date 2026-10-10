<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { Button } from 'tdesign-mobile-vue';
import { ChevronRightIcon, FileIcon, InfoCircleIcon, UserIcon } from 'tdesign-icons-vue-next';
import DisplayModePicker from '@/components/DisplayModePicker.vue';
import RecordDetailHeader from '@/components/RecordDetailHeader.vue';
import { useAuthStore } from '@/stores/auth';

const router = useRouter();
const auth = useAuthStore();
const logoutDialog = ref<HTMLDialogElement | null>(null);
const items = [
  {
    title: '账号设置',
    description: '个人资料与登录安全',
    path: '/settings/account',
    icon: UserIcon,
  },
  { title: '用户协议', description: '了解使用条款', path: '/settings/agreement', icon: FileIcon },
  { title: '隐私政策', description: '了解信息使用方式', path: '/settings/privacy', icon: FileIcon },
  {
    title: '关于循形',
    description: '应用介绍与版本信息',
    path: '/settings/about',
    icon: InfoCircleIcon,
  },
];
async function logout(): Promise<void> {
  logoutDialog.value?.close();
  auth.logout();
  await router.replace('/login');
}
</script>

<template>
  <main class="view-page settings-page">
    <RecordDetailHeader title="设置" />
    <section class="settings-section" aria-label="设置项目">
      <div class="settings-list">
        <RouterLink v-for="item in items" :key="item.path" :to="item.path" class="settings-row">
          <component :is="item.icon" aria-hidden="true" />
          <span class="settings-row__body"
            >{{ item.title
            }}<span class="settings-row__description">{{ item.description }}</span></span
          >
          <ChevronRightIcon class="settings-row__chevron" aria-hidden="true" />
        </RouterLink>
      </div>
    </section>
    <DisplayModePicker />
    <Button class="logout-button" variant="outline" block @click="logoutDialog?.showModal()"
      >退出登录</Button
    >
    <dialog ref="logoutDialog" class="logout-dialog" aria-labelledby="logout-title">
      <h2 id="logout-title">退出当前账号？</h2>
      <p>你的记录会保留，再次登录后仍可查看</p>
      <div>
        <Button variant="outline" @click="logoutDialog?.close()">取消</Button
        ><Button theme="primary" @click="logout">退出登录</Button>
      </div>
    </dialog>
  </main>
</template>

<style scoped lang="scss">
@use '@/styles/settings';
.logout-button {
  color: var(--color-danger-text);
  background: var(--color-surface);
}
.logout-dialog {
  width: min(320px, calc(100% - 40px));
  padding: 24px;
  color: var(--color-text-primary);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--border-radius-lg);
  &::backdrop {
    background: rgb(0 0 0 / 35%);
  }
  h2 {
    margin: 0;
    font-size: 1rem;
  }
  p {
    margin: 12px 0 22px;
    color: var(--color-text-secondary);
    font-size: 0.8125rem;
    line-height: 1.6;
  }
  > div {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 12px;
  }
}
</style>
