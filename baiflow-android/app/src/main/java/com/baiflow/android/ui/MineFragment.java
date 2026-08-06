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
import com.baiflow.android.model.ApiResponse;
import com.baiflow.android.model.UserInfo;
import com.baiflow.android.network.ApiClient;

import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
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
        view.findViewById(R.id.btnLogout).setOnClickListener(v -> doLogout());
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
                    client.updateProfile(name).enqueue(new Callback<ApiResponse<UserInfo>>() {
                        @Override
                        public void onResponse(Call<ApiResponse<UserInfo>> call,
                                               Response<ApiResponse<UserInfo>> response) {
                            if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                                session.saveDisplayName(name);
                                refreshUserCard();
                                Toast.makeText(requireContext(), getString(R.string.mine_profile_updated), Toast.LENGTH_SHORT).show();
                            } else {
                                String msg = response.body() != null ? response.body().getMessage() : getString(R.string.common_save_failed);
                                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<ApiResponse<UserInfo>> call, Throwable t) {
                            Toast.makeText(requireContext(), getString(R.string.common_network_error_short), Toast.LENGTH_SHORT).show();
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
                    client.changePassword(oldP, newP).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                        @Override
                        public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                               Response<ApiResponse<Map<String, Object>>> response) {
                            if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                                Toast.makeText(requireContext(), getString(R.string.mine_password_changed), Toast.LENGTH_SHORT).show();
                                doLogout();
                            } else {
                                String msg = response.body() != null ? response.body().getMessage() : getString(R.string.mine_password_change_failed);
                                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                            Toast.makeText(requireContext(), getString(R.string.common_network_error_short), Toast.LENGTH_SHORT).show();
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

    private void doLogout() {
        session.clearSession();
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        requireActivity().finish();
    }
}
