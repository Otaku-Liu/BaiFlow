# 08 · Android iOS 风格设计系统

> 状态：**核心组件已实现**（2026-08-06）；分段控件/开关待后续按需补充
> 相关：`docs/05-android.md`、`docs/glossary.md`

Android 客户端统一为**类 iOS 简约风**，组件样式在 `styles_ios.xml` 集中定义，所有布局引用同一 `@style`，**样式改一次全局生效**。

## 1. 设计要点

- **基于 Java + XML Views，不重写。**
- **自建集中式 iOS 风格设计系统**：组件样式在 `styles_ios.xml` 集中定义，所有布局引用同一 `@style`，**样式改一次全局生效**——解决"每个按钮手改"的痛点。
- 覆盖**全套组件**（按钮 / 输入框 / 卡片列表 / 开关 / 分段控件 / 弹窗 / 标题栏 / 空状态 / 标签 / 头像）。

## 2. 设计系统结构

### 2.1 主题（`themes.xml`，已部分就位）

Apple 色板：#007AFF 主色、#1D1D1F 主文字、#F5F5F7 背景、#86868B 次要文字、#E5E5EA 分割线；圆角统一 8–12dp。

### 2.2 `styles_ios.xml`（新增，集中定义组件样式）

| 组件 | 样式 | 说明 |
|---|---|---|
| 按钮 | `Ios.Button` + `Primary / Text / Danger` | 全宽或常规、12dp 圆角、#007AFF 主变体、#FF3B30 危险变体 |
| 输入框 | `Ios.TextInput` | Outlined、12dp 圆角、聚焦蓝 |
| 卡片/列表项 | 直接复用 `bg_card` / `bg_list_item` drawable | 白底、12dp 圆角（不单独设样式） |
| 开关 | `Ios.Switch` | iOS 风格 track/thumb（待补充） |
| 分段控件 | `Ios.Segmented` | 自定义 `SegmentedControl` View（待补充） |
| 弹窗 | `Ios.Dialog`（已落地，见 §2.6） | 圆角、白底、统一按钮：统一走 `MaterialAlertDialogBuilder` + `materialAlertDialogTheme`（16dp 圆角全局生效），不设单独 `@style/Ios.Dialog` |
| 标题栏 | `Ios.Header` + `.Title`（居中）+ `.BackLabel`（上一级名） | 居中标题 + 左侧 chevron+上一级名，无阴影 |
| 编辑器工具栏 | `Ios.ToolbarButton`（文本式按钮）+ `Ios.ToolbarRow`（容器行） | 随手记块编辑器工具栏，激活态由代码改文字颜色 |

### 2.3 drawable

卡片/列表：`bg_card`、`bg_list_item`、`bg_avatar`、`bg_role_tag`；弹层：`bg_dropdown_rounded`（下拉菜单）；点击涟漪：`bg_ripple_rounded`（单卡片）/ `bg_ripple_top` / `bg_ripple_bottom`（多行卡片首/末行）；图标：`ic_back_chevron`、`ic_folder`、`ic_nav_*`、`ic_type_*`。

### 2.4 自定义组件（`ui/widget/`）

- `SegmentedControl`（iOS 分段控件）
- `CupertinoSwitch`（iOS 风格开关，必要时）
- `DropdownMenu`（统一下拉菜单，替代系统 `PopupMenu`，2026-08-26）：固定宽度、每行 44dp、行间**整行**浅灰分隔线（`@color/divider`）、可选左侧 √（选中项显示**黑色**/未选中占位）与右侧图标槽（排序方向箭头，20dp）。背景 **白底 + 细边框 `#E5E5EA` + 投影**（`bg_dropdown_rounded`，投影由 elevation 提供），与白色卡片/工具栏区分。全 app 下拉（新建/排序/块类型/插入）统一走它；原 `Ios.PopupMenu` 样式与 `bg_popup_rounded` 已移除

项目**没有**底部动作面板（ActionSheet）组件：确认类交互统一走 `MaterialAlertDialogBuilder`（见 §2.6 弹窗），选择类交互走 `DropdownMenu`。

### 2.5 渐进落地

各界面布局逐步改为引用 `@style/Ios.*`，移除硬编码色值/圆角；不改变网络与业务逻辑。

### 2.6 实现状态（2026-08-06）

已落地：

- `res/values/styles_ios.xml`：`Ios` 基样式 + 按钮（`Ios.Button` / `.Primary` / `.Text` / `.Danger`）、输入框 `Ios.TextInput`、标题栏 `Ios.Header`（`.Title` 居中 / `.BackLabel` 上一级名）、编辑器工具栏（`Ios.ToolbarButton` / `Ios.ToolbarRow`，2026-08-06 随手记编辑器新增）
- 应用到现有界面：服务器配置、传输任务、预览、文件、我的、**登录**（输入框/按钮）、**随手记编辑器**（工具栏）
- 返回按钮 = chevron + 上一级名，无按压反馈；标题栏无阴影

**样式清理**（2026-08-06）：删除未使用的 `Ios.Button.Tonal`、`Ios.Card`、`Ios.Header.Back`（空样式）与 `bg_ripple_middle.xml`；所有界面统一改用 `@style/Ios.*`，无 Material 默认控件样式残留。

待后续按需补充：开关 `Ios.Switch`、分段控件 `Ios.Segmented`（`ui/widget/SegmentedControl`）。

**弹窗 `Ios.Dialog`（已落地，2026-08-26）**：全项目 13 处 `AlertDialog.Builder` 统一改为 `MaterialAlertDialogBuilder`，读取 `themes.xml` 的 `materialAlertDialogTheme`（`ThemeOverlay.BaiFlow.Dialog` + `ShapeAppearance.BaiFlow.Dialog`，16dp 圆角）全局生效；同时移除语义错配的 `alertDialogTheme` 配置（两者语义不同：前者供 `MaterialAlertDialogBuilder` 读取，后者供 appcompat `AlertDialog.Builder` 读取，混用会导致弹窗不套 shape、显示直角）。

**新增组件规范**：新组件样式一律加入 `styles_ios.xml`（`@style/Ios.*`），布局通过 `@style` 引用，不写硬编码色值/圆角，延续本文的 iOS 设计。

**头像编辑带（已落地，2026-08-27）**：「修改资料」页头像改为 96dp 居中圆形，圆内**贴底一条 24dp 半透明黑色带**（`avatar_edit_bg` `#99000000` + `avatar_edit_text` 白字 12sp「编辑」），底层照片隐约透出。

- **裁切实现**：色带是 `match_parent` 的**矩形**，靠父容器 `clipToOutline="true"` 被圆形 outline 裁成弓形——不需要自定义 View 或弧形 drawable。父容器 `background` 必须留给 `bg_avatar`（oval，提供 outline），水波纹改挂 `foreground`（`?android:attr/selectableItemBackgroundBorderless`），两者不能都占 `background`。
- **交互**：整圆（含色带）是**单一点击入口**，点击后**直接拉起系统图片选择器**（`ActivityResultContracts.GetContent`，无需运行时权限），不弹中间层菜单。
- **上传中反馈**：色带兼作状态位，上传期间文案换 `mine_avatar_uploading`「上传中…」并 `setEnabled(false)` 禁用整圆，**压缩失败 / 接口失败 / 网络失败三条分支都必须还原**。
- **提交语义**：头像为**即时上传**（选图即传，Toast 反馈），展示名称走「保存」按钮——两者语义不同是有意为之，未统一。
- **Android 端不提供删除头像**：`ProfileActivity.deleteAvatar()`、`ApiClient.deleteAvatar()` 与 `mine_delete_avatar` / `mine_avatar_deleted` / `mine_avatar_delete_failed` 三条中英文案均已移除。服务端 `DELETE /api/auth/avatar` 与 Web 端入口保留不变。
- 「我的」页 56dp 头像为纯展示，**不加编辑带**，入口是整行 `rowProfile`。
- 原右下角圆形徽标方案（`bg_edit_badge`）已废弃删除。

**点击涟漪（ripple）位置规则**：单卡片用 `bg_ripple_rounded`（四角圆角）；**多行卡片**的行涟漪按位置选——首行 `bg_ripple_top`（上圆下直）、末行 `bg_ripple_bottom`（下圆上直）；若出现中间行，需新增全直角涟漪（`bg_ripple_middle`，当前无用已删除、按需重建）。保证点击高亮与卡片圆角曲率一致。

## 3. 组件清单（全套）

1. **按钮**：primary / tonal / text / danger 四种变体，全宽 + 常规尺寸
2. **输入框**：圆角 Outlined、聚焦高亮
3. **卡片 / 列表项**：圆角白卡片 + 类型图标 + 间距
4. **开关**：iOS 风格
5. **分段控件**：`SegmentedControl`
6. **弹窗 / 确认框**：圆角、统一按钮
7. **标题栏**：返回头（‹）+ 标题
8. **空状态 / 加载**：图标 + 文案
9. **标签**：隐私 / 角色 tag
10. **头像**：圆形；可编辑态在圆内贴底叠半透明「编辑」弓形带

## 4. 范围与边界

- **本期**：纯 UI 层样式重构，不动网络/传输/业务逻辑。
- **不做**：iOS 毛玻璃/动态效果、3D Touch、真实 UIKit 外观——这些需 Compose Cupertino 或 Flutter。
- **远期**：若需要真 iOS 组件，迁 Compose Cupertino（本方案不采用）。

## 5. 文档同步（实现时）

- `docs/05-android.md`：补「设计系统」章节，登记 `styles_ios.xml` 与组件清单。
- `docs/glossary.md`：补「设计系统」「SegmentedControl」「Compose Cupertino」等词条。
