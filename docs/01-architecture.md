# 架构、需求与安全

## 技术栈

| 层面 | 技术 |
|---|---|
| 后端 | JDK 17, Spring Boot 3.x, MyBatis Plus, Lombok, MySQL 8, Redis 7 |
| Web | Vue 3, Vite, Vue Router, Pinia, Axios, Element Plus |
| Android | Java, Retrofit, OkHttp, WorkManager, Foreground Service |
| 部署 | Ubuntu 24, Docker Compose, Nginx |

## 总体架构

```
Vue 3 Web 管理台          Android Java App
         |                      |
         v                      v
       Nginx (HTTPS, 静态资源, API 反代)
                  |
                  v
     Spring Boot 3 API Server
     (认证/文件/传输/通知/设备)
         |          |            |
         v          v            v
      MySQL 8    Redis 7      后台任务
      (元数据)  (计数/登录锁)  (扫描/同步/通知)
         |
         v
   Storage Roots (本地磁盘 / NAS 挂载)
```

## 模块边界

- **baiflow-server**：核心业务、权限、数据库、文件操作、下载任务、随手记笔记、SSE 事件、对外 API。文件路径只在服务端存在。
- **数据访问层**：实体 Service（IService）承载单表查询（`lambdaQuery()` 等），Mapper 保持纯 `BaseMapper`；仅多表 JOIN / 特殊 SQL 留在 XML Mapper（见 `docs/06-coding-standards.md`）
- **baiflow-web**：Web 管理台，只通过 REST API 通信。
- **baiflow-android**：移动端文件查看、上传、下载、随手记（仅在线模式；服务器地址按构建类型固定，不手动配置）。
- **deploy**：Docker Compose、Nginx、环境变量。

### SSE 事件（`com.baiflow.event`）
- `GET /api/events`（text/event-stream）长连接推送，需登录（EventSource 用 `?token=` 查询参数鉴权）
- `SseService` 维护"用户 → 连接"注册表，定时心跳保活并清理失效连接
- SSE 事件：`NOTE_UPDATED`（笔记跨端同步刷新；曾规划的传输/下载/通知事件已移除）

## MVP 功能

### 认证与权限
- 用户名密码登录 + **登录会话 token**（长会话，吊销驱动 + 滑动续期：ANDROID 180 天 / WEB 约 2h 不活跃兜底）
- 三种角色：ADMIN、USER、GUEST
- 访客通过分享 URL 访问，不登录管理台

### 文件中心
- Storage Root 配置、启动时自动初始化默认存储根目录
- 目录浏览、文件上传/下载
- 新建文件夹、重命名、移动、删除
- 用户主目录隔离：每个用户拥有以用户名命名的个人主目录，文件视图自动限定在主目录内；主目录不可重命名、不可设隐私
- 管理员可切换查看其他用户的主目录内容；管理员访问隐私空间免密码
- **隐私空间**：每个用户主目录下自动创建「隐私空间」子目录（`PRIVATE`，初始无密码）。首访设置密码（`40107`），之后输入密码换取 30 分钟访问令牌（`X-Privacy-Access-Token`），内部操作不再重复验证；密码只存 hash；暂不提供重置（后续接短信）。任意文件夹不再单独设隐私，旧隐私文件夹保留兼容

### 分享
- 文件/文件夹分享链接，支持过期时间、访问次数、下载次数、提取码
- 不暴露服务器真实路径
- 管理员或创建者可撤销；创建者可「停用 / 启用」链接（DISABLED 状态，可恢复）
- 分享提取码连续错误 5 次锁定 15 分钟（Redis，多实例共享）

### 文件下载记录
- 每次下载（文件中心直接下载 / 分享下载）写入 `bf_download_record`
- 文件中心列表显示每文件下载次数（CLIENT + SHARE 均计入），点击查看详情（来源 / 下载人 / IP / 时间）
- 下载通道仅两条：登录用户（owner/admin）或有效分享链接，无匿名直下端点

### 传输与通知
- 统一上传/下载任务展示
- Web 内通知中心

### Android
- 登录、文件列表、上传、下载
- 前台通知

### 多语言（i18n）
- Web / Android 界面全量中英双语（语言设置在客户端"我的"页，Android 按应用语言发 `Accept-Language`）
- 服务端错误消息按请求头 `Accept-Language` 返回中/英：以「中文文案即 key」组织词条（`i18n/messages*.properties`），`I18nUtil.translate()` 统一翻译，默认中文（`spring.messages.default-locale=zh_CN`）
- 业务错误码为 5 位数字码（见 `docs/03-api.md` 错误码表），客户端按数字码区分业务分支而非解析文案

## 非目标

- 在线预览仅覆盖图片/视频/音频/PDF/文本/Markdown（随手记笔记为块式编辑器直接编辑，Office 文档暂不支持在线预览）
- 随手记不做标签/置顶/分类、回收站、笔记间链接、实时协同编辑（SSE 仅做刷新通知）与笔记分享
- 登录会话不做多设备登录冲突提示/挤线（各设备独立会话，自行管理）

## 部署

```
/data/baiflow/
  files/         # 文件存储根
  avatars/       # 头像（Nginx 直接 serve）
  notes-media/   # 笔记媒体
```

### Docker Compose（server + web 容器化）
- `deploy/docker-compose.yml`：`server`（Spring Boot，宿主机 8080）+ `web`（Nginx，宿主机 8088），host 网络直连服务器上**已有的 MySQL/Redis 容器**（不重建、不动数据）
- 连接信息与管理员密码配在 `deploy/.env`（模板 `deploy/.env.example`）；数据目录默认 `/data/baiflow`（`BAIFLOW_DATA_DIR`）bind mount 进容器
- 首次启动自动建表（Flyway `R__V1_init.sql`）、创建初始管理员、创建存储根目录
- 首次启动：`cd deploy && docker compose up -d --build`；重启 server/web：`docker compose restart`（MySQL/Redis 为服务器既有容器，独立管理，不随 compose 重启）

### 镜像构建
- `baiflow-server/Dockerfile`：Maven 多阶段 → Temurin JRE
- `baiflow-web/Dockerfile`：Node 构建 → Nginx（`baiflow-web/nginx.conf` 容器版配置）

### Nginx 职责
- 托管静态文件、`/api/` 反代（127.0.0.1:8080）、SSE 支持、Range/流式透传、上传大小限制、头像静态服务

## 安全基线

### 网络隔离
- MySQL 不暴露公网
- Spring Boot 管理端点不暴露公网
- 防火墙只开放必要端口

### 认证与鉴权
- 受保护 API 必须携带会话 token：`Authorization: Bearer <token>` 或 `?token=`（后者供 `<img>/<video>`、SSE 等浏览器直接请求）
- 服务端逐请求校验 `bf_auth_session`（记录存在/未过期），吊销即删除记录；ANDROID / WEB 会话均滑动续期
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
- HTTPS + 强密码 + 登录失败限制（Redis 滑动窗口：15 分钟内连续失败 5 次锁定 15 分钟，多实例共享；锁定时将用户状态持久化为 LOCKED，锁键到期后由定时任务自动恢复为 NORMAL）+ 定期备份

### 隐私与机密
- 配置中避免出现真实用户路径/域名/硬编码凭据；每次代码调整涉及配置/路径/凭据时做隐私与机密核查，涉及隐私先确认「保留还是屏蔽 git」
- **已知待办**：`/home/lxb/...` 真实路径暂存于 `application.yml`（3 处默认）、`application-dev.example.yml`（2 处默认）、`deploy/nginx.conf`（root/alias/注释），部署脚本化时统一改为占位符 + 环境变量注入
- 真实运行配置（`application-dev.yml`，含域名与凭据）gitignored，不随仓库分发

### 安全检查项
- 未登录访问文件接口 → 401
- 普通用户访问未授权文件 → 403
- 分享链接过期/超次后不可访问
- 提取码错误不能访问分享内容
- 隐私文件夹密码错误不能访问
- 文件操作不会越出 Storage Root
