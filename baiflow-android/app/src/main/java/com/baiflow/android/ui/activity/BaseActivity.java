package com.baiflow.android.ui.activity;

import android.content.Context;
import android.content.res.Configuration;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.baiflow.android.auth.SessionManager;

import java.util.Locale;

/**
 * 公共基类：统一应用持久化的应用语言。
 * <p>
 * 语言切换不再用 {@code AppCompatDelegate.setApplicationLocales}（其强制系统级重建全部
 * Activity，切换会闪黑屏），改为「SessionManager 持久化 + 重启任务」路径；本基类在
 * {@link #attachBaseContext(Context)} 把持久化语言应用到每个 Activity 的 Context，
 * 新页面创建即按新语言渲染，窗口背景走应用底色（灰）不闪黑。见 docs/19。
 */
public class BaseActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(applyLanguageContext(newBase));
    }

    @Override
    public void applyOverrideConfiguration(@Nullable Configuration overrideConfiguration) {
        if (overrideConfiguration != null) {
            // 保留当前 UI 模式（深/浅色），避免 createConfigurationContext 重置掉
            int uiMode = overrideConfiguration.uiMode;
            Configuration config = new Configuration();
            config.setTo(getBaseContext().getResources().getConfiguration());
            config.uiMode = uiMode;
            overrideConfiguration.setTo(config);
        }
        super.applyOverrideConfiguration(overrideConfiguration);
    }

    /** 把持久化的应用语言应用到基础 Context；未设置语言时返回原样（用系统默认） */
    private Context applyLanguageContext(Context base) {
        String lang = SessionManager.getInstance(base).getLanguage();
        if (lang == null || lang.isEmpty()) {
            return base;
        }
        Locale locale = Locale.forLanguageTag(lang);
        Locale.setDefault(locale);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(locale);
        return base.createConfigurationContext(config);
    }
}
