package com.baiflow.android.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.baiflow.android.R;
import com.baiflow.android.auth.SessionManager;
import com.baiflow.android.data.AppDatabase;
import com.baiflow.android.data.LocalNote;
import com.baiflow.android.data.LocalNoteDao;
import com.baiflow.android.data.SyncService;
import com.baiflow.android.sync.SyncWorker;

import java.util.ArrayList;
import java.util.List;

/**
 * 随手记列表页（底部「随手记」栏）— 列表 / 搜索 / 新建 / 删除。
 * <p>
 * 离线模式三态共用：数据一律读本地 Room（分区键 = 服务器地址 或 LOCAL）；在线模式
 * resume/下拉时后台同步并刷新。管理员用户切换仅在线模式显示。
 * 见 docs/12-android-offline-mode.md。
 */
public class NotesFragment extends Fragment {

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvEmpty;
    private ProgressBar progressBar;
    private View adminRow;
    private Spinner spinnerUser;
    private EditText etSearch;
    private NoteAdapter adapter;
    private SessionManager session;
    private LocalNoteDao dao;

    private String searchKeyword = "";
    private final android.os.Handler searchHandler = new android.os.Handler();
    private Runnable searchRunnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notes, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        session = SessionManager.getInstance(requireContext());
        dao = AppDatabase.get(requireContext()).noteDao();

        recyclerView = view.findViewById(R.id.recyclerView);
        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        progressBar = view.findViewById(R.id.progressBar);
        adminRow = view.findViewById(R.id.adminRow);
        spinnerUser = view.findViewById(R.id.spinnerUser);
        etSearch = view.findViewById(R.id.etSearch);
        View btnNew = view.findViewById(R.id.btnNew);

        adapter = new NoteAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(() -> {
            reload();
            syncAndReload();
        });
        btnNew.setOnClickListener(v -> openEditor(null));

        // 搜索：防抖 500ms（本地模糊过滤）
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
                searchRunnable = () -> {
                    searchKeyword = etSearch.getText().toString().trim();
                    reload();
                };
                searchHandler.postDelayed(searchRunnable, 500);
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        // 离线优先：本地分区为本人笔记，不再支持管理员按用户切换（见 docs/12 §9 边界）
        adminRow.setVisibility(View.GONE);

        reload();
    }

    @Override
    public void onResume() {
        super.onResume();
        // 从编辑器返回刷新；在线模式触发一次后台同步后刷新
        reload();
        syncAndReload();
    }

    /** 读本地 Room 渲染列表（三态通用） */
    private void reload() {
        String partition = session.getDataPartition();
        List<LocalNote> items = searchKeyword.isEmpty()
                ? dao.listByServer(partition)
                : dao.search(partition, "%" + searchKeyword + "%");
        adapter.setItems(items != null ? items : new ArrayList<>());
        tvEmpty.setVisibility(items == null || items.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(items == null || items.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** 在线模式：后台同步一次，完成后刷新列表 */
    private void syncAndReload() {
        if (!session.isOnlineMode()) return;
        android.content.Context ctx = requireContext();
        new Thread(() -> {
            SyncService.syncOnce(ctx);
            if (getActivity() != null) {
                getActivity().runOnUiThread(this::reload);
            }
        }).start();
    }

    private void openEditor(LocalNote note) {
        android.content.Intent intent = new android.content.Intent(requireContext(), NoteEditActivity.class);
        if (note != null) {
            intent.putExtra(NoteEditActivity.EXTRA_LOCAL_ID, note.id);
        }
        startActivity(intent);
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // ==================== Adapter ====================

    class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.ViewHolder> {
        private List<LocalNote> items = new ArrayList<>();

        void setItems(List<LocalNote> items) { this.items = items; notifyDataSetChanged(); }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int pos) {
            LocalNote note = items.get(pos);
            holder.tvTitle.setText(note.title != null && !note.title.isEmpty()
                    ? note.title : getString(R.string.notes_untitled));
            holder.tvTime.setText(formatTime(note.updatedAt));

            holder.itemView.setOnClickListener(v -> openEditor(note));
            holder.itemView.setOnLongClickListener(v -> {
                confirmDelete(note);
                return true;
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvTime;
            ViewHolder(View v) {
                super(v);
                tvTitle = v.findViewById(R.id.tvTitle);
                tvTime = v.findViewById(R.id.tvTime);
            }
        }
    }

    private void confirmDelete(LocalNote note) {
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.common_confirm_delete))
                .setMessage(getString(R.string.common_delete_message,
                        note.title == null || note.title.isEmpty()
                                ? getString(R.string.notes_untitled) : note.title))
                .setPositiveButton(getString(R.string.common_delete), (dialog, which) -> {
                    if (note.serverId == null) {
                        // 从未上传：直接删本地行
                        dao.delete(note);
                    } else {
                        // 已同步：标记 tombstone，重连同步删除服务端
                        note.source = SyncService.SOURCE_TOMBSTONE;
                        note.dirty = true;
                        note.updatedAt = System.currentTimeMillis();
                        dao.update(note);
                        if (session.isOnlineMode()) {
                            SyncWorker.requestNow(requireContext());
                        }
                    }
                    reload();
                })
                .setNegativeButton(getString(R.string.common_cancel), null)
                .show();
    }

    /** 时间显示：epoch millis → "yyyy-MM-dd HH:mm" */
    static String formatTime(long epochMillis) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(epochMillis));
    }
}
