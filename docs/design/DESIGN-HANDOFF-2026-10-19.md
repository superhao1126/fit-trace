# 设计交接清单 · 2026-10-19

## 顺序与边界

先完成可编辑 Figma 核心设计，再从 H5 首页逐页实现。用户已明确后续都按 CalJimi 设计：优先参考对应页面的结构和操作方式，截图未覆盖的业务状态才作循形适配。H5 与原生 Android 独立。本批均为视觉/状态设计，未接入 API、未安装或部署；不要把设计截图当成运行验收证据。

当前设计阶段已完成视觉和关键状态收尾。最终集合为 113 个页面/状态，390/320px 与浅/深色共 452 个版本；[完整交付说明](final/README.md) 为最新入口。Figma 40–46 号稿覆盖新增与全部主题/宽度；身体和照片已有原生点击连线，全流程本地原型另行提供。导入稿可编辑，不能称为全部已绑定组件/自动布局；业务实现仍在下一阶段。

## 当前使用的稿件

| 模块 | 主稿 | 本地源文件 |
| --- | --- | --- |
| 首页原定布局 | [29 · 三页样稿](https://www.figma.com/design/Ct4AworB8fYBPjpSVwWbRW?node-id=151-2774) | Figma 原有图层 |
| 首页关键状态 | [37 · 六屏](https://www.figma.com/design/Ct4AworB8fYBPjpSVwWbRW?node-id=209-4885) | fittrace-home-states-green-2026-10-19.svg |
| 趋势与档案 | [36 · 十二屏](https://www.figma.com/design/Ct4AworB8fYBPjpSVwWbRW?node-id=206-6334) | fittrace-trends-archive-green-2026-10-19.svg |
| 目标与表单边界 · CalJimi | [39 · 十二屏](https://www.figma.com/design/Ct4AworB8fYBPjpSVwWbRW?node-id=217-5088) | fittrace-goal-forms-caljimi-2026-10-19.svg |
| 我的、资料、设置（目标以 39 为准） | [35 · 十八屏](https://www.figma.com/design/Ct4AworB8fYBPjpSVwWbRW?node-id=203-8647) | fittrace-profile-green-2026-10-19.svg |
| 力量训练 · CalJimi 清单流程 | [38 · 九屏](https://www.figma.com/design/Ct4AworB8fYBPjpSVwWbRW?node-id=214-4365) | fittrace-strength-caljimi-2026-10-19.svg |
| 运动选择与记录 | [34 · 实拍图六屏](https://www.figma.com/design/Ct4AworB8fYBPjpSVwWbRW?node-id=196-4795) | fittrace-training-caljimi-2026-10-19.svg |
| 饮食主流程 | [30 · 原生自动布局](https://www.figma.com/design/Ct4AworB8fYBPjpSVwWbRW?node-id=163-4320) | 直接在 Figma 修改；本地 meal-flow PNG 供预览 |
| 登录 | [32 · 完整状态](https://www.figma.com/design/Ct4AworB8fYBPjpSVwWbRW?node-id=188-4148) | fittrace-login-caljimi-2026-10-19.svg |

上述 SVG/PNG 均在本目录。不要使用已被替换的 198:7592 或 198:6654 作为我的页面最新稿。

## 当前配色

当前 9 组稿（29、30、32、34–39）已在 Figma 原位统一主绿 #24C867；背景 #F7F8FA、浅绿 #E6F8EE、主文字 #222222、次文字 #777777。主绿为 CalJimi 截图采样近似，配套色为循形适配。变量别名保留，本地 7 份 SVG 同步；历史单屏 PNG 未全部重拍，颜色以最新总览和 Figma 为准。详见 [颜色清单](tokens/caljimi-light-2026-10-19.json)。深色已按最终交付补齐，旧单屏预览不作为最新颜色依据。

## 已确认的交互方向

- 力量训练也采用 CalJimi 分类清单与底部数字输入；保留完成组记录，模板降为次要入口。参考截图未证明 CalJimi 存在逐组训练功能。
- 饮食与运动参考 CalJimi 的选择 → 调整份量/时长 → 加入清单 → 完成记录；参考来自 UI Notes 截图，没有实际操作竞品 App。
- 食物与具体运动使用对应摄影图，插画用于小芽与功能提示；运动照片来源见 sport-photos/README.md。
- 选食物清单与份量弹层均 390 × 478、顶部 Y=366。份量弹层纵向间距 16，按钮下方保持常规边距。
- 资料页为居中头像＋相机入口＋分组行；昵称/性别先更新草稿，再统一保存。保存失败保留输入。
- 我的/设置已移除「重新查看功能引导」入口；当前 H5 代码仍有该功能，实施时需同步处理。
- 无数据、未知热量、请求失败分开表达；不把 null 当作 0，不把读取失败当成空记录。
- 历史补记必须携带日期；刷新失败保留同一日期的旧数据，不能冒充最新结果。

## 设计收尾结果与下一阶段

身体记录、照片、账号安全分支、二级页、保持目标、加载/失败、窄屏、深色与关键原型已补齐。当前来源、节点、路由与复现方式全部见 [最终交付](final/README.md)；其清单优先于本文件早期批次表。协议/隐私为已有正文，品牌角色仍为循形小芽。原生 Figma 连线限身体和照片，其他关键流程用本机原型验收。

下一阶段从 H5 首页开始实施，再做饮食、运动、身体/照片、趋势/档案、我的/设置；原生 Android 独立排期。本轮没有开始改业务源码。食品库 360 项仍是候选目录，其营养与照片许可核验属于数据工作。

## 实现时必须核对

首页 DashboardView.vue；趋势 TrendsView.vue；档案 ArchiveView.vue；我的 ProfileView.vue；资料 AccountSettingsView.vue；目标 GoalSettingsView.vue。设计是现有能力的新版组织方式，部分导航/状态为后续实现约定，须再对照真实接口和数据口径。

本轮未改业务源码。仓库另有未跟踪的登录 SVG 与 docs/food-library/，保留原状，不一并提交或删除。详细节点、证据和限制见 CORE-DESIGN-PROGRESS.md。
