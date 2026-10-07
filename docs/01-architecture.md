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
     (认证/文件/笔记/下载记录/审计)
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
- **baiflow-android**：移动端文件查看、上传、下载、随手记（仅在线模式；**服务器地址在 App 内运行时设置**，见 `docs/05-android.md`「登录态」）。
- **deploy**：Docker Compose、Nginx、环境变量。

### SSE 事件（`com.baiflow.event`）
- `GET /api/events`（text/event-stream）长连接推送，需登录（EventSource 用 `?token=` 查询参数鉴权）
- `SseService` 维护"用户 → 连接"注册表，定时心跳保活并清理失效连接
- SSE 事件：`NOTE_UPDATED`（笔记跨端同步刷新）

## 数据库连接与往返成本

**开发环境连接远端 MySQL**（便于本地直连服务器上的真实数据），因此**每次数据库往返要 40–75ms**（实测），且公网偶发丢包会造成 200–400ms 的重传停顿。

这决定了后端性能的基本盘：**DB 耗时 ≈ 查询次数 × 单次往返**，应用侧逻辑（映射、过滤、组装）耗时可忽略。所以本项目的性能优化**只盯两件事**：把每个接口的**往返次数**降下来、把响应体里**用不到的字段**去掉。索引与执行计划不是重点（服务端执行本身接近 0ms）。

具体纪律见 `docs/06-coding-standards.md`「数据库往返」。

日志相关的约定（HTTP 请求日志的形状与脱敏、SQL 计时、链路 id）统一放在「日志与可观测性」一章。

### `last_opened_at` 异步落库

「进入目录时记录上次打开时间」原本是 **GET 里的 UPDATE**：每次进目录多一次往返，还在读路径上拿行锁。现改为**异步批量**：先写内存缓冲（一个 id 集合，同一目录反复进只留一份；落库时间统一取刷库时刻），由 `schedule` 包每 5 秒用**一条** `UPDATE bf_file_item SET last_opened_at = ? WHERE id IN (...)` 落库（同一批统一取刷库时刻 —— 这些目录的打开都发生在最近一个周期内，精度够用，换来一条语句写完整批），应用关闭前补刷一次。

**代价**：长摁弹窗里的「上次打开时间」最多滞后一个刷库周期（`LastOpenedFlushScheduler.FLUSH_INTERVAL_MS` = 5 秒，常量），进程被强杀时最多丢一个周期的记录 —— 该字段是参考信息，不参与任何判定。

## 日志与可观测性

框架自己打的每一条都**带箭头**：出向 `==>`、回向 `<==`（与 MyBatis 的 `==>  Preparing:` / `<==      Total:` 同一套词汇），不再混用 `→` / `←`。不引入额外的观测栈（无 APM / Micrometer），排查所需的信息全部由日志给出。

### HTTP 请求日志（`HttpLoggingFilter`）

每个请求两行，第二行起是缩进详情：

```
==> HTTP 请求 GET http://host/api/files?…
    请求头 : {host=…, authorization=***, user-agent=…}
    查询参数: storageRootId=…&page=1&size=50
    请求体 : (无)
<== HTTP 响应 200 (381ms)
    响应头: (无)
    响应体: {"code":0,…（截断 1024 字符）
```

- **只对 `Authorization` / `Cookie` / `Set-Cookie` 请求头打码**，其余（查询串、请求体、响应体）**原样记录**：这是为了排查方便而**有意为之** —— 代价是**日志里会含会话 token 与密码**（登录响应体、`?token=` 的 URL），所以**日志文件不得外发**
- **这些路径不走本过滤器**（`shouldNotFilter`：实现见 `HttpLoggingFilter`）：`/api/files/**/download`、`/api/files/**/preview`、`/api/events`、`/avatars/**`。过滤器用 `ContentCachingResponseWrapper` 缓存整个响应体，下载大文件等于把整份内容放进堆内存；SSE 是长连接，更不该被包住
- 响应体只留前 1024 字符（超出标 `…(截断)`）

### 失败留痕

`GlobalExceptionHandler` 对三类失败都打 warn —— **这是「为什么这个请求被拒」在服务端的唯一线索**：

- **业务异常**：`code + 方法 + URI + message`（如「密码错误（剩余尝试次数：N）」「账号已被锁定」）
- **参数校验失败**：字段名 + 约束（**不含字段值** —— 报的是哪个字段不合法，不是它是什么）
- **权限拒绝**：方法 + URI
- **唯一键冲突**（并发下同时创建同一个东西）：方法 + URI + 提示「请刷新后重试」，避免偶发竞态变成 500

与上面的请求日志互补：**请求日志给入参，这里给判定结果**，两者用 traceId 对上。

### SQL 计时日志

用于随时验证「数据库连接与往返成本」那条公式：**每条 SQL 执行完，紧跟 MyBatis 自己那几行 SQL 内容
（`Preparing` / `Parameters` / `Total`）之后另起一行**输出

```
... c.b.f.m.BfFileItemMapper.selectById - ==>  Preparing: SELECT … WHERE id=?
... c.b.f.m.BfFileItemMapper.selectById - ==> Parameters: 41339036…(String)
... c.b.f.m.BfFileItemMapper.selectById - <==      Total: 1
... c.b.f.m.BfFileItemMapper.selectById - ==> 执行时间：42ms
```

**logger 名取 statement id**，与 MyBatis 打 SQL 内容用的是**同一个 logger**：左边那一列与相邻三行完全对齐，方法名就在行首，消息里只放 `==> 执行时间：xxms`。这样一份 `logback-spring.xml` 同时管住内容与计时（各 mapper 包分别开了 DEBUG）；若改用拦截器自己类的 logger，既对不齐、又会出现「有 SQL 内容、没执行时间」——两处开关对不上。

**不做请求级汇总**（刻意）：要数一次请求发了几条、各花多久，直接数这段时间里的 `执行时间` 行即可
（`grep -c "执行时间"`），汇总行反而多一层需要解释的口径。

实现要点：

- 一个 MyBatis `Interceptor`（`@Intercepts` 挂 `Executor.query` / `Executor.update`），
  **注册为独立 `@Bean`** —— 不能塞进 `MybatisPlusConfig` 里的 `MybatisPlusInterceptor`，
  那是 MyBatis-Plus 自己的内部链，看不到 XML 里的原生 SQL
- 挂在 `Executor` 上才能看到所有语句（含 XML 原生 SQL 与批量）

### 链路 id（traceId）

**入站带 `X-Trace-Id` 就沿用**（便于多端/多服务对齐同一次操作），没带就生成一个 → 写入 MDC（日志 pattern 带上 `%X{traceId}`）→ 由 `ApiResponse` 统一回填进响应体，**成功与失败都有值**（此前只有异常链填，成功响应恒为 `null`，与 `docs/03-api.md` 的信封约定不符）。

**只有这一个 id 来源**：`TraceIdFilter` 写 MDC，`ApiResponse` 的所有工厂从 MDC 回填 —— 日志、`X-Trace-Id` 响应头、响应体三处必定一致（此前异常链另有一个从请求头取、兜底生成 32 位随机串的口径，已删）。入站长于 64 字符的 id 视为无效并重新生成。

有了它，一次请求的 HTTP 日志、每条 SQL 计时、异常日志可以用同一个 id 串起来。

### 环境区分

- **SQL 语句日志**（MyBatis 的 `Preparing/Parameters/Total` 与每条的耗时行）走 DEBUG，且**只在 dev profile 默认打开**（`logback-spring.xml` 用 `<springProfile name="dev">` 包住那些 logger）→ 生产默认不打印 SQL
- **`HttpLoggingFilter` 的请求/响应详情是 INFO**：它在生产同样输出 —— 这也是"日志含密码/token、不得外发"的来源（见上）

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
- **隐私空间**：每个用户主目录下自动创建「隐私空间」子目录（`PRIVATE`，初始无密码）。首访设置密码（`40107`），之后输入密码换取 30 分钟访问令牌（`X-Privacy-Access-Token`），内部操作不再重复验证；密码只存 hash。隐私入口仅此一个，旧隐私文件夹保留兼容

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
- **未实现**：原计划的「统一上传/下载任务展示」「Web 内通知中心」没有落地 —— `bf_transfer_task` / `bf_notification` 两张表已建但无写入方、两端客户端也未接入，对应的 controller / service 已删（见 `docs/02-database.md`、`docs/03-api.md`）
- 传输进度目前由 Android 端前台通知承担（见 `docs/05-android.md`）；上传/下载的**记录**由 `bf_upload_record` / `bf_download_record` 两张表承担

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
- 镜像从 GHCR 拉取，服务器上不编译源码；命名空间与版本都在 `deploy/.env`（`BAIFLOW_IMAGE_NAMESPACE` / `BAIFLOW_IMAGE_TAG`，发版时由 `release.yml` 自动写入），仓库里不写死账号名
- 回滚 = 把 `BAIFLOW_IMAGE_TAG` 改成上一版号再 `docker compose pull`
- 连接信息与管理员密码配在 `deploy/.env`（模板 `deploy/.env.example`）；数据目录默认 `/data/baiflow`（`BAIFLOW_DATA_DIR`）bind mount 进容器
- 首次启动自动建表（Flyway `R__V1_init.sql`）与创建存储根目录；**不再预置管理员账号**——第一个管理员由 Web 端 `/setup` 向导创建（需启动日志里的一次性初始化令牌，见「安全基线 · 初始化入口」）
- 启动：`cd deploy && docker compose pull && docker compose up -d`；重启 server/web：`docker compose restart`（MySQL/Redis 为服务器既有容器，独立管理，不随 compose 重启）
- 本地从源码构建验证（不拉镜像）：`docker compose -f docker-compose.yml -f docker-compose.build.yml up -d --build`

### 镜像构建与发布（GitHub Actions）
- `baiflow-server/Dockerfile`：Maven 多阶段 → Temurin JRE；`baiflow-web/Dockerfile`：Node 构建 → Nginx（`baiflow-web/nginx.conf` 容器版配置）
- `.github/workflows/ci.yml`：push main 与 PR 触发，三端并行校验——后端 `mvn package`、前端 `npm ci && npm run build`、Android `testDebugUnitTest + assembleDebug`（debug APK 作为构建产物上传）；不依赖任何 Secret
- `.github/workflows/release.yml`：推 `v*` tag 触发，构建 server/web 镜像推 GHCR（打 `v1.2.3` / `sha-<短哈希>` / `latest` 三个 tag），随后 SSH 登服务器把版本号写进 `.env`、`docker compose pull && up -d`，最后探 `/api/health` 确认就绪（失败则输出容器状态与日志并让工作流失败）
- 镜像在 GHCR 上设为公开，服务器拉取无需登录；首次推送后需在 GitHub 包设置里手动改为 Public
- 部署用的服务器地址、SSH 用户与私钥、仓库路径存于 GitHub 仓库 Secret，不写进仓库

### Nginx 职责
- 托管静态文件、`/api/` 反代（127.0.0.1:8080）、SSE 支持、Range/流式透传、上传大小限制、头像静态服务

## 安全基线

### 网络隔离
- MySQL 不暴露公网
- Spring Boot 管理端点不暴露公网
- 防火墙只开放必要端口
- **开发环境直连远端 MySQL 是例外**（见「数据库连接与往返成本」）：该端口**必须只对白名单 IP 开放**，不应对整个互联网可达

### 认证与鉴权
- 受保护 API 必须携带会话 token：`Authorization: Bearer <token>` 或 `?token=`（后者供 `<img>/<video>`、SSE 等浏览器直接请求）
- 服务端逐请求校验 `bf_auth_session`（记录存在/未过期），吊销即删除记录；ANDROID / WEB 会话均滑动续期
- **未认证/会话过期返回 401**（客户端清会话回登录）；已登录但无权限返回 403（保留登录态，仅提示）
- 强制 ADMIN/USER/GUEST 角色行为（role 取用户表当前值）
- 密码、分享 token、提取码、隐私密码、会话 token 只存 hash

### 初始化入口
- 首次部署无预置账号，第一个管理员经 `POST /api/setup/init` 创建；入口本身公开，因此**必须携带启动时生成的一次性令牌**（32 字节随机、常量时间比对、15 分钟内错 10 次锁定）
- 令牌打印到启动日志并落盘 `setup-token.txt`（`BAIFLOW_SETUP_TOKEN_PATH`，仅属主可读）；初始化成功后文件删除、令牌作废
- 入口开关只看 `bf_system_setting.initialized_at` **单向标记**，不看是否存在管理员——删号/改名不会重新开放入口；数据库不可用时保守判定为「已初始化」（fail-closed）
- 初始化成功直接签发登录会话（避免刚设置的密码立刻输错）

### 文件安全
- 文件操作限制在配置的 Storage Root 内，路径需归一化校验
- 后端进程不用 root 运行
- 不向客户端暴露服务器绝对路径
- 启动时自动从 `baiflow.storage.default-root-path` 创建默认存储根目录（环境变量 `BAIFLOW_STORAGE_ROOT`）
- 笔记媒体（随手记图片/录音/画画）落盘 `baiflow.notes.media-path`（环境变量 `BAIFLOW_NOTE_MEDIA_PATH`），独立于文件中心，不参与 `/api/files` 列表
- NAS_MOUNT 类型的存储根离线时**拒绝写入**（读取放行）
- **NAS 健康检查定时任务默认关闭**：`baiflow.nas.health-check-enabled`（环境变量 `BAIFLOW_NAS_HEALTH_CHECK_ENABLED`）——暂无 NAS 硬件时不空转，接上 NAS 后设为 true 恢复（无需改代码）。关闭期间存储根 status 不自动刷新，需要时调 `POST /api/storage-roots/{id}/check` 手工检测

### 分享安全
- 分享 URL 使用不可预测 token，数据库只存 hash
- 分享过期/超次/撤销后不可访问
- 公开分享接口不返回服务器真实路径

### 外网访问
- HTTPS + 强密码 + 登录失败限制（Redis 滑动窗口：15 分钟内连续失败 5 次锁定 15 分钟，多实例共享；锁定时将用户状态持久化为 LOCKED，锁键到期后由定时任务自动恢复为 NORMAL）+ 定期备份

### 隐私与机密
- 配置中避免出现真实用户路径/域名/硬编码凭据；每次代码调整涉及配置/路径/凭据时做隐私与机密核查，涉及隐私先确认「保留还是屏蔽 git」
- 真实路径与域名已从仓库清除：`application.yml` 存储默认值改为中性的 `/data/baiflow/...`（生产实际值仍由 `BAIFLOW_*` 环境变量注入）、`application-dev.example.yml` 改为 `/path/to/...` 占位符
- 含真实路径且与 web 容器抢 8088 端口的本地测试配置 `deploy/nginx.conf` 已删除，本地测试统一走 `baiflow-web/nginx.conf`（容器版）
- 真实运行配置（`application-dev.yml`，含域名与凭据）gitignored，不随仓库分发

### 安全检查项
- 未登录访问文件接口 → 401
- 普通用户访问未授权文件 → 403
- 分享链接过期/超次后不可访问
- 提取码错误不能访问分享内容
- 隐私文件夹密码错误不能访问
- 文件操作不会越出 Storage Root
