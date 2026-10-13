# 0.1.10 提示优化发布记录

- 发布日期：2026-10-13。
- Android：`com.fittrace.app`，versionName `0.1.10`，versionCode `12`，沿用原有签名证书。
- 线上入口：https://fittrace.idoit.icu
- APK：https://fittrace.idoit.icu/downloads/fittrace.apk
- APK SHA-256：`9c71e405e80c29d263786bca8d6a3a39ff2986886c0fb35a6b7fbbe9c7d79d85`。

本版统一成功、提醒、错误和普通信息提示：顶部轻量浮层、状态图标、长文本换行、深浅主题及自动关闭；后出现的提示替换前一个，旧计时器不会关闭新提示。浮层不阻挡页面操作。普通 H5 继续不显示品牌开屏。

## 验证

- `pnpm check`、171 项 API/H5 测试、正式 H5/APK 构建通过。
- 存在原有 `UiInput.vue` 属性顺序 lint 警告及非阻断 Gradle 弃用提示。
- APK 内 209 个前端文件与 H5 构建一致；公网 209 个文件及下载 APK 与本地产物一致。
- 发布后业务 smoke 通过，临时账号、记录和上传文件已清理。
- 公网 health 返回 `OK`，未登录 dashboard 返回 401，`.env` 返回 403，源映射返回 404。
- 本轮更新 APK 下载包，未操作手机安装；此前提示的窄屏、长文本、深色及操作穿透已在内置浏览器验证。

## 部署及回滚

- 当前版本：`/opt/fittrace/releases/20261013-toast-0.1.10`。
- 前一版：`/opt/fittrace/releases/20261013-brand-0.1.9`。
- 发布前完整备份：`/var/backups/fittrace/20261013-toast-0.1.10`，包含数据库、配置及旧 APK。
- 后端沿用 `/opt/fittrace/runtime/api-bcbe53e/api`，没有数据库迁移。
- 切换当前版本和 APK 后重启 API，通过检查后执行依赖校验及清理；只保留上述两个版本和本次完整备份。
- 清理后再次确认服务 active、健康接口正常，稳定 API 目录存在。
- 本轮未提交或推送 Git。
