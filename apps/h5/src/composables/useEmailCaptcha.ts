import { onBeforeUnmount, onMounted } from 'vue';
import { getEmailCaptchaConfig } from '@/api/auth';
import { EmailCaptchaClient } from '@/utils/email-captcha';

export function useEmailCaptcha(): EmailCaptchaClient {
  const captcha = new EmailCaptchaClient(getEmailCaptchaConfig);
  onMounted(() => {
    // 前置加载 SDK 并初始化挑战，把环境采集和资源准备放在用户点击之前。
    void captcha.prepare().catch(() => undefined);
  });
  onBeforeUnmount(() => captcha.dispose());
  return captcha;
}
