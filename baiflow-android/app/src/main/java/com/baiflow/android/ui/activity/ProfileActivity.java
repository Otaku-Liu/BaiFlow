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
 * 修改资料页 — 编辑展示名称、更换头像并保存。
 * 头像为即时上传（点头像直接选图、选完就传），展示名称需点「保存」提交。
 */
public class ProfileActivity extends BaseActivity {

    private static final int AVATAR_MAX_DIMENSION = 512;

    private SessionManager session;
    private ApiClient client;
    private EditText etDisplayName;
    private TextView tvAvatarLetter;
    private TextView tvAvatarEditBand;
    private ImageView ivAvatar;
    private View avatarContainer;

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
        tvAvatarEditBand = findViewById(R.id.tvAvatarEditBand);
        ivAvatar = findViewById(R.id.ivAvatar);
        avatarContainer = findViewById(R.id.avatarContainer);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSave).setOnClickListener(v -> saveProfile());
        // 整个头像圆（含底部「编辑」带）点击后直接拉起系统图片选择器
        avatarContainer.setOnClickListener(v -> pickAvatar.launch("image/*"));

        loadCurrentAvatar();
    }

    /** 上传中：编辑带改显「上传中…」并禁用整圆点击，避免重复选图 */
    private void setUploading(boolean uploading) {
        tvAvatarEditBand.setText(uploading ? R.string.mine_avatar_uploading : R.string.mine_avatar_edit);
        avatarContainer.setEnabled(!uploading);
    }

    /** 切换头像图片与首字占位的显隐 */
    private void showAvatarImage(boolean show) {
        ivAvatar.setVisibility(show ? View.VISIBLE : View.GONE);
        tvAvatarLetter.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    /** 加载当前头像预览（后台拉取；无头像时展示展示名首字占位） */
    private void loadCurrentAvatar() {
        tvAvatarLetter.setText(FormatUtil.firstCharOrQuestion(session.getDisplayName()));

        String fullUrl = AvatarLoader.resolveUrl(session.getServerUrl(), session.getAvatarUrl());
        if (fullUrl == null) {
            showAvatarImage(false);
            return;
        }
        AvatarLoader.loadInto(ivAvatar, fullUrl,
                () -> showAvatarImage(true),
                () -> showAvatarImage(false));
    }

    /** 选图后：后台缩放压缩 → 上传 → 更新本地会话与预览；每条结束分支都要还原「上传中」态 */
    private void uploadAvatar(Uri uri) {
        setUploading(true);
        new Thread(() -> {
            byte[] jpeg = ImageUtil.scaleDown(getApplicationContext(), uri, AVATAR_MAX_DIMENSION, 1024 * 1024);
            if (jpeg == null) {
                runOnUiThread(() -> {
                    setUploading(false);
                    Toast.makeText(ProfileActivity.this,
                            R.string.mine_avatar_upload_failed, Toast.LENGTH_SHORT).show();
                });
                return;
            }
            client.uploadAvatar(jpeg, "avatar.jpg", "image/jpeg")
                    .enqueue(new UiCallback<ApiResponse<UserInfo>>(ProfileActivity.this) {
                        @Override
                        protected void onUiResponse(Call<ApiResponse<UserInfo>> call, Response<ApiResponse<UserInfo>> response) {
                            setUploading(false);
                            if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                                UserInfo data = response.body().getData();
                                if (data != null && data.getAvatarUrl() != null && !data.getAvatarUrl().isEmpty()) {
                                    session.saveAvatarUrl(data.getAvatarUrl());
                                }
                                Toast.makeText(ProfileActivity.this, R.string.mine_avatar_updated, Toast.LENGTH_SHORT).show();
                                // 直接用刚选的原图刷新预览（后台解码，≤512px 很快）
                                new Thread(() -> {
                                    Bitmap preview = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
                                    runOnUiThread(() -> {
                                        if (preview != null) {
                                            ivAvatar.setImageBitmap(preview);
                                            showAvatarImage(true);
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
                            // 网络失败已由 UiCallback 统一提示，这里只还原上传态
                            setUploading(false);
                        }
                    });
        }).start();
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
