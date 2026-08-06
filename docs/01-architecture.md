# 架构、需求与安全

## 技术栈

| 层面 | 技术 |
|---|---|
| 后端 | JDK 17, Spring Boot 3.x, MyBatis Plus, Lombok, MySQL 8 |
| Web | Vue 3, Vite, Vue Router, Pinia, Axios, Element Plus |
| Android | Java, Retrofit, OkHttp, WorkManager, Foreground Service |
| 部署 | Ubuntu 24, Docker Compose, Nginx, aria2 RPC |

## 总体架构

```
Vue 3 Web 管理台          Android Java App
         |                      |
         v                      v
       Nginx (HTTPS, 静态资源, API 反代)
                  |
                  v
     Spring Boot 3 API Server
     (认证/文件/下载/传输/通知/设备)
         |                |
         v                v
      MySQL 8         后台任务 (aria2 RPC)
      (元数据)         (扫描/同步/通知)
         |
         v
   Storage Roots (本地磁盘 / NAS 挂载)
```

## 模块边界

- **baiflow-server**：核心业务、权限、数据库、文件操作、下载任务、随手记笔记、SSE 事件、对外 API。文件路径只在服务端存在。
- **baiflow-web**：Web 管理台，只通过 REST API 通信。
- **baiflow-android**：移动端文件查看、上传、下载（随手记在线/离线阶段规划见 `docs/07-quick-notes.md`）。
- **deploy**：Docker Compose、Nginx、环境变量。

### SSE 事件（`com.baiflow.event`）
- `GET /api/events`（text/event-stream）长连接推送，需登录（EventSource 用 `?token=` 查询参数鉴权）
- `SseService` 维护"用户 → 连接"注册表，定时心跳保活并清理失效连接
- 已实现事件：`NOTE_UPDATED`；`TRANSFER_PROGRESS` / `DOWNLOAD_COMPLETED` / `DOWNLOAD_FAILED` / `NOTIFICATION_CREATED` 为已定义待接入

## MVP 功能

### 认证与权限
- 用户名密码登录 + **登录会话 token**（长会话，吊销驱动 + ANDROID 180 天不活跃兜底 / WEB 固定 2h，见 `docs/09-auth-sessions.md`）
- 三种角色：ADMIN、USER、GUEST
- 访客通过分享 URL 访问，不登录管理台

### 文件中心
- Storage Root 配置、启动时自动初始化默认存储根目录
- 目录浏览、文件上传/下载
- 新建文件夹、重命名、移动、删除
- 用户主目录隔离：每个用户拥有以用户名命名的个人主目录，文件视图自动限定在主目录内
- 管理员可切换查看其他用户的主目录内容
- 隐私文件夹（额外密码验证，密码只存 hash）

### 分享
- 文件/文件夹分享链接，支持过期时间、访问次数、下载次数、提取码
- 不暴露服务器真实路径
- 管理员或创建者可撤销

### 下载中心
- 通过 aria2 RPC 创建 URL/磁力/BT 下载
- 暂停、恢复、删除、状态和进度展示

### 传输与通知
- 统一上传/下载任务展示
- Web 内通知中心

### Android
- 登录、文件列表、上传、下载、任务状态
- 前台通知

## 非目标

- 第一版不做团队协作空间；在线预览仅覆盖图片/视频/音频/PDF/文本/Markdown（随手记笔记为 Web 端编辑，Office 文档暂不支持在线预览）
- 不自研 BT/磁力下载协议
- 不做 WebDAV/SMB/NFS 客户端
- 不做复杂自动化流程

## 部署

```
/opt/baiflow/
  app/           # 应用
  data/files/    # 文件存储
  data/downloads/ # 下载目录
  mysql/         # 数据库
  nginx/         # Web 服务
  logs/          # 日志
```

### Docker Compose 服务
- `baiflow-server`、`mysql`、`aria2`、`nginx`
- Redis 后续可选

### Nginx 职责
- 托管静态文件、`/api/` 反代、SSE 支持、HTTPS、上传大小限制

## 安全基线

### 网络隔离
- MySQL、aria2 RPC 不暴露公网
- Spring Boot 管理端点不暴露公网
- 防火墙只开放必要端口

### 认证与鉴权
- 受保护 API 必须携带会话 token：`Authorization: Bearer <token>` 或 `?token=`（后者供 `<img>/<video>`、SSE 等浏览器直接请求）
- 服务端逐请求校验 `bf_auth_session`（未吊销/未过期），吊销即时生效；ANDROID 会话滑动续期
- **未认证/会话过期返回 401**（客户端清会话回登录）；已登录但无权限返回 403（保留登录态，仅提示）
- 强制 ADMIN/USER/GUEST 角色行为（role 取用户表当前值）
- 密码、分享 token、提取码、隐私密码、会话 token 只存 hash

### 文件安全
- 文件操作限制在配置的 Storage Root 内，路径需归一化校验
- 后端进程不用 root 运行
- 不向客户端暴露服务器绝对路径
- 启动时自动从 `baiflow.storage.default-root-path` 创建默认存储根目录（环境变量 `BAIFLOW_STORAGE_ROOT`）
- 笔记媒体（随手记图片/录音/画画）落盘 `baiflow.notes.media-path`（环境变量 `BAIFLOW_NOTE_MEDIA_PATH`），独立于文件中心，不参与 `/api/files` 列表

### 分享安全
- 分享 URL 使用不可预测 token，数据库只存 hash
- 分享过期/超次/撤销后不可访问
- 公开分享接口不返回服务器真实路径

### 外网访问
- HTTPS + 强密码 + 登录失败限制 + 定期备份

### 安全检查项
- 未登录访问文件接口 → 401
- 普通用户访问未授权文件 → 403
- 分享链接过期/超次后不可访问
- 提取码错误不能访问分享内容
- 隐私文件夹密码错误不能访问
- 文件操作不会越出 Storage Root
