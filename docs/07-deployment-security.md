# BaiFlow 部署与安全设计

## 推荐环境
- Ubuntu 24
- Docker Compose
- JDK 17 镜像
- MySQL 8
- Nginx
- aria2

CentOS 7 不建议作为主环境。它系统库较旧，长期维护成本高，只作为兼容目标考虑。

## 目录建议
```text
/opt/baiflow/
  app/
  data/
    files/
    downloads/
  mysql/
  nginx/
  logs/
```

## Docker Compose 服务
- `baiflow-server`：Spring Boot 后端
- `mysql`：MySQL 8
- `aria2`：下载引擎
- `nginx`：静态资源和反向代理
- `redis`：后续可选

## Nginx 职责
- 托管 Vue 构建后的静态文件。
- `/api/` 反向代理到后端。
- `/api/events` 支持 SSE。
- `/s/` 或公开分享入口反向代理到后端。
- `/download/` 作为受控下载入口。
- 配置 HTTPS。
- 限制上传大小。
- 配置下载、上传和 SSE 的超时。

## 安全基线
- MySQL 不暴露公网端口。
- aria2 RPC 不暴露公网端口。
- Spring Boot 管理端点不暴露公网。
- 所有敏感配置使用环境变量。
- 初始管理员账号为 `admin` / `admin`，首次启动后必须修改密码。
- Storage Root 必须是明确配置的目录。
- 后端必须阻止路径穿越。
- Nginx 配置请求大小限制和超时。
- 登录、提取码、隐私密码验证必须有失败次数限制或冷却策略。
- 分享链接必须支持过期、撤销和次数限制。

## 用户与权限安全
- 管理员只能从受保护接口创建和禁用用户。
- 普通用户不能访问未授权 Storage Root 或目录。
- 访客只能通过有效分享链接访问。
- 权限拒绝必须记录审计日志。

## 分享安全
- 分享 URL 使用不可预测 token。
- 数据库只保存分享 token hash。
- 可选提取码只保存 hash。
- 公开分享接口不返回服务器真实路径。
- 分享下载必须经过后端鉴权和计数，不允许 Nginx 直接暴露磁盘目录。

## 隐私文件夹安全
- 隐私文件夹密码只保存 hash。
- 已登录用户访问隐私文件夹也要额外验证。
- 隐私访问会话必须有过期时间。
- 修改隐私密码后清理已有访问会话。

## 文件权限
- 后端进程只拥有 Storage Root 所需权限。
- 不使用 root 用户运行应用容器。
- NAS 挂载目录建议使用单独系统用户和只需权限。

## 外网访问
第一版优先内网使用。外网访问必须满足：
- HTTPS
- 强密码
- 登录失败限制
- 防火墙只开放必要端口
- 定期备份 MySQL 和配置文件

## 备份
- 定期备份 MySQL。
- 定期备份 `deploy/.env`。
- 文件本体由磁盘或 NAS 备份策略负责。
