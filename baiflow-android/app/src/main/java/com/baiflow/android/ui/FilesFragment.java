package com.baiflow.android.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.baiflow.android.R;
import com.baiflow.android.auth.SessionManager;
import com.baiflow.android.model.ApiResponse;
import com.baiflow.android.model.FileItem;
import com.baiflow.android.model.PagedResult;
import com.baiflow.android.model.StorageRoot;
import com.baiflow.android.model.UserInfo;
import com.baiflow.android.network.ApiClient;
import com.baiflow.android.transfer.DownloadService;
import com.baiflow.android.transfer.UploadService;
import com.baiflow.android.util.FormatUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 文件浏览页（底部「文件」栏）— 浏览、上传、下载、删除，进入子目录。
 * <p>
 * 与 Web 对齐：
 * - 不再显示存储根下拉框，自动使用第一个可用存储根；
 * - 管理员显示「查看用户」切换（viewUserId），逻辑与 Web 相同。
 */
public class FilesFragment extends Fragment {

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvPath, tvEmpty;
    private ProgressBar progressBar;
    private View adminRow;
    private Spinner spinnerUser;
    private FileAdapter adapter;
    private SessionManager session;
    private ApiClient client;

    private List<StorageRoot> roots = new ArrayList<>();
    private List<UserInfo> users = new ArrayList<>();
    private String currentRootId;
    private String currentParentId;
    private String currentPath = "";
    private String currentViewUserId;   // 管理员切换的目标用户 ID（null = 全部）

    // 隐私文件夹映射: folderId -> accessToken
    private final Map<String, String> privacyTokens = new java.util.HashMap<>();

    /** 上传文件选择器（Activity Result API） */
    private final ActivityResultLauncher<String> filePicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    String fileName = getFileName(uri);
                    Intent uploadIntent = new Intent(requireContext(), UploadService.class);
                    uploadIntent.putExtra(UploadService.EXTRA_STORAGE_ROOT_ID, currentRootId);
                    uploadIntent.putExtra(UploadService.EXTRA_PARENT_ID, currentParentId != null ? currentParentId : "");
                    uploadIntent.putExtra(UploadService.EXTRA_FILE_PATH, uri.toString());
                    uploadIntent.putExtra(UploadService.EXTRA_VIEW_USER_ID, currentViewUserId);
                    uploadIntent.putExtra("file_name", fileName);
                    requireContext().startForegroundService(uploadIntent);
                    Toast.makeText(requireContext(), "上传已开始: " + fileName, Toast.LENGTH_SHORT).show();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_files, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        session = SessionManager.getInstance(requireContext());
        client = ApiClient.getInstance(session);

        recyclerView = view.findViewById(R.id.recyclerView);
        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        tvPath = view.findViewById(R.id.tvPath);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        progressBar = view.findViewById(R.id.progressBar);
        adminRow = view.findViewById(R.id.adminRow);
        spinnerUser = view.findViewById(R.id.spinnerUser);
        View btnUpload = view.findViewById(R.id.btnUpload);

        adapter = new FileAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(this::loadFiles);
        btnUpload.setOnClickListener(v -> filePicker.launch("*/*"));

        // 返回键：在子目录时先返回上级
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (currentParentId != null) {
                            navigateUp();
                        } else {
                            setEnabled(false);
                            requireActivity().getOnBackPressedDispatcher().onBackPressed();
                        }
                    }
                });

        // 管理员显示用户切换
        if ("ADMIN".equals(session.getRole())) {
            adminRow.setVisibility(View.VISIBLE);
            loadUsers();
        }

        loadStorageRoots();
    }

    // ---- 存储根目录：自动选第一个可用根（不再用下拉框） ----
    private void loadStorageRoots() {
        client.listStorageRoots().enqueue(new Callback<ApiResponse<List<StorageRoot>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<StorageRoot>>> call,
                                   Response<ApiResponse<List<StorageRoot>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                    roots = response.body().getData();
                    if (roots == null || roots.isEmpty()) {
                        Toast.makeText(requireContext(), "没有可用的存储根目录", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    StorageRoot first = roots.get(0);
                    if (!first.getId().equals(currentRootId)) {
                        currentRootId = first.getId();
                        currentParentId = null;
                        currentPath = "";
                        tvPath.setVisibility(View.GONE);
                        loadFiles();
                    }
                } else {
                    Toast.makeText(requireContext(), "无法加载存储根目录", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<StorageRoot>>> call, Throwable t) {
                Toast.makeText(requireContext(), "网络错误：" + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ---- 管理员用户切换 ----
    private void loadUsers() {
        users.clear();
        fetchUsersPage(1);
    }

    /** 分页拉取用户（每页 100，直到拉完），避免 >100 用户被静默截断 */
    private void fetchUsersPage(final int page) {
        client.listUsers(page, 100).enqueue(new Callback<ApiResponse<PagedResult<UserInfo>>>() {
            @Override
            public void onResponse(Call<ApiResponse<PagedResult<UserInfo>>> call,
                                   Response<ApiResponse<PagedResult<UserInfo>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                    PagedResult<UserInfo> result = response.body().getData();
                    List<UserInfo> records = result != null ? result.getRecords() : new ArrayList<>();
                    users.addAll(records);
                    if (records.size() == 100) {
                        fetchUsersPage(page + 1);
                    } else {
                        populateUserSpinner();
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<PagedResult<UserInfo>>> call, Throwable t) {
                Toast.makeText(requireContext(), "用户列表加载失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 用户切换下拉：与 Web 一致，默认「我的文件」（viewUserId=自己），
     * 可选其他用户，最后「全部（所有用户）」（viewUserId=null）。
     */
    private void populateUserSpinner() {
        final List<String> names = new ArrayList<>();
        final List<String> targetIds = new ArrayList<>();   // 与 names 平行；空串表示"全部"
        String myId = session.getUserId();

        names.add("我的文件");
        targetIds.add(myId != null ? myId : "");

        for (UserInfo u : users) {
            if (u.getId() != null && u.getId().equals(myId)) continue;   // 跳过自己，避免与"我的文件"重复
            String display = u.getDisplayName() != null ? u.getDisplayName() : u.getUsername();
            names.add(display + "（" + u.getUsername() + "）");
            targetIds.add(u.getId());
        }

        names.add("全部（所有用户）");
        targetIds.add("");

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, names);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerUser.setAdapter(spinnerAdapter);

        spinnerUser.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                String target = targetIds.get(pos);
                currentViewUserId = target.isEmpty() ? null : target;
                // 切换用户后回到根目录重新加载
                currentParentId = null;
                currentPath = "";
                tvPath.setVisibility(View.GONE);
                loadFiles();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    // ---- 文件列表加载 ----
    private void loadFiles() {
        if (currentRootId == null) { return; }

        showLoading(true);
        swipeRefresh.setRefreshing(true);

        String token = privacyTokens.get(currentParentId);

        client.listFiles(currentRootId, currentParentId, 1, 100, currentViewUserId, token)
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
                        Toast.makeText(requireContext(), "网络错误：" + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private String getFileName(Uri uri) {
        String name = "unknown";
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            android.database.Cursor cursor = requireContext().getContentResolver()
                    .query(uri, null, null, null, null);
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
            Toast.makeText(requireContext(), resp.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ---- 隐私密码对话框 ----
    private void showPrivacyPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("隐私文件夹访问验证");
        builder.setMessage("此文件夹受隐私保护，需要输入隐私密码才能访问。");

        final EditText input = new EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
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
                        Toast.makeText(requireContext(), "验证成功", Toast.LENGTH_SHORT).show();
                        loadFiles();
                    }
                } else {
                    String msg = response.body() != null ? response.body().getMessage() : "密码错误";
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
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
        currentParentId = null;
        currentPath = "";
        tvPath.setVisibility(View.GONE);
        loadFiles();
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
            String meta = item.isDirectory() ? "文件夹" : FormatUtil.formatSize(item.getSizeBytes());
            if (item.getCreatedAt() != null) { meta += " · " + item.getCreatedAt().substring(0, 10); }
            holder.tvMeta.setText(meta);

            holder.ivIcon.setImageResource(iconFor(item));
            holder.ivIcon.setColorFilter(colorFor(item), android.graphics.PorterDuff.Mode.SRC_IN);

            holder.tvPrivacyTag.setVisibility(item.isPrivate() ? View.VISIBLE : View.GONE);

            holder.itemView.setOnClickListener(v -> {
                if (item.isDirectory()) {
                    navigateTo(item);
                } else if (PreviewActivity.canPreview(item.getMimeType())) {
                    // 可预览类型：打开预览页
                    startActivity(PreviewActivity.newIntent(requireContext(), item.getId(), item.getName(),
                            item.getMimeType(), privacyTokens.get(currentParentId),
                            item.getSizeBytes() != null ? item.getSizeBytes() : 0L));
                } else {
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

        /** 按文件类型返回图标 */
        private int iconFor(FileItem item) {
            if (item.isDirectory()) return R.drawable.ic_folder;
            String mime = item.getMimeType();
            if (mime == null) return R.drawable.ic_type_file;
            if (mime.startsWith("image/")) return R.drawable.ic_type_image;
            if (mime.startsWith("video/")) return R.drawable.ic_type_video;
            if (mime.startsWith("audio/")) return R.drawable.ic_type_audio;
            if ("application/pdf".equals(mime)) return R.drawable.ic_type_pdf;
            if (mime.startsWith("text/") || "application/json".equals(mime)
                    || "application/xml".equals(mime)) return R.drawable.ic_type_text;
            if (mime.contains("msword") || mime.contains("wordprocessingml")
                    || mime.contains("ms-excel") || mime.contains("spreadsheetml")
                    || mime.contains("ms-powerpoint") || mime.contains("presentationml")) return R.drawable.ic_type_office;
            if (mime.contains("zip") || mime.contains("compressed") || mime.contains("x-tar")
                    || mime.contains("gzip")) return R.drawable.ic_type_archive;
            return R.drawable.ic_type_file;
        }

        /** 文件类型图标颜色（iOS 语义色） */
        private int colorFor(FileItem item) {
            if (item.isDirectory()) return 0xFF007AFF;
            String mime = item.getMimeType();
            if (mime == null) return 0xFF8E8E93;
            if (mime.startsWith("image/")) return 0xFF34C759;
            if (mime.startsWith("video/")) return 0xFFFF9500;
            if (mime.startsWith("audio/")) return 0xFFFF3B30;
            if ("application/pdf".equals(mime)) return 0xFFFF3B30;
            if (mime.startsWith("text/") || "application/json".equals(mime)
                    || "application/xml".equals(mime)) return 0xFFAF52DE;
            if (mime.contains("msword") || mime.contains("wordprocessingml")
                    || mime.contains("ms-excel") || mime.contains("spreadsheetml")
                    || mime.contains("ms-powerpoint") || mime.contains("presentationml")) return 0xFF5856D6;
            return 0xFF8E8E93;
        }

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
        Intent downloadIntent = new Intent(requireContext(), DownloadService.class);
        downloadIntent.putExtra(DownloadService.EXTRA_FILE_ID, item.getId());
        downloadIntent.putExtra(DownloadService.EXTRA_FILE_NAME, item.getName());
        downloadIntent.putExtra(DownloadService.EXTRA_SIZE_BYTES,
                item.getSizeBytes() != null ? item.getSizeBytes() : 0L);
        requireContext().startForegroundService(downloadIntent);
        Toast.makeText(requireContext(), "下载已开始: " + item.getName(), Toast.LENGTH_SHORT).show();
    }

    private void showFileContextMenu(FileItem item) {
        String[] options = item.isDirectory() ? new String[]{"删除"} : new String[]{"下载", "删除"};
        new AlertDialog.Builder(requireContext())
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
        new AlertDialog.Builder(requireContext())
                .setTitle("确认删除")
                .setMessage("确定要删除 \"" + item.getName() + "\" 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    String token = privacyTokens.get(currentParentId);
                    client.deleteFile(item.getId(), token).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                        @Override
                        public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                               Response<ApiResponse<Map<String, Object>>> response) {
                            if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                                Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show();
                                loadFiles();
                            } else {
                                String msg = response.body() != null ? response.body().getMessage() : "删除失败";
                                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                            Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
