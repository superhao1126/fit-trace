# FitTrace 生产部署

## 服务器布局

- 服务器：`ubuntu@101.43.38.7`。
- H5：`https://fittrace.idoit.icu`，Nginx 提供静态资源并支持 History 路由回退。
- API：`https://fittrace.idoit.icu/api/v1`，转发至 `127.0.0.1:3100`。
- 数据库：沿用服务器现有 PostgreSQL `fit_trace`，与当前开发环境共用。
- 运行目录：`/opt/fittrace/current` 指向 `/opt/fittrace/releases/<发布标识>`。
- 生产配置：`/etc/fittrace/api.env`，权限 `root:fittrace 640`；发布包不包含密钥。
- 图片：`/var/lib/fittrace/uploads`，与代码发布目录分开，更新代码不会删除图片。
- APK：`/var/lib/fittrace/downloads/fittrace.apk`。
- API 服务：`fittrace-api.service`，使用独立 `fittrace` 用户与 Node 24，开机启动。
- TLS：复用 `/etc/ssl/soybean/` 中现有的 `*.idoit.icu` 证书。

`nginx-fittrace.conf` 仅为本项目增加精确域名站点，现有其他 Nginx 站点继续保留。
API 的 systemd 内存上限为 512 MB，V8 老生代上限为 256 MB，避免影响同机其他服务。

## 构建 H5 和正式 APK

本次发布保留运行时 GlitchTip 错误上报，跳过源码映射生成与上传：

```bash
GLITCHTIP_UPLOAD_SOURCEMAPS=false \
JAVA_HOME=$(/usr/libexec/java_home -v 21) \
pnpm build:release
```

正式 APK 为 `apps/h5/android/app/build/outputs/apk/release/app-release.apk`。
APK 使用 HTTPS 接口，正式包不允许明文 HTTP；局域网 HTTP 许可仅留在 Debug 资源中。

签名文件位于仓库根目录 `.android-signing/fittrace-release.jks`；
签名配置位于 `apps/h5/android/keystore.properties`，两者均被 Git 忽略。
更新必须沿用原签名，并递增 `android/app/build.gradle` 的 `versionCode`。
旧 Debug APK 的签名与正式包不同，首次换用正式包需要卸载 Debug 包再安装；服务器记录不受影响。

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
6. 运行 `deploy/smoke.mjs`，检查公网 H5、API、图片和 APK 下载。
7. 验收通过后运行 `deploy/prune-server-releases.sh <本次发布前备份目录>`：只保留当前版本、前一个版本及本次发布前的完整备份，清理更早发布和备份；备份必须包含数据库、APK 与 API 配置。`activate-server.sh` 已在成功验收后执行这一清理。

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
curl -fsS https://fittrace.idoit.icu/api/v1/health
```

回滚时将 `current` 指向上一个发布目录并重启 API；数据库迁移与代码回滚分开处理。
代码更新不清理数据库、上传目录、签名证书或其他项目的容器与文件。
数据库备份须另外保存在服务器之外，并定期确认可恢复。

保留版本的 API 运行包必须是完整目录或链接到 `/opt/fittrace/runtime/` 内的稳定运行包；不得引用即将删除的旧发布目录。清理脚本会检查保留版本中的失效链接及跨旧发布目录依赖，并在发现问题时拒绝删除。当前 API 运行包位于 `/opt/fittrace/runtime/api-bcbe53e/api`，发布目录内的部署脚本已独立保存。
