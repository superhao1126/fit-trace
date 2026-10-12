# APK 0.1.6 顶部安全区修复

- 修复提交：595ba4b；APK versionCode 8，versionName 0.1.6。
- 问题：页面顶部 padding 会随文档滚动离开视口，edge-to-edge WebView 的滚动内容进入 Android 状态栏区域。
- 修复：原生 App 公共样式增加固定顶部安全区遮挡层，采用系统注入的实际安全区高度与主题背景，覆盖滚动内容。普通浏览器不显示此层，没有更改页面布局、数据库或 API。
- 格式检查、H5 类型检查及生产 H5/APK 构建通过。
- 真机：原版本趋势标题与状态栏时间图标重叠；安装 0.1.6 后趋势页向上滚动至卡片和图表区域，状态栏背景保持固定，时间及图标清晰。设备 WebView 146.0.7680.119。未新增或修改记录。
- 线上发布目录：/opt/fittrace/releases/20261012-595ba4b，复用原 API；previous 为 20261012-1ff4102。API 健康 OK。
- APK SHA-256：87efd0d0c76c5dc9c36475d4def6a1e59e022c023e71f147f0652ad49e9c8cc4。
