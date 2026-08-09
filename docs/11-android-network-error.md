# 11 · Android 网络连接失败提示（Network Error Feedback）方案（ADR）

> 状态：**已实现**（2026-08-07）；Grilling 定稿后当日完成实现，`./gradlew :app:compileDebugJavaWithJavac` 通过，并经受 code-review（按发现修复了未使用 Throwable 参数、清理死字符串资源等）
> 触发点：Android 端连不上服务器时要有明确提示；当前错误处理是逐调用 `onFailure` → Toast，且直接拼 `t.getMessage()`（泄漏 IP/技术细节）
> 类型：架构决策记录（ADR）
> 相关：`docs/05-android.md`、`docs/10-web-connection-timeout.md`（同主题 Web 端对照）、`baiflow-android/app/src/main/java/com/baiflow/android/network/`

## 1. 背景与目标

Android 端网络错误处理现状：
- 各 Fragment/Activity 在 `Callback.onFailure` 里各自 `Toast.makeText(..., getString(R.string.common_network_error, t.getMessage()))`——用户看到 "Failed to connect to /192.168.x.x:8080"、"timeout" 等技术细节，**泄漏服务器 IP**。
- **无** ConnectivityManager 设备断网监听；断网/服务器宕机时只有用户主动操作且请求失败才有反馈。
- OkHttp 超时 connect 30s / read 60s / write 60s——服务器不可达时最久挂 30s 才反馈。
- 文件/笔记页已有 SwipeRefreshLayout（下拉重试）。

目标：**连不上服务器时有明确、友好、不打扰的提示**，且与后台任务解耦。

关键区分（对照 Web 端）：**Android 网络失败 ≠ 会话失效**——设备断网或服务器宕机时会话仍有效，**不应清会话、不应回登录页**；用户留在当前页可下拉重试。

## 2. Grilling 决策

| 决策 | 结论 |
|---|---|
| 提示形式 | **全局统一 Toast + 去重**：网络级失败统一友好文案，同一「断连时段」内只提示一次 |
| 触发面 | **请求失败 + 设备断网监听**：交互请求网络级失败 → 提示；ConnectivityManager 检测设备无网（飞行/断WiFi）→ 主动提示「无网络连接」 |
| 文案粒度 | **细分三种**：设备无网「无网络连接」/ 服务器不可达「无法连接服务器」/ HTTP 5xx「服务器异常」 |
| 作用范围 | **主界面 + 登录页文案**：文件/笔记/下载/我的等交互界面统一友好提示；登录/服务器配置页改友好文案；**上传下载前台服务、WorkManager 后台同步不弹 UI**（已有通知/自身失败机制） |
| 去重界定 | **时段内一条，成功即清除**：任一次请求成功（收到任何响应）即视为恢复并清除；断连期间并发/重试失败不刷屏 |
| 恢复提示 | 断连时段结束后弹一条「网络已恢复，请继续操作」 |
| 超时调整 | **仅 connect 超时 30s → 10s**（连不上更快反馈）；read/write 保持 60s（下载/大响应需要长读） |
| 5xx 归属 | 5xx 走**全局兜底**提示「服务器异常」，各页面不再重复弹网络类提示（避免双弹） |

## 3. 关键事实

- **UploadService / DownloadService 共用 `ApiClient` 的 OkHttp 客户端**（`call.execute()` 同步调用），因此**不能用 OkHttp 全局拦截器做提示**——会误伤后台任务，违背「后台服务不弹 UI」。改为**交互层 `UiCallback` 包装**：交互调用点显式选用，后台 `execute()` 天然不受影响。
- Retrofit `enqueue` 回调运行在主线程，Toast 无需额外切线程；ConnectivityManager 回调需用主线程 Handler 注册。
- App 已全量中英双语（`values/` + `values-en/`），新文案两套都补。

## 4. 设计

### 4.1 判定与去重状态机

```
交互请求 (UiCallback)
  ├─ 收到任何 HTTP 响应 → reportContact()
  │     ├─ 若处于断连时段 → 结束时段 + toast「网络已恢复」
  │     └─ 若 5xx → reportServerError()：toast「服务器异常」（serverErrorShown 去重，2xx/3xx/4xx 清除）
  └─ 网络级失败 (IOException，无响应) → reportFailure(t)
        ├─ 设备无网(ConnectivityManager) → toast「无网络连接」
        ├─ 否则 → toast「无法连接服务器，请检查网络或稍后重试」
        └─ 同一断连时段内后续失败不再提示（offline 去重）

设备断网监听 (ConnectivityManager, MainActivity 注册)
  ├─ 网络丢失 → reportFailure(null) → 「无网络连接」（offline 去重）
  └─ 网络恢复 → reportContact() → 结束时段 + toast「网络已恢复」（时段内仅一次）
```

- 断连时段标记 `offline`：网络级失败/设备断网置位；任一响应清除（触发恢复提示，每时段一次）。
- 5xx 标记 `serverErrorShown`：5xx 置位并提示；非 5xx 响应清除。
- 分类 `classify(t)`：查 ConnectivityManager `hasNetwork()`——无网 → 无网络连接；有网但请求失败 → 无法连接服务器。

### 4.2 组件

- **`network/NetworkFeedback.java`**（静态单例，持有 Application context；静态状态仅主线程读写）
  - `reportFailure(Context)` / `reportDeviceOffline(Context)` / `reportContact(Context)` / `reportServerError(Context)` / `reportServerOk(Context)` / `classify(Context)` / `hasNetwork(Context)`
  - 内部维护 `offline`、`serverErrorShown` 去重状态；Toast 统一走 `Handler(Looper.getMainLooper())`
- **`network/UiCallback<T>`**（抽象包装，实现 `retrofit2.Callback<T>`）
  - `final onResponse`：`reportContact()` + 5xx 检测 → 委托 `onUiResponse`
  - `final onFailure`：IOException → `reportFailure()` → 委托 `onUiFailure`
  - 抽象 `onUiResponse` / `onUiFailure`：各页面继承，只写业务处理；**不再写网络错误 Toast**
- **`MainActivity`**：注册 ConnectivityManager `NetworkCallback`（onLost→reportFailure / onAvailable→reportContact），onDestroy 注销；覆盖文件/笔记/我的三个 Tab

### 4.3 各页面改造（行为对齐）

- 交互调用点（FilesFragment / NotesFragment / MineFragment / NoteEditActivity 笔记编辑器）`new Callback<X>(){}` → `new UiCallback<X>(ctx){}`，删除 onFailure 里的网络错误 Toast；onResponse 里对 `code >= 500` 的分支不再弹 server message（全局已兜底）。
- LoginActivity / ServerConfigActivity：onFailure 改用 `NetworkFeedback.classify(t)` 的友好文案做内联提示（**不触发全局 Toast**，避免双弹）。
- 后台 UploadService / DownloadService：不改（保持现有失败处理，不弹 UI）。

### 4.4 文案（`values/` + `values-en/` 新增）

| key | 中文 | English |
|---|---|---|
| `network_no_connection` | 无网络连接，请检查网络设置 | No network connection. Check your network settings. |
| `cannot_reach_server` | 无法连接服务器，请检查网络或稍后重试 | Cannot reach the server. Check the network and retry later. |
| `server_error` | 服务器异常，请稍后重试 | Server error. Please retry later. |
| `network_recovered` | 网络已恢复，请继续操作 | Network recovered. Please continue. |

## 5. 端改动（Android）

- 新增 `network/NetworkFeedback.java`、`network/UiCallback.java`
- `network/ApiClient.java`：OkHttp `connectTimeout` 30s → 10s
- `ui/MainActivity.java`：ConnectivityManager 监听注册/注销
- `ui/FilesFragment.java`、`ui/NotesFragment.java`、`ui/MineFragment.java`、`ui/NoteEditActivity.java`：调用点换 `UiCallback`，删网络错误 Toast，5xx 分支去重
- `ui/LoginActivity.java`、`ui/ServerConfigActivity.java`：失败文案改 `classify()` 友好文案
- `res/values/strings.xml` + `res/values-en/strings.xml`：新增 4 个 key

## 6. 安全

- 替换 `t.getMessage()`：不再向用户展示异常原文/服务器 IP/技术细节。
- 网络失败不清会话、不回登录页（与 401 语义区分）；401 仍由 `AuthInterceptor` 清会话（既有行为，本需求不改）。

## 7. 范围与边界

- **本期范围**：Android 交互界面（文件/笔记/我的/下载/登录/服务器配置）。**Web 端已实现**（`docs/10-web-connection-timeout.md`）。
- **后台任务不做**：上传/下载前台服务（有通知与失败状态）、WorkManager 后台同步（自身重试机制）不弹 UI。
- **已接受的局限**：无后台心跳——用户闲置（不操作）时服务器宕机无法即时发现；设备断网可被 ConnectivityManager 立即感知。
- **待办（相邻缺口，非本期）**：401 清会话后无即时回登录导航（下次启动才回）；与「设备断网监听」同一文件可顺带完善，但本期不扩 scope。
