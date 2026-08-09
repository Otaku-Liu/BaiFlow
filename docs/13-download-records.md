# 13 · 文件下载记录与下载安全（ADR）

> 状态：**已实现**（2026-08-09）
> 类型：架构决策记录（ADR）
> 相关：`docs/01-architecture.md`、`docs/02-database.md`、`docs/03-api.md`、`docs/04-frontend.md`、`docs/05-android.md`、`docs/09-auth-sessions.md`

## 1. 背景与问题

原「下载中心」是 aria2 URL/磁力/BT 下载任务管理（贴链接由 aria2 下载到服务器），与用户最初设想的「文件中心下载的记录 + 次数统计」不符。用户确认：**移除 aria2 功能**，下载中心不再保留独立页面；文件中心展示每文件下载次数；保留下载记录供 ADMIN 管理与审计；并对「防文件被随意下载」做安全管理。

## 2. 决策

| 决策 | 结论 |
|---|---|
| aria2 URL 下载 | **整体移除**：后端 `download` 模块、`bf_download_task` 表、Web 下载中心、Android 传输任务列表页全部下线；`baiflow.aria2.*` 配置与文档同步清理 |
| 下载记录 | 新增 `bf_download_record` 表：每次下载一条（文件、文件名快照、下载人、来源 CLIENT/SHARE、分享 ID、IP、UA、时间） |
| 下载次数 | 文件中心列表显示每文件下载次数（CLIENT + SHARE 均计入）；点击文件查看下载详情（时间/来源/下载人/IP） |
| 记录范围 | 直接下载（登录用户，web/Android 同走 `/api/files/download/{id}`）→ `source=CLIENT`、记录下载人；分享下载 → `source=SHARE`、下载人为空、关联分享 ID |
| 下载权限 | 下载通道仅两条：登录用户（owner/admin，已有）+ 有效分享链接（token/过期/次数/提取码，已有）。**不存在匿名直下端点** |
| 分享提取码限次 | 提取码错误 5 次 → Redis 锁定 15 分钟（多实例共享，参考登录锁定） |
| 分享生效管理 | 新增 `DISABLED` 状态：创建者可「停用 / 启用」自己的分享链接（ACTIVE ↔ DISABLED）；停用后访问被拒 |

## 3. 关键事实

- Android 设备↔服务器传输（前台服务 UploadService/DownloadService）**不是 aria2**，保留；Android 下载文件走 `/files/download/{fileId}`，天然被记录。
- `downloadFile` 被预览（`previewFile`）复用，故下载记录写在 **Controller 的 download 端点**而非 service，避免预览被计入。
- 分享 `validateAndLog` 只匹配 `ACTIVE`，新增 `DISABLED` 后停用即天然拒绝访问，无需额外拦截逻辑。

## 4. 数据模型（并入可重复迁移 `db/R__V1_init.sql`）

`bf_download_record`：

```sql
id                 VARCHAR(32)  -- 主键
file_id            VARCHAR(32)  -- 被下载文件
file_name          VARCHAR(512) -- 文件名快照
downloader_user_id VARCHAR(32)  -- 下载人（分享匿名为 NULL）
source             VARCHAR(16)  -- CLIENT / SHARE
share_id           VARCHAR(32)  -- 来源分享 ID（非分享为 NULL）
ip_address         VARCHAR(64)
user_agent         VARCHAR(255)
created_at         TIMESTAMP
```

## 5. 安全模型

- 直接下载：`/api/files/download/{id}` 要求登录（401）且 **owner/admin**（`checkOwnership`），隐私文件夹另需密码。
- 分享下载：`/api/public/shares/**` 要求有效 token（高熵不可猜测）+ 未过期 + 未超次 + 提取码正确；提取码连续错 5 次锁定 15 分钟。
- 所有下载写入 `bf_download_record`，可追溯（ADMIN 审计）。
- 匿名/未授权用户无任何下载通道（无公开直下端点）。

## 6. API 变更

- 移除：`/api/downloads**`（aria2 下载任务 CRUD）
- 新增：`GET /api/files/{id}/downloads` — 文件下载记录分页（本人文件；管理员可查任意）
- `FileItemInfo` 增加 `downloadCount` 字段（文件列表返回）
- 分享：`PATCH /api/shares/{id}` 现有 `status` 字段支持 `DISABLED` / `ACTIVE`（停用/启用）

## 7. 范围与边界

- **不做**：下载频次/并发限制（个人场景过度设计）；按目录「禁止下载」开关；分享下载限 IP/设备。
- aria2 移除后，若未来需要「URL 下载到服务器」能力，另行设计（不在本 ADR 范围）。
