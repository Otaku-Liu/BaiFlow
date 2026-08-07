package com.baiflow.android.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.fragment.app.Fragment;

import com.baiflow.android.R;
import com.baiflow.android.auth.SessionManager;
import com.baiflow.android.data.AppDatabase;
import com.baiflow.android.data.SyncService;
import com.baiflow.android.model.ApiResponse;
import com.baiflow.android.model.UserInfo;
import com.baiflow.android.network.ApiClient;
import com.baiflow.android.network.UiCallback;
import com.baiflow.android.sync.SyncWorker;

import java.util.Map;

import retrofit2.Call;
import retrofit2.Response;

/**
 * 我的页 — 用户信息、修改资料、修改密码、语言设置、传输任务、服务器配置、退出登录。
 */
public class MineFragment extends Fragment {

    private SessionManager session;
    private ApiClient client;
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
        client = ApiClient.getInstance(session);

        tvAvatar = view.findViewById(R.id.tvAvatar);
        tvDisplayName = view.findViewById(R.id.tvDisplayName);
        TextView tvUsername = view.findViewById(R.id.tvUsername);
        TextView tvRole = view.findViewById(R.id.tvRole);
        refreshUserCard();

        String username = session.getUsername();
        String role = session.getRole();
        tvUsername.setText(username != null ? "@" + username : "");
        tvRole.setText(role != null ? role : "USER");

        view.findViewById(R.id.rowProfile).setOnClickListener(v -> showProfileDialog());
        view.findViewById(R.id.rowPassword).setOnClickListener(v -> showPasswordDialog());
        view.findViewById(R.id.rowLanguage).setOnClickListener(v -> showLanguageDialog());
        view.findViewById(R.id.rowTransfers).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), TransferListActivity.class)));
        view.findViewById(R.id.rowServer).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), ServerConfigActivity.class)));
        view.findViewById(R.id.rowOffline).setOnClickListener(v -> handleOfflineToggle());
        view.findViewById(R.id.rowSync).setOnClickListener(v -> handleSync());
        view.findViewById(R.id.rowReconnect).setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), LoginActivity.class));
        });
        view.findViewById(R.id.btnLogout).setOnClickListener(v -> doLogout());

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

    /** 离线模式开关：进离线清 token 留缓存；退出离线 → 重新登录 */
    private void handleOfflineToggle() {
        if (session.isOfflineMode()) {
            session.saveOffline(false);
            startActivity(new Intent(requireContext(), LoginActivity.class));
        } else {
            session.enterOfflineMode();
            SyncWorker.cancel(requireContext());
            Toast.makeText(requireContext(), getString(R.string.mine_offline_toggle_on), Toast.LENGTH_SHORT).show();
            requireActivity().recreate();
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

    // ---- 修改资料 ----

    private void showProfileDialog() {
        final EditText input = new EditText(requireContext());
        input.setHint(getString(R.string.mine_display_name_hint));
        input.setText(session.getDisplayName() != null ? session.getDisplayName() : "");
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.mine_profile))
                .setView(input)
                .setPositiveButton(getString(R.string.common_save), (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    client.updateProfile(name).enqueue(new UiCallback<ApiResponse<UserInfo>>(requireContext()) {
                        @Override
                        protected void onUiResponse(Call<ApiResponse<UserInfo>> call,
                                                    Response<ApiResponse<UserInfo>> response) {
                            if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                                session.saveDisplayName(name);
                                refreshUserCard();
                                Toast.makeText(requireContext(), getString(R.string.mine_profile_updated), Toast.LENGTH_SHORT).show();
                            } else if (response.code() < 500) {
                                String msg = response.body() != null ? response.body().getMessage() : getString(R.string.common_save_failed);
                                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        protected void onUiFailure(Call<ApiResponse<UserInfo>> call, Throwable t) {
                            // 网络失败已由 UiCallback 统一提示
                        }
                    });
                })
                .setNegativeButton(getString(R.string.common_cancel), null)
                .show();
    }

    // ---- 修改密码（重置后服务端吊销全部会话，全设备下线重新登录） ----

    private void showPasswordDialog() {
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(40, 12, 40, 0);

        EditText oldPwd = new EditText(requireContext());
        oldPwd.setHint(getString(R.string.mine_old_password_hint));
        oldPwd.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText newPwd = new EditText(requireContext());
        newPwd.setHint(getString(R.string.mine_new_password_hint));
        newPwd.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText confirmPwd = new EditText(requireContext());
        confirmPwd.setHint(getString(R.string.mine_confirm_password_hint));
        confirmPwd.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        content.addView(oldPwd);
        content.addView(newPwd);
        content.addView(confirmPwd);

        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.mine_password))
                .setMessage(getString(R.string.mine_password_message))
                .setView(content)
                .setPositiveButton(getString(R.string.common_confirm), (d, w) -> {
                    String oldP = oldPwd.getText().toString();
                    String newP = newPwd.getText().toString();
                    String confirmP = confirmPwd.getText().toString();
                    if (oldP.isEmpty() || newP.isEmpty() || confirmP.isEmpty()) {
                        Toast.makeText(requireContext(), getString(R.string.mine_fill_all_fields), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!newP.equals(confirmP)) {
                        Toast.makeText(requireContext(), getString(R.string.mine_password_mismatch), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    client.changePassword(oldP, newP).enqueue(new UiCallback<ApiResponse<Map<String, Object>>>(requireContext()) {
                        @Override
                        protected void onUiResponse(Call<ApiResponse<Map<String, Object>>> call,
                                                    Response<ApiResponse<Map<String, Object>>> response) {
                            if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                                Toast.makeText(requireContext(), getString(R.string.mine_password_changed), Toast.LENGTH_SHORT).show();
                                doLogout();
                            } else if (response.code() < 500) {
                                String msg = response.body() != null ? response.body().getMessage() : getString(R.string.mine_password_change_failed);
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

    // ---- 语言设置（中文 / 英文） ----

    private void showLanguageDialog() {
        String[] options = {getString(R.string.mine_language_chinese), getString(R.string.mine_language_english)};
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.mine_language))
                .setItems(options, (d, which) -> {
                    // AppCompat 持久化并自动重建 Activity 应用新语言
                    AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.forLanguageTags(which == 0 ? "zh" : "en"));
                })
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
