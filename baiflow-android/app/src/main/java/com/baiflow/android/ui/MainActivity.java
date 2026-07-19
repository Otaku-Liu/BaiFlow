package com.baiflow.android.ui;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.baiflow.android.auth.SessionManager;

/**
 * 主 Activity — 应用入口，根据登录状态决定跳转目标。
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SessionManager session = SessionManager.getInstance(this);

        Intent intent;
        if (session.isLoggedIn()) {
            // 已登录：直接进入文件列表
            intent = new Intent(this, FileListActivity.class);
        } else if (session.getServerUrl() != null) {
            // 有服务器地址但未登录：进入登录页
            intent = new Intent(this, LoginActivity.class);
        } else {
            // 首次使用：进入服务器配置页
            intent = new Intent(this, ServerConfigActivity.class);
        }
        startActivity(intent);
        finish();
    }
}
