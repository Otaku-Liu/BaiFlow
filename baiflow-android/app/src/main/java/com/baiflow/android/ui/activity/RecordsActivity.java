package com.baiflow.android.ui.activity;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.baiflow.android.R;
import com.baiflow.android.auth.SessionManager;
import com.baiflow.android.model.ApiResponse;
import com.baiflow.android.model.DownloadRecord;
import com.baiflow.android.model.PagedResult;
import com.baiflow.android.model.UploadRecord;
import com.baiflow.android.model.UserInfo;
import com.baiflow.android.network.ApiClient;
import com.baiflow.android.network.UiCallback;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import retrofit2.Call;
import retrofit2.Response;

/**
 * 传输记录页 — 我的/文件上传与下载历史。
 * <p>两个 Tab（上传/下载）各自独立查询；过滤：时间范围/文件名/来源；admin 可按用户查看。
 */
public class RecordsActivity extends BaseActivity {

    /** 预选 Tab：true=上传记录，false=下载记录 */
    public static final String EXTRA_UPLOAD = "extra_upload";

    private ApiClient client;

    private com.google.android.material.tabs.TabLayout tabLayout;
    private TextView btnDateRange;
    private Spinner spinnerSource;
    private Spinner spinnerUser;
    private EditText etFileName;
    private RecyclerView recyclerView;
    private TextView tvEmpty;

    private final List<Row> rows = new ArrayList<>();
    private RecordAdapter adapter;

    private boolean uploadTab = true;   // true=上传, false=下载
    private String start;
    private String end;                 // YYYY-MM-DD
    private String source = "";
    private String userId = "";         // admin 目标用户（空=全部）
    private int page = 1;
    private boolean loading;
    private boolean noMore;

    private List<UserInfo> users = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_records);

        SessionManager session = SessionManager.getInstance(this);
        client = ApiClient.getInstance(session);

        // 默认查看当天（占位时间范围随日期变化自然失效）
        uploadTab = getIntent().getBooleanExtra(EXTRA_UPLOAD, true);
        String today = java.time.LocalDate.now().toString();
        start = today;
        end = today;

        tabLayout = findViewById(R.id.tabLayout);
        btnDateRange = findViewById(R.id.btnDateRange);
        spinnerSource = findViewById(R.id.spinnerSource);
        spinnerUser = findViewById(R.id.spinnerUser);
        etFileName = findViewById(R.id.etFileName);
        recyclerView = findViewById(R.id.recyclerView);
        tvEmpty = findViewById(R.id.tvEmpty);
        btnDateRange.setText(start + " ~ " + end);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnDateRange.setOnClickListener(v -> pickDateRange());
        findViewById(R.id.btnSearch).setOnClickListener(v -> {
            page = 1;
            load();
        });

        tabLayout.addTab(tabLayout.newTab().setText(R.string.records_upload_tab));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.records_download_tab));
        tabLayout.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                uploadTab = tab.getPosition() == 0;
                setupSourceSpinner();
                page = 1;
                load();
            }

            @Override public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) { }

            @Override public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) { }
        });

        setupSourceSpinner();
        if ("ADMIN".equals(session.getRole())) {
            spinnerUser.setVisibility(View.VISIBLE);
            loadUsers();
        }

        adapter = new RecordAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm != null && lm.findLastVisibleItemPosition() >= adapter.getItemCount() - 3
                        && !loading && !noMore) {
                    load();
                }
            }
        });

        etFileName.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                page = 1;
                load();
                return true;
            }
            return false;
        });

        if (uploadTab) {
            load();
        } else {
            // 预选下载 Tab：触发 onTabSelected → 切到下载并加载
            tabLayout.selectTab(tabLayout.getTabAt(1));
        }
    }

    /** 来源下拉随 Tab 切换：上传 WEB/ANDROID，下载 CLIENT/SHARE */
    private void setupSourceSpinner() {
        String[] options = uploadTab
                ? new String[]{getString(R.string.records_source_all), "WEB", "ANDROID"}
                : new String[]{getString(R.string.records_source_all), "CLIENT", "SHARE"};
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSource.setAdapter(ad);
        spinnerSource.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                source = pos == 0 ? "" : options[pos];
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
    }

    /** admin 加载用户下拉（默认「全部用户」） */
    private void loadUsers() {
        client.listUsers(1, 100).enqueue(new UiCallback<ApiResponse<PagedResult<UserInfo>>>(this) {
            @Override
            protected void onUiResponse(Call<ApiResponse<PagedResult<UserInfo>>> call,
                                        Response<ApiResponse<PagedResult<UserInfo>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isOk()
                        && response.body().getData() != null) {
                    users = response.body().getData().getRecords() != null
                            ? response.body().getData().getRecords() : new ArrayList<>();
                    List<String> labels = new ArrayList<>();
                    labels.add(getString(R.string.records_all_users));
                    for (UserInfo u : users) {
                        labels.add(u.getDisplayName() != null && !u.getDisplayName().isEmpty()
                                ? u.getDisplayName() : u.getUsername());
                    }
                    ArrayAdapter<String> ad = new ArrayAdapter<>(RecordsActivity.this,
                            android.R.layout.simple_spinner_item, labels);
                    ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinnerUser.setAdapter(ad);
                    spinnerUser.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                            userId = pos == 0 ? "" : users.get(pos - 1).getId();
                        }
                        @Override public void onNothingSelected(AdapterView<?> p) { }
                    });
                }
            }

            @Override
            protected void onUiFailure(Call<ApiResponse<PagedResult<UserInfo>>> call, Throwable t) { }
        });
    }

    private void pickDateRange() {
        LocalDate today = LocalDate.now();
        int y = today.getYear();
        int m = today.getMonthValue() - 1;
        int d = today.getDayOfMonth();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            start = String.format(java.util.Locale.ROOT, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
            new DatePickerDialog(RecordsActivity.this, (view2, year2, month2, dayOfMonth2) -> {
                end = String.format(java.util.Locale.ROOT, "%04d-%02d-%02d", year2, month2 + 1, dayOfMonth2);
                btnDateRange.setText(start + " ~ " + end);
                page = 1;
                load();
            }, y, m, d).show();
        }, y, m, d).show();
    }

    private void load() {
        if (loading) return;
        loading = true;
        String fileFilter = etFileName.getText().toString().trim();
        String src = source.isEmpty() ? null : source;
        String uid = userId.isEmpty() ? null : userId;

        if (uploadTab) {
            client.uploadRecords(start, end, fileFilter.isEmpty() ? null : fileFilter, src, uid, page, 20)
                    .enqueue(new UiCallback<ApiResponse<PagedResult<UploadRecord>>>(this) {
                        @Override
                        protected void onUiResponse(Call<ApiResponse<PagedResult<UploadRecord>>> call,
                                                    Response<ApiResponse<PagedResult<UploadRecord>>> response) {
                            loading = false;
                            List<UploadRecord> recs = dataRecords(response);
                            if (recs != null) {
                                applyRows(recs, r -> new Row(r.getFileName(), r.getUploaderUsername(),
                                        r.getSource(), r.getIpAddress(), r.getCreatedAt()));
                            } else {
                                noMore = true;
                            }
                        }

                        @Override
                        protected void onUiFailure(Call<ApiResponse<PagedResult<UploadRecord>>> call, Throwable t) {
                            loading = false;
                        }
                    });
        } else {
            client.downloadRecords(start, end, fileFilter.isEmpty() ? null : fileFilter, src, uid, page, 20)
                    .enqueue(new UiCallback<ApiResponse<PagedResult<DownloadRecord>>>(this) {
                        @Override
                        protected void onUiResponse(Call<ApiResponse<PagedResult<DownloadRecord>>> call,
                                                    Response<ApiResponse<PagedResult<DownloadRecord>>> response) {
                            loading = false;
                            List<DownloadRecord> recs = dataRecords(response);
                            if (recs != null) {
                                applyRows(recs, r -> new Row(r.getFileName(), r.getDownloaderUsername(),
                                        r.getSource(), r.getIpAddress(), r.getCreatedAt()));
                            } else {
                                noMore = true;
                            }
                        }

                        @Override
                        protected void onUiFailure(Call<ApiResponse<PagedResult<DownloadRecord>>> call, Throwable t) {
                            loading = false;
                        }
                    });
        }
    }

    private <T> List<T> dataRecords(Response<ApiResponse<PagedResult<T>>> response) {
        if (response.isSuccessful() && response.body() != null && response.body().isOk()
                && response.body().getData() != null) {
            return response.body().getData().getRecords() != null
                    ? response.body().getData().getRecords() : new ArrayList<>();
        }
        return null;
    }

    /** 追加一页记录；首页先清空；不足一页视为没有更多 */
    private <T> void applyRows(List<T> recs, Function<T, Row> mapper) {
        if (page == 1) rows.clear();
        for (T r : recs) {
            rows.add(mapper.apply(r));
        }
        noMore = recs.size() < 20;
        page++;
        adapter.notifyDataSetChanged();
        tvEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** 单行数据（上传/下载统一形状） */
    private static class Row {
        final String fileName, username, source, ip, time;

        Row(String fileName, String username, String source, String ip, String time) {
            this.fileName = fileName == null ? "" : fileName;
            this.username = username == null ? "" : username;
            this.source = source == null ? "" : source;
            this.ip = ip == null ? "" : ip;
            this.time = time == null ? "" : time;
        }
    }

    private class RecordAdapter extends RecyclerView.Adapter<RecordAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int pos) {
            Row r = rows.get(pos);
            holder.title.setText(r.fileName + "  [" + r.source + "]");
            String meta = r.time.replace('T', ' ');
            if (!r.username.isEmpty()) meta = r.username + " · " + meta;
            if (!r.ip.isEmpty()) meta = meta + " · " + r.ip;
            holder.subtitle.setText(meta);
        }

        @Override
        public int getItemCount() { return rows.size(); }

        class VH extends RecyclerView.ViewHolder {
            final TextView title, subtitle;

            VH(View v) {
                super(v);
                title = v.findViewById(android.R.id.text1);
                subtitle = v.findViewById(android.R.id.text2);
            }
        }
    }
}
