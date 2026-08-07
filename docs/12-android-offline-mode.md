# 12 · Android 离线模式（Offline Mode）方案（ADR）

> 状态：**已实现**（2026-08-07）；Grilling 定稿后当日完成实现，后端 `updatedAfter` 增量 + Android Room/三态入口/SyncService/WorkManager，`assembleDebug` 通过
> 触发点：Android 端「设置服务器」界面支持离线模式；离线时文件中心不可用、随手记本地可用、重连后上传数据；并回答「未登录能否本地使用」的产品矛盾
> 类型：架构决策记录（ADR）
> 相关：`docs/05-android.md`、`docs/07-quick-notes.md`（Phase 3 离线同步）、`docs/11-android-network-error.md`、`docs/09-auth-sessions.md`、`baiflow-android/app/src/main/java/com/baiflow/android/`

## 1. 背景与目标

现状：
- Android **无本地持久化**（无 Room/SQLite 依赖，仅 `SessionManager` 用 SharedPreferences），随手记 **100% 在线**（列表/详情/保存全走 API）。
- `MainActivity` 未登录即被挡：无服务器 → `ServerConfigActivity`，有服务器 → `LoginActivity`——**不登录到不了主界面**。
- 离线同步已在 `docs/05-android.md` 规划为「随手记 Phase 3」：Room 本地缓存 + WorkManager 同步 + outbox + `updatedAfter` 增量合并，但**未实现**。

核心产品矛盾（Grilling 提出并化解）：
- 纯在线 → 服务器不可达/忘密码/被踢时完全不可用，体验差；
- 纯本地 → 「设置服务器」失去意义。

**化解：把「设置服务器」与「登录」解耦成状态，而非强绑定**——设置服务器解锁的是**在线能力**（文件中心、同步），本地能力（随手记）在三态下始终可用；「有服务器后必须登录」只挡**入口**，而**离线模式是用户主动的本地例外**（进离线清会话，本地笔记免登录）。

## 2. Grilling 决策

| 决策 | 结论 |
|---|---|
| 登录门槛 | **有服务器后必须登录**（未配服务器 = 本地模式，免登录） |
| 进入方式 | **未设服务器 = 本地模式 + 手动开关进离线** |
| 在线数据处理 | **全量镜像本地 + outbox**（首次在线拉全笔记，离线改动进 outbox 队列） |
| 重连 | **重连必重新登录**（进离线清 token） |
| 离线会话 | **进离线清 token，本地免登录**（作为「有服务器后必须登录」的明确例外） |
| 冲突策略 | **对齐现有 NOTE_CONFLICT**：outbox 带 baseUpdatedAt，冲突时复用「覆盖/重载」弹窗 |
| 删除同步 | **同步删除**（tombstone 标记，重连后删除服务端） |
| 缓存绑定 | **绑定服务器地址**；切换服务器/登出时清空该服务器缓存，防串号 |
| 本地模式→配服务器 | **上传前询问**：首次登录弹「有 N 条本地笔记，是否上传」 |
| 进离线 vs 登出 | **两级**：进离线 = 清 token 留缓存；登出 = 清 token + 清该服务器缓存 |
| 同步时机 | **自动立即 + WorkManager 后台周期 + 手动「同步」** |
| 首次入口 | **先配置引导页**：设置服务器 / 「暂不，先本地用」二选一 |
| 登录页逃生 | **登录页提供「使用离线模式」入口**（服务器不可达/忘密码不被锁死） |
| 媒体离线 | **同步时缓存服务器媒体**（图片/录音/画画）到本地，离线完整可读 |
| 文件中心 | 本地/离线模式**禁用 + 占位提示**（不缓存文件元数据） |
| 下载/传输 | 本地/离线模式**禁用**（后台传输依赖服务器） |

## 3. 关键事实

- 后端 `GET /api/notes` 目前仅 `keyword / viewUserId / page / size`，**无 `updatedAfter`**——增量拉取需后端扩展（Phase 3 的前置）。
- 笔记软删除（`status = ACTIVE / DELETED`）；删除同步需后端在增量结果中暴露删除标记（或独立 sync 端点）。
- `NOTE_CONFLICT` 乐观并发已存在（Web/Android 编辑器均有「覆盖/重载」逻辑），可复用。
- 媒体鉴权走 `?token=` 查询参数通道（`SessionAuthenticationFilter` 已支持），离线缓存媒体为本地文件，无鉴权问题。
- 会话模型：ANDROID 长期会话（180 天吊销驱动），但本设计在**进离线时主动清 token**，重连必重登。

## 4. 状态模型

```
┌─ 本地模式（未配服务器，免登录）
│    随手记全本地 · 文件中心禁用 · 无同步目标
│        │ 我的→设置服务器→登录
│        ▼
┌─ 在线模式（已配服务器 + token）
│    全功能 · 笔记同步 · 文件中心可用
│        │ 我的→离线模式开关（清 token，留缓存）
│        ▼
┌─ 离线模式（已配服务器，无 token，离线标记）
│    随手记用本地镜像 + outbox · 文件中心禁用 · 会话已清
```

入口转移：
- 首次启动 → **配置引导页** →「设置服务器」（→ 登录 → 在线）或「暂不，先本地用」（→ 本地模式）。
- 本地模式 → 我的 → 设置服务器 → 登录 → 在线模式（本地笔记**上传前询问**）。
- 在线模式 → 我的 → 离线模式开关 → 清 token → 离线模式。
- 离线模式 → 重连（登录）→ 在线模式 + **立即同步**。
- 登录页（已设服务器）→「使用离线模式」→ 离线模式（逃生口）。
- 在线/离线模式 → 退出登录 → 清 token + **清该服务器缓存** → 停在登录页。
- 登录页「使用离线模式」后，若后续想回在线 → 登录 → 在线 + 立即同步。

## 5. 数据模型（本地 Room，`bf_local_note`）

| 列 | 说明 |
|---|---|
| `id` | 本地主键（自增或 UUID） |
| `server_id` | 已同步的服务端笔记 ID（可空 = 尚未上传） |
| `server_url` | 缓存绑定键（服务器地址；本地模式用特殊键「LOCAL」） |
| `title` / `content` | 笔记内容 |
| `base_updated_at` | 最近一次成功同步的服务端 `updatedAt`（乐观并发基准） |
| `dirty` | 待同步标记（进 outbox） |
| `source` | `LOCAL_ONLY`（本地模式）/ `SYNCED`（服务端镜像）/ `TOMBSTONE`（离线删除待同步） |
| `media_refs` | JSON：`[{kind, localPath?, serverUrl?, mediaId?}]` 离线创建的媒体存本地文件，同步上传后回填 serverUrl |
| `created_at` / `updated_at` | 本地时间戳 |

- **outbox**：由 `dirty + source` 表达；tombstone 只保留 `server_id` 供删除同步。
- **缓存绑定**：全表按 `server_url` 分区；切换服务器/登出 = 删该分区；本地模式数据（LOCAL 键）独立保留，配服务器后**上传前询问**是否归入服务器分区。

## 6. 同步流程

重连/登录成功 → **立即同步**：
1. **推 outbox**：`dirty` 笔记 create/update（带 `baseUpdatedAt`）；`TOMBSTONE` → `DELETE server_id`。
2. **拉增量**：`GET /api/notes?updatedAfter=<最近同步时间>`（需后端扩展），含删除标记；合并进 `SYNCED` 分区。
3. **冲突**：返回 `NOTE_CONFLICT` → 笔记标记为冲突；打开时复用现有「覆盖/重载」弹窗，用户决定。
4. **媒体**：离线上传的媒体先传（`POST /api/notes/media`）回填 URL；服务器媒体按需下载缓存到本地文件。
5. 更新 `base_updated_at`、清 `dirty`。

- **WorkManager**：网络恢复 / 周期触发同步；**离线模式暂停**（无目标服务器）。
- **手动**：我的页「同步」按钮强制触发。
- 首次配服务器登录：`LOCAL` 分区有笔记 → 弹「有 N 条本地笔记，是否上传到服务器」→ 用户选择后迁移分区并同步。

## 7. 端改动

- **Android**：
  - 新增 Room 依赖 + `bf_local_note` 表（`data/LocalNote`、`LocalNoteDao`、`AppDatabase`）。
  - **离线优先（实现偏差，简化 ADR §7 的 Provider 抽象）**：Room 作为笔记唯一数据源（分区 = 服务器地址 或 LOCAL），NotesFragment / NoteEditActivity 直接读写 Room；`SyncService`（`data/`）负责 outbox 推 + 增量拉 + 冲突标记 + 媒体上传/缓存。媒体离线新建用 `local://` 引用（`data/MediaFiles`），同步时上传并改写为服务端 URL。
  - 入口流改造：新增 `GuideActivity` 引导页；`MainActivity` 按三态分发（本地/在线/离线 → 主界面；服务器已设未登录未离线 → 登录页）。
  - `ServerConfigActivity`（换服务器清旧分区）/ `LoginActivity`（「使用离线模式」入口 + 本地笔记上传询问 + 同步调度）/ `MineFragment`（模式指示、离线开关、立即同步、重连、两级登出）。
  - `FilesFragment` / `TransferListActivity`：本地/离线模式显示禁用占位。
  - WorkManager `SyncWorker`（`sync/`）：在线模式周期 + 网络约束；离线/登出取消。
- **后端**（增量拉取所需）：
  - `GET /api/notes` 增 `updatedAfter` 参数（配合 `updatedAt` 索引），并返回删除标记（或新增独立 sync 端点）。
  - 其余复用现有 create/update/delete/media/upload 接口。

## 8. 安全与隐私

- 本地笔记明文存设备 Room（与设备信任一致，需在文档中写明边界）；不加密存储。
- 缓存**绑定服务器地址**：切换服务器/登出清空，防把 A 服务器数据串到 B 服务器。
- **进离线清 token**：离线期间本地笔记免登录可用 = 拿到设备即可读（设备级信任）；重连重登后恢复在线能力。
- **上传前询问**：本地模式笔记首次上云需用户确认，防本地隐私误传。

## 9. 范围与边界

- **本期范围**：Android 三态离线 + 本地随手记 + outbox/增量同步 + 后端 `updatedAfter` 扩展。
- **边界**：
  - 文件中心/下载/传输离线不可用（不缓存文件元数据，只缓存笔记与其媒体）。
  - 多设备冲突依赖 NOTE_CONFLICT 手动合并，不做自动合并。
  - 服务器媒体缓存占存储；清理策略（按需/LRU）列为后续。
  - **管理员移动端按用户切换笔记视图不支持**（离线优先分区 = 本人笔记）；管理员仍可浏览自己的笔记。
  - 本地模式笔记若在「上传前询问」选「暂不」，留在 LOCAL 分区，在线视图不显示；换回本地模式或清服务器后仍可访问（不丢数据，见 ADR §5）。
  - 离线新建媒体上传后**不删除本地文件**（避免推送失败后坏引用/编辑器未关重存）；`note_media` 目录可能残留已上传文件，后续加清理。
  - Web / iOS 端离线模式不在本期（本期仅 Android）。
