# Android 客户端规划

2026-10-15 起，仓库已移除 Capacitor 和 H5 打包 APK 的工程、依赖、命令及原生运行时适配。

- `apps/h5`：Vue 浏览器端，使用 Vite 构建和部署。
- `apps/api`：供各客户端共用的 NestJS API。
- `apps/android`：后续独立创建 Kotlin + Jetpack Compose 工程，本次尚未创建。

通用品牌素材保留在 `assets/brand`，H5 使用的图标保留在 `apps/h5/public`。
旧签名证书仍保存在被 Git 忽略的 `.android-signing`；旧签名配置单独保存为其中的 `legacy-keystore.properties`，仅供归档，其相对路径属于旧工程，不能直接用于新工程。

旧发布和安装记录仅用于追溯，记录中的 APK 路径及构建命令不再适用于当前代码。
后续原生客户端按新项目开发，不依赖旧 APK 的升级或本地数据迁移。
