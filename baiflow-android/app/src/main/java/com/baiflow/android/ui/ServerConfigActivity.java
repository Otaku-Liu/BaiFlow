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

/**
 * 服务器配置页 — 首次使用时输入 BaiFlow 服务器地址。
 */
public class ServerConfigActivity extends AppCompatActivity {

    private EditText etServerUrl;
    private Button btnConnect;
    private TextView tvError;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_config);

        session = SessionManager.getInstance(this);
        etServerUrl = findViewById(R.id.etServerUrl);
        btnConnect = findViewById(R.id.btnConnect);
        tvError = findViewById(R.id.tvError);

        // 自动填充上次保存的服务器地址
        if (session.getServerUrl() != null) {
            etServerUrl.setText(session.getServerUrl());
        }

        btnConnect.setOnClickListener(v -> {
            String url = etServerUrl.getText().toString().trim();
            if (url.isEmpty()) {
                tvError.setText("请输入服务器地址");
                tvError.setVisibility(TextView.VISIBLE);
                return;
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://" + url;
                etServerUrl.setText(url);
            }

            session.saveServerUrl(url);
            Toast.makeText(this, "服务器地址已保存", Toast.LENGTH_SHORT).show();

            // 跳转到登录页
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
        });
    }
}
