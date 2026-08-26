# Web 前端设计

## 技术栈

Vue 3 + Vite + Vue Router + Pinia + Axios + Element Plus

## 页面结构

| 页面 | 路由 | 说明 |
|---|---|---|
| 登录 | `/login` | 用户名密码登录 |
| 主布局 | `/` | 侧边栏 + 顶栏 + 内容区，需登录 |
| 文件中心 | `/` 内 | 管理员用户切换、面包屑、文件列表（双击/按钮预览）、上传/下载/重命名/删除、隐私空间（密码保护，进入即验证）；**列头排序**（`el-table` `sortable="custom"`：`prop`→`sort` 映射 `name`/`createdAt`/`size`（`sizeBytes`→`size`），列 `:sort-orders` 固定单方向——`name` 升序 / `createdAt` 降序 / 大小降序，点到已排序列（`order` 为空）回落默认 `name`；`sort` 状态存组件内 ref，跨目录导航保持、刷新/离开重置；「目录优先」由后端保证）；**大小/项数列**——文件显示字节大小，文件夹直接显示子项数（`childCount`，隐私文件夹显示「-」） |
| 随手记 | `/` 内 | 笔记列表 + **所见即所得块编辑器**（`NoteBlockEditor`：文本/标题块 + 图片/音频媒体，contenteditable 就地渲染行内格式、编辑即预览；浮动 B/I/U/S 格式条；顶部「＋」在上方插入）、搜索、SSE 实时同步、跨设备续读进度、笔记媒体渲染（经 `?token=` 鉴权） |
| 分享管理 | `/` 内 | 分享链接创建/查看/撤销、访问日志（管理员） |
| 用户管理 | `/` 内 | 管理员可见：用户列表（**头像列** `el-avatar`：`avatarUrl` 有则图、无则取 `displayName`/`username` 首字回退，样式与 `HomeView` 一致——透明底图 + 浅灰 `#c0c4cc` 首字）、创建/编辑、批量删除、重置密码 |
| 操作日志 | `/` 内 | 管理员可见：`el-sub-menu` 子菜单入口 |
| 登录日志 | `/` 内 | 管理员可见：分页表格，用户名模糊搜索、日期时间范围、登录结果筛选 |
| 个人资料 | 弹窗 | 展示名、更换/删除头像、修改密码、登录设备管理（强制下线） |
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
| `fileStore` | 当前 Storage Root、面包屑路径、文件列表、隐私令牌；`FilesView.vue` 用 localStorage（`baiflow_file_breadcrumb`）持久化路径，刷新浏览器后保持当前目录 |

## 组件与 Composables

| 文件 | 用途 |
|---|---|
| `components/ConfirmDialog.vue` | 基于 `el-dialog` 的通用确认弹窗，替代 `ElMessageBox.confirm`，确保所有弹窗样式统一 |
| `components/PreviewDrawer.vue` | 文件预览抽屉，按 MIME 类型路由到 5 种渲染器（img/video/audio/iframe/pre） |
| `views/NotesView.vue` + `components/NoteBlockEditor.vue` + `utils/noteBlocks.js` | 随手记页：左侧笔记列表 + 右侧**块编辑器**（文本/标题/列表/引用/代码/图片/音频块，媒体是 `<img>`/`<audio>` 真实组件；`noteBlocks.js` 做 Markdown↔块转换，落库仍是 Markdown）、10s 自动保存 + 手动保存、编辑区滚动保存 SCROLL_PERCENT、SSE 收 NOTE_UPDATED 刷新列表/别端保存时未在编辑则同步正文、乐观并发冲突（覆盖/重载）；媒体 URL 直接带 `?token=<会话token>` 渲染 |
| `composables/useConfirmDialog.js` | 提供 `confirm()` promise 式 API，搭配 `ConfirmDialog` 使用 |
| `composables/usePlaybackProgress.js` | 播放/阅读进度管理：查询历史进度、打开时**自动恢复位置**并提示「已恢复到上次观看位置」（不再弹跳转确认）、10s 自动保存、关闭时最终保存；滚动百分比按「滚动范围（scrollHeight - clientHeight）」计算，与 Android 一致 |
| `composables/useSse.js` | SSE 长连接封装：`EventSource` 连 `/api/events?token=<会话token>`，按事件名注册回调，组件卸载关闭 |
| `api/notes.js` | 笔记 CRUD + 阅读进度 API 封装 |
| `utils/mime.js` | 扩展名→MIME 映射表、MIME 主类型判定（image/video/audio/pdf/markdown/text/unknown）、预览支持判断、进度类型推断（SECONDS/PAGE/SCROLL_PERCENT） |
| `utils/format.js` | `formatDateTime`、`formatSize`、`formatSpeed` |

文件预览抽屉的 Markdown 渲染使用 showdown 库将源码转 HTML；随手记笔记编辑器为块式所见即所得（块存「行内 markdown 源」，contenteditable 就地渲染行内格式，编辑即预览；HTML↔Markdown 经 showdown + turndown 往返）。

笔记媒体（图片/录音/画画）在正文中以 `/api/notes/media/{id}` 相对路径引用，浏览器 `<img>/<audio>` 带不了 `Authorization` 头，`NotesView.vue` 在渲染后把媒体 URL 追加 `?token=<当前会话token>`（后端 `SessionAuthenticationFilter` 已支持 `?token=` 兜底），并把 `mediaType=audio` 的链接转成 `<audio controls>`。

登录设备管理在 `HomeView.vue` 个人资料弹窗：「登录设备」列表（`GET /api/auth/devices`，历史设备全展示并标注在线/离线）+ 在线设备「强制下线」（`DELETE /api/auth/sessions/{id}`，撤销全部会话变离线）+ **离线设备「删除」**（`DELETE /api/auth/devices?deviceName=`，移除登录历史记录，需确认）；登录带 `X-Device-Type: WEB` 头建会话。

## API 调用

- Axios 统一注入 Bearer token
- 管理员文件列表传入 `viewUserId` 参数切换用户视角
- 文件中心排序复用后端 `GET /api/files` 的 `sort` 参数（`name`/`createdAt`/`size`，固定方向、目录优先由后端保证）；**Web 不调用 `/files/{id}/size`**——文件夹大小 Web 直接显示子项数，该大小端点仅供 Android 长摁弹窗异步拉取
- 文件上传显示进度，文件下载使用浏览器下载能力

### 401 与网络级失败（服务器连接超时）

- **401**：`http.js` 收到 401 → `clearSession()` 清会话 → ElMessage「登录已过期」→ 约 1.5s 后整页跳转登录页（`window.location.href='/login'`）；服务器可达（有响应）即不会触发超时。
- **连接超时检测**：仅依赖实际请求失败（不做心跳轮询）。收到任何 HTTP 响应（含 4xx/5xx）即 `noteContact()` 刷新基准并视为服务器可达；登录态请求发生网络级失败（`error.response` 为空：连不上/请求超时/断网）且距上次成功联系 ≥`THRESHOLD_MS`(30s) 判定超时；阈值前的单次失败**静默**（避免笔记自动保存等高频请求刷屏），`timeoutFired` 去重防并发失败重复触发。
  - **处理**：置 `authStore.connectionTimeout=true` + ElMessage「服务器连接超时」→ `App.vue` watch 后约 1.5s `router.push('/login')` **客户端路由跳转**（不整页刷新，**保留 token**；超时≠会话失效，服务器会话未删除）。
  - **登录页超时态**（`connectionTimeout` 驱动）：「无法连接服务器」`el-alert` 提示条 + 登录表单仍可用 + 「重新连接」按钮；重连先 `GET /api/health`（公开免认证）再 `GET /auth/me`——会话有效则 `setSession` 清标志并重启检测回主界面，401 则 `clearSession` 转正常登录表单，仍失败保持超时态。
  - **路由守卫**：`to.path==='/login' && isLoggedIn && !connectionTimeout` 才弹回主界面；连接超时态（保留 token）放行登录页以便展示「重新连接」。
  - **组件**：`utils/connectionMonitor.js`（模块级状态 `started`/`lastContactAt`/`timeoutFired`，导出 `startMonitor`/`ensureMonitor`/`noteContact`/`resetMonitor`/`shouldFireTimeout`）、`api/http.js`（登录态首个请求 `ensureMonitor()` 仅首次启动、不重置基准）、`stores/auth.js`（`connectionTimeout` 状态；`setSession` 清标志并 `startMonitor`，`clearSession` 复位并 `resetMonitor`）、`api/health.js`（`getHealth`）。
  - **边界**：仅 Web 管理台；Android 有独立重试/同步机制不做，公共分享页（GUEST）无会话不适用；仅请求驱动 → 用户闲置无请求时断连无法即时发现，冷启动后需有请求且距起点 ≥30s 才触发。

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

## Apple 风格设计摘要

| 项 | 说明 |
|---|---|
| 技术路径 | 深度定制 Element Plus |
| 设计语言 | iOS 11-14（卡片化、简约、触控友好） |
| 字体 | Inter |
| 侧边栏 | 浅灰无边框（iPad 分栏） |

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
