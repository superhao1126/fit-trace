# 循形Fitness V0.1 产品需求与技术设计文档

> 本文档是 循形Fitness 当前唯一有效的 V0.1 产品与技术设计文档。
> Codex 应以本文档作为项目初始化、架构设计和后续开发的主要依据。

---

# 1. 项目概述

## 1.1 项目名称

**循形Fitness**

Git 仓库名称：

```text
fit-trace
```

---

## 1.2 产品定位

循形Fitness 是一个面向个人用户的：

**身体数据 + 饮食 + 训练 + 身材变化长期记录工具。**

核心不是简单地“记体重”或者“记饮食”，而是建立个人长期健康数据轨迹：

```text
记录
 ↓
趋势
 ↓
对比
 ↓
洞察
```

用户可以持续记录：

* 体重
* 体脂率
* 腰围等身体指标
* 每日饮食
* 训练情况
* 身材照片

系统通过时间维度将这些数据关联起来，让用户能够看到：

> 我最近吃了什么、练了什么，以及身体发生了什么变化。

---

# 2. V0.1 产品目标

V0.1 的核心目标不是构建完整健身平台，而是验证：

**用户是否愿意持续记录，并通过趋势看到身体变化。**

因此 V0.1 聚焦以下能力：

1. 用户登录
2. 身体数据记录
3. 身体数据趋势
4. 饮食记录
5. AI 辅助识别食物
6. 训练记录
7. 身材照片记录
8. Dashboard 汇总
9. 7 / 30 / 90 天趋势分析

暂不实现：

* 社区
* 好友
* 排行榜
* 教练系统
* 商城
* 会员体系
* 支付
* 复杂营养计划
* 自动生成训练计划
* 社交分享
* 多人协作
* Apple Health / Health Connect
* 微信运动同步
* 原生 App

---

# 3. 产品原则

循形Fitness V0.1 遵循以下原则。

## 3.1 记录必须简单

任何高频记录操作都应该尽可能少步骤完成。

例如记录体重：

```text
首页
 ↓
点击记录
 ↓
输入体重
 ↓
保存
```

避免复杂表单。

---

## 3.2 趋势比单次数据重要

循形Fitness 不强调：

```text
今天 70.2kg
```

而强调：

```text
过去 30 天

72.4kg
 ↓
70.2kg

-2.2kg
```

---

## 3.3 数据关联

核心数据必须能够通过日期建立联系：

```text
日期
├── 身体数据
├── 饮食
├── 训练
└── 身材照片
```

未来才能分析：

```text
饮食
+
训练
+
身体变化
```

---

## 3.4 AI 是辅助输入工具

AI 在 V0.1 中主要用于：

**降低饮食记录成本。**

例如：

```text
拍摄午餐
 ↓
上传图片
 ↓
AI 识别
 ↓
鸡胸肉
米饭
西兰花
 ↓
用户确认 / 修改
 ↓
保存
```

AI 识别结果不能直接作为最终数据。

**必须允许用户确认和修改。**

---

# 4. 技术架构

整体架构：

```text
                  循形Fitness

                     │
          ┌──────────┴──────────┐
          │                     │
       Vue 3 H5              NestJS API
          │                     │
          │                 PostgreSQL
          │                     │
          └────── REST API ─────┘
                                │
                       ┌────────┴────────┐
                       │                 │
                  AI Provider      Storage Provider
```

未来增加：

```text
              循形Fitness API
                   │
          ┌────────┴────────┐
          │                 │
       Vue 3 H5       微信小程序
```

H5 与微信小程序共享：

* 后端 API
* 数据模型
* 业务规则
* TypeScript 类型
* Design Tokens
* 部分工具函数

---

# 5. Git 仓库与 Monorepo 规范

## 5.1 Git 仓库数量

循形Fitness **只创建一个 Git 仓库**。

仓库名称：

```text
fit-trace
```

整个项目只允许存在：

```text
fit-trace/.git
```

禁止：

```text
apps/h5/.git
apps/api/.git
apps/miniprogram/.git
```

也就是说：

```text
一个产品
=
一个 Git Repository
=
一个 pnpm Monorepo
```

H5、API、未来微信小程序均属于同一个 Git 仓库。

---

## 5.2 Monorepo

使用：

```text
pnpm workspace
```

管理整个项目。

根目录：

```text
fit-trace/
```

应用统一放置于：

```text
apps/
```

公共代码统一放置于：

```text
packages/
```

---

## 5.3 Git 提交原则

一个业务功能涉及前后端时，应允许在一个 Commit 中完整提交。

例如：

```text
feat(meal): implement meal record flow
```

该 Commit 可以同时修改：

```text
apps/h5
apps/api
packages/shared
```

不要人为拆成前端仓库 Commit 和后端仓库 Commit。

推荐 Conventional Commits：

```text
feat:
fix:
refactor:
chore:
docs:
test:
```

例如：

```text
feat(body): add body record
feat(meal): implement meal records
feat(workout): add workout tracking
feat(photo): add progress photos
fix(auth): refresh expired token
refactor(api): extract storage provider
chore: initialize monorepo
```

---

# 6. 前端技术栈

H5：

```text
Vue 3
TypeScript
Vite
Vue Router
Pinia
Axios
SCSS
ECharts
dayjs
TDesign Vue Next
```

使用 Composition API：

```text
<script setup lang="ts">
```

---

# 7. CSS 与 UI 规范

## 7.1 CSS

使用：

```text
SCSS
+
CSS Variables
+
Scoped Style
```

不使用：

```text
UnoCSS
Tailwind CSS
```

避免大量 Utility Class。

---

## 7.2 Design Tokens

全局维护基础 Design Tokens：

```scss
:root {
  --color-primary: ...;
  --color-success: ...;
  --color-warning: ...;
  --color-danger: ...;

  --color-text-primary: ...;
  --color-text-secondary: ...;

  --color-background: ...;
  --color-surface: ...;

  --border-radius-sm: ...;
  --border-radius-md: ...;
  --border-radius-lg: ...;

  --spacing-xs: ...;
  --spacing-sm: ...;
  --spacing-md: ...;
  --spacing-lg: ...;
}
```

后续微信小程序应尽量复用同样的设计变量。

---

# 8. UI 组件策略

核心原则：

> 简单组件使用原生 HTML + SCSS，复杂交互组件使用 TDesign。

不使用：

```text
Vant
```

---

## 8.1 原生实现

以下组件优先自行实现：

```text
Button
Card
Badge
Tag
Avatar
Divider
Empty
Skeleton
Section
StatCard
PageContainer
```

例如：

```html
<button class="ft-button">
  保存
</button>
```

---

## 8.2 TDesign Vue Next

复杂交互使用：

```text
TDesign Vue Next
```

例如：

```text
DatePicker
Calendar
Popup
Dialog
ActionSheet
Upload
Slider
Picker
Toast
Loading
```

不要为了使用组件库而使用组件库。

---

# 9. 微信小程序兼容策略

循形Fitness V0.1 先开发：

```text
Vue 3 H5
```

微信小程序基础版选型（2026-10-30 更新，取代旧预留方案）：

```text
unibest（uni-app）
+
Vue 3 + TypeScript
+
uni-app 基础组件（可按需接入 Wot UI）
```

小程序当前认证方式为邮箱 + 6 位验证码，使用原邮箱直接读取原账号数据；不使用关联码或微信 code 登录。专用发码入口不要求阿里云验证，继续复用后端限流、验证码有效期与错误次数限制。UI 以当前 Android 的 CalJimi 亮绿风格为准，详见 [小程序 Figma 设计交付](design/miniprogram-email-ui-2026-10-30.md)。

因此 H5 阶段应避免：

* Vant 强绑定
* Tailwind 强绑定
* UnoCSS 强绑定
* 大量依赖浏览器特性的 UI 架构
* 将业务逻辑直接写死在 DOM 中

尽量保持：

```text
UI
 ↓
业务逻辑
 ↓
API
```

之间的边界。

未来：

```text
H5
TDesign Vue Next

小程序
uni-app 基础组件（可按需接入 Wot UI）
```

保持相似视觉语言。

---

# 10. 后端技术栈

后端：

```text
Node.js
NestJS
TypeScript
Prisma
PostgreSQL
JWT
class-validator
class-transformer
```

API 风格：

```text
RESTful API
```

统一前缀：

```text
/api/v1
```

---

# 11. 页面路由

H5 页面：

```text
/login

/dashboard

/body
/body/create
/body/history

/meals
/meals/create
/meals/:id

/workouts
/workouts/create
/workouts/:id

/photos
/photos/create

/profile
```

---

# 12. 页面结构

底部主导航：

```text
首页
记录
趋势
我的
```

建议：

```text
/dashboard
/record
/trends
/profile
```

其中：

```text
记录
```

作为聚合入口：

```text
身体数据
饮食
训练
身材照片
```

---

# 13. Dashboard

首页主要展示：

## 今日状态

```text
今日体重

70.2 kg

较上次
-0.3kg
```

---

## 最近趋势

展示：

```text
7天
30天
90天
```

切换。

指标：

```text
体重
体脂率
腰围
```

---

## 今日记录

```text
早餐 ✓
午餐 ✓
晚餐 -
训练 ✓
身体数据 ✓
```

---

## 快速记录

```text
记录身体数据
记录饮食
记录训练
上传身材照片
```

---

# 14. 身体数据

用户可以记录：

```text
体重
体脂率
腰围
胸围
臀围
备注
```

其中：

```text
体重
```

为主要指标。

其他字段允许为空。

---

# 15. 身体趋势

支持：

```text
7 天
30 天
90 天
```

展示：

```text
体重趋势
体脂趋势
腰围趋势
```

图表：

```text
ECharts Line Chart
```

统计：

```text
当前值
起始值
变化值
最高值
最低值
平均值
```

---

# 16. 饮食记录

饮食类型：

```text
BREAKFAST
LUNCH
DINNER
SNACK
```

用户可以：

```text
手动添加
拍照 AI 识别
```

---

# 17. AI 饮食识别

流程：

```text
拍照 / 上传图片
        ↓
上传 Storage
        ↓
获得图片 URL
        ↓
AI Provider
        ↓
识别食物
        ↓
返回结构化结果
        ↓
用户确认 / 修改
        ↓
保存 MealRecord
```

AI 返回：

```json
{
  "foods": [
    {
      "name": "鸡胸肉",
      "estimatedAmount": "150g",
      "estimatedCalories": 250
    }
  ]
}
```

AI 结果只作为：

```text
suggestion
```

不得自动写入最终记录。

---

# 18. AI Provider 抽象

禁止业务代码直接依赖某一家 AI 服务。

定义：

```ts
interface FoodRecognitionProvider {
  recognize(imageUrl: string): Promise<FoodRecognitionResult>
}
```

业务层只依赖：

```text
FoodRecognitionProvider
```

未来可以实现：

```text
OpenAI Provider
其他视觉模型 Provider
Mock Provider
```

---

# 19. 文件 Storage 抽象

同样禁止业务代码绑定具体对象存储。

定义：

```ts
interface StorageProvider {
  upload(file: Buffer, options?: UploadOptions): Promise<UploadResult>
  delete(key: string): Promise<void>
}
```

未来可实现：

```text
LocalStorageProvider
COSStorageProvider
OSSStorageProvider
S3StorageProvider
```

开发环境可以首先实现：

```text
LocalStorageProvider
```

---

# 20. 训练记录

训练记录：

```text
训练类型
训练名称
开始时间
持续时间
消耗热量（可选）
备注
```

训练类型：

```text
STRENGTH
CARDIO
RUNNING
CYCLING
SWIMMING
OTHER
```

V0.1 不做：

```text
动作库
组数
次数
重量历史
训练计划
```

这些以后扩展。

---

# 21. 身材照片

用户可以上传：

```text
正面
侧面
背面
其他
```

类型：

```text
FRONT
SIDE
BACK
OTHER
```

照片记录：

```text
日期
照片
类型
备注
```

未来用于：

```text
30 天对比
90 天对比
Before / After
```

V0.1 先完成记录与查看。

---

# 22. 数据模型

## User

```text
id
email
passwordHash
nickname
avatarUrl
createdAt
updatedAt
```

---

## BodyRecord

```text
id
userId

weight
bodyFat
waist
chest
hip

recordedAt
note

createdAt
updatedAt
```

---

## MealRecord

```text
id
userId

type
recordedAt
note

imageUrl

createdAt
updatedAt
```

---

## FoodItem

```text
id
mealRecordId

name
amount
calories

aiGenerated

createdAt
updatedAt
```

---

## WorkoutRecord

```text
id
userId

type
name

startedAt
durationMinutes
calories

note

createdAt
updatedAt
```

---

## ProgressPhoto

```text
id
userId

type
imageUrl

recordedAt
note

createdAt
updatedAt
```

---

# 23. 数据关系

```text
User
 │
 ├── BodyRecord[]
 │
 ├── MealRecord[]
 │      │
 │      └── FoodItem[]
 │
 ├── WorkoutRecord[]
 │
 └── ProgressPhoto[]
```

所有业务数据必须关联：

```text
userId
```

---

# 24. Prisma 规范

Prisma Schema：

```text
apps/api/prisma/schema.prisma
```

迁移：

```text
apps/api/prisma/migrations
```

禁止：

* 手工维护数据库结构
* 绕过 Prisma Migration 修改正式数据库结构

Schema 变更必须通过 Migration。

---

# 25. API 设计

统一前缀：

```text
/api/v1
```

---

# 26. Auth API

注册：

```http
POST /api/v1/auth/register
```

登录：

```http
POST /api/v1/auth/login
```

当前用户：

```http
GET /api/v1/auth/me
```

---

# 27. Body API

新增：

```http
POST /api/v1/body-records
```

列表：

```http
GET /api/v1/body-records
```

详情：

```http
GET /api/v1/body-records/:id
```

修改：

```http
PATCH /api/v1/body-records/:id
```

删除：

```http
DELETE /api/v1/body-records/:id
```

---

# 28. Body Trend API

```http
GET /api/v1/body-records/trends
```

参数：

```text
range=7d
range=30d
range=90d
```

例如：

```text
/api/v1/body-records/trends?range=30d
```

返回：

```json
{
  "weight": [],
  "bodyFat": [],
  "waist": []
}
```

---

# 29. Meal API

新增：

```http
POST /api/v1/meals
```

列表：

```http
GET /api/v1/meals
```

详情：

```http
GET /api/v1/meals/:id
```

修改：

```http
PATCH /api/v1/meals/:id
```

删除：

```http
DELETE /api/v1/meals/:id
```

---

# 30. AI Recognition API

```http
POST /api/v1/ai/food-recognition
```

参数：

```json
{
  "imageUrl": "..."
}
```

返回：

```json
{
  "foods": []
}
```

---

# 31. Workout API

```http
POST   /api/v1/workouts
GET    /api/v1/workouts
GET    /api/v1/workouts/:id
PATCH  /api/v1/workouts/:id
DELETE /api/v1/workouts/:id
```

---

# 32. Progress Photo API

```http
POST   /api/v1/progress-photos
GET    /api/v1/progress-photos
GET    /api/v1/progress-photos/:id
DELETE /api/v1/progress-photos/:id
```

---

# 33. Upload API

```http
POST /api/v1/uploads
```

使用：

```text
multipart/form-data
```

返回：

```json
{
  "url": "...",
  "key": "..."
}
```

---

# 34. Dashboard API

```http
GET /api/v1/dashboard
```

返回：

```json
{
  "latestBodyRecord": {},
  "bodyChanges": {},
  "today": {
    "bodyRecorded": true,
    "meals": {
      "breakfast": true,
      "lunch": true,
      "dinner": false
    },
    "workoutRecorded": true
  }
}
```

Dashboard 不应该由 H5 请求大量 API 后自行拼装。

由后端提供聚合接口。

---

# 35. API 返回格式

所有 `/api/v1` JSON 接口统一包含 `code`、`message`、`data`、`meta` 四个字段。

- 成功：`code` 固定为 `OK`，`message` 为 `请求成功`。
- 数据在 `data` 内，删除成功、没有最新记录等无数据情况为 `null`。
- 分页列表的 `data` 是数组，`meta` 包含 `page`、`pageSize`、`total`；其他返回的 `meta` 为 `null`。
- 错误保留业务错误码和中文提示，`data`、`meta` 都为 `null`。
- HTTP 状态码保留其语义：查询/更新/删除成功为 200，创建及现有 POST 成功为 201；失败使用 400、401、404、409、413、500、502、503 等对应状态码。
- 参数校验返回 `VALIDATION_ERROR`，`message` 为第一条校验提示；未捕获异常返回 `INTERNAL_ERROR`，不向客户端暴露堆栈和内部异常详情。
- 健康检查同样使用统一格式，状态位于 `data.status`。
- 上传返回的是图片地址和 key，使用统一 JSON 格式；`/uploads/...` 图片静态资源保留原始文件响应。

成功：

```json
{
  "code": "OK",
  "message": "请求成功",
  "data": {},
  "meta": null
}
```

列表：

```json
{
  "code": "OK",
  "message": "请求成功",
  "data": [],
  "meta": {
    "page": 1,
    "pageSize": 20,
    "total": 0
  }
}
```

错误：

```json
{
  "code": "BODY_RECORD_NOT_FOUND",
  "message": "身体记录不存在",
  "data": null,
  "meta": null
}
```

控制器内部使用共享的 `ApiPayload` / `ApiListPayload` 返回数据，全局响应拦截器补齐成功字段，异常过滤器负责错误格式。客户端使用 `ApiResponse` / `ApiListResponse` / `ApiErrorResponse` 类型，保持 `response.data.data` 和 `response.data.meta` 的取值路径。

---

# 36. 时间规范

数据库统一保存：

```text
UTC
```

API 使用：

```text
ISO 8601
```

例如：

```text
2026-09-18T06:30:00.000Z
```

前端根据用户本地时区展示。

---

# 37. 身份认证

使用：

```text
JWT
```

请求：

```http
Authorization: Bearer <token>
```

后端所有个人数据查询必须基于：

```text
currentUser.id
```

禁止：

```text
客户端传 userId
```

然后直接查询。

例如：

```text
GET /body-records?userId=123
```

这种设计禁止。

---

# 38. 数据安全

所有：

```text
BodyRecord
MealRecord
WorkoutRecord
ProgressPhoto
```

必须验证资源属于当前登录用户。

例如：

```text
DELETE /body-records/:id
```

必须验证：

```text
record.userId === currentUser.id
```

---

# 39. Monorepo 最终目录

项目根目录：

```text
fit-trace/
│
├── apps/
│   │
│   ├── h5/
│   │   ├── src/
│   │   │   ├── api/
│   │   │   ├── assets/
│   │   │   ├── components/
│   │   │   ├── composables/
│   │   │   ├── layouts/
│   │   │   ├── router/
│   │   │   ├── stores/
│   │   │   ├── styles/
│   │   │   │   ├── tokens.scss
│   │   │   │   ├── variables.scss
│   │   │   │   ├── mixins.scss
│   │   │   │   └── global.scss
│   │   │   ├── types/
│   │   │   ├── utils/
│   │   │   ├── views/
│   │   │   ├── App.vue
│   │   │   └── main.ts
│   │   ├── index.html
│   │   ├── package.json
│   │   ├── tsconfig.json
│   │   └── vite.config.ts
│   │
│   ├── api/
│   │   ├── prisma/
│   │   │   ├── schema.prisma
│   │   │   └── migrations/
│   │   ├── src/
│   │   │   ├── auth/
│   │   │   ├── users/
│   │   │   ├── body-records/
│   │   │   ├── meals/
│   │   │   ├── workouts/
│   │   │   ├── progress-photos/
│   │   │   ├── dashboard/
│   │   │   ├── ai/
│   │   │   ├── uploads/
│   │   │   ├── providers/
│   │   │   │   ├── ai/
│   │   │   │   └── storage/
│   │   │   ├── common/
│   │   │   │   ├── decorators/
│   │   │   │   ├── filters/
│   │   │   │   ├── guards/
│   │   │   │   ├── interceptors/
│   │   │   │   └── pipes/
│   │   │   ├── app.module.ts
│   │   │   └── main.ts
│   │   ├── package.json
│   │   └── tsconfig.json
│   │
│   └── miniprogram/
│       └── README.md
│
├── packages/
│   └── shared/
│       ├── src/
│       │   ├── constants/
│       │   ├── types/
│       │   ├── utils/
│       │   └── index.ts
│       └── package.json
│
├── docs/
│   └── PRD.md
│
├── .gitignore
├── .editorconfig
├── .git/
├── package.json
├── pnpm-workspace.yaml
├── pnpm-lock.yaml
└── README.md
```

注意：

```text
apps/miniprogram
```

V0.1 不进行正式开发。

可以只保留：

```text
README.md
```

用于说明未来技术方案。

---

# 40. pnpm Workspace

根目录：

```yaml
packages:
  - "apps/*"
  - "packages/*"
```

根 `package.json`：

```json
{
  "name": "fit-trace",
  "private": true
}
```

整个 Monorepo 使用：

```text
一个 pnpm-lock.yaml
```

禁止：

```text
apps/h5/pnpm-lock.yaml
apps/api/pnpm-lock.yaml
```

---

# 41. packages/shared 原则

`packages/shared` 用于真正可以跨应用复用的代码，例如：

```text
TypeScript Types
Enums
Constants
纯函数 Utils
API 公共类型
```

不要为了 Monorepo 而过度抽象。

V0.1 阶段：

> 能留在应用内部的代码先留在应用内部。

只有真正出现共享需求后，再迁移到：

```text
packages/shared
```

尤其未来开发：

```text
apps/miniprogram
```

时，再逐步抽取 H5 与小程序的共享逻辑。

---

# 42. 环境变量

根目录提供：

```text
.env.example
```

API：

```text
DATABASE_URL=
JWT_SECRET=

AI_PROVIDER=
AI_API_KEY=

STORAGE_PROVIDER=
```

禁止提交：

```text
.env
```

到 Git。

---

# 43. 开发命令

目标：

```bash
pnpm install
```

安装整个 Monorepo。

开发 H5：

```bash
pnpm dev:h5
```

开发 API：

```bash
pnpm dev:api
```

同时启动：

```bash
pnpm dev
```

构建：

```bash
pnpm build
```

Lint：

```bash
pnpm lint
```

Type Check：

```bash
pnpm typecheck
```

---

# 44. V0.1 开发阶段

不要让 Codex 一次性实现全部功能。

按照以下 Phase 顺序开发。

---

## Phase 1：项目基础架构

目标：

**项目能够运行。**

实现：

```text
Git Repository
pnpm workspace
Vue 3 H5
NestJS API
Prisma
PostgreSQL
ESLint
Prettier
SCSS
Vue Router
Pinia
Axios
基础目录
环境变量
Health Check
```

同时建立：

```text
apps/h5
apps/api
packages/shared
docs
```

要求：

```text
pnpm install
```

可以成功。

```text
pnpm dev
```

可以同时启动 H5 和 API。

API：

```http
GET /api/v1/health
```

返回：

```json
{
  "status": "ok"
}
```

---

## Phase 2：Auth

实现：

```text
User
注册
登录
JWT
AuthGuard
/me
登录页
Token 管理
Axios interceptor
```

---

## Phase 3：身体数据

实现：

```text
BodyRecord
新增
编辑
删除
列表
历史记录
最新数据
```

这是第一个完整业务闭环。

---

## Phase 4：身体趋势

实现：

```text
7 天
30 天
90 天

体重
体脂
腰围
```

前端：

```text
ECharts
```

后端：

```text
Trend API
```

---

## Phase 5：饮食记录

实现：

```text
MealRecord
FoodItem
早餐
午餐
晚餐
加餐
手动添加食物
```

先完成：

```text
手动记录
```

不要一开始接 AI。

---

## Phase 6：文件上传

实现：

```text
StorageProvider
LocalStorageProvider
Upload API
```

打通：

```text
H5 → API → Storage
```

---

## Phase 7：AI 饮食识别

实现：

```text
FoodRecognitionProvider
AI Provider
图片识别
结构化 FoodItem
用户确认
用户修改
保存
```

---

## Phase 8：训练记录

实现：

```text
WorkoutRecord
新增
编辑
删除
列表
详情
```

---

## Phase 9：身材照片

实现：

```text
ProgressPhoto
上传
列表
删除
```

---

## Phase 10：Dashboard

最后实现：

```text
今日状态
最新身体数据
趋势摘要
饮食完成情况
训练情况
快速记录
```

Dashboard 应建立在前面模块已经稳定的基础上。

---

# 45. Codex 开发原则

Codex 必须遵守：

## 原则 1

不要一次实现整个 PRD。

严格按照：

```text
Phase 1
 ↓
Phase 2
 ↓
Phase 3
...
```

逐步开发。

---

## 原则 2

每个 Phase 完成后：

```text
Lint
Type Check
Build
```

必须通过。

---

## 原则 3

不要自行增加大型依赖。

如果原生实现足够：

```text
优先原生
```

---

## 原则 4

不要安装：

```text
Vant
UnoCSS
Tailwind CSS
```

---

## 原则 5

复杂 UI：

```text
TDesign Vue Next
```

简单 UI：

```text
HTML
+
SCSS
```

---

## 原则 6

后端遵循 NestJS 模块化结构：

```text
Controller
 ↓
Service
 ↓
Repository / Prisma
```

Controller 不写复杂业务逻辑。

---

## 原则 7

外部服务必须抽象 Provider。

例如：

```text
AI
Storage
```

禁止业务代码直接依赖具体厂商 SDK。

---

## 原则 8

不要过度设计。

V0.1 优先：

```text
可运行
可维护
可扩展
```

而不是：

```text
复杂架构
大量抽象层
提前设计未来功能
```

---

# 46. Git 工作规范

默认主分支：

```text
main
```

功能开发建议：

```text
feat/auth
feat/body-record
feat/body-trends
feat/meals
feat/uploads
feat/ai-food-recognition
feat/workouts
feat/progress-photos
feat/dashboard
```

一个功能可以同时修改：

```text
H5
API
shared
```

因为它们属于同一个 Repository。

---

# 47. V0.1 验收标准

用户可以完成完整流程：

```text
注册
 ↓
登录
 ↓
记录体重
 ↓
持续记录
 ↓
查看 7 / 30 / 90 天趋势
```

同时可以：

```text
记录饮食
 ↓
上传饮食照片
 ↓
AI 识别
 ↓
修改 AI 结果
 ↓
保存
```

以及：

```text
记录训练
```

和：

```text
上传身材照片
```

Dashboard 可以汇总：

```text
今天记录了什么
最近身体有什么变化
```

---

# 48. V0.1 成功标准

V0.1 成功不等于功能数量多。

真正需要验证的是：

```text
用户是否愿意持续记录？
```

以及：

```text
用户能否通过 循形Fitness
清晰看到自己的长期变化？
```

只要这两个问题得到验证，循形Fitness V0.1 就达到了目的。

---

# 49. Codex 当前任务

当前只执行：

# Phase 1：项目基础架构

不要实现：

```text
Auth
BodyRecord
Meal
Workout
ProgressPhoto
AI Recognition
Dashboard
```

Codex 当前需要：

1. 创建 `fit-trace` pnpm Monorepo。
2. 初始化 `apps/h5` Vue 3 + TypeScript + Vite。
3. 初始化 `apps/api` NestJS + TypeScript。
4. 创建 `packages/shared`。
5. 配置 pnpm workspace。
6. 配置根目录统一开发命令。
7. H5 配置 Vue Router、Pinia、Axios、SCSS。
8. 安装并配置 TDesign Vue Next，但暂不大量使用组件。
9. API 配置 Prisma 与 PostgreSQL。
10. 建立 `.env.example`。
11. 建立 `/api/v1/health`。
12. 配置 ESLint、Prettier、TypeScript。
13. 确保：

```bash
pnpm install
pnpm dev
pnpm lint
pnpm typecheck
pnpm build
```

能够正常执行。

14. 不实现任何 Phase 2 之后的业务功能。
15. 不创建多个 Git Repository。
16. `apps/h5`、`apps/api`、`packages/shared` 均由根目录 `fit-trace/.git` 统一管理。

Phase 1 完成后停止开发，输出：

```text
完成内容
目录结构
关键技术决策
运行命令
验证结果
遗留问题
下一阶段建议
```

等待人工确认后，再进入：

```text
Phase 2：Auth
```

---

# 50. 最终工程约束总结

循形Fitness 当前技术方案最终确定为：

```text
Git
└── 单 Repository：fit-trace

Monorepo
└── pnpm workspace

Frontend
├── Vue 3
├── TypeScript
├── Vite
├── Vue Router
├── Pinia
├── Axios
├── SCSS
├── ECharts
└── TDesign Vue Next

Backend
├── NestJS
├── TypeScript
├── Prisma
├── PostgreSQL
└── JWT

UI
├── 原生 HTML 优先
├── SCSS
├── CSS Variables / Design Tokens
└── 复杂组件使用 TDesign

禁止
├── Vant
├── UnoCSS
└── Tailwind CSS

Future MiniProgram
├── unibest（uni-app）
├── Vue 3 + TypeScript
└── uni-app 基础组件（可按需接入 Wot UI）

Repository
├── apps/h5
├── apps/api
├── apps/miniprogram（未来）
└── packages/shared
```

核心原则：

> **一个产品、一个 Git 仓库、一个 Monorepo。**

H5、API 和未来微信小程序都属于 循形Fitness，不拆分成独立 Git Repository。
