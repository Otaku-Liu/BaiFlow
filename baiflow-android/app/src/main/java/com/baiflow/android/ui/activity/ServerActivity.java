package com.baiflow.android.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.baiflow.android.R;
import com.baiflow.android.auth.SessionManager;
import com.baiflow.android.network.ApiClient;
import com.baiflow.android.network.ServerSetup;
import com.baiflow.android.util.ServerSetupUi;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

/**
 * 服务器设置页 — 配置 BaiFlow 服务器地址，设置后才能登录。
 * <p>
 * 地址存 {@link SessionManager}（运行时设置，正式包不再把地址编进安装包）。用户填完整地址，
 * 未写协议时由 {@link SessionManager#normalizeServerUrl} 补全（带端口按 http、无端口按 https）。
 * <p>
 * 「测试连接」与「保存」都会探测一次（{@link ServerSetup}）：可达则提示成功；
 * 可达但未初始化则引导去浏览器完成首次建号；不可达时保存会弹确认框，允许坚持保存
 * （例如服务器暂时没开机）。
 */
public class ServerActivity extends BaseActivity {

    private TextInputEditText etServerUrl;
    private TextView tvStatus;
    private MaterialButton btnSave;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server);

        session = SessionManager.getInstance(this);
        etServerUrl = findViewById(R.id.etServerUrl);
        tvStatus = findViewById(R.id.tvStatus);
        btnSave = findViewById(R.id.btnSave);

        // 已配置（含调试包预填）则回填；正式包未配置时留空，强制用户自己填，
        // 否则预填的 "http://" 会让 normalizeServerUrl 认为协议已给出，scheme 推断永不触发
        etServerUrl.setText(session.getServerUrl());

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnTest).setOnClickListener(v -> testConnection());
        btnSave.setOnClickListener(v -> save());

        // 地址一改，上一次的测试结论即作废
        etServerUrl.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                tvStatus.setVisibility(View.GONE);
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });
    }

    /** 读取输入并归一化；为空时提示并返回 null */
    private String readTargetUrl() {
        String url = SessionManager.normalizeServerUrl(etServerUrl.getText() != null
                ? etServerUrl.getText().toString() : "");
        if (url.isEmpty()) {
            Toast.makeText(this, getString(R.string.server_empty), Toast.LENGTH_SHORT).show();
            return null;
        }
        return url;
    }

    /** 「测试连接」：只看结果，不保存 */
    private void testConnection() {
        String url = readTargetUrl();
        if (url == null) {
            return;
        }
        showStatus(getString(R.string.server_testing), false);
        ServerSetup.probe(this, url, (reachable, initialized, detail) -> {
            if (!reachable) {
                showStatus(getString(R.string.server_test_failed, detail), false);
                return;
            }
            // 归一化后的地址回填到输入框：用户能看到最终到底用哪个地址，而不是被静默改写
            etServerUrl.setText(url);
            etServerUrl.setSelection(url.length());
            if (initialized) {
                showStatus(getString(R.string.server_test_ok, url), true);
                return;
            }
            showStatus(getString(R.string.server_not_initialized, ServerSetupUi.setupUrl(url)), false);
            ServerSetupUi.showNotInitializedDialog(this, url);
        });
    }

    private void save() {
        final String url = readTargetUrl();
        if (url == null) {
            return;
        }
        if (url.equals(session.getServerUrl())) {
            Toast.makeText(this, getString(R.string.server_unchanged), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        // 已登录时换服务器 = 退出登录并清本机状态，先确认再动
        if (session.isLoggedIn()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.server_switch_confirm_title))
                    .setMessage(getString(R.string.server_switch_confirm_msg))
                    .setPositiveButton(getString(R.string.common_confirm), (d, w) -> testThenSave(url))
                    .setNegativeButton(getString(R.string.common_cancel), null)
                    .show();
            return;
        }
        testThenSave(url);
    }

    /** 保存前默认再测一次连接；不可达时允许用户坚持保存 */
    private void testThenSave(final String url) {
        setBusy(true);
        showStatus(getString(R.string.server_testing), false);
        ServerSetup.probe(this, url, (reachable, initialized, detail) -> {
            setBusy(false);
            if (reachable) {
                applyServer(url);
                return;
            }
            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.server_test))
                    .setMessage(getString(R.string.server_save_failed_confirm, detail))
                    .setPositiveButton(getString(R.string.server_save), (d, w) -> applyServer(url))
                    .setNegativeButton(getString(R.string.common_cancel), null)
                    .show();
        });
    }

    /** 落盘并进入登录页：换服务器会清会话，必须回到登录界面重新登录 */
    private void applyServer(String url) {
        session.setServerUrl(url);
        // baseUrl 变了：丢弃 ApiClient 内部旧地址的 Retrofit 实例
        ApiClient.onServerChanged();
        Toast.makeText(this, getString(R.string.server_saved), Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showStatus(String text, boolean ok) {
        tvStatus.setText(text);
        tvStatus.setTextColor(getColor(ok ? R.color.accent : R.color.text_secondary));
        tvStatus.setVisibility(View.VISIBLE);
    }

    private void setBusy(boolean busy) {
        btnSave.setEnabled(!busy);
    }
}
