package com.baiflow.android.data;

import android.content.Context;

import com.baiflow.android.auth.SessionManager;
import com.baiflow.android.model.ApiResponse;
import com.baiflow.android.model.NoteDetail;
import com.baiflow.android.model.NoteSummary;
import com.baiflow.android.model.PagedResult;
import com.baiflow.android.network.ApiClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * 笔记同步引擎（Room ↔ 服务器）。
 * <p>
 * - pushOutbox：tombstone 删除 + dirty 笔记 create/update（带 baseUpdatedAt，冲突标记不丢数据）；
 * - pull：updatedAfter 增量拉取（含软删除标记），合并本地镜像，缓存服务端媒体；
 * - 首次同步全量拉取；仅在线模式执行，离线/本地模式不跑。
 * 见 docs/05-android.md「离线三态」。
 */
public final class SyncService {

    /** 正文中的服务端媒体引用 /api/notes/media/{id} */
    private static final Pattern SERVER_MEDIA = Pattern.compile("/api/notes/media/([0-9a-zA-Z_-]+)");
    /** 离线新建媒体的本地引用 local://fileName（? 后为 mediaType 等查询参数，不纳入文件名） */
    private static final Pattern LOCAL_MEDIA = Pattern.compile("local://([^)\"'\\s?]+)");

    /** 笔记来源常量（替代魔法字符串） */
    public static final String SOURCE_LOCAL_ONLY = "LOCAL_ONLY";
    public static final String SOURCE_SYNCED = "SYNCED";
    public static final String SOURCE_TOMBSTONE = "TOMBSTONE";

    /** 同步互斥：WorkManager 周期与手动/登录触发的立即同步不并发跑，避免 Room/lastSyncAt 竞态 */
    private static final java.util.concurrent.atomic.AtomicBoolean SYNCING =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private SyncService() {}

    /** 同步一次（后台线程调用）。非在线模式或已在同步中返回 false；网络失败返回 false（保持 dirty 下次重试）。 */
    public static boolean syncOnce(Context context) {
        SessionManager session = SessionManager.getInstance(context);
        if (!session.isOnlineMode()) return false;
        if (!SYNCING.compareAndSet(false, true)) return false;
        try {
            ApiClient client = ApiClient.getInstance(session);
            LocalNoteDao dao = AppDatabase.get(context).noteDao();
            String partition = session.getDataPartition();
            pushOutbox(context, client, dao, partition);
            pull(context, session, client, dao, partition);
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            SYNCING.set(false);
        }
    }

    /** 是否还有待上传的本地模式笔记（首次登录「上传前询问」） */
    public static boolean hasLocalOnlyNotes(Context context) {
        return AppDatabase.get(context).noteDao().countLocalOnly() > 0;
    }

    /** 把本地模式笔记迁移到服务器分区（用户确认上传后调用），由后续同步推送到服务端 */
    public static void migrateLocalNotes(Context context) {
        SessionManager session = SessionManager.getInstance(context);
        if (session.isLocalMode()) return;
        LocalNoteDao dao = AppDatabase.get(context).noteDao();
        String partition = session.getDataPartition();
        for (LocalNote n : dao.listLocalOnly()) {
            n.serverUrl = partition;
            n.dirty = true;
            dao.update(n);
        }
    }

    // ---- 推 outbox ----

    private static void pushOutbox(Context context, ApiClient client, LocalNoteDao dao,
                                   String partition) throws IOException {
        // 1. tombstone → DELETE serverId
        for (LocalNote t : dao.listTombstones(partition)) {
            if (t.serverId != null) {
                client.deleteNote(t.serverId).execute();
            }
            dao.delete(t);
        }
        // 2. dirty 笔记 → create / update（服务端已删则重建为新笔记，避免死循环）
        for (LocalNote n : dao.listDirty(partition)) {
            if (n.source != null && SOURCE_TOMBSTONE.equals(n.source)) continue;
            uploadLocalMedia(context, client, n);
            boolean needCreate = n.serverId == null;
            if (!needCreate) {
                Response<ApiResponse<NoteDetail>> resp =
                        client.updateNote(n.serverId, n.title, n.content, n.baseUpdatedAt).execute();
                if (resp.isSuccessful() && resp.body() != null && resp.body().isOk()) {
                    NoteDetail d = resp.body().getData();
                    n.baseUpdatedAt = d != null && d.getUpdatedAt() != null ? d.getUpdatedAt() : n.baseUpdatedAt;
                    n.dirty = false;
                    n.conflict = false;
                    dao.update(n);
                } else if (resp.isSuccessful() && resp.body() != null
                        && resp.body().getCode() == 40901) {
                    // 冲突：标记并保留本地改动；用户打开时选「覆盖」(清 base) 或「重载」(拉服务端)
                    n.conflict = true;
                    dao.update(n);
                } else if (resp.isSuccessful() && resp.body() != null
                        && resp.body().getCode() == 40401) {
                    // 服务端已删该笔记：清 serverId 转新建，本地内容保留为新笔记
                    n.serverId = null;
                    needCreate = true;
                }
                // 其它错误（网络/4xx）：保持 dirty 下次重试
            }
            if (needCreate) {
                Response<ApiResponse<NoteDetail>> resp = client.createNote(n.title, n.content).execute();
                if (resp.isSuccessful() && resp.body() != null && resp.body().isOk()
                        && resp.body().getData() != null) {
                    NoteDetail d = resp.body().getData();
                    n.serverId = d.getId();
                    n.baseUpdatedAt = d.getUpdatedAt();
                    n.source = SOURCE_SYNCED;
                    n.dirty = false;
                    n.conflict = false;
                    dao.update(n);
                }
            }
        }
    }

    // ---- 拉增量 ----

    private static void pull(Context context, SessionManager session, ApiClient client,
                             LocalNoteDao dao, String partition) throws IOException {
        String since = session.getLastSyncAt();
        boolean hasSince = since != null && !since.isEmpty();
        // 本地镜像为空时增量游标无意义（登出清缓存等场景会留旧 lastSyncAt），强制全量重拉，
        // 否则增量 updatedAfter=旧时间 会漏掉更早的笔记，Room 永远为空
        if (hasSince && dao.countSynced(partition) == 0) {
            hasSince = false;
        }
        String sinceIso = hasSince ? since : "1970-01-01T00:00:00";
        LocalDateTime latest = parse(sinceIso, LocalDateTime.of(1970, 1, 1, 0, 0));

        int page = 1;
        while (true) {
            Response<ApiResponse<PagedResult<NoteSummary>>> resp =
                    client.listNotesSince(session.getUserId(), sinceIso, page, 200).execute();
            if (!resp.isSuccessful() || resp.body() == null || !resp.body().isOk()
                    || resp.body().getData() == null) break;
            List<NoteSummary> list = resp.body().getData().getRecords();
            if (list == null || list.isEmpty()) break;
            for (NoteSummary s : list) {
                LocalDateTime st = parse(s.getUpdatedAt(), null);
                if (st != null && st.isAfter(latest)) latest = st;
                applySummary(context, client, dao, partition, s);
            }
            if (list.size() < 200) break;
            page++;
        }
        session.saveLastSyncAt(latest.toString());
    }

    private static void applySummary(Context context, ApiClient client, LocalNoteDao dao,
                                     String partition, NoteSummary s) throws IOException {
        LocalNote local = dao.getByServerId(s.getId(), partition);
        if ("DELETED".equals(s.getStatus())) {
            // 服务端已删：本地无未同步改动则移除；有改动则保留（推送时重建/更新）
            if (local != null && !local.dirty) {
                dao.delete(local);
            }
            return;
        }
        if (local != null && local.dirty) {
            // 本地有待推改动：以本地为准；服务端比本地基准新 → 冲突标记
            if (local.baseUpdatedAt != null && s.getUpdatedAt() != null
                    && s.getUpdatedAt().compareTo(local.baseUpdatedAt) > 0) {
                local.conflict = true;
                dao.update(local);
            }
            return;
        }
        // 拉详情（含正文）合并
        Response<ApiResponse<NoteDetail>> resp = client.getNote(s.getId()).execute();
        if (!resp.isSuccessful() || resp.body() == null || !resp.body().isOk()
                || resp.body().getData() == null) return;
        NoteDetail d = resp.body().getData();
        if (local == null) {
            LocalNote n = new LocalNote();
            n.serverId = d.getId();
            n.serverUrl = partition;
            n.title = d.getTitle() != null ? d.getTitle() : "";
            n.content = d.getContent() != null ? d.getContent() : "";
            n.baseUpdatedAt = d.getUpdatedAt();
            n.source = SOURCE_SYNCED;
            n.createdAt = System.currentTimeMillis();
            n.updatedAt = System.currentTimeMillis();
            n.dirty = false;
            dao.insert(n);
        } else {
            local.title = d.getTitle() != null ? d.getTitle() : "";
            local.content = d.getContent() != null ? d.getContent() : "";
            local.baseUpdatedAt = d.getUpdatedAt();
            local.updatedAt = System.currentTimeMillis();
            local.conflict = false;
            dao.update(local);
        }
        cacheMedia(context, client, d.getContent());
    }

    /** 缓存正文中的服务端媒体到本地（离线可读），失败不影响笔记同步 */
    private static void cacheMedia(Context context, ApiClient client, String content) {
        if (content == null) return;
        Matcher m = SERVER_MEDIA.matcher(content);
        while (m.find()) {
            String mediaId = m.group(1);
            File f = MediaFiles.cachedMediaFile(context, mediaId);
            if (f == null || f.exists()) continue;
            try {
                Response<ResponseBody> resp = client.getNoteMedia(mediaId).execute();
                if (resp.isSuccessful() && resp.body() != null) {
                    try (ResponseBody body = resp.body();
                         java.io.InputStream in = body.byteStream();
                         FileOutputStream out = new FileOutputStream(f)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                    }
                }
            } catch (IOException ignored) {
            }
        }
    }

    // ---- 离线上传媒体 ----

    /** 上传 dirty 笔记正文里 local:// 引用的离线媒体，并把 markdown 改写为服务端 URL */
    private static void uploadLocalMedia(Context context, ApiClient client, LocalNote n) throws IOException {
        String content = n.content;
        if (content == null || !content.contains("local://")) return;
        Matcher m = LOCAL_MEDIA.matcher(content);
        while (m.find()) {
            String fileName = m.group(1);
            File f = new File(MediaFiles.localMediaDir(context), fileName);
            if (!f.exists()) continue;
            byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
            boolean audio = fileName.endsWith(".m4a") || fileName.endsWith(".mp3");
            String mediaType = audio ? "AUDIO" : "IMAGE";
            String mime = audio ? "audio/mp4" : "image/jpeg";
            Response<ApiResponse<com.baiflow.android.model.NoteMedia>> resp =
                    client.uploadNoteMedia(mediaType, bytes, fileName, mime).execute();
            if (resp.isSuccessful() && resp.body() != null && resp.body().isOk()
                    && resp.body().getData() != null) {
                String url = resp.body().getData().getUrl();
                content = content.replace("local://" + fileName, url);
                // 不删除本地文件：push 失败时仍能重试；且若编辑器未关闭再次保存，local:// 仍可解析
            }
        }
        n.content = content;
    }

    private static LocalDateTime parse(String s, LocalDateTime fallback) {
        if (s == null || s.isEmpty()) return fallback;
        try {
            return LocalDateTime.parse(s);
        } catch (Exception e) {
            return fallback;
        }
    }
}
