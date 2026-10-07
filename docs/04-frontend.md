# Web 前端设计

## 技术栈

Vue 3 + Vite + Vue Router + Pinia + Axios + Element Plus

## 页面结构

| 页面 | 路由 | 说明 |
|---|---|---|
| 首次初始化 | `/setup` | 首次部署向导：创建第一个管理员（初始化令牌 + 用户名 + 显示名 + 密码 + 确认密码），未初始化时所有路径强制跳到这里（详见「首次部署向导」） |
| 登录 | `/login` | 用户名密码登录 |
| 主布局 | `/` | 侧边栏 + 顶栏 + 内容区，需登录 |
| 文件中心 | `/` 内 | 管理员用户切换、面包屑、文件列表（双击/按钮预览；**行内文本不可选中** —— 双击若选中文字会弹出浏览器选区浮层，浮层飘在预览抽屉遮罩之上会挡住抽屉左边缘的拖动热区）、上传/下载/重命名/删除、隐私空间（密码保护，进入即验证）；列头排序（`el-table` `sortable="custom"`：`prop`→`sort` 映射 `name`/`createdAt`/`size`（`sizeBytes`→`size`），`:sort-orders` 固定单方向——`name` 升序 / `createdAt` 降序 / 大小降序，点到已排序列（`order` 为空）回落默认 `name`；`sort` 存组件内 ref，跨目录导航保持、刷新/离开重置；目录优先由后端保证）；大小/项数列——文件显示字节大小，文件夹显示子项数（`childCount`，隐私文件夹显示「-」） |
| 随手记 | `/` 内 | 笔记列表 + 所见即所得块编辑器（`NoteBlockEditor`：文本/标题块 + 图片/音频媒体；contenteditable 就地渲染行内格式、编辑即预览；浮动 B/I/U/S 格式条；顶部「＋」在上方插入）、搜索、SSE 实时同步、跨设备续读进度、笔记媒体渲染；**新建笔记带客户端生成的幂等 id**（点「新建」时生成一次、保存重试复用，服务端据此去重，见 `docs/03-api.md`） |
| 分享管理 | `/` 内 | 分享链接创建/查看/撤销、访问日志（管理员） |
| 用户管理 | `/` 内 | 管理员可见：用户列表（头像列 `el-avatar`：`avatarUrl` 有则图、无则取 `displayName`/`username` 首字回退，样式同 `HomeView`——透明底图 + 浅灰 `#c0c4cc` 首字）、创建/编辑、批量删除、重置密码 |
| 操作日志 | `/` 内 | 管理员可见：`el-sub-menu` 子菜单入口 |
| 登录日志 | `/` 内 | 管理员可见：分页表格，用户名模糊搜索、日期时间范围（默认当天，按 UTC+8 计算，日期框可清空看全部历史）、登录结果筛选 |
| 上传记录 / 下载记录 | `/` 内 | 两个独立菜单入口（`RecordsView` type=upload/download）：分页表格 + 时间范围（默认当天）/文件名/来源过滤 + 重置按钮（还原当天默认并重查）；admin 可切用户看全部。文件名列 = 类型图标 + 文件名（`fileIconPath` 补扩展名兜底，记录无 mime 也显示真实类型图标）。下载记录行提供「删除源文件」：二次确认 → `DELETE /api/files/{id}`；code `40401` 提示「源文件不存在或已删除」，成功提示「已删除」，记录保留（上传记录 Web 端不提供）。行高统一 66px（时间列单行 + tooltip，避免折行参差） |
| 个人资料 | 弹窗 | 展示名、更换/删除头像、修改密码、登录设备管理（强制下线） |
| 预览抽屉 | Drawer | 左边缘可拖动改宽（默认 75% 屏宽，见「预览抽屉」）。按 MIME 路由：图片(`<img>`)、视频(`<video>`+进度)、音频(`<audio>`+进度)、PDF(`<iframe>`)、Markdown(showdown→HTML，白底内容区 + 浅灰边框 + 左侧目录，见「Markdown 预览」)、文本/代码(`<pre>`)、其他(降级下载；Office 文档归为此类) |

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
| `components/MarkdownToc.vue` | Markdown 预览的章节目录：左侧常驻栏 + 展开/收起拉手。无状态组件：标题与 id 由 `PreviewDrawer` 渲染后传入，只负责展示与上报点击/收起。见「Markdown 预览」 |
| `components/PreviewDrawer.vue` | 文件预览抽屉：`el-drawer` + `resizable`（左边缘拖动改宽，见「预览抽屉」）；按 MIME 类型路由到 7 类渲染（image / video / audio / pdf / markdown / text / zip），其余降级为「不支持预览」；渲染器清单见「页面结构」，Markdown 的边框与目录见「Markdown 预览」 |
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

## 预览抽屉

所有文件类型共用的容器（`el-drawer`，`direction="rtl"` 从右侧滑出）。**宽度默认 75% 屏宽，左边缘可拖动改宽，范围 40% 屏宽 ↔ 满屏。**

拖动用 Element Plus 内置的 `resizable`（**依赖 Element Plus ≥ 2.9**）：它自带 `.el-drawer__dragger`（`cursor: ew-resize`、平时透明、**悬停时显出一条 3px 主色细条**），是抽屉的子元素；`resize-start` / `resize` / `resize-end` 三个事件都回传宽度数字。

- **热区 12px 宽**（Element 默认 8px，`--el-drawer-dragger-size` 覆盖；提示线仍是 3px，只是好抓）—— 抽屉最左侧是 body 的 20px 内边距，加宽不会压到正文
- **下限由两层机制各管一件事**：CSS `min-width: 40vw` 是**永不破的那条线**（`min-width` 优先级高于内联 width，挡住拖过头）；`resize` 里触底时把 `size` 收回下限，让本次拖动就地在限位处结束，不必等松手。内置自己的下限只有 4px，且**反复触底后它会粘住拖动起点**（只在起点干净时才重读 `offsetWidth`）—— 所以触底时还要强制清一次它的内部状态，否则下次拖动要往右拖回一大段才有反应
- **宽度记忆**：`resize-end` 时按 px 存 `localStorage.baiflow_preview_drawer_width`，读取时按当前窗口重新夹取；没存过则默认 75%（宽度是刻意选择，故记忆，与目录「展开状态不记忆」不同）
- **不支持键盘微调与触摸拖动**：内置只监听鼠标事件（`mousedown`/`mousemove`）
- 抽屉宽度同时决定**正文与目录的比例**（目录恒为预览卡片的 25%，见「Markdown 预览」）—— 调比例的动作只有这一处

## Markdown 预览

`.md` / `.markdown` 文件在预览抽屉里 showdown 转 HTML 渲染。**目录栏 + 正文整体**是一张**白底卡片 + 1px 浅灰边框**（`var(--el-border-color)` = `#e5e5ea`，与正文里 h1/h2 分隔线、表格边框同色；圆角 8px）——白卡片落在同样白色的抽屉里，边框是唯一的内容区边界。**其余预览类型不加边框**（图片/视频/音频自带深色底或插图板，PDF 框内是浏览器自带阅读器，本来就有边界）。

### 左侧目录（`MarkdownToc.vue`）

- **默认展开、常驻内容区左侧**：宽 **预览卡片的 25%**（`width: 25%`，下限 `min-width: 160px` —— 抽屉拖窄时 25% 会小到没法看，这是唯一的破例），右侧一条分隔线、自身可滚（标题多时），收 **h1–h3**（三级以下不进目录）并按层级缩进（h2/h3 各再缩进 12px），**当前章节高亮** `#007AFF` + 浅蓝底
- **宽度不可拖动**：目录宽度恒为卡片 25% —— 要调正文/目录的比例就拖**整个预览抽屉**（见「预览抽屉」章），目录跟着等比走
- **拉手（展开/收起共用的同一片）**：14px × 60px、垂直居中，**左长右短**的梯形（`clip-path`，左边缘满高、右边缘收到 40%），灰底 `--el-border-color`、悬停加深一档，中间一根手画 chevron。**两态形状与朝向完全相同，不翻转** —— 变的只是贴哪条边、箭头指哪边：
  - **收起态**：贴**预览框左边缘**（长边落在框上、向右收窄），箭头指向右＝指进正文（把目录拉出来）；点它展开
  - **展开态**：贴**分隔线右侧**（长边落在分隔线上、向右收窄），箭头指向左＝指回目录（把目录收回去）；点它收起
  - 两态箭头**相反**，与「展开 / 收起」两个动作对应；拉手 14px 都落在卡片内部（收起态在正文 24px 左内边距里），**不压字**
  - 卡片保留 `overflow: hidden` 把目录栏/正文的直角裁进圆角里 —— 两片拉手都在卡片内部，不受影响
  - 分隔线一带横向顺序：`目录列表 → 滚动条（6px 细条）→ 分隔线 → 收起拉手（14px）→ 正文左内边距（24px）`
- **展开状态不记忆**：每次打开预览抽屉都回到默认的**展开**（常驻即默认态）
- **目录栏头部**只剩「目录」标题 —— 收起草按钮已并入上面的拉手，不再单设按钮
- **文档无 h1–h3** 时目录栏与拉手都不出现，正文占满（不留空栏）
- **点击跳转**：按章节元素相对滚动容器的偏移（`getBoundingClientRect` 差值 + 当前 `scrollTop`，再留 8px 呼吸位）置 `scrollTop`，**不用 `scrollIntoView`**（它会连带滚动祖先/抽屉）；文字用 `id` 锚点（`href="#md-h-N"` + `preventDefault`）保证可聚焦
- **跳转照旧触发阅读进度保存**（`SCROLL_PERCENT`，防抖 2s）：跳一下进度即改写到新位置，与「进度跟着读者走」一致，不做特殊处理
- **加载态**：Markdown / 文本拉正文期间也走抽屉的「加载中」转圈（与图片/音视频/PDF 拉 blob 共用一个加载态变量 —— 两类由 `category` 分派、互斥）
- **滚动监听只有一处**：模板 `@scroll`（进度防抖保存与目录高亮共用），不用命令式 `addEventListener` —— 后者在重开同一文件时会叠加出多个监听。高亮从当前项出发只比相邻标题，稳态滚动每帧 O(1) 次 rect 读取（远跳如拖滚动条则为 O(经过的标题数)）、**不预存偏移量**，正文图片撑开高度后也能自愈；滚到底时兜底高亮末节（最后一个标题可能永远到不了阈值线）
- **空标题不进目录**（如标题里只有图片）：与 Android 一致，否则会出现点不动的空行
- 目录标题与拉手文案走 i18n

### 预览区高度（`--preview-h`）

预览区高度只在一处定义：`.preview-container` 上的 `--preview-h: max(75vh, calc(100vh - 180px))`，Markdown 卡片、文本/代码、PDF `iframe`、图片、视频共用它。

抽屉头 + 尾 + body 内边距固定占约 165px，取其上 180px 做余量 → 预览跟着窗口一起变高；`max()` 兜住短窗口（不低于 75vh），也不会高到把抽屉 body 撑出滚动条。

### 标题 id 自赋（不能直接用 showdown 的）

showdown 对中文标题生成的 id 是空/退化的，拿来做锚点会直接失效，因此转换器设 **`noHeaderId: true`** 关掉它，改由渲染后自赋 `md-h-N`。

**在离屏容器里**（`createElement` + `innerHTML`，不插进文档：图片不发请求、脚本不执行）先解析出 HTML、给进目录的标题赋好 id，再**把目录与正文一次性写入** —— 分两步写会让抽屉先画出满宽正文、随后目录栏插入把正文挤窄，肉眼就是「正文先到、目录后弹」。

**标题从渲染结果抓，不是正则扫 Markdown 源**：源码扫描会漏 setext 标题（`标题` 换行 `===`）、并把围栏代码块里的 `#` 误判为标题；从 DOM 抓则天然与渲染器一致（Android 端同理，见 `docs/05-android.md`）。

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
