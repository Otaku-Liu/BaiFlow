# 07 · Android iOS 风格设计系统

> 相关：`docs/05-android.md`、`docs/glossary.md`

Android 客户端统一为**类 iOS 简约风**。基础是 Java + XML Views（**不重写**）；组件样式在 `res/values/styles_ios.xml` 集中定义，所有布局引用同一 `@style/Ios.*`——**样式改一次全局生效**。新增组件一律加进 `styles_ios.xml` 并按 `@style` 引用，不写硬编码色值 / 圆角。

## 主题与色板

`themes.xml`：`#007AFF` 主色、`#1D1D1F` 主文字、`#F5F5F7` 背景、`#86868B` 次要文字、`#E5E5EA` 分割线；圆角统一 8–12dp。

## 样式（`styles_ios.xml`）

| 组件 | 样式 | 说明 |
|---|---|---|
| 按钮 | `Ios.Button` + `Primary` / `Text` / `Danger` | 全宽或常规、12dp 圆角、`#007AFF` 主变体、`#FF3B30` 危险变体 |
| 输入框 | `Ios.TextInput` | Outlined、12dp 圆角、聚焦蓝 |
| 标题栏 | `Ios.Header` + `.Title`（居中）+ `.BackLabel`（上一级名） | 返回 = chevron + 上一级名，无按压反馈，无阴影 |
| 编辑器工具栏 | `Ios.ToolbarButton` / `Ios.ToolbarRow` | 随手记块编辑器，激活态由代码改文字颜色 |
| 卡片 / 列表项 | 直接复用 drawable，不单独设样式 | `bg_card` / `bg_list_item`：白底、12dp 圆角 |
| 空状态 / 加载 | 无独立样式 | 图标 + 文案 |
| 开关 | `Ios.Switch` | 待补充 |
| 分段控件 | `Ios.Segmented` | 待补充（`ui/widget/SegmentedControl`） |

**弹窗**：统一走 `MaterialAlertDialogBuilder`，由 `themes.xml` 的 `materialAlertDialogTheme`（`ThemeOverlay.BaiFlow.Dialog` + `ShapeAppearance.BaiFlow.Dialog`，16dp 圆角）全局生效；不用 appcompat `AlertDialog.Builder`（不套 shape、显直角）。注意 `materialAlertDialogTheme` 与 `alertDialogTheme` 语义不同、**不可混用**，配错即显直角。

## drawable

- 卡片 / 列表：`bg_card`、`bg_list_item`、`bg_avatar`、`bg_role_tag`
- 弹层：`bg_dropdown_rounded`；图标：`ic_back_chevron`、`ic_folder`、`ic_nav_*`、`ic_type_*`
- 点击涟漪：单卡片 `bg_ripple_rounded`（四角圆角）；多行卡片按行位置选——首行 `bg_ripple_top`（上圆下直）、末行 `bg_ripple_bottom`（下圆上直），若出现中间行需新增全直角 `bg_ripple_middle`。保证点击高亮与卡片圆角曲率一致

## 自定义组件（`ui/widget/`）

- `SegmentedControl`（iOS 分段控件）、`CupertinoSwitch`（iOS 风格开关，必要时）
- `DropdownMenu`（统一下拉菜单，替代系统 `PopupMenu`）：固定宽度、每行 44dp、行间整行浅灰分隔线（`@color/divider`）、可选左侧 √（选中黑 / 未选中占位）与右侧图标槽（排序方向箭头 20dp）；背景白底 + 细边框 `#E5E5EA` + 投影（`bg_dropdown_rounded`），与白色卡片区分。全 app 下拉（新建 / 排序 / 块类型 / 插入）统一走它

项目**没有** ActionSheet 组件：确认类交互走 `MaterialAlertDialogBuilder`，选择类交互走 `DropdownMenu`。

## 头像编辑带（「修改资料」页）

96dp 居中圆形，圆内**贴底 24dp 半透明黑带**（`avatar_edit_bg` `#99000000` + 白字 12sp「编辑」），底层照片隐约透出。

- **裁切**：色带是 `match_parent` 矩形，靠父容器 `clipToOutline="true"` 被圆形 outline 裁成弓形——不需要自定义 View 或弧形 drawable。父容器 `background` 必须留给 `bg_avatar`（oval，提供 outline），水波纹改挂 `foreground`（`?android:attr/selectableItemBackgroundBorderless`），两者不能都占 `background`
- **交互**：整圆（含色带）是单一点击入口，点击后直接拉起系统图片选择器（`ActivityResultContracts.GetContent`，无需运行时权限），不弹中间层菜单
- **上传中反馈**：色带兼作状态位，文案换 `mine_avatar_uploading`「上传中…」并 `setEnabled(false)` 禁用整圆；压缩失败 / 接口失败 / 网络失败三条分支都必须还原
- **提交语义**：头像为即时上传（选图即传，Toast 反馈），展示名称走「保存」按钮——两者语义不同是有意为之，未统一
- Android 端不提供删除头像；服务端 `DELETE /api/users/me/avatar` 与 Web 端入口保留
- 「我的」页 56dp 头像为纯展示，不加编辑带，入口是整行 `rowProfile`

## 范围与边界

- 纯 UI 层样式重构，不动网络 / 传输 / 业务逻辑
- **不做**：iOS 毛玻璃与动态效果、3D Touch、真实 UIKit 外观——这些需 Compose Cupertino 或 Flutter
- **远期**：若需要真 iOS 组件，迁 Compose Cupertino（本方案不采用）
