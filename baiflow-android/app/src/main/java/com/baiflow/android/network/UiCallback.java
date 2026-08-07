package com.baiflow.android.network;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 交互层统一网络错误处理的 Retrofit 回调包装。
 * <p>
 * - onResponse：收到任何响应即记录成功联系（清除断连时段，必要时提示恢复）；
 *   HTTP 5xx 全局兜底提示「服务器异常」，非 5xx 清除 5xx 去重标记。
 * - onFailure：网络级失败（IOException）统一分类提示，页面无需再写网络错误 Toast。
 * <p>
 * 见 docs/11-android-network-error.md。后台传输服务用 {@code execute()} 同步调用，
 * 不经过本包装，因此不会对后台任务弹 UI。
 */
public abstract class UiCallback<T> implements Callback<T> {

    private final Context context;

    public UiCallback(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public final void onResponse(@NonNull Call<T> call, @NonNull Response<T> response) {
        NetworkFeedback.reportContact(context);
        if (response.code() >= 500) {
            NetworkFeedback.reportServerError(context);
        } else {
            NetworkFeedback.reportServerOk(context);
        }
        onUiResponse(call, response);
    }

    @Override
    public final void onFailure(@NonNull Call<T> call, @NonNull Throwable t) {
        if (t instanceof IOException) {
            NetworkFeedback.reportFailure(context);
        }
        onUiFailure(call, t);
    }

    /** 业务成功/失败处理（收到 HTTP 响应，含 4xx/5xx 业务错误） */
    protected abstract void onUiResponse(Call<T> call, Response<T> response);

    /** 请求失败处理（网络失败已统一提示，这里只做必要的页面状态兜底） */
    protected abstract void onUiFailure(Call<T> call, Throwable t);
}
