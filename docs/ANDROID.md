# Android 客户端

`apps/android` 已创建独立 Kotlin + Jetpack Compose 多模块工程，使用 Android Studio 打开该目录。
构建步骤、环境与产物见 [工程 README](../apps/android/README.md)，分层及依赖约束见 [架构规范](../apps/android/ARCHITECTURE.md)。

- `apps/h5` 负责浏览器端，Vite/pnpm 构建。
- `apps/android` 负责原生客户端，Gradle 构建，不加载 H5 资源。
- `apps/api` 提供共享接口；数据库保持共用。

当前完成工程底座、原生登录/找回密码及加密会话保存与续期，登录后仍为欢迎/关于页，业务首页尚未迁移。详见 [登录实现与验收](ANDROID-AUTH.md)。
新包名为 `com.fittrace.android`，当前原生版本 `0.1.1 / 2`，不依赖旧 Capacitor APK 升级或本地数据迁移。

通用品牌素材仍在 `assets/brand`。旧签名证书和配置保留在 Git 忽略的 `.android-signing` 中，仅供历史归档，未接入新工程。
旧 Capacitor 发布和安装记录仅供追溯，其中的构建命令与路径不再适用。
