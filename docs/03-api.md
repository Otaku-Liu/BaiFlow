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
| 40105 | 需要隐私空间/隐私文件夹密码 |
| 40106 | 隐私空间/隐私文件夹密码错误 |
| 40107 | 隐私空间尚未设置密码，需先设置 |
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

## 接口清单

### 系统
- `GET /api/health`

### 认证
- `POST /api/auth/login` — 登录建会话，返回 `{ token, sessionId, expiresAt, user }`；设备类型/名称走 `X-Device-Type` / `X-Device-Name` 请求头（ANDROID 长期 / WEB 短期）
  - 登录失败锁定：15 分钟内连续失败 5 次返回 `42301`，用户状态持久化为 `LOCKED`；锁键到期后由定时任务（每 60s）或登录兜底判定自动恢复为 `NORMAL`
- `POST /api/auth/logout` — 吊销当前请求 token 对应的会话（立即生效）
- `GET /api/auth/me`
- `PATCH /api/auth/profile` — 更新展示名，**不允许为空**（空值返回 `40001` 展示名不能为空）
- `POST /api/auth/avatar`（multipart, ≤1MB, jpg/png/gif/webp）— 头像文件名带时间戳版本，返回 URL 每次上传唯一（避免浏览器缓存旧图）；服务端会删除旧头像文件；`/avatars/**` 为公开静态资源（生产 nginx alias，开发经后端静态映射）
- `DELETE /api/auth/avatar` — 删除当前用户头像（删除文件 + 清空 avatarUrl），回到首字占位
- `POST /api/auth/change-password` — 改密后吊销该用户全部会话（所有设备下线）
- `GET /api/auth/sessions` — 当前用户的登录会话列表 `{ id, deviceName, deviceType, ip, lastUsedAt, createdAt, current }`
- `GET /api/auth/devices` — 当前用户的登录设备列表（**含历史与在线状态**；强制下线（撤销全部会话）后变为离线）`{ deviceName, deviceType, firstLoginAt, lastLoginAt, lastActiveAt, online, current, activeSessionId }`
- `DELETE /api/auth/sessions/{id}` — 强制下线某登录设备（**撤销该设备名下的全部会话**，排除当前会话；本人任意会话；管理员可任意用户）
- `DELETE /api/auth/devices?deviceName=` — 删除某登录设备（仅可删除**离线**设备：在线设备需先强制下线；撤销其历史会话 + 删除登录历史记录；写审计日志 `DELETE_DEVICE`）

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
- `GET /api/files?storageRootId=&parentId=&page=&size=&viewUserId=&sort=&dir=`（文件列表每项含 `downloadCount`、`lastOpenedAt`、`childCount`——目录的直接活跃子项数（文件+子文件夹），**隐私目录为 null 不返回**；`sort`：`name`/`createdAt`/`size`（默认 `name`），`dir`：`asc`/`desc`（缺省按惯例：名称升序 / 创建时间降序 / 大小降序），**任何排序都目录优先**；非法 `sort` 回落 `name`）
- `POST /api/files/upload`（支持 `viewUserId` 参数）
- `GET /api/files/download/{fileId}`（登录用户直接下载，写入下载记录）
- `GET /api/files/{id}/downloads` — 文件的下载记录分页（时间/来源/下载人/IP；本人文件，管理员可查任意）
- aria2 URL/磁力/BT 下载任务中心（`/api/downloads**`）已整体移除，文件下载仅经上述两条通道（登录直接下载 + 分享下载）
- `POST /api/files/folders`（支持 `viewUserId` 参数）
- `PATCH /api/files/{id}/rename` · `PATCH /api/files/{id}/move`
- `DELETE /api/files/{id}`（软删除；**级联删除该文件的播放/阅读进度行**）
- **隐私限制**：主目录、根级隐私空间、`privacyMode == PRIVATE` 的隐私文件夹本身**不支持重命名/删除**（需先 `DELETE /api/files/{id}/privacy` 移除隐私）；重命名/删除隐私文件夹**内**的项目需 `X-Privacy-Access-Token`
- `POST /api/files/{id}/privacy` · `DELETE /api/files/{id}/privacy`
- `POST /api/files/{id}/privacy/verify`

**`viewUserId` 参数**：管理员传入此参数可切换查看指定用户的文件视图。非管理员或未传入时，文件列表自动限定在当前用户的主目录（以用户名命名的文件夹）内。普通用户无法访问主目录上层，确保文件隔离。

**隐私空间（新模型）**：每个用户主目录下自动创建「隐私空间」子目录（`PRIVATE`、初始无密码）。首访时 `40107` 要求先设置密码（`POST privacy`）；已设置后每次进入需 `40105` 验证密码换取 `X-Privacy-Access-Token`（30 分钟）；**管理员访问免隐私密码**。主目录（根级文件夹）与「隐私空间」**均不可重命名、不可删除**（管理员也不可），主目录不可设隐私。任意文件夹不再提供「设为隐私」，仅隐私空间一个隐私入口；旧隐私文件夹保留兼容。已设密码的隐私空间 `POST privacy` 返回拒绝（暂不支持重置）。

### 分享
- `POST/GET /api/shares` · `GET/PATCH/DELETE /api/shares/{id}`（PATCH 的 `status` 支持 `ACTIVE` / `DISABLED`，创建者可停用/启用）
  - **分享类型自动推导**：`POST /api/shares` 的 `shareType` 已可选，服务端按目标的 `itemType` 自动推导（目录→FOLDER，文件→FILE），前端无需选择
  - **隐私文件夹不可分享**：目标自身或其**任一父链**为 `PRIVATE` 时返回 `40301 隐私文件夹不可分享`（前端选择器已禁用隐私项）
- `GET /api/shares/{id}/analytics`（管理员）
- 分享提取码连续错误 5 次锁定 15 分钟（Redis）

### 公开分享
- `GET /api/public/shares/{token}`
- `POST /api/public/shares/{token}/verify-code`
- `POST /api/public/shares/{token}/verify-private-password`
- `GET /api/public/shares/{token}/files`
- `GET /api/public/shares/{token}/download`

### 文件预览与进度
- `GET /api/files/{id}/preview` — inline 流式返回文件（支持 Range 请求），Content-Type 按扩展名推断。前端通过 Axios 获取 blob 后创建 Object URL 加载
- `GET /api/files/{id}/size` — 计算文件/文件夹大小：文件返回自身 `sizeBytes`，文件夹用 MySQL 递归 CTE 汇总子树内所有活跃文件字节数（SQL 见 `docs/02-database.md` 常用查询）。权限复用文件操作的归属 + 隐私校验：文件校验父目录链隐私（`checkPrivacyAccess(parentId)`），文件夹从自身开始校验（`checkPrivacyAccess(id)`，隐私文件夹本身未解锁不可计算）；隐私分支需 `X-Privacy-Access-Token`
- `GET /api/files/{id}/progress` — 查询当前用户的播放/阅读进度 `{ positionType, positionValue, updatedAt }`
- `PUT /api/files/{id}/progress` — 保存进度 `{ positionType, positionValue }`

预览 URL 同时支持 `?token=` 查询参数鉴权。

**Office 文档**（doc/docx/xls/xlsx/ppt/pptx/odt/ods/odp）**暂不支持在线预览**：前端将其归为不支持类型，预览抽屉降级为「下载查看」。后端 `FileConvertService`（LibreOffice 转 PDF）为遗留代码，依赖未安装且前端不再触发。

### 随手记（笔记）
- `GET /api/notes?page=&size=&keyword=&viewUserId=` — 分页列出笔记（不含正文，按更新时间倒序）；`keyword` 搜标题/正文；非管理员限本人，管理员可 `viewUserId` 切换
- `POST /api/notes` — 新建 `{ title, content }`（content 为 Markdown）
- `GET /api/notes/{id}` — 详情（含 Markdown 正文）
- `PATCH /api/notes/{id}` — 更新 `{ title, content, baseUpdatedAt? }`，服务端刷新 `updated_at`；`baseUpdatedAt` 为乐观并发依据，早于服务端当前 `updated_at` 时返回业务码 `40901`（NOTE_CONFLICT，客户端可选覆盖/重新加载）
- `DELETE /api/notes/{id}` — 软删除（status=DELETED；**级联删除该笔记的阅读进度行**）
- `GET /api/notes/{id}/progress` — 查询当前用户对笔记的阅读进度 `{ positionType, positionValue, updatedAt }`
- `PUT /api/notes/{id}/progress` — 保存阅读进度 `{ positionValue }`（滚动百分比 0~1）

**笔记媒体**：
- `POST /api/notes/media` — multipart `file` + 可选 `mediaType`（IMAGE/AUDIO/DRAWING）；MIME 白名单 + ≤20MB；返回 `{ id, mediaType, url, mimeType, sizeBytes, createdAt }`
- `GET /api/notes/media/{id}` — 读取媒体内容（inline）；鉴权 Bearer 头或 `?token=`（供 Web `<img>/<audio>` 渲染）；所有者或管理员
- 正文引用约定：图片/画画 `![名称](/api/notes/media/{mediaId})`；录音 `[录音](/api/notes/media/{mediaId}?mediaType=audio)`（`mediaType=audio` 供渲染器识别音频）

笔记独立于文件系统，不受存储根目录/隐私文件夹约束。Android 离线增量同步（`updatedAfter`）随客户端离线阶段落地。笔记媒体独立存储，不进文件中心列表。

### 审计日志（管理员）
- `GET /api/admin/audit-logs/login` — 分页查询登录与会话操作日志（`LOGIN_SUCCESS` / `LOGIN_FAILED` / `LOGOUT` / `FORCE_LOGOUT` / `PASSWORD_CHANGED` / `ACCOUNT_LOCKED` / `ACCOUNT_UNLOCKED`），支持用户名模糊搜索、操作类型和日期范围筛选

### 传输 · 通知 · 设备 · 事件
- `GET /api/transfers` · `GET /api/transfers/{id}`
- `GET /api/notifications` · `PATCH /api/notifications/{id}/read`
- `GET /api/events`（SSE，需登录）：事件类型 `NOTE_UPDATED`（笔记跨端同步刷新；曾规划的传输/下载/通知事件已移除）

SSE 鉴权：浏览器 EventSource 无法携带 `Authorization` 头，使用 `GET /api/events?token=<会话token>` 查询参数（后端 `SessionAuthenticationFilter` 支持 `?token=` fallback）。`NOTE_UPDATED` 只推送给笔记所有者。

## 安全要求

- 文件 ID 在服务端解析为受控路径，不返回 `root_path`
- 上传文件名清洗非法字符
- 普通用户文件视图自动限定在主目录内（`parentId` 为空时重定向到主目录）
- 普通用户通过 user_storage_permission 校验范围
- 下载通道仅两条：登录用户（owner/admin，文件归属 + 隐私校验）与有效分享链接（token/过期/次数/提取码）；**不存在匿名直下端点**
- 所有下载写入 `bf_download_record`，可追溯并按文件聚合下载次数（ADMIN 审计）
- 公开分享接口记录访问日志
