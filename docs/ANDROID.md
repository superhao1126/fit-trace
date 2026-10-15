# Android 客户端

`apps/android` 已创建独立 Kotlin + Jetpack Compose 多模块工程，使用 Android Studio 打开该目录。
构建步骤、环境与产物见 [工程 README](../apps/android/README.md)，分层及依赖约束见 [架构规范](../apps/android/ARCHITECTURE.md)。

- `apps/h5` 负责浏览器端，Vite/pnpm 构建。
- `apps/android` 负责原生客户端，Gradle 构建，不加载 H5 资源。
- `apps/api` 提供共享接口；数据库保持共用。

当前完成工程底座与最小启动/关于页，登录、业务页面及本地数据持久化尚未实现。
新包名为 `com.fittrace.android`，版本从 `0.1.0 / 1` 开始，不依赖旧 APK 升级或本地数据迁移。

通用品牌素材仍在 `assets/brand`。旧签名证书和配置保留在 Git 忽略的 `.android-signing` 中，仅供历史归档，未接入新工程。
旧 Capacitor 发布和安装记录仅供追溯，其中的构建命令与路径不再适用。
