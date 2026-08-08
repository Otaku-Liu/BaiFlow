# API 设计

## 基础约定

- 前缀：`/api`
- 鉴权：`Authorization: Bearer <token>`（浏览器直接请求场景支持 `?token=` 查询参数 fallback）
- 国际化：前端传 `Accept-Language: zh-CN` / `en` 头，后端错误消息自动切换
- JSON 请求/响应，文件上传用 `multipart/form-data`
- 时间：ISO-8601 字符串
- 分页参数：`page`、`size`
- 分享访问：`/api/public/shares/**`，不要求登录

## 统一响应

```json
{ "code": 0, "message": "success", "data": {}, "traceId": "..." }
```

分页响应：`{ "records": [], "page": 1, "size": 20, "total": 0 }`

## 错误码

`code` 为 5 位数字错误码，按错误域分组（定义见后端 `ErrorCode.java`）；`message` 按请求头 `Accept-Language`（`zh-CN` 默认 / `en`）返回对应语言文案。

| 错误码 | 含义 |
|---|---|
| 0 | 成功 |
| 40001 | 请求参数校验失败 |
| 40101 | 未登录或 token 无效 |
| 40102 | 用户名或密码错误 |
| 40103 | 需要提取码 |
| 40104 | 提取码错误 |
| 40105 | 需要隐私文件夹密码 |
| 40106 | 隐私文件夹密码错误 |
| 40301 | 无权限 |
| 40401 | 资源不存在 |
| 40402 | 分享链接无效 |
| 40901 | 笔记已被其他设备修改（乐观并发冲突） |
| 40902 | 用户名已存在 |
| 41001 | 分享链接已过期 |
| 42301 | 账号已被锁定 |
| 42302 | 账号已被禁用 |
| 42901 | 分享访问或下载次数已达上限 |
| 50000 | 服务端内部错误 |
| 50001 | 文件操作失败 |
| 50002 | 存储根目录不可用 |
| 50003 | 下载引擎错误 |

## 接口清单

### 系统
- `GET /api/health`

### 认证
- `POST /api/auth/login` — 登录建会话，返回 `{ token, sessionId, expiresAt, user }`；设备类型/名称走 `X-Device-Type` / `X-Device-Name` 请求头（ANDROID 长期 / WEB 短期）
  - 登录失败锁定：15 分钟内连续失败 5 次返回 `42301`，用户状态持久化为 `LOCKED`；锁键到期后由定时任务（每 60s）或登录兜底判定自动恢复为 `NORMAL`
- `POST /api/auth/logout` — 吊销当前请求 token 对应的会话（立即生效）
- `GET /api/auth/me`
- `PATCH /api/auth/profile`
- `POST /api/auth/avatar`（multipart, ≤1MB, jpg/png/gif/webp）
- `POST /api/auth/change-password` — 改密后吊销该用户全部会话（所有设备下线）
- `GET /api/auth/sessions` — 当前用户的登录设备列表 `{ id, deviceName, deviceType, ip, lastUsedAt, createdAt, current }`
- `DELETE /api/auth/sessions/{id}` — 强制下线某登录设备（本人任意会话；管理员可任意用户）

### 用户（管理员）
- `GET/POST /api/users` · `PATCH /api/users/{id}` · `POST /api/users/{id}/reset-password`
- `DELETE /api/users?ids=id1,id2`（批量删除）
- `PATCH /api/users?ids=id1,id2&status=DISABLED`（批量禁用/启用，仅 ADMIN，目标仅限 USER 角色；拒绝 `LOCKED` 目标）
- `GET/PUT /api/users/{id}/permissions`
- 用户状态仅支持 `NORMAL` / `DISABLED`（管理员禁用）；`LOCKED` 由登录失败自动锁定维护，不可手动设置，锁键到期自动恢复
- 将锁定中的用户改为其他状态（如禁用）时，服务端会清除其 Redis 登录锁定键，避免残留锁键拦截登录

### 存储根目录
- `GET /api/storage-roots/active`（返回所有 ACTIVE 状态的存储根目录，供文件中心选择器使用）
- `GET/POST /api/storage-roots` · `PATCH /api/storage-roots/{id}`（管理员）
- `POST /api/storage-roots/{id}/check`（管理员检测 NAS 连通性）

### 文件
- `GET /api/files?storageRootId=&parentId=&page=&size=&viewUserId=`
- `POST /api/files/upload`（支持 `viewUserId` 参数）
- `GET /api/files/download/{fileId}`
- `POST /api/files/folders`（支持 `viewUserId` 参数）
- `PATCH /api/files/{id}/rename` · `PATCH /api/files/{id}/move`
- `DELETE /api/files/{id}`
- `POST /api/files/{id}/privacy` · `DELETE /api/files/{id}/privacy`
- `POST /api/files/{id}/privacy/verify`

**`viewUserId` 参数**：管理员传入此参数可切换查看指定用户的文件视图。非管理员或未传入时，文件列表自动限定在当前用户的主目录（以用户名命名的文件夹）内。普通用户无法访问主目录上层，确保文件隔离。

隐私文件夹访问需传 `X-Privacy-Access-Token` 头，令牌有效期 30 分钟。

### 分享
- `POST/GET /api/shares` · `GET/PATCH/DELETE /api/shares/{id}`
- `GET /api/shares/{id}/analytics`（管理员）

### 公开分享
- `GET /api/public/shares/{token}`
- `POST /api/public/shares/{token}/verify-code`
- `POST /api/public/shares/{token}/verify-private-password`
- `GET /api/public/shares/{token}/files`
- `GET /api/public/shares/{token}/download`

### 文件预览与进度
- `GET /api/files/{id}/preview` — inline 流式返回文件（支持 Range 请求），Content-Type 按扩展名推断。前端通过 Axios 获取 blob 后创建 Object URL 加载
- `GET /api/files/{id}/progress` — 查询当前用户的播放/阅读进度 `{ positionType, positionValue, updatedAt }`
- `PUT /api/files/{id}/progress` — 保存进度 `{ positionType, positionValue }`

预览 URL 同时支持 `?token=` 查询参数鉴权。

**Office 文档**（doc/docx/xls/xlsx/ppt/pptx/odt/ods/odp）**暂不支持在线预览**：前端将其归为不支持类型，预览抽屉降级为「下载查看」。后端 `FileConvertService`（LibreOffice 转 PDF）为遗留代码，依赖未安装且前端不再触发。

### 随手记（笔记）
- `GET /api/notes?page=&size=&keyword=&viewUserId=` — 分页列出笔记（不含正文，按更新时间倒序）；`keyword` 搜标题/正文；非管理员限本人，管理员可 `viewUserId` 切换
- `POST /api/notes` — 新建 `{ title, content }`（content 为 Markdown）
- `GET /api/notes/{id}` — 详情（含 Markdown 正文）
- `PATCH /api/notes/{id}` — 更新 `{ title, content, baseUpdatedAt? }`，服务端刷新 `updated_at`；`baseUpdatedAt` 为乐观并发依据，早于服务端当前 `updated_at` 时返回业务码 `40901`（NOTE_CONFLICT，客户端可选覆盖/重新加载）
- `DELETE /api/notes/{id}` — 软删除（status=DELETED）
- `GET /api/notes/{id}/progress` — 查询当前用户对笔记的阅读进度 `{ positionType, positionValue, updatedAt }`
- `PUT /api/notes/{id}/progress` — 保存阅读进度 `{ positionValue }`（滚动百分比 0~1）

**笔记媒体**：
- `POST /api/notes/media` — multipart `file` + 可选 `mediaType`（IMAGE/AUDIO/DRAWING）；MIME 白名单 + ≤20MB；返回 `{ id, mediaType, url, mimeType, sizeBytes, createdAt }`
- `GET /api/notes/media/{id}` — 读取媒体内容（inline）；鉴权 Bearer 头或 `?token=`（供 Web `<img>/<audio>` 渲染）；所有者或管理员
- 正文引用约定：图片/画画 `![名称](/api/notes/media/{mediaId})`；录音 `[录音](/api/notes/media/{mediaId}?mediaType=audio)`（`mediaType=audio` 供渲染器识别音频）

笔记独立于文件系统，不受存储根目录/隐私文件夹约束。Android 离线增量同步（`updatedAfter`）随客户端离线阶段落地。笔记媒体独立存储，不进文件中心列表。

### 审计日志（管理员）
- `GET /api/admin/audit-logs/login` — 分页查询登录与会话操作日志（`LOGIN_SUCCESS` / `LOGIN_FAILED` / `LOGOUT` / `FORCE_LOGOUT` / `PASSWORD_CHANGED` / `ACCOUNT_LOCKED` / `ACCOUNT_UNLOCKED`），支持用户名模糊搜索、操作类型和日期范围筛选

### 下载
- `POST/GET /api/downloads` · `GET /api/downloads/{id}`
- `POST /api/downloads/{id}/pause` · `POST /api/downloads/{id}/resume`
- `DELETE /api/downloads/{id}`

后端通过 aria2 JSON-RPC 管理下载，定时同步状态。

### 传输 · 通知 · 设备 · 事件
- `GET /api/transfers` · `GET /api/transfers/{id}`
- `GET /api/notifications` · `PATCH /api/notifications/{id}/read`
- `POST /api/devices/register` · `GET /api/devices` · `PATCH /api/devices/{id}`（**待实现**：推送设备注册，与登录会话表正交）
- `GET /api/events`（SSE，需登录）：事件类型 `NOTE_UPDATED`（已实现）/ `TRANSFER_PROGRESS` / `DOWNLOAD_COMPLETED` / `DOWNLOAD_FAILED` / `NOTIFICATION_CREATED`（后四者待各模块接入）

SSE 鉴权：浏览器 EventSource 无法携带 `Authorization` 头，使用 `GET /api/events?token=<会话token>` 查询参数（后端 `SessionAuthenticationFilter` 支持 `?token=` fallback）。`NOTE_UPDATED` 只推送给笔记所有者。

## 安全要求

- 文件 ID 在服务端解析为受控路径，不返回 `root_path`
- 上传文件名清洗非法字符
- 普通用户文件视图自动限定在主目录内（`parentId` 为空时重定向到主目录）
- 普通用户通过 user_storage_permission 校验范围
- 公开分享接口记录访问日志
