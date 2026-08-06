package com.baiflow.android.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.baiflow.android.R;
import com.baiflow.android.auth.SessionManager;
import com.baiflow.android.model.ApiResponse;
import com.baiflow.android.model.NoteSummary;
import com.baiflow.android.model.PagedResult;
import com.baiflow.android.model.UserInfo;
import com.baiflow.android.network.ApiClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 随手记列表页（底部「随手记」栏）— 列表 / 搜索 / 新建 / 删除，管理员可切换查看用户。
 * 点击项或「新建」进入 {@link NoteEditActivity}。
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
    private ApiClient client;

    private final List<UserInfo> users = new ArrayList<>();
    private String currentViewUserId;   // 管理员切换目标（null = 全部）
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
        client = ApiClient.getInstance(session);

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

        swipeRefresh.setOnRefreshListener(this::loadNotes);
        btnNew.setOnClickListener(v -> openEditor(null, null));

        // 搜索：防抖 500ms
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
                searchRunnable = () -> {
                    searchKeyword = etSearch.getText().toString().trim();
                    loadNotes();
                };
                searchHandler.postDelayed(searchRunnable, 500);
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        // 管理员显示用户切换
        if ("ADMIN".equals(session.getRole())) {
            adminRow.setVisibility(View.VISIBLE);
            loadUsers();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // 从编辑器返回（MainActivity 重新 resume）时刷新列表，让新建/编辑/删除立即生效
        loadNotes();
    }

    private void loadNotes() {
        showLoading(true);
        swipeRefresh.setRefreshing(true);

        client.listNotes(searchKeyword.isEmpty() ? null : searchKeyword,
                        currentViewUserId, 1, 100)
                .enqueue(new Callback<ApiResponse<PagedResult<NoteSummary>>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<PagedResult<NoteSummary>>> call,
                                           Response<ApiResponse<PagedResult<NoteSummary>>> response) {
                        swipeRefresh.setRefreshing(false);
                        showLoading(false);
                        if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                            PagedResult<NoteSummary> result = response.body().getData();
                            List<NoteSummary> items = result != null ? result.getRecords() : new ArrayList<>();
                            adapter.setItems(items);
                            tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                            recyclerView.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
                        } else {
                            String msg = response.body() != null ? response.body().getMessage() : getString(R.string.common_load_failed);
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<PagedResult<NoteSummary>>> call, Throwable t) {
                        swipeRefresh.setRefreshing(false);
                        showLoading(false);
                        Toast.makeText(requireContext(), getString(R.string.common_network_error, t.getMessage()), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /** 管理员用户切换（对齐文件页）：默认「我的文件」，可切换其他用户 / 全部 */
    private void loadUsers() {
        users.clear();
        fetchUsersPage(1);
    }

    private void fetchUsersPage(final int page) {
        client.listUsers(page, 100).enqueue(new Callback<ApiResponse<PagedResult<UserInfo>>>() {
            @Override
            public void onResponse(Call<ApiResponse<PagedResult<UserInfo>>> call,
                                   Response<ApiResponse<PagedResult<UserInfo>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                    PagedResult<UserInfo> result = response.body().getData();
                    List<UserInfo> records = result != null ? result.getRecords() : new ArrayList<>();
                    users.addAll(records);
                    if (records.size() == 100) {
                        fetchUsersPage(page + 1);
                    } else {
                        populateUserSpinner();
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<PagedResult<UserInfo>>> call, Throwable t) {
                Toast.makeText(requireContext(), getString(R.string.files_load_users_failed), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void populateUserSpinner() {
        final List<String> names = new ArrayList<>();
        final List<String> targetIds = new ArrayList<>();
        String myId = session.getUserId();

        names.add(getString(R.string.notes_my_notes));
        targetIds.add(myId != null ? myId : "");

        for (UserInfo u : users) {
            if (u.getId() != null && u.getId().equals(myId)) continue;
            String display = u.getDisplayName() != null ? u.getDisplayName() : u.getUsername();
            names.add(getString(R.string.files_user_display, display, u.getUsername()));
            targetIds.add(u.getId());
        }
        names.add(getString(R.string.files_all_users));
        targetIds.add("");

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, names);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerUser.setAdapter(spinnerAdapter);

        spinnerUser.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                String target = targetIds.get(pos);
                currentViewUserId = target.isEmpty() ? null : target;
                loadNotes();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void openEditor(String noteId, String title) {
        Intent intent = new Intent(requireContext(), NoteEditActivity.class);
        if (noteId != null) {
            intent.putExtra(NoteEditActivity.EXTRA_NOTE_ID, noteId);
            intent.putExtra(NoteEditActivity.EXTRA_TITLE, title);
        }
        startActivity(intent);
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // ==================== Adapter ====================

    class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.ViewHolder> {
        private List<NoteSummary> items = new ArrayList<>();

        void setItems(List<NoteSummary> items) { this.items = items; notifyDataSetChanged(); }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int pos) {
            NoteSummary note = items.get(pos);
            holder.tvTitle.setText(note.getTitle() != null && !note.getTitle().isEmpty()
                    ? note.getTitle() : getString(R.string.notes_untitled));
            holder.tvTime.setText(formatTime(note.getUpdatedAt()));

            holder.itemView.setOnClickListener(v -> openEditor(note.getId(), note.getTitle()));
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

    private void confirmDelete(NoteSummary note) {
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.common_confirm_delete))
                .setMessage(getString(R.string.common_delete_message,
                        note.getTitle() == null || note.getTitle().isEmpty()
                                ? getString(R.string.notes_untitled) : note.getTitle()))
                .setPositiveButton(getString(R.string.common_delete), (dialog, which) -> {
                    client.deleteNote(note.getId()).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                        @Override
                        public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                               Response<ApiResponse<Map<String, Object>>> response) {
                            if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                                Toast.makeText(requireContext(), getString(R.string.common_deleted), Toast.LENGTH_SHORT).show();
                                loadNotes();
                            } else {
                                String msg = response.body() != null ? response.body().getMessage() : getString(R.string.common_delete_failed);
                                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                            Toast.makeText(requireContext(), getString(R.string.common_network_error_short), Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton(getString(R.string.common_cancel), null)
                .show();
    }

    /** 时间显示：取到分钟，去掉 T */
    static String formatTime(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        String s = iso.length() > 16 ? iso.substring(0, 16) : iso;
        return s.replace('T', ' ');
    }
}
