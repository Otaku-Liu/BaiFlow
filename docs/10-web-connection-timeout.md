# 10 · Web 服务器连接超时处理（Server Connection Timeout）方案（ADR）

> 状态：**Grilling 定稿**（2026-08-07），待实现
> 触发点：浏览器进入管理台后若一段时间无法连接服务器（网络级失败），提示「服务器连接超时」并返回登录页（保留会话，可一键重连）
> 类型：架构决策记录（ADR）
> 相关：`docs/04-frontend.md`、`docs/09-auth-sessions.md`、`baiflow-web/src/api/http.js`

## 1. 背景与目标

Web 管理台当前对**网络级失败**（连不上服务器、连接超时、断网）是静默的：`http.js` 拦截器只处理 401（清会话回登录）与 403（仅提示），`error.response === undefined` 的请求直接 reject，用户看到「点了没反应」。

目标：登录进入管理台后，若**一段时间无法连接服务器**，明确提示「服务器连接超时」，并回到登录页；因超时是网络问题而非会话失效，**保留会话 token**，登录页提供「重新连接」一键恢复，避免服务恢复后被迫重输密码。

## 2. Grilling 决策

| 决策 | 结论 |
|---|---|
| 检测方式 | **仅依赖实际请求失败**（不引入心跳轮询）——以「最后一次成功联系」为基准；某次请求发生网络级失败时，若距上次成功联系已 ≥30s 即判定超时。用户闲置无请求时检测不到（已确认接受的请求驱动局限） |
| 超时时长 | **30 秒**（距上次成功联系 ≥30s 且发生一次失败） |
| 触发范围 | **仅网络级失败**（`error.response` 为空：连不上 / 连接超时 / 断网）；收到任何 HTTP 响应（含 4xx/5xx）都视为服务器可达，重置计时 |
| 会话处理 | **保留 token** + 登录页「重新连接」按钮（服务器会话未删除，超时≠会话失效） |
| 提示形式 | **toast + 自动跳转**：顶部 ElMessage 提示，约 1.5s 后自动跳登录页（与 401「登录已过期」风格一致，但走客户端路由跳转，不整页刷新） |
| 计时起点 | **从进入界面起算**：首次登录态请求即启动检测并以此为基准；刷新/重进后服务器已挂时，用户后续操作且距起点 ≥30s 可被触发 |
| 阈值前失败 | **静默**：单次失败未达阈值不提示，避免笔记自动保存等高频请求下刷屏 |
| 重新连接 | 先探测 `GET /api/health`（公开免认证），通过后校验 `GET /auth/me`：会话有效 → 直接回主界面；401 → 清 token 走正常登录表单 |
| 登录页呈现 | 超时态下：提示条「无法连接服务器」+ 正常登录表单仍可用 + 「重新连接」按钮 |
| 401 优先级 | **401 优先**：服务器可达（有响应）即不会触发超时；401 仍走现有清会话流程 |

## 3. 关键事实

- `GET /api/health` 已在 `SecurityConfig` 配置 `permitAll`（公开免认证），适合做重连探测（见 `docs/01-architecture.md`）。
- SSE `/api/events` 仅笔记页开启且 EventSource 自动重连，不适合做全局连接信号，本方案不依赖它。
- 现 401 流程用 `window.location.href = '/login'` **整页刷新**；超时流程**必须用客户端路由跳转**（`router.push('/login')`），否则整页刷新会 `restoreSession` 恢复 token 又被守卫弹回主界面，且超时态标志丢失。
- 登录页 `LoginView.vue` 目前未接入 i18n（硬编码中文），本功能提示沿用硬编码中文，与登录页现状一致。

## 4. 判定流程

```
请求发出（登录态）→ 启动/已有检测（lastContactAt = 进入时刻）
      │
      ├─ 收到任何 HTTP 响应（含 4xx/5xx）→ lastContactAt = now（服务器可达，重置计时）
      │      └─ 401 → 现有清会话流程（优先于超时）
      │      └─ 403 → 仅提示，计时已重置
      │
      └─ 网络级失败（error.response 为空）→ 若 now - lastContactAt ≥ 30s 且未触发过
              └─ 触发：authStore.connectionTimeout = true
                      + ElMessage「服务器连接超时」
                      + App.vue 监听标志 → router.push('/login')
                      + 不清 token，不整页刷新
```

超时后登录页「超时态」：

```
登录页（connectionTimeout = true）
      ├─ 提示条「无法连接服务器，请检查网络连接」
      ├─ 正常登录表单仍可用（直接输密码登录 → 成功即清标志回主界面）
      └─ 「重新连接」→ GET /api/health → GET /auth/me
             ├─ 健康 + 会话有效 → 清标志 + 重启检测 + 回主界面
             ├─ /auth/me 401 → 现有流程清会话（转为正常登录表单）
             └─ 仍失败 → 保持超时态，可再次重连
```

## 5. 状态与组件改动（Web）

- 新增 `baiflow-web/src/utils/connectionMonitor.js`：模块级检测状态
  - `lastContactAt`（最近成功联系或进入时刻）、`started`、`timeoutFired`（去重，防并发失败重复触发）
  - 导出 `startMonitor()`（强制重启）/ `ensureMonitor()`（仅首次启动，**不重置基准**）/ `noteContact()` / `resetMonitor()` / `shouldFireTimeout()`
- `baiflow-web/src/api/http.js`：
  - 请求拦截器：登录态首个请求时 `ensureMonitor()`（仅首次生效；不能每次拨动 `lastContactAt`，否则 30s 判定永不触发）
  - 响应成功 / 收到任何状态码：`noteContact()`
  - 网络级失败：`shouldFireTimeout()` 为真 → 置 `authStore.connectionTimeout = true` + toast（不导航、不清会话）
- `baiflow-web/src/stores/auth.js`：新增 `connectionTimeout` 状态；`setSession` 清标志并 `startMonitor()`（重启检测，登录/重连即进入界面）；`clearSession` 复位标志并 `resetMonitor()`
- `baiflow-web/src/router/index.js`：守卫放行 `/login`——`to.path === '/login' && isLoggedIn && connectionTimeout` 时不再弹回 `/`
- `baiflow-web/src/App.vue`：watch `authStore.connectionTimeout`，为 true 时延迟 1.5s `router.push('/login')`（客户端跳转，避免整页刷新丢状态；重连/重登清除标志则取消未执行跳转）
- `baiflow-web/src/views/LoginView.vue`：超时态提示条 + 「重新连接」按钮 + `handleReconnect`（探测健康 + 校验会话；401 或业务错误清会话转正常登录表单）；登录成功经 `setSession` 已清标志并重启检测
- 新增 `baiflow-web/src/api/health.js`：`getHealth()` 封装 `GET /api/health`

## 6. 安全

- 超时态**不**清除会话 token，仅客户端导航到登录页；token 仍只在内存 + `localStorage`（现状），不新增存储面。
- 重连探测 `/api/health` 为公开端点，无敏感信息。
- 超时判定只发生在**登录态**请求上；登录页（未登录）请求失败仍走现有「登录请求失败」提示，不进入超时态。

## 7. 范围与边界

- **本期范围**：Web 管理台（`baiflow-web/`）。**Android 不做**（有独立的重试/同步机制）。
- **公共分享页（GUEST）不做**：访客无会话，不适用本机制。
- **已接受的局限**：仅请求驱动 → 用户闲置（无任何请求）时断连无法即时发现；冷启动后需有请求且距起点 ≥30s 才触发。
- **待办**：登录页整体 i18n（本功能仅新增中文文案，随登录页 i18n 一起收编）。
