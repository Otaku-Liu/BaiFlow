package com.baiflow.android.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.baiflow.android.R;
import com.baiflow.android.auth.SessionManager;
import com.baiflow.android.data.AppDatabase;
import com.baiflow.android.data.SyncService;
import com.baiflow.android.model.ApiResponse;
import com.baiflow.android.model.LoginData;
import com.baiflow.android.model.UserInfo;
import com.baiflow.android.network.ApiClient;
import com.baiflow.android.network.NetworkFeedback;
import com.baiflow.android.sync.SyncWorker;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 登录页 — 用户名密码登录 BaiFlow 服务器。
 */
public class LoginActivity extends AppCompatActivity {

    private EditText etUsername, etPassword;
    private Button btnLogin, btnChangeServer;
    private TextView tvError, tvServerUrl;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        session = SessionManager.getInstance(this);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnChangeServer = findViewById(R.id.btnChangeServer);
        tvError = findViewById(R.id.tvError);
        tvServerUrl = findViewById(R.id.tvServerUrl);

        tvServerUrl.setText(getString(R.string.login_server_prefix, session.getServerUrl()));

        btnLogin.setOnClickListener(v -> doLogin());
        btnChangeServer.setOnClickListener(v -> {
            Intent intent = new Intent(this, ServerConfigActivity.class);
            startActivity(intent);
            finish();
        });
        // 登录页逃生口：服务器不可达/忘密码时进离线模式（本地笔记可用，见 docs/12 §4）
        findViewById(R.id.btnOfflineMode).setOnClickListener(v -> {
            session.enterOfflineMode();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }

    private void doLogin() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            tvError.setText(getString(R.string.login_input_required));
            tvError.setVisibility(TextView.VISIBLE);
            return;
        }

        btnLogin.setEnabled(false);
        btnLogin.setText(getString(R.string.login_logging_in));
        tvError.setVisibility(TextView.GONE);

        ApiClient client = ApiClient.getInstance(session);
        client.login(username, password).enqueue(new Callback<ApiResponse<LoginData>>() {
            @Override
            public void onResponse(Call<ApiResponse<LoginData>> call, Response<ApiResponse<LoginData>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                    String token = response.body().getData().getToken();
                    session.saveToken(token);
                    fetchUserInfo();
                } else {
                    String msg = response.body() != null ? response.body().getMessage() : getString(R.string.login_failed);
                    showError(msg);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<LoginData>> call, Throwable t) {
                showError(getString(NetworkFeedback.classify(LoginActivity.this)));
            }
        });
    }

    /** 获取用户信息后进入主页 */
    private void fetchUserInfo() {
        ApiClient client = ApiClient.getInstance(session);
        client.getCurrentUser().enqueue(new Callback<ApiResponse<UserInfo>>() {
            @Override
            public void onResponse(Call<ApiResponse<UserInfo>> call, Response<ApiResponse<UserInfo>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                    UserInfo user = response.body().getData();
                    session.saveUser(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
                    // 登录成功：复位离线标记（重连后回到在线模式）
                    session.saveOffline(false);
                    Toast.makeText(LoginActivity.this, getString(R.string.login_success), Toast.LENGTH_SHORT).show();
                    // 登录成功：调度同步 + 本地模式笔记上传询问
                    SyncWorker.schedule(LoginActivity.this);
                    SyncWorker.requestNow(LoginActivity.this);
                    maybePromptUploadLocal();
                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                    startActivity(intent);
                    finish();
                } else {
                    showError(getString(R.string.login_fetch_user_failed));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<UserInfo>> call, Throwable t) {
                showError(getString(NetworkFeedback.classify(LoginActivity.this)));
            }
        });
    }

    /** 本地模式创建的笔记 → 首次登录「上传前询问」 */
    private void maybePromptUploadLocal() {
        int count = AppDatabase.get(this).noteDao().countLocalOnly();
        if (count == 0) return;
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.offline_upload_prompt_title))
                .setMessage(getString(R.string.offline_upload_prompt_message, count))
                .setPositiveButton(getString(R.string.offline_upload_yes), (d, w) -> {
                    SyncService.migrateLocalNotes(this);
                    SyncWorker.requestNow(this);
                })
                .setNegativeButton(getString(R.string.offline_upload_no), null)
                .show();
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(TextView.VISIBLE);
        btnLogin.setEnabled(true);
        btnLogin.setText(getString(R.string.login_button));
    }
}
