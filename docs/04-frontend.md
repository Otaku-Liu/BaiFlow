# Web 前端设计

## 技术栈

Vue 3 + Vite + Vue Router + Pinia + Axios + Element Plus

## 页面结构

| 页面 | 路由 | 说明 |
|---|---|---|
| 登录 | `/login` | 用户名密码登录 |
| 主布局 | `/` | 侧边栏 + 顶栏 + 内容区，需登录 |
| 文件中心 | `/` 内 | 管理员用户切换、面包屑（上传时间）、文件列表（双击/按钮预览，无类型列）、上传/下载/重命名/删除、隐私文件夹 |
| 随手记 | `/` 内 | 笔记列表 + Vditor 编辑器（IR 即时渲染 + 工具栏含代码块按钮）、搜索、SSE 实时同步、跨设备续读进度、笔记媒体渲染（图片/音频，经 `?token=` 鉴权） |
| 分享管理 | `/` 内 | 分享链接创建/查看/撤销、访问日志（管理员） |
| 用户管理 | `/` 内 | 管理员可见：用户列表、创建/编辑、批量删除、重置密码 |
| 操作日志 | `/` 内 | 管理员可见：`el-sub-menu` 子菜单入口 |
| 登录日志 | `/` 内 | 管理员可见：分页表格，用户名模糊搜索、日期时间范围、登录结果筛选 |
| 个人资料 | 弹窗 | 展示名、头像上传、修改密码、登录设备管理（强制下线） |
| 预览抽屉 | Drawer | 按 MIME 路由：图片(`<img>`)、视频(`<video>`+进度)、音频(`<audio>`+进度)、PDF(`<iframe>`)、Markdown(showdown→HTML)、文本/代码(`<pre>`)、其他(降级下载；Office 文档归为此类) |

## 国际化 (i18n)

- **vue-i18n**：所有 UI 文本通过 `t('namespace.key')` 引用，语言包位于 `src/locales/`
- **Element Plus**：通过 `el-config-provider` 响应式切换组件语言
- **顶部语言切换**：右上角下拉框（中文 / English），写入 `localStorage.baiflow_locale`
- **Axios**：请求头自动带 `Accept-Language`，后端错误消息同步切换
- 数据库数据（文件名、用户名等）不翻译，仅翻译列名、按钮、提示等 UI 文案

## 状态管理

| Store | 职责 |
|---|---|
| `authStore` | token、用户信息、登录状态、`isAdmin` 判断 |
| `fileStore` | 当前 Storage Root、面包屑路径、文件列表、隐私令牌 |

## 组件与 Composables

| 文件 | 用途 |
|---|---|
| `components/ConfirmDialog.vue` | 基于 `el-dialog` 的通用确认弹窗，替代 `ElMessageBox.confirm`，确保所有弹窗样式统一 |
| `components/PreviewDrawer.vue` | 文件预览抽屉，按 MIME 类型路由到 5 种渲染器（img/video/audio/iframe/pre） |
| `views/NotesView.vue` | 随手记页：左侧笔记列表 + 右侧 Vditor 编辑器（IR 即时渲染，输出 Markdown 源；工具栏含自定义代码块按钮）、10s 自动保存 + 手动保存、编辑区滚动保存 SCROLL_PERCENT、SSE 收 NOTE_UPDATED 刷新列表/别端保存时未在编辑则同步正文、乐观并发冲突（覆盖/重载）；`rewriteMediaAuth()` 把 `/api/notes/media/{id}` 的 `<img>`/`<audio>` 追加 `?token=<会话token>`（复用 SSE 的 token 鉴权）|
| `composables/useConfirmDialog.js` | 提供 `confirm()` promise 式 API，搭配 `ConfirmDialog` 使用 |
| `composables/usePlaybackProgress.js` | 播放/阅读进度管理：查询历史进度、打开时**自动恢复位置**并提示「已恢复到上次观看位置」（不再弹跳转确认）、10s 自动保存、关闭时最终保存；滚动百分比按「滚动范围（scrollHeight - clientHeight）」计算，与 Android 一致 |
| `composables/useSse.js` | SSE 长连接封装：`EventSource` 连 `/api/events?token=<会话token>`，按事件名注册回调，组件卸载关闭 |
| `api/notes.js` | 笔记 CRUD + 阅读进度 API 封装 |
| `utils/mime.js` | 扩展名→MIME 映射表、MIME 主类型判定（image/video/audio/pdf/markdown/text/unknown）、预览支持判断、进度类型推断（SECONDS/PAGE/SCROLL_PERCENT） |
| `utils/format.js` | `formatDateTime`、`formatSize`、`formatSpeed` |

文件预览抽屉的 Markdown 渲染使用 showdown 库将源码转 HTML；随手记笔记编辑器使用 Vditor（IR 即时渲染，见上「随手记」）。

笔记媒体（图片/录音/画画）在正文中以 `/api/notes/media/{id}` 相对路径引用，浏览器 `<img>/<audio>` 带不了 `Authorization` 头，`NotesView.vue` 在渲染后把媒体 URL 追加 `?token=<当前会话token>`（后端 `SessionAuthenticationFilter` 已支持 `?token=` 兜底），并把 `mediaType=audio` 的链接转成 `<audio controls>`。

登录设备管理在 `HomeView.vue` 个人资料弹窗：「登录设备」列表（`GET /api/auth/sessions`）+「强制下线」按钮（`DELETE /api/auth/sessions/{id}`）；登录带 `X-Device-Type: WEB` 头建会话。

## API 调用

- Axios 统一注入 Bearer token
- 401 统一跳转登录页；**网络级失败（连不上/超时/断网）**：距上次成功联系 ≥30s 判定「服务器连接超时」→ 提示后回登录页（**保留 token**，登录页提供「重新连接」），见 `docs/10-web-connection-timeout.md`
- 管理员文件列表传入 `viewUserId` 参数切换用户视角
- 文件上传显示进度，文件下载使用浏览器下载能力

## 视觉风格 · Apple 风格 (iOS 11-14)

### 主色
`#007AFF`（Apple 系统蓝），覆盖 Element Plus 默认 `#409EFF`

### 语义色
红 `#FF3B30` / 绿 `#34C759` / 橙 `#FF9500` / 青 `#5AC8FA`

### 中性色

| Token | 值 | 用途 |
|---|---|---|
| 页面背景 | `#f5f5f7` | 主内容区 |
| 侧边栏 | `#f2f2f7` | iPad 分栏风格，无边框 |
| 卡片/表格 | `#ffffff` | 纯白 |
| 主文字 | `#1d1d1f` | — |
| 次要文字 | `#86868b` | 辅助信息 |
| 边框 | `#e5e5ea` | 表格、输入框 |
| hover 背景 | `rgba(0,0,0,0.04)` | — |
| 选中背景 | `rgba(0,122,255,0.08)` | — |

### 圆角

`6px`（标签/小按钮）· `8px`（全局基础）· `12px`（卡片/弹窗）· `16px`（大容器）

### 阴影

`0 1px 3px rgba(0,0,0,0.04)`（卡片）· `0 4px 12px rgba(0,0,0,0.08)`（弹窗）

### 字体

Inter（Google Fonts），中文回退 PingFang SC。字号：`12px`（辅助）· `14px`（正文）· `16px`（导航）· `18-24px`（标题）

### 侧边栏

浅灰底 `#f2f2f7`，无边框分割线。选中项圆角蓝色高亮（`rgba(0,122,255,0.1)`）。管理员的「操作日志」使用 `el-sub-menu` 展开子菜单。响应式：`<768px` 变为左侧滑出抽屉 + 遮罩。

### 表格

Finder 列表视图风格：无斑马纹、hover 行浅蓝底、行高 `44px`。

### 动画

路由过渡 `fade 200ms`、对话框弹入 `scale 250ms`、按钮按下 `scale(0.97)`、卡片 hover 微浮。

### 图标

当前使用 Element Plus Icons，后续可换 `lucide-vue-next`。

## ADR-001：Apple 风格决策摘要

| 决策 | 选择 | 理由 |
|---|---|---|
| 技术路径 | 深度定制 Element Plus | 换 UI 库成本过高 |
| 设计语言 | iOS 11-14 | 卡片化、简约、触控友好 |
| 字体 | Inter | Web 上最接近 SF Pro 的替代 |
| 侧边栏 | 浅灰无边框（iPad 分栏） | 最匹配管理台场景 |

## 弹窗组件统一

所有确认弹窗（删除、撤销等）统一使用 `ConfirmDialog` 组件（`el-dialog`），不再使用 `ElMessageBox`。弹窗样式集中在 `styles.css` 的"弹窗与浮层"章节：

- **`el-dialog`**：header `24px 24px 0` / body `20px 24px` / footer `0 24px 20px`，按钮 flex + gap 10px
- **`el-message-box`**（如仍使用）：外层 `padding:0`，内部间距与 `el-dialog` 对齐
- 输入框边框使用 `box-shadow: 0 0 0 1px var(--el-border-color) inset`，与下拉选择框统一
- 弹窗底部按钮统一右对齐、10px 间距

## UI 原则

- 管理台以信息密度和可扫描性为主
- 文件列表优先表格
- 危险操作二次确认（统一使用 `ConfirmDialog`）
- 面包屑根节点根据用户上下文动态显示（"我的文件" / 用户名 / "根目录"）
- 长任务显示状态和错误原因
- 空状态说明下一步操作
