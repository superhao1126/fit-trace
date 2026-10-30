# 循形Fitness 微信小程序基础版

Vue 3 + TypeScript + uni-app，复用现有 NestJS API、PostgreSQL 和 `@fit-trace/shared`。本次选型取代原 weapp-vite / Wevu / TDesign 预留方案。

## 来源与版本

以 [feige996/unibest](https://github.com/feige996/unibest) `base` 分支提交 `dc3af8ddb99b78fa806078c80434b664a4b1ce93`（4.4.1）为工程底座，保留其 uni-app Vite 构建、SSR 入口、pages/manifest 配置生成方式及 MIT 许可证。审查时 `main` 为 `1b33720470f75bd135c1d69f59f45dd6ce93e201`，二者并非同一提交。

选择性接入，不复制模板演示页、后台上传脚本、Git 初始化、Husky、嵌套锁文件、重复 ESLint/Prettier 或多端依赖。本版按 Figma 的 Android CalJimi 风格接入腾讯官方 `@tdesign/uniapp@1.0.0`，通过 easycom 按需编译；不使用 MiniProgram Chat 或 Wot UI。

锁定 uni-app / 微信编译器 `3.0.0-4070620250821001`、Vue `3.4.21`、Vite `5.2.8`、TypeScript `5.9.3`、vue-tsc `3.0.6`、DCloud types `3.4.14`。uni-h5 的传递 Vue Router 固定 `4.5.1`，避免新版 router 要求 Vue 3.5。统一使用根 pnpm 12.10.1、workspace 和锁文件。安装不会运行模板初始化脚本。

## 本地运行

在此目录复制 `.env.example` 为 `.env`，填写 `VITE_API_BASE_URL`（包含 `/api/v1` 的 HTTPS 地址）与 `VITE_WECHAT_APP_ID`。AppID 不属于秘密。当前邮箱登录不需要 AppSecret；历史微信接口的 AppSecret 仅保留在服务端环境，不得使用 VITE 前缀或进入小程序。

从仓库根执行：

```sh
pnpm install --frozen-lockfile
pnpm dev:mp
pnpm build:mp
pnpm --filter @fit-trace/miniprogram typecheck
pnpm --filter @fit-trace/miniprogram test
```

微信开发者工具分别导入 `dist/dev/mp-weixin` 或 `dist/build/mp-weixin`。生产构建不要求真实凭据，也不会自动上传或发布。`scripts/bootstrap.mjs` 在 uni CLI 提前读取 manifest 前从配置源生成基础 JSON；Vite 插件再生成完整页面列表。不要手改 `src/pages.json`、`src/manifest.json`、`uni-pages.d.ts`。轮询监听兼容受限 macOS 环境。

API 域名必须配置到微信 request 合法域名，证书/备案及 HTTPS 转发由实际环境配置。本版不关闭域名检查。

## 已有流程

- 邮箱 + 6 位验证码登录；原邮箱直接访问原账号数据，新邮箱自动注册；自动恢复、续期和退出
- 今日计划、每周计划选择、模板和自由训练，复用账号原计划
- 动作说明、重量/次数/秒数、增减组、完成标记、上次成绩
- 按账号隔离持久草稿，切聊天/锁屏后通过结束时间校正页面休息倒计时，无后台通知
- 训练保存（账号内幂等键防止丢失响应后的重复提交）、分页历史、逐组详情、修改及删除；无逐组数据的旧记录支持基础字段修改
- 基础体重增删改查、我的及协议

## 邮箱登录与设计

小程序通过 `POST /auth/miniprogram/email-code` 发送登录验证码，通过 `POST /auth/miniprogram/login` 登录。小程序发送入口跳过阿里云验证，保留原有邮箱/IP 额度、60 秒冷却、10 分钟有效期及错误次数限制。旧 H5/Android 发码入口的验证策略不变。

使用原账号邮箱即可读取原训练数据，无需关联码或微信身份授权。旧微信会话缓存不再读取；重新使用邮箱登录，不迁移或合并原独立微信账号的数据。H5 账号设置的关联码入口已移除。

[Android 同风格 Figma 设计](https://www.figma.com/design/Ozw8Aukuy7wFgOyH1IBe1b?node-id=8-467) 覆盖 8 个核心页面和 4 种交互状态。详见 [设计交付](../../docs/design/miniprogram-email-ui-2026-10-30.md)。8 个核心画面已实现到小程序，包括同一路由中的邮箱登录/今日训练，以及独立每周计划、训练录入、历史、详情、体重和我的；协议页也采用相同主题。

## 数据库与验证边界

迁移 `apps/api/prisma/migrations/20261030090000_wechat_identity/migration.sql` 允许邮箱/密码为空并创建微信身份与关联码表、训练保存幂等键。保留已有邮箱账号字段及唯一约束。需先评审、备份，再由授权部署流程执行；开发过程中不对真实/共享数据库执行迁移。

既有迁移已在授权部署中应用；此次邮箱登录无需新迁移。构建与接口测试不能替代真实收信或微信真机验收。后续需验证合法域名、实际验证码收取、原账号登录、弱网保存、切聊天、锁屏、杀进程恢复、退出后重登及多账号隔离。

暂缓饮食 AI、身材照片、复杂趋势、微信运动、后台通知和多端打包。

## TDesign UI 实现与验证

组件采用 TDesign UniApp 的 Navbar、TabBar、Button、Input、Checkbox、Cell、Popup、Dialog、Search、Stepper 和 Loading。全局主题在 `src/styles/theme.css`，复用亮绿、浅绿、白色卡片和胶囊按钮。登录插画及小芽为 Figma 同源的安卓原始矢量，存放在 `src/static/brand/`；不依赖临时 Figma 资源链接。

输入校验、登录失败和验证码发送结果使用微信原生 `uni.showToast`，页面加载失败保留重试卡片。训练录入保持账号隔离草稿、前台计时校正与保存去重；详情默认摘要，可展开查看每组记录。体重曲线只使用实际 API 返回值，不生成演示数据。

生产构建、类型检查、ESLint、Oxlint、13 项小程序测试通过。微信开发者工具已查看正式登录页面；登录后画面通过 `/tmp/fittrace-mp-ui-review-20261030` 的独立编译副本及显式视觉样例检查，此副本不调用业务 API、不进入正式产物，也不代表真实账号或真机验收。此前 `shadow-grey.png` 的开发者工具资源错误仍可见，来源不属于本轮业务页面；没有用它判断业务失败。

开发者工具打开的正式目录仍为仓库内 `dist/build/mp-weixin`。本轮不需要服务器更新，未上传或发布微信小程序代码。真实邮箱收信、原账号登录及真机训练仍需实际使用验收。
