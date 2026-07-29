# Android 客户端设计

## 技术栈

Java + Retrofit + OkHttp + WorkManager + Foreground Service + SharedPreferences

## MVP 功能

- 登录、文件列表、上传手机文件、下载服务器文件、查看传输状态
- 长任务前台通知

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

登录 → 服务器配置 → 文件列表 → 上传/下载 → 传输任务 → 设置

## 失败处理

网络不可用提示、token 失效跳登录、失败保留任务和错误原因
