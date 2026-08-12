package com.baiflow.android.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.baiflow.android.R;
import com.baiflow.android.auth.SessionManager;
import com.baiflow.android.data.AppDatabase;
import com.baiflow.android.network.ApiClient;
import com.baiflow.android.network.NetworkFeedback;

/**
 * 服务器配置页 — 输入 BaiFlow 服务器地址，并检测连通性（经 ApiClient 请求 /api/health）。
 * <p>
 * 连通成功才保存地址并进入登录页；失败则提示，不跳转。
 * 注意：在 Android 模拟器中连接本机后端请用 {@code http://10.0.2.2:8080}
 * （10.0.2.2 指向宿主机回环；不要用 127.0.0.1，那是模拟器自身）。
 */
public class ServerConfigActivity extends AppCompatActivity {

    private EditText etServerUrl;
    private Button btnConnect;
    private TextView tvError;
    private SessionManager session;
    private ApiClient client;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_config);

        session = SessionManager.getInstance(this);
        client = ApiClient.getInstance(session);
        etServerUrl = findViewById(R.id.etServerUrl);
        btnConnect = findViewById(R.id.btnConnect);
        tvError = findViewById(R.id.tvError);

        // 顶部返回按钮
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // 自动填充上次保存的服务器地址
        if (session.getServerUrl() != null) {
            etServerUrl.setText(session.getServerUrl());
        }

        btnConnect.setOnClickListener(v -> testAndConnect());
    }

    private void testAndConnect() {
        String url = etServerUrl.getText().toString().trim();
        if (url.isEmpty()) {
            showError(getString(R.string.server_url_required));
            return;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
            etServerUrl.setText(url);
        }
        // 去掉末尾斜杠（session.saveServerUrl 也会规范化，保持一致）
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        setLoading(true);
        showError("");

        final String finalUrl = url;
        new Thread(() -> {
            boolean ok = client.checkHealth(finalUrl);
            mainHandler.post(() -> {
                setLoading(false);
                if (ok) {
                    // 换服务器时清掉旧服务器的本地缓存与同步基准，防数据串号/漏拉（见 docs/12 §8）
                    String oldUrl = session.getServerUrl();
                    if (oldUrl != null && !oldUrl.equals(finalUrl)) {
                        AppDatabase.get(this).noteDao().clearByServer(oldUrl);
                        session.saveLastSyncAt(null);
                    }
                    session.saveServerUrl(finalUrl);
                    Toast.makeText(this, getString(R.string.server_connect_success), Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, LoginActivity.class));
                    finish();
                } else {
                    // 友好文案：设备无网 → 无网络连接；否则 → 无法连接服务器
                    showError(getString(NetworkFeedback.classify(this)));
                }
            });
        }).start();
    }

    private void setLoading(boolean loading) {
        btnConnect.setEnabled(!loading);
        btnConnect.setText(loading ? getString(R.string.server_checking) : getString(R.string.server_connect));
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(msg.isEmpty() ? TextView.GONE : TextView.VISIBLE);
    }
}
