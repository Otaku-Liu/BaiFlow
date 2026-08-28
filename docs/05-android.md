# Android 客户端设计

## 技术栈

Java + Retrofit + OkHttp + WorkManager + Foreground Service + SharedPreferences

## MVP 功能

- 登录、文件列表（底部三栏：文件 / 随手记 / 我的）、上传手机文件、下载服务器文件
- 长任务前台通知
- 文件页与 Web 对齐：自动使用第一个可用存储根；管理员可「查看用户」切换 viewUserId
- 随手记：所见即所得块式笔记编辑器（文本/标题块 + 图片/录音/画画媒体；块内渲染行内格式、编辑即预览；顶部块类型栏 + 第二行格式栏 B/I/U/S；系统默认选中菜单；引用已移除），见本文「随手记」章节

## 模块

```
baiflow-android/app/src/main/java/
  auth/        # 登录会话 token 管理
  editor/      # 随手记富文本编辑器（纯 JVM Markdown 解析/发射 + Spannable 适配层）
  model/       # API 数据模型
  network/     # Retrofit + OkHttp
  transfer/    # 上传/下载前台服务（设备 ↔ 服务器文件传输）
  ui/activity/ # 独立页面 Activity（登录/服务器配置/预览/资料/密码/语言/笔记编辑/画画）
  ui/fragment/ # 主界面 Fragment（Files / Notes / Mine）
  widget/      # 自定义控件（按压渐变 AnimatedTextButton / AnimatedTextLabel / AnimatedTintImageView）
  util/        # 通用工具
```

## 登录态

SharedPreferences 保存会话 token（长期保持）与服务器地址。登录带 `X-Device-Type` / `X-Device-Name` 头，服务端据此建会话（ANDROID 长期、180 天不活跃兜底）；被强制下线/过期后 401 清会话回登录。详见 `docs/02-database.md`「auth_session」。

## 上传下载

- 小文件 Retrofit multipart 上传；长任务前台服务（UploadService / DownloadService）带通知
- 下载计次由后端记录（见 `docs/02-database.md`、`docs/03-api.md`）
- **下载落公共 Download 文件夹**：API 29+ 走 `MediaStore.Downloads`（作用域存储，无需权限，系统「下载」/文件管理器可见）；API 26-28 写 `Environment.DIRECTORY_DOWNLOADS`，下载前申请 `WRITE_EXTERNAL_STORAGE`
- **不支持预览的文件**：点按弹「暂不支持在线预览」对话框（含「下载」按钮），**不自动下载**，手动点「下载」才下载
- **预览渲染**：视频/音频用 Media3 ExoPlayer（视频 `PlayerView`、音频 `PlayerControlView`，正确处理旋转元数据、宽高比、控制器时长）；横屏视频按「旋转 90/270 或有效宽高为横」自动转横屏

## 浏览进度（跨端同步）

- 文件预览：视频/音频存 **SECONDS**（10s 定时 + 退出时保存）；文本/Markdown 存 **SCROLL_PERCENT**（滚动防抖 2s，回顶保存 0 清除历史）；打开时自动 seek/滚动到记录位置并 Toast「已恢复到上次观看位置」
- 随手记：编辑器滚动防抖 800ms 上报 SCROLL_PERCENT，打开时自动滚动到记录位置；短笔记（不足一屏）不记录进度
- 与 Web 共用服务端 `bf_playback_progress` / `bf_note_progress`，按用户存、不记设备；视频/音频仅在播放过（played）后才允许存 0，避免未播放关闭时误清历史

## 页面

- 登录 / 服务器配置（连通性检测）→ **MainActivity**（底部三栏，`ViewPager2` 滑动 + 底部导航双向同步）
  - **文件** `FilesFragment`：标题居中；左上「返回上一级」图标（根目录置灰）、右上「刷新」+「三点」菜单（下拉：新建文件夹 / 上传文件）；长按文件/文件夹弹操作菜单（重命名 / 下载 / 删除，重命名走 `PATCH /api/files/{id}/rename`）；文件列表按类型用彩色 PNG 图标（md/pdf/json/xml/word/excel/ppt 等）；不支持预览的文件点按弹下载确认框。**隐私空间**：主目录下「隐私空间」文件夹，首次进入弹「设置密码」（`POST privacy`，40107），之后进入弹「输入密码」（`verifyPrivacy` 换令牌）；令牌仅存内存（`privacyTokens`），重进需重输；管理员访问后端直接放行
  - **随手记** `NotesFragment`：列表/搜索/删除 → `NoteEditActivity`（**所见即所得块编辑器**：RecyclerView 每块一个真实 View，文本 EditText 经 `BlockRichText` 渲染行内 markdown 的格式效果、编辑即预览，图片 ImageView，音频 `NoteAudioPlayerView`；加载 Markdown→`NoteBlocks.fromDoc`、保存 `NoteBlocks.toDoc`→Markdown，落库仍是 Markdown）→ `NoteDrawActivity`（画画）
  - **我的** `MineFragment`：分组（账号/通用/同步）；修改资料 / 修改密码 / 语言为独立页面；退出登录二次确认 + `doLogout()` 幂等守卫（`logoutStarted` 标志）防连点。头像展示：有 `avatarUrl` 时 OkHttp 拉取圆形展示（`AvatarLoader`），无则浅灰底 + 展示名首字；修改资料页（`ProfileActivity`）支持**更换头像**（96dp 圆形 + 贴底「编辑」带，选图即上传，`ImageUtil` 缩放/EXIF 校正/压缩 ≤1MB → `POST /api/auth/avatar`），实现细节见 `docs/07-ios-design-system.md`；Android 端不提供删除头像（服务端与 Web 端入口保留）。进入「我的」页时（`onResume`）在线模式调 `/auth/me` 刷新本地用户信息（头像/展示名可能在其他端修改）

## 多语言（i18n）

默认中文 + 英文。语言持久化到 `SessionManager`（`getLanguage`/`saveLanguage`，SharedPreferences），由 `BaseActivity.attachBaseContext` 应用到每个 Activity（`createConfigurationContext` + `Locale.setDefault`），`applyOverrideConfiguration` 保留深/浅色 UI 模式；全部 Activity 继承 `BaseActivity`。切换语言 = `saveLanguage` + **`CLEAR_TASK` 重启回主界面**（任务内页面按新语言重建，普通启动路径走 `windowBackground` 应用底色，不闪黑）。首次使用未设置语言时返回原样 Context，走系统默认语言。布局 `@string/key`、Java `getString(R.string.key)`、带参 `%1$s`。

## 失败处理

- 网络失败：`network/NetworkFeedback`（静态单例）+ `network/UiCallback<T>` 统一处理。**网络失败 ≠ 会话失效**：断网/服务器宕机不清会话、不回登录页，用户留在当前页可重试。
  - 交互请求（UiCallback）：收到任何 HTTP 响应视为成功联系并清除断连标记；IOException 按 `classify()` 分类（设备无网→「无网络连接」，有网但请求失败→「无法连接服务器」）；HTTP 5xx 全局兜底「服务器异常」。
  - 全局统一 Toast + **时段内去重**（同一断连时段只提示一次，任一次响应成功即清除），断连结束后弹一次「网络已恢复」。
  - 断网由 `MainActivity` 注册 `ConnectivityManager` NetworkCallback 即时提示（onLost→「无网络连接」 / onAvailable→恢复），onDestroy 注销；覆盖文件/笔记/我的三个 Tab。
  - 登录页/服务器配置页：onFailure 用 `classify()` 友好文案**内联**提示，不触发全局 Toast（避免双弹）。
  - 不再向用户展示 `t.getMessage()` 异常原文/服务器 IP/技术细节。
  - 后台上传/下载前台服务、WorkManager 后台同步**不弹 UI**，保持各自失败机制。
  - 超时：仅 connect 30s→10s（连不上更快反馈），read/write 保持 60s（下载/大响应需要长读）。
- token 失效：`AuthInterceptor` 401 清会话回登录
- 上传/下载失败：任务保留 + 错误原因记录

## 设计系统（iOS 风格）

集中式 iOS 风格设计系统（`res/values/styles_ios.xml`，`@style/Ios.*`），新增样式一律加进该文件，不写硬编码色值/圆角（详见 `docs/07-ios-design-system.md`）：

- 组件：按钮（Primary / Text / **DangerOutline** 白底红字）、输入框、标题栏（居中标题 + 返回图标）、卡片、圆角弹窗（16dp）/ 下拉（12dp）
- **弹窗统一走 `MaterialAlertDialogBuilder`**（16dp 圆角全局生效，appcompat `AlertDialog.Builder` 已弃用），详见 `docs/07-ios-design-system.md`
- **文件中心长摁弹窗**：自定义布局（`dialog_file_info.xml`）上半简介区（图标/名称/大小/创建/修改/上次打开时间），下半动作行（重命名/下载/「立即删除」红色，删除仍二次确认）；`Ios.DialogAction` 动作行样式；**隐私文件夹/项目仅展示简介**（隐藏重命名/下载/立即删除及全部动作分隔线，后端同样拒绝，需先移除隐私）；**时间空值显示「--」**；**文件夹大小按需计算**——弹窗打开时异步拉取 `GET /api/files/{id}/size` 更新简介大小行（失败回落「文件夹」标记）；弹窗四边 padding 一致（16dp）
- **文件中心列表行**：文件夹行 meta 显示**直接子项数**（`childCount`，「N 项」，文件 + 子文件夹） + 右侧「>」箭头（`ivChevron`，表示可进入下一级）；文件行 meta 显示字节大小；不再显示「文件夹」提示字（界面空间有限，**不展示创建时间**；长摁弹窗简介区仍展示，时间精确到时分秒）
- **文件中心排序**：工具栏「刷新」与「三点」间排序按钮（`btnSort`，`ic_sort` 原排序图标），弹出 名称/创建时间/文件大小 单选菜单（`DropdownMenu`），当前项**左侧 √ 勾选、右侧「>」图标指示方向**（`ic_chevron_up` / `ic_chevron_down`），**再点当前项切换升/降序**；排序走后端 `sort` + `dir` 参数，目录始终优先。详见 `docs/03-api.md`
- **下拉菜单统一**：全 app 下拉（新建/排序/块类型/块上方插入）走自定义 `DropdownMenu` 组件（固定宽度、44dp 行高、选中项 √ 黑色、右侧箭头指示方向），详见 `docs/07-ios-design-system.md`
- **按压渐变**：`widget/` 的 `AnimatedTextButton` / `AnimatedTextLabel` / `AnimatedTintImageView`，文字/图标按压「蓝→浅蓝」平滑过渡（`text_accent_selector`）；返回/刷新/上一级用单色 PNG + tint 参与渐变
- 文件类型图标：彩色 PNG（`res/drawable/ic_type_*`，含 md；Web 端复用同一批 PNG 展示）

## 随手记（在线编辑器 + 离线三态）

### 编辑器取舍
- Android 行内强调嵌套可能摊平为相邻 run、有序列表总是从 1 开始、块间空行归一化——**代码块内容/标题标记/媒体 URL/任何文本绝不丢失**（透传保证）
- 引用块已移除：旧引用内容映射为普通文本块（保留内容，丢弃 `>` 前缀）；下划线用 `<u>` 表示

### 离线三态（Room + outbox）
- **本地模式**（未配服务器，免登录）：随手记全本地，文件中心/下载/传输禁用
- **在线模式**（已配服务器 + token）：全功能，笔记同步
- **离线模式**（已配服务器但主动离线）：进离线**清 token**（本地笔记免登录），随手记用 Room 本地镜像 + outbox，文件中心/下载/传输禁用；重连**必重新登录**再立即同步
- Room 表 `bf_local_note` 按 `server_url` 分区（**缓存绑定服务器**）：切换服务器/登出清空对应分区防串号；本地模式数据（LOCAL 键）独立保留，配服务器后**上传前询问**（「有 N 条本地笔记，是否上传」）
- 同步：outbox（`dirty` + tombstone）先推 create/update（带 `baseUpdatedAt`）/delete，再 `GET /api/notes?updatedAfter=` 增量拉取合并；冲突对齐 NOTE_CONFLICT（覆盖/重载）；WorkManager 后台周期 + 网络恢复触发 + 手动「同步」按钮
- 媒体：离线上传的媒体先传回填服务端 URL；服务器媒体按需下载缓存本地文件
- 安全边界：本地笔记明文存设备（不加密，设备级信任）；进离线清 token，拿到设备即可读
- 边界：管理员移动端按用户切换笔记视图不支持（离线优先分区 = 本人笔记）；Web/iOS 不做离线模式
