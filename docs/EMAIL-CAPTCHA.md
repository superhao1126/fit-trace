# 阿里云邮箱安全验证

登录、注册、找回密码和设置密码的邮箱验证码发送接口共用阿里云验证码 2.0 的 V3 校验。验证形态由控制台场景配置决定：选择“图像复原”等交互形态时弹出验证；选择“无痕验证”时，正常用户直接通过，可疑请求再出现二次挑战。

## 控制台配置

1. 开通验证码 2.0 按量版，获取身份标 `prefix`。
2. 创建正式场景，接入方式为 `Web/H5`，用于浏览器。
3. 使用专用 RAM 程序用户的 AccessKey，授予验证码校验权限。不要使用主账号密钥，不要给密钥加 `VITE_` 前缀。
4. 将下面的配置写入本地 `.env` 或生产 `/etc/fittrace/api.env`，然后重启 API。

```dotenv
ALIYUN_CAPTCHA_ENABLED=true
ALIYUN_CAPTCHA_REGION=cn
ALIYUN_CAPTCHA_PREFIX=控制台身份标
ALIYUN_CAPTCHA_SCENE_ID=Web_H5场景ID
ALIYUN_CAPTCHA_ACCESS_KEY_ID=RAM访问密钥ID
ALIYUN_CAPTCHA_ACCESS_KEY_SECRET=RAM访问密钥Secret
```

`cn` 对应上海公网校验地址，`sgp` 对应新加坡。H5 运行时从 API 获取公开配置，无需为身份标或场景 ID 重新构建。H5 固定使用 Web/H5 场景。

开关默认关闭，适合服务未开通的开发环境。开启后，缺少必需配置会阻止 API 启动；前端不会收到 AccessKey。

服务端保留可选的 `ALIYUN_CAPTCHA_APP_SCENE_ID` 和 `app` 平台接口兼容性，H5 不再使用。后续原生 Android 的验证码接入方式在独立工程中确定。

专用 RAM 用户不启用控制台登录。按官方最小权限示例，校验使用下列自定义策略（授权操作名与业务调用的 `VerifyIntelligentCaptcha` 名称不同）；本项目已用真实接口确认该权限可调用校验接口：

```json
{
  "Version": "1",
  "Statement": [{ "Effect": "Allow", "Action": "yundun-afs:VerifyCaptcha", "Resource": "*" }]
}
```

参考：[RAM 验证码权限说明](https://help.aliyun.com/zh/captcha/captcha2-0/user-guide/authorize-a-ram-user-to-access-alibaba-cloud-captcha)。勿授予验证码全量管理权限。修改 `.env` 后需重启 API，开发模式下仅修改环境文件不会自动重新读取配置。

## 请求流程与失败行为

`GET /api/v1/auth/email-captcha/config` 返回公开配置。页面进入时，前端动态加载官方 SDK 并预初始化验证码；初始化超过 2 秒且实例就绪后，点击获取验证码直接唤起验证。过早点击只等待剩余准备时间，验证结束后预初始化下一次；页面退出会清理实例，长时间停留后会更新挑战。通过验证后，将原始 `captchaVerifyParam` 和 `captchaPlatform: "web"`提交给发邮件接口：

- `POST /api/v1/auth/email-code`：`{ email, purpose, captchaVerifyParam, captchaPlatform }`。
- `POST /api/v1/auth/password/email-code`：`{ captchaVerifyParam, captchaPlatform }`，邮箱由当前登录账号确定。

API 指定对应场景并调用 `VerifyIntelligentCaptcha`，只有 `Success` 和 `VerifyResult` 都通过才进入邮件发送事务。校验请求在数据库事务外执行；云端凭据只能使用一次，过期或重放必须重新验证。

用户关闭弹窗不发邮件、不开始倒计时。验证码加载异常或二次校验超时提示重试，邮件发送额度保持不变。供应商接口异常时选择拦截，避免邮件接口被绕过；这与阿里云文档建议的异常放行策略不同，是本项目针对邮件发送的选择。现有发送限流和 SMTP 失败退还额度继续生效，校验接口另有每 IP 每分钟 20 次请求限制。

## 费用与验收

按量版免费开通，基础校验按调用计费。前三个场景不收场景扩展费，建议保持默认策略；付费自定义策略开关应保持关闭。实际费用以控制台账单为准。

自动化检查覆盖必需凭据、服务器场景选择、过期/重放拒绝、供应商异常、所有邮件发送入口以及前端取消和清理。自动化测试中的供应商响应为模拟结果，不能代替真实阿里云验证。上线前由使用者检查一次浏览器中的真实邮箱发送与安全验证流程。

官方文档：[接入指引](https://help.aliyun.com/zh/captcha/captcha2-0/user-guide/quick-start)、[V3 客户端](https://help.aliyun.com/zh/captcha/captcha2-0/user-guide/new-architecture-for-web-and-h5-client-access)、[服务端](https://help.aliyun.com/zh/captcha/captcha2-0/user-guide/server-access)、[计费说明](https://help.aliyun.com/zh/captcha/captcha2-0/billing)。
