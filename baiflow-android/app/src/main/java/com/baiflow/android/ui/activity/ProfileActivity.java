package com.baiflow.android.ui.activity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.baiflow.android.R;
import com.baiflow.android.auth.SessionManager;
import com.baiflow.android.model.ApiResponse;
import com.baiflow.android.model.UserInfo;
import com.baiflow.android.network.ApiClient;
import com.baiflow.android.network.UiCallback;
import com.baiflow.android.util.AvatarLoader;
import com.baiflow.android.util.FormatUtil;
import com.baiflow.android.util.ImageUtil;
import com.baiflow.android.util.KeyboardUtil;

import retrofit2.Call;
import retrofit2.Response;

/**
 * 修改资料页 — 编辑展示名称、更换/删除头像并保存。
 */
public class ProfileActivity extends AppCompatActivity {

    private static final int AVATAR_MAX_DIMENSION = 512;

    private SessionManager session;
    private ApiClient client;
    private EditText etDisplayName;
    private TextView tvAvatarLetter;
    private ImageView ivAvatar;
    private View btnDeleteAvatar;

    /** 头像选择（系统图片选择器，无需运行时权限） */
    private final ActivityResultLauncher<String> pickAvatar =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    uploadAvatar(uri);
                }
            });

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
        tvAvatarLetter = findViewById(R.id.tvAvatarLetter);
        ivAvatar = findViewById(R.id.ivAvatar);
        btnDeleteAvatar = findViewById(R.id.btnDeleteAvatar);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSave).setOnClickListener(v -> saveProfile());
        findViewById(R.id.btnChangeAvatar).setOnClickListener(v -> pickAvatar.launch("image/*"));
        btnDeleteAvatar.setOnClickListener(v -> confirmDeleteAvatar());

        loadCurrentAvatar();
    }

    /** 加载当前头像预览（后台拉取；无头像时展示展示名首字占位并隐藏删除按钮） */
    private void loadCurrentAvatar() {
        tvAvatarLetter.setText(FormatUtil.firstCharOrQuestion(session.getDisplayName()));

        String fullUrl = AvatarLoader.resolveUrl(session.getServerUrl(), session.getAvatarUrl());
        if (fullUrl == null) {
            ivAvatar.setVisibility(View.GONE);
            btnDeleteAvatar.setVisibility(View.GONE);
            return;
        }
        btnDeleteAvatar.setVisibility(View.VISIBLE);
        AvatarLoader.loadInto(ivAvatar, fullUrl,
                () -> ivAvatar.setVisibility(View.VISIBLE),
                () -> ivAvatar.setVisibility(View.GONE));
    }

    /** 选图后：后台缩放压缩 → 上传 → 更新本地会话与预览 */
    private void uploadAvatar(Uri uri) {
        new Thread(() -> {
            byte[] jpeg = ImageUtil.scaleDown(getApplicationContext(), uri, AVATAR_MAX_DIMENSION, 1024 * 1024);
            if (jpeg == null) {
                runOnUiThread(() -> Toast.makeText(ProfileActivity.this,
                        R.string.mine_avatar_upload_failed, Toast.LENGTH_SHORT).show());
                return;
            }
            client.uploadAvatar(jpeg, "avatar.jpg", "image/jpeg")
                    .enqueue(new UiCallback<ApiResponse<UserInfo>>(ProfileActivity.this) {
                        @Override
                        protected void onUiResponse(Call<ApiResponse<UserInfo>> call, Response<ApiResponse<UserInfo>> response) {
                            if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                                UserInfo data = response.body().getData();
                                if (data != null && data.getAvatarUrl() != null && !data.getAvatarUrl().isEmpty()) {
                                    session.saveAvatarUrl(data.getAvatarUrl());
                                }
                                Toast.makeText(ProfileActivity.this, R.string.mine_avatar_updated, Toast.LENGTH_SHORT).show();
                                btnDeleteAvatar.setVisibility(View.VISIBLE);
                                // 直接用刚选的原图刷新预览（后台解码，≤512px 很快）
                                new Thread(() -> {
                                    Bitmap preview = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
                                    runOnUiThread(() -> {
                                        if (preview != null) {
                                            ivAvatar.setImageBitmap(preview);
                                            ivAvatar.setVisibility(View.VISIBLE);
                                        }
                                    });
                                }).start();
                            } else if (response.code() < 500) {
                                String msg = response.body() != null ? response.body().getMessage()
                                        : getString(R.string.mine_avatar_upload_failed);
                                Toast.makeText(ProfileActivity.this, msg, Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        protected void onUiFailure(Call<ApiResponse<UserInfo>> call, Throwable t) {
                            // 网络失败已由 UiCallback 统一提示
                        }
                    });
        }).start();
    }

    /** 删除头像：二次确认 → 调接口 → 清本地会话 → 回到首字占位 */
    private void confirmDeleteAvatar() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.mine_delete_avatar))
                .setMessage(getString(R.string.mine_avatar_delete_confirm))
                .setPositiveButton(getString(R.string.common_confirm), (d, w) -> deleteAvatar())
                .setNegativeButton(getString(R.string.common_cancel), null)
                .show();
    }

    private void deleteAvatar() {
        client.deleteAvatar().enqueue(new UiCallback<ApiResponse<UserInfo>>(this) {
            @Override
            protected void onUiResponse(Call<ApiResponse<UserInfo>> call, Response<ApiResponse<UserInfo>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                    // "无头像"与服务端约定为空字符串
                    session.saveAvatarUrl("");
                    Toast.makeText(ProfileActivity.this, R.string.mine_avatar_deleted, Toast.LENGTH_SHORT).show();
                    ivAvatar.setVisibility(View.GONE);
                    btnDeleteAvatar.setVisibility(View.GONE);
                } else if (response.code() < 500) {
                    String msg = response.body() != null ? response.body().getMessage()
                            : getString(R.string.mine_avatar_delete_failed);
                    Toast.makeText(ProfileActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            protected void onUiFailure(Call<ApiResponse<UserInfo>> call, Throwable t) {
                // 网络失败已由 UiCallback 统一提示
            }
        });
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
