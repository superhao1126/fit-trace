# 0.1.16 记录效率真机安装与后端发布

- 日期：2026-10-13。用户授权覆盖安装 APK，并明确同意同步部署后端以完整试用。
- 手机：已连接的 Redmi K50 Ultra；应用 `com.fittrace.app`，版本 `0.1.16`，versionCode `18`。
- `adb install -r` 返回 `Success`，系统包信息确认 `0.1.16 / 18`，没有卸载或清除应用数据。本轮只安装和核对版本，没有代做手机页面验收。
- APK 沿用原签名，证书 SHA-256 为 `d7f7574f10d78c09a96eb41d5d818025bd0fb782027a26cb9e0fd10b4140b1bd`。
- APK SHA-256：`79966a752822e821cde87b210f5852a7894f498504df3471e25f2e26eb7e7049`。包内 216 个前端文件与生产 H5 构建逐字节一致，使用线上 HTTPS API，关闭混合内容，无环境文件或 source map。
- 源码基于 `415a9a3` 加本轮尚未提交的工作树修改。正式构建通过，保留既有趋势页大分包及 Gradle 弃用提示。
- 新功能：饮食草稿、首页开始/继续训练、未安排/休息/训练中状态、上次成绩带入、常用餐收藏与复用。实现和验收清单见 [记录效率说明](./RECORD-EFFICIENCY-2026-10-13.md)。

## 后端

- 发布目录：`/opt/fittrace/releases/20261013-record-efficiency-0.1.16`。
- 回滚目录：`/opt/fittrace/releases/20261013-toast-0.1.10`，API 旧运行包保留。
- 发布前备份：`/var/backups/fittrace/20261013-record-efficiency-0.1.16`，包含数据库、API 配置、Nginx 配置和公开 APK。数据库另存本机 `/tmp/fittrace-before-0116.dump`；`pg_restore --list` 校验通过，两份备份 SHA-256 相同。
- 新增迁移 `20261013090000_meal_favorites` 已成功应用。没有修改或删除已有记录。
- API 在本机打包并生成 Linux Prisma 引擎，上传后原子切换发布指向并重启；失败自动恢复旧 API 的机制保留在本轮发布脚本中。数据库迁移是新增表，回滚代码时保留该表。
- 真实数据库上的本机和公网 API 验收均通过，覆盖登录、四类记录、常用餐零值/未知值及快照、计划是否配置、上次成绩、图片、续期和退出，临时账号、记录及上传文件已清理。
- 服务状态 active，重启计数为 0。公网 health 正常，未登录接口 401，`.env` 403、`.map` 404。新旧 H5 首页哈希一致。
- 本轮只发布后端，线上 H5 与公开 APK 下载继续保留原发布版本；新版 APK 已安装在手机，未推送 Git。

APK 路径：`apps/h5/android/app/build/outputs/apk/release/app-release.apk`。
