<script setup lang="ts">
import UiButton from '@/components/ui/UiButton.vue';
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import UiIcon from '@/components/ui/UiIcon.vue';
import DisplayModePicker from '@/components/DisplayModePicker.vue';
import UiDetailHeader from '@/components/ui/UiDetailHeader.vue';
import { useAuthStore } from '@/stores/auth';

const router = useRouter();
const auth = useAuthStore();
const logoutDialog = ref<HTMLDialogElement | null>(null);
const items = [
  {
    title: '账号设置',
    description: '个人资料与登录安全',
    path: '/settings/account',
    icon: 'user' as const,
  },
  {
    title: '用户协议',
    description: '了解使用条款',
    path: '/settings/agreement',
    icon: 'archive' as const,
  },
  {
    title: '隐私政策',
    description: '了解信息使用方式',
    path: '/settings/privacy',
    icon: 'archive' as const,
  },
  {
    title: '关于循形',
    description: '应用介绍与版本信息',
    path: '/settings/about',
    icon: 'home' as const,
  },
];
async function logout(): Promise<void> {
  logoutDialog.value?.close();
  auth.logout();
  await router.replace('/login');
}
</script>

<template>
  <main class="view-page ui-page settings-page">
    <UiDetailHeader title="设置" />
    <section class="settings-section" aria-label="设置项目">
      <div class="settings-menu">
        <RouterLink v-for="item in items" :key="item.path" :to="item.path" class="settings-row">
          <UiIcon :name="item.icon" />
          <span class="settings-row__body"
            >{{ item.title
            }}<span class="settings-row__description">{{ item.description }}</span></span
          >
          <UiIcon name="right" class="settings-row__chevron" />
        </RouterLink>
      </div>
    </section>
    <DisplayModePicker />
    <UiButton class="logout-button" kind="secondary" block @click="logoutDialog?.showModal()"
      >退出登录</UiButton
    >
    <dialog ref="logoutDialog" class="logout-dialog" aria-labelledby="logout-title">
      <h2 id="logout-title">退出当前账号？</h2>
      <p>你的记录会保留，再次登录后仍可查看</p>
      <div>
        <UiButton kind="secondary" @click="logoutDialog?.close()">取消</UiButton
        ><UiButton @click="logout">退出登录</UiButton>
      </div>
    </dialog>
  </main>
</template>

<style scoped lang="scss">
@use '@/styles/settings';
.settings-menu {
  display: grid;
  gap: 8px;
  .settings-row {
    background: var(--color-surface);
    border: 0;
    border-radius: 20px;
    min-height: 76px;
  }
}
.logout-button.ui-button--secondary.t-button {
  color: var(--ui-primary);
  background: var(--color-surface);
}
.logout-dialog {
  width: min(320px, calc(100% - 40px));
  padding: 24px;
  color: var(--color-text-primary);
  background: var(--color-surface);
  border: 0;
  border-radius: 24px;
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
