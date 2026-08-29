# ADR-001：笔记媒体缓存管理（手动清理 + 上限滑块 + 同步后 LRU）

**状态**：已接受 · **日期**：2026-08-30

## 背景（Context）

Android 笔记媒体缓存（`filesDir/note_media_cache/<mediaId>`）**只增不减、从不清理**：登出/清分区只删 Room 笔记行，服务端软删除后媒体成孤儿也不回收。长期使用会占满设备内部存储。离线阅读依赖该缓存（`MediaFiles.resolveLocal` 有缓存用缓存、无则回网络），清除后离线打开笔记/媒体需重新下载。

## 决策（Decision）

1. **清理范围仅服务端媒体缓存** `note_media_cache/`。**不动** `note_media/`（离线新建媒体，正文 `local://` 引用，可能含未上传数据，清了会丢）。
2. **「我的」页新增「存储」分组**（同步分组上方）：
   - **清理缓存**行：左「清理缓存」+ 右侧缓存总大小（`FormatUtil.formatSize`）；点击弹 `MaterialAlertDialog` 二次确认（文案：「将清除已下载的笔记媒体（图片/音频）。清除后，离线打开相应笔记或媒体时需要重新下载。此操作不可恢复，确定继续？」）→ 确认后清空缓存目录并刷新大小。
   - **缓存上限**行：左「缓存上限」+ 右侧当前值（如 `300MB`）；点击弹窗内 `SeekBar`（**50–2000MB，步进 50，默认 300MB**，拖动实时显示），确定保存。
3. **自动清理（LRU）**：`syncOnce` 写完媒体缓存后调用 `MediaFiles.enforceLimit(context)`——读上限，`note_media_cache/` 总大小超限则按 `lastModified` 删最旧文件直到 ≤ 上限。上限存 `SessionManager`（SharedPreferences，`KEY_CACHE_LIMIT_MB`，默认 300）。
4. 上限持久化：SharedPreferences（与语言等设置同级）。

## 后果（Consequences）

- ✅ 缓存有界：手动可清 + 自动 LRU 防无限增长。
- ✅ 零数据丢失：仅清可重新下载的服务端媒体缓存，离线新建媒体不受影响。
- ⚠️ 清理后离线打开相应笔记媒体需重新下载（下次在线同步自动补拉）。
- ⚠️ LRU 按文件最后修改时间（`lastModified`）排序，同一批同步写入的文件时间接近，淘汰顺序近似随机——对预览/阅读场景足够。
- ⚠️ `note_media/`（离线新建未上传媒体）无自动清理，属于「数据不删」的取舍。

## 相关

- `docs/05-android.md`（同步/媒体）、`MediaFiles.java`、`MineFragment`、`SyncService`
