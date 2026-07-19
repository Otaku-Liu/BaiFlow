# BaiFlow 技术架构

## 技术栈
- 后端：JDK 17 + Spring Boot 3.x
- ORM：MyBatis Plus
- Lombok：减少样板代码（@Data / @Getter）
- 数据库：MySQL 8
- Web 前端：Vue 3 + Vite
- Android：Java
- 反向代理：Nginx
- 下载引擎：aria2 RPC
- 部署：Ubuntu 24 + Docker Compose

## 总体架构
```text
Vue 3 Web 管理台
Android Java App
        |
        v
Nginx
  - HTTPS
  - 静态资源
  - API 反代
  - 下载入口
        |
        v
Spring Boot 3 API Server
  - 认证授权
  - 文件管理
  - 下载任务
  - 传输任务
  - 通知中心
  - 设备管理
        |
        +------------------+
        |                  |
        v                  v
MySQL 8              后台任务执行器
元数据/任务/日志       - aria2 RPC
                    - 文件扫描
                    - 状态同步
                    - 通知发送
        |
        v
Storage Roots
  - 服务器本地磁盘
  - NAS 挂载目录
```

## 模块边界
### baiflow-server
负责核心业务、权限、数据库、文件操作、下载任务、通知和对外 API。所有真实文件路径只在服务端存在，不返回给前端和 Android。

### baiflow-web
负责 Web 管理台。只通过 REST API 和事件接口与服务端通信，不访问数据库或服务器文件系统。

### baiflow-android
负责移动端文件查看、上传、下载和任务状态展示。Android 使用 Java，网络请求通过 Retrofit/OkHttp。

### deploy
负责 Docker Compose、Nginx、环境变量示例和部署说明。

## 数据流
1. Web 或 Android 登录获取 token。
2. 客户端携带 token 调用 `/api/**`。
3. Spring Boot 校验权限并执行文件、任务或通知操作。
4. 文件写入 Storage Root，元数据写入 MySQL。
5. 下载任务交给 aria2，后端定时或事件同步状态。
6. 前端通过轮询、SSE 或 WebSocket 获取任务进度。

## 安全边界
- 客户端永远不直接访问服务器真实路径。
- 文件操作必须限制在 Storage Root 内。
- 所有受保护 API 必须校验 token。
- Nginx 不直接暴露 MySQL、aria2 RPC 和内部管理端口。
