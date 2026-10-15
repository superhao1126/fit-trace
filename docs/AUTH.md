# 登录、注册与邮箱验证码

## QQ 邮箱配置

在 QQ 邮箱设置中开启 SMTP 服务，取得 SMTP 授权码，在项目根目录 `.env` 填写：

```dotenv
SMTP_HOST=smtp.qq.com
SMTP_PORT=465
SMTP_SECURE=true
SMTP_USER=你的QQ邮箱@qq.com
SMTP_PASS=QQ邮箱SMTP授权码
SMTP_FROM=FitTrace <你的QQ邮箱@qq.com>
```

`SMTP_PASS` 使用 SMTP 授权码。配置文件不要提交 Git。修改后重启 API。没有配置时接口返回 `MAIL_NOT_CONFIGURED`，前端显示提示；发送失败返回 `MAIL_SEND_FAILED`，不会返回或打印验证码。

## 数据库与启动

本次新增邮箱验证码、限流计数、图形验证码表，以及 `users.tokenVersion`。确认根目录 `.env` 的 `DATABASE_URL` 指向要升级的数据库，再执行：

```sh
pnpm --filter @fit-trace/api prisma:deploy
pnpm --filter @fit-trace/api prisma:generate
pnpm dev
```

应先升级数据库再启动新版 API；未升级时新的登录与受保护接口无法正常查询。迁移不修改既有密码；旧 JWT 按版本 0 兼容。重置密码会递增版本，使该用户之前的 JWT 全部失效，要求重新登录。

若 API 在反向代理后面，设置 `TRUST_PROXY` 为实际可信代理 IP/CIDR，多个以逗号分隔。不配置时使用直接连接 IP，不信任外部提交的转发头。

## App 持久登录（2026-10-09）

`JWT_EXPIRES_IN_SECONDS` 明确按正整数秒解析，默认 `900`（15 分钟）。旧实现将 `.env` 的 `604800` 字符串传给 JWT 库，被按毫秒解析，实际只有 604 秒。配置 `604800` 现在会正确得到 7 天，但推荐保持 900 秒，通过长期会话自动续期。

登录和注册返回 `{accessToken,accessTokenExpiresAt,refreshToken,user}`。每次登录生成独立的 256 位随机设备凭证，数据库 `auth_sessions` 只保存 SHA-256 摘要、账号版本、使用时间和撤销状态。设备会话没有固定到期时间；续期凭证保持稳定，避免并发续期或响应丢失导致本机掉线。访问令牌始终短期有效，并绑定设备会话 ID。

| 方法与路径           | 请求与行为                                                                                      |
| -------------------- | ----------------------------------------------------------------------------------------------- |
| POST `/auth/refresh` | `{refreshToken}`，校验设备会话和账号版本，返回最新访问令牌与用户资料                            |
| POST `/auth/logout`  | `{refreshToken}`，幂等撤销当前设备会话，无需有效的访问令牌                                      |
| POST `/auth/session` | 携带仍有效且没有设备会话 ID 的旧 Bearer JWT，无感升级旧版登录状态；新版访问令牌不能换取长期凭证 |

App/WebView 在 `fit-trace:session` 中持久保存凭证和上次账号资料，不保存密码。启动、恢复到前台、恢复网络时检查会话；访问令牌剩余不足 30 秒时先续期，受保护请求遇到 401 时最多续期并重试一次。同一账号并发请求共用续期任务；退出或更换账号后，旧请求不能恢复旧账号。重新打开时先恢复会话，再进入目标页面。

网络错误、超时、服务异常保留本机登录，只有会话明确失效才返回登录页。主动退出立即清除本机账号；离线退出暂存待撤销凭证，恢复网络或下次启动补发撤销。服务端收到撤销后，当前设备的访问令牌和续期凭证都失效；其他设备会话不受影响。重置密码递增账号版本，使所有旧设备会话同时失效。

升级需先执行 `prisma:deploy` 和 `prisma:generate`，再重启 API。仍有效的旧 JWT 可以自动升级；已经过期的旧 JWT 需要首次重新登录。清除应用数据、卸载重装或清除浏览器存储也会清除本机登录。生产环境应使用 HTTPS 保护登录与续期凭证的传输。

## 接口

所有路径以 `/api/v1` 开头，继续返回统一 `{code,message,data,meta}`。

| 方法与路径                              | 请求与行为                                                                                                       |
| --------------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| POST `/auth/email-code`                 | `{email,purpose}`，purpose 为 `REGISTER` 或 `RESET_PASSWORD`；返回 `{retryAfterSeconds:60,expiresInSeconds:600}` |
| POST `/auth/register`                   | `{email,password,emailCode}`；验证邮箱后创建账号并登录；兼容可选 nickname                                        |
| POST `/auth/login`                      | `{email,password}`；需要图形验证时追加 `{captchaId,captchaCode}`                                                 |
| GET `/auth/captcha`                     | 返回 `{id,image,expiresInSeconds:120}`；image 是 SVG data URL                                                    |
| POST `/auth/reset-password/verify-code` | `{email,emailCode}`；返回 `{resetToken,expiresInSeconds:300}`，随后进入设置密码                                  |
| POST `/auth/reset-password`             | `{email,password,resetToken}`；成功 data 为 null，回登录页重新登录                                               |

密码 8–72 个字符。邮箱由后端去除首尾空格并转小写。验证码只保存 HMAC 摘要，按邮箱和用途隔离，6 位、10 分钟有效、一次使用，错误 5 次锁定。验证码消费和账号写入共用数据库事务，业务失败会回滚消费。重新发送会使上一验证码失效。

发送接口对已注册邮箱的注册请求、未注册邮箱的重置请求返回相同结果且不发信，以减少账号枚举；因此前端提示“若邮箱可用，验证码将发送至你的邮箱”。

限流采用数据库固定窗口计数：

- 邮件：同邮箱同用途 1 次/分钟，同邮箱 5 次/小时，同 IP 10 次/小时。
- 登录：同邮箱 20 次/15 分钟，同 IP 40 次/15 分钟；邮箱或 IP 在 15 分钟内失败 3 次后必须通过图形验证码。
- 图形验证码：同 IP 20 次/分钟，绑定 IP，2 分钟有效，一次尝试后消费。
- 注册：同 IP 20 次/小时。重置：同 IP 20 次/小时、同邮箱 10 次/15 分钟。

超过阈值返回 HTTP 429、`AUTH_RATE_LIMITED`。图形验证码所需状态返回 HTTP 403、`LOGIN_CAPTCHA_REQUIRED`，错误或失效返回 `CAPTCHA_INVALID`；登录页面自动展开或刷新验证区域。成功登录或重置清除失败次数，不清除请求频率计数。

## 验证范围

```sh
pnpm check
pnpm build
node --test apps/api/test/auth.test.mjs apps/api/test/auth-session.test.mjs apps/api/test/dashboard.test.mjs apps/api/test/response.test.mjs apps/h5/test/auth-session.test.mjs apps/h5/test/response-handler.test.mjs
```

认证服务测试使用数据库和邮件替身，覆盖验证码用途隔离、到期、重复使用、错误次数、事务回滚、发信失败清理、图形验证码 IP 绑定、JWT 版本、登录失败阈值和密码重置。接口测试挂载真实控制器并替换服务，验证统一返回格式。替身测试不能证明 PostgreSQL 的实际并发锁、SMTP 投递或真机体验。

上线前用已配置的 QQ 邮箱和迁移后的开发库完成真实收信、注册、重置、旧会话失效、重复验证码以及并发提交验证。手机系统键盘、密码管理器与实际收信仍需真机验收。

## 找回密码分步流程（2026-10-08）

第一步只填写邮箱和 6 位邮件验证码。后端验证成功后，将已消费验证码的记录转为 5 分钟有效的重置凭证，前端进入第二步，显示已验证邮箱、新密码和确认密码。重置凭证只保存在页面内存，刷新、返回验证或更换邮箱后重新验证。

凭证使用 UUID 随机值，数据库仅存 HMAC 摘要；其摘要前缀与邮件验证码隔离，绑定邮箱，只能成功使用一次。消费凭证和更新密码共用加行锁的数据库事务；写入失败回滚，重新发送邮件会使先前凭证失效。重置接口不接受旧的 `emailCode` 参数，未验证不能直接提交新密码；失效返回 `RESET_VERIFICATION_INVALID`，前端退回邮箱验证步骤。

邮箱验证接口额外限制同 IP 30 次/15 分钟、同邮箱 10 次/15 分钟。本次复用现有认证表，不需要新增迁移。页面的确认密码用于一致性校验，后端仍独立校验密码格式和重置凭证。

## 账号资料与修改密码（2026-10-09）

「我的」右上角进入设置，账号设置支持修改昵称、性别和头像。头像复用现有图片上传流程，按上传日期和用户保存；个人资料保存成功后同步更新本机登录缓存，重新打开仍能回显。

| 方法与路径                | 请求与行为                                                                                                                                     |
| ------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| PATCH `/users/me/profile` | Bearer JWT；可选 `{nickname,gender,avatarUrl}`，返回完整公开用户资料。昵称去除首尾空格后 1–40 个字符，性别为 `UNSPECIFIED`、`MALE` 或 `FEMALE` |
| PATCH `/auth/password`    | Bearer JWT；`{currentPassword,password}`，密码 8–72 个字符，成功 data 为 null，当前设备及其他设备需重新登录                                    |

头像地址仅接受 HTTP(S) URL，传 `null` 可移除头像。用户 ID 由登录令牌确定，接口不接受客户端指定账号。资料接口只允许修改昵称、性别和头像，不修改邮箱、目标或密码。

新增迁移 `20261009080000_user_gender`，已有账号性别默认为 `UNSPECIFIED`。部署到其他环境时，先执行上文的 `prisma:deploy` 和 `prisma:generate`，再构建并重启 API；H5 页面需重新构建并发布。

修改密码限制每个账号 15 分钟内 10 次。当前密码错误返回 HTTP 400、`CURRENT_PASSWORD_INCORRECT`，保留本机登录；新密码与当前密码相同返回 `PASSWORD_UNCHANGED`。后端使用旧密码摘要作为更新条件，防止并发请求覆盖已变更的密码，并递增 `tokenVersion`，使旧访问令牌及设备续期凭证失效。

资料与密码回归测试见 `apps/api/test/profile.test.mjs`，前端缓存和密码接口续期测试见 `apps/h5/test/auth-session.test.mjs`。本轮通过真实 PostgreSQL 临时账号验证资料持久化、密码摘要和旧设备会话失效，完成后已删除临时账号及其会话；手机相册选择和系统键盘仍需真机验收。
