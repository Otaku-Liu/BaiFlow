package com.baiflow.android.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.baiflow.android.R;
import com.baiflow.android.auth.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * 主 Activity — 底部三栏（文件 / 随手记 / 我的）的容器壳。
 * <p>
 * 用 {@link ViewPager2} 承载三个 Fragment，支持左右滑动切换，与底部导航双向同步。
 * 应用入口：未登录时先引导到服务器配置/登录；已登录直接进入三栏界面。
 */
public class MainActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private BottomNavigationView bottomNav;

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

        viewPager = findViewById(R.id.viewPager);
        bottomNav = findViewById(R.id.bottomNav);

        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @Override
            public int getItemCount() {
                return 3;
            }

            @NonNull
            @Override
            public Fragment createFragment(int position) {
                return switch (position) {
                    case 0 -> new FilesFragment();
                    case 1 -> new NotesFragment();
                    default -> new MineFragment();
                };
            }
        });
        // 三个页面都保活，滑动切换不重建、状态不丢
        viewPager.setOffscreenPageLimit(2);

        // 滑动 → 同步底部导航选中
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                bottomNav.getMenu().getItem(position).setChecked(true);
            }
        });

        // 底部导航点击 → 切换页面（平滑动画）
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            int pos = id == R.id.nav_files ? 0 : id == R.id.nav_notes ? 1 : 2;
            if (viewPager.getCurrentItem() != pos) {
                viewPager.setCurrentItem(pos, true);
            }
            return true;
        });
    }
}
