package com.baiflow.android.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.baiflow.android.R;
import com.baiflow.android.auth.SessionManager;
import com.baiflow.android.model.*;
import com.baiflow.android.network.ApiClient;
import com.baiflow.android.network.UiCallback;
import com.baiflow.android.util.FormatUtil;
import retrofit2.Call;
import retrofit2.Response;
import java.util.*;

/**
 * 下载任务列表页 — 查看服务器上的下载任务状态（进度、速度等）。
 */
public class TransferListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private TaskAdapter adapter;
    private ApiClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transfer_list);

        com.baiflow.android.auth.SessionManager session = com.baiflow.android.auth.SessionManager.getInstance(this);
        client = ApiClient.getInstance(session);
        recyclerView = findViewById(R.id.recyclerView);
        tvEmpty = findViewById(R.id.tvEmpty);

        // 顶部返回按钮
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // 本地/离线模式：传输不可用
        if (!session.isOnlineMode()) {
            tvEmpty.setText(getString(R.string.transfer_offline_unavailable));
            tvEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            return;
        }

        adapter = new TaskAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        loadTasks();
    }

    private void loadTasks() {
        client.listDownloads(null, 1, 50).enqueue(new UiCallback<ApiResponse<PagedResult<DownloadTask>>>(this) {
            @Override
            protected void onUiResponse(Call<ApiResponse<PagedResult<DownloadTask>>> call,
                                        Response<ApiResponse<PagedResult<DownloadTask>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                    PagedResult<DownloadTask> result = response.body().getData();
                    List<DownloadTask> tasks = result != null ? result.getRecords() : new ArrayList<>();
                    adapter.setItems(tasks);
                    tvEmpty.setVisibility(tasks.isEmpty() ? View.VISIBLE : View.GONE);
                    recyclerView.setVisibility(tasks.isEmpty() ? View.GONE : View.VISIBLE);
                } else if (response.code() < 500) {
                    Toast.makeText(TransferListActivity.this, getString(R.string.common_load_failed), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            protected void onUiFailure(Call<ApiResponse<PagedResult<DownloadTask>>> call, Throwable t) {
                // 网络失败已由 UiCallback 统一提示
            }
        });
    }

    class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.ViewHolder> {
        private List<DownloadTask> items = new ArrayList<>();

        void setItems(List<DownloadTask> items) { this.items = items; notifyDataSetChanged(); }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_download_task, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int pos) {
            DownloadTask task = items.get(pos);

            holder.tvFileName.setText(task.getFileName() != null ? task.getFileName() : task.getSourceUrl());
            holder.tvStatus.setText(statusLabel(task.getStatus()));

            boolean running = "RUNNING".equals(task.getStatus());
            holder.progressBar.setVisibility(running ? View.VISIBLE : View.GONE);
            if (running) {
                holder.progressBar.setProgress(task.getProgress());
            }

            if (task.getSpeedBytesPerSecond() > 0) {
                holder.tvSpeed.setVisibility(View.VISIBLE);
                holder.tvSpeed.setText(formatSpeed(task.getSpeedBytesPerSecond()) + " | " +
                        FormatUtil.formatSize(task.getCompletedBytes()) + " / " + FormatUtil.formatSize(task.getTotalBytes()));
            } else if (task.getErrorMessage() != null && !task.getErrorMessage().isEmpty()) {
                holder.tvSpeed.setVisibility(View.VISIBLE);
                holder.tvSpeed.setText(getString(R.string.transfer_error, task.getErrorMessage()));
            } else {
                holder.tvSpeed.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() { return items.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvFileName, tvStatus, tvSpeed;
            ProgressBar progressBar;
            ViewHolder(View v) {
                super(v);
                tvFileName = v.findViewById(R.id.tvFileName);
                tvStatus = v.findViewById(R.id.tvStatus);
                tvSpeed = v.findViewById(R.id.tvSpeed);
                progressBar = v.findViewById(R.id.progressBar);
            }
        }
    }

    private String statusLabel(String status) {
        switch (status) {
            case "WAITING": return getString(R.string.transfer_status_waiting);
            case "RUNNING": return getString(R.string.transfer_status_running);
            case "PAUSED": return getString(R.string.transfer_status_paused);
            case "COMPLETED": return getString(R.string.transfer_status_completed);
            case "FAILED": return getString(R.string.transfer_status_failed);
            default: return status;
        }
    }

    private String formatSpeed(long bytesPerSec) {
        return FormatUtil.formatSize(bytesPerSec) + "/s";
    }
}
