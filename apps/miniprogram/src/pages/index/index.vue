<script setup lang="ts">
import { computed, ref, onUnmounted } from 'vue';
import { onShow, onHide } from '@dcloudio/uni-app';
import type { TrainingTemplate, TrainingPlanState, DashboardOverview } from '@fit-trace/shared';
import { session, login, sendEmailCode, notify, request } from '../../session';
import { draftKey } from '../../draft';
import { dayNames, weekDays } from '../../presentation';
import { useTrainingLauncher } from '../../training-launcher';
import FitShell from '../../components/FitShell.vue';
import FitState from '../../components/FitState.vue';
const agreed = ref(false),
  busy = ref(false),
  email = ref(''),
  emailCode = ref(''),
  sending = ref(false),
  now = ref(Date.now()),
  cooldownEndsAt = ref(0),
  error = ref(''),
  loading = ref(false),
  hasDraft = ref(false);
const templates = ref<TrainingTemplate[]>([]),
  plan = ref<TrainingPlanState | null>(null),
  dashboard = ref<DashboardOverview | null>(null);
const selectedTimestamp = ref<number | null>(null);
const days = computed(() => weekDays(new Date(now.value)));
const selectedDay = computed(
  () =>
    days.value.find((d) => d.timestamp === selectedTimestamp.value) ??
    days.value.find((d) => d.today)!,
);
const selectedTemplate = computed(() =>
  templates.value.find(
    (t) =>
      t.id === plan.value?.days.find((d) => d.weekday === selectedDay.value.weekday)?.templateId,
  ),
);
const dateLabel = computed(() => selectedDay.value.fullLabel);
const nav = (page: string) => uni.navigateTo({ url: '/pages/' + page + '/index' });
const { start, resume, draftPrompt } = useTrainingLauncher();
async function load() {
  if (!session.value || loading.value) return;
  loading.value = true;
  hasDraft.value = !!uni.getStorageSync(draftKey(session.value.user.id));
  try {
    [templates.value, plan.value, dashboard.value] = await Promise.all([
      request<TrainingTemplate[]>('/training/templates'),
      request<TrainingPlanState>('/training/plan/state'),
      request<DashboardOverview>('/dashboard?tzOffset=' + -new Date().getTimezoneOffset()),
    ]);
    error.value = '';
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败';
  } finally {
    loading.value = false;
  }
}
const retrySeconds = computed(() =>
  Math.max(0, Math.ceil((cooldownEndsAt.value - now.value) / 1000)),
);
let ticker: ReturnType<typeof setInterval> | undefined;
const validEmail = () => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value.trim());
async function getCode() {
  if (sending.value || retrySeconds.value || busy.value) return;
  if (!validEmail()) {
    notify(new Error('请输入正确的邮箱地址'));
    return;
  }
  sending.value = true;
  try {
    const result = await sendEmailCode(email.value);
    cooldownEndsAt.value = Date.now() + result.retryAfterSeconds * 1000;
    uni.setStorageSync('fit-trace:email-code:cooldown', cooldownEndsAt.value);
    uni.showToast({ title: '验证码已发送', icon: 'none' });
  } catch (e) {
    notify(e);
  } finally {
    sending.value = false;
  }
}
async function signIn() {
  if (busy.value) return;
  if (!agreed.value) {
    notify(new Error('请阅读并同意用户协议和隐私政策'));
    return;
  }
  if (!validEmail()) {
    notify(new Error('请输入正确的邮箱地址'));
    return;
  }
  if (!/^\d{6}$/.test(emailCode.value)) {
    notify(new Error('请输入 6 位邮箱验证码'));
    return;
  }
  busy.value = true;
  try {
    await login(email.value, emailCode.value);
    emailCode.value = '';
    await load();
  } catch (e) {
    notify(e);
  } finally {
    busy.value = false;
  }
}
onShow(() => {
  now.value = Date.now();
  cooldownEndsAt.value = Number(uni.getStorageSync('fit-trace:email-code:cooldown')) || 0;
  clearInterval(ticker);
  ticker = setInterval(() => {
    now.value = Date.now();
  }, 1000);
  void load();
});
onHide(() => clearInterval(ticker));
onUnmounted(() => clearInterval(ticker));
</script>
<template>
  <FitShell brand title="循形Fitness" :tab="session ? 'today' : undefined" :guest="!session">
    <template v-if="!session">
      <view class="brand-intro">
        <view class="title">记录一点，慢慢变好</view>
        <view class="sub">训练和身体变化，都记在这里</view>
        <image class="login-art" src="/static/brand/login.svg" mode="aspectFit" />
      </view>
      <view class="card login-card">
        <view
          ><text class="field-label">邮箱</text
          ><view class="form-field"
            ><t-input
              v-model:value="email"
              placeholder="请输入邮箱"
              :maxlength="254"
              :disabled="busy || sending"
              borderless
              cursor-color="#229d60" /></view
        ></view>
        <view>
          <text class="field-label">验证码</text>
          <view class="row code-row">
            <view class="form-field flex-1"
              ><t-input
                v-model:value="emailCode"
                type="number"
                placeholder="6 位验证码"
                :maxlength="6"
                :disabled="busy"
                borderless
                cursor-color="#229d60"
                @enter="signIn"
            /></view>
            <t-button
              theme="primary"
              variant="light"
              shape="round"
              :loading="sending"
              :disabled="sending || busy || retrySeconds > 0"
              @click="getCode"
              >{{ retrySeconds ? retrySeconds + ' 秒后重发' : '获取验证码' }}</t-button
            >
          </view>
        </view>
        <view class="agreement-row">
          <t-checkbox
            :checked="agreed"
            :disabled="busy"
            :block="false"
            icon="circle"
            t-class-icon="login-agreement-icon"
            borderless
            @change="agreed = $event.checked"
          />
          <view class="caption"
            >我已阅读并同意<text class="agreement-link" @tap="nav('agreement')"
              >《用户协议》和《隐私政策》</text
            ></view
          >
        </view>
        <t-button
          theme="primary"
          shape="round"
          size="large"
          block
          :loading="busy"
          :disabled="busy || sending"
          @click="signIn"
          >登录 / 注册</t-button
        >
      </view>
    </template>
    <template v-else>
      <view class="title">{{ selectedDay.today ? '今日训练' : '训练计划' }}</view
      ><view class="sub">{{ dateLabel }}</view>
      <view class="week-strip"
        ><view
          v-for="day in days"
          :key="day.timestamp"
          class="week-day"
          :class="{ selected: day.timestamp === selectedDay.timestamp }"
          aria-role="button"
          :aria-label="day.fullLabel + (day.timestamp === selectedDay.timestamp ? '，已选中' : '')"
          @tap="selectedTimestamp = day.timestamp"
          ><text class="caption">{{ day.label }}</text
          ><text>{{ day.date }}</text></view
        ></view
      >
      <FitState :loading="loading && !dashboard" :error="error" @retry="load" />
      <view v-if="!loading || dashboard" class="card today-card">
        <view class="between"
          ><text class="caption">{{
            selectedDay.today ? '今日计划' : dayNames[selectedDay.weekday] + '计划'
          }}</text
          ><text class="link" @tap="nav('plan')">查看计划 ›</text></view
        >
        <view class="between plan-hero"
          ><view class="flex-1"
            ><view class="title">{{
              selectedTemplate?.name || (plan?.configured ? '休息日' : '未设置计划')
            }}</view
            ><view class="sub">{{
              selectedTemplate
                ? selectedTemplate.durationMinutes +
                  ' 分钟 · ' +
                  selectedTemplate.exercises.length +
                  ' 个动作'
                : plan?.configured
                  ? '无训练安排'
                  : '设置每周训练安排'
            }}</view></view
          ><image class="today-sprout" src="/static/brand/sprout.svg" mode="aspectFit"
        /></view>
        <t-button
          theme="primary"
          shape="round"
          size="large"
          block
          @click="selectedTemplate ? start(selectedTemplate) : nav('plan')"
          >{{
            selectedTemplate
              ? selectedDay.today
                ? '按计划开始训练'
                : '使用此计划训练'
              : '查看每周计划'
          }}</t-button
        >
      </view>
      <view v-if="dashboard" class="card summary-card"
        ><view class="between"
          ><text class="caption">近 7 天记录</text
          ><text class="link" @tap="nav('history')">查看全部 ›</text></view
        ><view class="metrics"
          ><view
            ><view class="metric-number">{{ dashboard.recentTrend.workoutCount }}</view
            ><text class="caption">次训练</text></view
          ><view
            ><view class="metric-number">{{ dashboard.recentTrend.workoutMinutes }}</view
            ><text class="caption">分钟训练</text></view
          ><view
            ><view class="metric-number">{{ dashboard.latestBodyRecord?.weight ?? '—' }}</view
            ><text class="caption">kg 最新体重</text></view
          ></view
        ></view
      >
      <view class="row quick-actions spaced"
        ><view class="flex-1"
          ><t-button
            theme="primary"
            variant="light"
            shape="round"
            size="large"
            block
            @click="start()"
            >＋ 自由训练</t-button
          ></view
        ><view class="flex-1"
          ><t-button shape="round" variant="outline" size="large" block @click="nav('body')"
            >＋ 记体重</t-button
          ></view
        ></view
      >
      <view v-if="hasDraft" class="card tint draft-card" @tap="resume"
        ><view class="between"
          ><text class="accent">继续上次训练</text><text class="link">继续 ›</text></view
        ><view class="caption">已在本机保存你的训练进度</view></view
      >
    </template>
    <template #overlay
      ><t-dialog
        :visible="draftPrompt"
        title="继续未完成的训练？"
        content="上次进度仍保存在当前账号，回到前台后会校正休息时间。"
        confirm-btn="继续训练"
        cancel-btn="暂时不用"
        @confirm="resume"
        @close="draftPrompt = false"
    /></template>
  </FitShell>
</template>
<style scoped>
.brand-intro {
  padding-top: 4px;
}
.login-art {
  width: 100%;
  height: auto;
  aspect-ratio: 350 / 152;
  display: block;
  margin-top: 8px;
}
.login-card {
  border-radius: 28px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  margin-top: 14px;
}
.code-row {
  gap: 12px;
}
.agreement-row {
  --td-checkbox-title-line-height: 18px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.agreement-row .caption {
  flex: 1;
  line-height: 18px;
}
.agreement-link {
  color: #777;
}
.week-strip {
  display: flex;
  justify-content: space-between;
  gap: 5px;
  margin-top: 16px;
}
.week-day {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 5px;
  height: 56px;
  flex: 1;
  border-radius: 16px;
  font-size: 13px;
}
.week-day.selected {
  background: #fff;
  color: #229d60;
}
.week-day.selected .caption {
  color: #229d60;
}
.plan-hero {
  padding: 4px 0 12px;
}
.today-sprout {
  width: 90px;
  height: 94px;
  flex-shrink: 0;
}
.metrics {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
}
.metrics > view {
  flex: 1;
  min-width: 0;
}
.metric-number {
  font-size: 28px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  line-height: 1.45;
}
.draft-card {
  padding: 16px 20px;
  font-size: 13px;
}
</style>
<style>
.login-agreement-icon {
  margin-top: 0;
  margin-right: 0;
}
</style>
