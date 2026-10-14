# 0.1.17 首页训练卡 UI 修正

[Figma：21 · 首页训练卡修正](https://www.figma.com/design/Ct4AworB8fYBPjpSVwWbRW?node-id=105-3)。先完成可编辑 SVG 设计，再实现局部修正。

- 首页训练卡补齐与上方概览的 16px 间距。
- 未安排/休息时，“自由训练”采用主按钮，“安排/调整计划”保留次要操作，补齐操作间距。
- 训练卡内边距、标题及按钮高度统一收紧；不更改接口、记录或草稿恢复逻辑。运动工具页沿用其原有网格间距。
- 浏览器使用实际首页组件和隔离数据检查 390px 浅色、320px 深色：概览和卡片间距实测 16px，页面无横向溢出，截图保存在本目录。没有使用真实账号进行浏览器写入或操作手机界面。
- H5 类型检查、局部 ESLint、正式 H5/APK 构建及 `git diff --check` 通过；保留原有趋势页分包和 Gradle 弃用提示。
- APK 版本 `0.1.17`，versionCode `19`。原签名证书一致，包内 216 个前端文件与构建产物逐字节一致，正式包关闭混合内容，无环境文件和 source map。
- APK SHA-256：`f43d00b5cd115d43398a20e9288d3cac5169dff0769c23ca55a35de747cbfbd8`。
- 手机重新连接后，`adb install -r` 返回 `Success`，系统包信息确认 `0.1.17 / 19`；没有卸载或清除应用数据。后端沿用已上线的 0.1.16 API；本轮没有发布线上 H5 或公开 APK。

APK：`apps/h5/android/app/build/outputs/apk/release/app-release.apk`。
