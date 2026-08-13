package com.baiflow.android.data;

import android.util.Log;

import com.baiflow.android.model.ApiResponse;
import com.baiflow.android.model.NoteProgress;
import com.baiflow.android.network.ApiClient;

import java.util.Map;

import retrofit2.Response;

/**
 * 浏览进度上报工具 — 与 Web 共用同一份数据（每用户 × 每内容一条）。
 * <p>
 * 文件进度 {@code positionType} = SECONDS（视频/音频）/ SCROLL_PERCENT（文本/Markdown）；
 * 笔记进度固定 SCROLL_PERCENT（0~1）。进度按用户存，不记录设备信息。
 * 全部尽力而为：fetch 失败返回 0，save 异步静默，不影响预览/编辑主流程。
 */
public final class ProgressReporter {

    public static final String TYPE_SECONDS = "SECONDS";
    public static final String TYPE_SCROLL_PERCENT = "SCROLL_PERCENT";

    private static final String TAG = "Progress";

    private ProgressReporter() {
    }

    /** 拉取文件进度（同步，须在后台线程调用）；无记录或失败返回 0 */
    public static double fetchFileProgress(ApiClient client, String fileId) {
        try {
            Response<ApiResponse<Map<String, Object>>> resp = client.getProgress(fileId).execute();
            Log.d(TAG, "GET /files/" + fileId + "/progress -> " + resp.code()
                    + " ok=" + (resp.body() != null && resp.body().isOk()));
            if (resp.isSuccessful() && resp.body() != null && resp.body().isOk()
                    && resp.body().getData() != null) {
                Object v = resp.body().getData().get("positionValue");
                return v instanceof Number ? ((Number) v).doubleValue() : 0;
            }
        } catch (Exception e) {
            Log.w(TAG, "fetch file progress failed", e);
        }
        return 0;
    }

    /** 保存文件进度（异步，失败静默） */
    public static void saveFileProgress(ApiClient client, String fileId, String positionType, double value) {
        new Thread(() -> {
            try {
                Response<ApiResponse<Map<String, Object>>> resp =
                        client.saveProgress(fileId, positionType, value).execute();
                Log.d(TAG, "PUT /files/" + fileId + "/progress (" + positionType + "=" + value + ") -> "
                        + resp.code() + " ok=" + (resp.body() != null && resp.body().isOk()));
            } catch (Exception e) {
                Log.w(TAG, "save file progress failed, file=" + fileId, e);
            }
        }).start();
    }

    /** 拉取笔记进度（同步，须在后台线程调用）；无记录或失败返回 0 */
    public static double fetchNoteProgress(ApiClient client, String noteId) {
        try {
            Response<ApiResponse<NoteProgress>> resp = client.getNoteProgress(noteId).execute();
            Log.d(TAG, "GET /notes/" + noteId + "/progress -> " + resp.code()
                    + " ok=" + (resp.body() != null && resp.body().isOk()));
            if (resp.isSuccessful() && resp.body() != null && resp.body().isOk()
                    && resp.body().getData() != null) {
                return resp.body().getData().getPositionValue();
            }
        } catch (Exception e) {
            Log.w(TAG, "fetch note progress failed", e);
        }
        return 0;
    }

    /** 保存笔记进度（异步，失败静默） */
    public static void saveNoteProgress(ApiClient client, String noteId, double pct) {
        new Thread(() -> {
            try {
                Response<ApiResponse<Map<String, Object>>> resp =
                        client.saveNoteProgress(noteId, pct).execute();
                Log.d(TAG, "PUT /notes/" + noteId + "/progress (" + pct + ") -> "
                        + resp.code() + " ok=" + (resp.body() != null && resp.body().isOk()));
            } catch (Exception e) {
                Log.w(TAG, "save note progress failed, note=" + noteId, e);
            }
        }).start();
    }
}
