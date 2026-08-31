package com.baiflow.android.ui.fragment;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.util.Log;
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
import com.baiflow.android.util.FileTypeIcon;
import com.baiflow.android.util.FormatUtil;
import com.baiflow.android.widget.DropdownMenu;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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

    // 上传占位进度：轮询 UploadService 任务队列，仅目标目录匹配的任务渲染占位行，完成换真行
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    /** 已处理的终端任务（完成/失败/取消），避免同一任务被轮询重复处理 */
    private final java.util.Set<String> handledTasks = new java.util.HashSet<>();
    /** 最近一次渲染的占位行签名（避免无变化的轮询整体重绑列表） */
    private String lastPlaceholdersSig = "";
    private boolean polling;

    private final Runnable uploadPoller = new Runnable() {
        @Override
        public void run() {
            pollUpload();
            if (polling) {
                mainHandler.postDelayed(this, 800);
            }
        }
    };

    // 隐私文件夹映射: folderId -> accessToken
    private final Map<String, String> privacyTokens = new java.util.HashMap<>();

    /** 上传文件选择器（Activity Result API，多选 → 顺序上传队列） */
    private final ActivityResultLauncher<String> filePicker = registerForActivityResult(
            new ActivityResultContracts.GetMultipleContents(), uris -> {
                if (uris == null || uris.isEmpty()) {
                    return;
                }
                // 队列首个任务用 startForegroundService 启动服务，其余仅入队（服务已在前台）；
                // 隐私令牌取当前目录的有效令牌，随任务透传给后端（此前缺令牌上传隐私文件夹被 403）
                String privacyToken = effectivePrivacyToken();
                boolean first = true;
                for (Uri uri : uris) {
                    String fileName = getFileName(uri);
                    Intent uploadIntent = new Intent(requireContext(), UploadService.class);
                    uploadIntent.putExtra(UploadService.EXTRA_STORAGE_ROOT_ID, currentRootId);
                    uploadIntent.putExtra(UploadService.EXTRA_PARENT_ID, currentParentId != null ? currentParentId : "");
                    uploadIntent.putExtra(UploadService.EXTRA_FILE_PATH, uri.toString());
                    uploadIntent.putExtra(UploadService.EXTRA_VIEW_USER_ID, currentViewUserId);
                    uploadIntent.putExtra(UploadService.EXTRA_FILE_NAME, fileName);
                    uploadIntent.putExtra(UploadService.EXTRA_PRIVACY_TOKEN, privacyToken);
                    if (first) {
                        requireContext().startForegroundService(uploadIntent);
                        first = false;
                    } else {
                        requireContext().startService(uploadIntent);
                    }
                }
                Toast.makeText(requireContext(),
                        getString(R.string.files_upload_started_count, uris.size()), Toast.LENGTH_SHORT).show();
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
            }
        }
        // 兜底：savedInstanceState 未携带目录状态（后台重建丢状态/进程回收/冷启动）时，从本地持久化恢复，
        // 保证从预览（尤其强制横屏的视频）返回后仍停在当前目录而非回根
        if (currentRootId == null && folderStack.isEmpty()) {
            restoreNavState();
        }
        if (!folderStack.isEmpty()) {
            currentParentId = folderStack.peek().getId();
            rebuildPath();
        }
        if (currentRootId != null) {
            loadFiles();
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
        saveNavState();
    }

    @Override
    public void onResume() {
        super.onResume();
        polling = true;
        mainHandler.removeCallbacks(uploadPoller);
        mainHandler.post(uploadPoller);
    }

    @Override
    public void onPause() {
        super.onPause();
        polling = false;
        mainHandler.removeCallbacks(uploadPoller);
    }

    /** 轮询上传任务队列：目标目录匹配的排队/上传中任务渲染占位行；完成/失败/取消各处理一次并触发刷新 */
    private void pollUpload() {
        List<UploadService.UploadTask> tasks = UploadService.getQueueSnapshot();
        if (tasks.isEmpty()) {
            handledTasks.clear();
        }
        List<UploadService.UploadTask> placeholders = new ArrayList<>();
        boolean needRefresh = false;
        for (UploadService.UploadTask t : tasks) {
            if (!matchesUploadDir(t)) {
                continue;
            }
            if (UploadService.STATE_QUEUED.equals(t.state)
                    || UploadService.STATE_UPLOADING.equals(t.state)) {
                placeholders.add(t);
            } else if (handledTasks.add(t.taskId)) {
                if (UploadService.STATE_DONE_OK.equals(t.state)) {
                    handleUploadDone(t);
                    needRefresh = true;
                } else if (UploadService.STATE_FAILED.equals(t.state)) {
                    Toast.makeText(requireContext(),
                            t.errorMessage != null ? t.errorMessage : getString(R.string.transfer_upload_failed),
                            Toast.LENGTH_SHORT).show();
                    UploadService.removeTask(t.taskId);
                } else if (UploadService.STATE_CANCELLED.equals(t.state)) {
                    Toast.makeText(requireContext(), getString(R.string.transfer_upload_cancelled),
                            Toast.LENGTH_SHORT).show();
                    UploadService.removeTask(t.taskId);
                }
            }
        }
        // 仅占位状态（taskId+state+percent）变化才重绑，避免每 800ms 对无变化列表整体刷新
        String sig = placeholderSig(placeholders);
        if (!sig.equals(lastPlaceholdersSig)) {
            lastPlaceholdersSig = sig;
            adapter.setPlaceholders(placeholders);
            updateEmptyState();
        }
        if (needRefresh) {
            loadFiles();
        }
    }

    /** 占位行签名：taskId + 状态 + 百分比，用于判断是否需要重绑列表 */
    private String placeholderSig(List<UploadService.UploadTask> placeholders) {
        StringBuilder sb = new StringBuilder();
        for (UploadService.UploadTask t : placeholders) {
            sb.append(t.taskId).append('|').append(t.state).append('|').append(t.percent).append(';');
        }
        return sb.toString();
    }

    /** 上传任务目标目录是否等于当前浏览目录（仅该目录展示占位行）；根目录下 null 与空串等价 */
    private boolean matchesUploadDir(UploadService.UploadTask t) {
        if (currentRootId == null || !currentRootId.equals(t.rootId)) {
            return false;
        }
        return currentParentId == null
                ? (t.parentId == null || t.parentId.isEmpty())
                : currentParentId.equals(t.parentId);
    }

    /** 上传完成：用响应 FileItem 原位换真行（插入占位位置，随后 loadFiles 按排序归位） */
    private void handleUploadDone(UploadService.UploadTask t) {
        if (t.completedFileItem != null) {
            List<FileItem> server = new ArrayList<>(adapter.getServerItems());
            server.add(0, t.completedFileItem);
            adapter.setServerItems(server);
        }
        UploadService.removeTask(t.taskId);
    }

    /** 点击占位行：确认后取消该任务（上传中 → 中断并续下一个；排队中 → 移出队列） */
    private void confirmCancelUpload(UploadService.UploadTask task) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.files_upload_cancel_title))
                .setMessage(getString(R.string.files_upload_cancel_message, task.fileName))
                .setPositiveButton(getString(R.string.common_confirm), (d, w) -> {
                    Intent intent = new Intent(requireContext(), UploadService.class);
                    intent.setAction(UploadService.ACTION_CANCEL_TASK);
                    intent.putExtra(UploadService.EXTRA_TASK_ID, task.taskId);
                    requireContext().startService(intent);
                })
                .setNegativeButton(getString(R.string.common_cancel), null)
                .show();
    }

    /** 空态联动：服务器列表与占位行都为空才显示「此目录为空」 */
    private void updateEmptyState() {
        boolean empty = adapter.getServerItems().isEmpty() && adapter.getPlaceholders().isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
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
                                adapter.setServerItems(items);
                                updateEmptyState();
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
        saveNavState();
        loadFiles();
    }

    /** 返回上一级；根目录时无操作（按钮此时置灰） */
    private void navigateUp() {
        if (folderStack.isEmpty()) { return; }
        folderStack.pop();
        currentParentId = folderStack.isEmpty() ? null : folderStack.peek().getId();
        rebuildPath();
        saveNavState();
        loadFiles();
    }

    // ==================== 目录导航栈持久化（重建/进程回收/冷启动兜底恢复当前目录） ====================

    private static final String NAV_PREFS = SessionManager.PREFS_FILES_NAV;
    private static final String NAV_ROOT_ID = "root_id";
    private static final String NAV_ROOT_NAME = "root_name";
    private static final String NAV_PARENT_ID = "parent_id";
    private static final String NAV_VIEW_USER = "view_user";
    private static final String NAV_STACK = "stack";

    /** 保存当前目录导航状态（每次导航/状态保存时写入；登出时由 SessionManager 清除） */
    private void saveNavState() {
        SharedPreferences sp = requireContext().getSharedPreferences(NAV_PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();
        ed.putString(NAV_ROOT_ID, currentRootId);
        ed.putString(NAV_ROOT_NAME, currentRootName);
        ed.putString(NAV_PARENT_ID, currentParentId);
        ed.putString(NAV_VIEW_USER, currentViewUserId);
        if (!folderStack.isEmpty()) {
            try {
                // 序列化 ArrayList<FileItem>（当前..根）→ Base64 存储
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                oos.writeObject(new ArrayList<>(folderStack));
                oos.close();
                ed.putString(NAV_STACK, Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP));
            } catch (Exception e) {
                Log.w("FilesFragment", "保存目录导航状态失败", e);
            }
        } else {
            ed.remove(NAV_STACK);
        }
        ed.apply();
    }

    /** 从本地持久化恢复目录导航状态（savedInstanceState 缺失时的兜底） */
    private void restoreNavState() {
        SharedPreferences sp = requireContext().getSharedPreferences(NAV_PREFS, Context.MODE_PRIVATE);
        currentRootId = sp.getString(NAV_ROOT_ID, null);
        currentRootName = sp.getString(NAV_ROOT_NAME, null);
        currentParentId = sp.getString(NAV_PARENT_ID, null);
        currentViewUserId = sp.getString(NAV_VIEW_USER, null);
        String stackB64 = sp.getString(NAV_STACK, null);
        if (stackB64 != null) {
            try {
                byte[] bytes = Base64.decode(stackB64, Base64.NO_WRAP);
                ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes));
                Object obj = ois.readObject();
                ois.close();
                if (obj instanceof ArrayList<?>) {
                    for (Object o : (ArrayList<?>) obj) {
                        if (o instanceof FileItem) {
                            folderStack.addLast((FileItem) o);
                        }
                    }
                }
            } catch (Exception e) {
                Log.w("FilesFragment", "恢复目录导航状态失败", e);
            }
        }
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
        static final int TYPE_UPLOAD = 1;
        static final int TYPE_FILE = 2;
        private List<UploadService.UploadTask> placeholders = new ArrayList<>();
        private List<FileItem> serverItems = new ArrayList<>();
        /** 展示序 = 占位行（队首在上） + 服务器文件 */
        private List<Object> display = new ArrayList<>();

        void setPlaceholders(List<UploadService.UploadTask> tasks) {
            this.placeholders = tasks;
            rebuild();
        }

        void setServerItems(List<FileItem> items) {
            this.serverItems = items;
            rebuild();
        }

        List<UploadService.UploadTask> getPlaceholders() {
            return placeholders;
        }

        List<FileItem> getServerItems() {
            return serverItems;
        }

        private void rebuild() {
            display.clear();
            display.addAll(placeholders);
            display.addAll(serverItems);
            notifyDataSetChanged();
        }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public int getItemViewType(int position) {
            return display.get(position) instanceof UploadService.UploadTask ? TYPE_UPLOAD : TYPE_FILE;
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int pos) {
            Object o = display.get(pos);
            if (o instanceof UploadService.UploadTask) {
                bindUpload(holder, (UploadService.UploadTask) o);
            } else {
                bindFile(holder, (FileItem) o);
            }
        }

        @Override
        public int getItemCount() {
            return display.size();
        }

        private void bindFile(ViewHolder holder, FileItem item) {
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

            // 上传占位子视图复位（RecyclerView 复用占位行后可能残留）
            holder.uploadProgressArea.setVisibility(View.GONE);
            holder.uploadProgressBar.setProgress(0);
            holder.uploadProgressBar.setIndeterminate(false);
            holder.tvUploadPercent.setVisibility(View.GONE);
            holder.tvMeta.setVisibility(View.VISIBLE);

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

        /** 上传占位行：外观与真实文件行一致，原 meta 位置换成横向进度条 + 百分比 */
        private void bindUpload(ViewHolder holder, UploadService.UploadTask task) {
            holder.tvName.setText(task.fileName);
            holder.ivIcon.setImageResource(FileTypeIcon.forName(task.fileName, null));
            holder.tvPrivacyTag.setVisibility(View.GONE);
            holder.ivChevron.setVisibility(View.GONE);
            holder.tvMeta.setVisibility(View.GONE);
            holder.uploadProgressArea.setVisibility(View.VISIBLE);
            holder.tvUploadPercent.setVisibility(View.VISIBLE);
            if (UploadService.STATE_QUEUED.equals(task.state)) {
                // 排队中：进度 0 + 「排队中」，与上传中区分（点击语义不同：排队可移出、上传中可中断）
                holder.uploadProgressBar.setProgress(0);
                holder.tvUploadPercent.setText(getString(R.string.files_upload_queued));
            } else {
                holder.uploadProgressBar.setProgress(task.percent);
                holder.tvUploadPercent.setText(getString(R.string.files_uploading, task.percent));
            }

            holder.itemView.setOnClickListener(v -> confirmCancelUpload(task));
            holder.itemView.setOnLongClickListener(null);
        }

        /** 按文件类型返回图标（委托公共工具，与传输记录行共用） */
        private int iconFor(FileItem item) {
            return item.isDirectory() ? FileTypeIcon.forDirectory()
                    : FileTypeIcon.forName(item.getName(), item.getMimeType());
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView tvName, tvMeta, tvPrivacyTag, tvUploadPercent;
            ImageView ivChevron;
            View uploadProgressArea;
            ProgressBar uploadProgressBar;
            ViewHolder(View v) {
                super(v);
                ivIcon = v.findViewById(R.id.ivIcon);
                tvName = v.findViewById(R.id.tvName);
                tvMeta = v.findViewById(R.id.tvMeta);
                tvPrivacyTag = v.findViewById(R.id.tvPrivacyTag);
                ivChevron = v.findViewById(R.id.ivChevron);
                uploadProgressArea = v.findViewById(R.id.uploadProgressArea);
                uploadProgressBar = v.findViewById(R.id.uploadProgressBar);
                tvUploadPercent = v.findViewById(R.id.tvUploadPercent);
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
