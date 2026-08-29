package com.baiflow.android.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Bundle;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.baiflow.android.R;
import com.baiflow.android.auth.SessionManager;
import com.baiflow.android.network.NetworkFeedback;
import com.baiflow.android.ui.fragment.FilesFragment;
import com.baiflow.android.ui.fragment.MineFragment;
import com.baiflow.android.ui.fragment.NotesFragment;
import com.baiflow.android.util.KeyboardUtil;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * 主 Activity — 底部三栏（文件 / 随手记 / 我的）的容器壳。
 * <p>
 * 用 {@link ViewPager2} 承载三个 Fragment，支持左右滑动切换，与底部导航双向同步。
 * 应用入口：未登录时进登录页；已登录直接进入三栏界面。
 * 注册设备网络监听：断网即时提示「无网络连接」，恢复时提示「网络已恢复」（见 docs/05-android.md「失败处理」）。
 */
public class MainActivity extends BaseActivity {

    private ViewPager2 viewPager;
    private BottomNavigationView bottomNav;
    private ConnectivityManager.NetworkCallback networkCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SessionManager session = SessionManager.getInstance(this);

        // 仅在线模式：已登录 → 主界面；未登录 → 登录页（登录门槛）
        if (!session.isOnlineMode()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        registerNetworkListener();

        // 周期后台同步 + 实时 SSE 长连接（收到 NOTE_UPDATED 立即同步）
        com.baiflow.android.sync.SyncWorker.schedule(this);
        com.baiflow.android.sync.NoteSseClient.getInstance().start(this);

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

    /** 点击空白区域（非输入框）收起键盘并让搜索框失焦 */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        KeyboardUtil.hideOnTouchOutside(this, ev);
        return super.dispatchTouchEvent(ev);
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
        // 页面销毁（登出/离线等）停掉实时长连接
        com.baiflow.android.sync.NoteSseClient.getInstance().stop();
    }
}
