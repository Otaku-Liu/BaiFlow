# Android 客户端设计

## 技术栈

Java + Retrofit + OkHttp + WorkManager + Foreground Service + SharedPreferences

## MVP 功能

- 登录、文件列表（底部三栏：文件 / 随手记 / 我的）、上传手机文件、下载服务器文件
- 长任务前台通知
- 文件页与 Web 对齐：自动使用第一个可用存储根；管理员可「查看用户」切换 viewUserId
- 随手记：富文本所见即所得笔记编辑器（加粗/标题/列表/引用/代码块 + 图片/录音/画画媒体），见 `docs/07-quick-notes.md`

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

SharedPreferences 保存会话 token（长期保持）与服务器地址。登录带 `X-Device-Type` / `X-Device-Name` 头，服务端据此建会话（ANDROID 长期、180 天不活跃兜底）；被强制下线/过期后 401 清会话回登录。详见 `docs/09-auth-sessions.md`。

## 上传下载

- 小文件 Retrofit multipart 上传；长任务前台服务（UploadService / DownloadService）带通知
- 下载计次由后端记录（见 `docs/13-download-records.md`）
- **下载落公共 Download 文件夹**：API 29+ 走 `MediaStore.Downloads`（作用域存储，无需权限，系统「下载」/文件管理器可见）；API 26-28 写 `Environment.DIRECTORY_DOWNLOADS`，下载前申请 `WRITE_EXTERNAL_STORAGE`
- **不支持预览的文件**：点按弹「暂不支持在线预览」对话框（含「下载」按钮），**不自动下载**，手动点「下载」才下载

## 浏览进度（跨端同步）

- 文件预览：视频/音频存 **SECONDS**（10s 定时 + 退出时保存）；文本/Markdown 存 **SCROLL_PERCENT**（滚动防抖 2s，回顶保存 0 清除历史）；打开时自动 seek/滚动到记录位置并 Toast「已恢复到上次观看位置」
- 随手记：编辑器滚动防抖 800ms 上报 SCROLL_PERCENT，打开时自动滚动到记录位置
- 与 Web 共用服务端 `bf_playback_progress` / `bf_note_progress`，按用户存、不记设备；视频/音频仅在播放过（played）后才允许存 0，避免未播放关闭时误清历史

## 页面

- 登录 / 服务器配置（连通性检测）→ **MainActivity**（底部三栏，`ViewPager2` 滑动 + 底部导航双向同步）
  - **文件** `FilesFragment`：标题居中；左上「返回上一级」图标（根目录置灰）、右上「刷新」+「新建」（下拉：新建文件夹 / 上传文件）；文件列表按类型用彩色 PNG 图标（md/pdf/json/xml/word/excel/ppt 等）；不支持预览的文件点按弹下载确认框
  - **随手记** `NotesFragment`：列表/搜索/删除 → `NoteEditActivity`（富文本）→ `NoteDrawActivity`（画画）
  - **我的** `MineFragment`：分组（账号/通用/同步）；修改资料 / 修改密码 / 语言为独立页面；退出登录二次确认

## 多语言（i18n）

默认中文 + 英文；「我的 → 语言」用 `AppCompatDelegate.setApplicationLocales` 持久化并重建；布局 `@string/key`、Java `getString(R.string.key)`、带参 `%1$s`。

## 失败处理

- 网络失败（见 `docs/11-android-network-error.md`）：`UiCallback` 统一处理——收到响应视为成功联系，IOException 按无网络/无法连接全局 Toast + 去重；HTTP 5xx 全局兜底；OkHttp 超时 10s；断网由 MainActivity 监听即时提示。后台传输服务不弹 UI。
- token 失效：`AuthInterceptor` 401 清会话回登录
- 上传/下载失败：任务保留 + 错误原因记录

## 设计系统（iOS 风格）

集中式 iOS 风格设计系统（`res/values/styles_ios.xml`，`@style/Ios.*`），新增样式一律加进该文件，不写硬编码色值/圆角（详见 `docs/08-ios-design-system.md`）：

- 组件：按钮（Primary / Text / **DangerOutline** 白底红字）、输入框、标题栏（居中标题 + 返回图标）、卡片、圆角弹窗（16dp）/ 下拉（12dp）
- **按压渐变**：`widget/` 的 `AnimatedTextButton` / `AnimatedTextLabel` / `AnimatedTintImageView`，文字/图标按压「蓝→浅蓝」平滑过渡（`text_accent_selector`）；返回/刷新/上一级用单色 PNG + tint 参与渐变
- 文件类型图标：彩色 PNG（`res/drawable/ic_type_*`，含 md；Web 端复用同一批 PNG 展示）

## 随手记（Phase 2 在线已实现；Phase 3 离线模式已实现）

- Phase 2（在线）：富文本编辑器 + 图片/录音/画画媒体，见 `docs/07-quick-notes.md`
- Phase 3（离线模式）：三态（本地/在线/离线），Room 本地缓存 + outbox + `updatedAfter` 增量合并 + WorkManager 后台同步，见 `docs/12-android-offline-mode.md`
