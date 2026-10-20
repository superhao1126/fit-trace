# 核心界面设计交付 · 2026-10-19

当前设计阶段的视觉、关键状态、深色、窄屏及点击演示已补齐。共有 **113 个页面/状态 × 390/320px × 浅/深色 = 452 个 SVG 版本**。这是设计交付，不代表 H5、Android、API 或真实账号流程已经实现、部署或验收。

## 看哪里

| 内容 | Figma / 本地 |
| --- | --- |
| 身体录入、补充、键盘、历史、详情、删除、空白与错误 10 屏 | [40 · 身体记录](https://www.figma.com/design/Ct4AworB8fYBPjpSVwWbRW?node-id=221-4365) · body-caljimi.svg |
| 照片归档、上传/保存、重试、详情、删除、空白与错误 8 屏 | [41 · 身材照片](https://www.figma.com/design/Ct4AworB8fYBPjpSVwWbRW?node-id=221-4910) · photos-caljimi.svg |
| 饮食核对/详情、模板、训练计划、密码、关于、协议等 14 屏 | [42 · 二级页](https://www.figma.com/design/Ct4AworB8fYBPjpSVwWbRW?node-id=222-64214) · other-light.svg |
| 113 个完整深色状态 | [43 · 深色](https://www.figma.com/design/Ct4AworB8fYBPjpSVwWbRW?node-id=222-64739) · dark-all.svg |
| 113 个 320px 浅色状态 | [44 · 窄屏](https://www.figma.com/design/Ct4AworB8fYBPjpSVwWbRW?node-id=222-71426) · narrow-all.svg |
| 113 个 320px 深色状态 | [45 · 深色窄屏](https://www.figma.com/design/Ct4AworB8fYBPjpSVwWbRW?node-id=222-78117) · narrow-dark-all.svg |
| 记录聚合、运动工具、自定义运动、邮箱验证/失效、保持目标、加载等 15 屏 | [46 · 补齐路由分支](https://www.figma.com/design/Ct4AworB8fYBPjpSVwWbRW?node-id=222-63808) · completion-light.svg |
| 身体保存 → 详情 → 返回，Figma 原生播放已点击验证 | [身体原型](https://www.figma.com/proto/Ct4AworB8fYBPjpSVwWbRW?node-id=221-25291&starting-point-node-id=221%3A25291&page-id=0%3A1&scaling=min-zoom) |
| 照片归档 → 添加 → 保存回归档，Figma 原生播放已点击验证 | [照片原型](https://www.figma.com/proto/Ct4AworB8fYBPjpSVwWbRW?node-id=221-25389&starting-point-node-id=221%3A25389&page-id=0%3A1&scaling=min-zoom) |
| 首页、饮食、训练、我的、目标、身体与照片等完整点击演示 | index.html · 当前本机 [打开原型](http://127.0.0.1:4178) |

全流程演示通过可编辑 SVG + 独立 HTML 实现；Figma 原生连线只覆盖上述身体、照片两个流程，共 4 条。不能将本地原型的全部连线称为已在 Figma 中建立。菜单可选择每个状态，顶部切换主题和宽度；演示控件不发送验证码、不上传照片、不调用业务接口。

## 设计依据与适配

- 主绿 #24C867 延续上一轮 CalJimi 截图采样近似值，保持分组白卡、绿色选中态、圆角输入及底部操作。
- 身体参考 [CalJimi 029 当前体重](https://uinotes.com/pin/335169353368659636)、[031 目标体重](https://uinotes.com/pin/152289517613816092) 的大数字与标尺。饮食、运动、资料等沿用前面已核对的 CalJimi 参考，来源见上级 CORE-DESIGN-PROGRESS.md。
- 照片搜索结果主要是食物照片编辑，未找到 CalJimi 身材照片归档的直接参考；身材照片按其分组表单、白卡与底部弹层适配。图中是标明“示例占位”的匿名矢量示意，不是用户照片或完整页面位图。
- 未见 CalJimi 深色参考；深色是循形语义适配。theme-tokens.json 为实际生成器使用的色值。Figma 现有深色基础变量也已同步，别名关系保留，回读记录见 dark-variable-values.txt。
- 主设计沿用已有能力：未知值显示 —；失败与无记录分开；历史日期传递；只保存已完成训练组；体重必填，其余身体字段选填；照片先上传后保存记录，失败保留选择；邮箱验证失效时清空授权和新密码并重新验证。
- 协议/隐私使用仓库 PolicyContent.vue 现有原文，原型“阅读全文”可滚动查看。未重新起草法律条款。版本、体重、日期、食物等均为示例。
- 旧 profile-screen-5/15/16 已被 39 号目标稿替代，不进入最终集合。饮食主流程的原生自动布局稿仍以 30 号为实施主稿；本目录补充其详情和跨主题演示。

## 路由与复用

| 当前路由 | 设计状态或复用规则 |
| --- | --- |
| /login | login-1…6；邮箱/密码模式、失败与键盘 |
| /dashboard | home-1…6；四个快捷记录入口，日期与失败状态 |
| /record | record-hub；二级聚合入口，不新增第五个底栏 |
| /trends、/archive | trends-1…12；趋势与按日档案 |
| /body/create、/body/:id/edit、/body/history、/body/:id | body、body-more、body-keyboard、body-history、body-detail 等；创建/修改复用预填表单 |
| /photos、/photos/:id | photos、photo-upload…photo-detail 等 |
| /meals、/meals/create、/meals/:id、/meals/:id/edit | 30 号原生稿与 meal…meal-detail；修改复用预填表单 |
| /workouts、/workouts/create、/workouts/:id、/workouts/:id/edit | sports-1…6、sports-custom、workout-detail；修改复用预填表单 |
| /training | training-tools |
| /training/exercises | strength-1 分类结构的动作库上下文 + exercise-detail；此上下文加入模板，训练中则加入当前清单 |
| /training/templates、创建与编辑模板 | strength-6、template-edit、template-empty、template-failed |
| /training/plan、/training/session | training-plan、strength-1…9；次数/按秒动作的字段标签按 exercise.unit 适配，不把时长写成次数或重量 |
| /profile、/profile/info、/settings/account | profile；新版头像直接进入编辑，资料查看与修改共用分组字段布局，邮箱只读 |
| /goal | goal-1…12 + goal-maintain；保持不套用增/减脂方向校验 |
| /settings、/settings/system | profile-6、profile-10；统一显示模式入口 |
| /settings/password | password、password-email、password-code-error、password-reset、password-expired、password-loadfail |
| /settings/about、协议与隐私（含登录外公开页） | about、agreement、privacy；复用相同正文 |

## 复现与检查

```sh
python3 docs/design/final/scripts/build.py
python3 docs/design/final/scripts/verify.py
python3 -m http.server 4178 --bind 127.0.0.1 --directory docs/design/final
```

生成器仅依赖 Python 标准库和仓库内源 SVG/资产。catalog.json 为索引，screens.json 为全部版本，figma-imports.json 为当前节点位置，prototype-links.json 为原生连线。模块级 SVG 保留便于分批编辑；最终集合见 *-all.svg。

验证：全部 SVG 解析、全部状态四版本齐全、别名/热点目标存在；320px 字体不小于 11px、估算右边界无溢出。图形、长文、字号与键盘已在编辑器和浏览器抽查；这不是所有手机/所有字体的运行证明。已实际点击本地身体输入、饮食核对保存、力量训练完成，以及 Figma 身体/照片流程。验证摘要见 validation.json，截图见本目录 PNG。

大部分新增状态是可编辑文字/矢量静态画板；原有原生组件与变量仍保留，但不能声称所有新增画板均已绑定组件或具有自动布局响应约束。320/390 是明确的布局稿，开发时用弹性布局、滚动容器与键盘避让实现。食品库 360 项营养/照片许可核验、业务源码实现、设备和生产验收均属于后续工作，不在本次设计交付中。
