# 原生 Android 指纹 / 生物识别登录

2026-10-23。功能代码已接入，设备硬件和真实数据库联调尚未验收。H5 登录流程保持原有行为。

## 使用流程

1. 首次通过邮箱登录，在“我的 → 设置 → 登录安全 → 指纹 / 生物识别登录”主动开启。
2. 服务端检查当前会话最近5分钟内的邮箱认证。密码登录或邮箱认证已过期时，页面提示发送并输入当前账号验证码，再完成系统验证；发送沿用现有安全验证和限流。
3. 开启成功后，普通退出清除并撤销旧会话，设备密钥单独保留。再次登录时显示脱敏邮箱，用户主动点击生物识别后取得全新会话；可切换邮箱，再返回生物识别入口。
4. 关闭需联网撤销绑定，成功后清理本机密钥，当前会话保持登录。网络失败保留凭据，可重试；也可明确只移除本机凭据，服务端记录仍保留但已删除的私钥不能再用于登录。
5. 新增生物识别、移除锁屏或密钥失效后，需要邮箱登录、关闭原凭据，再重新开启。切换账号不能静默覆盖原账号的本机凭据。

每次安装只保存一个账号的本机凭据。所有已录入且符合强度要求的生物识别都可能通过系统验证，不承诺只识别某一枚指纹；邮箱始终可以恢复登录。已有有效会话继续自动恢复，不增加每次启动验证。

## 实现与安全约束

- AndroidX Biometric 1.1.0 稳定版；`FragmentActivity` 提供验证宿主，AppCompat 主题支持旧版指纹弹窗。使用 `BIOMETRIC_STRONG`，不以弱人脸或设备 PIN 替代。
- Android Keystore 生成不可导出的 P-256 私钥。每次签名须系统认证，使用 `BiometricPrompt.CryptoObject(Signature)` 和 SHA256withECDSA；API 26–29 使用逐次认证参数，30+ 指定强生物认证。系统取消、旋转离开页面、进入后台都不会自动再次弹出验证。
- 客户端签名服务器返回的完整 UTF-8 payload；公钥为 Base64 SPKI，签名为 Base64URL DER。payload 含协议版本、受众、用途、作用域、挑战ID、32字节随机量和到期时间；注册绑定账号、公钥摘要及当前会话。
- 凭据和挑战存于 PostgreSQL。120秒挑战通过条件更新一次性消费；用户行锁串行化凭据注册/撤销、登录和密码变更，挑战消费与会话创建同事务，防止并发重放。限流使用已有数据库限流服务。
- 普通退出不撤销设备密钥；找回或修改密码增加 `tokenVersion` 后，旧版本凭据和未消费挑战都不能签发会话。按安全版本拒绝，而不是依赖客户端开关。
- 注册请求前先持久化待确认ID；响应超时不误显示已开启，也不盲目删钥。重新进入设置可查询恢复；关闭待确认绑定也会取消未完成注册挑战，阻止延迟请求重新激活。
- 本机公开的账号提示和绑定ID位于 `noBackupFilesDir`，私钥仅在 Keystore；dev/prod 安装包私有目录相互隔离，服务端记录另含 `android-dev` / `android-prod` 作用域。
- 服务端验证的是持有密钥及挑战签名。第一阶段未加入硬件证明链，不宣称服务端可独立证明某次真实指纹扫描。

官方依据：[Android 系统生物识别与 CryptoObject](https://developer.android.com/identity/sign-in/biometric-auth)、[AndroidX Biometric 发布记录](https://developer.android.com/jetpack/androidx/releases/biometric)。

## 接口与部署

相对 `/api/v1/auth/device-credentials`：

| 方法 / 路径               | 认证                               | 用途                                        |
| ------------------------- | ---------------------------------- | ------------------------------------------- |
| POST registration-options | 有效会话；近期邮箱验证或 emailCode | 上传SPKI、公钥作用域与设备名，取得注册挑战  |
| POST registration-verify  | 原会话                             | challengeId、签名创建绑定；相同挑战重试幂等 |
| POST login-options        | credentialId、scope；限流          | 返回一次性挑战；无效凭据返回同形随机挑战    |
| POST login-verify         | 挑战签名；限流                     | 一次消费后签发原格式 AuthSession            |
| GET 根路径                | 有效会话                           | 列出本账号当前安全版本的有效设备记录        |
| DELETE :id                | 有效会话且属于本账号               | 撤销设备及待确认注册挑战                    |

迁移：`apps/api/prisma/migrations/20261023030000_device_credentials/migration.sql`。新增 `device_credentials`、`device_auth_challenges` 及会话 `emailVerifiedAt`。迁移必须在部署新版API前执行；本轮未连接或修改共用数据库。

`DEVICE_CREDENTIAL_AUDIENCE` 可配置部署受众，默认 `fittrace-api-v1`。共享数据库但属于不同部署环境时应分别配置不同受众。挑战表的过期记录可按 `expiresAt` 清理；没有自动数据库定时任务。

## 验证边界

服务端测试使用真实 P-256 密钥与 Node 验签，数据库事务使用受控替身；覆盖近期邮箱证明、原会话绑定、用途/环境/账号隔离、普通退出后新会话、过期、错误签名、重复/并发消费、撤销、密码安全版本和待确认关闭。它们不是实际 PostgreSQL 并发验收。

客户端覆盖接口序列化与鉴权边界、取消清钥、超时查询恢复、退出后新会话持久化、关闭失败、密钥变化、账号隔离，以及浅色390/深色320页面交互。截图位于 `apps/android/feature/auth/build/reports/biometric-screenshots/`。

未进行手机安装、真实传感器触碰、新增指纹后的失效检查、真实邮件与数据库端到端联调。至少需要当前小米设备及旧Android分支检查，不能用本地测试替代硬件验收。

## 设计交付

先保存了[可编辑 SVG 状态稿](design/biometric-login/biometric-states.svg)，包含8个状态的浅色390与深色320版本。Figma 现有文件的新增页操作受到 Starter 页面上限拦截，改用已有页面后又达到 MCP 调用额度，因此本轮未写入 Figma 节点；该交付不能称为已同步 Figma。代码页面继续复用现有绿色主题、白卡、共享输入框、提示与确认框。

## 本轮验证结果

- API 构建、TypeScript 检查、修改文件的 ESLint 通过；全部160项服务端测试通过，其中12项为设备凭据安全回归。
- Android 完整 `verify` 与178项测试通过：network 27、data 33、auth 23、main 90、app 5，失败/错误/跳过均为0。
- dev/prod Debug、未签名 prodRelease 构建及生产 Debug/Release Lint 通过；对应应用 Lint 报告无错误或警告。格式与模块边界检查通过。
- 已查看本次生成的开启设置浅色390与失效登录深色320截图；同批包含脱敏账号登录、邮箱验证和确认交互测试。
- `apps/android/app/build/outputs/apk/prod/debug/app-prod-debug.apk`，版本0.2.0 / 3，SHA-256：`d3cfd33db92f4aa25bdfb1a829596197b44adf6d9d0ba209c2ab8d96fe18aa84`。

依赖锁已覆盖全部 dev/prod 及测试配置。首次在线下载新增依赖后，最终完整验证可离线执行。Release 产物用于验证R8链路，仍未配置发布签名；未安装或部署。


## 数据库迁移补验 · 2026-10-23

用户报告当前开发API续期时因 `auth_sessions.emailVerifiedAt` 缺失返回500。检查根目录 `.env` 指向的 `127.0.0.1:5432 / fit_trace`，确认仅缺少 `20261023030000_device_credentials`。已执行 `pnpm --filter @fit-trace/api prisma:deploy` 成功，14项迁移全部就绪。

只读回归确认 `authSession.findUnique`（包含原续期查询的关联用户字段）、`deviceCredential` 和 `deviceAuthChallenge` 均可访问。运行中的本机API使用符合格式的固定无效刷新凭据，返回预期401 / UNAUTHORIZED，未再触发缺列500。未读取真实Token、未修改业务记录、未重启用户管理的服务。真实账号续期和指纹传感器仍待客户端验证。本段迁移状态取代前文实施时“迁移未应用”的历史记录。
