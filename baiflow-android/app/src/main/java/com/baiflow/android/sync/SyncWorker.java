package com.baiflow.android.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.baiflow.android.data.SyncService;

import java.util.concurrent.TimeUnit;

/**
 * 笔记后台同步 Worker — 网络恢复/周期触发；离线/本地模式不执行（SyncService 内判断）。
 * 见 docs/05-android.md「离线三态」。
 */
public class SyncWorker extends Worker {

    private static final String PERIODIC = "note_sync_periodic";
    private static final String NOW = "note_sync_now";

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        boolean ok = SyncService.syncOnce(getApplicationContext());
        return ok ? Result.success() : Result.retry();
    }

    /** 登录/重连后调度周期同步（在线模式）；离线/登出后调用 {@link #cancel} */
    public static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(SyncWorker.class, 1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req);
    }

    public static void cancel(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC);
    }

    /** 立即同步一次（登录/重连/手动「同步」按钮） */
    public static void requestNow(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(context)
                .enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, req);
    }
}
