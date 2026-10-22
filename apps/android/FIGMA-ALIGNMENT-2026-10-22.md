# 原生 Android 设计对齐核对 · 2026-10-22

本轮只处理 `apps/android`，在 main 分支按功能分别验证、提交。H5 和服务端未修改；已有 README、IMPLEMENTATION、网络配置、图标替换、deploy 与设计素材等未提交内容仍保留。没有推送、安装 APK 或操作手机。

本文件记录本轮实际源码和验证结果。旧 `IMPLEMENTATION-2026-10-20.md` 中的待办并不都代表当前缺口，例如运动批量保存、照片专用归档、饮食工具和力量草稿已在本轮补齐。

## 设计依据与已落地范围

运动主稿通过 Figma MCP 读取上下文和截图。随后 MCP 达到 Starter 额度，继续用内置浏览器只读查看力量、照片和饮食主稿；没有因为额度限制停止，也没有修改 Figma 节点。交接资料来自 `docs/design/final/README.md`。

| 流程 / Figma 节点 | 本轮实现与核对 | 功能提交 |
| --- | --- | --- |
| 运动 `196:4795` | 搜索、基础分类与自定义、真实分钟数字录入、连续加入、清单修改/移除、核对保存、历史日期/时间、详情编辑与删除；保存逐条确认，部分失败保留未成功条目 | `144d331` |
| 力量 `214:4365` | 左侧分类动作库、连续选择、重量/次数或秒/组数数字面板、差异组、完成勾选、常用动作、组间休息、分页读取真实上次完成组、账号与日期隔离的本机草稿、模板创建/更新及恢复原草稿 | `44b8d5c`、`c066d7e` |
| 力量失败状态 / 每周计划 | 草稿失败提示与退出保护、写入恢复后清除失败态；未知或暂不可用模板显示明确状态，不误显示为休息，未加载完成的计划禁用保存 | `73b2d99`、`4a71fe5` |
| 照片 `221:4910` | 双列月归档、角度筛选、分页、首屏失败/空/刷新；归档背景上的录入弹层、选择/校验/上传、显式保存、失败重试、详情/删除确认、图片自身加载失败重试 | `1b3cfdc`、`5284d09` |
| 照片筛选状态 | 进入月归档清除隐藏的单日筛选；录入弹层的背景保持照片月份上下文 | `035910d`、`5284d09` |
| 饮食 `163:4320` 与二级 `222:64214` | 餐食图片上传、编辑时保留、明确移除后向 PATCH 发送 null；AI 文字/已上传照片识别、核对与修改后加入、最近记录复用、常用餐创建/复用/确认移除 | `5144ef3`、`719ff81` |
| 饮食识别状态 | 识别过程中禁止弹层隐藏，结束后恢复退出；模拟或空来源结果明确标注并禁止加入清单 | `97d67d8` |

此前已完成的首页、趋势、档案、身体和饮食基础流程没有重复重做；本轮完整验证包含它们的模块回归测试。登录、资料、目标等页面没有新增逐页人工验收声明。

## 数据与交互约束

- 所有业务请求沿用 `RecordsRepository` → `DefaultRecordsRepository` → `RecordsApi`，由 `AppContainer` 装配；feature 没有自行创建网络客户端。
- 未知热量、营养或份量保持 null / 待补充，没有套用稿件示例热量。AI 结果保留来源标记，`provider=mock` 或空来源不能加入餐食；真实服务未配置时展示返回的失败。
- 复用最近或常用餐只加入食物，保留本次日期、餐次、备注和图片。AI 加入后仍需显式保存；照片也先上传、再明确保存记录。
- 训练初始值是可编辑计划组，加入时不勾选完成；正式记录只包含实际勾选完成的组，按秒动作不写成次数。编辑已有力量记录保留组数据。
- 上次训练按现有运动历史 API 继续分页查找，服务端按 startedAt / createdAt 降序排列；带入后重新勾选，读取过程中修改过的动作不被旧响应覆盖。
- 照片角度筛选针对已加载条目；有剩余页时保留“加载更多”，不会将尚未读取的数据称为无记录。

## 验证与截图

最终执行：

```sh
cd apps/android
JAVA_HOME=/Library/Java/JavaVirtualMachines/liberica-jdk-21.jdk/Contents/Home ./gradlew verify :feature:main:lintDebug :app:assembleProdDebug --offline --no-daemon --no-configuration-cache --no-build-cache
```

完整验证通过，包括格式检查、模块依赖检查、JVM/Compose 测试、Lint 和 dev/prod Debug APK 构建。

| 模块 | 测试数 | 失败 / 错误 / 跳过 |
| --- | ---: | --- |
| core/network | 25 | 0 / 0 / 0 |
| core/data | 26 | 0 / 0 / 0 |
| feature/auth | 18 | 0 / 0 / 0 |
| feature/main | 82 | 0 / 0 / 0 |
| app（devDebug） | 5 | 0 / 0 / 0 |
| 合计 | **156** | **0 / 0 / 0** |

新增交互测试覆盖明确保存、失败保留、未知热量、部分提交重试、历史日期、草稿恢复、收藏恢复、上次训练分页、模板更新、照片筛选/删除/取消退出、餐食图片保留/移除，以及 AI 核对、模拟状态与识别中退出保护。测试使用受控 Repository 或 MockWebServer，验证接口合同与客户端行为。

已查看浅色390和深色320的代表性截图：

| 流程 | 产物目录（相对 apps/android） |
| --- | --- |
| 运动选择、数字面板、核对和详情 | `feature/main/build/reports/workout-screenshots/` |
| 力量清单、逐组修改、数字面板与模板 | `feature/main/build/reports/strength-screenshots/` |
| 照片归档、录入弹层、详情、空状态 | `feature/main/build/reports/photo-screenshots/` |
| 饮食 AI / 模拟 / 常用失败状态 | `feature/main/build/reports/meal-tools-screenshots/` |
| 餐食照片浅色编辑 / 深色320卡片 | `feature/main/build/reports/meal-screenshots/meal-photo-edit-390.png`、`meal-photo-dark-320.png` |

图片渲染测试使用仓库摄影素材作为受控测试输入，截图不代表真实用户照片或餐食。Robolectric 使用兼容的 BitmapFactory 解码器核对本地图片成功显示，同时保留独立的失败/重试路径；生产图片加载未替换为测试实现。弹层截图来自实际窗口，非仅 Activity decorView。

APK：`app/build/outputs/apk/prod/debug/app-prod-debug.apk`。

SHA-256：`af2f8bdf228b6e7aa79da9e659dd3bed727c8937894f3210bac33e5970c2796c`。

## 仍需区分的边界

- 这是按功能与代表状态的源码、交互和截图核对，**不是113页/452个版本全部实现或全部验收的声明**。
- 运动距离需要服务端字段，运动 AI 需要相应接口。当前使用实际分钟和手动记录，基础运动目录可用自定义补充，没有新增无法持久化的公里或虚假识别入口。
- Android 输入、日期/时间、提示与确认框已统一到 core/designsystem；系统文件选择器及部分逐组数字表单保留对应平台或业务交互，没有声明与全部静态画板逐像素一致。
- 真实账号 API、真实 AI 模型效果、真实图片服务器、系统进程回收恢复、后台休息提醒与手机交互尚未验收；当前休息计时支持页面内显示和恢复，不含后台通知。
- 指纹登录继续按此前计划延期，没有混入本轮。

## 真机反馈修复补充

- `e0e2c53`：体重标尺随拖动连续平移，固定中心指针对应当前体重；松手对齐到 0.1 kg。测试直接比较拖动前后标尺区域像素，并核对体重变化。
- `4165556`：按 Figma `163:4767` 的统一浅绿色圆角栏修正份量输入框，移除嵌套边框和浮动标签，数字居中；保留小数录入、25 g 加减及范围校验。已查看浅色390与深色320截图。
- `d5fbdf6`：添加和重新选择餐食照片时提供“拍照 / 从相册选择”。系统相机通过受限缓存 FileProvider 返回完整照片，取消时删除临时文件并保留原照片；相册取消同样保留原照片。来源选择后使用原有校验、上传和显式保存流程。

补充测试覆盖拍照取消后重新拍照、完整文件回调、Provider 非导出及临时 URI 授权、相册 MIME 类型与成功/取消，以及份量小数和双向加减。本次完整验证共150项测试通过；已核对 prod Debug 合并 Manifest 中 Provider 的实际 authority。未安装或操作手机，系统相机与相册真机验收仍待进行。

新增截图：`feature/main/build/reports/body-screenshots/body-ruler-drag-390.png`、`feature/main/build/reports/food-screenshots/food-portion-390.png`、`food-portion-dark-320.png`、`feature/main/build/reports/meal-screenshots/meal-photo-source-390.png`。

## 统一基础交互组件

组件入口与接入约定见 `core/designsystem/README.md`。新增 `FitTextField` / `FitStepperField`、`FitDatePickerDialog` / `FitTimePickerDialog`、`FitNotice` / `FitInlineMessage`、`FitDialog` / `FitDialogButton`。原 `FitMessage` 和资料页 `ProfileError` 复用同一提示实现。

登录、记录、训练、资料及设置的相关入口已迁移，业务模块不再自行调用 Material 输入框、日期时间选择器或 AlertDialog。字段键盘、密码显示、校验与只读行为保持；日期按场景分别限制记录不晚于今天、目标不早于今天。对话框统一宽度、圆角、间距、可滚动正文及底部操作，删除等操作使用语义警示色；只有显式确认才回传修改。

新增6项共享组件测试，覆盖闰日、非法日期与日期范围、时间边界、取消、长正文、大字体、深色窄屏和显式重试。完整156项测试通过，0失败/错误/跳过；同时运行 `verify :core:designsystem:lintDebug :feature:main:lintDebug :feature:auth:lintDebug :app:assembleProdDebug`。已查看 `feature/main/build/reports/shared-ui-screenshots/` 中日历、时间、手动日期、长确认框与输入/提示截图。未安装或操作手机，系统软键盘和真机显示仍待复测。
