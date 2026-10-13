# 0.1.8 基础组件与品牌加载

- Android：versionName 0.1.8，versionCode 10。沿用既有正式签名，已通过 adb install -r 覆盖安装到连接设备，安装后版本核对通过；未启动或操作 App。
- 本次发布当前工作区的 H5/APK 改动：时间滚轮、品牌 Loading、统一底部弹层、确认框与数字输入。源码尚未提交，构建标识基于 f76af61 工作区。
- 生产目录：/opt/fittrace/releases/20261013-ui-0.1.8。
- 回滚版本：/opt/fittrace/releases/20261012-b6a2cf2（0.1.7）。
- 发布前完整备份：/var/backups/fittrace/20261013-ui-0.1.8，包含数据库、Nginx 配置、API 配置与上一版 APK。
- 线上地址：https://fittrace.idoit.icu/；APK：https://fittrace.idoit.icu/downloads/fittrace.apk。
- APK SHA256：735d95b01e9ca2b2370de88ddc8c40c3aaf2e7d30419f9f0e74900a6075e9b9d。

## 发布验证

pnpm check、167 项 H5/API 回归、正式 H5/APK 构建通过；保留原有 UiInput 属性顺序 lint 警告及 Gradle 弃用提示。212 个 APK 前端文件与本地 H5 一致，公网 212 个 H5 文件和 APK 也与本地构建一致。APK 新旧签名一致，正式配置禁止 mixed content，无开发服务 URL、源码映射或环境文件。

生产烟雾检查覆盖数据库、登录、四类记录、训练模板/计划、逐组训练、首页、图片、续期与退出；临时账号、记录和上传文件已清理。health 返回 OK，未认证 dashboard 返回 401，.env 返回 403，源码映射返回 404。没有 API 业务变更或数据库迁移，沿用线上原 API 发布包。

## 版本保留规则

按用户要求，清理 7 个更早发布目录、5 个更早完整备份及 2 个旧配置备份。线上只保留当前版、前一版与本次发布前完整备份，清理后服务 active、health 正常。

新增 deploy/prune-server-releases.sh，activate-server.sh 在验收成功后执行清理。脚本先校验 current/previous 均位于发布目录，并确认保留备份含数据库、APK 和 API 配置，再删除更早的发布及备份。

## 用户真机验收

1. 时间滚轮的上下滑动、回显、返回日期与确认。
2. 浅色/深色 Loading、按钮提交中禁用；食品库弹层滚动与底部安全区。
3. 食品份量及训练数字输入键盘、清空与范围提示。
4. 删除失败后重试、取消与成功关闭。真实删除应使用可丢弃测试记录。
