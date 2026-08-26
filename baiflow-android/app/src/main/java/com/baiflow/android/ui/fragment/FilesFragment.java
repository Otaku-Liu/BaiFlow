package com.baiflow.android.ui.fragment;

import android.Manifest;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
import com.baiflow.android.network.UiCallback;
import com.baiflow.android.transfer.DownloadService;
import com.baiflow.android.transfer.UploadService;
import com.baiflow.android.ui.activity.PreviewActivity;
import com.baiflow.android.util.DownloadUtil;
import com.baiflow.android.util.FormatUtil;
import com.baiflow.android.widget.DropdownMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
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
    private android.widget.ImageView btnUp;
    /** 文件夹导航栈（栈顶=当前目录；空=根目录），支持逐级返回上一级 */
    private final java.util.ArrayDeque<FileItem> folderStack = new java.util.ArrayDeque<>();
    /** 待下载文件（API 26-28 权限回调后继续下载） */
    private FileItem pendingDownload;
    private final ActivityResultLauncher<String> downloadPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted && pendingDownload != null) {
                    startDownload(pendingDownload);
                } else {
                    Toast.makeText(requireContext(), getString(R.string.files_download_permission_denied), Toast.LENGTH_SHORT).show();
                }
                pendingDownload = null;
            });

    private List<StorageRoot> roots = new ArrayList<>();
    private List<UserInfo> users = new ArrayList<>();
    private String currentRootId;
    private String currentRootName;
    private String currentParentId;
    private String currentPath = "";
    private String currentViewUserId;   // 管理员切换的目标用户 ID（null = 全部）
    /** 列表排序字段：name（默认）/ createdAt / size；目录始终优先 */
    private String currentSort = "name";
    /** 排序方向：asc / desc（切换字段时用惯例默认：名称升序/创建时间降序/大小降序） */
    private String currentDir = "asc";
    /** 排序按钮（打开排序菜单；方向用菜单内「>」图标指示） */
    private View btnSort;

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
                    Toast.makeText(requireContext(), getString(R.string.files_upload_started, fileName), Toast.LENGTH_SHORT).show();
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
        View btnNew = view.findViewById(R.id.btnNew);
        btnUp = view.findViewById(R.id.btnUp);

        adapter = new FileAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(this::loadFiles);
        // 「新建」按钮：在按钮下方弹出 新建文件夹 / 上传文件 下拉
        btnNew.setOnClickListener(this::showNewMenu);
        // 「排序」按钮：弹出 名称/创建时间/文件大小 排序菜单（点击当前项切换升/降序）
        btnSort = view.findViewById(R.id.btnSort);
        btnSort.setOnClickListener(this::showSortMenu);
        // 「刷新」按钮：重新加载当前目录
        view.findViewById(R.id.btnRefresh).setOnClickListener(v -> loadFiles());
        // 「上一级」按钮：逐级返回，根目录时置灰
        btnUp.setOnClickListener(v -> navigateUp());
        updateUpButton();

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

        // 本地/离线模式：文件中心不可用（离线模式，见 docs/12 §4）
        if (!session.isOnlineMode()) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText(getString(R.string.files_offline_unavailable));
            recyclerView.setVisibility(View.GONE);
            swipeRefresh.setEnabled(false);
            adminRow.setVisibility(View.GONE);
            btnNew.setEnabled(false);
            view.findViewById(R.id.btnRefresh).setEnabled(false);
            return;
        }

        // 管理员显示用户切换
        if ("ADMIN".equals(session.getRole())) {
            adminRow.setVisibility(View.VISIBLE);
            loadUsers();
        }

        // 恢复重建前的文件夹导航状态（旋转/进程回收等导致 Activity 重建时保持当前目录，不再回根/上一级）
        if (savedInstanceState != null) {
            currentRootId = savedInstanceState.getString("state_root_id");
            currentRootName = savedInstanceState.getString("state_root_name");
            currentParentId = savedInstanceState.getString("state_parent_id");
            currentViewUserId = savedInstanceState.getString("state_view_user");
            java.io.Serializable stack = savedInstanceState.getSerializable("state_folder_stack");
            if (stack instanceof ArrayList<?>) {
                for (Object o : (ArrayList<?>) stack) {
                    if (o instanceof FileItem) {
                        // 保存序为 当前..根，addLast 后栈头=当前目录
                        folderStack.addLast((FileItem) o);
                    }
                }
                currentParentId = folderStack.isEmpty() ? null : folderStack.peek().getId();
                rebuildPath();
            }
            if (currentRootId != null) {
                loadFiles();
            }
        }

        loadStorageRoots();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("state_root_id", currentRootId);
        outState.putString("state_root_name", currentRootName);
        outState.putString("state_parent_id", currentParentId);
        outState.putString("state_view_user", currentViewUserId);
        if (!folderStack.isEmpty()) {
            // ArrayList 迭代序 = ArrayDeque 头到尾 = 当前目录..根目录
            outState.putSerializable("state_folder_stack", new ArrayList<>(folderStack));
        }
    }

    // ---- 存储根目录：自动选第一个可用根（不再用下拉框） ----
    private void loadStorageRoots() {
        client.listStorageRoots().enqueue(new UiCallback<ApiResponse<List<StorageRoot>>>(requireContext()) {
            @Override
            protected void onUiResponse(Call<ApiResponse<List<StorageRoot>>> call,
                                        Response<ApiResponse<List<StorageRoot>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                    roots = response.body().getData();
                    if (roots == null || roots.isEmpty()) {
                        Toast.makeText(requireContext(), getString(R.string.files_no_storage_root), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    StorageRoot first = roots.get(0);
                    if (!first.getId().equals(currentRootId)) {
                        currentRootId = first.getId();
                        currentRootName = first.getName();
                        folderStack.clear();
                        currentParentId = null;
                        updateUpButton();
                        rebuildPath();
                        loadFiles();
                    }
                } else if (response.code() < 500) {
                    // 5xx 已由 UiCallback 全局兜底提示「服务器异常」，这里避免双弹
                    Toast.makeText(requireContext(), getString(R.string.files_load_storage_root_failed), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            protected void onUiFailure(Call<ApiResponse<List<StorageRoot>>> call, Throwable t) {
                // 网络失败已由 UiCallback 统一提示
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
        client.listUsers(page, 100).enqueue(new UiCallback<ApiResponse<PagedResult<UserInfo>>>(requireContext()) {
            @Override
            protected void onUiResponse(Call<ApiResponse<PagedResult<UserInfo>>> call,
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
            protected void onUiFailure(Call<ApiResponse<PagedResult<UserInfo>>> call, Throwable t) {
                // 网络失败已由 UiCallback 统一提示
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

        names.add(getString(R.string.files_my_files));
        targetIds.add(myId != null ? myId : "");

        for (UserInfo u : users) {
            if (u.getId() != null && u.getId().equals(myId)) continue;   // 跳过自己，避免与"我的文件"重复
            String display = u.getDisplayName() != null ? u.getDisplayName() : u.getUsername();
            names.add(getString(R.string.files_user_display, display, u.getUsername()));
            targetIds.add(u.getId());
        }

        names.add(getString(R.string.files_all_users));
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
                folderStack.clear();
                currentParentId = null;
                updateUpButton();
                rebuildPath();
                loadFiles();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    // ---- 新建（新建文件夹 / 上传文件） ----

    /** 「新建」按钮：在按钮下方弹出 新建文件夹 / 上传文件 下拉（统一样式） */
    private void showNewMenu(View anchor) {
        java.util.List<DropdownMenu.Option> options = new java.util.ArrayList<>();
        options.add(new DropdownMenu.Option(getString(R.string.files_new_folder), this::showNewFolderDialog));
        options.add(new DropdownMenu.Option(getString(R.string.files_upload), () -> filePicker.launch("*/*")));
        DropdownMenu.show(requireContext(), anchor, options);
    }

    /** 新建文件夹：输入名称 → 调后端创建 → 刷新列表 */
    private void showNewFolderDialog() {
        final EditText input = new EditText(requireContext());
        input.setHint(getString(R.string.files_new_folder_hint));
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.files_new_folder))
                .setView(input)
                .setPositiveButton(getString(R.string.common_confirm), (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(requireContext(), getString(R.string.files_name_required), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    createFolder(name);
                })
                .setNegativeButton(getString(R.string.common_cancel), null)
                .show();
    }

    private void createFolder(String name) {
        client.createFolder(currentRootId, currentParentId, name, null)
                .enqueue(new UiCallback<ApiResponse<FileItem>>(requireContext()) {
                    @Override
                    protected void onUiResponse(Call<ApiResponse<FileItem>> call,
                                                Response<ApiResponse<FileItem>> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                            Toast.makeText(requireContext(), getString(R.string.files_folder_created), Toast.LENGTH_SHORT).show();
                            loadFiles();
                        } else if (response.code() < 500) {
                            String msg = response.body() != null ? response.body().getMessage()
                                    : getString(R.string.files_folder_create_failed);
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    protected void onUiFailure(Call<ApiResponse<FileItem>> call, Throwable t) {
                        // 网络失败已由 UiCallback 统一提示
                    }
                });
    }

    // ---- 文件列表加载 ----
    private void loadFiles() {
        if (currentRootId == null) { return; }

        showLoading(true);
        // 程序化加载只显示顶部进度条；SwipeRefresh 转圈仅由用户下拉触发，避免加载时出现遮罩
        // （下拉刷新时 SwipeRefreshLayout 已自行置 refreshing，响应回调里 setRefreshing(false) 会停掉）

        String token = effectivePrivacyToken();

        client.listFiles(currentRootId, currentParentId, 1, 100, currentViewUserId, token, currentSort, currentDir)
                .enqueue(new UiCallback<ApiResponse<PagedResult<FileItem>>>(requireContext()) {
                    @Override
                    protected void onUiResponse(Call<ApiResponse<PagedResult<FileItem>>> call,
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
                        } else if (response.code() < 500 && response.body() != null) {
                            // 5xx 已由 UiCallback 全局兜底提示，避免双弹；顺带防御空 body
                            handleError(response.body());
                        }
                    }

                    @Override
                    protected void onUiFailure(Call<ApiResponse<PagedResult<FileItem>>> call, Throwable t) {
                        // 网络失败已由 UiCallback 统一提示
                        swipeRefresh.setRefreshing(false);
                        showLoading(false);
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
        int code = resp.getCode();
        if (code == 40105 || code == 40106) {
            showPrivacyPasswordDialog();
        } else if (code == 40107) {
            // 隐私空间尚未设置密码：首次访问设置密码
            showPrivacySetupDialog();
        } else {
            Toast.makeText(requireContext(), resp.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ---- 隐私空间首次设置密码对话框 ----
    private void showPrivacySetupDialog() {
        showPrivacyInputDialog(R.string.files_privacy_setup_title, R.string.files_privacy_setup_message,
                R.string.files_privacy_setup_hint, R.string.files_privacy_setup, 4, this::setupPrivacyAndRetry);
    }

    private void setupPrivacyAndRetry(String password) {
        String folderId = currentParentId;
        if (folderId == null) { return; }

        client.setPrivacy(folderId, password).enqueue(new UiCallback<ApiResponse<FileItem>>(requireContext()) {
            @Override
            protected void onUiResponse(Call<ApiResponse<FileItem>> call, Response<ApiResponse<FileItem>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                    // 设密后立即用同一密码验证换取令牌，直接进入
                    verifyPrivacyAndRetry(password);
                } else if (response.code() < 500) {
                    String msg = response.body() != null ? response.body().getMessage()
                            : getString(R.string.files_privacy_setup_failed);
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            protected void onUiFailure(Call<ApiResponse<FileItem>> call, Throwable t) {
                // 网络失败已由 UiCallback 统一提示
            }
        });
    }

    // ---- 隐私密码对话框（验证/首次设置共用） ----
    private void showPrivacyPasswordDialog() {
        showPrivacyInputDialog(R.string.files_privacy_verify_title, R.string.files_privacy_verify_message,
                R.string.files_privacy_password_hint, R.string.files_privacy_verify, 1, this::verifyPrivacyAndRetry);
    }

    /** 隐私密码输入弹窗骨架：设置/验证共用，仅文案与回调不同 */
    private void showPrivacyInputDialog(int titleRes, int msgRes, int hintRes, int buttonRes,
                                        int minLen, java.util.function.Consumer<String> onPassword) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle(getString(titleRes));
        builder.setMessage(getString(msgRes));

        final EditText input = new EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint(getString(hintRes));
        builder.setView(input);

        builder.setPositiveButton(getString(buttonRes), (dialog, which) -> {
            String password = input.getText().toString().trim();
            if (password.length() >= minLen) { onPassword.accept(password); }
        });
        builder.setNegativeButton(getString(R.string.files_back_up), (dialog, which) -> navigateUp());
        builder.setCancelable(false);
        builder.show();
    }

    private void verifyPrivacyAndRetry(String password) {
        String folderId = currentParentId;
        if (folderId == null) { return; }

        client.verifyPrivacy(folderId, password).enqueue(new UiCallback<ApiResponse<Map<String, Object>>>(requireContext()) {
            @Override
            protected void onUiResponse(Call<ApiResponse<Map<String, Object>>> call,
                                        Response<ApiResponse<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                    Map<String, Object> data = response.body().getData();
                    String token = data != null ? (String) data.get("accessToken") : null;
                    if (token != null) {
                        privacyTokens.put(folderId, token);
                        Toast.makeText(requireContext(), getString(R.string.files_privacy_verify_success), Toast.LENGTH_SHORT).show();
                        loadFiles();
                    }
                } else if (response.code() < 500) {
                    String msg = response.body() != null ? response.body().getMessage() : getString(R.string.files_privacy_wrong_password);
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            protected void onUiFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                // 网络失败已由 UiCallback 统一提示
            }
        });
    }

    /** 沿 folderStack 从当前目录向上取最近隐私文件夹的有效令牌（支持隐私空间内子目录操作） */
    private String effectivePrivacyToken() {
        java.util.Iterator<FileItem> it = folderStack.descendingIterator();
        while (it.hasNext()) {
            FileItem f = it.next();
            if (f.isPrivate()) {
                String t = privacyTokens.get(f.getId());
                if (t != null) { return t; }
            }
        }
        return null;
    }

    // ---- 导航 ----
    private void navigateTo(FileItem folder) {
        folderStack.push(folder);
        currentParentId = folder.getId();
        rebuildPath();
        loadFiles();
    }

    /** 返回上一级；根目录时无操作（按钮此时置灰） */
    private void navigateUp() {
        if (folderStack.isEmpty()) { return; }
        folderStack.pop();
        currentParentId = folderStack.isEmpty() ? null : folderStack.peek().getId();
        rebuildPath();
        loadFiles();
    }

    /** 重建路径文案（始终显示，含根目录显示根名），并维护「上一级」按钮可用态 */
    private void rebuildPath() {
        if (folderStack.isEmpty()) {
            currentPath = (currentRootName != null && !currentRootName.isBlank())
                    ? currentRootName : getString(R.string.files_root);
        } else {
            StringBuilder sb = new StringBuilder();
            java.util.Iterator<FileItem> it = folderStack.descendingIterator();
            while (it.hasNext()) {
                if (sb.length() > 0) { sb.append(" / "); }
                sb.append(it.next().getName());
            }
            currentPath = sb.toString();
        }
        tvPath.setText(currentPath);
        tvPath.setVisibility(View.VISIBLE);
        updateUpButton();
    }

    private void updateUpButton() {
        if (btnUp != null) {
            // 根目录禁用：up_tint_selector 的禁用态会置灰，无需手动 alpha
            btnUp.setEnabled(!folderStack.isEmpty());
        }
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
            if (item.isDirectory()) {
                // 文件夹：显示子项数（隐私文件夹不提供，显示 "-" 对齐 Web）；并显示「>」箭头表示可进入下一级
                Long count = item.getChildCount();
                holder.tvMeta.setText(count != null
                        ? getString(R.string.files_item_count, count)
                        : "-");
                holder.ivChevron.setVisibility(View.VISIBLE);
            } else {
                holder.tvMeta.setText(FormatUtil.formatSize(item.getSizeBytes()));
                holder.ivChevron.setVisibility(View.GONE);
            }

            holder.ivIcon.setImageResource(iconFor(item));

            holder.tvPrivacyTag.setVisibility(item.isPrivate() ? View.VISIBLE : View.GONE);

            holder.itemView.setOnClickListener(v -> {
                if (item.isDirectory()) {
                    // 每次进入隐私空间都要验证密码：清掉缓存令牌，让后端返回 40105/40107 触发弹窗
                    if (item.isPrivate()) {
                        privacyTokens.remove(item.getId());
                    }
                    navigateTo(item);
                } else if (PreviewActivity.canPreview(item.getMimeType())) {
                    // 可预览类型：打开预览页
                    startActivity(PreviewActivity.newIntent(requireContext(), item.getId(), item.getName(),
                            item.getMimeType(), effectivePrivacyToken(),
                            item.getSizeBytes() != null ? item.getSizeBytes() : 0L));
                } else {
                    // 不支持预览：提示 + 手动确认下载，不自动下载
                    showUnsupportedDownloadDialog(item);
                }
            });

            holder.itemView.setOnLongClickListener(v -> {
                showFileContextMenu(item);
                return true;
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        /** 按文件类型返回图标（彩色 PNG；md/json/xml 独立，其它 text 共用文件图标） */
        private int iconFor(FileItem item) {
            if (item.isDirectory()) return R.drawable.ic_folder;
            // md 优先按扩展名识别：服务端存的 mime 是上传方 Content-Type，.md 可能不是 text/markdown
            if (isMarkdown(item)) return R.drawable.ic_type_md;
            String mime = item.getMimeType();
            if (mime == null) return R.drawable.ic_type_file;
            if (mime.startsWith("image/")) return R.drawable.ic_type_image;
            if (mime.startsWith("video/")) return R.drawable.ic_type_video;
            if (mime.startsWith("audio/")) return R.drawable.ic_type_audio;
            if ("application/pdf".equals(mime)) return R.drawable.ic_type_pdf;
            if (mime.endsWith("json")) return R.drawable.ic_type_json;
            if (mime.endsWith("xml")) return R.drawable.ic_type_xml;
            if (mime.startsWith("text/")) return R.drawable.ic_type_file;
            if (mime.contains("msword") || mime.contains("wordprocessingml")) return R.drawable.ic_type_word;
            if (mime.contains("ms-excel") || mime.contains("spreadsheetml")) return R.drawable.ic_type_excel;
            if (mime.contains("ms-powerpoint") || mime.contains("presentationml")) return R.drawable.ic_type_ppt;
            if (mime.contains("zip") || mime.contains("compressed") || mime.contains("x-tar")
                    || mime.contains("gzip")) return R.drawable.ic_type_archive;
            return R.drawable.ic_type_file;
        }

        /** 是否 Markdown 文件：扩展名 .md/.markdown，或 MIME 含 markdown */
        private boolean isMarkdown(FileItem item) {
            String name = item.getName();
            if (name != null) {
                String lower = name.toLowerCase(java.util.Locale.ROOT);
                if (lower.endsWith(".md") || lower.endsWith(".markdown")) return true;
            }
            String mime = item.getMimeType();
            return mime != null && mime.contains("markdown");
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView tvName, tvMeta, tvPrivacyTag;
            ImageView ivChevron;
            ViewHolder(View v) {
                super(v);
                ivIcon = v.findViewById(R.id.ivIcon);
                tvName = v.findViewById(R.id.tvName);
                tvMeta = v.findViewById(R.id.tvMeta);
                tvPrivacyTag = v.findViewById(R.id.tvPrivacyTag);
                ivChevron = v.findViewById(R.id.ivChevron);
            }
        }
    }

    private void downloadFile(FileItem item) {
        // API 26-28 写公共 Download 目录需存储权限；未授权先请求，授权后继续下载
        if (DownloadUtil.needsLegacyStoragePermission()
                && !DownloadUtil.hasLegacyStoragePermission(requireContext())) {
            pendingDownload = item;
            downloadPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return;
        }
        startDownload(item);
    }

    private void startDownload(FileItem item) {
        DownloadUtil.startDownloadService(requireContext(), item.getId(), item.getName(),
                item.getSizeBytes() != null ? item.getSizeBytes() : 0L);
        Toast.makeText(requireContext(), getString(R.string.files_download_started, item.getName()), Toast.LENGTH_SHORT).show();
    }

    /** 不支持预览的文件：提示 + 手动确认下载（不自动下载） */
    private void showUnsupportedDownloadDialog(FileItem item) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.preview_unsupported))
                .setMessage(getString(R.string.files_unsupported_download_prompt, item.getName()))
                .setPositiveButton(getString(R.string.files_download), (d, w) -> downloadFile(item))
                .setNegativeButton(getString(R.string.common_cancel), null)
                .show();
    }

    /** 文件/文件夹长摁弹窗：上半简介（图标/名称/大小/创建/修改/上次打开），下半动作（重命名/下载/立即删除红色） */
    private void showFileContextMenu(FileItem item) {
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_file_info, null);

        ((ImageView) content.findViewById(R.id.dialogFileIcon)).setImageResource(adapter.iconFor(item));
        ((TextView) content.findViewById(R.id.dialogFileName)).setText(item.getName());

        TextView tvMeta = content.findViewById(R.id.dialogFileMeta);
        if (item.isDirectory()) {
            if (item.isPrivate()) {
                // 隐私文件夹大小不可未解锁计算（后端也会拒绝）：直接显示文件夹标记，与 Web 端一致不提供大小
                tvMeta.setText(getString(R.string.files_folder));
            } else {
                // 文件夹大小按需计算：弹窗打开时异步拉取（递归汇总子树），meta 行先占位后更新
                tvMeta.setText(getString(R.string.files_info_size, getString(R.string.files_size_loading)));
                loadFolderSize(item, tvMeta);
            }
        } else {
            tvMeta.setText(getString(R.string.files_info_size, FormatUtil.formatSize(item.getSizeBytes())));
        }
        ((TextView) content.findViewById(R.id.dialogFileCreated))
                .setText(getString(R.string.files_info_created, formatOrDash(item.getCreatedAt())));
        ((TextView) content.findViewById(R.id.dialogFileModified))
                .setText(getString(R.string.files_info_modified, formatOrDash(item.getUpdatedAt())));
        ((TextView) content.findViewById(R.id.dialogFileLastOpened))
                .setText(getString(R.string.files_info_last_opened, formatOrDash(item.getLastOpenedAt())));

        boolean isFolder = item.isDirectory();
        boolean isPrivate = item.isPrivate();
        View actionDownload = content.findViewById(R.id.actionDownload);
        View dividerDownloadDelete = content.findViewById(R.id.dividerDownloadDelete);
        if (isPrivate) {
            // 隐私文件夹/项目不支持重命名/删除（后端同样拒绝）：仅展示简介，隐藏动作区及全部动作分隔线
            content.findViewById(R.id.dividerActionsTop).setVisibility(View.GONE);
            content.findViewById(R.id.actionRename).setVisibility(View.GONE);
            content.findViewById(R.id.dividerRenameDownload).setVisibility(View.GONE);
            actionDownload.setVisibility(View.GONE);
            dividerDownloadDelete.setVisibility(View.GONE);
            content.findViewById(R.id.actionDelete).setVisibility(View.GONE);
        } else if (!isFolder) {
            actionDownload.setVisibility(View.VISIBLE);
            dividerDownloadDelete.setVisibility(View.VISIBLE);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(content)
                .setCancelable(true)
                .show();

        if (!isPrivate) {
            content.findViewById(R.id.actionRename).setOnClickListener(v -> {
                dialog.dismiss();
                showRenameDialog(item);
            });
            if (!isFolder) {
                actionDownload.setOnClickListener(v -> {
                    dialog.dismiss();
                    downloadFile(item);
                });
            }
            content.findViewById(R.id.actionDelete).setOnClickListener(v -> {
                dialog.dismiss();
                confirmDelete(item);
            });
        }
    }

    /** ISO 时间 → 可读串；空/无效显示 "--" */
    private String formatOrDash(String iso) {
        String formatted = FormatUtil.formatDateTime(iso);
        return formatted.isEmpty() ? "--" : formatted;
    }

    /** 文件夹大小按需计算：异步拉取后端递归汇总值并更新弹窗 meta 行；失败回落「文件夹」标记 */
    private void loadFolderSize(FileItem item, TextView tvMeta) {
        client.getFileSize(item.getId(), effectivePrivacyToken())
                .enqueue(new UiCallback<ApiResponse<Long>>(requireContext()) {
                    @Override
                    protected void onUiResponse(Call<ApiResponse<Long>> call, Response<ApiResponse<Long>> response) {
                        if (getActivity() == null) {
                            return;
                        }
                        if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                            Long size = response.body().getData();
                            tvMeta.setText(getString(R.string.files_info_size,
                                    FormatUtil.formatSize(size != null ? size : 0L)));
                        }
                    }

                    @Override
                    protected void onUiFailure(Call<ApiResponse<Long>> call, Throwable t) {
                        // 网络失败已由 UiCallback 统一提示；meta 回落「文件夹」占位
                        if (getActivity() != null) {
                            tvMeta.setText(getString(R.string.files_folder));
                        }
                    }
                });
    }

    /** 排序菜单：名称/创建时间/文件大小（√ 勾选当前项，「>」图标居右指示方向）；再点当前项切换升/降序 */
    private void showSortMenu(View anchor) {
        java.util.List<DropdownMenu.Option> options = new java.util.ArrayList<>();
        options.add(sortOption("name", getString(R.string.files_sort_name)));
        options.add(sortOption("createdAt", getString(R.string.files_sort_created)));
        options.add(sortOption("size", getString(R.string.files_sort_size)));
        DropdownMenu.show(requireContext(), anchor, options);
    }

    /** 排序菜单项：当前排序项 √ + 右侧方向箭头（升序向上、降序向下） */
    private DropdownMenu.Option sortOption(String field, String label) {
        boolean active = field.equals(currentSort);
        int rightIcon = active
                ? ("asc".equals(currentDir) ? R.drawable.ic_chevron_up : R.drawable.ic_chevron_down)
                : 0;
        return new DropdownMenu.Option(label, active, rightIcon, () -> selectSort(field));
    }

    /** 选择排序：再点当前项切换升/降序；切字段用该字段惯例默认方向 */
    private void selectSort(String field) {
        if (field.equals(currentSort)) {
            currentDir = "desc".equals(currentDir) ? "asc" : "desc";
        } else {
            currentSort = field;
            currentDir = "name".equals(field) ? "asc" : "desc";
        }
        loadFiles();
    }

    /** 重命名：预填原名并全选，确定后调后端重命名 → 刷新列表 */
    private void showRenameDialog(FileItem item) {
        final EditText input = new EditText(requireContext());
        input.setHint(getString(R.string.files_rename_hint));
        String name = item.getName() != null ? item.getName() : "";
        input.setText(name);
        input.setSelection(0, name.length());
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.files_rename))
                .setView(input)
                .setPositiveButton(getString(R.string.common_confirm), (d, w) -> {
                    String newName = input.getText().toString().trim();
                    if (newName.isEmpty()) {
                        Toast.makeText(requireContext(), getString(R.string.files_name_required), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    rename(item, newName);
                })
                .setNegativeButton(getString(R.string.common_cancel), null)
                .show();
    }

    private void rename(FileItem item, String newName) {
        String token = effectivePrivacyToken();
        client.renameFile(item.getId(), newName, token)
                .enqueue(new UiCallback<ApiResponse<FileItem>>(requireContext()) {
                    @Override
                    protected void onUiResponse(Call<ApiResponse<FileItem>> call,
                                                Response<ApiResponse<FileItem>> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                            Toast.makeText(requireContext(), getString(R.string.files_rename_success), Toast.LENGTH_SHORT).show();
                            loadFiles();
                        } else if (response.code() < 500) {
                            String msg = response.body() != null ? response.body().getMessage()
                                    : getString(R.string.files_rename_failed);
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    protected void onUiFailure(Call<ApiResponse<FileItem>> call, Throwable t) {
                        // 网络失败已由 UiCallback 统一提示
                    }
                });
    }

    private void confirmDelete(FileItem item) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.common_confirm_delete))
                .setMessage(getString(R.string.common_delete_message, item.getName()))
                .setPositiveButton(getString(R.string.common_delete), (dialog, which) -> {
                    String token = effectivePrivacyToken();
                    client.deleteFile(item.getId(), token).enqueue(new UiCallback<ApiResponse<Map<String, Object>>>(requireContext()) {
                        @Override
                        protected void onUiResponse(Call<ApiResponse<Map<String, Object>>> call,
                                                    Response<ApiResponse<Map<String, Object>>> response) {
                            if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                                Toast.makeText(requireContext(), getString(R.string.common_deleted), Toast.LENGTH_SHORT).show();
                                loadFiles();
                            } else if (response.code() < 500) {
                                String msg = response.body() != null ? response.body().getMessage() : getString(R.string.common_delete_failed);
                                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        protected void onUiFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                            // 网络失败已由 UiCallback 统一提示
                        }
                    });
                })
                .setNegativeButton(getString(R.string.common_cancel), null)
                .show();
    }
}
