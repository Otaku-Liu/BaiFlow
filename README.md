# BaiFlow

BaiFlow（小白流转）是一个个人服务器上的下载与文件协同中心，包含 Spring Boot 后端、Vue 3 Web 管理台，后续阶段将接入 Android 客户端。

## 模块
- `baiflow-server`: Spring Boot API 服务端
- `baiflow-web`: Vue 3 Web 管理台
- `baiflow-android`: Android 客户端占位（后续阶段实现）
- `deploy`: Docker Compose 部署配置

## 环境要求

| 组件 | 版本 |
|---|---|
| JDK | 17+ |
| Maven | 3.8+ |
| Node.js | 18+（推荐 22 LTS） |
| MySQL | 8.0+ |
| Redis | 7.0+（后续阶段使用） |

## 快速启动

### 1. 数据库
确保 MySQL 8 已运行，创建数据库：
```sql
CREATE DATABASE IF NOT EXISTS baiflow_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. 后端
```powershell
cd baiflow-server

# 复制并编辑本地开发配置
copy src\main\resources\application-dev.example.yml src\main\resources\application-dev.yml
# 编辑 application-dev.yml 填入实际的数据库连接信息

# 运行测试
mvn test

# 启动服务（默认端口 8080）
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

### 3. 前端
```powershell
cd baiflow-web

# 安装依赖（首次运行，后续无需重复）
npm install

# 启动开发服务器（默认端口 5173，/api 请求自动代理到后端 8080）
npm run dev

# 构建生产包
npm run build
```

### 4. Docker Compose（MySQL + Redis）
```powershell
cd deploy
copy .env.example .env
# 编辑 .env 修改默认密码
docker compose up -d
```

## 项目阶段

| 阶段 | 状态 |
|---|---|
| Phase 0: 文档与边界 | ✅ |
| Phase 1: 项目骨架 | ✅ |
| Phase 2: 认证、用户与权限 | ✅ |
| Phase 3: 文件根据地 MVP | ✅ |
| Phase 3.5+ | 待实施 |
