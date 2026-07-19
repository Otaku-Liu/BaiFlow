# BaiFlow Android 设计

## 技术栈
- Android Java
- Retrofit
- OkHttp
- WorkManager
- Foreground Service
- SharedPreferences
- Room 可选

## MVP 功能
- 登录 BaiFlow 服务器。
- 查看文件列表。
- 上传手机文件到服务器。
- 下载服务器文件到手机。
- 查看传输任务状态。
- 长时间上传和下载显示前台通知。

## 模块建议
```text
baiflow-android/app/src/main/java/
  auth/
  network/
  file/
  transfer/
  notification/
  storage/
  ui/
```

## 网络层
- Retrofit 定义 REST API。
- OkHttp Interceptor 注入 Bearer Token。
- 401 响应触发重新登录。
- 上传下载设置合理超时时间。

## 登录态
第一版使用 SharedPreferences 保存 token 和服务器地址。后续如果缓存复杂数据，再引入 Room。

## 上传下载
- 小文件可直接通过 Retrofit multipart 上传。
- 大文件上传后续再做分片。
- 下载文件时写入 Android 公共下载目录或应用私有目录。
- 长任务使用 WorkManager 或 Foreground Service，避免后台被系统杀掉。

## 通知
- 上传和下载进行中显示前台通知。
- 任务完成、失败更新通知内容。
- 通知点击后进入任务详情页。

## 页面
- 登录页
- 服务器配置页
- 文件列表页
- 上传选择页
- 下载确认页
- 传输任务页
- 设置页

## 失败处理
- 网络不可用时明确提示。
- token 失效时跳转登录。
- 上传下载失败时保留任务记录和错误原因。
- 后续可以添加手动重试。
