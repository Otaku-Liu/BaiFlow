# 08 · 品牌资产：App 图标与 Web Logo

> 状态：已落地
> 相关：`docs/04-frontend.md`、`docs/05-android.md`

品牌资产复用品牌图经图标生成器导出的多平台套件（Android legacy/round mipmap、Web favicon 等），不重绘；项目内只补齐生成器缺失的 Android adaptive icon、透明 mark 与 Manifest/HTML 接线。

## 1. 设计要点

- **复用生成器产物**（Android legacy/round mipmap、Web favicon），不重绘。
- 本项目只补生成器缺失的三项：
  1. **Android adaptive icon**（`mipmap-anydpi-v26` + 透明前景）；
  2. **透明 mark**（`logo-mark.png`，顶栏用）；
  3. **Manifest / HTML 接线**。
- 权威 mark 源 = **去背景后的透明 PNG**（备份在 `docs/assets/brand/mark-source.png`），派生 adaptive 前景与 `logo-mark.png`。
- 不接入 iOS/macOS/tvOS/watchOS 资产（本项目只有 Android + Web）。

## 2. 调色板

| 角色 | 色值 |
|---|---|
| 主蓝 | `#3090F8` |
| 浅蓝 | `#D8F0FF` |
| 背景 | `#FFFFFF` |

## 3. Android

`baiflow-android/app/src/main/res/`：

| 资源 | 说明 |
|---|---|
| `mipmap-{mdpi..xxxhdpi}/ic_launcher.png` | legacy 图标（API<26） |
| 同目录 `ic_launcher_round.png` | 圆形图标 |
| 同目录 `ic_launcher_foreground.png` | 透明前景，mark 占画布 55% 居中（派生） |
| `mipmap-anydpi-v26/ic_launcher{,_round}.xml` | adaptive icon：白底 + 前景（API 26+） |
| `values/colors.xml` | `ic_launcher_background=#FFFFFF` |

`AndroidManifest.xml` 已配置 `android:icon` / `android:roundIcon` → `@mipmap/ic_launcher`。

## 4. Web

`baiflow-web/public/brand/`：

| 文件 | 用途 |
|---|---|
| `logo-mark.png` | 顶栏透明 mark（派生） |
| `logo-icon.png` | 登录页 app 图标卡片（派生） |
| `favicon.ico` | 浏览器标签页 |
| `apple-touch-icon.png` | iOS 添加到主屏 |
| `og.png` | 社交分享预览 |

接线：`index.html`（favicon + apple-touch-icon + theme-color + OG）、`LoginView.vue`（登录卡片）、`HomeView.vue`（顶栏）。

## 5. 派生规则

透明 mark 源按用途缩放居中：

- adaptive 前景：mark 宽 = 画布 **55%**（安全区内不裁切）；
- `logo-mark.png` / `logo-icon.png`：mark 占满画布。

生成脚本为一次性工具，未入库。

## 6. 待办

- Play Store 512px 图标在 `docs/assets/brand/play_store_512.png`，上架时取用。
