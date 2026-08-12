package com.baiflow.android.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.baiflow.android.R;
import com.baiflow.android.auth.SessionManager;
import com.baiflow.android.network.NetworkFeedback;
import com.baiflow.android.ui.fragment.FilesFragment;
import com.baiflow.android.ui.fragment.MineFragment;
import com.baiflow.android.ui.fragment.NotesFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * 主 Activity — 底部三栏（文件 / 随手记 / 我的）的容器壳。
 * <p>
 * 用 {@link ViewPager2} 承载三个 Fragment，支持左右滑动切换，与底部导航双向同步。
 * 应用入口：未登录时先引导到服务器配置/登录；已登录直接进入三栏界面。
 * 注册设备网络监听：断网即时提示「无网络连接」，恢复时提示「网络已恢复」（见 docs/11-android-network-error.md）。
 */
public class MainActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private BottomNavigationView bottomNav;
    private ConnectivityManager.NetworkCallback networkCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SessionManager session = SessionManager.getInstance(this);

        // 首次启动（未配服务器）：引导页「设置服务器 / 先本地用」
        if (!session.isGuideShown() && session.getServerUrl() == null) {
            startActivity(new Intent(this, GuideActivity.class));
            finish();
            return;
        }
        session.saveGuideShown();

        // 三态分发（见 docs/12-android-offline-mode.md §4）：
        // 本地模式 / 在线模式 / 离线模式 → 主界面；服务器已设但未登录且未离线 → 登录页（登录门槛）
        if (!session.isLocalMode() && !session.isOnlineMode() && !session.isOfflineMode()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        registerNetworkListener();

        // 在线模式：周期后台同步；离线/本地模式暂停
        if (session.isOnlineMode()) {
            com.baiflow.android.sync.SyncWorker.schedule(this);
        } else {
            com.baiflow.android.sync.SyncWorker.cancel(this);
        }

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

    /** 注册设备网络监听：断网即时提示，恢复时提示「网络已恢复」 */
    private void registerNetworkListener() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                NetworkFeedback.reportContact(MainActivity.this);
            }

            @Override
            public void onLost(@NonNull Network network) {
                NetworkFeedback.reportDeviceOffline(MainActivity.this);
            }
        };
        cm.registerDefaultNetworkCallback(networkCallback);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (networkCallback != null) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                cm.unregisterNetworkCallback(networkCallback);
            }
            networkCallback = null;
        }
    }
}
