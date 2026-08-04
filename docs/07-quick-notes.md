# 07 · 随手记（便签/笔记）方案（ADR）

> 状态：**Phase 1 已实现**（2026-08-05，后端 + Web + SSE）；Phase 2/3（Android 在线/离线）待实施
> 类型：架构决策记录（ADR）
> 相关：`docs/01-architecture.md`、`docs/02-database.md`、`docs/03-api.md`、`docs/04-frontend.md`、`docs/05-android.md`

## 1. 背景与目标

在 BaiFlow 中新增「随手记」：浏览器随手记笔记，Android App 可查看与编辑，**内容与阅读进度跨设备同步**。定位为轻量便签（类似 Apple 备忘录），非完整笔记应用。

## 2. Grilling 决策

| 决策 | 结论 |
|---|---|
| 功能定位 | 便签/笔记（标题 + Markdown 正文） |
| 手机端 | Android App |
| 数据存储 | 独立笔记表 `bf_note`，正文存 DB（不进文件中心） |
| 内容格式 | Markdown（Web 用 showdown 渲染；Android 纯文本编辑存 md 源） |
| 内容同步 | SSE 推送 `NOTE_UPDATED` + 打开时拉取 |
| 阅读进度 | 新表 `bf_note_progress`（复用 SCROLL_PERCENT 思路） |
| Android 离线 | Room 本地缓存 + WorkManager 后台同步 |
| 冲突策略 | 服务端时间戳后写覆盖（last-write-wins） |
| 管理范围 | 最小集：标题 + 正文 + 时间 + 搜索 + 删除 |
| 实施节奏 | 分三阶段（①后端+Web ②Android 在线 ③Android 离线+同步） |

## 3. 关键事实与前置缺口

- **SSE `/api/events` 尚未实现**：`docs/03-api.md` 规划了 `GET /api/events`（TRANSFER_PROGRESS / DOWNLOAD_COMPLETED 等），但服务端代码中不存在 SseEmitter / 事件通道。本功能若走 SSE 推送，**需先在 Phase 1 补齐 SSE 基础设施**（SseEmitter + 事件发布/订阅 + `/api/events` 端点）——这也服务于传输进度、通知等已有规划。
  - **备选**：若暂不建 SSE，可退化为 Web 端定时轮询拉取（改动更小，实时性差些）。已选 SSE，轮询仅作兜底。
- 数据库迁移当前到 `V2__playback_progress.sql`，笔记建表用 `V3__quick_notes.sql`。

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

### 同步协议（Android 离线，Phase 3）
- **拉取**：`GET /api/notes?updatedAfter=<本地最新时间>` 增量合并进 Room
- **推送**：离线编辑写入本地 outbox（待同步队列），恢复联网后逐个 `PATCH`
- **冲突**：两端都改同一篇 → 以服务端时间戳为准，后写覆盖
- **删除**：软删除标记随增量拉取同步

## 6. 后端改动（Phase 1）

| # | 文件 | 操作 | 说明 |
|---|---|---|---|
| 1 | `db/migration/V3__quick_notes.sql` | **新建** | 建 `bf_note` + `bf_note_progress` |
| 2 | `Note.java` + `NoteMapper` | **新建** | 笔记实体与 Mapper |
| 3 | `NoteService` / `NoteServiceImpl` | **新建** | CRUD、搜索、进度读写、`updatedAfter` 增量 |
| 4 | `NoteController` | **新建** | `/api/notes` REST + 进度端点 |
| 5 | `NoteProgress.java` + Mapper | **新建** | 进度实体（对齐 PlaybackProgress 模式） |
| 6 | SSE 基础设施 | **新建** | SseEmitter + 事件发布/订阅 + `/api/events` |

## 7. Web 改动（Phase 1）

| # | 文件 | 操作 | 说明 |
|---|---|---|---|
| 7 | `views/NotesView.vue` | **新建** | 左侧列表 + 右侧 Milkdown 编辑器（WYSIWYG，ProseMirror 底层，输出 Markdown 源；编辑器经 listener 插件监听变更自动保存；原 showdown 预览切换已由 Milkdown 取代） |
| 8 | `api/notes.js` | **新建** | CRUD + 进度 API 封装 |
| 9 | 路由 + 侧边栏 | 修改 | 加「随手记」入口 |
| 10 | SSE 监听 + 进度 | 修改 | 收 `NOTE_UPDATED` 刷新当前笔记；滚动防抖保存 SCROLL_PERCENT |

## 8. Android 改动

### Phase 2（在线笔记）
- 新增 `NotesActivity`：列表页 + 查看/编辑页（纯文本编辑器存 Markdown 源）
- Retrofit 定义 `/api/notes` CRUD + 进度接口

### Phase 3（离线 + 同步）
- **Room**：`NoteEntity` + DAO，本地缓存列表与正文
- **WorkManager**：同步 Worker，恢复联网 / 周期触发
- **outbox**：离线编辑入本地队列，联网后推送
- **增量合并**：`updatedAfter` 拉取，按服务端时间戳合并
- **冲突**：后写覆盖

## 9. 安全

- 非管理员只能访问自己的笔记（`ownerUserId` 隔离，沿用文件模式）；管理员可 `viewUserId` 切换
- 笔记与文件系统隔离，不进入文件中心；不受存储根目录 / 隐私文件夹约束
- SSE 端点需 JWT 鉴权；`NOTE_UPDATED` 只推送给笔记所有者
- 本期不做公开分享（`/api/public/**` 不涉及笔记）

## 10. 范围与边界

- **本期不做**：图片/附件、标签/置顶/分类、回收站、笔记间链接、实时协同编辑（OT/CRDT）——SSE 仅做"刷新通知"，非协同编辑
- **已知取舍**：后写覆盖可能丢编辑（个人使用可接受，已确认）
- **阅读进度**：仅对足够长的正文记录滚动百分比；短笔记不记
- **分享**：本期不做笔记分享

## 11. 文档同步（实现时）

- `docs/02-database.md`：登记 `bf_note`、`bf_note_progress`
- `docs/03-api.md`：登记 `/api/notes`、进度端点、`/api/events` 的 `NOTE_UPDATED`
- `docs/04-frontend.md`：登记「随手记」页
- `docs/05-android.md`：登记笔记模块（在线 / 离线）
- `docs/01-architecture.md`：模块图补笔记模块与 SSE
