# APK 0.1.5 弹层关闭修复

- 功能提交：1ff4102；versionCode 7，沿用正式签名覆盖安装。
- 根因：TDesign Mobile Vue 1.16.2 ActionSheet 取消和遮罩关闭触发 update:modelValue，而页面使用 v-model:visible。
- 修复：照片来源、模板动作、训练动作选择使用 v-model；周计划选择监听 update:model-value。没有布局变化、数据库迁移或 API 变更。
- H5 类型检查、改动文件 ESLint、生产 H5/APK 构建通过。
- 线上静态发布目录：/opt/fittrace/releases/20261012-1ff4102，复用上一版 API；previous 指向 20261012-bcbe53e。
- 公网健康 OK；公开 APK 与本地安装包 SHA-256 一致：0dd3d5eb97e67eabc2907a063ee3f3c6b6c2de35fb24e3010f7b2350283cc7ab。
- ADB 安装 Success；手机确认 0.1.5/versionCode 7。
- 真机验证：打开照片来源 → 点击取消 → 弹层关闭、记饮食表单保留；再次打开成功；点击遮罩也关闭。未拍照、上传图片或保存记录。
