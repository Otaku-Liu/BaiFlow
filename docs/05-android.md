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
  ui/activity/ # 独立页面 Activity（登录/预览/资料/密码/语言/笔记编辑/画画）
  ui/fragment/ # 主界面 Fragment（Files / Notes / Mine）
  widget/      # 自定义控件（按压渐变 AnimatedTextButton / AnimatedTextLabel / AnimatedTintImageView）
  util/        # 通用工具
```

## 登录态

SharedPreferences 保存会话 token（长期保持）。**服务器地址固定由构建类型决定，不再手动配置**：从 `baiflow-android/local.properties` 读取 `BAIFLOW_RELEASE_SERVER_URL`（release 打包连线上）/ `BAIFLOW_DEBUG_SERVER_URL`（debug 调试），经 `BuildConfig.SERVER_URL` 供 `SessionManager.getServerUrl()` 直接返回。`local.properties` 被 git 忽略、真实地址不提交，模板见 `baiflow-android/local.properties.example`；未配置时回落占位默认值。**换后端 = 改本地 `local.properties` 重新打包**，App 内无服务器配置界面。登录带 `X-Device-Type` / `X-Device-Name` 头，服务端据此建会话（ANDROID 长期、180 天不活跃兜底）；被强制下线/过期后 401 清会话回登录。详见 `docs/02-database.md`「auth_session」。

## 上传下载

- 小文件 Retrofit multipart 上传；长任务前台服务（UploadService / DownloadService）带通知
- **上传实现**：
  - 选择器 `GetMultipleContents` **多选** → `UploadService` **顺序队列**（`ConcurrentLinkedQueue`，一个传完才传下一个）；`content://` URI 经 `ContentResolver` 读入；**mime 按扩展名解析**；进度按已写字节上报
  - **占位行**：列表顶部渲染，外观与真实文件行一致（图标/文件名，meta 位换横向进度条 + 百分比），随列表滚动、空目录也显示；仅当上传目标目录 = 当前目录时展示；点击弹「取消上传」——**上传中项取消续下一个、排队项直接移出队列**
  - **完成过渡**：占位行就地换真实行（响应 `FileItem`，meta 显示真实大小）再 `loadFiles()` 归位；失败/取消移除占位 + Toast
  - 上传到**隐私文件夹**透传 `effectivePrivacyToken()`
  - **通知（单条前台）**：「第 i/N 个 fileName N%」+「取消」动作；队列耗尽 `STOP_FOREGROUND_DETACH` 保留结果；不做暂停/断点续传
  - Android 13+ 需运行时授权 `POST_NOTIFICATIONS`
- 下载计次由后端记录（见 `docs/02-database.md`、`docs/03-api.md`）
- **下载落公共 Download 文件夹**：API 29+ 走 `MediaStore.Downloads`（作用域存储，无需权限，系统「下载」/文件管理器可见）；API 26-28 写 `Environment.DIRECTORY_DOWNLOADS`，下载前申请 `WRITE_EXTERNAL_STORAGE`
- **不支持预览的文件**：点按弹「暂不支持在线预览」对话框（含「下载」按钮），**不自动下载**，手动点「下载」才下载
- **预览渲染**：视频/音频用 Media3 ExoPlayer（视频 `PlayerView`、音频 `PlayerControlView`，正确处理旋转元数据、宽高比、控制器时长）；横屏视频按「旋转 90/270 或有效宽高为横」自动转横屏，**离开时若强制过横屏则恢复传感器方向**（让旋转发生在预览页自身，避免把旋转带回文件列表触发其重建）

## 浏览进度（跨端同步）

- 文件预览：视频/音频存 **SECONDS**（10s 定时 + 退出时保存）；文本/Markdown 存 **SCROLL_PERCENT**（滚动防抖 2s，回顶保存 0 清除历史）；打开时自动 seek/滚动到记录位置并 Toast「已恢复到上次观看位置」
- 随手记：编辑器滚动防抖 800ms 上报 SCROLL_PERCENT，打开时自动滚动到记录位置；短笔记（不足一屏）不记录进度
- 与 Web 共用服务端 `bf_playback_progress` / `bf_note_progress`，按用户存、不记设备；视频/音频仅在播放过（played）后才允许存 0，避免未播放关闭时误清历史

## 页面

- 登录 → **MainActivity**（底部三栏，`ViewPager2` 滑动 + 底部导航双向同步）；首启未登录直接进登录页（服务器已固定，无引导页/服务器配置）
  - **文件** `FilesFragment`：标题居中；左上「返回上一级」图标（根目录置灰）、右上「刷新」+「三点」菜单（下拉：新建文件夹 / 上传文件）；长按文件/文件夹弹操作菜单（重命名 / 下载 / 删除，重命名走 `PATCH /api/files/{id}/rename`）；文件列表按类型用彩色 PNG 图标（md/pdf/json/xml/word/excel/ppt 等），**mime 判断后按扩展名兜底**（服务端 mime 可能被硬编码为 octet-stream，扩展名兜底保证 mp4/mkv 等显示视频图标）；不支持预览的文件点按弹下载确认框；**目录导航持久化**：`folderStack`（当前..根）序列化存 `SharedPreferences`（`files_nav`），每次进/退目录与 `onSaveInstanceState` 写入，Activity 重建/进程回收/冷启动时兜底恢复当前目录（**冷启动落在上次目录**），从预览返回不再丢目录；登出清除。**隐私空间**：主目录下「隐私空间」文件夹，首次进入弹「设置密码」（`POST privacy`，40107），之后进入弹「输入密码」（`verifyPrivacy` 换令牌）；令牌仅存内存（`privacyTokens`），重进需重输；管理员访问后端直接放行
  - **随手记** `NotesFragment`：列表/搜索/删除 → `NoteEditActivity`（**所见即所得块编辑器**：RecyclerView 每块一个真实 View，文本 EditText 经 `BlockRichText` 渲染行内 markdown 的格式效果、编辑即预览，图片 ImageView，音频 `NoteAudioPlayerView`；加载 Markdown→`NoteBlocks.fromDoc`、保存 `NoteBlocks.toDoc`→Markdown，落库仍是 Markdown）→ `NoteDrawActivity`（画画）
  - **我的** `MineFragment`：分组（账号/通用/传输记录/同步）
    - **传输记录**：「上传记录 / 下载记录」两个入口 → `RecordsActivity`（**按入口直达类型，页内无 Tab 切换**；默认查当天，时间/文件名/来源过滤 + 重置；admin 按用户查看）。**记录行** = 类型图标 + 文件名 + 时间（不展示用户/IP）；长摁弹详情（复用 `dialog_file_info.xml`）支持**下载**（计入下载记录）、**删除源文件**（`40401` 提示「源文件不存在」）与（仅下载记录且本机下载过）**打开文件 / 保存位置**：`ACTION_VIEW` 系统打开（API 29+ MediaStore URI / API 26-28 FileProvider），位置可点按复制；本地位置由 `DownloadLocationStore` 按 `fileId` 持久化。**查询区 = 卡片表单行**：时间/来源/用户（admin）为「标签 + 值 + chevron」字段行，来源/用户用 `DropdownMenu`，时间点开 DatePicker，文件名 `bg_field` 输入框 + `Ios.Button.Primary` 查询 + `Ios.Button.Text` 重置；字号统一 14sp
    - 修改资料 / 修改密码 / 语言为独立页面；退出登录二次确认 + `doLogout()` 幂等守卫
    - **头像**：有 `avatarUrl` 时 OkHttp 圆形展示（`AvatarLoader`），无则浅灰底 + 首字；`ProfileActivity` 支持更换头像（96dp 圆形 + 贴底「编辑」带，选图即上传，`ImageUtil` 缩放/EXIF/压缩 ≤1MB → `POST /api/users/me/avatar`，见 `docs/07-ios-design-system.md`）；Android 不提供删除头像
    - `onResume` 在线模式调 `/users/me` 刷新本地用户信息（头像/展示名可能在其他端修改）

## 多语言（i18n）

默认中文 + 英文。语言持久化到 `SessionManager`（`getLanguage`/`saveLanguage`，SharedPreferences），由 `BaseActivity.attachBaseContext` 应用到每个 Activity（`createConfigurationContext` + `Locale.setDefault`），`applyOverrideConfiguration` 保留深/浅色 UI 模式；全部 Activity 继承 `BaseActivity`。切换语言 = `saveLanguage` + **`CLEAR_TASK` 重启回主界面**（任务内页面按新语言重建，普通启动路径走 `windowBackground` 应用底色，不闪黑）。首次使用未设置语言时返回原样 Context，走系统默认语言。布局 `@string/key`、Java `getString(R.string.key)`、带参 `%1$s`。

## 失败处理

- 网络失败：`network/NetworkFeedback`（静态单例）+ `network/UiCallback<T>` 统一处理。**网络失败 ≠ 会话失效**：断网/服务器宕机不清会话、不回登录页，用户留在当前页可重试。
  - 交互请求（UiCallback）：收到任何 HTTP 响应视为成功联系并清除断连标记；IOException 按 `classify()` 分类（设备无网→「无网络连接」，有网但请求失败→「无法连接服务器」）；HTTP 5xx 全局兜底「服务器异常」。
  - 全局统一 Toast + **时段内去重**（同一断连时段只提示一次，任一次响应成功即清除），断连结束后弹一次「网络已恢复」。
  - 断网由 `MainActivity` 注册 `ConnectivityManager` NetworkCallback 即时提示（onLost→「无网络连接」 / onAvailable→恢复），onDestroy 注销；覆盖文件/笔记/我的三个 Tab。
  - 登录页：onFailure 用 `classify()` 友好文案**内联**提示，不触发全局 Toast（避免双弹）。
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

## 随手记（在线编辑器）

### 编辑器取舍
- Android 行内强调嵌套可能摊平为相邻 run、有序列表总是从 1 开始、块间空行归一化——**代码块内容/标题标记/媒体 URL/任何文本绝不丢失**（透传保证）
- 引用块已移除：旧引用内容映射为普通文本块（保留内容，丢弃 `>` 前缀）；下划线用 `<u>` 表示

### 在线同步（Room + outbox）
> 仅在线模式（已登录即在线）；本地模式与离线模式均已移除。
- 全功能，笔记同步
- Room 表 `bf_local_note` 按 `server_url` 分区（**缓存绑定服务器**）：登出清空对应分区防串号；升级前遗留的本地分区数据（LOCAL 键）独立保留，首次登录后**上传前询问**（「有 N 条本地笔记，是否上传」）
- 同步：outbox（`dirty` + tombstone）先推 create/update（带 `baseUpdatedAt`）/delete，再 `GET /api/notes?updatedAfter=` 增量拉取合并（增量模式列表携带正文，直接合并，无 N+1）；**另有 SSE 长连接**（`NoteSseClient`，手写解析 `/api/events`，收到 `NOTE_UPDATED` 立即触发一次增量同步）——实时 + 周期兜底；WorkManager 后台周期 + 网络恢复触发 + 手动「同步」按钮
- **冲突**：乐观并发（`baseUpdatedAt` 必传，缺失 40001 / 早于服务端 40901）；冲突弹窗**先拉服务端版本做块级差异预览**（本地改动 N 块 / 服务端改动 M 块 + 前 3 块预览），用户再选「覆盖」（以服务端最新 updatedAt 为基准重推）或「重新加载」
- **同步状态可见性**：笔记列表项右侧**徽标**（冲突=红 / 待推=灰，`LocalNote.dirty/conflict`）；「我的」页同步区显示「待同步 N 条 · 冲突 M 条」（`LocalNoteDao.countDirty/countConflict`，有未同步改动时显示）
- 媒体：本地新建媒体先上传回填服务端 URL；服务器媒体按需下载缓存到 `filesDir/note_media_cache/<id>`（**批量接口 + 有界并发**：`POST /api/notes/media/batch` 每批 ≤10、3 批并行；服务端跳过的大文件/失败项回退单个流式下载）。**缓存管理**：「我的」页「存储」分组——「清理缓存」行（右侧显示大小，二次确认后清空 `note_media_cache/`，清除后需重新下载）+「缓存上限」行（SeekBar 50–2000MB 默认 300MB）；同步写完媒体后自动按上限 LRU 清理（`MediaFiles.enforceLimit`）。本地新建媒体 `note_media/`（可能未上传）**永不自动清理**。
- 安全边界：本地笔记明文存设备（不加密，设备级信任）
- 边界：管理员移动端按用户切换笔记视图不支持（分区 = 本人笔记）
