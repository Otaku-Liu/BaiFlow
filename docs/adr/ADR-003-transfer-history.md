# ADR-003：文件传输历史（上传/下载记录）查询

**状态**：已接受 · **日期**：2026-08-31

## 背景（Context）

用户需要在 Android「我的」与 Web 端查看文件的上传/下载历史，admin 可看全部用户的记录，支持时间范围、文件名、来源过滤。现状：下载记录已有（`bf_download_record`，含 CLIENT/SHARE 来源），但只有**单文件**查询面；上传**无任何记录**（上传只建 FileItem）。

## 决策（Decision）

1. **两张表，下载不动**：新增 `bf_upload_record`（镜像下载表：`file_id`/`file_name` 快照/`uploader_user_id`/`source`/`ip`/`ua`/`created_at` + 索引）。现有 `bf_download_record` 及其下载计数/分享追踪逻辑**零改动**。不建统一传输表（迁移/并存成本高）。
2. **来源语义**：下载 `source` = CLIENT（登录直接下载）/ SHARE（分享链接下载）——已有字段；上传 `source` = WEB / ANDROID（客户端设备类型，取 `X-Device-Type`，Android 全局带，Web 缺省 WEB）。
3. **记录写入**：上传成功时在 `BfFileItemServiceImpl.uploadFile` 末尾异步写一条（`@Async`，不阻塞上传响应）。**历史上传不回填**（无法得知当时的设备/IP），只记新动作。
4. **查询 API（两 Tab 各自独立）**：
   - `GET /api/upload-records`、`GET /api/download-records`，均分页 + 过滤：时间范围（`start`/`end` 日期，含端点日）、文件名模糊（`fileName`）、来源精确（`source`）。
   - **角色限定**：非 admin 只看自己的（上传 `uploader_user_id = me`；下载 `downloader_user_id = me`，分享匿名下载 `downloader_user_id=null` 对普通用户不可见）；admin 传 `userId` 可筛任意用户，不传则全部。
5. **UI 形态**：Web 与 Android 均用**两个 Tab**（上传记录 / 下载记录），各查各的、过滤口径各自独立，不做合并流。

## 后果（Consequences）

- ✅ 下载计数/分享追踪逻辑不动，改动面最小。
- ✅ 上传/下载历史统一可查，admin 具备全局审计视图。
- ⚠️ 历史上传无记录（不回填），上传历史从功能上线之日起累积。
- ⚠️ 下载 SHARE 匿名记录不归属任何普通用户（仅 admin 可见），是既有数据模型的既有取舍。
- ⚠️ 新增一张表随 `R__V1_init.sql` 可重复迁移自动建（幂等 `IF NOT EXISTS`）。

## 相关

- `docs/02-database.md`（bf_upload_record）、`docs/03-api.md`（接口）、`docs/04-frontend.md`（Web 传输记录页）、`docs/05-android.md`（Android 传输记录页）
- `BfFileItemServiceImpl.uploadFile`、`BfUploadRecordService`、`BfDownloadRecordService.pageHistory`、`BfUploadRecordController`、`BfDownloadRecordController`
