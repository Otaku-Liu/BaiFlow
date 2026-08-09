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
- **数据访问层**：每个实体有对应 `IService`（实体 Service），领域 Service `extends IService<主实体>` 或注入实体 Service；单表查询在 Service 层用 `lambdaQuery()` / `getOne` / `list` / `count` / `page`，Mapper 保持纯 `BaseMapper`；仅多表 JOIN / 特殊 SQL（审计登录日志、笔记进度 upsert）留在 XML Mapper（见 `docs/06-coding-standards.md`）
- **baiflow-web**：Web 管理台，只通过 REST API 通信。
- **baiflow-android**：移动端文件查看、上传、下载（随手记在线/离线阶段规划见 `docs/07-quick-notes.md`）。
- **deploy**：Docker Compose、Nginx、环境变量。

### SSE 事件（`com.baiflow.event`）
- `GET /api/events`（text/event-stream）长连接推送，需登录（EventSource 用 `?token=` 查询参数鉴权）
- `SseService` 维护"用户 → 连接"注册表，定时心跳保活并清理失效连接
- SSE 事件：`NOTE_UPDATED`（笔记跨端同步刷新；曾规划的传输/下载/通知事件已移除）

## MVP 功能

### 认证与权限
- 用户名密码登录 + **登录会话 token**（长会话，吊销驱动 + 滑动续期：ANDROID 180 天 / WEB 约 2h 不活跃兜底，见 `docs/09-auth-sessions.md`）
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
- 登录、文件列表、上传、下载、任务状态
- 前台通知

### 多语言（i18n）
- Web / Android 界面全量中英双语（语言设置在客户端"我的"页，Android 按应用语言发 `Accept-Language`）
- 服务端错误消息按请求头 `Accept-Language` 返回中/英：以「中文文案即 key」组织词条（`i18n/messages*.properties`），`I18nUtil.translate()` 统一翻译，默认中文（`spring.messages.default-locale=zh_CN`）
- 业务错误码为 5 位数字码（见 `docs/03-api.md` 错误码表），客户端按数字码区分业务分支而非解析文案

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
- `deploy/docker-compose.yml`：`mysql` + `redis`（数据库 / 缓存）
- 应用服务（server / web / nginx）单独运行，部署脚本化规划中

### Nginx 职责
- 托管静态文件、`/api/` 反代、SSE 支持、HTTPS、上传大小限制

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
