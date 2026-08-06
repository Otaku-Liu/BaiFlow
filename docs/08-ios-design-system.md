# 08 · Android iOS 风格设计系统（ADR）

> 状态：**核心组件已实现**（2026-08-06）；分段控件/开关待后续按需补充
> 类型：架构决策记录（ADR）
> 相关：`docs/05-android.md`、`docs/glossary.md`

## 1. 背景

用户希望把 Android 界面统一为**类 iOS 简约风**，并避免每个控件手改样式。

调研结论：

- **XML Views 技术栈没有"拿来即用的完整 iOS 组件库"**——Android 的设计语言是 Material，iOS 外观只能靠主题化。
- **真正的 iOS 组件库需换框架**：Compose Cupertino（Kotlin + Compose 全量重写）、Flutter（换语言重写）。
- 当前 app 是小型 Java + XML Views 应用，重写代价远大于收益。

## 2. 决策

- **留在 Java + XML Views，不重写。**
- **自建集中式 iOS 风格设计系统**：组件样式在 `styles.xml` 集中定义，所有布局引用同一 `@style`，**样式改一次全局生效**——解决"每个按钮手改"的痛点。
- 覆盖**全套组件**（按钮 / 输入框 / 卡片列表 / 开关 / 分段控件 / 弹窗 / 标题栏 / 空状态 / 标签 / 头像）。

## 3. 组件库评估（备选方案）

| 方案 | 结论 | 理由 |
|---|---|---|
| Compose Cupertino（`io.github.alexzhirkevich:cupertino`） | 不选 | Java→Kotlin/Compose 全量重写，风险高；还原度最高，远期可选 |
| Flutter Cupertino | 不选 | 换语言重写，代价最大 |
| Material Components 主题化 + 集中 `styles` | **采纳** | 不重写；样式集中定义、全局继承 |

## 4. 设计系统结构

### 4.1 主题（`themes.xml`，已部分就位）

Apple 色板：#007AFF 主色、#1D1D1F 主文字、#F5F5F7 背景、#86868B 次要文字、#E5E5EA 分割线；圆角统一 8–12dp。

### 4.2 `styles_ios.xml`（新增，集中定义组件样式）

| 组件 | 样式 | 说明 |
|---|---|---|
| 按钮 | `Ios.Button` + `Primary / Text / Danger` | 全宽或常规、12dp 圆角、#007AFF 主变体、#FF3B30 危险变体 |
| 输入框 | `Ios.TextInput` | Outlined、12dp 圆角、聚焦蓝 |
| 卡片/列表项 | 直接复用 `bg_card` / `bg_list_item` drawable | 白底、12dp 圆角（不单独设样式） |
| 开关 | `Ios.Switch` | iOS 风格 track/thumb（待补充） |
| 分段控件 | `Ios.Segmented` | 自定义 `SegmentedControl` View（待补充） |
| 弹窗 | `Ios.Dialog` | 圆角、白底、统一按钮（待补充） |
| 标题栏 | `Ios.Header` + `.Title`（居中）+ `.BackLabel`（上一级名） | 居中标题 + 左侧 chevron+上一级名，无阴影 |
| 编辑器工具栏 | `Ios.ToolbarButton`（文本式按钮）+ `Ios.ToolbarRow`（容器行） | 随手记富文本编辑器工具栏，激活态由代码改文字颜色 |

### 4.3 drawable

卡片/列表：`bg_card`、`bg_list_item`、`bg_avatar`、`bg_role_tag`；点击涟漪：`bg_ripple_rounded`（单卡片）/ `bg_ripple_top` / `bg_ripple_bottom`（多行卡片首/末行）；图标：`ic_back_chevron`、`ic_folder`、`ic_nav_*`、`ic_type_*`。

### 4.4 自定义组件（`ui/widget/`）

- `SegmentedControl`（iOS 分段控件）
- `CupertinoSwitch`（iOS 风格开关，必要时）

### 4.5 渐进落地

各界面布局逐步改为引用 `@style/Ios.*`，移除硬编码色值/圆角；不改变网络与业务逻辑。

## 4.6 实现状态（2026-08-06）

已落地：

- `res/values/styles_ios.xml`：`Ios` 基样式 + 按钮（`Ios.Button` / `.Primary` / `.Text` / `.Danger`）、输入框 `Ios.TextInput`、标题栏 `Ios.Header`（`.Title` 居中 / `.BackLabel` 上一级名）、编辑器工具栏（`Ios.ToolbarButton` / `Ios.ToolbarRow`，2026-08-06 随手记编辑器新增）
- 应用到现有界面：服务器配置、传输任务、预览、文件、我的、**登录**（输入框/按钮）、**随手记编辑器**（工具栏）
- 返回按钮 = chevron + 上一级名，无按压反馈；标题栏无阴影

**样式清理**（2026-08-06）：删除未使用的 `Ios.Button.Tonal`、`Ios.Card`、`Ios.Header.Back`（空样式）与 `bg_ripple_middle.xml`；所有界面统一改用 `@style/Ios.*`，无 Material 默认控件样式残留。

待后续按需补充：开关 `Ios.Switch`、分段控件 `Ios.Segmented`（`ui/widget/SegmentedControl`）、弹窗 `Ios.Dialog`。

**新增组件规范**：新组件样式一律加入 `styles_ios.xml`（`@style/Ios.*`），布局通过 `@style` 引用，不写硬编码色值/圆角，延续本 ADR 的 iOS 设计。

**点击涟漪（ripple）位置规则**：单卡片用 `bg_ripple_rounded`（四角圆角）；**多行卡片**的行涟漪按位置选——首行 `bg_ripple_top`（上圆下直）、末行 `bg_ripple_bottom`（下圆上直）；若出现中间行，需新增全直角涟漪（`bg_ripple_middle`，当前无用已删除、按需重建）。保证点击高亮与卡片圆角曲率一致。

## 5. 组件清单（全套）

1. **按钮**：primary / tonal / text / danger 四种变体，全宽 + 常规尺寸
2. **输入框**：圆角 Outlined、聚焦高亮
3. **卡片 / 列表项**：圆角白卡片 + 类型图标 + 间距
4. **开关**：iOS 风格
5. **分段控件**：`SegmentedControl`
6. **弹窗 / 确认框**：圆角、统一按钮
7. **标题栏**：返回头（‹）+ 标题
8. **空状态 / 加载**：图标 + 文案
9. **标签**：隐私 / 角色 tag
10. **头像**：圆形

## 6. 范围与边界

- **本期**：纯 UI 层样式重构，不动网络/传输/业务逻辑。
- **不做**：iOS 毛玻璃/动态效果、3D Touch、真实 UIKit 外观——这些需 Compose Cupertino 或 Flutter。
- **远期**：若需要真 iOS 组件，迁 Compose Cupertino（本 ADR 不采用）。

## 7. 文档同步（实现时）

- `docs/05-android.md`：补「设计系统」章节，登记 `styles_ios.xml` 与组件清单。
- `docs/glossary.md`：补「设计系统」「SegmentedControl」「Compose Cupertino」等词条。
