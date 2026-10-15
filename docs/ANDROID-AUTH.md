# 原生 Android 登录（2026-10-15）

## 页面与行为

沿用已确认的 Figma「循形 · 统一设计系统与核心页面」登录稿和仓库归档 `fittrace-auth-refined-2026-10-10.zip`，用 Jetpack Compose 实现业务界面。默认邮箱验证码登录，支持密码登录、邮箱验证后设置新密码、用户协议和隐私政策原生长文弹窗、深浅色及窄屏滚动。协议默认不勾选；关闭协议不代表同意。验证码登录时服务端可自动注册新邮箱。

密码、验证码及第三方验证凭据只存在于内存。切换登录方式清理敏感输入；旋转屏幕保留当前流程，登录成功后销毁认证 ViewModel。进程被系统回收后重新输入凭据。验证码冷却按邮箱及用途区分，并处理服务端 Retry-After；第三方验证取消或失败不会发送邮件。

预览来自 Robolectric 本地绘制，不代表真机截图：

- [浅色邮箱登录](android-auth-email-2026-10-15.png)
- [深色邮箱登录](android-auth-email-dark-2026-10-15.png)

## 模块与接口

- `feature:auth`：原生页面、表单校验、认证 ViewModel、人机验证交互。
- `core:domain` / `core:model`：认证仓库、会话存储契约和领域状态。
- `core:network`：序列化 DTO、Retrofit API；`core:data`：错误映射、持久化协调与刷新互斥。
- `core:session`：Android Keystore AES-GCM 加密，AtomicFile 写入 noBackupFilesDir；不保存明文令牌或表单凭据。
- `app`：依赖装配、启动/回到前台时恢复与校验会话、认证与登录后入口切换；登录后的业务首页尚未迁移。

| 接口 | 用途 |
| --- | --- |
| `GET auth/email-captcha/config` | 读取第三方验证开关和 App 场景 |
| `POST auth/email-code` | 发送 LOGIN / RESET_PASSWORD 邮箱码，显式携带 captchaPlatform=app |
| `POST auth/login/email` | 邮箱码登录，提交 acceptedTerms |
| `POST auth/login` / `GET auth/captcha` | 密码登录及风险图形验证 |
| `POST auth/reset-password/verify-code` / `POST auth/reset-password` | 获取短期重置凭据并更新密码 |
| `GET auth/me` / `POST auth/refresh` / `POST auth/logout` | 校验、轮换与撤销会话 |

成功同时检查 HTTP 状态、业务 code 和数据结构。会话写入成功后才进入登录后页面。过期令牌串行刷新，临时网络故障保留刷新凭据并提供重试，明确失效则清理；退出先清理本地，断网不阻止退出，服务端撤销仍取决于网络。磁盘清理失败会提示恢复，不伪装退出成功。

## 阿里云安全验证

业务登录与找回页面均为原生 Compose；独立 WebView 仅运行阿里云官方验证 SDK，返回一次性验证凭据，由现有 API 校验后发邮件。WebView 不接收登录会话，禁止文件/内容访问及顶层页面跳转；关闭时销毁。密码风控 SVG 验证码的独立视图禁用 JavaScript 和网络访问。

阿里云控制台的 **App 场景须为交互式验证**（滑块、拼图等）。官方 FAQ 的 Q13 说明，独立验证 WebView 不能使用无痕场景；客户端使用真实“开始安全验证”按钮。若线上 App 场景配置不同，需要在控制台调整并真机联调，不能在客户端绕过安全验证。参考 [阿里云客户端接入 FAQ](https://help.aliyun.com/zh/captcha/captcha2-0/user-guide/captcha-2-0-client-access-faq) 及 [新版 Web/H5 接入](https://help.aliyun.com/zh/captcha/captcha2-0/user-guide/new-architecture-for-web-and-h5-client-access)。

## 构建和验证

当前原生版本 `0.1.1 / versionCode 2`。完整验证命令：

```bash
cd apps/android
./gradlew verify :app:assembleProdDebug :app:assembleProdRelease :app:lintProdRelease
```

50 项本地测试通过：网络契约 7、仓库 20、认证 ViewModel 13、Compose 页面 5、应用生命周期 5。包括邮箱码 app 场景字段、错误和限流、续期互斥、断网恢复、存储失败、保存期间取消、旋转状态所属关系、登录后敏感状态销毁、协议默认未勾选、320dp 和深色布局。Compose 截图为 Robolectric SDK 35 的本地渲染；没有运行设备仪器测试。

格式、模块边界、Lint、devDebug / prodDebug / prodRelease 构建通过。Release 未配置生产签名。未修改 H5 或 API，未部署，也未发送真实验证码邮件。

真机联调正式接口请选择 Android Studio **Build Variants → app → prodDebug**，或安装 `apps/android/app/build/outputs/apk/prod/debug/app-prod-debug.apk`。包名 `com.fittrace.android.debug`；devDebug 的包名不同，两个变体安装为不同应用，数据不互通。devDebug 使用 `http://10.0.3.54:3000/api/v1/`，无需 adb reverse。手机需能访问电脑局域网，后端须监听 `0.0.0.0:3000` 或该局域网地址，且防火墙允许访问。

## 真机验收清单

1. 勾选协议前不能发登录验证码或登录；协议与隐私弹窗可独立查看。
2. 真实 App 场景验证成功后收到邮件；取消、失败、超时不发邮件；倒计时和再次发送正常。
3. 已有邮箱码登录、新邮箱首次登录自动注册、密码登录及错误提示正常；按服务端风控要求显示并刷新图形验证码。
4. 找回密码：邮箱码验证、两次新密码一致性、过期处理、新密码登录及旧密码失效。
5. 旋转屏幕、键盘弹出、320dp/大字体、深浅色、返回键不会遮挡主要操作。
6. 登录后强制关闭重开仍恢复；到期后续期，断网重试；退出后重开不恢复；另一账号登录时协议重新勾选。
7. 本机 Android Keystore 的保存、读取及系统进程重建实际行为；设备/WebView 上第三方 SDK 和真实账号接口链路。

用户已反馈局域网开发接口调通；未逐项记录完整验收清单。代理本轮未操作真机，真实邮件、安全验证、设备存储和交互的其余项目仍由用户联调确认。
