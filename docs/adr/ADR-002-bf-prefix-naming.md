# ADR-002：类命名统一 Bf 表名前缀 + 头像/资料接口归入 BfUserController

**状态**：已接受 · **日期**：2026-08-30

## 背景（Context）

- 数据库表统一 `bf_` 前缀，但 Java 类名不带前缀，无法一眼区分"绑定某张表"与"无表业务逻辑"两类类。
- `AuthController`（`/api/auth`）混放了纯认证接口（login/logout/sessions/devices）与个人信息接口（me/profile/avatar/change-password），职责混杂。
- `UserController`（`/api/users`，仅 ADMIN）与自服务头像接口的归属语义一直模糊，根因是命名无法表达类与表的绑定关系。

## 决策（Decision）

1. **命名规则**：绑定 `bf_*` 表的类统一加 `Bf` 前缀，**按表名命名**（例：`bf_share_link` → `BfShareLink` / `BfShareLinkMapper` / `BfShareLinkService`），覆盖 **Entity / Mapper / Service(+Impl) / Controller** 四层。**DTO / VO / Request / enum / 配置类保持原名**（业务模型不挂表）。
2. **无单一主表的类不带 `Bf`**：`AuthController`、`PublicShareController`、`HealthController`、`AuthService`、`SessionTokenService`、`HealthService`。
3. 模块控制器也按表名：`FileController` → `BfFileItemController`、`TransferController` → `BfTransferTaskController`、`ShareController` → `BfShareLinkController`。
4. **自服务个人信息接口**（`GET /me`、`PATCH /profile`、`POST /avatar`、`DELETE /avatar`）从 `/api/auth/*` 迁入 `BfUserController` 的 `/api/users/me/**`；服务逻辑（`me`/`updateProfile`/`uploadAvatar`/`deleteAvatar`）从 `AuthService` 迁入 `BfUserService`。
5. **`change-password` 留守 `AuthController`**（`/api/auth/change-password` 不变）：改密需吊销全部会话，属认证侧职责，避免产生 user→auth 反向依赖。
6. **权限**：不引入 Shiro。维持路径式门禁（`SecurityConfig`），在 `/api/users/**` ADMIN 门禁**之前**声明 `/api/users/me/**` `authenticated()` 例外——集中审计 + fail-closed（新增 admin 端点默认受保护）。
7. **全量重命名一次到位**，不保留旧类名兼容别名；无测试文件波及。
8. **已评估并拒绝**"上传公共工具类"：头像上传（1MB + 扩展名白名单 + avatar 目录 + `transferTo`）与文件上传（任意类型 + storage-root + `verifyPathInRoot` + SHA-256）校验/路径/写入方式均不同，唯一共性是 2-3 行 `Files.createDirectories` + 写入，不值得抽类。

## 后果（Consequences）

- ✅ 类名 = 表名，一眼可辨绑定哪张表；无表业务类保持原名，两类一眼区分。
- ✅ 表映射已由 `@TableName("bf_*")` 显式声明、Mapper 扫描为 `com.baiflow.**.mapper` 通配，纯类名重命名零行为变化、不触 DB。
- ⚠️ 接口 URL 变更：`/api/auth/me|profile|avatar` → `/api/users/me/**`；`change-password` 不变。Web `api/auth.js` 与 Android `ApiClient.java` 需同步；服务端与客户端**不原子升级**，旧客户端打新服务端这三组接口 404。
- ⚠️ `/api/users/me/**` 若不在 `SecurityConfig` 中先于 admin 门禁放行，自服务接口会被 403 拦截。
- ⚠️ `BfUserController` 同时承载管理台 CRUD（ADMIN）与自服务（任何登录用户），两个 actor 共居一控制器，靠路径 ACL 区分——已接受的取舍。

## 相关

- `docs/03-api.md`（认证/用户接口）、`docs/06-coding-standards.md`（命名规则）
- `SecurityConfig.java`、`AuthController` / `AuthServiceImpl`、`UserController` / `UserService` / `AuthServiceImpl`（me/profile/avatar 迁出源）
- `baiflow-web/src/api/auth.js`、`baiflow-android/.../ApiClient.java`
