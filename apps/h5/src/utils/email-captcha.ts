import type { EmailCaptchaConfig, EmailCaptchaProof } from '@fit-trace/shared';

interface CaptchaInstance {
  hide?: () => void | Promise<void>;
}
interface PreparedCaptcha {
  initializedAt: number;
  start: () => Promise<EmailCaptchaProof | null>;
  cancel: () => Promise<void>;
}
interface CaptchaOptions {
  SceneId: string;
  mode: 'popup';
  element: string;
  button: string;
  language: 'cn';
  rem: number;
  success: (proof: string) => void;
  getInstance: (instance: CaptchaInstance) => void;
  onError: () => void;
  onClose: (reason: string) => void;
}
declare global {
  interface Window {
    AliyunCaptchaConfig?: { region: 'cn' | 'sgp'; prefix: string };
    initAliyunCaptcha?: (options: CaptchaOptions) => void;
  }
}

const scriptUrl = 'https://o.alicdn.com/captcha-frontend/aliyunCaptcha/AliyunCaptcha.js';
let scriptLoading: Promise<void> | null = null;
let nextCaptchaId = 0;

function loadScript(config: Extract<EmailCaptchaConfig, { enabled: true }>): Promise<void> {
  window.AliyunCaptchaConfig = { region: config.region, prefix: config.prefix };
  if (window.initAliyunCaptcha) return Promise.resolve();
  if (scriptLoading) return scriptLoading;
  scriptLoading = new Promise<void>((resolve, reject) => {
    const script = document.createElement('script');
    let settled = false;
    const timer = window.setTimeout(failed, 10000);
    function failed(): void {
      if (settled) return;
      settled = true;
      window.clearTimeout(timer);
      script.remove();
      scriptLoading = null;
      reject(new Error('安全验证加载失败，请检查网络后重试'));
    }
    script.src = scriptUrl;
    script.async = true;
    script.onerror = failed;
    script.onload = () => {
      if (settled) return;
      if (!window.initAliyunCaptcha) return failed();
      settled = true;
      window.clearTimeout(timer);
      resolve();
    };
    document.head.append(script);
  });
  return scriptLoading;
}

/** 一次调用对应一次验证；凭据直接交给发邮件接口，不在客户端缓存。 */
export class EmailCaptchaClient {
  private readonly getConfig: () => Promise<EmailCaptchaConfig>;
  private preparing: Promise<EmailCaptchaConfig> | null = null;
  private disposed = false;
  private cancel: PreparedCaptcha['cancel'] | null = null;
  private prepared: PreparedCaptcha | null = null;

  constructor(getConfig: () => Promise<EmailCaptchaConfig>) {
    this.getConfig = getConfig;
  }

  prepare(): Promise<EmailCaptchaConfig> {
    if (!this.preparing) {
      this.preparing = this.getConfig()
        .then(async (config) => {
          if (config.enabled && !this.disposed) await loadScript(config);
          return config;
        })
        .catch((error: unknown) => {
          this.preparing = null;
          throw error;
        });
    }
    return this.preparing.then((config) => {
      if (config.enabled && !this.disposed && !this.prepared) {
        const sceneId = config.sceneId;
        if (!sceneId) throw new Error('安全验证暂不可用，请稍后重试');
        this.prepared = this.initialize(sceneId);
      }
      return config;
    });
  }

  async verify(): Promise<EmailCaptchaProof | undefined | null> {
    if (this.disposed) return null;
    const config = await this.prepare();
    if (this.disposed) return null;
    if (!config.enabled) return undefined;
    // 长时间停留页面后重新准备，避免使用过旧的云端挑战。
    if (this.prepared && Date.now() - this.prepared.initializedAt >= 600000) {
      await this.prepared.cancel();
      this.prepared = null;
      await this.prepare();
    }
    if (this.disposed || !this.prepared) return null;
    await this.cancel?.();
    if (this.disposed || !this.prepared) return null;
    const challenge = this.prepared;
    this.prepared = null;
    this.cancel = challenge.cancel;
    try {
      return await challenge.start();
    } finally {
      if (this.cancel === challenge.cancel) this.cancel = null;
      // 验证结束后提前初始化下一次，不重复使用已经消费的凭据。
      void this.prepare().catch(() => undefined);
    }
  }

  private initialize(sceneId: string): PreparedCaptcha {
    let start!: PreparedCaptcha['start'];
    let cancel!: () => void;
    const initializedAt = Date.now();
    const result = new Promise<EmailCaptchaProof | null>((resolve, reject) => {
      // 这里只需唯一 DOM ID；兼容没有 randomUUID 的 HTTP 页面。
      const id = `email-captcha-${Date.now().toString(36)}-${++nextCaptchaId}`;
      const host = document.createElement('div');
      const trigger = document.createElement('button');
      host.id = id;
      trigger.id = `${id}-trigger`;
      trigger.type = 'button';
      trigger.hidden = true;
      document.body.append(host, trigger);
      let settled = false;
      let instance: CaptchaInstance | undefined;
      let startTimer: number | undefined;
      let verificationTimer: number | undefined;
      let requested = false;
      let activated = false;
      let closed = false;
      const readyTimer = window.setTimeout(
        () => finish(new Error('安全验证加载超时，请重试')),
        10000,
      );
      const finish = (result: EmailCaptchaProof | Error | null): void => {
        if (settled) return;
        settled = true;
        window.clearTimeout(readyTimer);
        window.clearTimeout(startTimer);
        window.clearTimeout(verificationTimer);
        const complete = (): void => {
          host.remove();
          trigger.remove();
          if (result instanceof Error) reject(result);
          else resolve(result);
        };
        // SDK 的 hide 包含异步动画且共用配置；完成后才能初始化下一次。
        try {
          const hiding = closed ? undefined : instance?.hide?.();
          if (hiding) {
            void hiding.then(complete, complete);
            return;
          }
        } catch {
          /* 清理失败不应阻止结果返回。 */
        }
        complete();
      };
      const activate = (): void => {
        if (settled || !requested || !instance || activated) return;
        activated = true;
        const show = (): void => {
          if (settled) return;
          try {
            trigger.click();
          } catch {
            finish(new Error('安全验证加载失败，请稍后重试'));
          }
        };
        // 等待发生在页面预初始化阶段；准备充分后点击可直接唤起。
        const remaining = 2100 - (Date.now() - initializedAt);
        if (remaining > 0) startTimer = window.setTimeout(show, remaining);
        else show();
      };
      cancel = () => finish(null);
      start = () => {
        if (!settled && !requested) {
          requested = true;
          verificationTimer = window.setTimeout(
            () => finish(new Error('安全验证已超时，请重试')),
            120000,
          );
          activate();
        }
        return result;
      };
      try {
        window.initAliyunCaptcha!({
          SceneId: sceneId,
          mode: 'popup',
          element: `#${host.id}`,
          button: `#${trigger.id}`,
          language: 'cn',
          rem: Math.min(1, Math.max(0.5, (window.innerWidth - 32) / 360)),
          success: (captchaVerifyParam) => {
            if (typeof captchaVerifyParam !== 'string' || !captchaVerifyParam.trim()) {
              finish(new Error('安全验证未完成，请重新验证'));
              return;
            }
            finish({ captchaVerifyParam, captchaPlatform: 'web' });
          },
          getInstance: (value) => {
            if (settled) {
              try {
                value.hide?.();
              } catch {
                /* 忽略已取消实例的清理异常。 */
              }
              return;
            }
            instance = value;
            window.clearTimeout(readyTimer);
            activate();
          },
          onError: () => finish(new Error('安全验证加载失败，请稍后重试')),
          onClose: (reason) => {
            // 只处理用户已唤起的弹窗，忽略预初始化阶段的关闭通知。
            if (!requested || settled) return;
            closed = true;
            if (reason === 'userDismiss') finish(null);
          },
        });
      } catch {
        finish(new Error('安全验证加载失败，请稍后重试'));
      }
    });
    // 预加载失败时尚无业务请求等待；点击后仍会收到原始错误。
    void result.catch(() => undefined);
    return {
      initializedAt,
      start: () => start(),
      cancel: () => {
        cancel();
        return result.then(
          () => undefined,
          () => undefined,
        );
      },
    };
  }

  dispose(): void {
    this.disposed = true;
    void this.prepared?.cancel();
    this.prepared = null;
    void this.cancel?.();
  }
}
