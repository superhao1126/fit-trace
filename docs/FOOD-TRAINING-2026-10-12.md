# 饮食与运动工具第一版

## 设计来源

先在原 Figma 文件中添加「12 · 饮食与运动工具 / 2026-10-12」，再实现主要页面代码。MCP 达到调用额度后，按用户授权，通过 Chrome 中的 Figma 编辑器与 Community Scripter 操作原生可编辑节点，沿用变量、字体和按钮组件。

设计页：https://www.figma.com/design/Ct4AworB8fYBPjpSVwWbRW?node-id=45-15

本地备份 `fittrace-food-training-2026-10-12.fig` 包含首次完成的 13 个页面。浏览器随后补充了添加记录入口、训练结果两个页面，并校对食品来源示例。最后浏览器提示修改待联网同步，尚未再次导出这两页的本地备份；云端同步不能视为已确认。

## 实现范围

- 饮食日记始终展示早餐、午餐、晚餐、加餐；选日期、热量汇总、按餐次添加，保存返回当天日记。
- 食品库搜索、分类、账户隔离的常用收藏、按克换算热量。首批 13 种基础食物来自 USDA 官方 API，明确生熟状态与来源链接。
- 保留手动填写、最近吃过、现有 AI 识别；Android 原生相机/系统相册双入口沿用本次较早的真机修复。
- 动作库首批 5 个动作，支持部位、器械筛选和文字要点、ACE 来源链接；没有内置动作视频。
- 3 个预设训练模板，自建和编辑模板，组数、次数/秒、重量、组间休息配置。
- 每周七天安排模板或休息，按登录账户保存到后端。
- 模板训练/自由训练，逐组完成状态、休息计时、账户隔离的本地草稿；结束确认只保存完成的组，并在训练详情展示。

## 数据变更

新增训练模板、周计划表，训练记录增加可选 exercises JSON。旧训练记录保留原流程。新接口均要求 JWT，模板读写和计划引用验证当前账户。

接口：GET/POST /training/templates，PATCH /training/templates/:id，GET/PUT /training/plan。训练记录原 POST/PATCH /workouts 接口增加可选 exercises。

迁移文件：`apps/api/prisma/migrations/20261012030000_training_tools/migration.sql`。

发布时必须先按项目部署流程备份数据库、执行 Prisma migrate deploy、部署新 API 和 shared 构建产物，再发布 H5/APK。shared 增加 require 导出供 Nest CommonJS 使用；项目要求 Node >=22.12。此轮没有执行数据库迁移、生产部署或安装这一版 APK。

## 验证

- `pnpm check` 通过；现有 UiInput.vue 有一项属性顺序 warning，未改动该文件。
- shared、API、H5 构建通过；H5 按生产 API 地址构建并关闭本次 source-map 上传。
- `node --test apps/api/test/*.test.mjs apps/h5/test/*.test.mjs`：165 项通过。
- 新测试覆盖份量换算、训练完成组过滤、嵌套参数校验、模板所有权、非法动作、重复星期和其他账户模板引用。
- 独立内存接口的 390×844 浏览器验收：午餐食品库选择 150 g 熟米饭得到 195 kcal，保存回四餐日记；预设训练完成一组 12 次后详情仅显示这一组；周计划选择模板并保存。属于模拟接口 UI 验证，不能代替真实数据库/API 联调。
- 相机、相册打开和取消的真机验证来自本轮较早的 APK 修复；新运动工具尚未真机验证，AI 实际识别上传也未提交。

现有未跟踪的旧 UI/登录压缩包和设计文件未改动；未提交 Git。
