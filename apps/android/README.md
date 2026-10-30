# 循形Fitness Android

独立 Kotlin + Jetpack Compose 客户端。使用 Android Studio 打开本目录；H5、Node.js、pnpm 均不参与 Android 构建。

## 当前范围

已建立多模块工程、构建规范、主题、导航、分层网络与 CI，并接入原生邮箱验证码登录、密码登录、找回密码、协议阅读、加密会话保存、续期与退出。登录页沿用已确认的设计稿，详见 [登录实现与验收](../../docs/ANDROID-AUTH.md)。
登录后首页已按本地最终设计更新绿色主卡，接入身体、饮食、运动、照片录入与详情、趋势、分页档案、力量训练与模板/每周计划；资料支持头像、昵称和性别，目标为两步设置。全部使用现有真实接口，失败与无记录分开显示；高级能力和真机验收边界见 [本轮实现进度](IMPLEMENTATION-2026-10-20.md)。

指纹 / 生物识别登录已接入邮箱验证开启、普通退出后签名重新登录、关闭和失效恢复。使用与部署要求见 [指纹登录实现](../../docs/ANDROID-BIOMETRIC-LOGIN.md)；需要先应用服务端迁移，真实传感器及账号联调尚未验收。原始方案保留于 [指纹登录方案](../../docs/ANDROID-BIOMETRIC-LOGIN-PLAN.md)。

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
./gradlew :app:assembleProdDebug
./gradlew :app:assembleProdRelease :app:lintProdRelease
```

`verify` 包含 Spotless/ktfmt 格式检查、模块边界检查、网络/数据 JVM 单元测试、ViewModel 与本地 Compose UI 回归、Android 单元测试、Lint 和开发 APK 构建。
CI 另外构建并检查生产 Release，确保 R8 混淆链路可用。

| 变体 | 包名 | API | 用途 |
| --- | --- | --- | --- |
| devDebug | `com.fittrace.android.dev.debug` | `http://10.0.3.54:3000/api/v1/` | 局域网访问电脑开发 API |
| prodDebug | `com.fittrace.android.debug` | `https://fitness.rungos.cn/api/v1/` | 当前指定接口，保留现有安装包身份 |
| prodRelease | `com.fittrace.android` | 同上 | 生产构建，未配置签名 |

`devRelease` 禁用。Dev 与 Prod 均允许任意地址的 HTTP 请求，同时保留 HTTPS 支持。
真机联调当前指定接口时，在 Android Studio 的 Build Variants 中选择 `prodDebug`。两个 Debug 变体包名不同，会安装为独立应用。`devDebug` 通过局域网访问电脑 `10.0.3.54:3000`，不需要 adb reverse。手机与电脑需网络互通，后端须监听 `0.0.0.0:3000` 或该局域网地址，并允许防火墙入站访问。电脑 IP 变化时需同步修改 dev 基础地址。

产物：

- `app/build/outputs/apk/dev/debug/app-dev-debug.apk`
- `app/build/outputs/apk/prod/debug/app-prod-debug.apk`
- `app/build/outputs/apk/prod/release/app-prod-release-unsigned.apk`

当前原生版本为 `0.2.0 / versionCode 3`；不复用旧 Capacitor APK 包名、签名或本地数据。
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

## 开发日志

Debug 包已启用本地接口诊断日志，Android Studio 的 Logcat 以 `tag:循形Fitness.Http` 筛选。记录完整 URL、查询参数、请求正文、服务端响应正文、HTTP 状态码、耗时及网络异常类型。按调试要求，正文中的密码、验证码和 Token 均明文显示；JSON 缩进并分段输出，请求头/响应头不输出。Release 关闭。查看步骤和验证边界见 [开发日志说明](../../docs/ANDROID-LOGGING.md)。

2026-10-20：原域名已停用，按用户指定将 prod 接口改为 `http://10.0.3.54:3000/api/v1/`；2026-10-21 起 prod/dev 网络策略允许任意地址的 HTTP 请求。沿用 prodDebug 包名覆盖更新。

2026-10-30：prod API 与验证码页面来源恢复为 `https://fitness.rungos.cn`；dev 继续使用局域网联调地址。
