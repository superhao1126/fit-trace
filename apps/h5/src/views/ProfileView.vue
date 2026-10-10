<script setup lang="ts">
import type { BodyRecord } from '@fit-trace/shared';
import dayjs from 'dayjs';
import { Skeleton } from 'tdesign-mobile-vue';
import UiPageHeader from '@/components/ui/UiPageHeader.vue';
import UiButton from '@/components/ui/UiButton.vue';
import UiIcon from '@/components/ui/UiIcon.vue';
import UiRecordRow from '@/components/ui/UiRecordRow.vue';
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
  <main class="view-page ui-page profile-page">
    <UiPageHeader title="我的" subtitle="记录日常，看见改变"
      ><template #action
        ><UiButton kind="text" @click="router.push('/settings')">设置</UiButton></template
      ></UiPageHeader
    >
    <RouterLink class="account" to="/profile/info" aria-label="查看个人资料">
      <UserAvatar :url="auth.user?.avatarUrl" :name="displayName" fallback="icon" />
      <div class="account__body">
        <h2>{{ displayName }}</h2>
        <p>{{ auth.user?.email }}</p>
        <span v-if="auth.user?.createdAt" class="account__joined"
          >{{ dayjs(auth.user.createdAt).format('YYYY年M月D日') }} 加入</span
        >
      </div>
      <UiIcon name="right" class="account__edit" />
    </RouterLink>

    <section class="profile-section" aria-label="记录档案">
      <UiRecordRow
        kind="body"
        icon="archive"
        tone="neutral"
        title="记录档案"
        subtitle="按日期回看记录"
        @click="router.push('/archive')"
      ></UiRecordRow>
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
          <strong>{{
            auth.user?.goal ? `${goalLabels[auth.user.goal.type]}计划` : '设置健身目标'
          }}</strong>
          <span class="goal-card__action">{{ auth.user?.goal ? '调整' : '去设置' }}</span>
          <UiIcon name="right" class="chevron" />
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
        <span class="row-icon"><UiIcon name="flag" /></span
        ><span class="setting-row__label">设置健身目标</span><UiIcon name="right" class="chevron" />
      </button>
    </section>
  </main>
</template>

<style scoped lang="scss">
.account {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px 16px;
  color: var(--ui-text-primary);
  background: var(--color-surface);
  border-radius: var(--ui-radius-card);
  text-decoration: none;
  &__body {
    min-width: 0;
    flex: 1;
    h2,
    p {
      margin: 0;
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
    }
    h2 {
      font-size: 18px;
      font-weight: 600;
    }
    p {
      margin-top: 5px;
      color: var(--ui-text-secondary);
      font-size: 12px;
    }
  }
  &__joined {
    display: block;
    margin-top: 7px;
    color: var(--ui-text-secondary);
    font-size: 11px;
  }
  &__edit {
    width: 16px;
    height: 16px;
    color: var(--ui-text-tertiary);
  }
}
.profile-section {
  margin-top: 28px;
}
.profile-card {
  border: 0;
  border-radius: var(--ui-radius-card);
  background: var(--color-surface);
  color: var(--ui-text-primary);
  cursor: pointer;
}
.summary-retry {
  margin-top: 10px;
  padding: 6px 0;
  color: var(--ui-text-secondary);
  background: transparent;
  border: 0;
  font-size: 12px;
}
.goal-card {
  display: block;
  width: 100%;
  padding: 20px 16px;
  text-align: left;
  &__heading {
    display: flex;
    align-items: center;
    gap: 6px;
    > strong {
      flex: 1;
      font-size: 16px;
      font-weight: 600;
    }
  }
  &__action {
    color: var(--ui-primary);
    font-size: 12px;
  }
  &__metrics {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 20px minmax(0, 1fr);
    gap: 16px;
    align-items: center;
    margin-top: 32px;
    > div {
      display: grid;
      min-width: 0;
      gap: 10px;
    }
    span {
      font-size: 12px;
      color: var(--ui-text-secondary);
    }
    strong {
      font-size: 30px;
      line-height: 1.2;
      font-weight: 600;
      font-variant-numeric: tabular-nums;
      white-space: nowrap;
    }
    small {
      color: var(--ui-text-secondary);
      font-size: 12px;
      font-weight: 400;
    }
    .goal-card__empty {
      font-size: 17px;
      color: var(--ui-text-secondary);
    }
    .goal-card__separator {
      text-align: center;
      font-size: 18px;
    }
  }
  &__note,
  &__updated {
    margin: 16px 0 0;
    color: var(--ui-text-secondary);
    font-size: 11px;
    line-height: 1.7;
  }
  &__updated {
    margin-top: 6px;
  }
}
.goal-progress {
  margin-top: 24px;
  span {
    display: block;
    margin-bottom: 8px;
    color: var(--ui-text-secondary);
    font-size: 12px;
  }
  > div {
    height: 5px;
    overflow: hidden;
    background: var(--ui-border);
    border-radius: 5px;
    i {
      display: block;
      height: 100%;
      background: var(--ui-primary);
      border-radius: 5px;
    }
  }
}
.chevron {
  width: 16px;
  height: 16px;
  color: var(--ui-primary);
}
.setting-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-height: 80px;
  padding: 16px;
  text-align: left;
  &__label {
    flex: 1;
    font-size: 15px;
  }
}
.row-icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  color: var(--ui-primary);
  background: var(--ui-primary-light);
  border-radius: 14px;
}
@media (max-width: 360px) {
  .goal-card__metrics strong {
    font-size: 26px;
  }
}
</style>
