# 原生 Android「我的」（2026-10-16）

沿用 [Figma 我的主画板](https://www.figma.com/design/Ct4AworB8fYBPjpSVwWbRW?node-id=21-5479)，以及个人资料（23:7544）、目标（23:7572）、设置（23:7605）、账号设置（23:7651）和关于（23:7762）。主页面保留灰底白卡、头像与账号信息、档案入口和目标进度；所有二级页面隐藏底部导航。

## 本轮实现

- `GET auth/me`：真实昵称、邮箱、头像、加入日期和目标。昵称未设置时使用邮箱前缀。
- `GET body-records/latest`：最新体重及更新时间；与首页只显示所选日期体重的口径区分。体重读取失败与未记录分别展示，失败可重试；刷新失败保留已加载资料。
- 目标进度沿用 H5：按起始、当前及目标体重计算，限制为 0–100%；保持目标、起始等于目标或没有当前体重时不显示百分比。
- 个人资料展示、昵称与性别编辑，使用 `PATCH users/me/profile`。请求不包含 `avatarUrl`，保留已有头像；头像支持真实图片加载及默认图形兜底，上传/移除尚未迁移。
- 目标新建/编辑，使用 `PATCH users/me/goal`。校验 1–500 kg、最多两位小数、减脂/增肌方向和目标日期；未来目标日期可选，日期可清除，取消选择不修改原值。
- 没有体重时允许填写当前体重；只有主动填写/编辑新体重才提交 `currentWeight`。界面说明保存目标会同步记录该体重，避免无意新增身体记录。保存后刷新我的摘要和首页。
- 昵称、性别和目标草稿保存在 ViewModel，旋转时保留；保存失败保留输入，提交期间阻止重复提交和返回。没有将资料草稿写入磁盘。
- 设置包含账号设置、真实用户协议与隐私内容、关于/实际版本、退出确认，以及跟随系统/浅色/深色；显示模式由 app 层保存在本地 SharedPreferences，并作用于整个客户端。
- 协议由 app 组合现有 auth 公共入口，不建立 feature→feature 的项目依赖。

## 保留边界

完整档案、头像上传/移除及登录后的密码修改尚未迁移，相关入口明确提示实际状态；密码仍可从登录页设置或找回。本轮未启用指纹登录，继续保留原方案。

API、H5 和数据库 schema 未改动。没有操作设备、调用正式账号保存接口或部署。实际账号读取、资料/目标持久化、图片加载、系统返回/键盘和安全区仍需设备验收。

## 依赖与验证

按实际头像消费引入 Coil 3.3.0，版本目录及锁文件纳入工程；UI 使用 [Coil Compose](https://coil-kt.github.io/coil/compose/) 的 AsyncImage，网络运行时在 app 装配，feature 不直接引入 Retrofit/OkHttp。

测试使用 MockWebServer、隔离仓库和 Robolectric。覆盖 nullable 最新体重、资料字段和目标映射、业务错误与无效响应、草稿校验和失败保留、重复提交、目标进度、日期取消、资料保存回显、二级页导航隐藏、显示模式、协议及退出取消。截图为本地绘制，非真机截图。

```bash
cd apps/android
./gradlew verify :app:assembleProdDebug :app:assembleProdRelease :app:lintProdRelease
```

图像源与图层对应见 `feature/main/src/main/assets/figma/README.md`。完整测试画面输出至 `feature/main/build/reports/profile-screenshots/`。


最终验证全部通过：84 项测试（network 19、data 26、auth 18、main 16、app 5），模块边界、格式检查、devDebug/prodDebug/prodRelease 构建与 prodRelease Lint 通过。Release 仍沿用未签名配置。

本地绘制预览：[我的](android-profile-2026-10-16.png)、[320dp 深色](android-profile-dark-320-2026-10-16.png)、[设置](android-profile-settings-2026-10-16.png)、[目标编辑](android-profile-goal-2026-10-16.png)。

## 收尾复核（2026-10-19）

复核资料/目标接口、导航和主题装配后，再次执行上述完整验证命令（增加 `--offline`，复用本机依赖及任务缓存），全部通过。测试报告共 84 项，失败与错误均为 0；devDebug、prodDebug、未签名 prodRelease 构建及 prodRelease Lint 通过。此次仅做本地复核，未安装设备、向真实账号保存数据或部署。
