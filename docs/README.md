# BaiFlow

小白流转 — 个人服务器上的下载与文件协同中心。

## 项目定位

BaiFlow 运行在个人服务器上，以本地磁盘为文件根据地，围绕下载任务、文件管理、设备协同构建个人数据中枢。第一版不追求完整网盘替代，目标是让一个人稳定、安全、可控地管理自己的文件流转。

## 模块

| 模块 | 技术 | 说明 |
|---|---|---|
| `baiflow-server` | JDK 17, Spring Boot 3.x, MyBatis Plus, MySQL 8 | REST API 服务端 |
| `baiflow-web` | Vue 3, Vite, Element Plus, Pinia, Axios | Web 管理台 |
| `baiflow-android` | Java, Retrofit, OkHttp, WorkManager | Android 客户端 |
| `deploy` | Docker Compose, Nginx | 部署配置 |

## 快速开始

```bash
# 后端
cd baiflow-server && mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 前端
cd baiflow-web && npm install && npm run dev

# 部署
cd deploy && docker compose up -d
```

## 文档

| 文档 | 内容 |
|---|---|
| [01-architecture.md](01-architecture.md) | 技术架构、需求范围、部署安全 |
| [02-database.md](02-database.md) | 数据库表结构与索引 |
| [03-api.md](03-api.md) | API 约定与接口清单 |
| [04-frontend.md](04-frontend.md) | Web 前端设计与 Apple 风格 Design Token |
| [05-android.md](05-android.md) | Android 客户端设计 |
| [06-coding-standards.md](06-coding-standards.md) | 编码规范（后端/前端/Android） |
| [07-quick-notes.md](07-quick-notes.md) | ADR：随手记（便签/笔记）方案与三阶段实施计划 |
| [08-ios-design-system.md](08-ios-design-system.md) | ADR：Android iOS 风格设计系统（集中式 styles） |
| [09-auth-sessions.md](09-auth-sessions.md) | 登录会话与设备管理（认证模型 2） |
| [10-web-connection-timeout.md](10-web-connection-timeout.md) | Web 连接超时检测与恢复 |
| [11-android-network-error.md](11-android-network-error.md) | Android 网络错误处理 |
| [12-android-offline-mode.md](12-android-offline-mode.md) | Android 离线模式与随手记同步 |
| [13-download-records.md](13-download-records.md) | ADR：文件下载记录与下载安全 |
| [14-brand-assets.md](14-brand-assets.md) | 品牌资源与图标规范 |
| [glossary.md](glossary.md) | 术语表 |
