# 原生 Android 首页（2026-10-16）

登录后从欢迎页进入 Compose 首页。沿用 [Figma 完整首页](https://www.figma.com/design/Ct4AworB8fYBPjpSVwWbRW?node-id=21-4957) 与 [首次使用状态](https://www.figma.com/design/Ct4AworB8fYBPjpSVwWbRW?node-id=21-5067)，保留灰底白卡、三项指标、四类入口、当天记录、近七天统计和四项底部导航。原图标通过 Figma 文件内导出随 APK 打包。

## 已接入

- `GET dashboard?date=yyyy-MM-dd&tzOffset=...`：选中日期的体重、已记录热量、训练时长、当天记录预览及近七天摘要。
- `GET auth/me`：展示当前账号真实体重目标；没有目标时显示设置入口。目标摘要只在今天显示，避免把当前目标误作历史目标。
- 日期栏以周一为首，可左右滑动换周；日历提供年月切换、历史日期选择、确认与取消。禁止选择未来日期；查看历史日期时可回到今天。
- 原生下拉刷新；加载、请求失败和空记录状态分别处理。刷新失败保留当前日期已成功加载的数据，切换日期先清除旧日期数据并取消旧请求。
- 仓库沿用同一会话锁、到期刷新和一次 401 重试；再次失效清除本机会话并返回登录。令牌不进入 feature 或 UI 状态。
- 热量未知保留 `null` 并显示“—”或“热量未填写”，不伪造为 0；记录时间按本地时区显示。
- 我的页保留账号邮箱、关于和退出；从我的/关于返回可回到首页。

首页展示的是聚合接口提供的记录预览，饮食和照片最多各 4 条；并非完整档案。记体重、记饮食、记训练、记照片、趋势和完整档案入口目前提示“暂未开放”，未接入保存或模拟业务成功。训练计划/训练进行中卡片随原生训练模块后续迁移。

## 验证与边界

验证通过：`verify`、prodDebug/prodRelease 构建与 prodRelease Lint。69 项本地测试全部通过（network 17、data 23、auth 18、main 6、app 5）。新增 10 项首页相关测试覆盖接口查询与 nullable 字段、目标映射、业务失败、401 刷新和失效清理、日期切换取消、未来日期禁选、失败保留、日历取消、快捷入口及页面状态。

本地渲染预览：[空首页](android-home-empty-2026-10-16.png)、[有记录](android-home-records-2026-10-16.png)、[320dp 深色](android-home-dark-320-2026-10-16.png)。

测试使用隔离替身和本机 MockWebServer，Compose 画面使用 Robolectric 本地绘制，不是设备截图。没有向正式账号写入记录，没有安装 APK、操作设备或部署。真机需验收系统安全区、返回键、下拉和横滑手势、真实账号记录/目标、日期切换、断网恢复、登录续期与退出。

```bash
cd apps/android
./gradlew verify :app:assembleProdDebug :app:assembleProdRelease :app:lintProdRelease
```

截图由 `feature:main` 测试输出至 `build/reports/home-screenshots/`。生产 Release 沿用现有未签名配置；真实接口联调应按 Android README 选择合适的 devDebug 或 prodDebug 变体。


## 时区偏移修正（2026-10-16）

真机日志发现首页把东八区传成 `tzOffset=-480`。API 约定 UTC 以东为正，Java ZoneOffset 已经符合该约定，移除额外负号后上海发送 `480`。例如 16 日 13:52（UTC 05:52）误传 -480 会让后端算出 15 日 21:52，进而拒绝所选的 16 日。修正客户端和契约测试的符号，并补充 ViewModel 实际请求在上海、UTC、洛杉矶三个时区的参数回归。


「我的」、目标设置、个人资料及设置已于 2026-10-16 接入原生实现，首页目标行可直接进入目标编辑；保存后刷新首页摘要。详见 [我的页面](ANDROID-PROFILE.md)。
