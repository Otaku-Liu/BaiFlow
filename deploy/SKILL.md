---
name: baiflow-deploy
description: BaiFlow 部署：Docker Compose、Nginx、MySQL 8、Ubuntu 24、HTTPS
---

# BaiFlow Deploy

## 约束

- Ubuntu 24，Docker Compose
- MySQL 不暴露公网，密钥走环境变量
- Nginx：静态资源 + `/api/` 反代 + HTTPS + 上传大小限制
- 详见 `docs/01-architecture.md`（部署与安全章节）
