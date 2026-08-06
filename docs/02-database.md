# 数据库设计

数据库：MySQL 8。文件本体保存在磁盘，数据库只存元数据。

## 命名规范

- 表名/字段名：小写下划线
- 主键：`id`（varchar 类型）
- 时间：`created_at`、`updated_at`、`deleted_at`
- 逻辑删除：`deleted`（0/1）
- 密码/提取码/token 只存 hash
- 注释：**所有表与字段必须带 `COMMENT` 注释**，说明其含义，便于管理与理解

## 核心表

### user — 系统用户
`id, username, password_hash, display_name, avatar_url, role(ADMIN/USER/GUEST), status(ACTIVE/DISABLED/LOCKED), last_login_at, created_at, updated_at`

### user_storage_permission — 用户存储权限
`id, user_id, storage_root_id, file_item_id, permission(READ/WRITE/MANAGE), created_by, created_at, updated_at`

### storage_root — 存储根目录
`id, name, type(LOCAL/NAS_MOUNT), root_path, status(ACTIVE/OFFLINE/DISABLED), readonly, created_at, updated_at`

### file_item — 文件/目录元数据
`id, storage_root_id, parent_id, owner_user_id, name, relative_path, item_type(FILE/DIRECTORY), size_bytes, mime_type, hash_sha256, privacy_mode(NORMAL/PRIVATE), privacy_password_hash, status(ACTIVE/DELETED), created_at, updated_at, deleted_at`

`mime_type` 字段前端不展示（文件名自带扩展名），后端用于预览 MIME 路由和 Content-Type 返回。

### private_folder_access — 隐私访问会话
`id, user_id, file_item_id, access_token_hash, expires_at, created_at`

### share_link — 分享链接
`id, target_file_item_id, created_by, token_hash, extraction_code_hash, share_type(FILE/FOLDER), access_mode(VIEW/DOWNLOAD), expires_at, max_views, view_count, max_downloads, download_count, status(ACTIVE/EXPIRED/REVOKED), created_at, updated_at`

### share_access_log — 分享访问日志
`id, share_link_id, action(VIEW/DOWNLOAD/VERIFY_CODE/FAILED), ip_address, user_agent, success, failure_reason, created_at`

### download_task — 下载任务
`id, created_by, source_url, aria2_gid, target_storage_root_id, target_relative_path, status(WAITING/RUNNING/PAUSED/FAILED/COMPLETED/DELETED), progress, speed_bytes_per_second, error_message, created_at, updated_at, completed_at`

### transfer_task — 传输任务
`id, created_by, task_type(UPLOAD/DOWNLOAD/DEVICE_SEND), status(WAITING/RUNNING/PAUSED/FAILED/COMPLETED), progress, error_message, created_at, updated_at`

### device — 客户端设备
`id, user_id, name, device_type(ANDROID/WEB/SERVER/NAS), token_hash, last_seen_at, status, created_at, updated_at`

### notification — 通知
`id, user_id, level(INFO/WARN/ERROR), title, content, read_status, created_at, read_at`

### audit_log — 操作审计
`id, actor_user_id, action, target_type, target_id, ip_address, user_agent, detail, created_at`

登录事件（`LOGIN_SUCCESS` / `LOGIN_FAILED`）在此记录，供登录日志查询。

### playback_progress — 播放/阅读进度
`id, user_id, file_item_id, position_type(SECONDS/PAGE/SCROLL_PERCENT), position_value, created_at, updated_at`

每个用户对每个文件只存一条记录。`position_type` 区分视频秒数、PDF 页码、文本滚动百分比，支持跨设备断点续看。

### note — 随手记笔记
`id, user_id, title, content(LONGTEXT, Markdown), status(ACTIVE/DELETED 软删除), created_at, updated_at, deleted_at`

笔记独立于文件系统，正文直接落库。`updated_at` 由服务端显式刷新，作为乐观并发（保存携带 `baseUpdatedAt` 比对）的时间基准。`status` 软删除标记随增量拉取同步。

### note_progress — 笔记阅读进度
`id, user_id, note_id, position_type(SCROLL_PERCENT), position_value, created_at, updated_at`

每个用户对每篇笔记只存一条记录，`(user_id, note_id)` 唯一。复用 playback 的 SCROLL_PERCENT 思路，支持跨设备续读长笔记。

### note_media — 笔记媒体
`id, user_id, media_type(IMAGE/AUDIO/DRAWING), file_name, mime_type, size_bytes, created_at`

Android 富文本编辑器的图片/录音/画画媒体元数据。文件本体落磁盘（`baiflow.notes.media-path` 专用目录，文件名 `<mediaId>.<ext>`），独立于文件中心、不参与 `/api/files` 列表；正文通过 Markdown 引用（`![…](/api/notes/media/{id})` / `[录音](…?mediaType=audio)`）关联媒体。

### auth_session — 登录会话
`id, user_id, device_name, device_type(ANDROID/WEB), ip, user_agent, token_hash(SHA-256), expires_at, last_used_at, created_at, revoked_at`

登录会话（模型 2）：每次请求按 `token_hash` 精确查询校验（未吊销/未过期），吊销即时生效。ANDROID 会话滑动续期（180 天不活跃兜底），WEB 会话固定短时。`token_hash` 只存哈希，数据库泄露不暴露可用 token。详见 `docs/09-auth-sessions.md`。

> 以上 5 张表统一由可重复迁移 `db/R__V1_init.sql` 创建（**项目约定：新表一律追加进 `R__V1_init.sql`，不单独建迁移脚本**；可重复迁移文件有改动即自动重新执行，全表 `IF NOT EXISTS` 幂等），**所有表与字段均带 COMMENT 注释**，便于管理与理解。

## 主要索引

- `user(username)` UNIQUE
- `file_item(storage_root_id, parent_id, deleted)`
- `file_item(storage_root_id, relative_path)` UNIQUE
- `share_link(token_hash)` UNIQUE
- `share_link(created_by, status, created_at)`
- `download_task(created_by, status, created_at)`
- `notification(user_id, read_status, created_at)`

## 一致性原则

- 磁盘操作成功后再提交数据库状态
- 下载完成后创建 file_item 记录
- 隐私密码更新后清理已有 private_folder_access
- 分享过期/撤销/超次后不可访问
