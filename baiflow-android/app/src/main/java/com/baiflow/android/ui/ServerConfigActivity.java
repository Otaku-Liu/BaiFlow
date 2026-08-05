package com.baiflow.android.ui;

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
import com.baiflow.android.network.ApiClient;

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
            showError("请输入服务器地址");
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
                    session.saveServerUrl(finalUrl);
                    Toast.makeText(this, "连接成功，服务器地址已保存", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, LoginActivity.class));
                    finish();
                } else {
                    showError("无法连接服务器（" + finalUrl + "）\n请检查地址、网络，或确认后端已启动");
                }
            });
        }).start();
    }

    private void setLoading(boolean loading) {
        btnConnect.setEnabled(!loading);
        btnConnect.setText(loading ? "检测中…" : "连接服务器");
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(msg.isEmpty() ? TextView.GONE : TextView.VISIBLE);
    }
}
