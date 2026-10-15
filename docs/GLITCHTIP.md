# GlitchTip 错误监控

FitTrace H5 使用 `@sentry/vue` 捕获 Vue 组件错误、未处理异常和 Promise 拒绝。SDK 在 `apps/h5/src/main.ts` 挂载应用之前初始化，性能事务采样率为 1%，移除 `BrowserSession` 集成。当前不包含 NestJS 服务端错误监控。

诊断事件移除用户信息、Cookie、请求体、请求头与请求 URL 查询参数；关闭控制台和 DOM 面包屑，Vue 错误不附带组件 props。

## 构建配置

在仓库根目录本地 `.env` 或 CI 环境配置：

```env
VITE_GLITCHTIP_DSN=https://74f021de9c014807a656c8f160bc2e05@glitchtip.moshangl.cn/2
VITE_GLITCHTIP_ENABLED=true
VITE_GLITCHTIP_RELEASE=
SENTRY_URL=https://glitchtip.moshangl.cn
SENTRY_ORG=yuwenovo
SENTRY_PROJECT=fittrace
SENTRY_AUTH_TOKEN=你的上传令牌
```

上传令牌仅供 Vite 构建使用，不使用 `VITE_` 前缀，不写入源码或提交到 Git。曾写入源码的旧令牌需在 GlitchTip 后台撤销并替换本地 `.env` 中的值。

release 优先使用 `VITE_GLITCHTIP_RELEASE`，其次使用 `SENTRY_RELEASE`；都为空时取 Git 提交标识（含未提交修改标记），非 Git 环境回退到包版本。Vite 将同一个值注入运行时代码并用于上传，H5 与 source map 共用该版本。

## H5 构建

仅生成 H5 发布目录：

```bash
pnpm build:h5
```

Vite 自动注入 Debug ID、创建发布记录并上传 source map，成功后删除 `.map`。上传失败会使构建失败并保留映射文件；排除失败原因后重新执行构建即可。启用监控时缺少令牌会直接报错。无需安装或手动运行 GlitchTip CLI。

H5 产物位于 `apps/h5/dist`。

设 `VITE_GLITCHTIP_ENABLED=false` 或将 DSN 留空可关闭监控和自动上传，此时构建不要求上传令牌。

## 验证

构建日志应包含 source map 上传成功提示。成功构建后，发布目录不含 `.map`。

此前已使用 H5 构建产物在内置浏览器触发合成未捕获异常，确认 [FITTRACE-1](https://glitchtip.moshangl.cn/yuwenovo/issues/5?project=2) 入库、release 关联及 SDK 堆栈的 source map 映射。业务代码堆栈还原仍需进一步验证。
