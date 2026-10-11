<script setup lang="ts">
import UiTabs from '@/components/ui/UiTabs.vue';
import UiInput from '@/components/ui/UiInput.vue';
import UiIcon from '@/components/ui/UiIcon.vue';
import UiButton from '@/components/ui/UiButton.vue';
import type { UserGender } from '@fit-trace/shared';
import { computed, onBeforeUnmount, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ToastPlugin } from 'tdesign-mobile-vue';
import UiDetailHeader from '@/components/ui/UiDetailHeader.vue';
import UserAvatar from '@/components/UserAvatar.vue';
import { uploadImage } from '@/api/uploads';
import { updateProfile } from '@/api/users';
import { authSession } from '@/api/session';
import { useAuthStore } from '@/stores/auth';
import { showRequestError } from '@/utils/request-error';

const auth = useAuthStore();
const router = useRouter();
const nickname = ref(auth.user?.nickname ?? auth.user?.email.split('@')[0] ?? '循形用户');
const gender = ref<UserGender>(auth.user?.gender ?? 'UNSPECIFIED');
const avatarUrl = ref(auth.user?.avatarUrl ?? null);
const avatarFile = ref<File | null>(null);
const previewUrl = ref<string | null>(null);
const imageInput = ref<HTMLInputElement | null>(null);
const saving = ref(false);
const nicknameError = ref('');
let active = true;
const genderOptions: Array<{ value: UserGender; label: string }> = [
  { value: 'UNSPECIFIED', label: '不设置' },
  { value: 'MALE', label: '男' },
  { value: 'FEMALE', label: '女' },
];
const preview = computed(() => previewUrl.value ?? avatarUrl.value);

function clearPreview(): void {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value);
  previewUrl.value = null;
}
function selectAvatar(event: Event): void {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) return;
  if (
    !['image/jpeg', 'image/png', 'image/webp', 'image/heic', 'image/heif'].includes(
      file.type.toLowerCase(),
    )
  ) {
    ToastPlugin.warning('请选择 JPG、PNG 或 WebP 图片');
    return;
  }
  clearPreview();
  avatarFile.value = file;
  previewUrl.value = URL.createObjectURL(file);
}
function removeAvatar(): void {
  clearPreview();
  avatarFile.value = null;
  avatarUrl.value = null;
}
async function save(): Promise<void> {
  if (saving.value) return;
  const name = nickname.value.trim();
  nicknameError.value = !name
    ? '请输入昵称'
    : Array.from(name).length > 40
      ? '昵称不能超过 40 个字符'
      : '';
  if (nicknameError.value) return;
  saving.value = true;
  const generation = authSession.generation;
  try {
    if (avatarFile.value) {
      avatarUrl.value = (await uploadImage(avatarFile.value)).url;
      avatarFile.value = null;
    }
    if (generation !== authSession.generation || !active) return;
    const user = await updateProfile({
      nickname: name,
      gender: gender.value,
      avatarUrl: avatarUrl.value,
    });
    if (generation !== authSession.generation) return;
    auth.updateUser(user);
    if (!active) return;
    ToastPlugin.success('个人资料已保存');
    await router.back();
  } catch (error) {
    showRequestError(error, '保存失败，请稍后重试');
  } finally {
    saving.value = false;
  }
}
onBeforeUnmount(() => {
  active = false;
  clearPreview();
});
</script>

<template>
  <main class="view-page ui-page account-settings-page">
    <UiDetailHeader title="账号设置" />
    <form class="settings-form" novalidate @submit.prevent="save">
      <section class="settings-section" aria-labelledby="profile-title">
        <h2 id="profile-title">个人资料</h2>
        <div class="account-fields">
          <div class="avatar-field">
            <button
              type="button"
              class="avatar-picker"
              aria-label="更换头像"
              :disabled="saving"
              @click="imageInput?.click()"
            >
              <UserAvatar :url="preview" :name="nickname || '循形用户'" :size="76" />
              <span class="avatar-picker__camera"><UiIcon name="photo" /></span>
            </button>
            <div>
              <strong>头像</strong
              ><button
                type="button"
                class="text-action"
                :disabled="saving"
                @click="imageInput?.click()"
              >
                选择图片
              </button>
            </div>
            <button
              v-if="preview"
              type="button"
              class="remove-avatar"
              :disabled="saving"
              @click="removeAvatar"
            >
              移除
            </button>
            <input
              ref="imageInput"
              class="visually-hidden"
              type="file"
              accept="image/jpeg,image/png,image/webp,image/heic,image/heif"
              :disabled="saving"
              @change="selectAvatar"
            />
          </div>
          <div class="form-field">
            <label for="profile-nickname" class="form-label">昵称</label>
            <UiInput
              id="profile-nickname"
              v-model="nickname"
              aria-label="昵称"
              class="field-input"
              autocomplete="nickname"
              maxlength="80"
              placeholder="给自己起个名字"
              :readonly="saving"
              :aria-invalid="Boolean(nicknameError)"
              aria-describedby="nickname-error"
              @input="nicknameError = ''"
            />
            <small v-if="nicknameError" id="nickname-error" class="field-error" role="alert">{{
              nicknameError
            }}</small>
          </div>
          <div class="gender-field">
            <span class="field-label">性别 · 选填</span
            ><UiTabs
              :model-value="gender"
              :options="genderOptions"
              label="性别"
              :disabled="saving"
              @update:model-value="gender = $event as UserGender"
            />
          </div>
        </div>
        <p class="settings-hint">性别可选，不影响记录和使用</p>
      </section>
      <UiButton type="submit" block :loading="saving" :disabled="saving">{{
        saving ? '正在保存' : '保存资料'
      }}</UiButton>
      <section class="settings-section" aria-labelledby="security-title">
        <h2 id="security-title">登录安全</h2>
        <div class="settings-list">
          <div class="settings-row">
            <span>登录邮箱</span><span class="settings-row__value">{{ auth.user?.email }}</span>
          </div>
          <RouterLink
            to="/settings/password"
            class="settings-row"
            :aria-disabled="saving"
            @click="saving && $event.preventDefault()"
            ><UiIcon name="lock" /><span class="settings-row__body">{{
              auth.user?.hasPassword === false
                ? '设置密码'
                : auth.user?.hasPassword === true
                  ? '修改密码'
                  : '设置或修改密码'
            }}</span
            ><span v-if="typeof auth.user?.hasPassword === 'boolean'" class="settings-row__value">{{
              auth.user.hasPassword ? '已设置' : '未设置'
            }}</span
            ><UiIcon name="right" class="settings-row__chevron"
          /></RouterLink>
        </div>
      </section>
    </form>
  </main>
</template>

<style scoped lang="scss">
@use '@/styles/settings';
.avatar-field {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 20px 16px;
  margin-bottom: 28px;
  background: var(--color-surface);
  border-radius: 20px;
  > div {
    display: grid;
    gap: 8px;
    strong {
      font-size: 0.8125rem;
      font-weight: 500;
    }
  }
}
.avatar-picker {
  position: relative;
  flex: none;
  padding: 0;
  background: transparent;
  border: 0;
  &__camera {
    position: absolute;
    right: -1px;
    bottom: -1px;
    display: grid;
    width: 25px;
    height: 25px;
    place-items: center;
    color: var(--color-on-brand);
    background: var(--color-brand);
    border: 2px solid var(--color-surface);
    border-radius: 50%;
    font-size: 14px;
  }
}
.text-action,
.remove-avatar {
  padding: 5px 0;
  color: var(--color-accent-text);
  background: transparent;
  border: 0;
  font-size: 0.8125rem;
  text-align: left;
  cursor: pointer;
}
.remove-avatar {
  margin-left: auto;
  color: var(--color-text-tertiary);
}
.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
}
.gender-field {
  .field-label {
    display: block;
    margin-bottom: 12px;
    color: var(--ui-text-secondary);
    font-size: 13px;
  }
  :deep(.ui-tabs) {
    margin-bottom: 0;
  }
}
.avatar-picker__camera :deep(.ui-icon) {
  width: 14px;
  height: 14px;
}
</style>
