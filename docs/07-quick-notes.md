# 07 · 随手记（便签/笔记）方案

> 状态：**Phase 1 已实现**（2026-08-05，后端 + Web + SSE）；**Phase 2（Android 富文本编辑器 + 笔记媒体）已实现**（2026-08-06）；Phase 3（离线同步）待实施
> 相关：`docs/01-architecture.md`、`docs/02-database.md`、`docs/03-api.md`、`docs/04-frontend.md`、`docs/05-android.md`、`docs/08-ios-design-system.md`

在 BaiFlow 中新增「随手记」：浏览器随手记笔记，Android App 可查看与编辑，**内容与阅读进度跨设备同步**。定位为轻量便签（类似 Apple 备忘录），非完整笔记应用。

## 1. 关键约定

| 项 | 说明 |
|---|---|
| 功能定位 | 便签/笔记（标题 + Markdown 正文） |
| 手机端 | Android App |
| 数据存储 | 独立笔记表 `bf_note`，正文存 DB（不进文件中心） |
| 内容格式 | Markdown（Web 用**所见即所得块编辑器**：contenteditable 就地渲染行内格式、编辑即预览，HTML↔Markdown 经 showdown+turndown 往返；Android 用**块式富文本编辑器**——不手写源码，工具栏实现加粗/标题等，存储仍为 md 源） |
| Android 媒体 | 图片/录音/画画：上传到服务器**专用媒体目录**（不进文件中心列表），引用写进正文 Markdown，Web/Android 多端预览 |
| 编辑器实现 | 手写透传式 Markdown 解析/发射器 + Spannable 适配层（纯 JVM 可测，未知内容原样透传不丢数据） |
| 内容同步 | SSE 推送 `NOTE_UPDATED` + 打开时拉取 |
| 阅读进度 | 新表 `bf_note_progress`（复用 SCROLL_PERCENT 思路） |
| Android 离线 | Room 本地缓存 + WorkManager 后台同步 |
| 冲突策略 | **乐观并发**：保存携带 `baseUpdatedAt`，若被其他设备改过返回 `40901`（NOTE_CONFLICT），客户端弹「覆盖 / 重新加载」让用户选择 |
| 管理范围 | 最小集：标题 + 正文 + 时间 + 搜索 + 删除 |
| 实施节奏 | 分三阶段（①后端+Web ②Android 在线 ③Android 离线+同步） |

## 2. 关键事实与前置缺口

- **SSE `/api/events`**：原为缺口（`docs/03-api.md` 规划了 `GET /api/events`，但服务端无 SseEmitter / 事件通道），**Phase 1 已补齐**（`com.baiflow.event`：`SseService` 用户连接注册表 + `EventController` + 30s 心跳清理）。`NOTE_UPDATED` 已接入；`TRANSFER_PROGRESS` / `DOWNLOAD_COMPLETED` / `DOWNLOAD_FAILED` / `NOTIFICATION_CREATED` 从未接入，且 aria2 下载模块已移除，2026-08-09 已从 `SseEventType` 移除。
- 数据库迁移：统一 schema 可重复迁移 `R__V1_init.sql`（项目约定新表一律追加此文件；含 `bf_playback_progress` / `bf_note` / `bf_note_progress` / `bf_note_media` / `bf_auth_session` 五张表，全部表与字段均带注释）。

## 3. 数据库

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

## 4. API 设计

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
- **冲突**：乐观并发（保存携带 `baseUpdatedAt`，被改过返回 `40901`（NOTE_CONFLICT），客户端选覆盖 / 重新加载）
- **删除**：软删除标记随增量拉取同步

## 5. 后端改动（Phase 1）

| # | 文件 | 操作 | 说明 |
|---|---|---|---|
| 1 | `db/migration/V2__progress_and_quick_notes.sql` | **修改** | 建 `bf_note` + `bf_note_progress`，与 `bf_playback_progress` 同文件管理，全部表/字段带注释 |
| 2 | `Note.java` + `NoteMapper` | **新建** | 笔记实体与 Mapper |
| 3 | `NoteService` / `NoteServiceImpl` | **新建** | CRUD、搜索、进度读写、`updatedAfter` 增量 |
| 4 | `NoteController` | **新建** | `/api/notes` REST + 进度端点 |
| 5 | `NoteProgress.java` + Mapper | **新建** | 进度实体（对齐 PlaybackProgress 模式） |
| 6 | SSE 基础设施 | **新建** | SseEmitter + 事件发布/订阅 + `/api/events` |

## 6. Web 改动（Phase 1）

| # | 文件 | 操作 | 说明 |
|---|---|---|---|
| 7 | `views/NotesView.vue` | **新建** | 左侧列表 + 右侧所见即所得块编辑器 `NoteBlockEditor.vue`（文本/标题块 + 图片/音频媒体；contenteditable 就地渲染行内格式、编辑即预览；浮动 B/I/U/S 格式条跟随焦点块上方偏左、execCommand 就地格式化；顶部常显块类型栏；删除线渲染为 `<strike>`（保证 execCommand 再点取消稳定）；保存成功弹成功提示；插入线移除灰色横线只留「＋」） |
| 8 | `api/notes.js` | **新建** | CRUD + 进度 API 封装 |
| 9 | 路由 + 侧边栏 | 修改 | 加「随手记」入口 |
| 10 | SSE 监听 + 进度 | 修改 | 收 `NOTE_UPDATED` 刷新当前笔记；滚动防抖保存 SCROLL_PERCENT |

## 7. Android 改动

### Phase 2（在线笔记，已实现 2026-08-06）

**列表页 `NotesFragment`**（替换占位页）：
- `Ios.Header`「随手记」+ 新建；搜索框（keyword 防抖 500ms）；管理员 `viewUserId` 切换（对齐文件页）；下拉刷新 + 空状态；长按删除

**富文本编辑器 `NoteEditActivity`**（所见即所得块编辑器）：
- 正文为块列表（每块一个真实 View：文本 EditText / 图片 / 音频），加载 Markdown→`NoteBlocks.fromDoc`→RecyclerView、保存 `NoteBlocks.toDoc`→Markdown；块内存「行内 md 源」
- **所见即所得**：文本块经 `BlockRichText`（渲染 `MarkdownParser.parseInlines`+`ModelToSpanned.appendInlines`、回写 `SpanExtractor.extractInlines`+`MarkdownEmitter.emitInlines`）渲染行内 markdown 的格式效果，编辑即预览；`BlockRichTextTest`（Robolectric）保证往返稳定；文本块用**系统默认选中菜单**（剪切/复制/粘贴/全选），格式操作统一走底部工具栏
- 图片块位图缓存（`LruCache`，mediaUrl→Bitmap），滑动复用时不反复异步加载，避免画画等图片块闪烁；异步回填校验 holder 未被复用
- 工具栏常显两行：第一行块类型（文本/标题，切换当前焦点块类型，无焦点块时在末尾插入新块；标题不再区分 H1/H2/H3）+ 竖线分隔 + 媒体（图片/录音/画画），与 Web 一致；第二行格式栏 B/I/U/S = 加粗/斜体/下划线/删除线（选中文字后点击应用、再点取消；`focusable=false` 不抢焦点保住选中；未选中时提示先选中文字；**取消时按选中范围做 span 切分，只去选中部分、范围外保留，与 Web execCommand 行为对齐**）。格式按钮固定于工具栏第二行，不采用悬浮格式条（会遮挡正文与块顶「＋」、且与系统选中弹窗冲突）
- 空行是块间分隔，不生成可见空块（`NoteBlocks.fromDoc` 跳过空行、`toDoc` 在块间注入 `\n\n`，与 Web blocksToMarkdown 一致）
- 块卡片撑满整行宽、块间 8dp 间距（ItemDecoration）；每块顶部一条**全宽插入条**（居中「＋」，整条可点，与 Web 的插入横线一致）可**在上方插入**（文本/标题/图片/录音，媒体经 pendingInsertIdx 落到指定位置）；插入条位于卡内顶部、不凸出，避免被相邻卡片遮挡或 RecyclerView 裁剪；新增块后界面自动滚动过去
- 音频块：宽度**拉满整块**，右侧留出 32dp 给右上角删除 ×（不被遮挡）；播放/暂停用 TextView 而非系统 Button（避免默认样式导致按钮显示不全）；进入时按钮正确显示 ▶（仅准备完成未播放）
- 录音时长：录音时用**墙钟时长**（stop-start）写入 URL `&duration=`（避免 MediaPlayer/Retriever 误读，如 1s 读成 2s），播放组件优先用该已知时长显示
- 插入媒体（图片/录音/画画）后**不再自动补空文本块**——文本由用户手动插入（Web 与 Android 一致）
- 引用块已移除：旧引用内容打开后映射为普通文本块（内容保留，`>` 丢弃）；下划线用 `<u>...</u>`（与 Web 对齐）
- 实现：纯 JVM `MarkdownParser`/`MarkdownEmitter`/`DocModel`（透传未知内容，零数据丢失）+ `NoteBlocks` 块模型；JUnit/Robolectric 测试
- 返回时自动保存（PATCH/POST）

**媒体（图片/录音/画画）**（Web/Android 均为独立媒体块，非行内 span）：
- 图片：系统选择器选图 → 上传 → 追加图片块（`ImageView`，本地/服务端加载）；Android 侧位图按 mediaUrl 缓存（`LruCache`），滑动复用不闪烁，异步回填校验 holder 未被复用
- 录音：`MediaRecorder`（RECORD_AUDIO 权限）→ 上传 → 追加音频块（`NoteAudioPlayerView`：播放/暂停 + 进度条；时长用墙钟写入 `&duration=`，优先展示避免误读）
- 画画：`NoteDrawActivity`（Canvas）→ PNG → 上传 → 追加图片块
- 两端插入媒体后不再自动补文本块，文本手动插入；媒体块经整行 markdown 引用（`![alt](url)` / `[录音](url?mediaType=audio&duration=ms)`）序列化，两端互认

### Phase 3（离线 + 同步，待实施）
- **Room**：`NoteEntity` + DAO，本地缓存列表与正文
- **WorkManager**：同步 Worker，恢复联网 / 周期触发
- **outbox**：离线编辑入本地队列，联网后推送
- **增量合并**：`updatedAfter` 拉取，按服务端时间戳合并
- **冲突**：对齐在线端乐观并发（覆盖 / 重新加载）

## 8. 安全

- 非管理员只能访问自己的笔记（`ownerUserId` 隔离，沿用文件模式）；管理员可 `viewUserId` 切换
- 笔记与文件系统隔离，不进入文件中心；不受存储根目录 / 隐私文件夹约束
- 笔记媒体独立存储，不进入文件中心列表；媒体读取需鉴权（所有者或管理员），`?token=` 兜底仅用于 Web `<img>/<audio>` 渲染
- SSE 端点需会话 token 鉴权（`?token=`）；`NOTE_UPDATED` 只推送给笔记所有者
- 本期不做公开分享（`/api/public/**` 不涉及笔记与媒体）

## 9. 范围与边界

- **已支持**（Phase 2）：图片/录音/画画媒体（经 `bf_note_media` 专用存储 + 正文引用）
- **本期不做**：标签/置顶/分类、回收站、笔记间链接、实时协同编辑（OT/CRDT）——SSE 仅做"刷新通知"，非协同编辑
- **已知取舍**：乐观并发冲突由用户选择「覆盖 / 重新加载」，不再静默丢改动；同秒内（TIMESTAMP 秒级精度）的并发写仍后写覆盖；Android 富文本编辑器行内强调嵌套可能摊平为相邻 run、有序列表总是从 1 开始、块间空行归一化——**代码块内容/标题标记/媒体 URL/任何文本绝不丢失**（透传保证）
- **阅读进度**：仅对足够长的正文记录滚动百分比；短笔记不记；Android 编辑器续读时同样上报并自动滚动到记录位置（SCROLL_PERCENT，滚动防抖 800ms，与 Web 共用同一份数据）
- **分享**：本期不做笔记分享
- **孤儿媒体**：本期不清理（笔记软删除不影响媒体）

## 10. 文档同步（实现时）

- `docs/02-database.md`：登记 `bf_note`、`bf_note_progress`
- `docs/03-api.md`：登记 `/api/notes`、进度端点、`/api/events` 的 `NOTE_UPDATED`
- `docs/04-frontend.md`：登记「随手记」页
- `docs/05-android.md`：登记笔记模块（在线 / 离线）
- `docs/01-architecture.md`：模块图补笔记模块与 SSE
