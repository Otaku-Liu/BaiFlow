---
name: baiflow-android
description: BaiFlow Android Java 客户端：登录、文件浏览、上传/下载、传输任务、前台通知
---

# BaiFlow Android

## 约束

- Retrofit + OkHttp，Interceptor 注入 token，401 → 重新登录
- 长传输 Foreground Service / WorkManager
- 网络/认证/权限失败明确提示
- 详见 `docs/05-android.md`、`docs/06-coding-standards.md`
