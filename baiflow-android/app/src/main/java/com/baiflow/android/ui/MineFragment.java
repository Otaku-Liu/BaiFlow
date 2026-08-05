package com.baiflow.android.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.baiflow.android.R;
import com.baiflow.android.auth.SessionManager;

/**
 * 我的页 — 用户信息、传输任务入口、服务器配置、退出登录。
 */
public class MineFragment extends Fragment {

    private SessionManager session;

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

        TextView tvAvatar = view.findViewById(R.id.tvAvatar);
        TextView tvDisplayName = view.findViewById(R.id.tvDisplayName);
        TextView tvUsername = view.findViewById(R.id.tvUsername);
        TextView tvRole = view.findViewById(R.id.tvRole);

        String displayName = session.getDisplayName();
        String username = session.getUsername();
        String role = session.getRole();

        // offsetByCodePoints 避免 emoji 代理对被 substring 截断
        tvAvatar.setText(displayName != null && !displayName.isEmpty()
                ? displayName.substring(0, displayName.offsetByCodePoints(0, 1)) : "?");
        tvDisplayName.setText(displayName != null && !displayName.isEmpty() ? displayName : "未登录");
        tvUsername.setText(username != null ? "@" + username : "");
        tvRole.setText(role != null ? role : "USER");

        view.findViewById(R.id.rowTransfers).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), TransferListActivity.class)));

        view.findViewById(R.id.rowServer).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), ServerConfigActivity.class)));

        view.findViewById(R.id.btnLogout).setOnClickListener(v -> doLogout());
    }

    private void doLogout() {
        session.clearSession();
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        requireActivity().finish();
    }
}
