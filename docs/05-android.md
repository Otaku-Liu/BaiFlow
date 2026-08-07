# Android 客户端设计

## 技术栈

Java + Retrofit + OkHttp + WorkManager + Foreground Service + SharedPreferences

## MVP 功能

- 登录、文件列表（底部三栏：文件 / 随手记 / 我的）、上传手机文件、下载服务器文件、查看传输状态
- 长任务前台通知
- 文件页与 Web 对齐：自动使用第一个可用存储根（无下拉框）；管理员可「查看用户」切换 viewUserId
- 随手记：富文本所见即所得笔记编辑器（加粗/标题/列表/引用/代码块等 + 图片/录音/画画媒体），见 `docs/07-quick-notes.md` Phase 2

## 模块

```
baiflow-android/app/src/main/java/
  auth/        # 登录会话 token 管理
  editor/      # 随手记富文本编辑器（纯 JVM Markdown 解析/发射 + Spannable 适配层）
  model/       # API 数据模型
  network/     # Retrofit + OkHttp
  transfer/    # 传输任务
  ui/          # Activity/Fragment
  util/        # 通用工具
```

## 网络层

- Retrofit 定义 REST API
- OkHttp Interceptor 注入 Bearer token
- 401 → 重新登录
- 超时合理设置

## 登录态

SharedPreferences 保存 token（**登录会话 token，长期保持**）和服务器地址，后续复杂缓存引入 Room。登录请求带 `X-Device-Type: ANDROID` + `X-Device-Name`（机型）头，服务端据此建会话（ANDROID 长期，180 天不活跃兜底）；被强制下线/过期后 401 清会话回登录。详见 `docs/09-auth-sessions.md`。

## 上传下载

- 小文件 Retrofit multipart 上传
- 大文件后续做分片
- 长任务 WorkManager 或 Foreground Service

## 通知

- 上传下载前台通知
- 任务完成/失败更新通知
- 点击进入详情

## 页面

- 登录 / 服务器配置（含连通性检测）→ **MainActivity（底部三栏壳，`ViewPager2` 承载，左右滑动切换三页并与底部导航双向同步）**
  - **文件**：`FilesFragment`（列表/上传/下载/删除/隐私文件夹/管理员用户切换；标题栏「上一级」按钮逐级返回、根目录置灰）
  - **随手记**：`NotesFragment`（笔记列表/搜索/删除）→ `NoteEditActivity`（富文本编辑器）→ `NoteDrawActivity`（画画）
  - **我的**：`MineFragment`（用户信息、**修改资料 / 修改密码（重置后全设备强制下线重新登录）/ 语言设置（中英）**、传输任务、服务器配置、退出登录）
- 传输任务为独立 `TransferListActivity`，从「我的」进入

## 多语言（i18n）

- 默认中文（`res/values/strings.xml`）+ 英文（`res/values-en/strings.xml`），159 个字符串 key 两语言一致。
- 「我的 → 语言」切换：`AppCompatDelegate.setApplicationLocales` 持久化并自动重建 Activity 应用新语言。
- 布局一律 `@string/key`、Java 一律 `getString(R.string.key)`；带参数用 `%1$s` 格式串（如 `common_network_error`）。

## 失败处理

网络不可用提示、token 失效跳登录、失败保留任务和错误原因

- **网络连接失败**（见 `docs/11-android-network-error.md`）：`UiCallback` 交互回调统一处理——收到任何响应即视为成功联系，网络级失败（IOException）按「无网络连接 / 无法连接服务器」分类全局 Toast + 去重（断连时段内一条、成功即清除、恢复时提示「网络已恢复」）；HTTP 5xx 全局兜底「服务器异常」。OkHttp connect 超时 10s。设备断网由 MainActivity 的 ConnectivityManager 监听即时提示。**后台传输服务不弹 UI**（有通知与失败状态）。
- **token 失效**：`AuthInterceptor` 401 清会话（下次启动回登录）。
- **上传/下载失败**：任务保留 + 错误原因记录。

## 设计系统（iOS 风格）

Android UI 采用集中式 iOS 风格设计系统：组件样式定义在 `res/values/styles_ios.xml`（`@style/Ios.*`），所有布局引用继承，改一处全局生效。已覆盖按钮（Primary/Text/Danger）、输入框、标题栏（居中标题 + chevron 返回 + 上一级名，无阴影）、卡片/列表项。**新增组件样式一律加进 `styles_ios.xml`，不写硬编码色值/圆角**，规范详见 `docs/08-ios-design-system.md`。

## 随手记（Phase 2 已实现，Phase 3 待实施）

Phase 2（在线）：列表页 + 富文本所见即所得编辑器（`editor/` 纯 JVM Markdown 解析/发射 + Spannable 适配层，未知内容透传不丢数据）+ 图片/录音/画画媒体。详见 `docs/07-quick-notes.md`。

Phase 3（待实施）：**Android 离线模式**（三态：本地模式 / 在线模式 / 离线模式）——Room 本地缓存 + outbox + `updatedAfter` 增量合并（冲突对齐在线端乐观并发）+ WorkManager 后台同步；入口改为「配置引导页」，登录页提供离线逃生口，进离线清 token 本地免登录。完整设计见 `docs/12-android-offline-mode.md`。
