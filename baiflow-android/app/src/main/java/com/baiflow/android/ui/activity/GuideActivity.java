package com.baiflow.android.ui.activity;

import android.content.Intent;
import android.os.Bundle;


import com.baiflow.android.R;
import com.baiflow.android.auth.SessionManager;

/**
 * 首次启动引导页 — 「设置服务器」/「暂不，先本地使用」二选一。
 * 见 docs/05-android.md「离线三态」。
 */
public class GuideActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guide);

        SessionManager session = SessionManager.getInstance(this);

        findViewById(R.id.btnSetServer).setOnClickListener(v -> {
            session.saveGuideShown();
            startActivity(new Intent(this, ServerConfigActivity.class));
            finish();
        });
        findViewById(R.id.btnLocalOnly).setOnClickListener(v -> {
            session.saveGuideShown();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }
}
