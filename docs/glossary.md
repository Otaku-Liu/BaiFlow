# 术语表

BaiFlow 涉及的关键术语速查。按字母序。

## A

- **Auth Session（登录会话）**
  长会话 token + 服务端逐请求校验（`bf_auth_session`）的认证模型：吊销驱动，ANDROID 滑动续期（180 天不活跃兜底）/ WEB 固定 2h。见 `docs/09-auth-sessions.md`。

- **强制下线（Force Logout）**
  吊销某设备的登录会话，对方下次请求 401 被踢回登录。Web 个人资料弹窗「登录设备」里操作。

- **吊销驱动（Revocation-driven）**
  会话寿命不由固定到期决定，而是直到被主动吊销（登出 / 强制下线 / 改密码）才失效——类似聊天软件「登录后基本不下线」。

## B

- **bf_note**
  随手记笔记表：`id` / `user_id` / `title` / `content`（Markdown 源）/ `status`（ACTIVE / DELETED 软删除）/ 时间戳。
- **bf_note_progress**
  笔记阅读进度表：`(user_id, note_id)` 唯一，`position_type` + `position_value`，本期只有 `SCROLL_PERCENT`。

## C

- **Compose Cupertino（`io.github.alexzhirkevich:cupertino`）**
  Kotlin/Compose 生态的 iOS 风格组件库（CupertinoButton / SegmentedControl / Switch 等，含 `cupertino-adaptive` 自动按平台切换）。可为 Android 提供真 iOS 外观，但**需把 app 迁到 Jetpack Compose（重写）**——BaiFlow 当前不采用，远期可选。

## D

- **设计系统（Design System）**
  一套集中的样式/组件规范，一处定义、全局复用。BaiFlow Android 用 `styles_ios.xml` + 主题 + drawable 集中定义 iOS 风组件样式，布局通过 `@style/Ios.*` 引用继承，避免逐控件手改。见 `docs/08-ios-design-system.md`。

## I

- **增量同步（incremental sync）**
  客户端用 `GET /api/notes?updatedAfter=<时间戳>` 只拉取更新的记录，配合本地缓存做增量合并，是 Android 离线同步的基础。

## L

- **乐观并发（Optimistic Concurrency）**
  冲突处理策略：保存携带 `baseUpdatedAt`，服务端比对——若被其他设备改过则返回 `NOTE_CONFLICT`，客户端弹「覆盖 / 重新加载」由用户决定，不再静默丢改动。取代了早期「后写覆盖（last-write-wins）」。

## M

- **Markdown**
  笔记正文格式。随手记笔记 Web 端用 Vditor 编辑器（IR 即时渲染）编辑/渲染；文件预览抽屉的 .md 文件预览用 showdown 渲染为 HTML；Android 端用所见即所得富文本编辑器（工具栏实现加粗/标题/列表等，不手写源码），存储仍为 Markdown 源。

## N

- **NOTE_UPDATED**
  SSE 事件类型：笔记被编辑保存后推送 `{ noteId, updatedAt }`，笔记所有者的浏览器收到后自动刷新。

## O

- **Offline Mode（离线模式）**
  Android 三态之一（本地模式 / 在线模式 / 离线模式）：已配服务器但主动离线（清 token 留缓存），随手记用本地镜像 + outbox，文件中心禁用；重连必须重新登录。本地模式（未配服务器）免登录纯本地；在线模式（服务器 + token）全功能。见 `docs/12-android-offline-mode.md`。

- **Outbox（离线变更队列）**
  离线模式下本地笔记的待同步标记（`dirty + source`），重连登录后先推 outbox（create/update 带 `baseUpdatedAt`、TOMBSTONE 删除），再按 `updatedAfter` 拉增量合并。Android 离线编辑时写入本地队列，恢复联网后逐个推送。见 `docs/12-android-offline-mode.md`。

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

- **SCROLL_PERCENT**
  进度类型：滚动百分比（0.0~1.0）。用于文本、Markdown、笔记等长内容，跨设备续读。
- **SSE（Server-Sent Events）**
  服务端单向实时推送（`text/event-stream`）。`GET /api/events` 已实现（`com.baiflow.event`：`SseService` 用户连接注册表 + `EventController` + 定时心跳清理），已推送 `NOTE_UPDATED` 事件；`TRANSFER_PROGRESS` / `DOWNLOAD_COMPLETED` / `DOWNLOAD_FAILED` / `NOTIFICATION_CREATED` 为已定义待接入。
- **Server Connection Timeout（服务器连接超时）**
  Web 管理台对**网络级失败**（连不上/连接超时/断网）的处理：距上次成功联系 ≥30s 且发生一次失败即判定超时，提示后返回登录页，**保留会话 token**，登录页提供「重新连接」一键恢复。见 `docs/10-web-connection-timeout.md`。

- **UiCallback（Android 网络回调包装）**
  Android 交互层统一网络错误处理的 Retrofit `Callback` 包装：`onResponse` 全局记录成功联系 + 兜底 5xx，`onFailure` 全局分类提示（无网络/无法连接服务器），页面只写业务处理。后台传输服务用 `execute()` 不经过它，因此不弹 UI。见 `docs/11-android-network-error.md`。

- **SseEmitter**
  Spring 的 SSE 实现：服务端保持连接，向客户端推事件。

## V

- **viewUserId**
  管理员视角切换参数：管理员传入后以指定用户的身份查看其笔记/文件。非管理员忽略。

## W

- **WorkManager**
  Android 后台任务调度（本项目传输功能已用）。随手记离线同步用它实现"恢复联网 / 周期"触发同步。

## 随手记相关流程速览

- **内容同步**：编辑保存 → 服务端 `updated_at=now` → SSE 推 `NOTE_UPDATED` → 其他端刷新
- **阅读进度**：滚动 → 防抖保存 `bf_note_progress` → 换端打开时提示"续读到 X%"
- **离线（Phase 3）**：离线编辑 → Room + outbox → 联网 → 推 PATCH + `updatedAfter` 拉取合并 → 冲突对齐在线端乐观并发（覆盖 / 重载）
