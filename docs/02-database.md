# 数据库设计

数据库：MySQL 8。文件本体保存在磁盘，数据库只存元数据。

## 字符集与排序规则

- 库与表统一使用 `utf8mb4` + `utf8mb4_0900_ai_ci`（MySQL 8 默认排序规则，支持中文等 BMP + 辅助平面字符，比较不区分大小写、重音）
- 建库语句见 `README.md`：`CREATE DATABASE ... CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci`

## 命名规范

- 表名/字段名：小写下划线
- 主键：`id`（varchar 类型）
- 时间：`created_at`、`updated_at`、`deleted_at`
- 逻辑删除：`deleted`（0/1）
- 密码/提取码/token 只存 hash
- 注释：**所有表与字段必须带 `COMMENT` 注释**，说明其含义，便于管理与理解

**时间约定**：时间列统一用 **`DATETIME`**（毫秒精度的同步游标用 `DATETIME(3)`），**存 UTC+8 墙钟**（与应用 JVM/连接时区 `Asia/Shanghai` 一致，`LocalDateTime.now()` 写入即 +8 字面值，读取/比较零转换）。**约束：JVM 必须运行在 Asia/Shanghai**（`baiflow-server/Dockerfile` 已设 `ENV TZ=Asia/Shanghai`；非 Docker 启动需 `-Duser.timezone=Asia/Shanghai`，否则 `LocalDateTime.now()` 写入非 +8）。不用 `TIMESTAMP`——它内部按 UTC 存取，多一层隐藏转换易偏移（历史 bug），且上限 2038。

## 核心表

### user — 系统用户
`id, username, password_hash, display_name, avatar_url, role(ADMIN/USER/GUEST), status(NORMAL/DISABLED/LOCKED), last_login_at, created_at, updated_at`

> `status` 说明：
> - `NORMAL` 正常 · `DISABLED` 禁用（管理员可设置）· `LOCKED` 锁定
> - `LOCKED` 仅由登录失败自动锁定维护（15 分钟内连续失败 5 次），同时写入 Redis 锁键 `login:lock:<username>`（TTL 15 分钟）；锁键到期后由定时任务/登录兜底判定自动恢复为 `NORMAL`
> - 管理员仅支持设置 `NORMAL` / `DISABLED`，不支持手动锁定

### user_storage_permission — 用户存储权限
`id, user_id, storage_root_id, file_item_id, permission(READ/WRITE/MANAGE), created_by, created_at, updated_at`

### storage_root — 存储根目录
`id, name, type(LOCAL/NAS_MOUNT), root_path, status(ACTIVE/OFFLINE/DISABLED), readonly, created_at, updated_at`

### file_item — 文件/目录元数据
`id, storage_root_id, parent_id, owner_user_id, name, relative_path, item_type(FILE/DIRECTORY), size_bytes, mime_type, hash_sha256, privacy_mode(NORMAL/PRIVATE), privacy_password_hash, status(ACTIVE/DELETED), created_at, updated_at, last_opened_at, deleted_at`

`mime_type` 字段前端不展示（文件名自带扩展名），后端用于预览 MIME 路由和 Content-Type 返回。

**隐私空间（新模型）**：每个用户主目录下固定一个名为「隐私空间」的子目录（`privacy_mode=PRIVATE`，密码未设置时 `privacy_password_hash` 为空）。首访设密码、之后输密码换取 `private_folder_access` 令牌进入；管理员免验证。任意文件夹不再单独设为隐私。

**`last_opened_at`**：上次打开时间。文件预览/下载时刷新、进入目录时刷新该目录；分享下载不更新所有者文件的打开时间。刷新时保持 `updated_at` 不变（打开操作不改变「修改时间」）。

**`child_count`**：目录的直接活跃子项数（文件 + 子文件夹），由 `listFiles` 按 `parent_id` 派生统计，**非存储列**；隐私目录返回 null 不展示。

### private_folder_access — 隐私访问会话
`id, user_id, file_item_id, access_token_hash, expires_at, created_at`

### share_link — 分享链接
`id, target_file_item_id, created_by, token_hash, extraction_code_hash, share_type(FILE/FOLDER), access_mode(VIEW/DOWNLOAD), expires_at, max_views, view_count, max_downloads, download_count, status(ACTIVE/DISABLED/EXPIRED/REVOKED), created_at, updated_at`

> `DISABLED`（创建者停用，可恢复）也拒绝访问；提取码连续错误 5 次锁定 15 分钟（Redis）。

### share_access_log — 分享访问日志
`id, share_link_id, action(VIEW/DOWNLOAD/VERIFY_CODE/FAILED), ip_address, user_agent, success, failure_reason, created_at`

### download_record — 文件下载记录
`id, file_id, file_name, downloader_user_id, source(CLIENT/SHARE), share_id, ip_address, user_agent, created_at`

> 每次下载一条（登录直接下载 CLIENT 记录下载人；分享下载 SHARE 下载人为空关联分享 ID），文件下载次数 = 按 `file_id` 聚合。

### transfer_task — 传输任务
`id, created_by, task_type(UPLOAD/DOWNLOAD/DEVICE_SEND), status(WAITING/RUNNING/PAUSED/FAILED/COMPLETED), progress, error_message, created_at, updated_at`

### notification — 通知
`id, user_id, level(INFO/WARN/ERROR), title, content, read_status, created_at, read_at`

### audit_log — 操作审计
`id, actor_user_id, action, target_type, target_id, ip_address, user_agent, detail, created_at`

登录事件（`LOGIN_SUCCESS` / `LOGIN_FAILED`）在此记录，供登录日志查询。

### playback_progress — 播放/阅读进度
`id, user_id, file_item_id, position_type(SECONDS/PAGE/SCROLL_PERCENT), position_value, created_at, updated_at`

每个用户对每个文件只存一条记录。`position_type` 区分视频秒数、PDF 页码、文本滚动百分比，支持跨设备断点续看。**删除文件时级联删除该文件的全部进度行**。

### note — 随手记笔记
`id, user_id, title, content(LONGTEXT, 块结构序列化的 Markdown), status(ACTIVE/DELETED 软删除), created_at, updated_at(DATETIME(3) 毫秒), deleted_at`

笔记独立于文件系统，正文直接落库。编辑器改为**块结构**（文本/标题/列表/引用/代码/图片/音频块，每块一个真实组件），落库格式仍为 **Markdown**（块↔Markdown 转换，服务端不解析，纯客户端契约）。`updated_at` 为 **DATETIME(3) 毫秒精度**（范围 1000-9999，**无 MySQL TIMESTAMP 的 2038 上限**），由服务端显式刷新，作为乐观并发（保存携带 `baseUpdatedAt` **必传**比对，缺失/格式非法 40001、早于当前值 40901）与增量同步（`updatedAfter`）的时间基准；毫秒精度避免同秒内二次保存时 `updated_at` 不变导致增量 `gt(游标)` 漏拉。`status` 软删除标记随增量拉取同步。增量模式列表项携带 `content` 正文，离线端直接合并。

### note_progress — 笔记阅读进度
`id, user_id, note_id, position_type(SCROLL_PERCENT), position_value, created_at, updated_at`

每个用户对每篇笔记只存一条记录，`(user_id, note_id)` 唯一。复用 playback 的 SCROLL_PERCENT 思路，支持跨设备续读长笔记。**删除笔记时级联删除该笔记的进度行**。

### note_media — 笔记媒体
`id, user_id, media_type(IMAGE/AUDIO/DRAWING), file_name, mime_type, size_bytes, created_at`

Android 富文本编辑器的图片/录音/画画媒体元数据。文件本体落磁盘（`baiflow.notes.media-path` 专用目录，文件名 `<mediaId>.<ext>`），独立于文件中心、不参与 `/api/files` 列表；正文通过 Markdown 引用（`![…](/api/notes/media/{id})` / `[录音](…?mediaType=audio)`）关联媒体。

### auth_session — 登录会话
`id, user_id, device_name, device_type(ANDROID/WEB), ip, user_agent, token_hash(SHA-256), expires_at, last_used_at, created_at`

登录会话（模型 2）：每次请求按 `token_hash` 精确查询校验（记录存在 && 未过期），吊销即**删除记录**（即时生效），历史由审计日志留痕（`LOGOUT` / `FORCE_LOGOUT` / `PASSWORD_CHANGED`）。ANDROID / WEB 会话均**滑动续期**（活跃请求顺延 `expires_at`，距上次写库 >1h 才落库；不活跃兜底：ANDROID 180 天 / WEB 约 2h）。`token_hash` 只存哈希，数据库泄露不暴露可用 token。每次登录会顺手清理该用户已过期的历史会话（走 `idx_user` 索引，控制表体积，无需定时任务）。

### user_device — 用户登录设备
`id, user_id, device_name, device_type(ANDROID/WEB), first_login_at, last_login_at, updated_at`

登录设备登记（按 `user_id + device_name` 唯一）：每次登录 upsert，**登出不删，保留登录历史**。在线状态由「是否存在未过期会话（bf_auth_session）」判定；`GET /api/auth/devices` 返回本表**全部历史 + 在线/离线状态**，强制下线（撤销该设备全部会话）后变为离线，`DELETE /api/auth/devices` 删除离线设备记录后不再展示。

> 以上 5 张表统一由可重复迁移 `db/R__V1_init.sql` 创建（**项目约定：新表一律追加进 `R__V1_init.sql`，不单独建迁移脚本**；可重复迁移文件有改动即自动重新执行，全表 `IF NOT EXISTS` 幂等），**所有表与字段均带 COMMENT 注释**，便于管理与理解。

## 常用查询

### 文件/文件夹大小（递归汇总）

`GET /api/files/{id}/size` 计算文件/文件夹大小：文件直接返回自身 `size_bytes`；文件夹用 **MySQL 8 递归 CTE** 按 `parent_id` 树汇总其子树内所有活跃文件字节数（目录深度不限，避免 `relative_path LIKE` 的通配符转义问题）。下述 SQL 使用实际数据库表名 `bf_file_item`：

```sql
WITH RECURSIVE sub_tree AS (
  SELECT id FROM bf_file_item WHERE id = #{folderId} AND status = 'ACTIVE'
  UNION ALL
  SELECT c.id FROM bf_file_item c JOIN sub_tree s ON c.parent_id = s.id WHERE c.status = 'ACTIVE'
)
SELECT COALESCE(SUM(size_bytes), 0) FROM bf_file_item
WHERE id IN (SELECT id FROM sub_tree) AND item_type = 'FILE'
```

## 主要索引

- `user(username)` UNIQUE
- `file_item(storage_root_id, parent_id, deleted)`
- `file_item(storage_root_id, relative_path)` UNIQUE
- `share_link(token_hash)` UNIQUE
- `share_link(created_by, status, created_at)`
- `notification(user_id, read_status, created_at)`

## 一致性原则

- 磁盘操作成功后再提交数据库状态
- 下载完成后创建 file_item 记录
- 隐私密码更新后清理已有 private_folder_access
- 分享过期/撤销/超次后不可访问
