# Web 前端设计

## 技术栈

Vue 3 + Vite + Vue Router + Pinia + Axios + Element Plus

## 页面结构

| 页面 | 路由 | 说明 |
|---|---|---|
| 首次初始化 | `/setup` | 首次部署向导：创建第一个管理员（初始化令牌 + 用户名 + 显示名 + 密码 + 确认密码），未初始化时所有路径强制跳到这里（详见「首次部署向导」） |
| 登录 | `/login` | 用户名密码登录 |
| 主布局 | `/` | 侧边栏 + 顶栏 + 内容区，需登录 |
| 文件中心 | `/` 内 | 管理员用户切换、面包屑、文件列表（双击/按钮预览）、上传/下载/重命名/删除、隐私空间（密码保护，进入即验证）；列头排序（`el-table` `sortable="custom"`：`prop`→`sort` 映射 `name`/`createdAt`/`size`（`sizeBytes`→`size`），`:sort-orders` 固定单方向——`name` 升序 / `createdAt` 降序 / 大小降序，点到已排序列（`order` 为空）回落默认 `name`；`sort` 存组件内 ref，跨目录导航保持、刷新/离开重置；目录优先由后端保证）；大小/项数列——文件显示字节大小，文件夹显示子项数（`childCount`，隐私文件夹显示「-」） |
| 随手记 | `/` 内 | 笔记列表 + 所见即所得块编辑器（`NoteBlockEditor`：文本/标题块 + 图片/音频媒体；contenteditable 就地渲染行内格式、编辑即预览；浮动 B/I/U/S 格式条；顶部「＋」在上方插入）、搜索、SSE 实时同步、跨设备续读进度、笔记媒体渲染 |
| 分享管理 | `/` 内 | 分享链接创建/查看/撤销、访问日志（管理员） |
| 用户管理 | `/` 内 | 管理员可见：用户列表（头像列 `el-avatar`：`avatarUrl` 有则图、无则取 `displayName`/`username` 首字回退，样式同 `HomeView`——透明底图 + 浅灰 `#c0c4cc` 首字）、创建/编辑、批量删除、重置密码 |
| 操作日志 | `/` 内 | 管理员可见：`el-sub-menu` 子菜单入口 |
| 登录日志 | `/` 内 | 管理员可见：分页表格，用户名模糊搜索、日期时间范围（默认当天，按 UTC+8 计算，日期框可清空看全部历史）、登录结果筛选 |
| 上传记录 / 下载记录 | `/` 内 | 两个独立菜单入口（`RecordsView` type=upload/download）：分页表格 + 时间范围（默认当天）/文件名/来源过滤 + 重置按钮（还原当天默认并重查）；admin 可切用户看全部。文件名列 = 类型图标 + 文件名（`fileIconPath` 补扩展名兜底，记录无 mime 也显示真实类型图标）。下载记录行提供「删除源文件」：二次确认 → `DELETE /api/files/{id}`；code `40401` 提示「源文件不存在或已删除」，成功提示「已删除」，记录保留（上传记录 Web 端不提供）。行高统一 66px（时间列单行 + tooltip，避免折行参差） |
| 个人资料 | 弹窗 | 展示名、更换/删除头像、修改密码、登录设备管理（强制下线） |
| 预览抽屉 | Drawer | 按 MIME 路由：图片(`<img>`)、视频(`<video>`+进度)、音频(`<audio>`+进度)、PDF(`<iframe>`)、Markdown(showdown→HTML)、文本/代码(`<pre>`)、其他(降级下载；Office 文档归为此类) |

## 国际化 (i18n)

- **vue-i18n**：UI 文本统一走 `t('namespace.key')`，语言包在 `src/locales/`
- **Element Plus**：`el-config-provider` 响应式切换组件语言
- **语言切换**：右上角下拉框（中文 / English），写 `localStorage.baiflow_locale`；主界面与**登录页**都有（登录页切换器在右上角），登录页文案也走 i18n
- **Axios**：请求头自动带 `Accept-Language`，后端错误消息同步切换
- 数据库数据（文件名、用户名等）不翻译，只翻 UI 文案（列名、按钮、提示）

## 状态管理

| Store | 职责 |
|---|---|
| `authStore` | token、用户信息、登录状态、`isAdmin` 判断、连接超时态 |
| `systemStore` | 是否已完成首次初始化（`GET /api/setup/status`，每次页面加载只查一次），供路由守卫强制跳向导 |
| `fileStore` | 当前 Storage Root、面包屑路径、文件列表、隐私令牌；`FilesView.vue` 用 localStorage（`baiflow_file_breadcrumb`）持久化路径，刷新浏览器后保持当前目录 |

## 首次部署向导

首次部署的服务器**没有预置管理员**，必须先在 `/setup` 创建第一个管理员才能登录使用。

- **判定来源**：`GET /api/setup/status`（公开接口），结果缓存在 `systemStore`。
- **查询失败不阻断**：服务器不可达时保守按「已初始化」处理，放行到登录页，由既有连接超时流程提示——避免把用户困在打不开的向导页。
- **强制跳转**：未初始化时任何路径重定向到 `/setup`（登录页也进不去）；已初始化后访问 `/setup` 重定向到登录页或主界面，后端恒返回 `40302`（入口永久关闭）。
- **初始化令牌**：服务器启动日志打印（`docker compose logs server`），同时落盘数据目录的 `setup-token.txt`，向导页顶部提示写明获取方式；一次性，初始化成功后文件删除、令牌作废。
- **成功后**：`POST /api/setup/init` 直接返回登录会话（结构与 `/auth/login` 一致），前端写入会话即进主界面，不再让用户重输刚设置的密码。
- **密码长度**：向导页前端校验至少 8 位（后端当前无统一密码策略，只在前端提示）。
- **重新触发向导**（测试用）：清空 `bf_user` 与 `bf_system_setting` 两张表再重启，SQL 见 `deploy/SKILL.md`「重走首次初始化」。

## 组件与 Composables

| 文件 | 用途 |
|---|---|
| `components/AuthShell.vue` | 未登录页共用外壳（居中卡片 + 品牌头 + 右上语言切换 + 表单外观），登录页与首次初始化向导用默认插槽放入各自表单 |
| `components/ConfirmDialog.vue` | 基于 `el-dialog` 的通用确认弹窗，确保弹窗样式统一；取消/关闭默认都 reject `'cancel'`，传 `distinguishClose: true` 时关闭（X / ESC / 遮罩）reject `'close'` |
| `components/EditableBlock.vue` | 所见即所得文本块单元（`NoteBlockEditor` 用）：`contenteditable` 渲染行内 markdown 的最终效果，编辑即预览；输入/失焦经 `htmlToMarkdown` 回写、外部变更经 `inlineToHtml` 重渲染 |
| `components/PreviewDrawer.vue` | 文件预览抽屉：按 MIME 类型路由到 7 类渲染（image / video / audio / pdf / markdown / text / zip），其余降级为「不支持预览」；渲染器清单见「页面结构」 |
| `views/NotesView.vue` + `components/NoteBlockEditor.vue` + `utils/noteBlocks.js` | 随手记页：左侧笔记列表 + 右侧**块编辑器**（文本/标题/列表/引用/代码/图片/音频块，媒体是 `<img>`/`<audio>` 真实组件；`noteBlocks.js` 做 Markdown↔块转换，落库仍是 Markdown）、10s 自动保存 + 手动保存、编辑区滚动保存 `SCROLL_PERCENT`、SSE 收 `NOTE_UPDATED` 刷新列表 / 别端保存时未在编辑则同步正文、乐观并发冲突（覆盖/重载）；媒体 URL 直接带 `?token=<会话token>` 渲染 |
| `composables/useConfirmDialog.js` | 提供 `confirm()` promise 式 API，搭配 `ConfirmDialog` 使用 |
| `composables/usePlaybackProgress.js` | 播放/阅读进度管理：查历史进度、打开时**自动恢复位置**并提示「已恢复到上次观看位置」（不弹跳转确认）、10s 自动保存、关闭时最终保存；滚动百分比按滚动范围（scrollHeight - clientHeight）算，与 Android 一致 |
| `composables/useNoteProgress.js` | 随手记阅读进度（与上面的文件预览进度分开）：打开笔记时自动滚动到记录位置，滚动防抖保存 `SCROLL_PERCENT` |
| `composables/useSse.js` | SSE 长连接封装：`EventSource` 连 `/api/events?token=<会话token>`，按事件名注册回调，组件卸载关闭 |
| `api/notes.js` | 笔记 CRUD + 阅读进度 API 封装 |
| `utils/mime.js` | 扩展名→MIME 映射表、MIME 主类型判定（image/video/audio/pdf/markdown/text/unknown）、预览支持判断、进度类型推断（SECONDS/PAGE/SCROLL_PERCENT） |
| `utils/format.js` | `formatDateTime`、`formatSize`、`formatSpeed` |

笔记编辑器的块存「行内 markdown 源」，HTML↔Markdown 经 showdown + turndown 往返；预览抽屉的 Markdown 用 showdown 转 HTML。

笔记媒体（图片/录音/画画）在正文中以 `/api/notes/media/{id}` 相对路径引用；浏览器 `<img>/<audio>` 带不了 `Authorization` 头，`NotesView.vue` 渲染后给媒体 URL 追加 `?token=<当前会话token>`（后端 `SessionAuthenticationFilter` 支持 `?token=` 兜底），并把 `mediaType=audio` 链接转成 `<audio controls>`。

登录设备管理在 `HomeView.vue` 个人资料弹窗：「登录设备」列表（`GET /api/auth/devices`，历史设备全展示并标在线/离线）+ 在线设备「强制下线」（`DELETE /api/auth/sessions/{id}`，撤销全部会话变离线）+ **离线设备「删除」**（`DELETE /api/auth/devices?deviceName=`，移除登录历史记录，需确认）；登录带 `X-Device-Type: WEB` 头建会话。

## API 调用

- Axios 统一注入 Bearer token
- 管理员文件列表传 `viewUserId` 切换用户视角
- 文件中心排序复用后端 `GET /api/files` 的 `sort` 参数（`name`/`createdAt`/`size`）；**Web 不调用 `/files/{id}/size`**——文件夹大小 Web 显示子项数，该端点仅供 Android 长摁弹窗异步拉取
- 上传显示进度，下载用浏览器下载能力

### 401 与网络级失败（服务器连接超时）

- **401**：`http.js` 收到 401 → `clearSession()` 清会话 → 提示「登录已过期」→ 整页跳转登录页。
- **连接超时**（`utils/connectionMonitor.js` + `api/http.js`）：只依赖实际请求失败，不做心跳轮询。登录态请求网络级失败（`error.response` 为空：连不上/超时/断网）且距上次成功联系 ≥30s 判超时；阈值前的单次失败**静默**（避免笔记自动保存等高频请求刷屏），`timeoutFired` 去重。
  - **处理**：置 `authStore.connectionTimeout=true` → `App.vue` 约 1.5s 后 `router.push('/login')` **客户端路由跳转**（不整页刷新，**保留 token**；超时≠会话失效）。
  - **登录页超时态**：「无法连接服务器」提示条 + 登录表单仍可用 + 「重新连接」按钮（重连先 `GET /api/health` 再 `/users/me`：有效则回主界面，401 转正常登录表单）。
  - **边界**：仅 Web 管理台；GUEST 公共分享页无会话不适用；仅请求驱动，用户闲置无请求时无法即时发现断连。

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

浅灰底 `#f2f2f7`，无边框分割线。选中项圆角蓝色高亮（`rgba(0,122,255,0.1)`）。管理员的「操作日志」为子菜单展开。响应式：`<768px` 变左侧滑出抽屉 + 遮罩。

### 表格

Finder 列表视图风格：无斑马纹、hover 行浅蓝底、行高 `44px`。

### 动画

路由过渡 `fade 200ms`、对话框弹入 `scale 250ms`、按钮按下 `scale(0.97)`、卡片 hover 微浮。

### 图标

当前使用 Element Plus Icons，后续可换 `lucide-vue-next`。

## 弹窗组件统一

确认弹窗（删除、撤销等）统一用 `ConfirmDialog` 组件（`el-dialog`），不用 `ElMessageBox` / `ElMessageBox.confirm`。样式集中在 `styles.css` 的"弹窗与浮层"章节：

- **`el-dialog`**：header `24px 24px 0` / body `20px 24px` / footer `0 24px 20px`，按钮 flex + gap 10px
- **`el-message-box`**（如仍使用）：外层 `padding:0`，内部间距与 `el-dialog` 对齐
- 输入框边框用 `box-shadow: 0 0 0 1px var(--el-border-color) inset`，与下拉选择框统一
- 底部按钮统一右对齐、10px 间距

## UI 原则

- 管理台以信息密度和可扫描性为主
- 文件列表优先表格
- 危险操作二次确认（统一用 `ConfirmDialog`）
- 面包屑根节点按用户上下文动态显示（"我的文件" / 用户名 / "根目录"）
- 长任务显示状态和错误原因
- 空状态说明下一步操作
