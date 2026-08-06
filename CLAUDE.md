# BaiFlow

BaiFlow（小白流转）是一个个人服务器上的下载与文件协同中心，包含 Spring Boot 后端、Vue 3 Web 管理台和 Android 客户端。

## 技术栈

| 层面 | 技术 |
|---|---|
| 后端 | JDK 17, Spring Boot 3.x, MyBatis Plus, Lombok, MySQL 8 |
| Web | Vue 3, Vite, Vue Router, Pinia, Axios |
| Android | Java, Retrofit, OkHttp, WorkManager, Foreground Service |
| 部署 | Ubuntu 24, Docker Compose, Nginx, aria2 RPC |

## 项目结构

```
baiflow-server/     Spring Boot API 服务端
baiflow-web/        Vue 3 Web 管理台
baiflow-android/    Android 客户端
deploy/             Docker Compose 部署配置
docs/               项目文档（需求、架构、API、数据库等）
```

## 开发规则

### 架构约束
- Controller 只做 HTTP 映射和请求/响应转换，Service 持有业务逻辑，Mapper 只做 SQL
- DTO、VO、Entity、Request 类分离，不混用
- 统一 API 返回格式：`{ code, message, data, traceId }`
- API 前缀统一用 `/api`
- 文件本体落磁盘，数据库只存元数据
- 不向 Web/Android 暴露服务器绝对路径，文件 ID 到服务端路径的解析只在后端发生

### 安全规则
- 受保护 API 必须携带 `Authorization: Bearer <token>`
- 强制 ADMIN、USER、GUEST 角色行为
- 用户密码、分享 token、提取码、隐私文件夹密码只存储 hash
- 文件操作必须限制在配置的 Storage Root 内，路径需归一化校验
- MySQL 和 aria2 RPC 不暴露在公网路由上

### 阶段纪律
- 所有 10 个阶段（Phase 0–9）均已完成
- 修改代码时注意不要破坏已有功能
- 修改需求、API、数据库、安全规则或部署行为后同步更新 `docs/` 对应文档

### 数据库迁移
- schema 统一维护在**单个可重复迁移** `baiflow-server/src/main/resources/db/R__V1_init.sql`（全部表结构 + 初始数据）：
  - **可重复迁移**：每次启动比对校验和，文件有改动即自动重新执行（全表 `IF NOT EXISTS`、管理员 `INSERT WHERE NOT EXISTS`，幂等）
  - **新表 DDL 一律追加本文件末尾，不新建迁移脚本**；版本号以文件名 `R__V{n}_` 标识（排序与管理用）
- 脚本内只保留表/字段的 COMMENT 描述；长期约定与表结构说明写在 `docs/02-database.md` 等 md 文档

## 功能决策

- 支持三种角色：`ADMIN`、`USER`、`GUEST`
- 访客通过分享 URL 访问，不参与管理台登录
- 分享链接支持过期时间、访问次数、下载次数、提取码
- 隐私文件夹需要额外密码验证，密码只存 hash
- 权限模型提前设计，功能分阶段落地

## 模块技能

每个模块目录下都有 `SKILL.md`，处理对应模块前先读取：
- `baiflow-server/SKILL.md` — 后端架构、安全、持久化、注释规范
- `baiflow-web/SKILL.md` — 前端 UI 原则、API 调用、状态管理
- `baiflow-android/SKILL.md` — Android 客户端网络、UI、传输规则
- `deploy/SKILL.md` — 部署目标、服务编排、安全配置

## 参考文档

- `docs/01-architecture.md` — 技术架构、需求范围、部署与安全
- `docs/02-database.md` — 数据库表结构与索引
- `docs/03-api.md` — API 约定与接口清单
- `docs/04-frontend.md` — Web 前端设计与 Apple 风格 Design Token
- `docs/05-android.md` — Android 客户端设计
- `docs/06-coding-standards.md` — 编码规范（后端/前端/Android）
- `docs/07-quick-notes.md` — ADR：随手记（便签/笔记）方案与三阶段实施计划
- `docs/08-ios-design-system.md` — ADR：Android iOS 风格设计系统（集中式 styles）
- `docs/glossary.md` — 术语表

## 测试与验收

- 后端：单元测试 + 接口测试
- Web：手动验收，关键组件补测试
- Android：真机或模拟器手动验收
- 部署：每次改动后至少本地 Docker Compose 启动验证

### 关键安全检查项
- 未登录访问文件接口返回 401
- 普通用户访问未授权文件返回 403
- 分享链接过期/超次后不可访问
- 提取码错误不能访问分享内容
- 隐私文件夹密码错误不能访问
- 文件操作不会越出 Storage Root
