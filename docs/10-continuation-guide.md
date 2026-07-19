# BaiFlow 新对话续接指南

## 使用方式
在新对话中直接选择项目文件夹：

```text
E:\Workspace\AI\BaiFlow
```

然后让 Codex 先读取：
- `AGENTS.md`
- `SKILL.md`
- `docs/08-development-roadmap.md`
- 当前要做模块下的 `SKILL.md`

## 项目当前约束
- 项目名：BaiFlow，小白流转。
- 后端：JDK 17 + Spring Boot 3.x + MyBatis Plus + MySQL 8。
- 前端：Vue 3 + Vite。
- Android：Java。
- 部署：Ubuntu 24 + Docker Compose + Nginx + aria2。
- 不要一次性实现过多功能。
- 每个阶段必须可运行、可验收、可回滚。

## 当前功能方向
- 多用户：ADMIN、USER、GUEST。
- 文件/文件夹分享 URL。
- 隐私文件夹密码访问。
- 本地服务器文件根据地优先。
- NAS 通过 Linux 挂载目录接入。

## 推荐下一步
如果还没有实现代码，下一步是 Phase 1：
- 创建 `baiflow-server` Spring Boot 骨架。
- 创建 `baiflow-web` Vue 3 骨架。
- 保留 `baiflow-android` 为空模块或 Android 项目占位。
- 创建 `deploy` 下 Docker Compose 和 Nginx 草案。
- 只实现 `/api/health`、Vue 首页、MySQL 连接配置，不进入文件、分享、下载业务。
