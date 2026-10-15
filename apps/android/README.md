# 循形 Android

独立 Kotlin + Jetpack Compose 客户端。使用 Android Studio 打开本目录；H5、Node.js、pnpm 均不参与 Android 构建。

## 当前范围

本次建立多模块工程、构建规范、主题、导航、网络响应模型、领域仓库契约、数据映射、单元测试与 CI。
启动入口仅含欢迎页和关于页，用于验证原生工程链路，不代表业务首页已经实现。
登录、令牌存储/续期、业务 ViewModel、数据库、训练、饮食和相册尚未接入。没有自动请求线上健康数据。
业务页面后续先完成设计，再按 feature 接入；当前主题复用现有品牌色。

## 环境与运行

- JDK 21（输出 Java/Kotlin 字节码目标 17）。
- Android SDK Platform 36、Build Tools 35.0.0，最低 Android 8.0 / API 26。
- Gradle 8.14.3、AGP 8.13.0、Kotlin 2.2.20；Compose BOM 2025.09.00。
- 版本统一在 `gradle/libs.versions.toml`；Gradle Wrapper 校验官方发行包 SHA-256。
- Android Studio 设置 Gradle JDK 为 21；SDK 路径写在被忽略的 `local.properties`，或设置 `ANDROID_HOME`。

```properties
# local.properties 示例，替换为自己的路径
sdk.dir=/path/to/Android/sdk
```

```bash
cd apps/android
./gradlew verify
./gradlew :app:assembleProdRelease :app:lintProdRelease
```

`verify` 包含 Spotless/ktfmt 格式检查、模块边界检查、网络/数据 JVM 单元测试、Android 单元测试、Lint 和开发 APK 构建。
CI 另外构建并检查生产 Release，确保 R8 混淆链路可用。

| 变体 | 包名 | API | 用途 |
| --- | --- | --- | --- |
| devDebug | `com.fittrace.android.dev.debug` | `http://10.0.2.2:3000/api/v1/` | 模拟器访问宿主机开发 API |
| prodDebug | `com.fittrace.android.debug` | `https://fittrace.idoit.icu/api/v1/` | 显式选择后联调正式 API |
| prodRelease | `com.fittrace.android` | 同上 | 生产构建，未配置签名 |

`devRelease` 禁用。Dev 仅对白名单中的模拟器/回环地址允许 HTTP；Prod 禁止明文 HTTP。
真机联调使用可达的 HTTPS 开发服务，按环境修改 URL，不扩大生产网络策略。

产物：

- `app/build/outputs/apk/dev/debug/app-dev-debug.apk`
- `app/build/outputs/apk/prod/release/app-prod-release-unsigned.apk`

版本从 `0.1.0 / versionCode 1` 开始；不复用旧 APK 包名、签名或本地数据。
生产签名尚未接入，证书和密码只能通过本地/CI 密钥配置提供，不能入库或写进 Gradle 脚本。
本轮不操作设备，构建成功不能替代真机启动、返回键、安全区和深浅色验收。

## 目录和开发规范

模块职责、依赖图和扩展规则见 [ARCHITECTURE.md](ARCHITECTURE.md)。
新增依赖须更新版本目录和对应锁文件；在审核版本及传递依赖后执行：

```bash
./gradlew verify :app:assembleProdRelease :app:lintProdRelease --write-locks
```

使用 `./gradlew spotlessApply` 格式化 Kotlin 与构建脚本。提交前执行 `./gradlew verify`，禁止提交 `local.properties`、构建目录、密钥或真实用户数据。

版本兼容性参考：[AGP 8.13](https://developer.android.com/build/releases/agp-8-13-0-release-notes)、[Kotlin/AGP 兼容表](https://developer.android.com/build/kotlin-support)、[Compose BOM](https://developer.android.com/develop/ui/compose/bom)。

## IDE 构建排查

仓库根目录的 VS Code 工作区已关闭 Gradle 自动发现、Java Gradle 自动导入和 Java 自动编译，并通过 `java.import.exclusions` 排除 `apps/android`；Android 工程由 Android Studio 导入。不要让 Java 扩展将 `build-logic` 当成独立 Gradle 工程导入并使用另一套 Gradle/JDK；两套构建共用输出目录，可能发生生成文件与增量状态冲突。

如果打开旧工作区后出现 `compilePluginsBlocks` 的 `plugin-spec-builders is not a directory`，先让 VS Code 重新加载工作区配置，再在 Android Studio 执行 **File → Sync Project with Gradle Files**。Gradle JDK 选择 21，Gradle 版本使用项目 Wrapper。
