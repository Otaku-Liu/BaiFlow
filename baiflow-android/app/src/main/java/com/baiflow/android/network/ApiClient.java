package com.baiflow.android.network;

import android.util.Log;
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
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
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

    /** OkHttp 拦截器：自动注入 Bearer token，并输出 Http 日志 */
    private static class AuthInterceptor implements Interceptor {
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
            Request request = builder.build();

            long start = System.currentTimeMillis();
            Response response = chain.proceed(request);
            long duration = System.currentTimeMillis() - start;

            // 输出响应日志
            Log.i(TAG, "<-- " + response.code() + " " + original.method() + " " + original.url()
                    + " (" + duration + "ms)");

            // 401 时清除会话
            if (response.code() == 401) {
                Log.w(TAG, "收到 401，清除会话");
                session.clearSession();
            }
            return response;
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

        // --- 文件 ---
        @GET("files")
        Call<ApiResponse<PagedResult<FileItem>>> listFiles(
                @Query("storageRootId") String storageRootId,
                @Query("parentId") String parentId,
                @Query("page") int page,
                @Query("size") int size,
                @Header("X-Privacy-Access-Token") String privacyToken
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
                @Header("X-Privacy-Access-Token") String privacyToken
        );

        @GET("files/download/{fileId}")
        Call<ResponseBody> downloadFile(
                @Path("fileId") String fileId,
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

        // --- 存储根目录 ---
        @GET("storage-roots/active")
        Call<ApiResponse<List<StorageRoot>>> listStorageRoots();

        // --- 下载任务 ---
        @POST("downloads")
        Call<ApiResponse<DownloadTask>> createDownload(@Body Map<String, String> body);

        @GET("downloads")
        Call<ApiResponse<PagedResult<DownloadTask>>> listDownloads(
                @Query("status") String status,
                @Query("page") int page,
                @Query("size") int size
        );
    }

    // ==================== 便捷方法 ====================

    public Call<ApiResponse<LoginData>> login(String username, String password) {
        return getService().login(new LoginRequest(username, password));
    }

    public Call<ApiResponse<UserInfo>> getCurrentUser() {
        return getService().getCurrentUser();
    }

    public Call<ApiResponse<PagedResult<FileItem>>> listFiles(String storageRootId, String parentId,
                                                                int page, int size, String privacyToken) {
        return getService().listFiles(storageRootId, parentId, page, size, privacyToken);
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
                                                    byte[] fileBytes, String fileName, String privacyToken) {
        RequestBody rootPart = RequestBody.create(storageRootId, MediaType.parse("text/plain"));
        RequestBody parentPart = RequestBody.create(parentId != null ? parentId : "", MediaType.parse("text/plain"));
        RequestBody fileBody = RequestBody.create(fileBytes, MediaType.parse("application/octet-stream"));
        MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", fileName, fileBody);
        return getService().uploadFile(rootPart, parentPart, filePart, privacyToken);
    }

    public Call<ResponseBody> downloadFile(String fileId, String privacyToken) {
        return getService().downloadFile(fileId, privacyToken);
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

    public Call<ApiResponse<DownloadTask>> createDownload(String sourceUrl, String targetStorageRootId,
                                                            String targetRelativePath) {
        Map<String, String> body = new java.util.HashMap<>();
        body.put("sourceUrl", sourceUrl);
        body.put("targetStorageRootId", targetStorageRootId);
        if (targetRelativePath != null) { body.put("targetRelativePath", targetRelativePath); }
        return getService().createDownload(body);
    }

    public Call<ApiResponse<PagedResult<DownloadTask>>> listDownloads(String status, int page, int size) {
        return getService().listDownloads(status, page, size);
    }
}
