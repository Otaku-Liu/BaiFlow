# BaiFlow 数据库设计

## 数据库选择
使用 MySQL 8。数据库保存用户、角色、授权范围、存储根目录、文件元数据、隐私文件夹、分享链接、任务、通知和审计日志。文件本体保存在磁盘或 NAS 挂载目录。

## 命名规范
- 表名使用小写下划线。
- 主键字段统一为 `id`，类型建议 `varchar(32)` 或 `varchar(64)`。
- 时间字段统一为 `created_at`、`updated_at`、`deleted_at`。
- 逻辑删除字段统一为 `deleted`，0 表示未删除，1 表示已删除。
- 密码、提取码、访问 token 只保存 hash，不保存明文。

## 核心表
### user
系统用户。
- id
- username
- password_hash
- display_name
- role：ADMIN、USER、GUEST
- status：ACTIVE、DISABLED、LOCKED
- last_login_at
- created_at
- updated_at

### user_storage_permission
用户可访问的存储范围。MVP 可先按 Storage Root 或目录授权，不做复杂 ACL。
- id
- user_id
- storage_root_id
- file_item_id：为空表示整个 Storage Root，否则表示指定目录或文件
- permission：READ、WRITE、MANAGE
- created_by
- created_at
- updated_at

### storage_root
存储根目录。
- id
- name
- type：LOCAL、NAS_MOUNT
- root_path
- status：ACTIVE、OFFLINE、DISABLED
- readonly
- created_at
- updated_at

### file_item
文件和目录元数据。
- id
- storage_root_id
- parent_id
- owner_user_id
- name
- relative_path
- item_type：FILE、DIRECTORY
- size_bytes
- mime_type
- hash_sha256
- privacy_mode：NORMAL、PRIVATE
- privacy_password_hash：仅隐私文件夹使用
- status：ACTIVE、DELETED
- created_at
- updated_at
- deleted_at

### private_folder_access
隐私文件夹访问会话记录，用于短时间免重复输入密码。
- id
- user_id
- file_item_id
- access_token_hash
- expires_at
- created_at

### share_link
文件或文件夹分享链接。
- id
- target_file_item_id
- created_by
- token_hash
- extraction_code_hash
- share_type：FILE、FOLDER
- access_mode：VIEW、DOWNLOAD
- expires_at
- max_views
- view_count
- max_downloads
- download_count
- require_private_password
- status：ACTIVE、EXPIRED、REVOKED
- created_at
- updated_at

### share_access_log
分享访问日志。
- id
- share_link_id
- action：VIEW、DOWNLOAD、VERIFY_CODE、FAILED
- ip_address
- user_agent
- success
- failure_reason
- created_at

### download_task
下载任务。
- id
- created_by
- source_url
- aria2_gid
- target_storage_root_id
- target_relative_path
- status：WAITING、RUNNING、PAUSED、FAILED、COMPLETED、DELETED
- progress
- speed_bytes_per_second
- error_message
- created_at
- updated_at
- completed_at

### transfer_task
上传、下载、设备流转任务。
- id
- created_by
- task_type：UPLOAD、DOWNLOAD、DEVICE_SEND
- source_type
- target_type
- status：WAITING、RUNNING、PAUSED、FAILED、COMPLETED
- progress
- error_message
- created_at
- updated_at

### device
客户端设备。
- id
- user_id
- name
- device_type：ANDROID、WEB、SERVER、NAS
- token_hash
- last_seen_at
- status
- created_at
- updated_at

### notification
通知。
- id
- user_id
- level：INFO、WARN、ERROR
- title
- content
- read_status
- created_at
- read_at

### audit_log
操作审计。
- id
- actor_user_id
- action
- target_type
- target_id
- ip_address
- user_agent
- created_at

## 索引建议
- `user(username)` 唯一索引
- `user(role, status)`
- `user_storage_permission(user_id, storage_root_id)`
- `file_item(storage_root_id, parent_id, deleted)`
- `file_item(storage_root_id, relative_path)` 唯一索引
- `file_item(owner_user_id, created_at)`
- `share_link(token_hash)` 唯一索引
- `share_link(created_by, status, created_at)`
- `share_access_log(share_link_id, created_at)`
- `download_task(created_by, status, created_at)`
- `transfer_task(created_by, status, created_at)`
- `notification(user_id, read_status, created_at)`
- `audit_log(actor_user_id, created_at)`

## 状态一致性原则
- 磁盘操作成功后再提交数据库状态。
- 下载任务完成后再创建或更新 `file_item`。
- 删除文件必须记录审计日志。
- 分享链接过期、撤销、超过次数限制后不能继续访问。
- 隐私文件夹密码更新后，应清理已有 `private_folder_access`。
- 后续如做回收站，删除先移动到 `.baiflow-trash`，再标记 `DELETED`。
