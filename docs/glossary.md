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
  Android 公共 Activity 基类：`attachBaseContext` 把 `SessionManager` 持久化的应用语言应用到每个 Activity（`createConfigurationContext`），`applyOverrideConfiguration` 保留 UI 模式。语言切换用「持久化 + CLEAR_TASK 重启回主界面」，替代 `AppCompatDelegate.setApplicationLocales`（其全任务重建会闪黑屏）。全 app 10 个 Activity 继承它。见 `docs/05-android.md`。
- **bf_note**
  随手记笔记表：`id` / `user_id` / `title` / `content`（Markdown 源）/ `status`（ACTIVE / DELETED 软删除）/ 时间戳。
- **bf_note_progress**
  笔记阅读进度表：`(user_id, note_id)` 唯一，`position_type` + `position_value`，本期只有 `SCROLL_PERCENT`。

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
  客户端用 `GET /api/notes?updatedAfter=<时间戳>` 只拉取更新的记录，配合本地缓存做增量合并，是 Android 离线同步的基础。

## L

- **乐观并发（Optimistic Concurrency）**
  冲突处理策略：保存携带 `baseUpdatedAt`，服务端比对——若被其他设备改过则返回 `40901`（NOTE_CONFLICT），客户端弹「覆盖 / 重新加载」由用户决定，不再静默丢改动。取代了早期「后写覆盖（last-write-wins）」。
- **登录失败锁定（Login Lock）**
  防暴力破解：Redis 滑动窗口（15 分钟内连续失败 5 次锁定 15 分钟），多实例共享。登录时的锁定检查 Redis 不可用时 fail-open 降级（跳过锁定，保证登录可用）；但解锁判定（恢复 `NORMAL`）为 fail-closed——Redis 不可用时保守按仍锁定处理，避免误解锁。达到阈值时除写入 Redis 锁键外，同时将用户状态持久化为 `LOCKED`；锁键到期后由定时任务（`LoginLockScheduler`，每 60s 扫描）或登录时的兜底判定恢复为 `NORMAL`。`LOCKED` 仅由自动锁定维护，管理员仅支持禁用。原基于内存 `ConcurrentHashMap` 的实现已迁移至 Redis。
- **lastOpenedAt（上次打开时间）**
  `bf_file_item.last_opened_at`：文件/文件夹最近一次「打开」时间。文件在**预览或下载**时更新（`downloadFile` 统一 touch，预览复用该方法）；文件夹在**进入该目录**时更新（`listFiles` 传非空 `parentId`）。分享下载走独立链路不更新。更新时显式 `SET updated_at = updated_at`，避免打开动作刷新「修改时间」。Android 长摁弹窗简介区展示。见 `docs/02-database.md`。

## M

- **Markdown**
  笔记正文格式。随手记笔记 Web 端与 Android 端均为**所见即所得块编辑器**（块存「行内 markdown 源」，contenteditable/Spannable 就地渲染行内格式、编辑即预览；Web 用 showdown+turndown 往返，Android 用 BlockRichText 往返），存储仍为 Markdown 源；文件预览抽屉的 .md 文件预览用 showdown 渲染为 HTML。

- **MaterialAlertDialogBuilder**
  Material Components 的 AlertDialog 构建器（`com.google.android.material.dialog`）：从 `materialAlertDialogTheme` 解析 shape，用 `ShapeAppearanceDrawable` **程序化设置弹窗背景**，因此圆角一定生效。BaiFlow Android 全项目弹窗统一用它构建（不再用 appcompat `AlertDialog.Builder`），16dp 圆角全局生效。见 `docs/07-ios-design-system.md`。
- **materialAlertDialogTheme / alertDialogTheme**
  两个语义不同的主题属性：`materialAlertDialogTheme` 供 `MaterialAlertDialogBuilder` 读取（parent 应为 `ThemeOverlay.MaterialComponents.MaterialAlertDialog`）；`alertDialogTheme` 供 appcompat `AlertDialog.Builder` 读取（parent 应为 appcompat 链）。**不可混用**——把 MaterialAlertDialog 主题配到 `alertDialogTheme` 上，appcompat 弹窗不会套 shape，表现为直角/默认圆角。见 `docs/07-ios-design-system.md`。
- **幂等守卫（Idempotent Guard）**
  Android 防重复执行的轻量手段：方法内加标志位（如 `logoutStarted`），首行 `if (flag) return; flag = true;`，后续任何重复触发直接返回。用于 `doLogout()` 兜底连点/双弹窗导致的重复执行（重复 `startActivity` + `finish` 会触发 Fragment detach 闪退）。见 `docs/05-android.md`。

## N

- **NOTE_UPDATED**
  SSE 事件类型：笔记被编辑保存后推送 `{ noteId, updatedAt }`，笔记所有者的浏览器收到后自动刷新。

## O

- **Offline Mode（离线模式）**
  Android 三态之一（本地模式 / 在线模式 / 离线模式）：已配服务器但主动离线（清 token 留缓存），随手记用本地镜像 + outbox，文件中心禁用；重连必须重新登录。本地模式（未配服务器）免登录纯本地；在线模式（服务器 + token）全功能。见 `docs/05-android.md`「离线三态」。

- **Outbox（离线变更队列）**
  离线模式下本地笔记的待同步标记（`dirty + source`），重连登录后先推 outbox（create/update 带 `baseUpdatedAt`、TOMBSTONE 删除），再按 `updatedAfter` 拉增量合并。Android 离线编辑时写入本地队列，恢复联网后逐个推送。见 `docs/05-android.md`「离线三态」。

## P

- **PlaybackProgress / bf_playback_progress**
  播放/阅读进度表（视频秒数、PDF 页码、文本滚动百分比）。笔记进度沿用其 `SCROLL_PERCENT` 思路，但用独立的 `bf_note_progress` 表。
- **position_type / position_value**
  进度表的类型与值字段。取值：`SECONDS`（音视频秒数）、`PAGE`（PDF 页码）、`SCROLL_PERCENT`（滚动百分比 0~1）。

## R

- **Room**
  Android 官方 SQLite ORM。随手记离线功能用它缓存笔记列表与正文，支持离线查看/编辑。

## S

- **SegmentedControl（分段控件）**
  iOS 风格的分段选择控件（类似 iOS 的分段控制器）。BaiFlow Android 设计系统中作为自定义组件（`ui/widget/SegmentedControl`）提供。

- **styles_ios.xml**
  BaiFlow Android 设计系统的组件样式定义文件，集中定义按钮 / 输入框 / 卡片 / 开关 / 分段控件 / 标题栏等 `@style/Ios.*`，所有布局引用继承。
- **sort 排序参数（文件列表）**
  `GET /api/files` 的 `sort`（name / createdAt / size）+ `dir`（asc / desc）参数：**任何排序都目录优先**（后端 `childrenWrapper` 先 `orderByDesc(itemType)`），非法值回落 `name`；`dir` 缺省按惯例（名称升序 / 创建时间降序 / 大小降序）。Android 工具栏排序按钮保持 `ic_sort` 图标，排序菜单当前项用「>」图标（`ic_chevron_up`/`ic_chevron_down`）指示方向，再点当前项切换升/降序。见 `docs/03-api.md`。

- **SCROLL_PERCENT**
  进度类型：滚动百分比（0.0~1.0）。用于文本、Markdown、笔记等长内容，跨设备续读。
- **SSE（Server-Sent Events）**
  服务端单向实时推送（`text/event-stream`）。`GET /api/events` 已实现（`com.baiflow.event`：`SseService` 用户连接注册表 + `EventController` + 定时心跳清理），当前仅推送 `NOTE_UPDATED`（笔记跨端同步刷新；曾规划的传输/下载/通知事件已移除）。
- **Server Connection Timeout（服务器连接超时）**
  Web 管理台对**网络级失败**（连不上/连接超时/断网）的处理：距上次成功联系 ≥30s 且发生一次失败即判定超时，提示后返回登录页，**保留会话 token**，登录页提供「重新连接」一键恢复。见 `docs/04-frontend.md`。

- **UiCallback（Android 网络回调包装）**
  Android 交互层统一网络错误处理的 Retrofit `Callback` 包装：`onResponse` 全局记录成功联系 + 兜底 5xx，`onFailure` 全局分类提示（无网络/无法连接服务器），页面只写业务处理。后台传输服务用 `execute()` 不经过它，因此不弹 UI。见 `docs/05-android.md`。

- **SseEmitter**
  Spring 的 SSE 实现：服务端保持连接，向客户端推事件。

## V

- **viewUserId**
  管理员视角切换参数：管理员传入后以指定用户的身份查看其笔记/文件。非管理员忽略。

## W

- **WorkManager**
  Android 后台任务调度（本项目传输功能已用）。随手记离线同步用它实现"恢复联网 / 周期"触发同步。
- **文件夹大小 / 项数**
  文件夹无固有大小。Web 文件中心**直接显示子项数**（`childCount`：该文件夹一级下活跃文件+子文件夹数，后端列表批量 `GROUP BY parent_id` 统计），文件列仍显示字节大小，表头「大小/项数」；Android 长摁弹窗显示**递归字节大小**（`GET /api/files/{id}/size`，MySQL 8 递归 CTE 按 `parent_id` 树汇总子树文件字节数，深度不限）。隐私文件夹两项均不提供（`childCount` 为 null、`/size` 目录分支从自身校验隐私）。见 `docs/02-database.md`、`docs/03-api.md`。

- **文件下载记录（Download Record）**
  每次下载（文件中心直接下载 / 分享下载）写入 `bf_download_record`，供文件中心下载次数统计与 ADMIN 审计。直接下载记录下载人（CLIENT）；分享下载下载人为空、关联分享 ID（SHARE）。下载通道仅两条：登录用户（owner/admin）或有效分享链接，无匿名直下。见 `docs/02-database.md`、`docs/03-api.md`。

## 随手记相关流程速览

- **内容同步**：编辑保存 → 服务端 `updated_at=now` → SSE 推 `NOTE_UPDATED` → 其他端刷新
- **阅读进度**：滚动 → 防抖保存 `bf_note_progress` → 换端打开时自动恢复到记录位置并提示「已恢复到上次观看位置」；回顶保存 0 清除历史
- **离线**：离线编辑 → Room + outbox → 联网 → 推 PATCH + `updatedAfter` 拉取合并 → 冲突对齐在线端乐观并发（覆盖 / 重载）
