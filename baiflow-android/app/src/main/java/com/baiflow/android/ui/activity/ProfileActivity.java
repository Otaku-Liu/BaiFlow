package com.baiflow.android.ui.activity;

import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.baiflow.android.R;
import com.baiflow.android.auth.SessionManager;
import com.baiflow.android.model.ApiResponse;
import com.baiflow.android.model.UserInfo;
import com.baiflow.android.network.ApiClient;
import com.baiflow.android.network.UiCallback;
import com.baiflow.android.util.KeyboardUtil;

import retrofit2.Call;
import retrofit2.Response;

/**
 * 修改资料页 — 编辑展示名称并保存。
 */
public class ProfileActivity extends AppCompatActivity {

    private SessionManager session;
    private ApiClient client;
    private EditText etDisplayName;

    /** 点击空白区域（非输入框）收起键盘并让当前输入框失焦 */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        KeyboardUtil.hideOnTouchOutside(this, ev);
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        session = SessionManager.getInstance(this);
        client = ApiClient.getInstance(session);
        etDisplayName = findViewById(R.id.etDisplayName);
        etDisplayName.setText(session.getDisplayName() != null ? session.getDisplayName() : "");

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSave).setOnClickListener(v -> saveProfile());
    }

    private void saveProfile() {
        String name = etDisplayName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, getString(R.string.mine_fill_all_fields), Toast.LENGTH_SHORT).show();
            return;
        }
        client.updateProfile(name).enqueue(new UiCallback<ApiResponse<UserInfo>>(this) {
            @Override
            protected void onUiResponse(Call<ApiResponse<UserInfo>> call, Response<ApiResponse<UserInfo>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                    session.saveDisplayName(name);
                    Toast.makeText(ProfileActivity.this, getString(R.string.mine_profile_updated), Toast.LENGTH_SHORT).show();
                    finish();
                } else if (response.code() < 500) {
                    String msg = response.body() != null ? response.body().getMessage() : getString(R.string.common_save_failed);
                    Toast.makeText(ProfileActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            protected void onUiFailure(Call<ApiResponse<UserInfo>> call, Throwable t) {
                // 网络失败已由 UiCallback 统一提示
            }
        });
    }
}
