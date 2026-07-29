---
name: baiflow-web
description: BaiFlow Vue 3 Web 管理台：登录、文件中心、下载中心、分享、用户管理、Apple 风格 UI
---

# BaiFlow Web

## 约束

- Composition API + `<script setup>`，API 请求集中 `src/api/`，页面 `src/views/`
- Axios 统一注入 token，401 跳转登录
- 危险操作二次确认，长任务展示 loading/状态

## Store

`authStore`(token/用户) · `fileStore`(根目录/文件列表/隐私令牌) · `transferStore`(传输进度) · `notificationStore`(通知)

## 视觉

Apple 风格（iOS 11-14），Inter 字体，`#007AFF` 主色，侧边栏 `#f2f2f7` 无边框。详见 `docs/04-frontend.md`
