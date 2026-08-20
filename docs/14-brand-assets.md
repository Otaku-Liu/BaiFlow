# 14 · 品牌资产：App 图标与 Web Logo（ADR）

> 状态：**已落地**（2026-08-21）
> 类型：架构决策记录（ADR）
> 相关：`docs/04-frontend.md`、`docs/05-android.md`

## 1. 背景

品牌图此前两处空白：Android 无启动图标（系统默认机器人），Web 无 favicon/logo。用户提供品牌图，并用图标生成器导出多平台套件。

## 2. 决策

- **复用生成器产物**（Android legacy/round mipmap、Web favicon），不重绘。
- 本项目只补生成器缺失的三项：
  1. **Android adaptive icon**（`mipmap-anydpi-v26` + 透明前景）；
  2. **透明 mark**（`logo-mark.png`，顶栏用）；
  3. **Manifest / HTML 接线**。
- 权威 mark 源 = **去背景后的透明 PNG**，派生 adaptive 前景与 `logo-mark.png`。
- 不接入 iOS/macOS/tvOS/watchOS 资产（本项目只有 Android + Web）。

## 3. 调色板

| 角色 | 色值 |
|---|---|
| 主蓝 | `#3090F8` |
| 浅蓝 | `#D8F0FF` |
| 背景 | `#FFFFFF` |

## 4. Android

`baiflow-android/app/src/main/res/`：

| 资源 | 说明 |
|---|---|
| `mipmap-{mdpi..xxxhdpi}/ic_launcher.png` | legacy 图标（API<26） |
| 同目录 `ic_launcher_round.png` | 圆形图标 |
| 同目录 `ic_launcher_foreground.png` | 透明前景，mark 占画布 55% 居中（派生） |
| `mipmap-anydpi-v26/ic_launcher{,_round}.xml` | adaptive icon：白底 + 前景（API 26+） |
| `values/colors.xml` | `ic_launcher_background=#FFFFFF` |

`AndroidManifest.xml` 已配置 `android:icon` / `android:roundIcon` → `@mipmap/ic_launcher`。

## 5. Web

`baiflow-web/public/brand/`：

| 文件 | 用途 |
|---|---|
| `logo-mark.png` | 顶栏透明 mark（派生） |
| `logo-icon.png` | 登录页 app 图标卡片（派生） |
| `favicon.ico` | 浏览器标签页 |
| `apple-touch-icon.png` | iOS 添加到主屏 |
| `og.png` | 社交分享预览 |

接线：`index.html`（favicon + apple-touch-icon + theme-color + OG）、`LoginView.vue`（登录卡片）、`HomeView.vue`（顶栏）。

## 6. 派生规则

透明 mark 源按用途缩放居中：

- adaptive 前景：mark 宽 = 画布 **55%**（安全区内不裁切）；
- `logo-mark.png` / `logo-icon.png`：mark 占满画布。

生成脚本为一次性工具，未入库。

## 7. 待办

- Play Store 512px 图标未入库（来源目录），上架时取用。
