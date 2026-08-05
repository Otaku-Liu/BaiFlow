# 术语表

BaiFlow 涉及的关键术语速查。按字母序。

## B

- **bf_note**
  随手记笔记表：`id` / `user_id` / `title` / `content`（Markdown 源）/ `status`（ACTIVE / DELETED 软删除）/ 时间戳。
- **bf_note_progress**
  笔记阅读进度表：`(user_id, note_id)` 唯一，`position_type` + `position_value`，本期只有 `SCROLL_PERCENT`。

## I

- **增量同步（incremental sync）**
  客户端用 `GET /api/notes?updatedAfter=<时间戳>` 只拉取更新的记录，配合本地缓存做增量合并，是 Android 离线同步的基础。

## L

- **last-write-wins（后写覆盖）**
  冲突处理策略：同一篇笔记两端都改时，以服务端时间戳为准、后保存的覆盖先保存的。实现简单、可预测；代价是可能丢编辑。个人使用可接受。

## M

- **Markdown**
  笔记正文格式。随手记笔记 Web 端用 Vditor 编辑器（IR 即时渲染）编辑/渲染；文件预览抽屉的 .md 文件预览用 showdown 渲染为 HTML；Android 端用纯文本编辑器输入 Markdown 源。

## N

- **NOTE_UPDATED**
  SSE 事件类型：笔记被编辑保存后推送 `{ noteId, updatedAt }`，笔记所有者的浏览器收到后自动刷新。

## O

- **outbox（待同步队列）**
  Android 离线编辑时写入本地队列，恢复联网后逐个推送到服务端，实现离线可编辑。

## P

- **PlaybackProgress / bf_playback_progress**
  播放/阅读进度表（视频秒数、PDF 页码、文本滚动百分比）。笔记进度沿用其 `SCROLL_PERCENT` 思路，但用独立的 `bf_note_progress` 表。
- **position_type / position_value**
  进度表的类型与值字段。取值：`SECONDS`（音视频秒数）、`PAGE`（PDF 页码）、`SCROLL_PERCENT`（滚动百分比 0~1）。

## R

- **Room**
  Android 官方 SQLite ORM。随手记离线功能用它缓存笔记列表与正文，支持离线查看/编辑。

## S

- **SCROLL_PERCENT**
  进度类型：滚动百分比（0.0~1.0）。用于文本、Markdown、笔记等长内容，跨设备续读。
- **SSE（Server-Sent Events）**
  服务端单向实时推送（`text/event-stream`）。`GET /api/events` 已实现（`com.baiflow.event`：`SseService` 用户连接注册表 + `EventController` + 定时心跳清理），已推送 `NOTE_UPDATED` 事件；`TRANSFER_PROGRESS` / `DOWNLOAD_COMPLETED` / `DOWNLOAD_FAILED` / `NOTIFICATION_CREATED` 为已定义待接入。
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
- **离线（Phase 3）**：离线编辑 → Room + outbox → 联网 → 推 PATCH + `updatedAfter` 拉取合并 → 冲突后写覆盖
