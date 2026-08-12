package com.baiflow.android.ui.activity;

import android.os.Bundle;
import android.widget.RadioButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.baiflow.android.R;

/**
 * 语言设置页 — 选择中文 / English；AppCompat 持久化并自动重建应用语言。
 */
public class LanguageActivity extends AppCompatActivity {

    private RadioButton rbChinese;
    private RadioButton rbEnglish;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        // 立即同步选中态；setApplicationLocales 触发重建是异步的，不能等重建才反映
        updateSelection(tag);
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag));
    }
}
