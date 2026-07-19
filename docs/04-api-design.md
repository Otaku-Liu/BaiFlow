# BaiFlow API 设计

## 基础约定
- API 前缀：`/api`
- 鉴权方式：`Authorization: Bearer <token>`
- 请求和响应使用 JSON，文件上传使用 `multipart/form-data`。
- 时间使用 ISO-8601 字符串。
- 分享访问使用 `/s/{token}` 或 `/api/public/shares/**`，不要求登录，但必须校验分享 token、提取码、过期时间、次数限制和隐私密码。

## 统一响应
```json
{
  "code": "OK",
  "message": "success",
  "data": {},
  "traceId": "request-trace-id"
}
```

## 分页响应
```json
{
  "records": [],
  "page": 1,
  "size": 20,
  "total": 0
}
```

## 错误码
- OK：成功
- UNAUTHORIZED：未登录或 token 无效
- FORBIDDEN：无权限
- VALIDATION_ERROR：请求参数错误
- NOT_FOUND：资源不存在
- FILE_OPERATION_FAILED：文件操作失败
- STORAGE_ROOT_OFFLINE：存储根目录不可用
- DOWNLOAD_ENGINE_ERROR：aria2 或下载引擎错误
- SHARE_LINK_INVALID：分享链接无效
- SHARE_LINK_EXPIRED：分享链接已过期
- SHARE_LIMIT_EXCEEDED：分享访问或下载次数已达上限
- EXTRACTION_CODE_REQUIRED：需要提取码
- EXTRACTION_CODE_INVALID：提取码错误
- PRIVATE_PASSWORD_REQUIRED：需要隐私文件夹密码
- PRIVATE_PASSWORD_INVALID：隐私文件夹密码错误
- INTERNAL_ERROR：服务端错误

## 系统接口
- `GET /api/health`：返回服务基础状态，以及 `components.database.status` 数据库连通状态，用于 Phase 1 验收和部署探活

## 认证接口
- `POST /api/auth/login`：登录
- `POST /api/auth/logout`：退出
- `GET /api/auth/me`：当前用户信息

## 用户接口
- `GET /api/users`：用户列表，管理员可用
- `POST /api/users`：创建用户，管理员可用
- `PATCH /api/users/{id}`：更新用户显示名、角色、状态，管理员可用
- `POST /api/users/{id}/reset-password`：重置密码，管理员可用
- `GET /api/users/{id}/permissions`：用户资源权限
- `PUT /api/users/{id}/permissions`：更新用户资源权限

## 文件接口
- `GET /api/files`：文件列表，参数包括 `storageRootId`、`parentId`、`page`、`size`
- `POST /api/files/upload`：上传文件
- `GET /api/files/download/{fileId}`：下载文件
- `POST /api/files/folders`：新建文件夹
- `PATCH /api/files/{id}/rename`：重命名
- `PATCH /api/files/{id}/move`：移动
- `DELETE /api/files/{id}`：删除
- `POST /api/files/{id}/privacy`：设置或更新隐私文件夹密码
- `DELETE /api/files/{id}/privacy`：取消隐私文件夹
- `POST /api/files/{id}/privacy/verify`：验证隐私文件夹密码，返回短期访问令牌 `{accessToken, expiresAt}`

**隐私文件夹访问要求：** 访问隐私文件夹（PRIVATE 模式）时，除 `POST /api/files/{id}/privacy/verify` 外，所有文件接口均需要传递 `X-Privacy-Access-Token` 头，值为 `/api/files/{id}/privacy/verify` 返回的 `accessToken`。令牌有效期 30 分钟。隐私检查适用于所有角色，包括 ADMIN。

## 分享接口
- `POST /api/shares`：为文件或文件夹创建分享链接
- `GET /api/shares`：当前用户创建的分享列表；管理员可查看全部
- `GET /api/shares/{id}`：分享详情
- `PATCH /api/shares/{id}`：更新过期时间、次数限制、提取码、状态
- `DELETE /api/shares/{id}`：撤销分享链接

## 公开分享访问接口
- `GET /api/public/shares/{token}`：查看分享元信息
- `POST /api/public/shares/{token}/verify-code`：校验提取码
- `POST /api/public/shares/{token}/verify-private-password`：校验隐私文件夹密码
- `GET /api/public/shares/{token}/files`：浏览分享文件夹
- `GET /api/public/shares/{token}/download`：下载分享文件

## 下载接口
- `POST /api/downloads`：创建下载任务。请求体 `{ sourceUrl, targetStorageRootId, targetRelativePath? }`
- `GET /api/downloads`：下载任务列表，参数 `status`（可选）、`page`、`size`
- `GET /api/downloads/{id}`：下载任务详情
- `POST /api/downloads/{id}/pause`：暂停正在运行的任务
- `POST /api/downloads/{id}/resume`：恢复已暂停的任务
- `DELETE /api/downloads/{id}`：删除任务（逻辑删除，同时取消 aria2 中的对应任务）

**下载引擎说明：** 后端通过 aria2 JSON-RPC 提交和管理下载任务。定时同步任务定期从 aria2 查询状态，更新本地进度和速度。下载完成后自动在 file_item 表中创建文件记录。aria2 配置项 `baiflow.aria2.url`（默认 `http://127.0.0.1:6800/jsonrpc`）和 `baiflow.aria2.secret`（可选密钥）。

## 传输接口
- `GET /api/transfers`：传输任务列表
- `GET /api/transfers/{id}`：传输任务详情

## 通知接口
- `GET /api/notifications`：通知列表
- `PATCH /api/notifications/{id}/read`：标记已读

## 设备接口
- `POST /api/devices/register`：设备注册
- `GET /api/devices`：设备列表
- `PATCH /api/devices/{id}`：更新设备名称或状态

## 实时事件
第一版优先使用 SSE：
- `GET /api/events`

事件类型：
- TRANSFER_PROGRESS
- DOWNLOAD_COMPLETED
- DOWNLOAD_FAILED
- NOTIFICATION_CREATED

## 安全要求
- 所有文件 ID 必须在服务端解析为受控路径。
- API 不返回 `root_path` 等服务器真实路径。
- 上传文件名必须清洗非法字符。
- 下载接口必须校验文件是否属于当前 Storage Root。
- 普通用户必须通过 `user_storage_permission` 校验访问范围。
- 分享 token、提取码、隐私密码都只保存 hash。
- 公开分享接口必须记录访问日志和失败原因。
