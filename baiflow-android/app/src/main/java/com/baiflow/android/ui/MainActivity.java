package com.baiflow.android.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.baiflow.android.R;
import com.baiflow.android.auth.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * 主 Activity — 底部三栏（文件 / 随手记 / 我的）的容器壳。
 * <p>
 * 应用入口：未登录时先引导到服务器配置/登录；已登录直接进入三栏界面。
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SessionManager session = SessionManager.getInstance(this);
        if (!session.isLoggedIn()) {
            // 未登录：引导到服务器配置或登录页
            Intent intent = session.getServerUrl() != null
                    ? new Intent(this, LoginActivity.class)
                    : new Intent(this, ServerConfigActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);

        if (savedInstanceState == null) {
            FilesFragment files = new FilesFragment();
            NotesFragment notes = new NotesFragment();
            MineFragment mine = new MineFragment();
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragmentContainer, files, "files")
                    .add(R.id.fragmentContainer, notes, "notes")
                    .hide(notes)
                    .add(R.id.fragmentContainer, mine, "mine")
                    .hide(mine)
                    .commit();
        }

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            FragmentManager fm = getSupportFragmentManager();
            if (id == R.id.nav_files) show(fm, "files");
            else if (id == R.id.nav_notes) show(fm, "notes");
            else if (id == R.id.nav_mine) show(fm, "mine");
            return true;
        });
    }

    /** 只显示指定 fragment，隐藏其余两个（保留状态） */
    private void show(FragmentManager fm, String tag) {
        Fragment target = fm.findFragmentByTag(tag);
        if (target == null) return;
        FragmentTransaction t = fm.beginTransaction().show(target);
        if (!"files".equals(tag) && fm.findFragmentByTag("files") != null) t.hide(fm.findFragmentByTag("files"));
        if (!"notes".equals(tag) && fm.findFragmentByTag("notes") != null) t.hide(fm.findFragmentByTag("notes"));
        if (!"mine".equals(tag) && fm.findFragmentByTag("mine") != null) t.hide(fm.findFragmentByTag("mine"));
        t.commitAllowingStateLoss();
    }
}
