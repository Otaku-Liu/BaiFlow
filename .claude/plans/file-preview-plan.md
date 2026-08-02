# Plan: 浏览器在线预览 + 跨设备断点续看

## Grilling 决策

| 决策 | 结论 |
|---|---|
| 预览入口 | 操作列「预览」按钮 + 双击文件 |
| 进度提示 | Toast 轻提示「上次看到 XX:XX，点击跳转」，不点则 5 秒消失从头播 |
| 保存频率 | 播放中每 10 秒保存，暂停/关闭时立即最终保存 |
| 进度粒度 | 统一进度表，字段：`position_value`(DOUBLE) + `position_type`(枚举)。视频/音频存秒数，PDF 存页码，文本/Markdown 存滚动百分比。 |

---

## 涉及文件清单

### 后端

| # | 文件 | 操作 | 说明 |
|---|---|---|---|
| 1 | `.../file/entity/PlaybackProgress.java` | **新建** | 播放进度实体 |
| 2 | `db/migration/V2__playback_progress.sql` | **新建** | Flyway 建表 |
| 3 | `.../file/mapper/PlaybackProgressMapper.java` | **新建** | MyBatis-Plus Mapper |
| 4 | `.../file/service/FileService.java` | 修改 | 添加 preview / progress 方法签名 |
| 5 | `.../file/service/impl/FileServiceImpl.java` | 修改 | 实现 preview 流、get/save progress |
| 6 | `.../file/controller/FileController.java` | 修改 | 添加 3 个新端点 |

### 前端

| # | 文件 | 操作 | 说明 |
|---|---|---|---|
| 7 | `src/components/PreviewDrawer.vue` | **新建** | 预览抽屉，按 MIME 切换渲染器 |
| 8 | `src/composables/usePlaybackProgress.js` | **新建** | 进度读写 + 定时保存逻辑 |
| 9 | `src/api/files.js` | 修改 | 添加 preview / progress API |
| 10 | `src/views/FilesView.vue` | 修改 | 加预览按钮 + 双击事件 + 引入 PreviewDrawer |
| 11 | `src/views/LoginLogsView.vue` | 不变 | — |

---

## 1. 数据库

```sql
CREATE TABLE IF NOT EXISTS bf_playback_progress (
    id             VARCHAR(32)  NOT NULL,
    user_id        VARCHAR(32)  NOT NULL,
    file_item_id   VARCHAR(32)  NOT NULL,
    position_type  VARCHAR(16)  NOT NULL DEFAULT 'SECONDS',
    position_value DOUBLE       NOT NULL DEFAULT 0,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_file (user_id, file_item_id)
);
```

`position_type` 取值：

| 值 | 含义 | 适用类型 |
|---|---|---|
| `SECONDS` | 播放秒数 | 视频、音频 |
| `PAGE` | 页码 | PDF |
| `SCROLL_PERCENT` | 滚动百分比（0.0~1.0） | Markdown、文本、代码 |

## 2. API 详细设计

### 2.1 GET /api/files/{id}/preview

- 行为同 `download`，但 `Content-Disposition: inline` 而非 `attachment`
- 支持 `Range` 请求头（HTTP 206 Partial Content），用于视频 seek
- 同样校验认证 + 隐私文件夹令牌

### 2.2 GET /api/files/{id}/progress

响应：
```json
{
  "code": "OK",
  "data": {
    "fileId": "xxx",
    "positionType": "SECONDS",
    "positionValue": 735.2,
    "updatedAt": "2026-08-02T12:30:00"
  }
}
```
无记录返回 `data: null`。

### 2.3 PUT /api/files/{id}/progress

请求体：`{ "positionType": "SECONDS", "positionValue": 735.2 }`
       或 `{ "positionType": "PAGE", "positionValue": 12 }`
       或 `{ "positionType": "SCROLL_PERCENT", "positionValue": 0.45 }`

upsert 逻辑：有则更新，无则插入。按 `(user_id, file_item_id)` 唯一键 upsert。

## 3. 前端组件

### 3.1 PreviewDrawer.vue

```
Props: fileItem (FileItemInfo), visible (Boolean)
Emits: close

结构:
┌─ el-drawer (size="75%", direction="rtl") ───────────┐
│  header: 文件名                                       │
│  ┌──────────────────────────────────────────────────┐ │
│  │  MIME 路由:                                      │ │
│  │  image/*   → <img :src="previewUrl">             │ │
│  │  video/*   → <video ref controls                │ │
│  │               @timeupdate ...>  进度: SECONDS    │ │
│  │  audio/*   → <audio ref controls ...>            │ │
│  │               @timeupdate ...>  进度: SECONDS    │ │
│  │  app/pdf   → <iframe :src="previewUrl"           │ │
│  │               @message 监听页码> 进度: PAGE      │ │
│  │  text/*    → <pre ref @scroll ...>               │ │
│  │              进度: SCROLL_PERCENT                │ │
│  │  default   → <el-empty description="暂不支持预览">│ │
│  └──────────────────────────────────────────────────┘ │
│  footer: 文件大小 | Toast 进度提示                     │
└──────────────────────────────────────────────────────┘
```

### 3.2 usePlaybackProgress.js

```js
// 用法：
//   视频/音频: const { checkAndSeek, startAutoSave, saveNow } = usePlaybackProgress(fileId, 'SECONDS', mediaRef)
//   PDF:       const { checkAndSeek, startAutoSave, saveNow } = usePlaybackProgress(fileId, 'PAGE', pageRef)
//   文本/MD:   const { checkAndSeek, startAutoSave, saveNow } = usePlaybackProgress(fileId, 'SCROLL_PERCENT', scrollRef)
//
// checkAndSeek():
//   1. GET /progress → 如果有进度 → showToast("上次看到 第12页 / 12:30 / 45%", action: "跳转")
//   2. 用户点击 toast action → 根据 type 设置 video.currentTime / PDF.pageNumber / div.scrollTop
//
// startAutoSave():
//   SECONDS:       @timeupdate 每 10s → PUT /progress({ positionType: 'SECONDS', positionValue: currentTime })
//   PAGE:          @pagechange → PUT /progress({ positionType: 'PAGE', positionValue: pageNumber })
//   SCROLL_PERCENT: @scroll 防抖 2s → PUT /progress({ positionType: 'SCROLL_PERCENT', positionValue: scrollTop/scrollHeight })
//
// saveNow():
//   立即 PUT /progress（暂停/关闭/抽屉关闭时调用）
```

### 3.3 FilesView 改动

- 操作列加 `<el-button @click="showPreview(row)">预览</el-button>`
- 表格加 `@row-dblclick="onRowDblClick"`（文件双击预览，文件夹双击进入）
- 底部引入 `<PreviewDrawer :file-item="previewFile" :visible="previewVisible" @close="previewVisible=false" />`

## 4. 安全

- `GET /api/files/{id}/preview` 复用现有认证 + 隐私文件夹校验
- `GET/PUT /api/files/{id}/progress` 同样校验（不能看别人的进度，也不能写别人的进度）
- 路径穿越校验：preview 和 download 走同一套 `resolveRootPath` + `verifyPathInRoot`

## 5. 分阶段实现

### 阶段一（立即实施）：浏览器原生预览 + 断点续看

| # | 文件 | 操作 | 说明 |
|---|---|---|---|
| 1 | `db/migration/V2__playback_progress.sql` | **新建** | Flyway 建表 |
| 2 | `PlaybackProgress.java` | **新建** | 实体 |
| 3 | `PlaybackProgressMapper.java` | **新建** | Mapper |
| 4 | `FileService.java` | 修改 | 加 preview / progress 方法签名 |
| 5 | `FileServiceImpl.java` | 修改 | 实现 preview 流(Range 支持) + progress 读写 |
| 6 | `FileController.java` | 修改 | `GET preview` `GET progress` `PUT progress` |
| 7 | `src/api/files.js` | 修改 | 前端 API 封装 |
| 8 | `src/utils/mime.js` | **新建** | 扩展名→MIME 映射表 |
| 9 | `src/composables/usePlaybackProgress.js` | **新建** | 进度 Toast + 自动保存 |
| 10 | `src/components/PreviewDrawer.vue` | **新建** | MIME 路由渲染器 |
| 11 | `src/views/FilesView.vue` | 修改 | 预览按钮 + 双击 + Drawer 集成 |

**阶段一支持的格式**：图片(JPEG/PNG/GIF/WebP/SVG/BMP/AVIF/ICO)、视频(MP4/WebM/OGG/3GP)、音频(MP3/WAV/OGG/FLAC/AAC/M4A/OPUS)、PDF、纯文本/代码/JSON/CSV/XML/YAML/Markdown、XLSX(SheetJS)、ZIP(目录树)

### 阶段二（后续）：服务端转换预览

| # | 内容 | 说明 |
|---|---|---|
| 12 | Docker 镜像加装 `libreoffice-headless` + `ffmpeg` | 修改 `deploy/Dockerfile` |
| 13 | `FileConvertService.java` | **新建** — LibreOffice 转 PDF、FFmpeg remux/transcode |
| 14 | `GET /api/files/{id}/preview?convert=true` | Controller 加转换参数 |
| 15 | 转码缓存目录 | `storage-root/.cache/preview/` |

**阶段二新增支持的格式**：DOCX/DOC/PPTX/PPT/XLS/ODT/ODS/ODP(→PDF)、AVI/MKV/MOV(→MP4)、TAR.GZ(目录树)

### 阶段三（远期）：全量转码

| # | 内容 | 说明 |
|---|---|---|
| 16 | FFmpeg 全编码转码 | WMV/FLV/TS → MP4 |
| 17 | EPUB 预览 | 解压 HTML → 渲染 |
| 18 | 代码高亮 | highlight.js / Prism.js |
| 19 | 音频波形可视化 | Web Audio API |

## 6. 文件类型预览方案详表

### 6.1 图片

| 格式 | MIME | 预览方式 | 兼容性 | 备注 |
|---|---|---|---|---|
| JPEG | `image/jpeg` | `<img :src="previewUrl">` | ✅ 所有浏览器 | — |
| PNG | `image/png` | `<img>` | ✅ | 透明通道正常 |
| GIF | `image/gif` | `<img>` | ✅ | 动图自动播放 |
| WebP | `image/webp` | `<img>` | ✅ 现代浏览器 | Safari 14+ 支持 |
| SVG | `image/svg+xml` | `<img>` / 内联 `<div v-html>` | ✅ | 内联可做缩放交互；需 XSS 清洗 |
| BMP | `image/bmp` | `<img>` | ✅ | 无压缩，大文件加载慢 |
| TIFF | `image/tiff` | ❌ 不支持 | — | 浏览器均不支持，提示下载 |
| HEIC/HEIF | `image/heic` | ❌ 不支持 | — | iOS 拍摄格式，Safari 17+ 开始支持，Chrome 不支持 |
| AVIF | `image/avif` | `<img>` | ✅ Chrome/Firefox | Safari 16+ 支持 |
| ICO | `image/x-icon` | `<img>` | ✅ | favicon |

**图片额外能力**：支持滚轮缩放 + 拖拽平移。不做进度追踪。

### 6.2 视频

| 格式 | MIME | 阶段 | 预览方式 | 兼容性 |
|---|---|---|---|---|
| MP4 (H.264/AAC) | `video/mp4` | 阶段一 | `<video controls playsinline>` | ✅ 所有浏览器 |
| WebM (VP8/VP9) | `video/webm` | 阶段一 | `<video>` | Chrome/Firefox/Edge |
| OGG/Theora | `video/ogg` | 阶段一 | `<video>` | Chrome/Firefox/Edge |
| AVI | `video/x-msvideo` | 阶段二 | FFmpeg remux → MP4 → `<video>` | 仅需重封装（秒级），编码不兼容时转码 |
| MKV | `video/x-matroska` | 阶段二 | 同上 | 容器内通常已是 H.264，换容器即可播放 |
| MOV | `video/quicktime` | 阶段二 | 同上 | 同上 |
| WMV | `video/x-ms-wmv` | 阶段三 | FFmpeg 转码 → MP4 | 编码通常不兼容 |
| FLV | `video/x-flv` | 阶段三 | 同上 | 同上 |
| TS | `video/mp2t` | 阶段三 | 同上 | 同上 |
| 3GP | `video/3gpp` | 阶段一 | `<video>` | ⚠️ 部分 |

**阶段二处理流程**：
```
AVI/MKV/MOV 文件
      │
      ▼
  检测容器内编码 (ffprobe)
      │
      ├── 已是 H.264/AAC → ffmpeg -c copy remux → MP4（秒级，无质量损失）
      │
      └── 不兼容编码 → ffmpeg 转码 H.264/AAC → MP4（CPU 开销，异步处理）
                          │
                          ▼
                    首次转码后缓存，后续请求直接返回缓存
```
要求：Docker 镜像中预装 `ffmpeg`。

**视频额外能力**：
- Range 请求支持拖拽 seek
- 进度追踪（10s 自动保存）
- `playsinline` 避免 iOS 强制全屏
- 快捷键：空格暂停、← → 前进后退 5s、F 全屏

### 6.3 音频

| 格式 | MIME | 预览方式 | 兼容性 | 备注 |
|---|---|---|---|---|
| MP3 | `audio/mpeg` | `<audio controls>` | ✅ 所有浏览器 | — |
| WAV | `audio/wav` | `<audio>` | ✅ | 无压缩，文件较大 |
| OGG/Vorbis | `audio/ogg` | `<audio>` | ✅ Chrome/Firefox | Safari 不支持 |
| FLAC | `audio/flac` | `<audio>` | ✅ Chrome/Firefox/Edge | Safari 不支持 |
| AAC | `audio/aac` | `<audio>` | ✅ | M4A 容器内常见 |
| M4A | `audio/mp4` | `<audio>` | ✅ | — |
| WMA | `audio/x-ms-wma` | ❌ 不支持 | — | 仅 IE 支持 |
| OPUS | `audio/opus` | `<audio>` | ✅ Chrome/Firefox | Safari 不支持 |

**音频额外能力**：
- 进度追踪（10s 自动保存）
- 可视化：简单的波形/频谱（可选，后续迭代）

### 6.4 文档

| 格式 | MIME | 预览方式 | 说明 |
|---|---|---|---|
| PDF | `application/pdf` | `<iframe :src="previewUrl">` | 浏览器自带 PDF 查看器，零依赖。支持缩放、搜索、翻页、打印。 |
| TXT | `text/plain` | fetch 文本内容 → `<pre>` | 支持 GBK/UTF-8 自动检测 |
| CSV | `text/csv` | fetch 文本内容 → 简易表格 | 前 500 行渲染为 `<el-table>`，超出截断提示下载查看 |
| JSON | `application/json` | fetch → `<pre>` 语法高亮 | `JSON.stringify(data, null, 2)` 格式化展示 |
| XML | `text/xml`, `application/xml` | fetch → `<pre>` 语法高亮 | 同 JSON |
| Markdown (.md) | `text/markdown` | fetch → marked.js 渲染 | 可选引入 marked.js（~20KB），实时渲染为 HTML |
| YAML | `text/yaml` | fetch → `<pre>` | — |
| LOG | `text/plain` | fetch → `<pre>` | 同 TXT |

### 6.5 代码文件

| 扩展名 | 语言 | 预览方式 |
|---|---|---|
| `.js` `.ts` `.jsx` `.tsx` | JavaScript/TypeScript | fetch → `<pre>` |
| `.py` | Python | fetch → `<pre>` |
| `.java` `.kt` | Java/Kotlin | fetch → `<pre>` |
| `.go` | Go | fetch → `<pre>` |
| `.rs` | Rust | fetch → `<pre>` |
| `.c` `.cpp` `.h` `.hpp` | C/C++ | fetch → `<pre>` |
| `.html` `.htm` | HTML | fetch → `<iframe srcdoc>` 或 `<pre>` |
| `.css` `.scss` `.less` | CSS | fetch → `<pre>` |
| `.sql` | SQL | fetch → `<pre>` |
| `.sh` `.bash` | Shell | fetch → `<pre>` |
| `.env` `.ini` `.cfg` `.toml` | Config | fetch → `<pre>` |
| `.dockerfile` `Dockerfile` | Docker | fetch → `<pre>` |
| `.xml` `.yaml` `.yml` | Data | fetch → `<pre>` |
| `.vue` `.svelte` | Component | fetch → `<pre>` |

**代码文件**：后续可集成代码高亮库（highlight.js 或 Prism.js ~20KB），当前先 `<pre>` 展示原始文本。

### 6.6 Office 文档

| 格式 | 阶段 | 预览方式 | 说明 |
|---|---|---|---|
| `.xlsx` | 阶段一 | SheetJS 前端渲染 → `<el-table>` | 纯前端，~500KB，简单表格效果好 |
| `.docx` | 阶段二 | LibreOffice headless → PDF → `<iframe>` | 排版保真，支持复杂格式 |
| `.pptx` | 阶段二 | 同上 | 逐页转 PDF |
| `.doc` `.xls` `.ppt` | 阶段二 | 同上 | 老格式同样支持 |
| `.odt` `.ods` `.odp` | 阶段二 | 同上 | OpenDocument |

**阶段一（XLSX）**：
```
用户双击 .xlsx
      │
      ▼
  GET /api/files/{id}/preview → 拿到文件 ArrayBuffer
      │
      ▼
  SheetJS (xlsx.js) 前端解析 → 取第一个 Sheet → JSON 数组
      │
      ▼
  渲染为 <el-table>（前 500 行，超出提示下载）
```

**阶段二（所有 Office 格式）**：
```
DOCX/PPTX/XLS/.doc/.ppt 文件
      │
      ▼
  LibreOffice --headless --convert-to pdf
      │
      ▼
  返回 PDF 流 → 浏览器 <iframe> 预览（复用 PDF 渲染器）
```
要求：Docker 镜像中预装 `libreoffice-headless`。

### 6.7 压缩包

| 格式 | 预览方式 | 说明 |
|---|---|---|
| `.zip` | 列出目录树 | 服务端 `java.util.zip` 读取文件列表返回 JSON，前端渲染目录结构。不支持解压预览内部文件。 |
| `.tar` `.gz` `.bz2` `.xz` | ❌ 暂不支持 | 后续可加 Apache Commons Compress |
| `.rar` `.7z` | ❌ 暂不支持 | 专利/格式限制 |

### 6.8 电子书

| 格式 | 预览方式 | 说明 |
|---|---|---|
| `.epub` | ❌ 暂不支持 | 本质是 ZIP 包，可解压后提取 HTML。后续迭代 |
| `.mobi` | ❌ 暂不支持 | Amazon 格式 |

### 6.9 永久不支持预览的类型

以下文件类型无浏览器渲染路径，统一展示 `<el-empty description="暂不支持在线预览，请下载后查看">`：

- 二进制文件（`application/octet-stream`）
- 可执行文件（`.exe` `.dll` `.so` `.dylib`）
- 磁盘镜像（`.iso` `.dmg` `.img`）
- 数据库文件（`.db` `.sqlite` `.mdb`）
- 字体文件（`.ttf` `.otf` `.woff`）
- TIFF / HEIC 图片
- WMA 音频
- RAR / 7Z 压缩包
- EPUB / MOBI 电子书

---

## 7. 预览请求流程

```
用户双击文件 / 点击预览
        │
        ▼
┌─ 前端判断 MIME ─────────────────────────────────────┐
│                                                       │
│  image/* ──→ <img src="/api/files/{id}/preview">      │
│                                                       │
│  video/* ──→ GET /progress ──→ Toast 续看提示         │
│         ──→ <video src="/api/files/{id}/preview">     │
│         ──→ startAutoSave(每10s)                      │
│                                                       │
│  audio/* ──→ 同上（同 video 逻辑）                    │
│                                                       │
│  app/pdf ──→ <iframe src="/api/files/{id}/preview">   │
│                                                       │
│  text/*  ──→ GET /preview (fetch blob)               │
│  app/json ──→ 读文本内容 ──→ <pre> 展示              │
│                                                       │
│  app/zip  ──→ GET /api/files/{id}/entries            │
│         ──→ 目录树渲染                                │
│                                                       │
│  其他     ──→ <el-empty "暂不支持预览">               │
│                                                       │
└───────────────────────────────────────────────────────┘
```

## 8. MIME 类型判定策略

优先级从高到低：

1. **数据库记录**：`FileItem.mimeType`（上传时通过 `Files.probeContentType` 或 URLConnection 探测）
2. **文件扩展名回退**：数据库 MIME 为空或不准确时，用扩展名映射（如 `.mkv` → `video/x-matroska`）
3. **Tika 探测**（后续优化）：引入 Apache Tika 做内容级 MIME 探测，当前不上

扩展名 → MIME 映射表维护在前端常量中（`src/utils/mime.js`），方便调整。

## 9. 风险/边界

- **大视频**：Range 请求必须正确实现，否则 Safari 无法播放
- **iOS 限制**：`<video>` 在 iOS Safari 上 `controls` 强制全屏，`playsinline` 可解决
- **Android 同步**：Android 端后续加同样的 API 调用即可共享进度
- **并发写入**：`UNIQUE KEY` + `ON DUPLICATE KEY UPDATE` 保证幂等
- **MIME 类型**：`FileItem.mimeType` 由上传时检测，可能存在不准确的情况；preview 时以实际文件扩展名和服务端探测为准
