# 架构、需求与安全

## 技术栈

| 层面 | 技术 |
|---|---|
| 后端 | JDK 17, Spring Boot 3.x, MyBatis Plus, Lombok, MySQL 8 |
| Web | Vue 3, Vite, Vue Router, Pinia, Axios, Element Plus |
| Android | Java, Retrofit, OkHttp, WorkManager, Foreground Service |
| 部署 | Ubuntu 24, Docker Compose, Nginx, aria2 RPC |

## 总体架构

```
Vue 3 Web 管理台          Android Java App
         |                      |
         v                      v
       Nginx (HTTPS, 静态资源, API 反代)
                  |
                  v
     Spring Boot 3 API Server
     (认证/文件/下载/传输/通知/设备)
         |                |
         v                v
      MySQL 8         后台任务 (aria2 RPC)
      (元数据)         (扫描/同步/通知)
         |
         v
   Storage Roots (本地磁盘 / NAS 挂载)
```

## 模块边界

- **baiflow-server**：核心业务、权限、数据库、文件操作、下载任务、对外 API。文件路径只在服务端存在。
- **baiflow-web**：Web 管理台，只通过 REST API 通信。
- **baiflow-android**：移动端文件查看、上传、下载。
- **deploy**：Docker Compose、Nginx、环境变量。

## MVP 功能

### 认证与权限
- 用户名密码登录 + JWT token
- 三种角色：ADMIN、USER、GUEST
- 访客通过分享 URL 访问，不登录管理台

### 文件中心
- Storage Root 配置、目录浏览、文件上传/下载
- 新建文件夹、重命名、移动、删除
- 隐私文件夹（额外密码验证，密码只存 hash）

### 分享
- 文件/文件夹分享链接，支持过期时间、访问次数、下载次数、提取码
- 不暴露服务器真实路径
- 管理员或创建者可撤销

### 下载中心
- 通过 aria2 RPC 创建 URL/磁力/BT 下载
- 暂停、恢复、删除、状态和进度展示

### 传输与通知
- 统一上传/下载任务展示
- Web 内通知中心

### Android
- 登录、文件列表、上传、下载、任务状态
- 前台通知

## 非目标

- 第一版不做团队协作空间、在线预览
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
- `baiflow-server`、`mysql`、`aria2`、`nginx`
- Redis 后续可选

### Nginx 职责
- 托管静态文件、`/api/` 反代、SSE 支持、HTTPS、上传大小限制

## 安全基线

### 网络隔离
- MySQL、aria2 RPC 不暴露公网
- Spring Boot 管理端点不暴露公网
- 防火墙只开放必要端口

### 认证与鉴权
- 受保护 API 必须携带 `Authorization: Bearer <token>`
- 强制 ADMIN/USER/GUEST 角色行为
- 密码、分享 token、提取码、隐私密码只存 hash

### 文件安全
- 文件操作限制在配置的 Storage Root 内，路径需归一化校验
- 后端进程不用 root 运行
- 不向客户端暴露服务器绝对路径

### 分享安全
- 分享 URL 使用不可预测 token，数据库只存 hash
- 分享过期/超次/撤销后不可访问
- 公开分享接口不返回服务器真实路径

### 外网访问
- HTTPS + 强密码 + 登录失败限制 + 定期备份

### 安全检查项
- 未登录访问文件接口 → 401
- 普通用户访问未授权文件 → 403
- 分享链接过期/超次后不可访问
- 提取码错误不能访问分享内容
- 隐私文件夹密码错误不能访问
- 文件操作不会越出 Storage Root
