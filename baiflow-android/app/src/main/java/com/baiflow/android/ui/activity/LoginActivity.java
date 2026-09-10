package com.baiflow.android.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.baiflow.android.R;
import com.baiflow.android.auth.SessionManager;
import com.baiflow.android.data.AppDatabase;
import com.baiflow.android.data.SyncService;
import com.baiflow.android.model.ApiResponse;
import com.baiflow.android.model.LoginData;
import com.baiflow.android.model.UserInfo;
import com.baiflow.android.network.ApiClient;
import com.baiflow.android.network.NetworkFeedback;
import com.baiflow.android.network.ServerSetup;
import com.baiflow.android.util.ServerSetupUi;
import com.baiflow.android.sync.SyncWorker;
import com.baiflow.android.util.KeyboardUtil;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 登录页 — 用户名密码登录 BaiFlow 服务器。
 * <p>
 * 必须先配置服务器地址（未配置时点登录会引导去「服务器设置」）；
 * 服务器尚未完成首次初始化时，登录失败会明确提示去浏览器建号。
 */
public class LoginActivity extends BaseActivity {

    /** 后端 ErrorCode.INVALID_CREDENTIALS：用户名或密码错误 */
    private static final int CODE_INVALID_CREDENTIALS = 40102;

    private EditText etUsername, etPassword;
    private Button btnLogin;
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
        tvError = findViewById(R.id.tvError);
        tvServerUrl = findViewById(R.id.tvServerUrl);

        // 地址栏可点：任何时候都能进「服务器设置」
        tvServerUrl.setOnClickListener(v -> openServerSettings());

        btnLogin.setOnClickListener(v -> doLogin());
    }

    /** 从设置页返回时刷新地址显示（地址可能已改） */
    @Override
    protected void onResume() {
        super.onResume();
        refreshServerRow();
    }

    private void refreshServerRow() {
        if (session.hasServer()) {
            tvServerUrl.setText(getString(R.string.login_server_prefix, session.getServerUrl()));
        } else {
            tvServerUrl.setText(getString(R.string.login_no_server));
        }
    }

    private void openServerSettings() {
        startActivity(new Intent(this, ServerActivity.class));
    }

    /** 没配服务器就不让登录：弹框引导去设置页 */
    private boolean requireServer() {
        if (session.hasServer()) {
            return true;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.server_title))
                .setMessage(getString(R.string.login_no_server))
                .setPositiveButton(getString(R.string.login_go_settings), (d, w) -> openServerSettings())
                .setNegativeButton(getString(R.string.common_cancel), null)
                .show();
        return false;
    }

    /** 点击空白区域（非输入框）收起键盘并让当前输入框失焦 */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        KeyboardUtil.hideOnTouchOutside(this, ev);
        return super.dispatchTouchEvent(ev);
    }

    private void doLogin() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            tvError.setText(getString(R.string.login_input_required));
            tvError.setVisibility(TextView.VISIBLE);
            return;
        }
        if (!requireServer()) {
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
                    // 用户名或密码错误时多问一句：首次部署还没建管理员的话，任何账号都会走到这里
                    if (response.body() != null && response.body().getCode() == CODE_INVALID_CREDENTIALS) {
                        checkServerInitialized();
                    }
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
                    session.saveUser(user.getId(), user.getUsername(), user.getDisplayName(), user.getAvatarUrl(), user.getRole());
                    Toast.makeText(LoginActivity.this, getString(R.string.login_success), Toast.LENGTH_SHORT).show();
                    // 登录成功：调度同步 + 遗留本地笔记上传询问
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

    /** 遗留本地分区笔记 → 首次登录「上传前询问」 */
    private void maybePromptUploadLocal() {
        int count = AppDatabase.get(this).noteDao().countLocalOnly();
        if (count == 0) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.offline_upload_prompt_title))
                .setMessage(getString(R.string.offline_upload_prompt_message, count))
                .setPositiveButton(getString(R.string.offline_upload_yes), (d, w) -> {
                    SyncService.migrateLocalNotes(this);
                    SyncWorker.requestNow(this);
                })
                .setNegativeButton(getString(R.string.offline_upload_no), null)
                .show();
    }

    /**
     * 确认服务器是否尚未完成首次初始化 —— 是的话明确告知用户去浏览器建号。
     * <p>
     * 首次部署还没有管理员时，任何账号密码都是「用户名或密码错误」，
     * 不提示的话用户会以为是自己记错了密码。
     */
    private void checkServerInitialized() {
        if (!session.hasServer()) {
            return;
        }
        final String baseUrl = session.getServerUrl();
        ServerSetup.probe(this, baseUrl, (reachable, initialized, detail) -> {
            if (!reachable || initialized || isFinishing() || isDestroyed()) {
                return;
            }
            showError(getString(R.string.login_server_not_initialized, ServerSetupUi.setupUrl(baseUrl)));
            ServerSetupUi.showNotInitializedDialog(this, baseUrl);
        });
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(TextView.VISIBLE);
        btnLogin.setEnabled(true);
        btnLogin.setText(getString(R.string.login_button));
    }
}
