---
name: baiflow-deploy
description: BaiFlow 部署：Docker Compose、Nginx、MySQL 8、Ubuntu 24、HTTPS
---

# BaiFlow Deploy

## 约束

- Ubuntu 24，Docker Compose（server + web 容器化；MySQL/Redis 复用服务器已有容器，host 网络直连）
- 配置走 `deploy/.env` 环境变量，不写死路径/凭据
- 首次启动：`cd deploy && cp .env.example .env && docker compose up -d --build`
- 重启 server/web：`cd deploy && docker compose restart`（MySQL/Redis 独立管理，不随 compose 重启）
- MySQL 不暴露公网，密钥走环境变量
- Nginx：静态资源 + `/api/` 反代 + HTTPS + 上传大小限制
- 详见 `docs/01-architecture.md`（部署与安全章节）
