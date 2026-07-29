# API 设计

## 基础约定

- 前缀：`/api`
- 鉴权：`Authorization: Bearer <token>`
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

### 文件
- `GET /api/files?storageRootId=&parentId=&page=&size=`
- `POST /api/files/upload`
- `GET /api/files/download/{fileId}`
- `POST /api/files/folders`
- `PATCH /api/files/{id}/rename` · `PATCH /api/files/{id}/move`
- `DELETE /api/files/{id}`
- `POST /api/files/{id}/privacy` · `DELETE /api/files/{id}/privacy`
- `POST /api/files/{id}/privacy/verify`

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

### 下载
- `POST/GET /api/downloads` · `GET /api/downloads/{id}`
- `POST /api/downloads/{id}/pause` · `POST /api/downloads/{id}/resume`
- `DELETE /api/downloads/{id}`

后端通过 aria2 JSON-RPC 管理下载，定时同步状态。

### 传输 · 通知 · 设备 · 事件
- `GET /api/transfers` · `GET /api/transfers/{id}`
- `GET /api/notifications` · `PATCH /api/notifications/{id}/read`
- `POST /api/devices/register` · `GET /api/devices` · `PATCH /api/devices/{id}`
- `GET /api/events`（SSE）：TRANSFER_PROGRESS / DOWNLOAD_COMPLETED / DOWNLOAD_FAILED / NOTIFICATION_CREATED

## 安全要求

- 文件 ID 在服务端解析为受控路径，不返回 `root_path`
- 上传文件名清洗非法字符
- 普通用户通过 user_storage_permission 校验范围
- 公开分享接口记录访问日志
