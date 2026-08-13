package com.baiflow.android.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.baiflow.android.R;
import com.baiflow.android.auth.SessionManager;
import com.baiflow.android.data.AppDatabase;
import com.baiflow.android.model.ApiResponse;
import com.baiflow.android.network.ApiClient;
import com.baiflow.android.network.UiCallback;
import com.baiflow.android.sync.SyncWorker;
import com.baiflow.android.util.KeyboardUtil;

import java.util.Map;

import retrofit2.Call;
import retrofit2.Response;

/**
 * 修改密码页 — 验证旧密码后更新；服务端吊销全部会话，所有设备强制下线重新登录。
 */
public class PasswordActivity extends AppCompatActivity {

    private SessionManager session;
    private ApiClient client;
    private EditText etOldPassword, etNewPassword, etConfirmPassword;
    private TextView tvError;

    /** 点击空白区域（非输入框）收起键盘并让当前输入框失焦 */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        KeyboardUtil.hideOnTouchOutside(this, ev);
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_password);

        session = SessionManager.getInstance(this);
        client = ApiClient.getInstance(session);
        etOldPassword = findViewById(R.id.etOldPassword);
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        tvError = findViewById(R.id.tvError);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnConfirm).setOnClickListener(v -> changePassword());
    }

    private void changePassword() {
        String oldP = etOldPassword.getText().toString();
        String newP = etNewPassword.getText().toString();
        String confirmP = etConfirmPassword.getText().toString();
        tvError.setVisibility(TextView.GONE);

        if (oldP.isEmpty() || newP.isEmpty() || confirmP.isEmpty()) {
            showError(getString(R.string.mine_fill_all_fields));
            return;
        }
        if (!newP.equals(confirmP)) {
            showError(getString(R.string.mine_password_mismatch));
            return;
        }

        client.changePassword(oldP, newP).enqueue(new UiCallback<ApiResponse<Map<String, Object>>>(this) {
            @Override
            protected void onUiResponse(Call<ApiResponse<Map<String, Object>>> call,
                                        Response<ApiResponse<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                    Toast.makeText(PasswordActivity.this, getString(R.string.mine_password_changed), Toast.LENGTH_SHORT).show();
                    doLogout();
                } else if (response.code() < 500) {
                    String msg = response.body() != null ? response.body().getMessage() : getString(R.string.mine_password_change_failed);
                    showError(msg);
                }
            }

            @Override
            protected void onUiFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                // 网络失败已由 UiCallback 统一提示
            }
        });
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(msg.isEmpty() ? TextView.GONE : TextView.VISIBLE);
    }

    /** 密码修改成功 → 清会话回登录页（服务端已吊销全部会话，当前设备也需重新登录） */
    private void doLogout() {
        String partition = session.getDataPartition();
        session.clearSession();
        if (!SessionManager.PARTITION_LOCAL.equals(partition)) {
            AppDatabase.get(this).noteDao().clearByServer(partition);
        }
        SyncWorker.cancel(this);
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}
