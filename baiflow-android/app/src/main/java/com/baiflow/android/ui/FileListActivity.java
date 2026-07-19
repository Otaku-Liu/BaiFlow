package com.baiflow.android.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.baiflow.android.R;
import com.baiflow.android.auth.SessionManager;
import com.baiflow.android.model.*;
import com.baiflow.android.network.ApiClient;
import com.baiflow.android.transfer.DownloadService;
import com.baiflow.android.transfer.UploadService;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.io.*;
import java.util.*;

/**
 * 文件列表页 — 浏览、上传、下载文件，进入子目录。
 * <p>
 * 支持 Storage Root 切换、文件夹导航、下拉刷新、隐私文件夹密码验证。
 */
public class FileListActivity extends AppCompatActivity {

    private Spinner spinnerRoot;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvPath, tvEmpty;
    private ProgressBar progressBar;
    private Button btnUpload, btnTransfers, btnLogout;
    private FileAdapter adapter;
    private SessionManager session;
    private ApiClient client;

    private List<StorageRoot> roots = new ArrayList<>();
    private String currentRootId;
    private String currentParentId;
    private String currentPath = "";

    // 隐私文件夹映射: folderId -> accessToken
    private Map<String, String> privacyTokens = new HashMap<>();

    private static final int REQUEST_UPLOAD_FILE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_list);

        session = SessionManager.getInstance(this);
        client = ApiClient.getInstance(session);

        spinnerRoot = findViewById(R.id.spinnerRoot);
        recyclerView = findViewById(R.id.recyclerView);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        tvPath = findViewById(R.id.tvPath);
        tvEmpty = findViewById(R.id.tvEmpty);
        progressBar = findViewById(R.id.progressBar);
        btnUpload = findViewById(R.id.btnUpload);
        btnTransfers = findViewById(R.id.btnTransfers);
        btnLogout = findViewById(R.id.btnLogout);

        adapter = new FileAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(this::loadFiles);
        btnUpload.setOnClickListener(v -> pickFileForUpload());
        btnLogout.setOnClickListener(v -> doLogout());
        btnTransfers.setOnClickListener(v -> {
            startActivity(new Intent(this, TransferListActivity.class));
        });

        loadStorageRoots();
    }

    // ---- 存储根目录加载 ----
    private void loadStorageRoots() {
        client.listStorageRoots().enqueue(new Callback<ApiResponse<List<StorageRoot>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<StorageRoot>>> call,
                                   Response<ApiResponse<List<StorageRoot>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                    roots = response.body().getData();
                    if (roots == null) { roots = new ArrayList<>(); }

                    List<String> names = new ArrayList<>();
                    for (StorageRoot r : roots) { names.add(r.getName()); }

                    ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(FileListActivity.this,
                            android.R.layout.simple_spinner_item, names);
                    spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinnerRoot.setAdapter(spinnerAdapter);

                    spinnerRoot.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                            if (pos < roots.size()) {
                                StorageRoot r = roots.get(pos);
                                if (!r.getId().equals(currentRootId)) {
                                    currentRootId = r.getId();
                                    currentParentId = null;
                                    currentPath = "";
                                    tvPath.setVisibility(View.GONE);
                                    loadFiles();
                                }
                            }
                        }
                        @Override public void onNothingSelected(AdapterView<?> p) {}
                    });
                } else {
                    Toast.makeText(FileListActivity.this, "无法加载存储根目录", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<StorageRoot>>> call, Throwable t) {
                Toast.makeText(FileListActivity.this, "网络错误：" + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ---- 文件列表加载 ----
    private void loadFiles() {
        if (currentRootId == null) { return; }

        showLoading(true);
        swipeRefresh.setRefreshing(true);

        String token = privacyTokens.get(currentParentId);

        client.listFiles(currentRootId, currentParentId, 1, 100, token)
                .enqueue(new Callback<ApiResponse<PagedResult<FileItem>>>() {
            @Override
            public void onResponse(Call<ApiResponse<PagedResult<FileItem>>> call,
                                   Response<ApiResponse<PagedResult<FileItem>>> response) {
                swipeRefresh.setRefreshing(false);
                showLoading(false);

                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().isOk()) {
                        PagedResult<FileItem> result = response.body().getData();
                        List<FileItem> items = result != null ? result.getRecords() : new ArrayList<>();
                        adapter.setItems(items);
                        tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                        recyclerView.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
                    } else {
                        handleError(response.body());
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<PagedResult<FileItem>>> call, Throwable t) {
                swipeRefresh.setRefreshing(false);
                showLoading(false);
                Toast.makeText(FileListActivity.this, "网络错误：" + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ---- 上传文件 ----
    private void pickFileForUpload() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_UPLOAD_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_UPLOAD_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                String fileName = getFileName(uri);
                // 启动前台上传服务
                Intent uploadIntent = new Intent(this, UploadService.class);
                uploadIntent.putExtra(UploadService.EXTRA_STORAGE_ROOT_ID, currentRootId);
                uploadIntent.putExtra(UploadService.EXTRA_PARENT_ID, currentParentId != null ? currentParentId : "");
                uploadIntent.putExtra(UploadService.EXTRA_FILE_PATH, uri.toString());
                // 标记需要将内容复制到临时文件
                uploadIntent.putExtra("file_name", fileName);
                startForegroundService(uploadIntent);
                Toast.makeText(this, "上传已开始: " + fileName, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String getFileName(Uri uri) {
        String name = "unknown";
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) { cursor.moveToFirst(); name = cursor.getString(idx); }
                cursor.close();
            }
        } else {
            name = uri.getLastPathSegment() != null ? uri.getLastPathSegment() : "unknown";
        }
        return name;
    }

    // ---- 错误处理 ----
    private void handleError(ApiResponse<?> resp) {
        String code = resp.getCode();
        if ("PRIVATE_PASSWORD_REQUIRED".equals(code) || "PRIVATE_PASSWORD_INVALID".equals(code)) {
            showPrivacyPasswordDialog();
        } else {
            Toast.makeText(this, resp.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ---- 隐私密码对话框 ----
    private void showPrivacyPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("隐私文件夹访问验证");
        builder.setMessage("此文件夹受隐私保护，需要输入隐私密码才能访问。");

        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("请输入隐私密码");
        builder.setView(input);

        builder.setPositiveButton("验证", (dialog, which) -> {
            String password = input.getText().toString().trim();
            if (!password.isEmpty()) { verifyPrivacyAndRetry(password); }
        });
        builder.setNegativeButton("返回上级", (dialog, which) -> navigateUp());
        builder.setCancelable(false);
        builder.show();
    }

    private void verifyPrivacyAndRetry(String password) {
        // 使用当前文件夹 ID 作为隐私文件夹
        String folderId = currentParentId;
        if (folderId == null) { return; }

        client.verifyPrivacy(folderId, password).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                   Response<ApiResponse<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                    Map<String, Object> data = response.body().getData();
                    String token = data != null ? (String) data.get("accessToken") : null;
                    if (token != null) {
                        privacyTokens.put(folderId, token);
                        Toast.makeText(FileListActivity.this, "验证成功", Toast.LENGTH_SHORT).show();
                        loadFiles();
                    }
                } else {
                    String msg = response.body() != null ? response.body().getMessage() : "密码错误";
                    Toast.makeText(FileListActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                Toast.makeText(FileListActivity.this, "网络错误", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ---- 导航 ----
    private void navigateTo(FileItem folder) {
        currentParentId = folder.getId();
        currentPath = currentPath.isEmpty() ? folder.getName() : currentPath + " / " + folder.getName();
        tvPath.setText(currentPath);
        tvPath.setVisibility(View.VISIBLE);
        loadFiles();
    }

    private void navigateUp() {
        if (currentParentId == null) { return; }

        // 回到根目录（简化处理）
        currentParentId = null;
        currentPath = "";
        tvPath.setVisibility(View.GONE);
        loadFiles();
    }

    @Override
    public void onBackPressed() {
        if (currentParentId != null) {
            navigateUp();
        } else {
            super.onBackPressed();
        }
    }

    // ---- 退出 ----
    private void doLogout() {
        session.clearSession();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // ==================== RecyclerView Adapter ====================

    class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {
        private List<FileItem> items = new ArrayList<>();

        void setItems(List<FileItem> items) { this.items = items; notifyDataSetChanged(); }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int pos) {
            FileItem item = items.get(pos);

            holder.tvName.setText(item.getName());
            String meta = item.isDirectory() ? "文件夹" : formatSize(item.getSizeBytes());
            if (item.getCreatedAt() != null) { meta += " · " + item.getCreatedAt().substring(0, 10); }
            holder.tvMeta.setText(meta);

            int icon = item.isDirectory() ? android.R.drawable.ic_menu_sort_by_size
                    : android.R.drawable.ic_menu_gallery;
            holder.ivIcon.setImageResource(icon);

            holder.tvPrivacyTag.setVisibility(item.isPrivate() ? View.VISIBLE : View.GONE);

            holder.itemView.setOnClickListener(v -> {
                if (item.isDirectory()) {
                    navigateTo(item);
                } else {
                    // 点击文件：下载
                    downloadFile(item);
                }
            });

            holder.itemView.setOnLongClickListener(v -> {
                showFileContextMenu(item);
                return true;
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView tvName, tvMeta, tvPrivacyTag;
            ViewHolder(View v) {
                super(v);
                ivIcon = v.findViewById(R.id.ivIcon);
                tvName = v.findViewById(R.id.tvName);
                tvMeta = v.findViewById(R.id.tvMeta);
                tvPrivacyTag = v.findViewById(R.id.tvPrivacyTag);
            }
        }
    }

    private void downloadFile(FileItem item) {
        // 启动前台下载服务
        Intent downloadIntent = new Intent(this, DownloadService.class);
        downloadIntent.putExtra(DownloadService.EXTRA_FILE_ID, item.getId());
        downloadIntent.putExtra(DownloadService.EXTRA_FILE_NAME, item.getName());
        downloadIntent.putExtra(DownloadService.EXTRA_SIZE_BYTES,
                item.getSizeBytes() != null ? item.getSizeBytes() : 0L);
        startForegroundService(downloadIntent);
        Toast.makeText(this, "下载已开始: " + item.getName(), Toast.LENGTH_SHORT).show();
    }

    private void showFileContextMenu(FileItem item) {
        String[] options = item.isDirectory() ? new String[]{"删除"} : new String[]{"下载", "删除"};
        new AlertDialog.Builder(this)
                .setTitle(item.getName())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        if (item.isDirectory()) {
                            confirmDelete(item);
                        } else {
                            downloadFile(item);
                        }
                    } else if (which == 1) {
                        confirmDelete(item);
                    }
                })
                .show();
    }

    private void confirmDelete(FileItem item) {
        new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage("确定要删除 \"" + item.getName() + "\" 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    String token = privacyTokens.get(currentParentId);
                    client.deleteFile(item.getId(), token).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                        @Override
                        public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                               Response<ApiResponse<Map<String, Object>>> response) {
                            if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                                Toast.makeText(FileListActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                                loadFiles();
                            } else {
                                String msg = response.body() != null ? response.body().getMessage() : "删除失败";
                                Toast.makeText(FileListActivity.this, msg, Toast.LENGTH_SHORT).show();
                            }
                        }
                        @Override
                        public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                            Toast.makeText(FileListActivity.this, "网络错误", Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String formatSize(Long bytes) {
        if (bytes == null || bytes == 0) { return "0 B"; }
        if (bytes < 1024) { return bytes + " B"; }
        if (bytes < 1024 * 1024) { return String.format("%.1f KB", bytes / 1024.0); }
        if (bytes < 1024 * 1024 * 1024) { return String.format("%.1f MB", bytes / (1024.0 * 1024)); }
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
