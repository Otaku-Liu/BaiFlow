# Android 客户端设计

## 技术栈

Java + Retrofit + OkHttp + WorkManager + Foreground Service + SharedPreferences

## MVP 功能

- 登录、文件列表（底部三栏：文件 / 随手记 / 我的）、上传手机文件、下载服务器文件、查看传输状态
- 长任务前台通知
- 文件页与 Web 对齐：自动使用第一个可用存储根（无下拉框）；管理员可「查看用户」切换 viewUserId；随手记为占位页（笔记功能后续迭代）

## 模块

```
baiflow-android/app/src/main/java/
  auth/        # 登录与 token 管理
  network/     # Retrofit + OkHttp
  file/        # 文件操作
  transfer/    # 传输任务
  notification/ # 前台通知
  ui/          # Activity/Fragment
```

## 网络层

- Retrofit 定义 REST API
- OkHttp Interceptor 注入 Bearer token
- 401 → 重新登录
- 超时合理设置

## 登录态

SharedPreferences 保存 token 和服务器地址，后续复杂缓存引入 Room。

## 上传下载

- 小文件 Retrofit multipart 上传
- 大文件后续做分片
- 长任务 WorkManager 或 Foreground Service

## 通知

- 上传下载前台通知
- 任务完成/失败更新通知
- 点击进入详情

## 页面

- 登录 / 服务器配置（含连通性检测）→ **MainActivity（底部三栏壳）**
  - **文件**：`FilesFragment`（列表/上传/下载/删除/隐私文件夹/管理员用户切换）
  - **随手记**：`NotesFragment`（占位，开发中）
  - **我的**：`MineFragment`（用户信息、传输任务、服务器配置、退出登录）
- 传输任务为独立 `TransferListActivity`，从「我的」进入

## 失败处理

网络不可用提示、token 失效跳登录、失败保留任务和错误原因

## 规划：随手记（分阶段）

随手记笔记的 Android 端（Phase 2 在线列表/查看/编辑 → Phase 3 Room 离线缓存 + `updatedAfter` 增量同步 + outbox 后写覆盖）尚未实施，详见 `docs/07-quick-notes.md`。当前客户端仍为文件传输 MVP。
