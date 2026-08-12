package com.baiflow.android.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.baiflow.android.R;
import com.baiflow.android.auth.SessionManager;
import com.baiflow.android.data.AppDatabase;
import com.baiflow.android.data.SyncService;
import com.baiflow.android.sync.SyncWorker;
import com.baiflow.android.ui.activity.LanguageActivity;
import com.baiflow.android.ui.activity.LoginActivity;
import com.baiflow.android.ui.activity.PasswordActivity;
import com.baiflow.android.ui.activity.ProfileActivity;
import com.baiflow.android.ui.activity.ServerConfigActivity;

/**
 * 我的页 — 用户信息、修改资料、修改密码、语言设置、传输任务、服务器配置、退出登录。
 */
public class MineFragment extends Fragment {

    private SessionManager session;
    private TextView tvAvatar, tvDisplayName;

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

        tvAvatar = view.findViewById(R.id.tvAvatar);
        tvDisplayName = view.findViewById(R.id.tvDisplayName);
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
        view.findViewById(R.id.rowLanguage).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), LanguageActivity.class)));
        view.findViewById(R.id.rowServer).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), ServerConfigActivity.class)));
        view.findViewById(R.id.rowOffline).setOnClickListener(v -> handleOfflineToggle());
        view.findViewById(R.id.rowSync).setOnClickListener(v -> handleSync());
        view.findViewById(R.id.rowReconnect).setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), LoginActivity.class));
        });
        view.findViewById(R.id.btnLogout).setOnClickListener(v -> confirmLogout());

        updateModeRows(view);
    }

    /** 刷新模式指示与离线/同步/重连行的可见性 */
    private void updateModeRows(View root) {
        TextView tvMode = root.findViewById(R.id.tvMode);
        View rowOffline = root.findViewById(R.id.rowOffline);
        View rowSync = root.findViewById(R.id.rowSync);
        View rowReconnect = root.findViewById(R.id.rowReconnect);

        if (session.isLocalMode()) {
            tvMode.setText(getString(R.string.mine_mode_local));
            rowOffline.setVisibility(View.GONE);
            rowSync.setVisibility(View.GONE);
            rowReconnect.setVisibility(View.GONE);
        } else if (session.isOnlineMode()) {
            tvMode.setText(getString(R.string.mine_mode_online));
            rowOffline.setVisibility(View.VISIBLE);
            ((TextView) rowOffline.findViewById(R.id.tvOffline)).setText(getString(R.string.mine_offline_toggle_on));
            rowSync.setVisibility(View.VISIBLE);
            rowReconnect.setVisibility(View.GONE);
        } else {
            tvMode.setText(getString(R.string.mine_mode_offline));
            rowOffline.setVisibility(View.VISIBLE);
            ((TextView) rowOffline.findViewById(R.id.tvOffline)).setText(getString(R.string.mine_offline_toggle_off));
            rowSync.setVisibility(View.GONE);
            rowReconnect.setVisibility(View.VISIBLE);
        }
    }

    /** 离线模式开关：进入离线二次确认（清 token 留本地缓存）；退出离线 → 重新登录 */
    private void handleOfflineToggle() {
        if (session.isOfflineMode()) {
            session.saveOffline(false);
            startActivity(new Intent(requireContext(), LoginActivity.class));
        } else {
            new AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.mine_offline_confirm_title))
                    .setMessage(getString(R.string.mine_offline_confirm_msg))
                    .setPositiveButton(getString(R.string.common_confirm), (d, w) -> {
                        session.enterOfflineMode();
                        SyncWorker.cancel(requireContext());
                        Toast.makeText(requireContext(), getString(R.string.mine_offline_toggle_on), Toast.LENGTH_SHORT).show();
                        requireActivity().recreate();
                    })
                    .setNegativeButton(getString(R.string.common_cancel), null)
                    .show();
        }
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

    /** 刷新用户卡（展示名 + 头像首字） */
    private void refreshUserCard() {
        String displayName = session.getDisplayName();
        tvAvatar.setText(displayName != null && !displayName.isEmpty()
                ? displayName.substring(0, displayName.offsetByCodePoints(0, 1)) : "?");
        tvDisplayName.setText(displayName != null && !displayName.isEmpty() ? displayName : getString(R.string.mine_not_logged_in));
    }

    /** 退出登录：二次确认（清 token + 清该服务器本地缓存，保留服务器地址停在登录页） */
    private void confirmLogout() {
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.mine_logout_confirm_title))
                .setMessage(getString(R.string.mine_logout_confirm_msg))
                .setPositiveButton(getString(R.string.common_confirm), (d, w) -> doLogout())
                .setNegativeButton(getString(R.string.common_cancel), null)
                .show();
    }

    /** 登出：清 token + 清该服务器本地缓存（两级语义，见 docs/12 §2），保留服务器地址停在登录页 */
    private void doLogout() {
        String partition = session.getDataPartition();
        session.clearSession();
        if (!SessionManager.PARTITION_LOCAL.equals(partition)) {
            AppDatabase.get(requireContext()).noteDao().clearByServer(partition);
        }
        SyncWorker.cancel(requireContext());
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        requireActivity().finish();
    }
}
