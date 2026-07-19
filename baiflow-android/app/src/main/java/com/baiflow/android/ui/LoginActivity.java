package com.baiflow.android.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.baiflow.android.R;
import com.baiflow.android.auth.SessionManager;
import com.baiflow.android.model.ApiResponse;
import com.baiflow.android.model.LoginData;
import com.baiflow.android.model.UserInfo;
import com.baiflow.android.network.ApiClient;
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

        tvServerUrl.setText("服务器：" + session.getServerUrl());

        btnLogin.setOnClickListener(v -> doLogin());
        btnChangeServer.setOnClickListener(v -> {
            Intent intent = new Intent(this, ServerConfigActivity.class);
            startActivity(intent);
            finish();
        });
    }

    private void doLogin() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            tvError.setText("请输入用户名和密码");
            tvError.setVisibility(TextView.VISIBLE);
            return;
        }

        btnLogin.setEnabled(false);
        btnLogin.setText("登录中...");
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
                    String msg = response.body() != null ? response.body().getMessage() : "登录失败";
                    showError(msg);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<LoginData>> call, Throwable t) {
                showError("网络错误：" + t.getMessage());
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
                    session.saveUser(user.getUsername(), user.getDisplayName(), user.getRole());
                    Toast.makeText(LoginActivity.this, "登录成功", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(LoginActivity.this, FileListActivity.class);
                    startActivity(intent);
                    finish();
                } else {
                    showError("获取用户信息失败");
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<UserInfo>> call, Throwable t) {
                showError("网络错误：" + t.getMessage());
            }
        });
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(TextView.VISIBLE);
        btnLogin.setEnabled(true);
        btnLogin.setText("登 录");
    }
}
