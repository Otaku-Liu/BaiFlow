package com.baiflow.android.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.fragment.app.Fragment;

import com.baiflow.android.R;
import com.baiflow.android.auth.SessionManager;
import com.baiflow.android.data.AppDatabase;
import com.baiflow.android.data.MediaFiles;
import com.baiflow.android.data.SyncService;
import com.baiflow.android.model.ApiResponse;
import com.baiflow.android.model.UserInfo;
import com.baiflow.android.network.ApiClient;
import com.baiflow.android.network.UiCallback;
import com.baiflow.android.sync.SyncWorker;
import com.baiflow.android.ui.activity.LanguageActivity;
import com.baiflow.android.ui.activity.LoginActivity;
import com.baiflow.android.ui.activity.PasswordActivity;
import com.baiflow.android.ui.activity.RecordsActivity;
import com.baiflow.android.ui.activity.ProfileActivity;
import com.baiflow.android.util.AvatarLoader;
import com.baiflow.android.util.FormatUtil;

import retrofit2.Call;
import retrofit2.Response;

/**
 * 我的页 — 用户信息、修改资料、修改密码、语言设置、传输任务、退出登录。
 */
public class MineFragment extends Fragment {

    private SessionManager session;
    private com.baiflow.android.data.LocalNoteDao dao;
    private TextView tvAvatar, tvDisplayName;
    private ImageView ivAvatar;
    /** 上次已加载的头像 URL，避免 onResume 重复下载同一张 */
    private String lastAvatarUrl;
    /** 登出是否已开始（幂等守卫：防连点/双弹窗重复执行 doLogout 触发 Fragment detach 闪退） */
    private boolean logoutStarted;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_mine, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        session = SessionManager.getInstance(requireContext());
        dao = com.baiflow.android.data.AppDatabase.get(requireContext()).noteDao();

        tvAvatar = view.findViewById(R.id.tvAvatar);
        tvDisplayName = view.findViewById(R.id.tvDisplayName);
        ivAvatar = view.findViewById(R.id.ivAvatar);
        TextView tvUsername = view.findViewById(R.id.tvUsername);
        TextView tvRole = view.findViewById(R.id.tvRole);
        refreshUserCard();

        String username = session.getUsername();
        String role = session.getRole();
        tvUsername.setText(username != null ? "@" + username : "");
        tvRole.setText(role != null ? role : "USER");

        view.findViewById(R.id.rowProfile).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), ProfileActivity.class)));
        view.findViewById(R.id.rowPassword).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), PasswordActivity.class)));
        view.findViewById(R.id.rowUploadRecords).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), RecordsActivity.class)
                        .putExtra(RecordsActivity.EXTRA_UPLOAD, true)));
        view.findViewById(R.id.rowDownloadRecords).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), RecordsActivity.class)
                        .putExtra(RecordsActivity.EXTRA_UPLOAD, false)));
        view.findViewById(R.id.rowLanguage).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), LanguageActivity.class)));
        view.findViewById(R.id.rowClearCache).setOnClickListener(v -> confirmClearCache());
        view.findViewById(R.id.rowCacheLimit).setOnClickListener(v -> showCacheLimitDialog());
        ((TextView) view.findViewById(R.id.tvCacheLimit))
                .setText(getString(R.string.mine_cache_limit_value, session.getCacheLimitMb()));
        view.findViewById(R.id.rowSync).setOnClickListener(v -> handleSync());
        view.findViewById(R.id.btnLogout).setOnClickListener(v -> confirmLogout());

        updateModeRows(view);
        updateSyncStatus(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        // 从修改资料页返回（可能改了展示名/头像）后刷新
        refreshUserCard();
        // 头像/展示名可能在其他端（Web）改过：仅登录时缓存的本地态是旧的，进本页时从服务端拉最新
        refreshUserFromServer();
        // 同步后待推/冲突数会变，回到本页刷新；缓存大小也重算（可能被自动 LRU 清理）
        View root = getView();
        if (root != null) {
            updateSyncStatus(root);
            refreshCacheSize(root);
        }
    }

    /** 在线模式下从服务端拉取当前用户信息并刷新本地缓存（头像/展示名可能在其他端修改） */
    private void refreshUserFromServer() {
        if (!session.isOnlineMode()) {
            return;
        }
        ApiClient.getInstance(session).getCurrentUser().enqueue(new UiCallback<ApiResponse<UserInfo>>(requireContext()) {
            @Override
            protected void onUiResponse(Call<ApiResponse<UserInfo>> call, Response<ApiResponse<UserInfo>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                    UserInfo user = response.body().getData();
                    if (user != null) {
                        session.saveUser(user.getId(), user.getUsername(), user.getDisplayName(),
                                user.getAvatarUrl(), user.getRole());
                        if (getActivity() != null) {
                            refreshUserCard();
                        }
                    }
                }
            }

            @Override
            protected void onUiFailure(Call<ApiResponse<UserInfo>> call, Throwable t) {
                // 刷新失败静默：保留本地缓存即可
            }
        });
    }

    /** 在线模式：显示待推/冲突笔记数（读 Room，主线程允许）；无未同步改动或非在线模式隐藏 */
    private void updateSyncStatus(View root) {
        TextView tvStatus = root.findViewById(R.id.tvSyncStatus);
        if (!session.isOnlineMode()) {
            tvStatus.setVisibility(View.GONE);
            return;
        }
        String partition = session.getDataPartition();
        int conflict = dao.countConflict(partition);
        int pending = dao.countDirty(partition) - conflict;
        if (pending <= 0 && conflict <= 0) {
            tvStatus.setVisibility(View.GONE);
            return;
        }
        tvStatus.setText(getString(R.string.mine_sync_status, pending, conflict));
        tvStatus.setVisibility(View.VISIBLE);
    }

    /** 后台统计缓存大小刷新到「清理缓存」行右侧（onResume / 清缓存后调用） */
    private void refreshCacheSize(View root) {
        final TextView tv = root.findViewById(R.id.tvCacheSize);
        android.content.Context ctx = requireContext();
        new Thread(() -> {
            final long bytes = MediaFiles.cacheDirSize(ctx);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> tv.setText(FormatUtil.formatSize(bytes)));
            }
        }).start();
    }

    /** 清理缓存：二次确认后清空服务端媒体缓存（离线新建媒体不动），刷新大小 */
    private void confirmClearCache() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.mine_clear_cache_confirm_title))
                .setMessage(getString(R.string.mine_clear_cache_confirm_msg))
                .setPositiveButton(getString(R.string.mine_clear_cache_confirm), (d, w) -> {
                    MediaFiles.clearCacheDir(requireContext());
                    View root = getView();
                    if (root != null) {
                        refreshCacheSize(root);
                    }
                    Toast.makeText(requireContext(), getString(R.string.mine_clear_cache_done), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(getString(R.string.common_cancel), null)
                .show();
    }

    /** 缓存上限：弹窗内 SeekBar 拖动（50–2000MB，步进 50），确定保存 */
    private void showCacheLimitDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_cache_limit, null);
        final TextView tvValue = dialogView.findViewById(R.id.tvCacheLimitValue);
        SeekBar sb = dialogView.findViewById(R.id.sbCacheLimit);
        final int current = session.getCacheLimitMb();
        sb.setProgress((Math.max(50, Math.min(current, 2000)) - 50) / 50);
        tvValue.setText(getString(R.string.mine_cache_limit_value, 50 + sb.getProgress() * 50));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvValue.setText(getString(R.string.mine_cache_limit_value, 50 + progress * 50));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.mine_cache_limit))
                .setView(dialogView)
                .setPositiveButton(getString(R.string.common_confirm), (d, w) -> {
                    session.saveCacheLimitMb(50 + sb.getProgress() * 50);
                    View root = getView();
                    if (root != null) {
                        ((TextView) root.findViewById(R.id.tvCacheLimit))
                                .setText(getString(R.string.mine_cache_limit_value, session.getCacheLimitMb()));
                    }
                })
                .setNegativeButton(getString(R.string.common_cancel), null)
                .show();
    }

    /** 刷新模式指示（仅在线模式，无离线状态） */
    private void updateModeRows(View root) {
        TextView tvMode = root.findViewById(R.id.tvMode);
        tvMode.setText(getString(R.string.mine_mode_online));
        root.findViewById(R.id.rowSync).setVisibility(View.VISIBLE);
    }

    /** 手动同步：后台同步一次（仅在线模式有意义） */
    private void handleSync() {
        Toast.makeText(requireContext(), getString(R.string.mine_sync_pending), Toast.LENGTH_SHORT).show();
        android.content.Context ctx = requireContext();
        new Thread(() -> {
            boolean ok = SyncService.syncOnce(ctx);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> Toast.makeText(ctx,
                        getString(ok ? R.string.mine_sync_done : R.string.mine_sync_failed),
                        Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    /** 刷新用户卡（展示名 + 头像：有 URL 加载图片，否则展示首字占位） */
    private void refreshUserCard() {
        String displayName = session.getDisplayName();
        tvAvatar.setText(FormatUtil.firstCharOrQuestion(displayName));
        tvDisplayName.setText(displayName != null && !displayName.isEmpty() ? displayName : getString(R.string.mine_not_logged_in));
        loadAvatar();
    }

    /** 后台加载头像；加载成功显示图片并隐藏首字占位，无头像/加载失败回退首字占位 */
    private void loadAvatar() {
        String fullUrl = AvatarLoader.resolveUrl(session.getServerUrl(), session.getAvatarUrl());
        if (fullUrl == null || fullUrl.equals(lastAvatarUrl)) {
            if (fullUrl == null) {
                ivAvatar.setVisibility(View.GONE);
                tvAvatar.setVisibility(View.VISIBLE);
            }
            return;
        }
        lastAvatarUrl = fullUrl;
        AvatarLoader.loadInto(ivAvatar, fullUrl,
                () -> {
                    ivAvatar.setVisibility(View.VISIBLE);
                    tvAvatar.setVisibility(View.GONE);
                },
                () -> {
                    ivAvatar.setVisibility(View.GONE);
                    tvAvatar.setVisibility(View.VISIBLE);
                });
    }

    /** 退出登录：二次确认（清 token + 清该服务器本地缓存，保留服务器地址停在登录页） */
    private void confirmLogout() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.mine_logout_confirm_title))
                .setMessage(getString(R.string.mine_logout_confirm_msg))
                .setPositiveButton(getString(R.string.common_confirm), (d, w) -> doLogout())
                .setNegativeButton(getString(R.string.common_cancel), null)
                .show();
    }

    /** 登出：清 token + 清该服务器本地缓存（两级语义，见 docs/12 §2），保留服务器地址停在登录页 */
    private void doLogout() {
        if (logoutStarted) {
            return;
        }
        logoutStarted = true;
        String partition = session.getDataPartition();
        session.clearSession();
        AppDatabase.get(requireContext()).noteDao().clearByServer(partition);
        SyncWorker.cancel(requireContext());
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        requireActivity().finish();
    }
}
