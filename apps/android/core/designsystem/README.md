# Android 统一交互组件

所有原生 Android 页面通过 `core/designsystem` 使用基础输入和反馈组件，浅色/深色读取 `FitTraceTheme`。业务模块只提供数据、范围、校验和回调，不设置独立输入框边框、圆角或确认框宽度。

| 场景 | 组件 | 约定 |
| --- | --- | --- |
| 表单、搜索、备注、密码、数字 | `FitTextField` | 16dp 圆角，52dp 最小高度，统一浅绿底、焦点边框、禁用与错误状态；保留键盘和密码可见性行为 |
| 加减数字输入 | `FitStepperField` | 同一圆角栏内数字居中、两侧加减；步长、上下限、单位和无障碍名称由业务提供 |
| 日期 | `FitDatePickerDialog` | 自适应七列日历 / YYYY-MM-DD 手动录入；非法或超范围日期不可确认；支持闰日与状态恢复 |
| 时间 | `FitTimePickerDialog` | 24 小时制，统一时/分输入框；只允许 00–23 时、00–59 分；明确确认才回传 |
| 页面失败、状态提示和重试 | `FitMessage` / `FitNotice` | 共用主题色、14dp 圆角、字号、间距及 LiveRegion 读屏通知；失败保留内容，重试需要显式点击 |
| 字段校验提示 | `FitInlineMessage` | 同一提示组件的紧凑错误变体，显示在对应输入附近 |
| 提示框、确认框、弹窗表单 | `FitDialog` | 统一 24dp 圆角、宽度、内边距、标题及可滚动正文；操作区保持可达 |
| 弹窗按钮 | `FitDialogButton` | 主操作实心绿色、取消次要文本色；`destructive = true` 为警示色，支持禁用与忙碌保护 |

## 已接入

- 登录/注册/密码验证的输入、校验、提示、协议与安全验证弹窗。
- 首页日期、身体/饮食/运动/照片的记录日期和时间。
- 目标日期；记录仍禁止未来日期，目标仍禁止过去日期。
- 身体补充、备注、食物编辑/搜索/份量、运动搜索/自定义、力量表单和模板、昵称/资料/目标输入。
- 删除、退出登录、放弃修改、替换模板、常用餐移除，以及餐食照片来源选择等弹窗。
- 页面失败与重试、各字段校验提示。

体重标尺、力量数字键盘等直接操作控件保留各自业务交互；其校验提示使用同一基础组件。日期范围不会在共享组件中被固定成某个业务的规则。

## 使用示例

```kotlin
FitTextField(value, onValueChange, label = { Text("名称") }, singleLine = true)
FitDatePickerDialog(date, onDismiss, onSave, maxDate = LocalDate.now())
FitTimePickerDialog(time, onDismiss, onSave)
FitMessage("保存失败，输入已保留", onRetry, error = true)
FitDialog(
    onDismissRequest = onDismiss,
    title = { Text("删除这条记录？") },
    text = { Text("删除后无法恢复。") },
    confirmButton = {
        FitDialogButton(onClick = onDelete, destructive = true) { Text("确认删除") }
    },
    dismissButton = {
        FitDialogButton(onClick = onDismiss, primary = false) { Text("取消") }
    },
)
```

`SharedUiComponentsTest` 在真实 Compose 组件上覆盖闰日、范围限制、非法日期、24 小时时间边界、取消不提交、长正文、大字体、深色窄屏及显式重试。其截图输出到 `feature/main/build/reports/shared-ui-screenshots/`。已有页面测试继续检查原流程，系统软键盘及手机实际表现仍需真机复测。
