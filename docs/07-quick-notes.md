# 07 · 随手记（便签/笔记）方案（ADR）

> 状态：**Phase 1 已实现**（2026-08-05，后端 + Web + SSE）；**Phase 2（Android 富文本编辑器 + 笔记媒体）已实现**（2026-08-06）；Phase 3（离线同步）待实施
> 类型：架构决策记录（ADR）
> 相关：`docs/01-architecture.md`、`docs/02-database.md`、`docs/03-api.md`、`docs/04-frontend.md`、`docs/05-android.md`、`docs/08-ios-design-system.md`

## 1. 背景与目标

在 BaiFlow 中新增「随手记」：浏览器随手记笔记，Android App 可查看与编辑，**内容与阅读进度跨设备同步**。定位为轻量便签（类似 Apple 备忘录），非完整笔记应用。

## 2. Grilling 决策

| 决策 | 结论 |
|---|---|
| 功能定位 | 便签/笔记（标题 + Markdown 正文） |
| 手机端 | Android App |
| 数据存储 | 独立笔记表 `bf_note`，正文存 DB（不进文件中心） |
| 内容格式 | Markdown（Web 用 Vditor 编辑器渲染；Android 用**所见即所得富文本编辑器**——不手写源码，工具栏实现加粗/标题/列表等，存储仍为 md 源） |
| Android 媒体 | 图片/录音/画画：上传到服务器**专用媒体目录**（不进文件中心列表），引用写进正文 Markdown，Web/Android 多端预览 |
| 编辑器实现 | 手写透传式 Markdown 解析/发射器 + Spannable 适配层（纯 JVM 可测，未知内容原样透传不丢数据） |
| 内容同步 | SSE 推送 `NOTE_UPDATED` + 打开时拉取 |
| 阅读进度 | 新表 `bf_note_progress`（复用 SCROLL_PERCENT 思路） |
| Android 离线 | Room 本地缓存 + WorkManager 后台同步 |
| 冲突策略 | **乐观并发**：保存携带 `baseUpdatedAt`，若被其他设备改过返回 `NOTE_CONFLICT`，客户端弹「覆盖 / 重新加载」让用户选择（不再是静默后写覆盖） |
| 管理范围 | 最小集：标题 + 正文 + 时间 + 搜索 + 删除 |
| 实施节奏 | 分三阶段（①后端+Web ②Android 在线 ③Android 离线+同步） |

## 3. 关键事实与前置缺口

- **SSE `/api/events`**：原为缺口（`docs/03-api.md` 规划了 `GET /api/events`，但服务端无 SseEmitter / 事件通道），**Phase 1 已补齐**（`com.baiflow.event`：`SseService` 用户连接注册表 + `EventController` + 30s 心跳清理）。`NOTE_UPDATED` 已接入；`TRANSFER_PROGRESS` / `DOWNLOAD_COMPLETED` / `DOWNLOAD_FAILED` / `NOTIFICATION_CREATED` 为已定义、待各模块接入。
- 数据库迁移：统一 schema 可重复迁移 `R__V1_init.sql`（项目约定新表一律追加此文件；含 `bf_playback_progress` / `bf_note` / `bf_note_progress` / `bf_note_media` / `bf_auth_session` 五张表，全部表与字段均带注释）。

## 4. 数据库

`bf_note`：
```sql
CREATE TABLE IF NOT EXISTS bf_note (
    id         VARCHAR(32)  NOT NULL,
    user_id    VARCHAR(32)  NOT NULL,
    title      VARCHAR(200) NOT NULL DEFAULT '',
    content    LONGTEXT     NOT NULL,             -- Markdown 源
    status     VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE / DELETED
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP    NULL,
    PRIMARY KEY (id),
    KEY idx_user_updated (user_id, updated_at)
);
```

`bf_note_progress`：
```sql
CREATE TABLE IF NOT EXISTS bf_note_progress (
    id             VARCHAR(32) NOT NULL,
    user_id        VARCHAR(32) NOT NULL,
    note_id        VARCHAR(32) NOT NULL,
    position_type  VARCHAR(16) NOT NULL DEFAULT 'SCROLL_PERCENT',
    position_value DOUBLE      NOT NULL DEFAULT 0,
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_note (user_id, note_id)
);
```

说明：`position_type` 预留扩展（本期只有 SCROLL_PERCENT）；短笔记（内容不足一屏）不记录进度。

`bf_note_media`（Phase 2 新增，Android 富文本编辑器的图片/录音/画画媒体存储）：
```sql
CREATE TABLE IF NOT EXISTS bf_note_media (
    id         VARCHAR(32)  NOT NULL,
    user_id    VARCHAR(32)  NOT NULL,
    media_type VARCHAR(16)  NOT NULL,             -- IMAGE / AUDIO / DRAWING
    file_name  VARCHAR(255) NOT NULL,
    mime_type  VARCHAR(100) NOT NULL,
    size_bytes BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user (user_id, created_at)
);
```
媒体文件落磁盘（`baiflow.notes.media-path` 专用目录，文件名 `<mediaId>.<ext>`），本表只存元数据；独立于文件中心，不参与 `/api/files` 列表，不受存储根/隐私文件夹约束。

## 5. API 设计

### 笔记 CRUD
- `GET /api/notes?page=&size=&keyword=&viewUserId=` — 分页列表；`keyword` 搜标题/正文；非管理员限本人，管理员可 `viewUserId` 切换
- `POST /api/notes` — 新建 `{ title, content }`
- `GET /api/notes/{id}` — 详情
- `PATCH /api/notes/{id}` — 编辑 `{ title, content }`，服务端置 `updated_at = now`
- `DELETE /api/notes/{id}` — 软删除（`status=DELETED`，记 `deleted_at`）

> 注：`updatedAfter` 增量拉取（含删除清单）属 Android 离线同步（Phase 3）的接口，Phase 1 的 `GET /api/notes` 暂未提供，Phase 3 落地时补充。

### 阅读进度
- `GET /api/notes/{id}/progress` — `{ positionType, positionValue, updatedAt }`，无记录返回 null
- `PUT /api/notes/{id}/progress` — `{ positionValue }`，upsert

### 事件（Phase 1 补齐 SSE 后）
- SSE `GET /api/events` 新增事件类型 `NOTE_UPDATED { noteId, updatedAt }`，推送给笔记所有者

### 笔记媒体（Phase 2 新增）
- `POST /api/notes/media` — multipart `file` + 可选 `mediaType`（IMAGE/AUDIO/DRAWING）；MIME 白名单 + ≤20MB；返回 `{ id, mediaType, url, mimeType, sizeBytes, createdAt }`（`url` 为相对路径 `/api/notes/media/{id}`）
- `GET /api/notes/media/{id}` — 读取媒体内容（inline）；鉴权用 Bearer 头或 `?token=`（供 Web `<img>/<audio>` 渲染）
- **正文引用约定**（Android 写入 content，两端渲染识别）：
  - 图片/画画：`![名称](/api/notes/media/{mediaId})`
  - 录音：`[录音](/api/notes/media/{mediaId}?mediaType=audio)` — 合法 Markdown 链接，`mediaType=audio` 查询参数供渲染器识别音频
- 服务端不解析 content 里的引用，孤儿媒体本期不清理（笔记软删除不影响媒体）

### 同步协议（Android 离线，Phase 3）
- **拉取**：`GET /api/notes?updatedAfter=<本地最新时间>` 增量合并进 Room
- **推送**：离线编辑写入本地 outbox（待同步队列），恢复联网后逐个 `PATCH`
- **冲突**：乐观并发（保存携带 `baseUpdatedAt`，被改过返回 `NOTE_CONFLICT`，客户端选覆盖 / 重新加载）
- **删除**：软删除标记随增量拉取同步

## 6. 后端改动（Phase 1）

| # | 文件 | 操作 | 说明 |
|---|---|---|---|
| 1 | `db/migration/V2__progress_and_quick_notes.sql` | **修改** | 建 `bf_note` + `bf_note_progress`，与 `bf_playback_progress` 同文件管理，全部表/字段带注释 |
| 2 | `Note.java` + `NoteMapper` | **新建** | 笔记实体与 Mapper |
| 3 | `NoteService` / `NoteServiceImpl` | **新建** | CRUD、搜索、进度读写、`updatedAfter` 增量 |
| 4 | `NoteController` | **新建** | `/api/notes` REST + 进度端点 |
| 5 | `NoteProgress.java` + Mapper | **新建** | 进度实体（对齐 PlaybackProgress 模式） |
| 6 | SSE 基础设施 | **新建** | SseEmitter + 事件发布/订阅 + `/api/events` |

## 7. Web 改动（Phase 1）

| # | 文件 | 操作 | 说明 |
|---|---|---|---|
| 7 | `views/NotesView.vue` | **新建** | 左侧列表 + 右侧 Vditor 编辑器（IR 即时渲染，输出 Markdown 源，工具栏含自定义代码块按钮；原 showdown 预览切换已由 Vditor 取代） |
| 8 | `api/notes.js` | **新建** | CRUD + 进度 API 封装 |
| 9 | 路由 + 侧边栏 | 修改 | 加「随手记」入口 |
| 10 | SSE 监听 + 进度 | 修改 | 收 `NOTE_UPDATED` 刷新当前笔记；滚动防抖保存 SCROLL_PERCENT |

## 8. Android 改动

### Phase 2（在线笔记，已实现 2026-08-06）

**列表页 `NotesFragment`**（替换占位页）：
- `Ios.Header`「随手记」+ 新建；搜索框（keyword 防抖 500ms）；管理员 `viewUserId` 切换（对齐文件页）；下拉刷新 + 空状态；长按删除

**富文本编辑器 `NoteEditActivity`**：
- 所见即所得编辑：工具栏 B/I/S/H1-H3/无序有序列表/引用/代码块/行内码/链接 +「更多」展开图片/录音/画画
- 正文用 `RichEditText`（Spannable），打开 Markdown→Spannable、保存 Spannable→Markdown 往返
- 实现：纯 JVM `MarkdownParser`/`MarkdownEmitter`/`DocModel`（透传未知块，零数据丢失）+ Android 适配层 `ModelToSpanned`/`SpanExtractor`/自建段落 span；`ListKeyListener` 处理回车延续列表；JUnit 往返属性测试 + Robolectric 适配层测试
- 返回时自动保存（PATCH/POST）

**媒体（图片/录音/画画）**：
- 图片：系统选择器选图 → 压缩 → 上传 → 插入 `NoteImageSpan`；点击查看/替换/删除
- 录音：`MediaRecorder`（RECORD_AUDIO 权限）→ 上传 → 插入 `NoteAudioSpan`，点击经鉴权接口拉取后播放
- 画画：`NoteDrawActivity`（Canvas）→ PNG → 上传 → 插入图片 span
- 回读：正文中已有媒体引用时，后台拉取图片字节换真实位图（LRU 缓存）；音频引用还原为可点击播放 chip

### Phase 3（离线 + 同步，待实施）
- **Room**：`NoteEntity` + DAO，本地缓存列表与正文
- **WorkManager**：同步 Worker，恢复联网 / 周期触发
- **outbox**：离线编辑入本地队列，联网后推送
- **增量合并**：`updatedAfter` 拉取，按服务端时间戳合并
- **冲突**：对齐在线端乐观并发（覆盖 / 重新加载）

## 9. 安全

- 非管理员只能访问自己的笔记（`ownerUserId` 隔离，沿用文件模式）；管理员可 `viewUserId` 切换
- 笔记与文件系统隔离，不进入文件中心；不受存储根目录 / 隐私文件夹约束
- 笔记媒体独立存储，不进入文件中心列表；媒体读取需鉴权（所有者或管理员），`?token=` 兜底仅用于 Web `<img>/<audio>` 渲染
- SSE 端点需会话 token 鉴权（`?token=`）；`NOTE_UPDATED` 只推送给笔记所有者
- 本期不做公开分享（`/api/public/**` 不涉及笔记与媒体）

## 10. 范围与边界

- **已支持**（Phase 2）：图片/录音/画画媒体（经 `bf_note_media` 专用存储 + 正文引用）
- **本期不做**：标签/置顶/分类、回收站、笔记间链接、实时协同编辑（OT/CRDT）——SSE 仅做"刷新通知"，非协同编辑
- **已知取舍**：乐观并发冲突由用户选择「覆盖 / 重新加载」，不再静默丢改动；同秒内（TIMESTAMP 秒级精度）的并发写仍后写覆盖；Android 富文本编辑器行内强调嵌套可能摊平为相邻 run、有序列表总是从 1 开始、块间空行归一化——**代码块内容/标题标记/媒体 URL/任何文本绝不丢失**（透传保证）
- **阅读进度**：仅对足够长的正文记录滚动百分比；短笔记不记；Android 编辑器非阅读器，不上报进度（接口契约已定义）
- **分享**：本期不做笔记分享
- **孤儿媒体**：本期不清理（笔记软删除不影响媒体）

## 11. 文档同步（实现时）

- `docs/02-database.md`：登记 `bf_note`、`bf_note_progress`
- `docs/03-api.md`：登记 `/api/notes`、进度端点、`/api/events` 的 `NOTE_UPDATED`
- `docs/04-frontend.md`：登记「随手记」页
- `docs/05-android.md`：登记笔记模块（在线 / 离线）
- `docs/01-architecture.md`：模块图补笔记模块与 SSE
