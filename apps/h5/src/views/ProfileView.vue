<script setup lang="ts">
import type { BodyRecord } from '@fit-trace/shared';
import dayjs from 'dayjs';
import { Skeleton } from 'tdesign-mobile-vue';
import { ChevronRightIcon, FlagIcon, MeasurementIcon, SettingIcon } from 'tdesign-icons-vue-next';
import { computed, onActivated, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { getLatestBodyRecord } from '@/api/body-records';
import { useAuthStore } from '@/stores/auth';
import UserAvatar from '@/components/UserAvatar.vue';

defineOptions({ name: 'ProfileView' });

const auth = useAuthStore();
const router = useRouter();
const loading = ref(true);
const summaryError = ref(false);
const latestBody = ref<BodyRecord | null>(null);
const displayName = computed(
  () => auth.user?.nickname || auth.user?.email?.split('@')[0] || 'FitTrace 用户',
);
const goalProgress = computed(() => {
  const goal = auth.user?.goal;
  const current = latestBody.value?.weight;
  if (
    !goal ||
    current === undefined ||
    goal.type === 'MAINTAIN' ||
    goal.startWeight === goal.targetWeight
  )
    return null;
  return Math.max(
    0,
    Math.min(
      100,
      Math.round(((current - goal.startWeight) / (goal.targetWeight - goal.startWeight)) * 100),
    ),
  );
});
const goalLabels = { LOSE_FAT: '减脂', GAIN_MUSCLE: '增肌', MAINTAIN: '保持' } as const;
async function loadSummary(silent = false): Promise<void> {
  if (!silent) loading.value = true;
  try {
    latestBody.value = await getLatestBodyRecord();
    summaryError.value = false;
  } catch {
    summaryError.value = true;
  } finally {
    if (!silent) loading.value = false;
  }
}

onMounted(() => void loadSummary());
let activated = false;
onActivated(() => {
  if (!activated) {
    activated = true;
    return;
  }
  void loadSummary(true);
});
</script>

<template>
  <main class="view-page profile-page">
    <header class="primary-header profile-header">
      <h1>我的</h1>
      <button
        class="profile-settings"
        type="button"
        aria-label="设置"
        @click="router.push('/settings')"
      >
        <SettingIcon aria-hidden="true" />
      </button>
    </header>

    <RouterLink class="account" to="/profile/info" aria-label="查看个人资料">
      <UserAvatar :url="auth.user?.avatarUrl" :name="displayName" />
      <div class="account__body">
        <h2>{{ displayName }}</h2>
        <p>{{ auth.user?.email }}</p>
        <span v-if="auth.user?.createdAt" class="account__joined"
          >{{ dayjs(auth.user.createdAt).format('YYYY年M月D日') }} 加入</span
        >
      </div>
      <span class="account__edit">查看<ChevronRightIcon aria-hidden="true" /></span>
    </RouterLink>

    <section class="profile-section" aria-label="记录档案">
      <button class="profile-card setting-row" type="button" @click="router.push('/archive')">
        <span class="row-icon"><MeasurementIcon /></span
        ><span class="setting-row__label">记录档案</span
        ><span class="setting-row__value">按日期查看</span><ChevronRightIcon class="chevron" />
      </button>
      <button v-if="summaryError" class="summary-retry" type="button" @click="loadSummary()">
        体重暂时无法读取，点击重试
      </button>
    </section>
    <section class="profile-section" aria-labelledby="goal-title">
      <div class="section-heading"><h2 id="goal-title">我的目标</h2></div>
      <button
        v-if="auth.user?.goal"
        class="profile-card goal-card"
        type="button"
        @click="router.push('/goal')"
      >
        <div class="goal-card__heading">
          <span class="row-icon row-icon--accent"><FlagIcon aria-hidden="true" /></span>
          <strong>{{
            auth.user?.goal ? `${goalLabels[auth.user.goal.type]}计划` : '设置健身目标'
          }}</strong>
          <span class="goal-card__action">{{ auth.user?.goal ? '调整' : '去设置' }}</span>
          <ChevronRightIcon class="chevron" aria-hidden="true" />
        </div>
        <div class="goal-card__metrics">
          <div>
            <span>当前体重</span>
            <Skeleton v-if="loading" :loading="true" animation="gradient" :row-col="[1]" />
            <strong v-else-if="latestBody">{{ latestBody.weight }} <small>kg</small></strong>
            <strong v-else class="goal-card__empty">{{ summaryError ? '—' : '未记录' }}</strong>
          </div>
          <span class="goal-card__separator" aria-hidden="true">→</span>
          <div>
            <span>目标体重</span>
            <strong v-if="auth.user?.goal"
              >{{ auth.user.goal.targetWeight }} <small>kg</small></strong
            >
            <strong v-else class="goal-card__empty">未设置</strong>
          </div>
        </div>
        <div v-if="goalProgress !== null" class="goal-progress">
          <span>已完成 {{ goalProgress }}%</span>
          <div
            role="progressbar"
            aria-label="体重目标进度"
            :aria-valuenow="goalProgress"
            aria-valuemin="0"
            aria-valuemax="100"
          >
            <i :style="{ width: `${goalProgress}%` }" />
          </div>
        </div>
        <p class="goal-card__note">
          <template v-if="auth.user?.goal">
            起始 {{ auth.user.goal.startWeight }} kg
            <template v-if="auth.user.goal.targetDate"
              ><span aria-hidden="true"> · </span>目标日期
              {{ dayjs(auth.user.goal.targetDate).format('M月D日') }}</template
            >
          </template>
          <template v-else>减脂、增肌或保持，找到适合自己的节奏</template>
        </p>
        <p v-if="latestBody" class="goal-card__updated">
          体重更新于 {{ dayjs(latestBody.recordedAt).format('M月D日') }}
        </p>
      </button>
      <button v-else class="profile-card setting-row" type="button" @click="router.push('/goal')">
        <span class="row-icon"><FlagIcon /></span
        ><span class="setting-row__label">设置健身目标</span><ChevronRightIcon class="chevron" />
      </button>
    </section>
  </main>
</template>

<style scoped lang="scss">
.profile-settings {
  display: grid;
  width: 44px;
  height: 44px;
  flex: none;
  place-items: center;
  margin-right: -10px;
  padding: 0;
  background: transparent;
  border: 0;
  border-radius: 50%;
  font-size: 24px;
}
.account {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 8px 0 6px;
  color: var(--color-text-primary);
  text-decoration: none;
  &__body {
    min-width: 0;
    flex: 1;
    h2,
    p {
      overflow: hidden;
      margin: 0;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    h2 {
      font-size: 1.125rem;
      font-weight: 500;
      line-height: 1.4;
    }
    p {
      margin-top: 3px;
      color: var(--color-text-secondary);
      font-size: 0.8125rem;
    }
  }
  &__edit {
    display: flex;
    align-items: center;
    gap: 3px;
    flex: none;
    color: var(--color-text-tertiary);
    font-size: 0.75rem;
  }
  &:focus-visible {
    outline: 2px solid var(--color-accent-text);
    outline-offset: 4px;
  }
  &__joined {
    display: block;
    margin-top: 7px;
    color: var(--color-text-tertiary);
    font-size: 0.6875rem;
  }
}
.profile-section {
  margin-top: 24px;
}
.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
  h2 {
    margin: 0;
    font-size: 0.8125rem;
    font-weight: 600;
  }
  > span {
    color: var(--color-text-tertiary);
    font-size: 0.6875rem;
  }
}
.profile-card {
  overflow: hidden;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--border-radius-md);
}
.profile-page button {
  color: var(--color-text-primary);
  cursor: pointer;
  &:focus-visible {
    outline: 2px solid var(--color-accent-text);
    outline-offset: 3px;
  }
}
.summary-retry {
  margin-top: 8px;
  padding: 4px 0;
  color: var(--color-text-secondary);
  font-size: 0.75rem;
  background: transparent;
  border: 0;
}
.row-icon {
  display: grid;
  width: 32px;
  height: 32px;
  flex: none;
  place-items: center;
  color: var(--color-text-secondary);
  font-size: 1.125rem;
  background: var(--color-surface-muted);
  border-radius: 10px;
  &--accent {
    color: var(--color-accent-text);
    background: var(--color-primary-light);
  }
}
.chevron {
  flex: none;
  color: var(--color-text-tertiary);
  font-size: 1rem;
}
.goal-card {
  display: block;
  width: 100%;
  padding: 16px;
  text-align: left;
  &__heading {
    display: flex;
    align-items: center;
    gap: 10px;
    > strong {
      flex: 1;
      font-size: 0.9375rem;
      font-weight: 650;
    }
  }
  &__action {
    color: var(--color-text-tertiary);
    font-size: 0.75rem;
  }
  &__metrics {
    display: grid;
    grid-template-columns: 1fr 24px 1fr;
    align-items: center;
    gap: 14px;
    margin-top: 20px;
    > div {
      display: grid;
      min-width: 0;
      gap: 7px;
    }
    span {
      color: var(--color-text-tertiary);
      font-size: 0.75rem;
    }
    strong {
      font-size: 1.625rem;
      font-weight: 650;
      line-height: 1.2;
      font-variant-numeric: tabular-nums;
      letter-spacing: -0.035em;
    }
    small {
      color: var(--color-text-secondary);
      font-size: 0.75rem;
      font-weight: 400;
      letter-spacing: 0;
    }
    .goal-card__empty {
      padding: 5px 0;
      color: var(--color-text-tertiary);
      font-size: 1rem;
      font-weight: 500;
    }
    .goal-card__separator {
      font-size: 1.25rem;
      text-align: center;
    }
    .t-skeleton {
      max-width: 100px;
      height: 31px;
    }
  }
  &__note,
  &__updated {
    margin: 16px 0 0;
    color: var(--color-text-secondary);
    font-size: 0.6875rem;
    line-height: 1.6;
  }
  &__note {
    padding-top: 12px;
    border-top: 1px solid var(--color-border);
  }
  &__updated {
    margin-top: 2px;
    color: var(--color-text-tertiary);
  }
  &:active {
    background: var(--color-surface-muted);
  }
}
.setting-row {
  display: flex;
  width: 100%;
  min-height: 62px;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: transparent;
  border: 0;
  text-align: left;
  + .setting-row {
    position: relative;
    &::before {
      position: absolute;
      top: 0;
      right: 16px;
      left: 58px;
      height: 1px;
      background: var(--color-border);
      content: '';
    }
  }
  &__label {
    flex: 1;
    font-size: 0.875rem;
  }
  &__value {
    display: flex;
    align-items: center;
    gap: 6px;
    color: var(--color-text-tertiary);
    font-size: 0.75rem;
  }
  &:active {
    background: var(--color-surface-muted);
  }
}
.goal-progress {
  margin: 0 16px 14px;
  > span {
    display: block;
    margin-bottom: 8px;
    color: var(--color-text-secondary);
    font-size: 0.75rem;
  }
  > div {
    height: 5px;
    overflow: hidden;
    border-radius: 3px;
    background: var(--color-surface-muted);
  }
  i {
    display: block;
    height: 100%;
    background: var(--color-primary);
  }
}
</style>
