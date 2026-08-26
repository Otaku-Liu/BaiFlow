package com.baiflow.android.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.RadioButton;


import com.baiflow.android.R;
import com.baiflow.android.auth.SessionManager;

/**
 * 语言设置页 — 选择中文 / English；持久化到 SessionManager，切换后重启任务回主界面
 * （替代 AppCompatDelegate.setApplicationLocales，避免其强制重建导致切换黑屏，见 docs/19）。
 */
public class LanguageActivity extends BaseActivity {

    private SessionManager session;
    private RadioButton rbChinese;
    private RadioButton rbEnglish;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = SessionManager.getInstance(this);
        setContentView(R.layout.activity_language);

        rbChinese = findViewById(R.id.rbChinese);
        rbEnglish = findViewById(R.id.rbEnglish);

        // 初始选中：以界面实际渲染语言为准（不能用 getApplicationLocales() 可能为空、
        // 也不能用 Locale.getDefault() 是系统默认，而非应用语言）
        updateSelection(getResources().getConfiguration().getLocales().get(0).getLanguage());

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.rowChinese).setOnClickListener(v -> applyLanguage("zh"));
        findViewById(R.id.rowEnglish).setOnClickListener(v -> applyLanguage("en"));
    }

    private void updateSelection(String lang) {
        boolean zh = "zh".equals(lang);
        rbChinese.setChecked(zh);
        rbEnglish.setChecked(!zh);
    }

    private void applyLanguage(String tag) {
        // 立即同步选中态；持久化语言，重启任务回主界面（普通启动路径走窗口底色，不闪黑）
        updateSelection(tag);
        session.saveLanguage(tag);
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
