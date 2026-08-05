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
{ "code": "OK", "message": "success", "data": {}, "traceId": "..." }
```

分页响应：`{ "records": [], "page": 1, "size": 20, "total": 0 }`

## 错误码

`OK` · `UNAUTHORIZED` · `FORBIDDEN` · `VALIDATION_ERROR` · `NOT_FOUND` · `FILE_OPERATION_FAILED` · `STORAGE_ROOT_OFFLINE` · `DOWNLOAD_ENGINE_ERROR` · `SHARE_LINK_INVALID` · `SHARE_LINK_EXPIRED` · `SHARE_LIMIT_EXCEEDED` · `EXTRACTION_CODE_REQUIRED` · `EXTRACTION_CODE_INVALID` · `PRIVATE_PASSWORD_REQUIRED` · `PRIVATE_PASSWORD_INVALID` · `INTERNAL_ERROR`

## 接口清单

### 系统
- `GET /api/health`

### 认证
- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `PATCH /api/auth/profile`
- `POST /api/auth/avatar`（multipart, ≤1MB, jpg/png/gif/webp）
- `POST /api/auth/change-password`

### 用户（管理员）
- `GET/POST /api/users` · `PATCH /api/users/{id}` · `POST /api/users/{id}/reset-password`
- `DELETE /api/users?ids=id1,id2`（批量删除）
- `GET/PUT /api/users/{id}/permissions`

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
- `PATCH /api/notes/{id}` — 更新 `{ title, content }`，服务端刷新 `updated_at`
- `DELETE /api/notes/{id}` — 软删除（status=DELETED）
- `GET /api/notes/{id}/progress` — 查询当前用户对笔记的阅读进度 `{ positionType, positionValue, updatedAt }`
- `PUT /api/notes/{id}/progress` — 保存阅读进度 `{ positionValue }`（滚动百分比 0~1）

笔记独立于文件系统，不受存储根目录/隐私文件夹约束。Android 离线增量同步（`updatedAfter`）随客户端离线阶段落地。

### 审计日志（管理员）
- `GET /api/admin/audit-logs/login` — 分页查询登录日志，支持用户名模糊搜索、登录结果和日期范围筛选

### 下载
- `POST/GET /api/downloads` · `GET /api/downloads/{id}`
- `POST /api/downloads/{id}/pause` · `POST /api/downloads/{id}/resume`
- `DELETE /api/downloads/{id}`

后端通过 aria2 JSON-RPC 管理下载，定时同步状态。

### 传输 · 通知 · 设备 · 事件
- `GET /api/transfers` · `GET /api/transfers/{id}`
- `GET /api/notifications` · `PATCH /api/notifications/{id}/read`
- `POST /api/devices/register` · `GET /api/devices` · `PATCH /api/devices/{id}`
- `GET /api/events`（SSE，需登录）：事件类型 `NOTE_UPDATED`（已实现）/ `TRANSFER_PROGRESS` / `DOWNLOAD_COMPLETED` / `DOWNLOAD_FAILED` / `NOTIFICATION_CREATED`（后四者待各模块接入）

SSE 鉴权：浏览器 EventSource 无法携带 `Authorization` 头，使用 `GET /api/events?token=<jwt>` 查询参数（后端 `JwtAuthenticationFilter` 已支持 `?token=` fallback）。`NOTE_UPDATED` 只推送给笔记所有者。

## 安全要求

- 文件 ID 在服务端解析为受控路径，不返回 `root_path`
- 上传文件名清洗非法字符
- 普通用户文件视图自动限定在主目录内（`parentId` 为空时重定向到主目录）
- 普通用户通过 user_storage_permission 校验范围
- 公开分享接口记录访问日志
