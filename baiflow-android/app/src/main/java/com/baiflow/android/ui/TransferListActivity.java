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
import retrofit2.Call;
import retrofit2.Callback;
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

        client = ApiClient.getInstance(SessionManager.getInstance(this));
        recyclerView = findViewById(R.id.recyclerView);
        tvEmpty = findViewById(R.id.tvEmpty);

        adapter = new TaskAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        loadTasks();
    }

    private void loadTasks() {
        client.listDownloads(null, 1, 50).enqueue(new Callback<ApiResponse<PagedResult<DownloadTask>>>() {
            @Override
            public void onResponse(Call<ApiResponse<PagedResult<DownloadTask>>> call,
                                   Response<ApiResponse<PagedResult<DownloadTask>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                    PagedResult<DownloadTask> result = response.body().getData();
                    List<DownloadTask> tasks = result != null ? result.getRecords() : new ArrayList<>();
                    adapter.setItems(tasks);
                    tvEmpty.setVisibility(tasks.isEmpty() ? View.VISIBLE : View.GONE);
                    recyclerView.setVisibility(tasks.isEmpty() ? View.GONE : View.VISIBLE);
                } else {
                    Toast.makeText(TransferListActivity.this, "加载失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<PagedResult<DownloadTask>>> call, Throwable t) {
                Toast.makeText(TransferListActivity.this, "网络错误：" + t.getMessage(), Toast.LENGTH_SHORT).show();
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
                        formatSize(task.getCompletedBytes()) + " / " + formatSize(task.getTotalBytes()));
            } else if (task.getErrorMessage() != null && !task.getErrorMessage().isEmpty()) {
                holder.tvSpeed.setVisibility(View.VISIBLE);
                holder.tvSpeed.setText("错误：" + task.getErrorMessage());
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
            case "WAITING": return "等待中";
            case "RUNNING": return "下载中";
            case "PAUSED": return "已暂停";
            case "COMPLETED": return "已完成";
            case "FAILED": return "失败";
            default: return status;
        }
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) { return "0 B"; }
        if (bytes < 1024) { return bytes + " B"; }
        if (bytes < 1024 * 1024) { return String.format("%.1f KB", bytes / 1024.0); }
        if (bytes < 1024 * 1024 * 1024) { return String.format("%.1f MB", bytes / (1024.0 * 1024)); }
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private String formatSpeed(long bytesPerSec) {
        return formatSize(bytesPerSec) + "/s";
    }
}
