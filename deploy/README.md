# FitTrace 生产部署

## 服务器布局

- 服务器：`ubuntu@101.43.38.7`。
- H5：`https://fitness.rungos.cn`，Nginx 提供静态资源并支持 History 路由回退。
- API：`https://fitness.rungos.cn/api/v1`，转发至 `127.0.0.1:3100`。
- 数据库：沿用服务器现有 PostgreSQL `fit_trace`，与当前开发环境共用。
- 运行目录：`/opt/fittrace/current` 指向 `/opt/fittrace/releases/<发布标识>`。
- 生产配置：`/etc/fittrace/api.env`，权限 `root:fittrace 640`；发布包不包含密钥。
- 图片：`/var/lib/fittrace/uploads`，与代码发布目录分开，更新代码不会删除图片。
- API 服务：`fittrace-api.service`，使用独立 `fittrace` 用户与 Node 24，开机启动。
- 正式域名为 `fitness.rungos.cn`，HTTP 重定向到 HTTPS；源站需配置该域名的有效证书。

`nginx-fittrace.conf` 为本项目配置域名 HTTPS 站点，证书路径为 `/etc/letsencrypt/live/fitness.rungos.cn/`；部署前确认证书已签发。
生产 `STORAGE_PUBLIC_URL` 与 `CORS_ORIGINS` 使用 `https://fitness.rungos.cn`；已有记录中的绝对图片 URL 不会随环境变量自动改变；如需迁移，应先备份数据库再按完整 `/uploads/` 前缀替换。
API 的 systemd 内存上限为 512 MB，V8 老生代上限为 256 MB，避免影响同机其他服务。

## 构建 H5

在本机使用生产 API 地址构建。以下命令保留运行时 GlitchTip 错误上报，跳过源码映射生成与上传：

```bash
VITE_API_BASE_URL=https://fitness.rungos.cn/api/v1 \
GLITCHTIP_UPLOAD_SOURCEMAPS=false pnpm build:h5
```

产物为 `apps/h5/dist`。H5 的构建与发布不再需要 JDK、Android SDK 或 APK。

## API 发布包

服务器资源有限，依赖安装、Prisma 生成和 TypeScript 编译在本机完成。
使用锁定依赖生成独立运行包，不向服务器复制 macOS 的 Prisma 引擎：

```bash
pnpm --filter @fit-trace/api build
pnpm --filter @fit-trace/shared build
pnpm --filter @fit-trace/api deploy --prod --ignore-scripts /tmp/fittrace-api-package
```

在运行包的 `prisma/schema.prisma` 中，为生成器指定
`binaryTargets = ["debian-openssl-3.0.x"]`，然后从该目录使用项目安装的 Prisma CLI 执行
`generate --schema prisma/schema.prisma`。仅修改发布副本，不改变数据库结构。
生成后确认包内包含 `libquery_engine-debian-openssl-3.0.x.so.node`。

将独立 API 包放入发布目录的 `apps/api`，H5 产物放入 `apps/h5/dist`。
API 的 `.env` 链接至 `/etc/fittrace/api.env`；其他目录不复制环境配置、签名密码或上传目录。

## 发布与检查

1. 上传到新的 `/opt/fittrace/releases/<发布标识>`，不覆盖当前发布。
2. 备份现有 `fit_trace` 数据库与 Nginx 配置。
3. 如有新的迁移，在备份后通过现有 SSH 5432 隧道运行本机 `prisma:deploy`；不运行 `migrate dev` 或数据库重置。
4. 切换 `current` 链接，重启 `fittrace-api`，确认本机 `/api/v1/health` 成功。
5. 安装独立站点配置，`nginx -t` 成功后再 reload。
6. 运行 `deploy/smoke.mjs`，验收 API；另外检查公网 H5、History 路由和图片访问。
7. 验收通过后运行 `deploy/prune-server-releases.sh <本次发布前备份目录>`：只保留当前版本、前一个版本及本次发布前的完整备份，清理更早发布和备份；备份必须包含数据库与 API 配置。`activate-server.sh` 已在成功验收后执行这一清理。

只进行接口验收，不会发送真实验证码邮件或调用付费 AI：

```bash
sudo -u fittrace /opt/fittrace/runtime/bin/node /opt/fittrace/current/deploy/smoke.mjs
```

验收脚本创建独立临时账号，验证登录、四类记录、上传、首页聚合、续期和退出，最后清理临时数据。
SMTP 可通过 `nodemailer.verify()` 检查连接和认证，不发送邮件。
真实验证码收取、AI 识别、手机相机与键盘仍需实际使用验收。

常用维护命令：

```bash
sudo systemctl status fittrace-api
sudo journalctl -u fittrace-api -n 100 --no-pager
sudo systemctl restart fittrace-api
sudo /usr/local/nginx/sbin/nginx -t
curl -fsS https://fitness.rungos.cn/api/v1/health
```

回滚时将 `current` 指向上一个发布目录并重启 API；数据库迁移与代码回滚分开处理。
代码更新不清理数据库、上传目录、签名证书或其他项目的容器与文件。
数据库备份须另外保存在服务器之外，并定期确认可恢复。

保留版本的 API 运行包必须是完整目录或链接到 `/opt/fittrace/runtime/` 内的稳定运行包；不得引用即将删除的旧发布目录。清理脚本会检查保留版本中的失效链接及跨旧发布目录依赖，并在发现问题时拒绝删除。当前 API 运行包位于 `/opt/fittrace/releases/20261030-miniprogram-email-login/apps/api`，发布目录内的部署脚本已独立保存。

## 2026-10-30 微信基础版发布

已通过 SSH 别名 `my-tx-old` 发布到 `/opt/fittrace/releases/20261030-wechat-baseline-174403`，包含独立 Linux API 包和带微信关联码入口的 H5。该别名与旧服务器地址一致；连接时使用 `-o ClearAllForwardings=yes` 避免已有本地转发端口冲突。

发布前备份：`/var/backups/fittrace/20261030-wechat-baseline-174403`，包含可读取的 PostgreSQL dump、环境配置、服务定义和旧发布路径。此前发布和备份均保留。本次未运行清理脚本或修改 Nginx。

`20261030090000_wechat_identity` 已用 Prisma migrate deploy 成功应用，15 个迁移均完成。服务端 AppID/AppSecret 已配置，密钥通过 SSH 标准输入传递，未进入发布包、命令参数或源码。

既有 smoke 与新增 `deploy/smoke-wechat.mjs` 均通过：后者验证纯微信模型的空邮箱会话、不升级邮箱验证、保存去重、PUT 更新、上次成绩、续期和退出。验收临时数据清理后，原有账号/训练/体重计数仍为 3/6/6。公网 health 返回 200，微信登录无效参数返回 400、无效临时 code 返回 401 WECHAT_CODE_INVALID（此前为 404）。这不等于真实微信身份登录或老账号关联已验收。

真实微信首次登录前，应先在 H5 以邮箱验证码登录原账号、生成关联码，在小程序填入后确认登录。已有不同微信账号不能自动合并记录。

2026-10-30 随后发布 H5 关联码认证修复至 `/opt/fittrace/releases/20261030-h5-binding-auth-fix`，修正公开认证接口分类导致 binding-code 请求未携带登录令牌的问题。H5 67 项测试与构建通过。API 文件独立硬链接保留，未修改或重启 API、未迁移数据库；回滚指针备份在 `/var/backups/fittrace/20261030-h5-binding-auth-fix/previous-release.txt`。

同日发布空微信账号安全关联修复至 `/opt/fittrace/releases/20261030-wechat-empty-account-fix`，新备份为 `/var/backups/fittrace/20261030-wechat-empty-account-fix`。仅无资料/业务记录/其他身份凭证的纯微信空账号可在两端身份验证后重新关联，保留源账号并撤销旧会话，不合并记录。171 项测试与数据库事务专项验收通过，未新增结构迁移。专项脚本为 `deploy/smoke-wechat-rebinding.mjs`，只使用独立测试身份并自动清理数据。

## 2026-10-30 小程序邮箱登录发布（当前版本）

当前发布：`/opt/fittrace/releases/20261030-miniprogram-email-login`；备份：`/var/backups/fittrace/20261030-miniprogram-email-login`，包含已校验 PostgreSQL dump、环境配置、服务定义及上一版本路径。只更新 API 编译产物与 H5 静态资源，无新迁移、无 Nginx 修改、无清理旧发布。

小程序专用邮箱发码与登录路由已上线，发码跳过阿里云验证；H5/Android 原验证入口保持不变。H5 已移除关联码入口。真实数据库临时账号邮箱登录专项验收及原 smoke 通过，临时数据已清理；账号/训练/体重数量为 4/6/6，API active，NRestarts=0。

`deploy/smoke-miniprogram-email.mjs` 通过预置临时账号验证码摘要验收，不发送邮件，不接触真实账号身份。真实收信、微信合法域名与真机训练仍需实际使用验收。
