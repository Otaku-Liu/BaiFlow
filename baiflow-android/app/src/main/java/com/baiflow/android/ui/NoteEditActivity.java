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
import com.baiflow.android.model.ApiResponse;
import com.baiflow.android.model.NoteDetail;
import com.baiflow.android.model.NoteMedia;
import com.baiflow.android.network.ApiClient;

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
import retrofit2.Callback;
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

    public static final String EXTRA_NOTE_ID = "note_id";
    public static final String EXTRA_TITLE = "note_title";

    private static final String TAG = "NoteEdit";

    private SessionManager session;
    private ApiClient client;
    private EditorStyle editorStyle;

    private EditText etTitle;
    private RichEditText etContent;

    private NoteImageSpan replaceTarget;     // 替换旧图的目标 span

    private String noteId;                   // null = 新建
    private String noteUpdatedAt;            // 本次编辑基于的 updatedAt（乐观并发）
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
        editorStyle = new EditorStyle(this);

        noteId = getIntent().getStringExtra(EXTRA_NOTE_ID);
        String title = getIntent().getStringExtra(EXTRA_TITLE);

        etTitle = findViewById(R.id.etTitle);
        etContent = findViewById(R.id.etContent);
        moreRow = findViewById(R.id.moreRow);
        TextView headerTitle = findViewById(R.id.tvHeaderTitle);
        headerTitle.setText(noteId == null ? getString(R.string.note_edit_new_title) : getString(R.string.note_edit_edit_title));

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
        if (noteId != null) {
            loadNote(noteId);
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

    private void loadNote(String id) {
        client.getNote(id).enqueue(new Callback<ApiResponse<NoteDetail>>() {
            @Override
            public void onResponse(Call<ApiResponse<NoteDetail>> call,
                                   Response<ApiResponse<NoteDetail>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isOk()) {
                    NoteDetail detail = response.body().getData();
                    if (detail == null) return;
                    if (etTitle.getText().toString().isEmpty()) {
                        etTitle.setText(detail.getTitle() != null ? detail.getTitle() : "");
                    }
                    SpannableStringBuilder sb = ModelToSpanned.toSpannable(
                            MarkdownParser.parse(detail.getContent()), editorStyle);
                    etContent.setText(sb);
                    dirty = false;
                    noteUpdatedAt = detail.getUpdatedAt();
                    loadMediaImages();
                    refreshToolbarState();
                } else {
                    String msg = response.body() != null ? response.body().getMessage() : getString(R.string.common_load_failed);
                    Toast.makeText(NoteEditActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<NoteDetail>> call, Throwable t) {
                Toast.makeText(NoteEditActivity.this, getString(R.string.common_network_error, t.getMessage()), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void save(final boolean finishAfter) {
        if (saving) return;
        saving = true;
        String title = etTitle.getText().toString().trim();
        String content = MarkdownEmitter.emit(SpanExtractor.extract(etContent.getText()));

        Callback<ApiResponse<NoteDetail>> cb = new Callback<ApiResponse<NoteDetail>>() {
            @Override
            public void onResponse(Call<ApiResponse<NoteDetail>> call,
                                   Response<ApiResponse<NoteDetail>> response) {
                saving = false;
                if (response.isSuccessful() && response.body() != null && response.body().isOk()
                        && response.body().getData() != null) {
                    NoteDetail detail = response.body().getData();
                    noteId = detail.getId();
                    noteUpdatedAt = detail.getUpdatedAt();
                    dirty = false;
                    Toast.makeText(NoteEditActivity.this, getString(R.string.note_edit_saved), Toast.LENGTH_SHORT).show();
                    if (finishAfter) finish();
                } else if (response.body() != null && "NOTE_CONFLICT".equals(response.body().getCode())) {
                    // 乐观并发冲突：让用户选择覆盖或重新加载
                    showConflictDialog(finishAfter);
                } else {
                    String msg = response.body() != null ? response.body().getMessage() : getString(R.string.common_save_failed);
                    Toast.makeText(NoteEditActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<NoteDetail>> call, Throwable t) {
                saving = false;
                Toast.makeText(NoteEditActivity.this, getString(R.string.common_save_failed_detail, t.getMessage()), Toast.LENGTH_SHORT).show();
            }
        };

        if (noteId == null) {
            client.createNote(title, content).enqueue(cb);
        } else {
            client.updateNote(noteId, title, content, noteUpdatedAt).enqueue(cb);
        }
    }

    /** 乐观并发冲突弹窗：覆盖（丢对方改动）/ 重新加载（丢本地改动） */
    private void showConflictDialog(final boolean finishAfter) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.note_edit_conflict_title))
                .setMessage(getString(R.string.note_edit_conflict_message))
                .setPositiveButton(getString(R.string.note_edit_overwrite), (d, w) -> {
                    noteUpdatedAt = null;   // 不带 baseUpdatedAt 强制覆盖
                    save(finishAfter);
                })
                .setNegativeButton(getString(R.string.note_edit_reload), (d, w) -> reloadNote(finishAfter))
                .setCancelable(false)
                .show();
    }

    /** 重新加载服务端最新内容，放弃本地未保存改动 */
    private void reloadNote(final boolean finishAfter) {
        if (noteId == null) {
            finish();
            return;
        }
        client.getNote(noteId).enqueue(new Callback<ApiResponse<NoteDetail>>() {
            @Override
            public void onResponse(Call<ApiResponse<NoteDetail>> call,
                                   Response<ApiResponse<NoteDetail>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isOk()
                        && response.body().getData() != null) {
                    NoteDetail detail = response.body().getData();
                    noteUpdatedAt = detail.getUpdatedAt();
                    etTitle.setText(detail.getTitle() != null ? detail.getTitle() : "");
                    etContent.setText(ModelToSpanned.toSpannable(
                            MarkdownParser.parse(detail.getContent()), editorStyle));
                    dirty = false;
                    loadMediaImages();
                    refreshToolbarState();
                    Toast.makeText(NoteEditActivity.this, getString(R.string.note_edit_reloaded), Toast.LENGTH_SHORT).show();
                    if (finishAfter) finish();
                } else {
                    String msg = response.body() != null ? response.body().getMessage() : getString(R.string.common_load_failed);
                    Toast.makeText(NoteEditActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<NoteDetail>> call, Throwable t) {
                Toast.makeText(NoteEditActivity.this, getString(R.string.common_load_failed_detail, t.getMessage()), Toast.LENGTH_SHORT).show();
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

    /** 上传图片字节并插入 span（replace 非空则替换该 span 位置） */
    private void insertImageMedia(byte[] bytes, String fileName, String mime, String mediaType,
                                  String alt, NoteImageSpan replace) {
        client.uploadNoteMedia(mediaType, bytes, fileName, mime)
                .enqueue(new Callback<ApiResponse<NoteMedia>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<NoteMedia>> call,
                                           Response<ApiResponse<NoteMedia>> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().isOk()
                                && response.body().getData() != null) {
                            NoteMedia media = response.body().getData();
                            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                            if (replace != null) {
                                replaceImageSpan(replace, media.getId(), media.getUrl(), alt, bmp);
                            } else {
                                insertImageSpan(media.getId(), media.getUrl(), alt, bmp);
                            }
                        } else {
                            Toast.makeText(NoteEditActivity.this, getString(R.string.note_edit_image_upload_failed), Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<NoteMedia>> call, Throwable t) {
                        Toast.makeText(NoteEditActivity.this, getString(R.string.note_edit_image_upload_failed), Toast.LENGTH_SHORT).show();
                    }
                });
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

    private void uploadAudioBytes(byte[] bytes) {
        client.uploadNoteMedia("AUDIO", bytes, getString(R.string.note_edit_audio_filename), "audio/mp4")
                .enqueue(new Callback<ApiResponse<NoteMedia>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<NoteMedia>> call,
                                           Response<ApiResponse<NoteMedia>> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().isOk()
                                && response.body().getData() != null) {
                            NoteMedia media = response.body().getData();
                            insertAudioSpan(media.getId(), media.getUrl());
                        } else {
                            Toast.makeText(NoteEditActivity.this, getString(R.string.note_edit_recording_upload_failed), Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<NoteMedia>> call, Throwable t) {
                        Toast.makeText(NoteEditActivity.this, getString(R.string.note_edit_recording_upload_failed), Toast.LENGTH_SHORT).show();
                    }
                });
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
        if (span != null && span.getMediaId() != null) {
            Bitmap cached = bitmapCache.get(span.getMediaId());
            if (cached != null) return cached;
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
            String mid = img.getMediaId();
            if (mid == null || mid.isEmpty()) continue;
            if (bitmapCache.containsKey(mid)) {
                img.setBitmap(bitmapCache.get(mid), this);
                continue;
            }
            ioExecutor.execute(() -> {
                try {
                    Response<ResponseBody> resp = client.getNoteMedia(mid).execute();
                    if (resp.isSuccessful() && resp.body() != null) {
                        byte[] bytes = resp.body().bytes();
                        Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        if (bmp == null) return;
                        bitmapCache.put(mid, bmp);
                        mainHandler.post(() -> {
                            NoteImageSpan[] now = etContent.getText().getSpans(0, etContent.length(),
                                    NoteImageSpan.class);
                            for (NoteImageSpan cur : now) {
                                if (mid.equals(cur.getMediaId())) {
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
