package com.baiflow.android.ui.activity;

import android.app.DatePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
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
import com.baiflow.android.util.DownloadLocationStore;
import com.baiflow.android.util.DownloadUtil;
import com.baiflow.android.util.FileTypeIcon;
import com.baiflow.android.util.FormatUtil;
import com.baiflow.android.util.MimeUtil;
import com.baiflow.android.widget.DropdownMenu;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import retrofit2.Call;
import retrofit2.Response;

/**
 * 传输记录页 — 我的/文件上传或下载历史（类型由 EXTRA_UPLOAD 决定，页内不提供 Tab 切换）。
 * <p>过滤：时间范围/文件名/来源 + 重置；admin 可按用户查看。
 * <p>记录行 = 文件类型图标 + 文件名 + 时间；长摁弹详情（复用文件中心弹窗）支持下载/删除源文件/打开文件。
 */
public class RecordsActivity extends BaseActivity {

    /** 记录类型：true=上传记录，false=下载记录 */
    public static final String EXTRA_UPLOAD = "extra_upload";

    private ApiClient client;

    private View rowDateRange;
    private TextView tvDateRange;
    private View rowSource;
    private TextView tvSource;
    private View rowUser;
    private TextView tvUser;
    private View dividerSourceUser;
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

    private final List<String> userLabels = new ArrayList<>();
    private final List<String> userIds = new ArrayList<>();

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

        rowDateRange = findViewById(R.id.rowDateRange);
        tvDateRange = findViewById(R.id.tvDateRange);
        rowSource = findViewById(R.id.rowSource);
        tvSource = findViewById(R.id.tvSource);
        rowUser = findViewById(R.id.rowUser);
        tvUser = findViewById(R.id.tvUser);
        dividerSourceUser = findViewById(R.id.dividerSourceUser);
        etFileName = findViewById(R.id.etFileName);
        recyclerView = findViewById(R.id.recyclerView);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvDateRange.setText(start + " ~ " + end);

        // 标题按入口类型显示（我的页已区分上传/下载入口，页内不再提供 Tab 切换）
        ((TextView) findViewById(R.id.tvTitle)).setText(
                getString(uploadTab ? R.string.records_upload_tab : R.string.records_download_tab));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        rowDateRange.setOnClickListener(v -> pickDateRange());
        findViewById(R.id.btnSearch).setOnClickListener(v -> {
            page = 1;
            load();
        });
        findViewById(R.id.btnReset).setOnClickListener(v -> resetFilters());

        setupSourceField();
        if ("ADMIN".equals(session.getRole())) {
            rowUser.setVisibility(View.VISIBLE);
            dividerSourceUser.setVisibility(View.VISIBLE);
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

        load();
    }

    /** 重置全部查询条件到默认（当天），并重新查询 */
    private void resetFilters() {
        String today = java.time.LocalDate.now().toString();
        start = today;
        end = today;
        source = "";
        userId = "";
        tvDateRange.setText(start + " ~ " + end);
        tvSource.setText(getString(R.string.records_source_all));
        tvUser.setText(getString(R.string.records_all_users));
        etFileName.setText("");
        page = 1;
        load();
    }

    /** 来源字段：随 Tab 切换选项（上传 WEB/ANDROID，下载 CLIENT/SHARE），DropdownMenu 选择 */
    private void setupSourceField() {
        source = "";
        tvSource.setText(getString(R.string.records_source_all));
        rowSource.setOnClickListener(v -> showSourceMenu(v));
    }

    private void showSourceMenu(View anchor) {
        List<DropdownMenu.Option> options = new ArrayList<>();
        options.add(new DropdownMenu.Option(getString(R.string.records_source_all), source.isEmpty(), 0,
                () -> selectSource("")));
        if (uploadTab) {
            options.add(new DropdownMenu.Option("WEB", "WEB".equals(source), 0, () -> selectSource("WEB")));
            options.add(new DropdownMenu.Option("ANDROID", "ANDROID".equals(source), 0, () -> selectSource("ANDROID")));
        } else {
            options.add(new DropdownMenu.Option("CLIENT", "CLIENT".equals(source), 0, () -> selectSource("CLIENT")));
            options.add(new DropdownMenu.Option("SHARE", "SHARE".equals(source), 0, () -> selectSource("SHARE")));
        }
        DropdownMenu.show(this, anchor, options);
    }

    private void selectSource(String value) {
        source = value;
        tvSource.setText(value.isEmpty() ? getString(R.string.records_source_all) : value);
        page = 1;
        load();
    }

    /** admin 加载用户列表（字段行默认「全部用户」，DropdownMenu 选择） */
    private void loadUsers() {
        client.listUsers(1, 100).enqueue(new UiCallback<ApiResponse<PagedResult<UserInfo>>>(this) {
            @Override
            protected void onUiResponse(Call<ApiResponse<PagedResult<UserInfo>>> call,
                                        Response<ApiResponse<PagedResult<UserInfo>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isOk()
                        && response.body().getData() != null) {
                    userLabels.clear();
                    userIds.clear();
                    List<UserInfo> users = response.body().getData().getRecords() != null
                            ? response.body().getData().getRecords() : new ArrayList<>();
                    for (UserInfo u : users) {
                        userLabels.add(u.getDisplayName() != null && !u.getDisplayName().isEmpty()
                                ? u.getDisplayName() : u.getUsername());
                        userIds.add(u.getId());
                    }
                    tvUser.setText(getString(R.string.records_all_users));
                    rowUser.setOnClickListener(v -> showUserMenu(v));
                }
            }

            @Override
            protected void onUiFailure(Call<ApiResponse<PagedResult<UserInfo>>> call, Throwable t) { }
        });
    }

    private void showUserMenu(View anchor) {
        List<DropdownMenu.Option> options = new ArrayList<>();
        options.add(new DropdownMenu.Option(getString(R.string.records_all_users), userId.isEmpty(), 0,
                () -> selectUser("")));
        for (int i = 0; i < userIds.size(); i++) {
            String id = userIds.get(i);
            options.add(new DropdownMenu.Option(userLabels.get(i), id.equals(userId), 0, () -> selectUser(id)));
        }
        DropdownMenu.show(this, anchor, options);
    }

    private void selectUser(String id) {
        userId = id;
        tvUser.setText(id.isEmpty() ? getString(R.string.records_all_users) : userLabelOf(id));
        page = 1;
        load();
    }

    private String userLabelOf(String id) {
        for (int i = 0; i < userIds.size(); i++) {
            if (id.equals(userIds.get(i))) {
                return userLabels.get(i);
            }
        }
        return id;
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
                tvDateRange.setText(start + " ~ " + end);
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
                                applyRows(recs, r -> new Row(r.getFileId(), r.getFileName(),
                                        r.getSource(), r.getCreatedAt()));
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
                                applyRows(recs, r -> new Row(r.getFileId(), r.getFileName(),
                                        r.getSource(), r.getCreatedAt()));
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

    /** ISO 时间 → 可读串；空/无效回落原始值（T 换空格） */
    private String formatTime(String iso) {
        String formatted = FormatUtil.formatDateTime(iso);
        return formatted.isEmpty() ? (iso != null ? iso.replace('T', ' ') : "") : formatted;
    }

    /** 长摁记录行：弹详情（复用文件中心弹窗）— 图标/文件名/来源/时间 + 下载/删除该文件 */
    private void showRecordDialog(Row row) {
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_file_info, null);
        ((ImageView) content.findViewById(R.id.dialogFileIcon))
                .setImageResource(FileTypeIcon.forName(row.fileName, null));
        ((TextView) content.findViewById(R.id.dialogFileName)).setText(row.fileName);
        ((TextView) content.findViewById(R.id.dialogFileMeta))
                .setText(getString(R.string.records_source_fmt,
                        row.source.isEmpty() ? "-" : row.source));
        ((TextView) content.findViewById(R.id.dialogFileCreated))
                .setText(getString(uploadTab ? R.string.records_upload_time_fmt : R.string.records_download_time_fmt,
                        formatTime(row.time)));
        content.findViewById(R.id.dialogFileModified).setVisibility(View.GONE);
        content.findViewById(R.id.dialogFileLastOpened).setVisibility(View.GONE);
        content.findViewById(R.id.actionRename).setVisibility(View.GONE);
        content.findViewById(R.id.dividerRenameDownload).setVisibility(View.GONE);
        // 弹窗默认隐藏下载/删除动作（文件中心按需显示）；记录详情两者都展示
        content.findViewById(R.id.actionDownload).setVisibility(View.VISIBLE);
        content.findViewById(R.id.dividerDownloadDelete).setVisibility(View.VISIBLE);

        // 下载记录：本机下载过则展示保存位置（可点复制）+ 打开文件（系统查看器）；上传记录无此能力
        final DownloadLocationStore.Location loc =
                !uploadTab ? DownloadLocationStore.load(this, row.fileId) : null;
        if (loc != null) {
            TextView tvLocation = content.findViewById(R.id.dialogFileLocation);
            tvLocation.setText(getString(R.string.records_saved_to,
                    getString(R.string.records_download_dir) + "/" + loc.displayName));
            tvLocation.setVisibility(View.VISIBLE);
            tvLocation.setOnClickListener(v -> copyLocation(loc));
            content.findViewById(R.id.actionOpen).setVisibility(View.VISIBLE);
            content.findViewById(R.id.dividerOpenDownload).setVisibility(View.VISIBLE);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(content)
                .setCancelable(true)
                .show();

        content.findViewById(R.id.actionOpen).setOnClickListener(v -> {
            dialog.dismiss();
            if (loc != null) {
                openDownloadedFile(loc, row.fileName);
            }
        });
        content.findViewById(R.id.actionDownload).setOnClickListener(v -> {
            dialog.dismiss();
            // 记录无文件大小，传 0（通知显示「未知大小」）；后端自动计入下载记录（CLIENT）
            DownloadUtil.startDownloadService(this, row.fileId, row.fileName, 0L);
            Toast.makeText(this, getString(R.string.files_download_started, row.fileName), Toast.LENGTH_SHORT).show();
        });
        content.findViewById(R.id.actionDelete).setOnClickListener(v -> {
            dialog.dismiss();
            confirmDeleteRecordFile(row);
        });
    }

    /** 复制保存位置（实际 URI/路径）到剪贴板 */
    private void copyLocation(DownloadLocationStore.Location loc) {
        String value = loc.uri != null ? loc.uri : loc.filePath;
        if (value == null) {
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(getString(R.string.records_clip_label), value));
        Toast.makeText(this, getString(R.string.records_copied), Toast.LENGTH_SHORT).show();
    }

    /** 用 Android 系统查看器打开已下载文件（ACTION_VIEW，非内置预览）；失败提示 */
    private void openDownloadedFile(DownloadLocationStore.Location loc, String fileName) {
        try {
            Uri uri;
            if (loc.uri != null) {
                // API 29+：MediaStore content URI 可直接打开
                uri = Uri.parse(loc.uri);
            } else {
                // API 26-28：公共 Download 目录文件需经 FileProvider 暴露（file:// 受限）
                uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider",
                        new java.io.File(loc.filePath));
            }
            Intent view = new Intent(Intent.ACTION_VIEW);
            view.setDataAndType(uri, MimeUtil.guessFromName(fileName));
            view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(view);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.records_open_failed), Toast.LENGTH_SHORT).show();
        }
    }

    /** 删除文件二次确认：删服务器真实文件（记录保留） */
    private void confirmDeleteRecordFile(Row row) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.records_delete_file_title))
                .setMessage(getString(R.string.records_delete_file_message, row.fileName))
                .setPositiveButton(getString(R.string.files_delete_immediately), (d, w) -> deleteRecordFile(row))
                .setNegativeButton(getString(R.string.common_cancel), null)
                .show();
    }

    private void deleteRecordFile(Row row) {
        client.deleteFile(row.fileId, null).enqueue(new UiCallback<ApiResponse<Map<String, Object>>>(this) {
            @Override
            protected void onUiResponse(Call<ApiResponse<Map<String, Object>>> call,
                                        Response<ApiResponse<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                    Toast.makeText(RecordsActivity.this, getString(R.string.common_deleted), Toast.LENGTH_SHORT).show();
                } else if (response.body() != null && response.body().getCode() == 40401) {
                    // 源文件已不存在：给「未找到」专用提示，记录保留
                    Toast.makeText(RecordsActivity.this, getString(R.string.records_source_missing),
                            Toast.LENGTH_SHORT).show();
                } else if (response.body() != null) {
                    Toast.makeText(RecordsActivity.this, response.body().getMessage(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            protected void onUiFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                // 网络失败已由 UiCallback 统一提示
            }
        });
    }

    /** 单行数据（上传/下载统一形状；仅保留详情所需字段） */
    private static class Row {
        final String fileId, fileName, source, time;

        Row(String fileId, String fileName, String source, String time) {
            this.fileId = fileId == null ? "" : fileId;
            this.fileName = fileName == null ? "" : fileName;
            this.source = source == null ? "" : source;
            this.time = time == null ? "" : time;
        }
    }

    private class RecordAdapter extends RecyclerView.Adapter<RecordAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_record, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int pos) {
            Row r = rows.get(pos);
            holder.ivIcon.setImageResource(FileTypeIcon.forName(r.fileName, null));
            holder.tvName.setText(r.fileName);
            holder.tvTime.setText(formatTime(r.time));
            holder.itemView.setOnLongClickListener(v -> {
                showRecordDialog(r);
                return true;
            });
        }

        @Override
        public int getItemCount() { return rows.size(); }

        class VH extends RecyclerView.ViewHolder {
            final ImageView ivIcon;
            final TextView tvName, tvTime;

            VH(View v) {
                super(v);
                ivIcon = v.findViewById(R.id.ivIcon);
                tvName = v.findViewById(R.id.tvName);
                tvTime = v.findViewById(R.id.tvTime);
            }
        }
    }
}
