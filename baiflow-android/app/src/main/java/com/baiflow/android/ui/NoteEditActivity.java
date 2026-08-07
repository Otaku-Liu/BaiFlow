package com.baiflow.android.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.baiflow.android.R;
import com.baiflow.android.auth.SessionManager;
import com.baiflow.android.editor.EditorStyle;
import com.baiflow.android.editor.MarkdownEmitter;
import com.baiflow.android.editor.MarkdownParser;
import com.baiflow.android.editor.ModelToSpanned;
import com.baiflow.android.editor.NoteAudioSpan;
import com.baiflow.android.editor.NoteBulletSpan;
import com.baiflow.android.editor.NoteHeadingSpan;
import com.baiflow.android.editor.NoteImageSpan;
import com.baiflow.android.editor.NoteInlineCodeSpan;
import com.baiflow.android.editor.NoteOrderedSpan;
import com.baiflow.android.editor.ParagraphHelper;
import com.baiflow.android.editor.RichEditText;
import com.baiflow.android.editor.SpanExtractor;
import com.baiflow.android.data.AppDatabase;
import com.baiflow.android.data.LocalNote;
import com.baiflow.android.data.LocalNoteDao;
import com.baiflow.android.data.MediaFiles;
import com.baiflow.android.data.SyncService;
import com.baiflow.android.model.ApiResponse;
import com.baiflow.android.model.NoteDetail;
import com.baiflow.android.model.NoteMedia;
import com.baiflow.android.network.ApiClient;
import com.baiflow.android.network.UiCallback;
import com.baiflow.android.sync.SyncWorker;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

/**
 * 随手记富文本编辑器页 — 所见即所得 Markdown 编辑。
 * <p>
 * 加载：Markdown → {@link MarkdownParser} → Doc → {@link ModelToSpanned} → Spannable。
 * 保存：Spannable → {@link SpanExtractor} → Doc → {@link MarkdownEmitter} → Markdown。
 * 支持工具栏格式化（加粗/斜体/删除线/H1-H3/列表/引用/代码块/链接）、图片插入、
 * 录音插入、画画插入；返回时自动保存。媒体经服务器笔记媒体接口上传，引用写进正文。
 */
public class NoteEditActivity extends AppCompatActivity {

    public static final String EXTRA_LOCAL_ID = "local_id";
    public static final String EXTRA_TITLE = "note_title";

    private static final String TAG = "NoteEdit";

    private SessionManager session;
    private ApiClient client;
    private EditorStyle editorStyle;
    private LocalNoteDao dao;

    private EditText etTitle;
    private RichEditText etContent;

    private NoteImageSpan replaceTarget;     // 替换旧图的目标 span

    private LocalNote currentNote;           // 当前编辑的本地笔记（null = 新建）
    private boolean dirty = false;
    private boolean saving = false;

    // 工具栏
    private com.google.android.material.button.MaterialButton
            btnBold, btnItalic, btnStrike, btnH1, btnH2, btnBullet, btnMore;
    private com.google.android.material.button.MaterialButton
            btnH3, btnOrdered, btnInlineCode, btnLink, btnImage, btnAudio, btnDraw;
    private View moreRow;

    // 媒体加载
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // 图片位图 LRU 缓存（access-order，上限 64 张），避免重复拉取
    private final Map<String, Bitmap> bitmapCache = java.util.Collections.synchronizedMap(
            new LinkedHashMap<String, Bitmap>(32, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
                    return size() > 64;
                }
            });

    private MediaPlayer audioPlayer;

    // 图片选择（普通插入）
    private final ActivityResultLauncher<String> imagePicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                NoteImageSpan target = replaceTarget;
                replaceTarget = null;   // 一次性消费替换目标，避免下次选择误替换
                readImageAndInsert(uri, target);
            });

    // 画画结果
    private final ActivityResultLauncher<Intent> drawLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    byte[] png = result.getData().getByteArrayExtra(NoteDrawActivity.EXTRA_PNG);
                    if (png != null) {
                        insertImageMedia(png, getString(R.string.note_edit_drawing_filename), "image/png", "DRAWING", getString(R.string.note_draw_title));
                    }
                }
            });

    // 录音权限
    private final ActivityResultLauncher<String> recordPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startRecordingDialog();
                } else {
                    Toast.makeText(this, getString(R.string.note_edit_mic_permission_required), Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_edit);

        session = SessionManager.getInstance(this);
        client = ApiClient.getInstance(session);
        dao = AppDatabase.get(this).noteDao();
        editorStyle = new EditorStyle(this);

        long localId = getIntent().getLongExtra(EXTRA_LOCAL_ID, -1);
        String title = getIntent().getStringExtra(EXTRA_TITLE);

        etTitle = findViewById(R.id.etTitle);
        etContent = findViewById(R.id.etContent);
        moreRow = findViewById(R.id.moreRow);
        TextView headerTitle = findViewById(R.id.tvHeaderTitle);
        headerTitle.setText(localId < 0 ? getString(R.string.note_edit_new_title) : getString(R.string.note_edit_edit_title));

        wireToolbar();
        wireEditor();

        etContent.setMediaTapListener(new RichEditText.OnMediaTapListener() {
            @Override public void onImageTapped(NoteImageSpan span) { showImageMenu(span); }
            @Override public void onAudioTapped(NoteAudioSpan span) { playAudio(span); }
        });

        // 返回：有改动自动保存
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (dirty && !saving) {
                    save(true);
                } else {
                    finish();
                }
            }
        });

        // 新建 / 编辑
        etTitle.setText(title != null ? title : "");
        if (localId >= 0) {
            loadNote(localId);
        } else {
            etTitle.requestFocus();
        }

        // 正文变更 → 标脏 + 刷新工具栏激活态
        etContent.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable s) {
                dirty = true;
                refreshToolbarState();
            }
        });
        etTitle.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable s) { dirty = true; }
        });

        findViewById(R.id.btnSave).setOnClickListener(v -> save(false));
        findViewById(R.id.btnBack).setOnClickListener(v -> {
            if (dirty && !saving) save(true); else finish();
        });
    }

    // ==================== 工具栏 ====================

    private void wireToolbar() {
        btnBold = findViewById(R.id.btnBold);
        btnItalic = findViewById(R.id.btnItalic);
        btnStrike = findViewById(R.id.btnStrike);
        btnH1 = findViewById(R.id.btnH1);
        btnH2 = findViewById(R.id.btnH2);
        btnBullet = findViewById(R.id.btnBullet);
        btnMore = findViewById(R.id.btnMore);
        btnH3 = findViewById(R.id.btnH3);
        btnOrdered = findViewById(R.id.btnOrdered);
        btnInlineCode = findViewById(R.id.btnInlineCode);
        btnLink = findViewById(R.id.btnLink);
        btnImage = findViewById(R.id.btnImage);
        btnAudio = findViewById(R.id.btnAudio);
        btnDraw = findViewById(R.id.btnDraw);

        btnBold.setOnClickListener(v -> toggleInline(new StyleSpan(Typeface.BOLD), getString(R.string.note_edit_bold_sample)));
        btnItalic.setOnClickListener(v -> toggleInline(new StyleSpan(Typeface.ITALIC), getString(R.string.note_edit_italic_sample)));
        btnStrike.setOnClickListener(v -> toggleInline(new StrikethroughSpan(), getString(R.string.note_edit_strike_sample)));
        btnInlineCode.setOnClickListener(v -> toggleInline(new NoteInlineCodeSpan(
                editorStyle.colors.inlineCodeBgColor, editorStyle.colors.inlineCodeTextColor), getString(R.string.note_edit_code_sample)));

        btnH1.setOnClickListener(v -> toggleHeading(1));
        btnH2.setOnClickListener(v -> toggleHeading(2));
        btnH3.setOnClickListener(v -> toggleHeading(3));
        btnBullet.setOnClickListener(v -> toggleBullet());
        btnOrdered.setOnClickListener(v -> toggleOrdered());
        btnLink.setOnClickListener(v -> toggleLink());

        btnMore.setOnClickListener(v -> {
            boolean show = moreRow.getVisibility() != View.VISIBLE;
            moreRow.setVisibility(show ? View.VISIBLE : View.GONE);
            setActive(btnMore, show);
        });

        btnImage.setOnClickListener(v -> imagePicker.launch("image/*"));
        btnAudio.setOnClickListener(v -> recordPermission.launch(android.Manifest.permission.RECORD_AUDIO));
        btnDraw.setOnClickListener(v -> drawLauncher.launch(new Intent(this, NoteDrawActivity.class)));
    }

    private void wireEditor() {
        // RichEditText 已自带 ListKeyListener；这里额外接入选择变化刷新工具栏
        etContent.setOnClickListener(v -> refreshToolbarState());
    }

    // ---- 行内格式 ----

    private void toggleInline(@NonNull Object span, String sample) {
        int[] r = selectionRange(sample);
        Editable sp = etContent.getText();
        boolean on = hasSpan(sp, r[0], r[1], span.getClass());
        if (on) {
            for (Object s : sp.getSpans(r[0], r[1], span.getClass())) {
                sp.removeSpan(s);
            }
        } else {
            sp.setSpan(span, r[0], r[1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        dirty = true;
        refreshToolbarState();
    }

    private void toggleLink() {
        int[] r = selectionRange(getString(R.string.note_edit_link_sample));
        EditText input = new EditText(this);
        input.setHint("https://…");
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.note_edit_insert_link))
                .setView(input)
                .setPositiveButton(getString(R.string.common_confirm), (d, w) -> {
                    String url = input.getText().toString().trim();
                    if (url.isEmpty()) {
                        return;
                    }
                    etContent.getText().setSpan(new URLSpan(url), r[0], r[1],
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    dirty = true;
                    refreshToolbarState();
                })
                .setNegativeButton(getString(R.string.common_cancel), null)
                .show();
    }

    /** 选中区间；折叠时扩展到当前词；无词可包则插入示例文字并选中 */
    private int[] selectionRange(String sample) {
        int s = etContent.getSelectionStart();
        int e = etContent.getSelectionEnd();
        if (s != e) {
            return new int[]{s, e};
        }
        CharSequence t = etContent.getText();
        int ws = s, we = s;
        while (ws > 0 && !Character.isWhitespace(t.charAt(ws - 1))) ws--;
        while (we < t.length() && !Character.isWhitespace(t.charAt(we))) we++;
        if (ws == we) {
            Editable sp = etContent.getText();
            sp.insert(s, sample);
            etContent.setSelection(s, s + sample.length());
            return new int[]{s, s + sample.length()};
        }
        etContent.setSelection(ws, we);
        return new int[]{ws, we};
    }

    // ---- 段落格式 ----

    private void toggleHeading(int level) {
        ensureNonEmpty();
        Editable sp = etContent.getText();
        int sel = etContent.getSelectionStart();
        int[] line = ParagraphHelper.lineRange(sp, sel);
        NoteHeadingSpan existing = first(sp, line[0], line[1] + 1, NoteHeadingSpan.class);
        if (existing != null) {
            sp.removeSpan(existing);
        }
        if (existing == null || existing.getLevel() != level) {
            sp.setSpan(new NoteHeadingSpan(level, editorStyle.colors.headingColor),
                    line[0], line[1] + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        dirty = true;
        refreshToolbarState();
    }

    private void toggleBullet() {
        ensureNonEmpty();
        Editable sp = etContent.getText();
        int sel = etContent.getSelectionStart();
        int[] line = ParagraphHelper.lineRange(sp, sel);
        NoteBulletSpan bullet = first(sp, line[0], line[1] + 1, NoteBulletSpan.class);
        NoteOrderedSpan ordered = first(sp, line[0], line[1] + 1, NoteOrderedSpan.class);
        if (bullet != null) {
            sp.removeSpan(bullet);
        } else {
            if (ordered != null) sp.removeSpan(ordered);
            sp.setSpan(new NoteBulletSpan(editorStyle.listMarginPx, editorStyle.bulletRadiusPx,
                    editorStyle.colors.bulletColor), line[0], line[1] + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        dirty = true;
        refreshToolbarState();
    }

    private void toggleOrdered() {
        ensureNonEmpty();
        Editable sp = etContent.getText();
        int sel = etContent.getSelectionStart();
        int[] line = ParagraphHelper.lineRange(sp, sel);
        NoteBulletSpan bullet = first(sp, line[0], line[1] + 1, NoteBulletSpan.class);
        NoteOrderedSpan ordered = first(sp, line[0], line[1] + 1, NoteOrderedSpan.class);
        if (ordered != null) {
            sp.removeSpan(ordered);
        } else {
            if (bullet != null) sp.removeSpan(bullet);
            sp.setSpan(new NoteOrderedSpan(1, editorStyle.listMarginPx, editorStyle.colors.orderedColor),
                    line[0], line[1] + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        dirty = true;
        refreshToolbarState();
    }

    private void ensureNonEmpty() {
        if (etContent.getText().length() == 0) {
            etContent.getText().append('\n');
        }
    }

    // ---- 工具栏激活态 ----

    private void refreshToolbarState() {
        int sel = etContent.getSelectionStart();
        if (sel < 0) sel = 0;
        Editable sp = etContent.getText();
        setActive(btnBold, hasStyleSpan(sp, sel, Typeface.BOLD));
        setActive(btnItalic, hasStyleSpan(sp, sel, Typeface.ITALIC));
        setActive(btnStrike, hasSpan(sp, sel, StrikethroughSpan.class));
        setActive(btnInlineCode, hasSpan(sp, sel, NoteInlineCodeSpan.class));
        setActive(btnH1, ParagraphHelper.headingLevel(sp, sel) == 1);
        setActive(btnH2, ParagraphHelper.headingLevel(sp, sel) == 2);
        setActive(btnH3, ParagraphHelper.headingLevel(sp, sel) == 3);
        setActive(btnBullet, ParagraphHelper.isBullet(sp, sel));
        setActive(btnOrdered, ParagraphHelper.isOrdered(sp, sel));
    }

    private void setActive(com.google.android.material.button.MaterialButton btn, boolean active) {
        if (btn != null) {
            int color = active ? getColor(R.color.accent) : getColor(R.color.text_primary);
            btn.setTextColor(color);
            btn.setIconTint(android.content.res.ColorStateList.valueOf(color));
        }
    }

    private static boolean hasStyleSpan(Spannable sp, int sel, int style) {
        int s = Math.max(0, sel == sp.length() ? sel - 1 : sel);
        int e = Math.min(sp.length(), s + 1);
        if (s >= e) return false;
        StyleSpan[] spans = sp.getSpans(s, e, StyleSpan.class);
        for (StyleSpan ss : spans) {
            if (ss.getStyle() == style) return true;
        }
        return false;
    }

    private static boolean hasSpan(Spannable sp, int sel, Class<?> cls) {
        int s = Math.max(0, sel == sp.length() ? sel - 1 : sel);
        int e = Math.min(sp.length(), s + 1);
        if (s >= e) return false;
        return sp.getSpans(s, e, cls).length > 0;
    }

    private static boolean hasSpan(Spannable sp, int start, int end, Class<?> cls) {
        return sp.getSpans(start, Math.min(end, sp.length()), cls).length > 0;
    }

    private static <T> T first(Spanned sp, int start, int end, Class<T> cls) {
        T[] spans = sp.getSpans(start, Math.min(end, sp.length()), cls);
        return spans.length > 0 ? spans[0] : null;
    }

    // ==================== 笔记加载 / 保存 ====================

    private void loadNote(long localId) {
        LocalNote n = dao.getById(localId);
        if (n == null) { finish(); return; }
        currentNote = n;
        if (etTitle.getText().toString().isEmpty()) {
            etTitle.setText(n.title != null ? n.title : "");
        }
        SpannableStringBuilder sb = ModelToSpanned.toSpannable(
                MarkdownParser.parse(n.content != null ? n.content : ""), editorStyle);
        etContent.setText(sb);
        dirty = false;
        loadMediaImages();
        refreshToolbarState();
        // 同步冲突标记 → 打开时提示「覆盖/重载」
        if (n.conflict) {
            showConflictDialog(false);
        }
    }

    private void save(final boolean finishAfter) {
        if (saving) return;
        saving = true;
        String title = etTitle.getText().toString().trim();
        String content = MarkdownEmitter.emit(SpanExtractor.extract(etContent.getText()));

        // 离线优先：写本地 Room（标 dirty），在线模式由同步推送 outbox
        if (currentNote == null) {
            currentNote = new LocalNote();
            currentNote.serverUrl = session.getDataPartition();
            currentNote.source = SyncService.SOURCE_LOCAL_ONLY;
            currentNote.createdAt = System.currentTimeMillis();
        }
        currentNote.title = title;
        currentNote.content = content;
        currentNote.updatedAt = System.currentTimeMillis();
        currentNote.dirty = true;
        if (currentNote.id == 0) {
            currentNote.id = dao.insert(currentNote);
        } else {
            dao.update(currentNote);
        }
        saving = false;
        dirty = false;
        Toast.makeText(this, getString(R.string.note_edit_saved), Toast.LENGTH_SHORT).show();
        if (session.isOnlineMode()) {
            SyncWorker.requestNow(this);
        }
        if (finishAfter) finish();
    }

    /** 乐观并发冲突弹窗：覆盖（丢对方改动）/ 重新加载（丢本地改动） */
    private void showConflictDialog(final boolean finishAfter) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.note_edit_conflict_title))
                .setMessage(getString(R.string.note_edit_conflict_message))
                .setPositiveButton(getString(R.string.note_edit_overwrite), (d, w) -> {
                    if (currentNote != null) {
                        currentNote.baseUpdatedAt = null;   // 不带 baseUpdatedAt 强制覆盖
                        currentNote.conflict = false;        // 覆盖即解决冲突，不再重复弹窗
                    }
                    save(finishAfter);
                })
                .setNegativeButton(getString(R.string.note_edit_reload), (d, w) -> reloadNote(finishAfter))
                .setCancelable(false)
                .show();
    }

    /** 重新加载服务端最新内容（冲突「重载」），并写回本地缓存 */
    private void reloadNote(final boolean finishAfter) {
        if (currentNote == null || currentNote.serverId == null) {
            finish();
            return;
        }
        client.getNote(currentNote.serverId).enqueue(new UiCallback<ApiResponse<NoteDetail>>(this) {
            @Override
            protected void onUiResponse(Call<ApiResponse<NoteDetail>> call,
                                        Response<ApiResponse<NoteDetail>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isOk()
                        && response.body().getData() != null) {
                    NoteDetail detail = response.body().getData();
                    currentNote.title = detail.getTitle() != null ? detail.getTitle() : "";
                    currentNote.content = detail.getContent() != null ? detail.getContent() : "";
                    currentNote.baseUpdatedAt = detail.getUpdatedAt();
                    currentNote.dirty = false;
                    currentNote.conflict = false;
                    currentNote.updatedAt = System.currentTimeMillis();
                    dao.update(currentNote);
                    etTitle.setText(currentNote.title);
                    etContent.setText(ModelToSpanned.toSpannable(
                            MarkdownParser.parse(currentNote.content), editorStyle));
                    dirty = false;
                    loadMediaImages();
                    refreshToolbarState();
                    Toast.makeText(NoteEditActivity.this, getString(R.string.note_edit_reloaded), Toast.LENGTH_SHORT).show();
                    if (finishAfter) finish();
                } else if (response.code() < 500) {
                    String msg = response.body() != null ? response.body().getMessage() : getString(R.string.common_load_failed);
                    Toast.makeText(NoteEditActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            protected void onUiFailure(Call<ApiResponse<NoteDetail>> call, Throwable t) {
                // 网络失败已由 UiCallback 统一提示
            }
        });
    }

    // ==================== 媒体：图片 / 录音 / 画画 ====================

    /** 读取图片 URI → 上传 → 插入图片 span（replaceTarget 非空则替换旧图） */
    private void readImageAndInsert(Uri uri, NoteImageSpan replace) {
        ioExecutor.execute(() -> {
            try {
                byte[] bytes = readAll(uri);
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bmp == null) {
                    mainHandler.post(() -> Toast.makeText(this, getString(R.string.note_edit_image_decode_failed), Toast.LENGTH_SHORT).show());
                    return;
                }
                // JPEG 压缩（Q88）控制体积，避免 PNG 无损编码撑大照片超 20MB 上限
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.JPEG, 88, out);
                byte[] jpeg = out.toByteArray();
                if (replace != null) {
                    mainHandler.post(() -> insertImageMedia(jpeg, "image.jpg", "image/jpeg", "IMAGE", getString(R.string.note_edit_image_alt), replace));
                } else {
                    mainHandler.post(() -> insertImageMedia(jpeg, "image.jpg", "image/jpeg", "IMAGE", getString(R.string.note_edit_image_alt), null));
                }
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(this, getString(R.string.note_edit_image_read_failed), Toast.LENGTH_SHORT).show());
            }
        });
    }

    /** 上传图片字节并在光标处插入 span */
    private void insertImageMedia(byte[] bytes, String fileName, String mime, String mediaType, String alt) {
        insertImageMedia(bytes, fileName, mime, mediaType, alt, null);
    }

    /**
     * 插入图片 span（离线优先）：图片存本地 note_media 目录，正文用 {@code local://} 引用，
     * 同步时由 SyncService 上传并改写为服务端 URL。
     */
    private void insertImageMedia(byte[] bytes, String fileName, String mime, String mediaType,
                                  String alt, NoteImageSpan replace) {
        try {
            String unique = System.currentTimeMillis() + "_" + fileName;
            File f = new File(MediaFiles.localMediaDir(this), unique);
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(bytes);
            }
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            String url = MediaFiles.localUrl(unique);
            if (replace != null) {
                replaceImageSpan(replace, null, url, alt, bmp);
            } else {
                insertImageSpan(null, url, alt, bmp);
            }
            dirty = true;
        } catch (IOException e) {
            Toast.makeText(this, getString(R.string.note_edit_image_save_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private void insertImageSpan(String mediaId, String url, String alt, Bitmap bmp) {
        Editable sp = etContent.getText();
        int sel = etContent.getSelectionStart();
        if (sel < 0) sel = sp.length();
        sp.insert(sel, String.valueOf(ModelToSpanned.PLACEHOLDER));
        NoteImageSpan span = new NoteImageSpan(mediaId, url, alt,
                editorStyle.newImagePlaceholder(), editorStyle.maxImageWidthPx);
        if (bmp != null) span.setBitmap(bmp, this);
        sp.setSpan(span, sel, sel + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        dirty = true;
        refreshToolbarState();
    }

    private void replaceImageSpan(NoteImageSpan old, String mediaId, String url, String alt, Bitmap bmp) {
        Editable sp = etContent.getText();
        int start = sp.getSpanStart(old);
        int end = sp.getSpanEnd(old);
        if (start < 0 || end < 0) return;
        sp.removeSpan(old);
        sp.delete(start, end);
        // 在原位置插入新占位
        sp.insert(start, String.valueOf(ModelToSpanned.PLACEHOLDER));
        NoteImageSpan span = new NoteImageSpan(mediaId, url, alt,
                editorStyle.newImagePlaceholder(), editorStyle.maxImageWidthPx);
        if (bmp != null) span.setBitmap(bmp, this);
        sp.setSpan(span, start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        dirty = true;
    }

    // ---- 录音 ----

    private void startRecordingDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.note_edit_recording_title))
                .setNegativeButton(getString(R.string.common_cancel), null)
                .create();
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(40, 24, 40, 8);
        TextView status = new TextView(this);
        status.setText(getString(R.string.note_edit_recording_hint));
        status.setTextSize(15f);
        status.setTextColor(getColor(R.color.text_secondary));
        com.google.android.material.button.MaterialButton btn = new com.google.android.material.button.MaterialButton(this);
        btn.setText(getString(R.string.note_edit_record_start));
        btn.setPadding(0, 12, 0, 12);
        content.addView(status);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 24;
        content.addView(btn, lp);
        dialog.setView(content);

        final MediaRecorder[] recorder = new MediaRecorder[1];
        final File[] file = new File[1];
        final boolean[] recording = {false};

        btn.setOnClickListener(v -> {
            if (!recording[0]) {
                try {
                    file[0] = new File(getCacheDir(), "record_" + System.currentTimeMillis() + ".m4a");
                    MediaRecorder r = Build.VERSION.SDK_INT >= 31
                            ? new MediaRecorder(this)
                            : new MediaRecorder();
                    r.setAudioSource(MediaRecorder.AudioSource.MIC);
                    r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                    r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                    r.setAudioSamplingRate(44100);
                    r.setOutputFile(file[0].getAbsolutePath());
                    r.prepare();
                    r.start();
                    recorder[0] = r;
                    recording[0] = true;
                    status.setText(getString(R.string.note_edit_recording_status));
                    btn.setText(getString(R.string.note_edit_record_stop));
                } catch (Exception e) {
                    Toast.makeText(this, getString(R.string.note_edit_recording_start_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
                }
            } else {
                try {
                    recorder[0].stop();
                } catch (Exception ignored) {
                }
                try {
                    recorder[0].release();
                } catch (Exception ignored) {
                }
                recorder[0] = null;
                recording[0] = false;
                status.setText(getString(R.string.note_edit_processing));
                btn.setEnabled(false);
                dialog.dismiss();
                uploadAudio(file[0]);
            }
        });
        dialog.setOnDismissListener(d -> releaseRecorderQuietly(recorder[0], recording[0]));
        dialog.show();
    }

    private void releaseRecorderQuietly(MediaRecorder r, boolean recording) {
        if (r != null) {
            try { if (recording) r.stop(); } catch (Exception ignored) { }
            try { r.release(); } catch (Exception ignored) { }
        }
    }

    private void uploadAudio(File file) {
        ioExecutor.execute(() -> {
            try {
                byte[] bytes = readFile(file);
                if (bytes.length == 0) {
                    mainHandler.post(() -> Toast.makeText(this, getString(R.string.note_edit_recording_empty), Toast.LENGTH_SHORT).show());
                    return;
                }
                mainHandler.post(() -> uploadAudioBytes(bytes));
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(this, getString(R.string.note_edit_recording_read_failed), Toast.LENGTH_SHORT).show());
            }
        });
    }

    /** 插入录音 span（离线优先）：存本地文件，正文用 local://?mediaType=audio 引用，同步时上传 */
    private void uploadAudioBytes(byte[] bytes) {
        try {
            String fileName = System.currentTimeMillis() + "_recording.m4a";
            File f = new File(MediaFiles.localMediaDir(this), fileName);
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(bytes);
            }
            insertAudioSpan(null, MediaFiles.localUrl(fileName) + "?mediaType=audio");
        } catch (IOException e) {
            Toast.makeText(this, getString(R.string.note_edit_recording_upload_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private void insertAudioSpan(String mediaId, String url) {
        Editable sp = etContent.getText();
        int sel = etContent.getSelectionStart();
        if (sel < 0) sel = sp.length();
        sp.insert(sel, String.valueOf(ModelToSpanned.PLACEHOLDER));
        NoteAudioSpan span = new NoteAudioSpan(mediaId, url, getString(R.string.note_edit_audio_alt),
                editorStyle.colors.audioChipColor, editorStyle.colors.audioTextColor,
                editorStyle.audioPaddingPx, editorStyle.audioChipHeightPx);
        sp.setSpan(span, sel, sel + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        dirty = true;
    }

    // ---- 媒体点击 ----

    private void showImageMenu(NoteImageSpan span) {
        String[] options = {getString(R.string.note_edit_image_view), getString(R.string.note_edit_image_replace), getString(R.string.common_delete)};
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.note_edit_image_title))
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        viewImage(span);
                    } else if (which == 1) {
                        replaceTarget = span;
                        imagePicker.launch("image/*");
                    } else {
                        deleteSpan(span);
                    }
                })
                .show();
    }

    private void viewImage(NoteImageSpan span) {
        Bitmap bmp = bitmapOf(span);
        if (bmp == null) {
            Toast.makeText(this, getString(R.string.note_edit_image_not_loaded), Toast.LENGTH_SHORT).show();
            return;
        }
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(bmp);
        new AlertDialog.Builder(this)
                .setView(iv)
                .setNegativeButton(getString(R.string.common_close), null)
                .show();
    }

    private Bitmap bitmapOf(NoteImageSpan span) {
        if (span != null && span.getMediaUrl() != null) {
            return bitmapCache.get(span.getMediaUrl());
        }
        return null;
    }

    private void deleteSpan(NoteImageSpan span) {
        Editable sp = etContent.getText();
        int start = sp.getSpanStart(span);
        int end = sp.getSpanEnd(span);
        if (start >= 0 && end > start) {
            sp.removeSpan(span);
            sp.delete(start, end);
            dirty = true;
        }
    }

    private void playAudio(NoteAudioSpan span) {
        // 本地媒体文件优先（离线新建 / 同步缓存的音频）
        File local = MediaFiles.resolveLocal(this, span.getMediaUrl());
        if (local != null && local.exists()) {
            startPlaying(local);
            return;
        }
        String mediaId = span.getMediaId();
        if (mediaId == null || mediaId.isEmpty()) return;
        if (audioPlayer != null) {
            try {
                audioPlayer.stop();
                audioPlayer.release();
            } catch (Exception ignored) {
            }
            audioPlayer = null;
        }
        Toast.makeText(this, getString(R.string.note_edit_playing), Toast.LENGTH_SHORT).show();
        ioExecutor.execute(() -> {
            try {
                Response<ResponseBody> resp = client.getNoteMedia(mediaId).execute();
                if (resp.isSuccessful() && resp.body() != null) {
                    byte[] bytes = resp.body().bytes();
                    File f = new File(getCacheDir(), "audio_" + System.currentTimeMillis() + ".m4a");
                    try (FileOutputStream out = new FileOutputStream(f)) {
                        out.write(bytes);
                    }
                    mainHandler.post(() -> startPlaying(f));
                } else {
                    mainHandler.post(() -> Toast.makeText(this, getString(R.string.note_edit_audio_load_failed), Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(this, getString(R.string.note_edit_audio_load_failed), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void startPlaying(File f) {
        try {
            audioPlayer = new MediaPlayer();
            audioPlayer.setDataSource(f.getAbsolutePath());
            audioPlayer.setOnCompletionListener(mp -> {
                try { mp.release(); } catch (Exception ignored) { }
                if (audioPlayer == mp) audioPlayer = null;
            });
            audioPlayer.setOnErrorListener((mp, what, extra) -> {
                try { mp.release(); } catch (Exception ignored) { }
                if (audioPlayer == mp) audioPlayer = null;
                return true;
            });
            audioPlayer.prepare();
            audioPlayer.start();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.note_edit_play_failed), Toast.LENGTH_SHORT).show();
        }
    }

    // ---- 回读媒体图片 ----

    private void loadMediaImages() {
        Editable sp = etContent.getText();
        NoteImageSpan[] imgs = sp.getSpans(0, sp.length(), NoteImageSpan.class);
        for (NoteImageSpan img : imgs) {
            String url = img.getMediaUrl();
            if (url == null) continue;
            if (bitmapCache.containsKey(url)) {
                img.setBitmap(bitmapCache.get(url), this);
                continue;
            }
            // 本地文件优先（离线新建 / 同步缓存的服务端媒体）
            File local = MediaFiles.resolveLocal(this, url);
            if (local != null && local.exists()) {
                Bitmap bmp = BitmapFactory.decodeFile(local.getAbsolutePath());
                if (bmp != null) {
                    bitmapCache.put(url, bmp);
                    img.setBitmap(bmp, this);
                }
                continue;
            }
            // 服务端媒体（在线）拉取
            String mid = img.getMediaId();
            if (mid == null || mid.isEmpty()) continue;
            ioExecutor.execute(() -> {
                try {
                    Response<ResponseBody> resp = client.getNoteMedia(mid).execute();
                    if (resp.isSuccessful() && resp.body() != null) {
                        byte[] bytes = resp.body().bytes();
                        Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        if (bmp == null) return;
                        bitmapCache.put(url, bmp);
                        mainHandler.post(() -> {
                            NoteImageSpan[] now = etContent.getText().getSpans(0, etContent.length(),
                                    NoteImageSpan.class);
                            for (NoteImageSpan cur : now) {
                                if (url.equals(cur.getMediaUrl())) {
                                    cur.setBitmap(bmp, NoteEditActivity.this);
                                }
                            }
                            etContent.requestLayout();
                        });
                    }
                } catch (Exception ignored) {
                }
            });
        }
    }

    // ---- 工具 ----

    private byte[] readAll(Uri uri) throws IOException {
        java.io.InputStream in = getContentResolver().openInputStream(uri);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        in.close();
        return out.toByteArray();
    }

    private byte[] readFile(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (audioPlayer != null) {
            try {
                audioPlayer.release();
            } catch (Exception ignored) {
            }
            audioPlayer = null;
        }
        ioExecutor.shutdown();
    }
}
