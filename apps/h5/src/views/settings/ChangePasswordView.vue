<script setup lang="ts">
import UiInput from '@/components/ui/UiInput.vue';
import UiButton from '@/components/ui/UiButton.vue';
import axios from 'axios';
import type { ApiErrorResponse } from '@fit-trace/shared';
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { uiToast as ToastPlugin } from '@/components/ui/toast';
import UiDetailHeader from '@/components/ui/UiDetailHeader.vue';
import { changePassword, sendPasswordCode, setPassword, verifyPasswordCode } from '@/api/auth';
import { authSession } from '@/api/session';
import { useAuthStore } from '@/stores/auth';
import { showRequestError } from '@/utils/request-error';
import { useEmailCaptcha } from '@/composables/useEmailCaptcha';

const router = useRouter();
const auth = useAuthStore();
const emailCaptcha = useEmailCaptcha();
const mode = ref<'password' | 'email'>('email');
const currentPassword = ref('');
const password = ref('');
const confirmPassword = ref('');
const emailCode = ref('');
const grant = ref<{ token: string; expiresAt: number } | null>(null);
const visible = ref(false);
const saving = ref(false);
const sending = ref(false);
const loading = ref(true);
const loadFailed = ref(false);
const resendAt = ref(0);
const now = ref(Date.now());
const errors = reactive({ currentPassword: '', password: '', confirmPassword: '', emailCode: '' });
const busy = computed(() => saving.value || sending.value || loading.value);
const countdown = computed(() => Math.max(0, Math.ceil((resendAt.value - now.value) / 1000)));
const title = computed(() =>
  auth.user?.hasPassword === false
    ? '设置密码'
    : auth.user?.hasPassword === true
      ? '修改密码'
      : '设置或修改密码',
);
const verifying = computed(() => mode.value === 'email' && !grant.value);
let active = true;
const timer = window.setInterval(() => {
  now.value = Date.now();
}, 1000);

async function load(): Promise<void> {
  loading.value = true;
  loadFailed.value = false;
  try {
    await auth.fetchCurrentUser();
    if (!active) return;
    mode.value = auth.user?.hasPassword === true ? 'password' : 'email';
  } catch (error) {
    loadFailed.value = true;
    showRequestError(error, '账号信息加载失败，请重试');
  } finally {
    loading.value = false;
  }
}
function switchMode(next: 'password' | 'email'): void {
  if (busy.value) return;
  mode.value = next;
  currentPassword.value = '';
  password.value = '';
  confirmPassword.value = '';
  emailCode.value = '';
  grant.value = null;
  Object.assign(errors, { currentPassword: '', password: '', confirmPassword: '', emailCode: '' });
}
function handleError(error: unknown): void {
  if (axios.isAxiosError<ApiErrorResponse>(error)) {
    const code = error.response?.data?.code;
    const message = error.response?.data?.message ?? '';
    if (code === 'CURRENT_PASSWORD_INCORRECT') errors.currentPassword = message;
    if (code === 'EMAIL_CODE_INVALID' || code === 'EMAIL_CODE_EXPIRED') errors.emailCode = message;
    if (code === 'RESET_VERIFICATION_INVALID' || code === 'PASSWORD_CHANGED') {
      grant.value = null;
      password.value = '';
      confirmPassword.value = '';
      errors.emailCode = message;
    }
    if (code === 'AUTH_RATE_LIMITED') {
      const seconds = Number(error.response?.headers['retry-after']);
      if (Number.isFinite(seconds) && seconds > 0) resendAt.value = Date.now() + seconds * 1000;
    }
  }
  showRequestError(error, '操作失败，请稍后重试');
}
async function requestCode(): Promise<void> {
  if (busy.value || countdown.value) return;
  sending.value = true;
  const generation = authSession.generation;
  try {
    const proof = await emailCaptcha.verify();
    if (proof === null || !active || generation !== authSession.generation) return;
    const result = await sendPasswordCode(proof);
    if (!active || generation !== authSession.generation) return;
    resendAt.value = Date.now() + result.retryAfterSeconds * 1000;
    emailCode.value = '';
    errors.emailCode = '';
    ToastPlugin.success('验证码已发送，请查收邮箱');
  } catch (error) {
    if (active && generation === authSession.generation) handleError(error);
  } finally {
    sending.value = false;
  }
}
async function save(): Promise<void> {
  if (busy.value || loadFailed.value) return;
  if (verifying.value) {
    errors.emailCode = /^\d{6}$/.test(emailCode.value) ? '' : '请输入 6 位邮箱验证码';
    if (errors.emailCode) return;
  } else {
    errors.currentPassword =
      mode.value === 'password' &&
      (currentPassword.value.length < 8 || currentPassword.value.length > 72)
        ? '请输入 8 到 72 位当前密码'
        : '';
    errors.password =
      password.value.length < 8 || password.value.length > 72
        ? '新密码长度必须为 8 到 72 位'
        : mode.value === 'password' && password.value === currentPassword.value
          ? '新密码不能与当前密码相同'
          : '';
    errors.confirmPassword =
      confirmPassword.value !== password.value ? '两次输入的新密码不一致' : '';
    if (errors.currentPassword || errors.password || errors.confirmPassword) return;
    if (mode.value === 'email' && (!grant.value || grant.value.expiresAt <= Date.now())) {
      grant.value = null;
      password.value = '';
      confirmPassword.value = '';
      errors.emailCode = '邮箱验证已失效，请重新验证';
      return;
    }
  }
  saving.value = true;
  const generation = authSession.generation;
  const firstSetup = auth.user?.hasPassword === false;
  try {
    if (verifying.value) {
      const result = await verifyPasswordCode(emailCode.value);
      if (!active || generation !== authSession.generation) return;
      grant.value = {
        token: result.resetToken,
        expiresAt: Date.now() + result.expiresInSeconds * 1000,
      };
      emailCode.value = '';
      return;
    }
    const result =
      mode.value === 'password'
        ? await changePassword({ currentPassword: currentPassword.value, password: password.value })
        : await setPassword({ resetToken: grant.value!.token, password: password.value });
    // 页面已退出仍接收同一账号的新会话；退出或切换账号后不能恢复旧账号。
    if (generation !== authSession.generation) return;
    auth.setSession(result);
    if (!active) return;
    ToastPlugin.success(firstSetup ? '密码已设置' : '密码已保存');
    await router.replace('/settings/account');
  } catch (error) {
    if (active && generation === authSession.generation) handleError(error);
  } finally {
    saving.value = false;
  }
}
onMounted(() => {
  void load();
});
onBeforeUnmount(() => {
  active = false;
  window.clearInterval(timer);
});
</script>

<template>
  <main class="view-page ui-page password-settings-page">
    <UiDetailHeader
      :title="verifying ? '验证身份' : mode === 'email' ? '设置新密码' : title"
      :subtitle="verifying ? '通过登录邮箱接收验证码' : '验证身份后设置新的登录密码'"
    />
    <p v-if="loading" class="settings-hint" role="status">正在加载账号信息…</p>
    <div v-else-if="loadFailed" class="load-error">
      <p class="settings-hint">账号信息加载失败</p>
      <UiButton kind="secondary" @click="load">重新加载</UiButton>
    </div>
    <form v-else class="settings-form" novalidate @submit.prevent="save">
      <div v-if="mode === 'email'" class="email-note">
        <span>验证登录邮箱</span><strong>{{ auth.user?.email }}</strong>
        <p class="settings-hint">
          {{
            grant
              ? '邮箱验证已通过，请设置新密码'
              : auth.user?.hasPassword === false
                ? '设置后，也可以使用密码登录'
                : '验证邮箱后，可以设置新的登录密码'
          }}
        </p>
      </div>
      <div class="password-fields">
        <div v-if="verifying" class="form-field">
          <label for="password-email-code" class="form-label">邮箱验证码</label>
          <div class="code-field">
            <UiInput
              id="password-email-code"
              v-model="emailCode"
              aria-label="邮箱验证码"
              class="field-input"
              inputmode="numeric"
              autocomplete="one-time-code"
              maxlength="6"
              placeholder="输入 6 位验证码"
              :readonly="busy"
              :aria-invalid="Boolean(errors.emailCode)"
              aria-describedby="email-code-error"
              @input="errors.emailCode = ''"
            />
            <button type="button" :disabled="busy || countdown > 0" @click="requestCode">
              {{ sending ? '发送中…' : countdown > 0 ? `${countdown} 秒后重发` : '获取验证码' }}
            </button>
          </div>
          <small v-if="errors.emailCode" id="email-code-error" class="field-error" role="alert">{{
            errors.emailCode
          }}</small>
        </div>
        <template v-else>
          <div v-if="mode === 'password'" class="form-field">
            <label for="current-password" class="form-label">当前密码</label>
            <UiInput
              id="current-password"
              v-model="currentPassword"
              aria-label="当前密码"
              class="field-input"
              :type="visible ? 'text' : 'password'"
              autocomplete="current-password"
              maxlength="72"
              placeholder="输入当前密码"
              :readonly="busy"
              :aria-invalid="Boolean(errors.currentPassword)"
              aria-describedby="current-password-error"
              @input="errors.currentPassword = ''"
            />
            <small
              v-if="errors.currentPassword"
              id="current-password-error"
              class="field-error"
              role="alert"
              >{{ errors.currentPassword }}</small
            >
          </div>
          <div class="form-field">
            <label for="new-password" class="form-label">新密码</label>
            <UiInput
              id="new-password"
              v-model="password"
              aria-label="新密码"
              class="field-input"
              :type="visible ? 'text' : 'password'"
              autocomplete="new-password"
              maxlength="72"
              placeholder="设置 8 到 72 位新密码"
              :readonly="busy"
              :aria-invalid="Boolean(errors.password)"
              aria-describedby="new-password-error"
              @input="errors.password = ''"
            />
            <small
              v-if="errors.password"
              id="new-password-error"
              class="field-error"
              role="alert"
              >{{ errors.password }}</small
            >
          </div>
          <div class="form-field">
            <label for="confirm-password" class="form-label">确认新密码</label>
            <UiInput
              id="confirm-password"
              v-model="confirmPassword"
              aria-label="确认新密码"
              class="field-input"
              :type="visible ? 'text' : 'password'"
              autocomplete="new-password"
              maxlength="72"
              placeholder="再次输入新密码"
              :readonly="busy"
              :aria-invalid="Boolean(errors.confirmPassword)"
              aria-describedby="confirm-password-error"
              @input="errors.confirmPassword = ''"
            />
            <small
              v-if="errors.confirmPassword"
              id="confirm-password-error"
              class="field-error"
              role="alert"
              >{{ errors.confirmPassword }}</small
            >
          </div>
        </template>
      </div>
      <div class="password-note">
        <label v-if="!verifying"><input v-model="visible" type="checkbox" />显示密码</label>
        <p class="settings-hint">保存后当前设备保持登录，其他设备需要重新登录</p>
      </div>
      <UiButton type="submit" block :loading="saving" :disabled="busy">{{
        verifying ? '验证并继续' : auth.user?.hasPassword === false ? '确认设置' : '保存密码'
      }}</UiButton>
      <div class="mode-actions">
        <button
          v-if="mode === 'password'"
          type="button"
          :disabled="busy"
          @click="switchMode('email')"
        >
          忘记密码？通过邮箱验证
        </button>
        <button v-else-if="grant" type="button" :disabled="busy" @click="switchMode('email')">
          重新验证邮箱
        </button>
        <button
          v-else-if="auth.user?.hasPassword !== false"
          type="button"
          :disabled="busy"
          @click="switchMode('password')"
        >
          使用当前密码修改
        </button>
      </div>
    </form>
  </main>
</template>

<style scoped lang="scss">
@use '@/styles/settings';
.email-note {
  display: grid;
  gap: 6px;
  margin-bottom: 4px;
  padding: 16px;
  background: var(--ui-photo-background);
  border-radius: 16px;
  font-size: 0.8125rem;
  span {
    color: var(--color-text-secondary);
  }
  strong {
    overflow-wrap: anywhere;
    font-weight: 500;
  }
  .settings-hint {
    margin: 0;
  }
}
.code-field {
  display: flex;
  gap: 8px;
  .ui-input {
    min-width: 0;
    flex: 1;
  }
  button {
    flex: none;
    padding: 0 10px;
    min-height: 54px;
    color: var(--color-accent-text);
    background: var(--color-surface);
    border: 0;
    border-radius: 14px;
    font-size: 0.8125rem;
    &:disabled {
      color: var(--color-text-tertiary);
    }
  }
}
.password-note {
  label {
    display: inline-flex;
    min-height: 36px;
    align-items: center;
    gap: 8px;
    color: var(--color-text-secondary);
    font-size: 0.8125rem;
  }
  input {
    accent-color: var(--color-accent-text);
  }
  .settings-hint {
    margin-top: 4px;
  }
}
.mode-actions {
  text-align: center;
  button {
    min-height: 44px;
    padding: 8px;
    color: var(--color-accent-text);
    background: transparent;
    border: 0;
    font-size: 0.8125rem;
  }
}
.load-error {
  display: grid;
  justify-items: start;
  gap: 12px;
}
</style>
