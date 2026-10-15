# Android 开发日志

## 查看方式

1. 选择 `devDebug` 或 `prodDebug`，重新构建并运行应用。本次未新增依赖。
2. 打开 Android Studio **View → Tool Windows → Logcat**，选择设备和应用。
3. 搜索框输入 `tag:FitTrace.Http`，再在手机操作登录或发送验证码。

两个 Debug 变体启用完整参数日志；`prodRelease` 通过 `BuildConfig.DEBUG=false` 关闭。日志只输出到本地 Logcat，不写文件或上传外部平台；未接入远程崩溃上报，也不替换系统默认崩溃处理。

## 输出内容

根据用户要求，Debug 的 URL、查询参数、请求正文及服务端响应正文均明文输出，密码、邮箱验证码、验证凭据、Token 等 JSON 字段不隐藏。请求头和响应头不输出；因此 Authorization、Cookie 等头部字段不会额外打印。

每条记录带进程内递增编号，同一请求和响应使用同一编号；JSON 自动缩进，长行分段打印，避免 Logcat 单条限制。示例中的字段均为虚构值：

```text
#1 --> POST http://10.0.3.54:3000/api/v1/auth/login/email
#1 {
#1     "email": "demo@example.com",
#1     "emailCode": "123456",
#1     "acceptedTerms": true
#1 }
#1 <-- 201 POST http://10.0.3.54:3000/api/v1/auth/login/email (128ms)
#1 {
#1     "code": "OK",
#1     "message": "请求成功",
#1     "data": {
#1         "accessToken": "demo-access-token",
#1         "refreshToken": "demo-refresh-token"
#1     }
#1 }
```

请求、正常 HTTP 响应使用 Debug；HTTP 400 及以上和网络 IO 失败使用 Warning。网络失败输出异常类型，不额外打印异常消息及堆栈。耗时从开始到收到响应头或网络失败计算，不包含响应预览和 JSON 解析。

HTTP 200 仍需结合打印的业务 code 判断是否成功。网络层不会将业务拒绝伪装成传输失败，页面继续按数据层映射结果显示。

## 读取边界

- JSON、表单、文本等常规参数输出完整内容。无 Content-Type 的响应也尝试按文本读取。
- 单个正文上限 64 KiB，超过时输出提示并跳过正文；不是隐藏其中某些字段。
- 不重放一次性或双向流请求体；请求长度未知时跳过预览。
- 二进制、文件和 event-stream 响应跳过预览，避免损坏上传/流式行为。
- 响应用 peekBody 预览，保留原响应供 Retrofit 解析。预览或日志接收器异常不替换原接口结果。

## 实现与验证

`core:network` 保持纯 JVM，提供结构化 NetworkLogEvent、分行格式和 OkHttp 拦截器；`app/di/AppContainer` 只在 Debug 注入 Android Logcat 输出。默认客户端及 Release 不添加日志拦截器。

本地回归覆盖请求与响应明文字段、完整 URL 和查询参数、响应原文仍可读取、长行分段、超大响应与非法 JSON、一次性请求体不重复写入、网络错误传播、日志接收器故障不影响请求及默认关闭。设备上的真实 Logcat 展示由用户重新安装 Debug 包后确认，本轮不操作手机。

验证通过：59 项本地测试、格式/模块边界检查、Lint 与 devDebug/prodDebug/prodRelease 构建；APK 检查确认 Debug 保留日志、Release 移除。未操作设备。
