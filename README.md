# BaiFlow

BaiFlow（小白流转）——个人服务器上的下载与文件协同中心（Spring Boot + Vue 3 + Android）。

## 功能

- **文件中心**：多存储根、隐私文件夹、分享、下载
- **随手记**：所见即所得块编辑器（文本/标题 + 图片/录音/画画），Web/Android 双向 Markdown 互认
- **浏览进度跨端同步**：视频/音频续播、文本/笔记续读，Web/Android 共用一份数据
- 登录会话持久化与设备管理、传输中心、随手记在线同步（Room 本地缓存 + outbox + SSE 实时）、中英双语

## 模块

| 模块 | 说明 |
|---|---|
| `baiflow-server` | Spring Boot API 服务端 |
| `baiflow-web` | Vue 3 Web 管理台 |
| `baiflow-android` | Android 客户端 |
| `deploy` | Docker Compose 部署配置 |

## 快速启动

环境要求：JDK 17+ / Maven 3.8+ / Node 18+ / MySQL 8+ / Redis 7+。

```bash
# 后端（默认端口 8080；先复制 application-dev.example.yml 为 application-dev.yml 填数据库连接）
cd baiflow-server && mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 前端（默认端口 5173，/api 自动代理到后端）
cd baiflow-web && npm install && npm run dev

# 部署 server + web（MySQL/Redis 复用已有容器；先复制 deploy/.env.example 为 .env）
cd deploy && docker compose up -d --build
```

## 文档

索引见 [`docs/README.md`](docs/README.md)。
