# 术语表

BaiFlow 涉及的关键术语速查。按字母序。

## A

- **Auth Session（登录会话）**
  长会话 token + 服务端逐请求校验（`bf_auth_session`）的认证模型：吊销驱动，ANDROID 滑动续期（180 天不活跃兜底）/ WEB 固定 2h。见 `docs/02-database.md`「auth_session」、`docs/03-api.md`「认证」。

- **强制下线（Force Logout）**
  吊销某设备的登录会话，对方下次请求 401 被踢回登录。Web 个人资料弹窗「登录设备」里操作。

- **吊销驱动（Revocation-driven）**
  会话寿命不由固定到期决定，而是直到被主动吊销（登出 / 强制下线 / 改密码）才失效——类似聊天软件「登录后基本不下线」。

## B

- **BaseActivity（应用语言基类）**
  Android 公共 Activity 基类：`attachBaseContext` 应用持久化语言，`applyOverrideConfiguration` 保留 UI 模式；语言切换「持久化 + CLEAR_TASK 重启」避免全任务重建闪黑屏。全 app Activity 继承。见 `docs/05-android.md`。
- **bf_note**
  随手记笔记表：`id` / `user_id` / `title` / `content`（Markdown 源）/ `status`（ACTIVE / DELETED 软删除）/ 时间戳。
- **bf_note_progress**
  笔记阅读进度表：`(user_id, note_id)` 唯一，`position_type` + `position_value`，本期只有 `SCROLL_PERCENT`。
- **Bf 类名前缀（Bf Naming Prefix）**
  绑定 `bf_*` 表的后端类统一加 `Bf` 前缀、按表名命名（Entity / Mapper / Service(+Impl) / Controller），如 `bf_share_link` → `BfShareLink` / `BfShareLinkMapper` / `BfShareLinkService`；无单一主表的业务类（`AuthService`、`PublicShareController`、`HealthService` 等）不带 `Bf`；DTO / VO / Request / enum 保持原名。见 `docs/06-coding-standards.md`。

## C

- **Compose Cupertino（`io.github.alexzhirkevich:cupertino`）**
  Kotlin/Compose 生态的 iOS 风格组件库（CupertinoButton / SegmentedControl / Switch 等，含 `cupertino-adaptive` 自动按平台切换）。可为 Android 提供真 iOS 外观，但**需把 app 迁到 Jetpack Compose（重写）**——BaiFlow 当前不采用，远期可选。

## D

- **设计系统（Design System）**
  一套集中的样式/组件规范，一处定义、全局复用。BaiFlow Android 用 `styles_ios.xml` + 主题 + drawable 集中定义 iOS 风组件样式，布局通过 `@style/Ios.*` 引用继承，避免逐控件手改。见 `docs/07-ios-design-system.md`。

## I

- **I18nUtil**
  服务端语言工具类（`com.baiflow.common.util.I18nUtil`）：`translate(String)` 以「中文文案即 key」在 `i18n/messages*.properties` 查词条，按当前请求 `Accept-Language` 返回中/英，找不到（如动态拼接内容）原样返回中文。全局异常处理器用它统一翻译 `BusinessException` 的 `message`。
- **错误码（ErrorCode）**
  5 位数字业务错误码（`0` 成功 / `400xx` 参数 / `401xx` 认证 / `403xx` 权限 / `404xx` 不存在 / `409xx` 冲突 / `410xx` 过期 / `423xx` 锁定 / `429xx` 超限 / `500xx` 内部），定义见后端 `ErrorCode.java`，客户端按数字码区分业务分支。见 `docs/03-api.md` 错误码表。
- **增量同步（incremental sync）**
  `GET /api/notes?updatedAfter=<时间戳>` 只拉更新记录（含软删除），增量模式列表携带正文直接合并（无 N+1）；Android 在线另用 SSE 长连接（`NoteSseClient`）实时触发，与 WorkManager 周期并存。见 `docs/05-android.md`。

## L

- **乐观并发（Optimistic Concurrency）**
  保存**必须**携带 `baseUpdatedAt`（缺失 → `40001`；早于服务端 `updated_at`（DATETIME(3) 毫秒）→ `40901`）。冲突弹窗前先展示双方块级差异，再选「覆盖」（以服务端最新 `updatedAt` 为基准重推）/「重新加载」。见 `docs/05-android.md`。
- **登录失败锁定（Login Lock）**
  Redis 滑动窗口防暴力破解：15 分钟连续失败 5 次锁定 15 分钟，达阈值持久化 `LOCKED`、到期自动恢复 `NORMAL`（`LOCKED` 仅自动维护，管理员仅禁用）；Redis 不可用时检查 fail-open、解锁判定 fail-closed。见 `docs/01-architecture.md`。
- **lastOpenedAt（上次打开时间）**
  `bf_file_item.last_opened_at`：文件预览/下载、进入目录时更新（分享下载不更新），Android 长摁弹窗展示。见 `docs/02-database.md`。

## M

- **Markdown**
  笔记正文格式。Web/Android 均为块编辑器（块存「行内 markdown 源」、存储仍是 Markdown）；.md 文件预览用 showdown 渲染。

- **MaterialAlertDialogBuilder**
  Material Components 的 AlertDialog 构建器（`com.google.android.material.dialog`）：从 `materialAlertDialogTheme` 解析 shape，用 `ShapeAppearanceDrawable` **程序化设置弹窗背景**，因此圆角一定生效。BaiFlow Android 全项目弹窗统一用它构建（不再用 appcompat `AlertDialog.Builder`），16dp 圆角全局生效。见 `docs/07-ios-design-system.md`。
- **materialAlertDialogTheme / alertDialogTheme**
  语义不同的两个主题属性：前者供 `MaterialAlertDialogBuilder` 读取、后者供 appcompat `AlertDialog.Builder` 读取，**不可混用**（配错则弹窗圆角不生效）。见 `docs/07-ios-design-system.md`。
- **幂等守卫（Idempotent Guard）**
  Android 防重复执行的轻量手段：方法内加标志位（如 `logoutStarted`），首行 `if (flag) return; flag = true;`，后续任何重复触发直接返回。用于 `doLogout()` 兜底连点/双弹窗导致的重复执行（重复 `startActivity` + `finish` 会触发 Fragment detach 闪退）。见 `docs/05-android.md`。

- **媒体缓存（Media Cache）**
  笔记媒体离线缓存：服务端媒体缓存 `note_media_cache/`（可重下，「我的」页可手动清理 + 上限 50–2000MB 超限 LRU）；离线新建媒体 `note_media/`（可能未上传）**永不自动清理**。见 `docs/05-android.md`。

## N

- **NOTE_UPDATED**
  SSE 事件类型：笔记被编辑保存后推送 `{ noteId, updatedAt }`，笔记所有者的浏览器收到后自动刷新。

## O

- **离线模式（Offline Mode）**
  Android **仅在线模式**（已登录即在线），无主动离线看缓存。服务器地址固定由构建类型决定（读本地 `local.properties`，真实地址 git 忽略）。见 `docs/05-android.md`。

- **Outbox（本地待同步队列）**
  本地笔记的待同步标记（`dirty + source`），编辑保存后先推 outbox（create/update 带 `baseUpdatedAt`、TOMBSTONE 删除），再按 `updatedAfter` 拉增量合并。见 `docs/05-android.md`。

## P

- **PlaybackProgress / bf_playback_progress**
  播放/阅读进度表（视频秒数、PDF 页码、文本滚动百分比）。笔记进度沿用其 `SCROLL_PERCENT` 思路，但用独立的 `bf_note_progress` 表。
- **position_type / position_value**
  进度表的类型与值字段。取值：`SECONDS`（音视频秒数）、`PAGE`（PDF 页码）、`SCROLL_PERCENT`（滚动百分比 0~1）。

## R

- **Room**
  Android 官方 SQLite ORM。随手记同步用它本地缓存笔记列表与正文（在线模式）。

## S

- **SegmentedControl（分段控件）**
  iOS 风格的分段选择控件（类似 iOS 的分段控制器）。BaiFlow Android 设计系统中作为自定义组件（`ui/widget/SegmentedControl`）提供。

- **styles_ios.xml**
  BaiFlow Android 设计系统的组件样式定义文件，集中定义按钮 / 输入框 / 卡片 / 开关 / 分段控件 / 标题栏等 `@style/Ios.*`，所有布局引用继承。
- **sort 排序参数（文件列表）**
  `GET /api/files` 的 `sort`（name/createdAt/size）+ `dir`：**目录始终优先**，非法值回落 `name`。见 `docs/03-api.md`。

- **SCROLL_PERCENT**
  进度类型：滚动百分比（0.0~1.0）。用于文本、Markdown、笔记等长内容，跨设备续读。
- **SSE（Server-Sent Events）**
  服务端单向实时推送（`text/event-stream`）。`GET /api/events` 维护「用户 → 连接」注册表 + 心跳清理，仅推送 `NOTE_UPDATED`（笔记跨端刷新）。
- **Server Connection Timeout（服务器连接超时）**
  Web 对网络级失败的处理：距上次成功联系 ≥30s 判定超时，返回登录页（**保留 token**）+「重新连接」。见 `docs/04-frontend.md`。
- **SseEmitter**
  Spring 的 SSE 实现：服务端保持连接，向客户端推事件。

## U

- **UiCallback（Android 网络回调包装）**
  Retrofit `Callback` 包装：统一成功联系记录 + 失败分类提示，页面只写业务；后台传输用 `execute()` 不弹 UI。见 `docs/05-android.md`。
- **上传占位行（Upload Placeholder Row）**
  文件中心上传时，列表顶部渲染的、外观与真实文件行一致（图标 + 文件名 + 原 meta 位置换成进度条/百分比）的临时行，由 `UploadService` 任务队列（多文件顺序上传）驱动；上传完成用响应 `FileItem` 原位换真后再按排序归位，失败/取消即移除并提示；纯客户端机制，不新增后端记录。见 `docs/05-android.md`。

## V

- **viewUserId**
  管理员视角切换参数：管理员传入后以指定用户的身份查看其笔记/文件。非管理员忽略。

## W

- **WorkManager**
  Android 后台任务调度（本项目传输功能已用）。随手记离线同步用它实现"恢复联网 / 周期"触发同步。
- **文件夹大小 / 项数**
  Web 显示子项数（`childCount`），Android 长摁弹窗显示递归字节大小（`GET /api/files/{id}/size`，递归 CTE）；隐私文件夹两项均不提供。见 `docs/02-database.md`。

- **文件下载记录（Download Record）**
  每次下载写入 `bf_download_record`（直接下载记下载人 CLIENT / 分享下载关联分享 ID SHARE），供次数统计与审计；下载仅限登录用户或有效分享链接。见 `docs/02-database.md`。

## 随手记相关流程速览

- **内容同步**：编辑保存 → 服务端 `updated_at=now` → SSE 推 `NOTE_UPDATED` → 其他端刷新
- **阅读进度**：滚动 → 防抖保存 `bf_note_progress` → 换端打开时自动恢复到记录位置并提示「已恢复到上次观看位置」；回顶保存 0 清除历史
- **在线同步**：编辑保存 → outbox 推 PATCH → `updatedAfter` 增量拉取合并 → 冲突对齐乐观并发（覆盖 / 重载）
