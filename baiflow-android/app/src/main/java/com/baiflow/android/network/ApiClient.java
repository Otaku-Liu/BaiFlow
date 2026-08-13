package com.baiflow.android.network;

import android.os.Build;
import android.util.Log;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import com.baiflow.android.auth.SessionManager;
import com.baiflow.android.model.*;
import okhttp3.*;
import org.json.JSONObject;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * API 客户端 — 单例，管理 Retrofit 实例和所有 API 接口定义。
 * <p>
 * 使用 SessionManager 获取服务器地址和 token。
 * OkHttp 拦截器自动注入 Bearer token，并在 401 时清除会话。
 */
public class ApiClient {

    private static final String TAG = "Http";
    private static ApiClient instance;
    private final SessionManager session;
    private ApiService apiService;
    private String currentBaseUrl;

    /** 连通性探测专用客户端（短超时），供服务器配置页检测 /api/health */
    private final OkHttpClient healthClient = new OkHttpClient.Builder()
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    private ApiClient(SessionManager session) {
        this.session = session;
    }

    public static synchronized ApiClient getInstance(SessionManager session) {
        if (instance == null) { instance = new ApiClient(session); }
        return instance;
    }

    private ApiService getService() {
        String baseUrl = session.getApiBaseUrl();
        if (apiService == null || !baseUrl.equals(currentBaseUrl)) {
            currentBaseUrl = baseUrl;

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(new AuthInterceptor(session))
                    .addInterceptor(new LoggingInterceptor())
                    // connect 10s：连不上/拒连更快反馈；read/write 60s 保留（下载/大响应需要长读）
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .build();

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            apiService = retrofit.create(ApiService.class);
        }
        return apiService;
    }

    /** OkHttp 拦截器：自动注入 Bearer token（长会话）+ 设备标识头，并输出 Http 日志 */
    private static class AuthInterceptor implements Interceptor {
        private static String deviceName;

        private final SessionManager session;
        AuthInterceptor(SessionManager session) { this.session = session; }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request original = chain.request();
            String token = session.getToken();

            // 输出请求日志
            Log.i(TAG, "--> " + original.method() + " " + original.url());
            if (token != null && !token.isEmpty()) {
                Log.d(TAG, "Authorization: Bearer ***");
            }

            Request.Builder builder = original.newBuilder();
            if (token != null && !token.isEmpty()) {
                builder.header("Authorization", "Bearer " + token);
            }
            // 设备标识（登录时服务端据此建会话，供 Web 端设备管理/强制下线）
            builder.header("X-Device-Type", "ANDROID");
            builder.header("X-Device-Name", deviceName());
            // 服务端 i18n：按应用语言偏好发 Accept-Language（未设置时默认 zh-CN）
            LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
            String lang = locales.isEmpty() ? "zh" : locales.get(0).getLanguage();
            builder.header("Accept-Language", "en".equals(lang) ? "en" : "zh-CN");
            Request request = builder.build();

            long start = System.currentTimeMillis();
            Response response = chain.proceed(request);
            long duration = System.currentTimeMillis() - start;

            // 输出响应日志
            Log.i(TAG, "<-- " + response.code() + " " + original.method() + " " + original.url()
                    + " (" + duration + "ms)");

            // 401 时清除会话（长会话被强制下线/过期即回登录）
            if (response.code() == 401) {
                Log.w(TAG, "收到 401，清除会话");
                session.clearSession();
            }
            return response;
        }

        /** 设备名（机型），静态缓存避免每次请求重建 */
        private static String deviceName() {
            if (deviceName == null) {
                String manufacturer = Build.MANUFACTURER;
                String model = Build.MODEL;
                deviceName = ((manufacturer != null && !manufacturer.isBlank()) ? manufacturer + " " : "")
                        + (model != null ? model : "Android 设备");
            }
            return deviceName;
        }
    }

    /** OkHttp 拦截器：输出请求和响应体日志 */
    private static class LoggingInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();

            // 输出请求体（非 GET 请求）
            if (!"GET".equals(request.method()) && request.body() != null) {
                okio.Buffer buffer = new okio.Buffer();
                request.body().writeTo(buffer);
                String body = buffer.readUtf8();
                if (body.length() > 500) {
                    body = body.substring(0, 500) + "...";
                }
                Log.d(TAG, "--> Body: " + body);
            }

            Response response = chain.proceed(request);

            // 读取响应体（不消费原始的 body）
            if (response.body() != null) {
                String bodyStr = response.peekBody(1024).string();
                if (bodyStr.length() > 500) {
                    bodyStr = bodyStr.substring(0, 500) + "...";
                }
                Log.d(TAG, "<-- Body: " + bodyStr);
            }

            return response;
        }
    }

    // ==================== API 接口定义 ====================

    public interface ApiService {
        // --- 认证 ---
        @POST("auth/login")
        Call<ApiResponse<LoginData>> login(@Body LoginRequest request);

        @GET("auth/me")
        Call<ApiResponse<UserInfo>> getCurrentUser();

        @PATCH("auth/profile")
        Call<ApiResponse<UserInfo>> updateProfile(@Body Map<String, String> body);

        @POST("auth/change-password")
        Call<ApiResponse<Map<String, Object>>> changePassword(@Body Map<String, String> body);

        // --- 文件 ---
        @GET("files")
        Call<ApiResponse<PagedResult<FileItem>>> listFiles(
                @Query("storageRootId") String storageRootId,
                @Query("parentId") String parentId,
                @Query("page") int page,
                @Query("size") int size,
                @Query("viewUserId") String viewUserId,
                @Header("X-Privacy-Access-Token") String privacyToken
        );

        // --- 用户（管理员） ---
        @GET("users")
        Call<ApiResponse<PagedResult<UserInfo>>> listUsers(
                @Query("page") int page,
                @Query("size") int size
        );

        @POST("files/folders")
        Call<ApiResponse<FileItem>> createFolder(
                @Body Map<String, String> body,
                @Header("X-Privacy-Access-Token") String privacyToken
        );

        @Multipart
        @POST("files/upload")
        Call<ApiResponse<FileItem>> uploadFile(
                @Part("storageRootId") RequestBody storageRootId,
                @Part("parentId") RequestBody parentId,
                @Part MultipartBody.Part file,
                @Query("viewUserId") String viewUserId,
                @Header("X-Privacy-Access-Token") String privacyToken
        );

        @GET("files/download/{fileId}")
        Call<ResponseBody> downloadFile(
                @Path("fileId") String fileId,
                @Header("X-Privacy-Access-Token") String privacyToken
        );

        @GET("files/{id}/preview")
        Call<ResponseBody> previewFile(
                @Path("id") String fileId,
                @Header("X-Privacy-Access-Token") String privacyToken
        );

        @DELETE("files/{id}")
        Call<ApiResponse<Map<String, Object>>> deleteFile(
                @Path("id") String id,
                @Header("X-Privacy-Access-Token") String privacyToken
        );

        @POST("files/{id}/privacy/verify")
        Call<ApiResponse<Map<String, Object>>> verifyPrivacy(
                @Path("id") String id,
                @Body Map<String, String> body
        );

        // --- 文件预览进度 ---
        @GET("files/{id}/progress")
        Call<ApiResponse<Map<String, Object>>> getFileProgress(@Path("id") String id);

        @PUT("files/{id}/progress")
        Call<ApiResponse<Map<String, Object>>> saveFileProgress(@Path("id") String id,
                @Body Map<String, Object> body);

        // --- 存储根目录 ---
        @GET("storage-roots/active")
        Call<ApiResponse<List<StorageRoot>>> listStorageRoots();

        // --- 随手记笔记 ---
        @GET("notes")
        Call<ApiResponse<PagedResult<NoteSummary>>> listNotes(
                @Query("keyword") String keyword,
                @Query("viewUserId") String viewUserId,
                @Query("page") int page,
                @Query("size") int size,
                @Query("updatedAfter") String updatedAfter
        );

        @POST("notes")
        Call<ApiResponse<NoteDetail>> createNote(@Body Map<String, String> body);

        @GET("notes/{id}")
        Call<ApiResponse<NoteDetail>> getNote(@Path("id") String id);

        @HTTP(method = "PATCH", path = "notes/{id}", hasBody = true)
        Call<ApiResponse<NoteDetail>> updateNote(@Path("id") String id, @Body Map<String, String> body);

        @DELETE("notes/{id}")
        Call<ApiResponse<Map<String, Object>>> deleteNote(@Path("id") String id);

        // --- 笔记媒体 ---
        @Multipart
        @POST("notes/media")
        Call<ApiResponse<NoteMedia>> uploadNoteMedia(
                @Part("mediaType") RequestBody mediaType,
                @Part MultipartBody.Part file
        );

        @GET("notes/media/{id}")
        Call<ResponseBody> getNoteMedia(@Path("id") String id);

        // --- 笔记阅读进度（SCROLL_PERCENT，0~1）---
        @GET("notes/{id}/progress")
        Call<ApiResponse<NoteProgress>> getNoteProgress(@Path("id") String id);

        @PUT("notes/{id}/progress")
        Call<ApiResponse<Map<String, Object>>> saveNoteProgress(@Path("id") String id,
                @Body Map<String, Object> body);
    }

    // ==================== 便捷方法 ====================

    public Call<ApiResponse<LoginData>> login(String username, String password) {
        return getService().login(new LoginRequest(username, password));
    }

    public Call<ApiResponse<UserInfo>> getCurrentUser() {
        return getService().getCurrentUser();
    }

    public Call<ApiResponse<UserInfo>> updateProfile(String displayName) {
        Map<String, String> body = new java.util.HashMap<>();
        body.put("displayName", displayName != null ? displayName : "");
        return getService().updateProfile(body);
    }

    public Call<ApiResponse<Map<String, Object>>> changePassword(String oldPassword, String newPassword) {
        Map<String, String> body = new java.util.HashMap<>();
        body.put("oldPassword", oldPassword != null ? oldPassword : "");
        body.put("newPassword", newPassword != null ? newPassword : "");
        return getService().changePassword(body);
    }

    public Call<ApiResponse<PagedResult<FileItem>>> listFiles(String storageRootId, String parentId,
                                                                int page, int size, String viewUserId,
                                                                String privacyToken) {
        return getService().listFiles(storageRootId, parentId, page, size, viewUserId, privacyToken);
    }

    /** 管理员：分页列出用户（用于切换 viewUserId 查看用户文件） */
    public Call<ApiResponse<PagedResult<UserInfo>>> listUsers(int page, int size) {
        return getService().listUsers(page, size);
    }

    /**
     * 探测服务器连通性（同步阻塞）：请求 GET /api/health，返回是否可用。
     * 供服务器配置页在后台线程调用；短超时快速反馈。
     */
    public boolean checkHealth(String baseUrl) {
        Request request = new Request.Builder()
                .url(baseUrl + "/api/health")
                .get()
                .build();
        try (Response response = healthClient.newCall(request).execute()) {
            if (!response.isSuccessful()) return false;
            String body = response.body() != null ? response.body().string() : "";
            try {
                return new JSONObject(body).optInt("code") == 0;
            } catch (org.json.JSONException e) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }
    }

    public Call<ApiResponse<FileItem>> createFolder(String storageRootId, String parentId,
                                                      String name, String privacyToken) {
        Map<String, String> body = new java.util.HashMap<>();
        body.put("storageRootId", storageRootId);
        body.put("parentId", parentId != null ? parentId : "");
        body.put("name", name);
        return getService().createFolder(body, privacyToken);
    }

    public Call<ApiResponse<FileItem>> uploadFile(String storageRootId, String parentId,
                                                    byte[] fileBytes, String fileName, String viewUserId,
                                                    String privacyToken) {
        RequestBody rootPart = RequestBody.create(storageRootId, MediaType.parse("text/plain"));
        RequestBody parentPart = RequestBody.create(parentId != null ? parentId : "", MediaType.parse("text/plain"));
        RequestBody fileBody = RequestBody.create(fileBytes, MediaType.parse("application/octet-stream"));
        MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", fileName, fileBody);
        return getService().uploadFile(rootPart, parentPart, filePart, viewUserId, privacyToken);
    }

    public Call<ResponseBody> downloadFile(String fileId, String privacyToken) {
        return getService().downloadFile(fileId, privacyToken);
    }

    /** 获取文件预览字节流（inline 模式，带鉴权） */
    public Call<ResponseBody> previewFile(String fileId, String privacyToken) {
        return getService().previewFile(fileId, privacyToken);
    }

    public Call<ApiResponse<Map<String, Object>>> deleteFile(String id, String privacyToken) {
        return getService().deleteFile(id, privacyToken);
    }

    public Call<ApiResponse<Map<String, Object>>> verifyPrivacy(String folderId, String password) {
        Map<String, String> body = new java.util.HashMap<>();
        body.put("password", password);
        return getService().verifyPrivacy(folderId, body);
    }

    public Call<ApiResponse<List<StorageRoot>>> listStorageRoots() {
        return getService().listStorageRoots();
    }


    // ==================== 随手记笔记 ====================

    public Call<ApiResponse<PagedResult<NoteSummary>>> listNotes(String keyword, String viewUserId,
                                                                  int page, int size) {
        return getService().listNotes(keyword, viewUserId, page, size, null);
    }

    /** 增量同步：拉取 updatedAfter 之后更新的笔记（含软删除标记）；viewUserId 传自己则只拉本人 */
    public Call<ApiResponse<PagedResult<NoteSummary>>> listNotesSince(String viewUserId,
                                                                      String updatedAfter,
                                                                      int page, int size) {
        return getService().listNotes(null, viewUserId, page, size, updatedAfter);
    }

    public Call<ApiResponse<NoteDetail>> createNote(String title, String content) {
        Map<String, String> body = new java.util.HashMap<>();
        body.put("title", title != null ? title : "");
        body.put("content", content != null ? content : "");
        return getService().createNote(body);
    }

    public Call<ApiResponse<NoteDetail>> getNote(String id) {
        return getService().getNote(id);
    }

    public Call<ApiResponse<NoteDetail>> updateNote(String id, String title, String content,
                                                    String baseUpdatedAt) {
        Map<String, String> body = new java.util.HashMap<>();
        body.put("title", title != null ? title : "");
        body.put("content", content != null ? content : "");
        // 乐观并发：携带本次编辑基于的 updatedAt；早于服务端当前值则返回 NOTE_CONFLICT
        if (baseUpdatedAt != null && !baseUpdatedAt.isEmpty()) {
            body.put("baseUpdatedAt", baseUpdatedAt);
        }
        return getService().updateNote(id, body);
    }

    public Call<ApiResponse<Map<String, Object>>> deleteNote(String id) {
        return getService().deleteNote(id);
    }

    // ==================== 笔记媒体 ====================

    /**
     * 上传笔记媒体（图片/录音/画画），mediaType 取值 IMAGE / AUDIO / DRAWING。
     */
    public Call<ApiResponse<NoteMedia>> uploadNoteMedia(String mediaType, byte[] bytes,
                                                        String fileName, String mime) {
        RequestBody typePart = RequestBody.create(mediaType, MediaType.parse("text/plain"));
        RequestBody fileBody = RequestBody.create(bytes, MediaType.parse(mime != null ? mime : "application/octet-stream"));
        MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", fileName, fileBody);
        return getService().uploadNoteMedia(typePart, filePart);
    }

    /** 获取笔记媒体字节流（带鉴权，供编辑器回读图片/录音） */
    public Call<ResponseBody> getNoteMedia(String mediaId) {
        return getService().getNoteMedia(mediaId);
    }

    // ==================== 浏览进度 ====================

    public Call<ApiResponse<Map<String, Object>>> getProgress(String fileId) {
        return getService().getFileProgress(fileId);
    }

    public Call<ApiResponse<Map<String, Object>>> saveProgress(String fileId, String positionType,
                                                               double positionValue) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("positionType", positionType != null ? positionType : "SECONDS");
        body.put("positionValue", positionValue);
        return getService().saveFileProgress(fileId, body);
    }

    public Call<ApiResponse<NoteProgress>> getNoteProgress(String noteId) {
        return getService().getNoteProgress(noteId);
    }

    public Call<ApiResponse<Map<String, Object>>> saveNoteProgress(String noteId, double positionValue) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("positionValue", positionValue);
        return getService().saveNoteProgress(noteId, body);
    }
}
