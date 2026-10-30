<script setup lang="ts">
import type { UserGender } from '@fit-trace/shared';
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import dayjs from 'dayjs';
import UiDetailHeader from '@/components/ui/UiDetailHeader.vue';
import UserAvatar from '@/components/UserAvatar.vue';
import { useAuthStore } from '@/stores/auth';

const auth = useAuthStore();
const router = useRouter();
const nickname = computed(
  () => auth.user?.nickname || auth.user?.email.split('@')[0] || '循形Fitness用户',
);
const genderLabels: Record<UserGender, string> = {
  UNSPECIFIED: '未设置',
  MALE: '男',
  FEMALE: '女',
};
const gender = computed(() => genderLabels[auth.user?.gender ?? 'UNSPECIFIED']);
const joinedAt = computed(() =>
  auth.user?.createdAt ? dayjs(auth.user.createdAt).format('YYYY年M月D日') : '—',
);
</script>

<template>
  <main class="view-page ui-page profile-info-page">
    <UiDetailHeader
      title="个人资料"
      action-label="编辑资料"
      @action="router.push('/settings/account')"
    />
    <section class="settings-section" aria-label="个人资料">
      <dl class="settings-list">
        <div class="settings-row">
          <dt>头像</dt>
          <dd class="settings-row__value">
            <UserAvatar :url="auth.user?.avatarUrl" :name="nickname" :size="60" />
          </dd>
        </div>
        <div class="settings-row">
          <dt>昵称</dt>
          <dd class="settings-row__value">{{ nickname }}</dd>
        </div>
        <div class="settings-row">
          <dt>性别</dt>
          <dd class="settings-row__value">{{ gender }}</dd>
        </div>
        <div class="settings-row">
          <dt>邮箱</dt>
          <dd class="settings-row__value">{{ auth.user?.email ?? '—' }}</dd>
        </div>
        <div class="settings-row">
          <dt>加入时间</dt>
          <dd class="settings-row__value">{{ joinedAt }}</dd>
        </div>
      </dl>
    </section>
  </main>
</template>

<style scoped lang="scss">
@use '@/styles/settings';
.settings-list {
  margin: 0;
}
.settings-row {
  dt {
    flex: none;
  }
  dd {
    margin-block: 0;
  }
}
</style>
