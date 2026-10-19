# 设计交接清单 · 2026-10-19

## 顺序与边界

先完成可编辑 Figma 核心设计，再从 H5 首页逐页实现。H5 与原生 Android 独立。本批均为视觉/状态设计，未接入 API、未安装或部署；不要把设计截图当成运行验收证据。

Figma MCP Starter 额度已达到限制；近期以浏览器导入 SVG 原生图层和编辑器调整为主。文字和矢量可编辑，摄影图仅用于单个物品；近期导入批次尚未建立自动布局、变量/组件绑定和点击原型。这些事项仍未完成，不能把文字约定当成原型连线。

## 当前使用的稿件

| 模块 | 主稿 | 本地源文件 |
| --- | --- | --- |
| 首页原定布局 | [29 · 三页样稿](https://www.figma.com/design/Ct4AworB8fYBPjpSVwWbRW?node-id=151-2774) | Figma 原有图层 |
| 首页关键状态 | [37 · 六屏](https://www.figma.com/design/Ct4AworB8fYBPjpSVwWbRW?node-id=209-4885) | fittrace-home-states-green-2026-10-19.svg |
| 趋势与档案 | [36 · 十二屏](https://www.figma.com/design/Ct4AworB8fYBPjpSVwWbRW?node-id=206-6334) | fittrace-trends-archive-green-2026-10-19.svg |
| 我的、资料、目标、设置 | [35 · 十八屏](https://www.figma.com/design/Ct4AworB8fYBPjpSVwWbRW?node-id=203-8647) | fittrace-profile-green-2026-10-19.svg |
| 运动选择与记录 | [34 · 实拍图六屏](https://www.figma.com/design/Ct4AworB8fYBPjpSVwWbRW?node-id=196-4795) | fittrace-training-caljimi-2026-10-19.svg |
| 饮食主流程 | [30 · 原生自动布局](https://www.figma.com/design/Ct4AworB8fYBPjpSVwWbRW?node-id=163-4320) | 直接在 Figma 修改；本地 meal-flow PNG 供预览 |
| 登录 | [32 · 完整状态](https://www.figma.com/design/Ct4AworB8fYBPjpSVwWbRW?node-id=188-4148) | fittrace-login-green-2026-10-16.svg |

上述 SVG/PNG 均在本目录。不要使用已被替换的 198:7592 或 198:6654 作为我的页面最新稿。

## 已确认的交互方向

- 饮食与运动参考 CalJimi 的选择 → 调整份量/时长 → 加入清单 → 完成记录；参考来自 UI Notes 截图，没有实际操作竞品 App。
- 食物与具体运动使用对应摄影图，插画用于小芽与功能提示；运动照片来源见 sport-photos/README.md。
- 选食物清单与份量弹层均 390 × 478、顶部 Y=366。份量弹层纵向间距 16，按钮下方保持常规边距。
- 资料页为居中头像＋相机入口＋分组行；昵称/性别先更新草稿，再统一保存。保存失败保留输入。
- 我的/设置已移除「重新查看功能引导」入口；当前 H5 代码仍有该功能，实施时需同步处理。
- 无数据、未知热量、请求失败分开表达；不把 null 当作 0，不把读取失败当成空记录。
- 历史补记必须携带日期；刷新失败保留同一日期的旧数据，不能冒充最新结果。

## 下一轮先做什么

1. 补训练模板选择、动作选择/详情、结束确认和离开草稿提示；沿用已有能力，不扩大成新训练系统。
2. 补表单边界：无当前体重、目标方向错误、放弃修改、保存失败后重试。
3. 整体检查 320px 窄屏、键盘遮挡、深色，以及按钮/弹层位置；补可复用组件与必要点击原型。深色尚未完成，不能据一个显示模式选择弹层视为完成深色稿。
4. 确认核心流程可落地后，从 H5 首页开始实现，再做饮食、运动、趋势/档案、我的/设置；原生 Android 另行排期。

食品库 360 项仍是候选目录；逐项营养、照片与许可核验未完成，不视为生产入库。

## 实现时必须核对

首页 DashboardView.vue；趋势 TrendsView.vue；档案 ArchiveView.vue；我的 ProfileView.vue；资料 AccountSettingsView.vue；目标 GoalSettingsView.vue。设计是现有能力的新版组织方式，部分导航/状态为后续实现约定，须再对照真实接口和数据口径。

本轮未改业务源码。仓库另有未跟踪的登录 SVG 与 docs/food-library/，保留原状，不一并提交或删除。详细节点、证据和限制见 CORE-DESIGN-PROGRESS.md。
