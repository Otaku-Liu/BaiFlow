# 09 · 登录会话（Auth Session）方案（ADR）

> 状态：**已实现**（2026-08-06）；Grilling 定稿当日完成后端/Android/Web 实施
> 实施要点：`bf_auth_session` 并入统一可重复迁移 `db/R__V1_init.sql`（项目约定不单独建迁移脚本）；`SessionAuthenticationFilter` 替换 `JwtAuthenticationFilter`（保留 Bearer + `?token=`）；`AuthController` 增 `GET/DELETE /api/auth/sessions`、logout 改为吊销会话；Android 登录带设备头（长期会话）；Web 个人资料弹窗增「登录设备」列表 + 强制下线
> 类型：架构决策记录（ADR）
> 相关：`docs/01-architecture.md`、`docs/02-database.md`、`docs/03-api.md`、`docs/04-frontend.md`、`docs/05-android.md`
>
> 变更（2026-08-09）：吊销由「软删（置 `revoked_at`）」改为「**硬删会话记录**并移除该字段」，历史由审计日志留痕（`LOGOUT` / `FORCE_LOGOUT` / `PASSWORD_CHANGED`）；登录日志视图扩展为登录与会话操作日志。
> 变更（2026-08-09）：**WEB 会话改为滑动续期**（原固定 2h 到期必掉线）——活跃请求顺延 `expires_at`，2h 不活跃才过期；`touch()` 按设备类型取对应时长（ANDROID 180 天 / WEB webHours），节流 1h 写库。

## 1. 背景与目标

App 端当前 JWT 2 小时过期（`baiflow.jwt.expire-seconds`），用户频繁被要求重新登录，体验差。参考常见聊天软件「登录后基本不下线」的做法：

- 会话寿命是**吊销驱动**而非到期驱动——用户主动登出/被强制下线/改密码前，不因时间到期而要求重登；
- 客户端**后台无感续期**，用户看不到登录页；
- 多设备独立会话，可在别处查看并**强制下线**指定设备。

## 2. Grilling 决策

| 决策 | 结论 |
|---|---|
| 认证模型 | **模型 2：长会话 token + 服务端逐请求校验**（非「短 JWT + refresh 舞步」）——每次请求查 `bf_auth_session` 校验，吊销**即时生效** |
| 会话寿命 | 吊销驱动 + **不活跃兜底**（滑动：每次活跃请求顺延 `expires_at`；ANDROID 180 天 / WEB 约 2h 未使用自动失效） |
| 设备管理 | Web「个人资料」弹窗列**登录过的设备**（`bf_user_device` 历史 + 在线状态：在线/离线、首次/最近登录、最近活跃、当前标识），在线设备可「强制下线」；顺带支持修改密码、展示名/头像（已有） |
| Web 持久 | **Web 短期但滑动续期**：2h 内活跃即顺延（**2h 不活跃才过期**跳登录）；不做 Android 式长期免登录 |
| 登出 | 吊销当前会话 → 立即失效（区别于现状「客户端丢弃 token」） |
| 会话 token | 随机串只存 **SHA-256 哈希**；设备信息来自请求（设备类型/名称/UA/IP） |

## 3. 关键事实

- 现状：无状态 JWT（`JwtAuthenticationFilter` 校验 Bearer 或 `?token=`），无吊销、无 refresh；登出仅客户端丢 token。
- 每次请求查库对个人服务器量级可忽略（已确认接受）。

## 4. 数据模型（并入可重复迁移 `db/R__V1_init.sql`）

`bf_auth_session`：

```sql
id            VARCHAR(32)  -- 主键
user_id       VARCHAR(32)  -- 用户
device_name   VARCHAR(100) -- 设备名（App 机型 / Web UA 摘要）
device_type   VARCHAR(16)  -- ANDROID / WEB
ip            VARCHAR(64)
user_agent    VARCHAR(255)
token_hash    VARCHAR(64)  -- SHA-256(token)
expires_at    TIMESTAMP    -- 会话到期（WEB 短 / ANDROID 长）
last_used_at  TIMESTAMP    -- 最近使用（滑动续期的基准）
created_at    TIMESTAMP
```

## 5. 认证流程

- **登录**：`POST /api/auth/login`（请求可带 `X-Device-Type` / `X-Device-Name` 头）→ 建会话（`expires_at`：ANDROID 长期、WEB 短期）→ 返回 `{ token, sessionId, expiresAt, user }`。
- **每次请求**：过滤器取 token（Bearer 或 `?token=`）→ SHA-256 → 查 `bf_auth_session` → 校验「记录存在 && 未过 expires_at」→ **滑动续期**（距上次续期 >1h 才写库：更新 `last_used_at`、把 `expires_at` 顺延到 now + 对应设备时长）→ 注入身份。
- **登出 / 强制下线 / 改密码** → **删除**会话记录（即时失效），审计日志留痕（`LOGOUT` / `FORCE_LOGOUT` / `PASSWORD_CHANGED`）。

## 6. API 变更

- `POST /api/auth/login` — 响应新增 `sessionId` / `expiresAt`（`token`/`user` 保留兼容）
- `POST /api/auth/logout` — 吊销当前会话（从请求 token 定位）
- `GET /api/auth/sessions` — 当前用户的登录会话列表
- `GET /api/auth/devices` — 当前用户的登录设备列表（`bf_user_device` 历史 + 在线状态；登录时 upsert，登出不删）
- `DELETE /api/auth/sessions/{id}` — 强制下线某设备（本人；管理员可任意）
- `POST /api/auth/change-password`（已有）— 重置密码后吊销**全部**会话（所有设备强制下线重新登录）

## 7. 端改动

- **后端**：`bf_auth_session` 并入 `R__V1_init.sql`（可重复迁移）；`AuthSession` 实体 + Mapper；`SessionTokenService`（签发/校验/吊销/触摸）；认证过滤器**替换** `JwtAuthenticationFilter`（保留 Bearer 与 `?token=` 双通道）；`AuthController` 增 sessions/revoke；`LoginResponse` 扩展；`SecurityConfig`（`/api/auth/login` permitAll，其余照旧）；`BaiflowProperties` 增会话时长配置（WEB 2h / ANDROID 180d）；移除 `JwtService` 与 jwt 配置。
- **Android**：登录带 `X-Device-Type=ANDROID` + `X-Device-Name=机型`；`SessionManager` 存会话 token（长 token 直接发，无刷新舞步）；401 → 清会话（被强制下线/过期即回到登录）。
- **Web**：登录/拦截器沿用（token 变为会话 token）；个人资料弹窗扩展「修改密码」「登录设备列表 + 强制下线」。

## 8. 安全

- token 只存哈希，数据库泄露不直接暴露可用 token；吊销即时生效。
- 180 天不活跃兜底防「永不失效」的长期会话堆积。
- 重置密码吊销**全部**会话（含当前，所有设备强制下线重新登录）；强制下线即时。
- 吊销即硬删记录，登出/强制下线/改密操作均写审计日志留痕（`LOGOUT` / `FORCE_LOGOUT` / `PASSWORD_CHANGED`）。
- `?token=` 媒体/SSE 通道沿用（token 变为会话 token，渲染时取当前值）。

## 9. 范围与边界

- **本期不做**：多设备登录冲突提示/挤线。
- 会话审计：已实现硬删 + 审计留痕（`LOGOUT` / `FORCE_LOGOUT` / `PASSWORD_CHANGED`），登录日志视图按操作类型展示与筛选（2026-08-09 追加）。
- Web 会话短期 + 滑动续期（**2h 不活跃过期**，活跃不踢）；不做 Android 式长期免登录。2026-08-09 由「固定 2h 到期」改为滑动续期（原为按用户早期理解设计）。
- 每次请求一次 DB 查询（个人服务器可接受）。
