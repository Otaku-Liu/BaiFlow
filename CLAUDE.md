# BaiFlow

BaiFlow（小白流转）——个人服务器上的下载与文件协同中心。小步推进，每个阶段都要可运行、可验收、可回滚。

## 技术栈与结构

| 层面 | 技术 | 目录 |
|---|---|---|
| 后端 | JDK 17, Spring Boot 3.x, MyBatis Plus, Lombok, MySQL 8 | `baiflow-server/` |
| Web | Vue 3, Vite, Vue Router, Pinia, Axios | `baiflow-web/` |
| Android | Java, Retrofit, OkHttp, WorkManager, Foreground Service | `baiflow-android/` |
| 部署 | Ubuntu 24, Docker Compose, Nginx, GitHub Actions | `deploy/`、`.github/` |
| 文档 | — | `docs/`（索引见 `docs/README.md`） |

## 开发规则

### 架构
- Controller 只做 HTTP 映射与请求/响应转换，Service 持有业务逻辑，Mapper 只做 SQL
- DTO、VO、Entity、Request 分离，不混用
- 统一返回 `{ code, message, data, traceId }`，API 前缀 `/api`
- 文件本体落磁盘，数据库只存元数据
- 不向 Web/Android 暴露服务器绝对路径；文件 ID → 服务端路径的解析只在后端发生

### 安全
- 受保护 API 必须携带 `Authorization: Bearer <token>`，强制 ADMIN / USER / GUEST 角色行为
- 密码、分享 token、提取码、隐私文件夹密码只存 hash
- 文件操作限制在 Storage Root 内，路径需归一化校验；MySQL 不暴露公网
- 工作流里的敏感值一律走 GitHub Secrets，yml 内不出现真实地址/密钥/路径（详见 `deploy/SKILL.md`）
- **每次代码调整做隐私与机密核查**：真实路径、真实域名、硬编码密码/secret/token/API Key。涉及隐私先向用户确认「保留还是屏蔽 git」，不擅自提交

### 变更纪律
- 不破坏已有功能；不提前实现后续阶段的功能
- 改需求 / API / 数据库 / 安全规则 / 部署行为后，同步更新 `docs/` 对应主文档

### 数据库迁移
- schema 只在**单个可重复迁移** `baiflow-server/src/main/resources/db/R__V1_init.sql`（全部表结构）：文件有改动即自动重跑，全表 `IF NOT EXISTS`，幂等
- **新表 DDL 一律追加该文件末尾，不新建迁移脚本**；版本号以文件名 `R__V{n}_` 标识
- 脚本内只留表/字段 COMMENT；长期约定与表结构说明写 `docs/02-database.md`

## 功能决策

- 三种角色 `ADMIN` / `USER` / `GUEST`；访客经分享 URL 访问，不参与管理台登录
- 分享链接支持过期时间、访问次数、下载次数、提取码
- 隐私文件夹需额外密码验证，密码只存 hash
- 权限模型提前设计，功能分阶段落地
- **首次部署不预置管理员**：先在 Web 端 `/setup` 用启动日志里的一次性令牌创建第一个管理员；入口由 `bf_system_setting.initialized_at` 单向标记永久关闭（删号/改名不会重开）
- **Android 服务器地址是运行时设置**（存本机），正式包不预填；仅调试包可用 `local.properties` 的 `BAIFLOW_DEBUG_SERVER_URL` 预填

## 测试与验收

- 后端单元/接口测试；Web、Android 手动验收；部署每次改动后至少本地 Docker Compose 启动验证
- 关键安全检查项：未登录访问文件接口 401 · 越权文件 403 · 分享过期/超次不可访问 · 提取码错误不可访问 · 隐私文件夹密码错误不可访问 · 文件操作不越出 Storage Root
- 初始化入口：无令牌或令牌错误不能创建管理员；初始化完成后恒返回 40302（删号/改名也不会重开）
- Android 换服务器后，旧服务器的 token 不会再被发往新服务器

## 模块技能

改哪个模块，先读它的 `SKILL.md`：`baiflow-server/`（后端）· `baiflow-web/`（前端）· `baiflow-android/`（Android）· `deploy/`（部署）。

## 文档

主文档：`01-architecture`（架构/范围/部署/安全）· `02-database`（表结构）· `03-api`（接口与错误码）· `04-frontend`（Web）· `05-android`（Android）· `06-coding-standards`（编码规范）· `07-ios-design-system`（Android 设计系统）· `08-brand-assets`（品牌资产）· `glossary`（术语表）。索引见 `docs/README.md`。

单个功能/修复不单开文档：功能现状进对应主文档（Web 行为 → `04`、Android 行为 → `05`、表结构 → `02`、接口 → `03`），根因分析与排障过程不入库。
