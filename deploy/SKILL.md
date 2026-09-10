---
name: baiflow-deploy
description: BaiFlow 部署：Docker Compose 拉取 GHCR 镜像、Nginx、MySQL 8、Ubuntu 24、HTTPS、GitHub Actions 发布
---

# BaiFlow Deploy

## 约束

- Ubuntu 24，Docker Compose（server + web 容器化；MySQL/Redis 复用服务器已有容器，host 网络直连）
- 镜像从 GHCR 拉取，**服务器上不编译源码**；本地要从源码构建验证时叠加覆盖文件
- 配置走 `deploy/.env` 环境变量，不写死路径/凭据；镜像版本由 `BAIFLOW_IMAGE_TAG` 决定（默认 `latest`）
- 生产：`cd deploy && docker compose pull && docker compose up -d`
- 本地源码构建：`cd deploy && docker compose -f docker-compose.yml -f docker-compose.build.yml up -d --build`
- 重启 server/web：`cd deploy && docker compose restart`（MySQL/Redis 独立管理，不随 compose 重启）
- MySQL 不暴露公网，密钥走环境变量
- Nginx：静态资源 + `/api/` 反代 + HTTPS + 上传大小限制（`baiflow-web/nginx.conf` 容器版）
- 详见 `docs/01-architecture.md`（部署与安全章节）

## 发布链路

推 `v*` tag → Actions 构建 server/web 镜像推 GHCR → SSH 登服务器把版本号写进 `.env` → `docker compose pull && up -d` → 探 `/api/health` 确认就绪。

- `.github/workflows/ci.yml`：push main / PR 三端编译校验（不需要 Secret）
- `.github/workflows/release.yml`：tag 发版与部署（需要下面四个 Secret）

## 首次上线（一次性初始化）

**服务器侧**

1. `git clone https://github.com/Otaku-Liu/BaiFlow.git ~/baiflow` —— 只需要它的 `deploy/`；之后靠 `git pull` 更新 compose 文件（不编译）
2. `cd ~/baiflow/deploy && cp .env.example .env`，填 MySQL/Redis 连接；`BAIFLOW_IMAGE_TAG` 先留 `latest`（**不再需要管理员密码**，管理员由向导创建）
3. 确认 MySQL 里已有 `baiflow` 库（表由首次启动的 Flyway 迁移自动创建）
4. 确认 GitHub runner 能 SSH 到这台机器（见下方前置条件）

**创建第一个管理员（首次启动后）**：服务器**不再预置 admin 账号**。

1. 取初始化令牌：`docker compose logs server | grep -A3 初始化令牌`，或直接看数据目录的 `setup-token.txt`
2. 浏览器打开 `http://<服务器>:8088/setup`，填令牌 + 用户名 + 密码，提交后自动登录
3. 初始化入口随即永久关闭（`bf_system_setting.initialized_at` 单向标记），令牌文件删除

> 忘记管理员密码没有后门可走（这是有意的）：只能进 MySQL 改 `bf_user.password_hash`，或按下节清表重走一次向导。

**GitHub 侧**（Settings → Secrets and variables → Actions，新建 4 个 Repository secret）

- `DEPLOY_HOST` —— 服务器地址（GitHub runner 能 SSH 到达）
- `DEPLOY_USER` —— SSH 用户
- `DEPLOY_SSH_KEY` —— 该用户的 ed25519 私钥（OpenSSH 格式全文）
- `DEPLOY_PATH` —— 服务器上 clone 的绝对路径（内含 `deploy/`）

镜像首次推送后，到 GitHub 的 Packages 页面把 `baiflow-server` / `baiflow-web` 两个包改为 **Public**，否则服务器拉取需要 `docker login`。

## 重走首次初始化（测试 / 重新初始化）

只在测试库上用。清空这两张表再重启，向导入口就会重新打开：

```sql
DELETE FROM bf_user;            -- 全部用户（含管理员）
DELETE FROM bf_system_setting;  -- initialized_at 单向标记
```

```bash
cd deploy && docker compose restart server
docker compose logs server | grep -A3 初始化令牌
```

**必须两张一起清**，少清一张入口都不会开：

- 只清 `bf_user`：单向标记还在，入口不重开
- 只清 `bf_system_setting`：启动兜底发现仍有 ADMIN 用户，会立刻把标记补回去

⚠️ 会一并清掉全部账号（登录会话随之失效），笔记/文件等数据仍留在库里但失去归属——**别在生产库上做**。生产上忘记管理员密码时只重置那一个账号的密码即可。

## 彻底清库（连用户数据一起重来）

上一节只清账号，文件/笔记等数据仍留在库里但失去归属。要完全回到全新状态：

```sql
DROP DATABASE IF EXISTS baiflow;
CREATE DATABASE baiflow CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

```bash
cd deploy && docker compose restart server                  # 重启后 Flyway 自动重建全部表
rm -rf /data/baiflow/files /data/baiflow/notes-media        # 磁盘数据（avatars 视需要保留）
```

- 重建库用 `utf8mb4` / `utf8mb4_0900_ai_ci`，与迁移脚本里的表定义一致
- 重启时库为空、无迁移历史 → Flyway 重新执行 `R__V1_init.sql`，19 张表全部重建，向导入口自动打开
- 数据目录以 `deploy/.env` 的 `BAIFLOW_DATA_DIR` 为准（默认 `/data/baiflow`）

> 没有 `DROP DATABASE` 权限时改为只清表。**必须连 Flyway 历史表一起清**——否则 Flyway 以为迁移已执行过，不会重建表，服务会对着空库启动：
>
> ```sql
> SET FOREIGN_KEY_CHECKS = 0;
> SET SESSION group_concat_max_len = 1000000;
> SET @t = NULL;
> SELECT GROUP_CONCAT('`', table_name, '`') INTO @t FROM information_schema.tables
>  WHERE table_schema = DATABASE()
>    AND (table_name LIKE 'bf\_%' OR table_name = 'flyway_schema_history');
> SET @s = IFNULL(CONCAT('DROP TABLE IF EXISTS ', @t), 'SELECT 1');
> PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
> SET FOREIGN_KEY_CHECKS = 1;
> ```

## 前置条件

- **SSH 连通性**：托管 runner 从公网随机 IP 发起连接，服务器 SSH 端口必须能从公网到达（家庭网络需在路由器做端口转发；NAS 防火墙别只放行 Web 端口）。不便开放 SSH 时退回手动部署——Actions 仍会构建并推送镜像，登录服务器执行 `docker compose pull && up -d` 即可。
- `release.yml` 用默认 22 端口；非标准端口需在 ssh 命令补 `-p <端口>`。
- 不要在服务器上给 SSH 配来源 IP 白名单——runner 出口 IP 每次都不同。

## 回滚

1. 改服务器 `deploy/.env` 的 `BAIFLOW_IMAGE_TAG` 为上一版版本号
2. `docker compose pull && docker compose up -d`
