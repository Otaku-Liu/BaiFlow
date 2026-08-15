package com.baiflow.android.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Spanned;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.baiflow.android.R;
import com.baiflow.android.auth.SessionManager;
import com.baiflow.android.data.AppDatabase;
import com.baiflow.android.data.LocalNote;
import com.baiflow.android.data.LocalNoteDao;
import com.baiflow.android.data.MediaFiles;
import com.baiflow.android.data.ProgressReporter;
import com.baiflow.android.data.SyncService;
import com.baiflow.android.editor.BlockRichText;
import com.baiflow.android.editor.EditorStyle;
import com.baiflow.android.editor.MarkdownEmitter;
import com.baiflow.android.editor.MarkdownParser;
import com.baiflow.android.editor.NoteBlocks;
import com.baiflow.android.network.ApiClient;
import com.baiflow.android.sync.SyncWorker;
import com.baiflow.android.ui.adapter.NoteBlockAdapter;
import com.baiflow.android.util.KeyboardUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 随手记块编辑器 — 每块一个真实 View（文本 EditText / 图片 / 音频播放组件）。
 * 加载：Markdown → {@link MarkdownParser} → {@link NoteBlocks#fromDoc} → RecyclerView；
 * 保存：块 → {@link NoteBlocks#toDoc} → {@link MarkdownEmitter} → Room（离线优先，同步不变）。
 */
public class NoteEditActivity extends AppCompatActivity {

    public static final String EXTRA_LOCAL_ID = "local_id";
    public static final String EXTRA_TITLE = "note_title";

    private static final String TAG = "NoteEdit";

    private SessionManager session;
    private ApiClient client;
    private LocalNoteDao dao;

    private EditText etTitle;
    private RecyclerView recyclerBlocks;
    private FrameLayout blockFrame;
    private View floatBar;
    private NoteBlockAdapter blockAdapter;
    private EditorStyle editorStyle;
    // 最近一次聚焦的文本块（浮动条 B/I/U/S 与顶部块类型栏的操作对象；失去焦点后仍保留，
    // 便于点击工具栏按钮时继续对原块操作，下一次聚焦时更新）
    private EditText activeTextBlock;
    private int activeBlockPosition = -1;
    private Integer pendingInsertIdx;   // 「在上方插入」媒体时的目标位置（null = 追加末尾）

    private final List<NoteBlocks.Block> blocks = new ArrayList<>();
    private LocalNote currentNote;
    private boolean dirty = false;
    private boolean saving = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService ioExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private Runnable progressScrollTimer;   // 阅读进度滚动防抖上报

    // 媒体
    private final MediaRecorder[] activeRecorder = {null};
    private final File[] recordingFile = {null};
    private final boolean[] recording = {false};
    private final long[] recordingStartMs = {0};   // 录音起始墙钟（算真实时长，避免 MediaPlayer 误读）

    // 图片选择（普通插入）
    private final ActivityResultLauncher<String> imagePicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                ioExecutor.execute(() -> {
                    try {
                        byte[] bytes = readAll(uri);
                        addImageBlock(bytes, "image.jpg", "image/jpeg");
                    } catch (Exception e) {
                        mainHandler.post(() -> Toast.makeText(this,
                                getString(R.string.note_edit_image_decode_failed), Toast.LENGTH_SHORT).show());
                    }
                });
            });

    // 画画结果
    private final ActivityResultLauncher<Intent> drawLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    byte[] png = result.getData().getByteArrayExtra(NoteDrawActivity.EXTRA_PNG);
                    if (png != null) {
                        addImageBlock(png, getString(R.string.note_edit_drawing_filename), "image/png");
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

        long localId = getIntent().getLongExtra(EXTRA_LOCAL_ID, -1);
        String title = getIntent().getStringExtra(EXTRA_TITLE);

        etTitle = findViewById(R.id.etTitle);
        recyclerBlocks = findViewById(R.id.recyclerBlocks);
        editorStyle = new EditorStyle(this);
        blockFrame = findViewById(R.id.blockFrame);
        floatBar = findViewById(R.id.floatFormatBar);
        TextView headerTitle = findViewById(R.id.tvHeaderTitle);
        headerTitle.setText(localId < 0 ? getString(R.string.note_edit_new_title) : getString(R.string.note_edit_edit_title));
        // 键盘开合/布局变化时浮动条跟随焦点块重新定位
        blockFrame.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (floatBar.getVisibility() == View.VISIBLE) {
                positionFloatingBar();
            }
        });

        wireToolbar();
        setupBlockList();

        // 返回：有改动自动保存
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (dirty && !saving) {
                    save(true);
                } else {
                    saveNoteProgress();
                    finish();
                }
            }
        });

        etTitle.setText(title != null ? title : "");
        if (localId >= 0) {
            loadNote(localId);
        } else {
            etTitle.requestFocus();
            addBlock(NoteBlocks.TEXT);
        }

        etTitle.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable s) { dirty = true; }
        });

        findViewById(R.id.btnSave).setOnClickListener(v -> save(false));
        findViewById(R.id.btnBack).setOnClickListener(v -> {
            if (dirty && !saving) save(true); else {
                saveNoteProgress();
                finish();
            }
        });
    }

    /** 点击空白收起键盘；工具栏/浮动条上的点击不触发（避免格式化时焦点被清、浮动条消失） */
    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (!isToolbarTouch(ev)) {
            KeyboardUtil.hideOnTouchOutside(this, ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    /** 按下点是否落在工具栏（媒体/块类型）或浮动格式条上 */
    private boolean isToolbarTouch(android.view.MotionEvent ev) {
        if (ev.getAction() != android.view.MotionEvent.ACTION_DOWN) {
            return false;
        }
        return isPointInView(ev, floatBar)
                || isPointInView(ev, findViewById(R.id.toolbarRow));
    }

    private boolean isPointInView(android.view.MotionEvent ev, View v) {
        if (v == null || v.getVisibility() != View.VISIBLE) {
            return false;
        }
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        return ev.getRawX() >= loc[0] && ev.getRawX() <= loc[0] + v.getWidth()
                && ev.getRawY() >= loc[1] && ev.getRawY() <= loc[1] + v.getHeight();
    }

    // ==================== 块列表 ====================

    private void setupBlockList() {
        recyclerBlocks.setLayoutManager(new LinearLayoutManager(this));
        final ItemTouchHelper[] touchHelper = new ItemTouchHelper[1];
        blockAdapter = new NoteBlockAdapter(blocks, new NoteBlockAdapter.Listener() {
            @Override public void onChanged() { dirty = true; }
            @Override public void onDelete(int position) {
                blocks.remove(position);
                blockAdapter.notifyItemRemoved(position);
                // 删光后补一个空文本块，保证仍可输入
                if (blocks.isEmpty()) {
                    NoteBlocks.Block b = new NoteBlocks.Block();
                    b.type = NoteBlocks.TEXT;
                    blocks.add(b);
                    blockAdapter.notifyItemInserted(0);
                }
                dirty = true;
            }
            @Override public void onSwitchType(int position, int type, int level) {
                NoteBlocks.Block b = blocks.get(position);
                NoteBlocks.Block nb = new NoteBlocks.Block();
                nb.type = type;
                nb.level = level;
                nb.text = b.text;
                blocks.set(position, nb);
                blockAdapter.notifyItemChanged(position);
                dirty = true;
            }
            @Override public void onStartDrag(RecyclerView.ViewHolder holder) {
                touchHelper[0].startDrag(holder);
            }
            @Override public void onTextBlockFocused(int position, EditText et) {
                activeTextBlock = et;
                activeBlockPosition = position;
                showFloatingBar();
            }
            @Override public void onTextBlockFocusLost() {
                // 只隐藏浮动条，保留 activeTextBlock 供工具栏按钮对原块继续操作
                floatBar.setVisibility(View.GONE);
            }
            @Override public void onInsertAbove(int position, View anchor) {
                showInsertAboveMenu(position, anchor);
            }
            @Override public void onTextSelectionChanged(boolean selecting) {
                // 文本选中菜单弹出时隐藏浮动格式条，收起后恢复
                if (selecting) {
                    floatBar.setVisibility(View.GONE);
                } else if (activeTextBlock != null) {
                    showFloatingBar();
                }
            }
        }, client, editorStyle);
        recyclerBlocks.setAdapter(blockAdapter);
        // 块间间距（与 Web 的 margin-bottom 8px 一致；顶部「＋」插在间距处）
        recyclerBlocks.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(android.graphics.Rect outRect, View view,
                                       RecyclerView parent, RecyclerView.State state) {
                outRect.bottom = Math.round(8 * getResources().getDisplayMetrics().density);
            }
        });
        // 拖动排序：长按块左侧「⋮⋮⋮」触发
        ItemTouchHelper it = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override public boolean isLongPressDragEnabled() { return false; }
            @Override public boolean isItemViewSwipeEnabled() { return false; }
            @Override public int getMovementFlags(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
            }
            @Override public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                                           @NonNull RecyclerView.ViewHolder target) {
                blockAdapter.moveItem(vh.getBindingAdapterPosition(), target.getBindingAdapterPosition());
                return true;
            }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) { }
        });
        it.attachToRecyclerView(recyclerBlocks);
        touchHelper[0] = it;
        // 阅读进度：滚动防抖上报（保存与正文 dirty 完全独立）
        recyclerBlocks.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (progressScrollTimer != null) mainHandler.removeCallbacks(progressScrollTimer);
                progressScrollTimer = NoteEditActivity.this::saveNoteProgress;
                mainHandler.postDelayed(progressScrollTimer, 800);
                positionFloatingBar();
            }
        });
    }

    private void addBlock(int type) {
        NoteBlocks.Block b = new NoteBlocks.Block();
        b.type = type;
        blocks.add(b);
        blockAdapter.notifyItemInserted(blocks.size() - 1);
        dirty = true;
    }

    // ==================== 工具栏 ====================

    private void wireToolbar() {
        findViewById(R.id.btnImage).setOnClickListener(v -> imagePicker.launch("image/*"));
        findViewById(R.id.btnAudio).setOnClickListener(v -> recordPermission.launch(android.Manifest.permission.RECORD_AUDIO));
        findViewById(R.id.btnDraw).setOnClickListener(v -> drawLauncher.launch(new Intent(this, NoteDrawActivity.class)));
        // 顶部块类型栏（固定显示）：切换当前焦点块类型；无焦点块时在末尾插入新块
        findViewById(R.id.btnBlockText).setOnClickListener(v -> setBlockType(NoteBlocks.TEXT, 1));
        findViewById(R.id.btnBlockHeading).setOnClickListener(v -> setBlockType(NoteBlocks.HEADING, 1));
        // 浮动格式条（焦点块上方偏左）：B/I/U/S 就地切换选中文字的格式（所见即所得）
        findViewById(R.id.btnFloatBold).setOnClickListener(v -> toggleStyle(Typeface.BOLD));
        findViewById(R.id.btnFloatItalic).setOnClickListener(v -> toggleStyle(Typeface.ITALIC));
        findViewById(R.id.btnFloatUnderline).setOnClickListener(v -> toggleSpan(UnderlineSpan.class, new UnderlineSpan()));
        findViewById(R.id.btnFloatStrike).setOnClickListener(v -> toggleSpan(StrikethroughSpan.class, new StrikethroughSpan()));
    }

    /** 顶部块类型栏：有焦点文本块则切换其类型（保留内容），否则在末尾插入新块 */
    private void setBlockType(int type, int level) {
        if (activeTextBlock != null && activeBlockPosition >= 0 && activeBlockPosition < blocks.size()) {
            NoteBlocks.Block b = blocks.get(activeBlockPosition);
            NoteBlocks.Block nb = new NoteBlocks.Block();
            nb.type = type;
            nb.level = level;
            nb.text = b.text;
            blocks.set(activeBlockPosition, nb);
            blockAdapter.notifyItemChanged(activeBlockPosition);
        } else {
            NoteBlocks.Block b = new NoteBlocks.Block();
            b.type = type;
            b.level = level;
            blocks.add(b);
            blockAdapter.notifyItemInserted(blocks.size() - 1);
            scrollToBlock(blocks.size() - 1);   // 新增块后界面滚动过去
        }
        dirty = true;
    }

    /** 滚动到指定块（新增块后让界面跟过去） */
    private void scrollToBlock(int position) {
        recyclerBlocks.post(() -> recyclerBlocks.smoothScrollToPosition(position));
    }

    /** 块顶部「＋」：弹出插入菜单（文本/标题/图片/音频），插到该块上方 */
    private void showInsertAboveMenu(int position, View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor, android.view.Gravity.NO_GRAVITY, 0, R.style.Ios_PopupMenu);
        menu.getMenu().add(0, 1, 0, getString(R.string.note_block_text));
        menu.getMenu().add(0, 2, 0, getString(R.string.note_block_heading));
        menu.getMenu().add(0, 3, 0, getString(R.string.note_edit_image_title));
        menu.getMenu().add(0, 4, 0, getString(R.string.note_edit_recording_title));
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: insertBlockAt(NoteBlocks.TEXT, 1, position); break;
                case 2: insertBlockAt(NoteBlocks.HEADING, 1, position); break;
                case 3: pendingInsertIdx = position; imagePicker.launch("image/*"); break;
                case 4: pendingInsertIdx = position;
                        recordPermission.launch(android.Manifest.permission.RECORD_AUDIO); break;
                default: break;
            }
            return true;
        });
        menu.show();
    }

    /** 在指定位置插入文本类块并聚焦（「在上方插入」） */
    private void insertBlockAt(int type, int level, int position) {
        NoteBlocks.Block b = new NoteBlocks.Block();
        b.type = type;
        b.level = level;
        blocks.add(position, b);
        blockAdapter.notifyItemInserted(position);
        dirty = true;
        scrollToBlock(position);
        focusTextBlockAt(position);
    }

    /** 媒体块落位：pendingInsertIdx 非空 → 插到该位置；否则追加末尾 */
    private void insertOrAppendMedia(NoteBlocks.Block media) {
        if (pendingInsertIdx != null) {
            int idx = pendingInsertIdx;
            pendingInsertIdx = null;
            blocks.add(idx, media);
            blockAdapter.notifyItemInserted(idx);
            scrollToBlock(idx);
        } else {
            appendMediaBlock(media);
        }
        dirty = true;
    }

    /** 聚焦指定位置的文本块（「在上方插入」后可直接输入） */
    private void focusTextBlockAt(int position) {
        recyclerBlocks.post(() -> {
            RecyclerView.ViewHolder vh = recyclerBlocks.findViewHolderForAdapterPosition(position);
            if (vh != null) {
                EditText et = vh.itemView.findViewWithTag("block_text");
                if (et != null) {
                    et.requestFocus();
                }
            }
        });
    }

    /** StyleSpan（加粗/斜体）toggle：整段被同款式覆盖则移除，否则加样式 */
    private void toggleStyle(int style) {
        EditText et = activeTextBlock;
        if (et == null || !requireSelection(et)) {
            return;
        }
        Editable ed = et.getText();
        int start = Math.min(et.getSelectionStart(), et.getSelectionEnd());
        int end = Math.max(et.getSelectionStart(), et.getSelectionEnd());
        boolean covered = false;
        for (StyleSpan s : ed.getSpans(start, end, StyleSpan.class)) {
            if (s.getStyle() == style && ed.getSpanStart(s) <= start && ed.getSpanEnd(s) >= end) {
                covered = true;
                break;
            }
        }
        if (covered) {
            for (StyleSpan s : ed.getSpans(start, end, StyleSpan.class)) {
                if (s.getStyle() == style) {
                    ed.removeSpan(s);
                }
            }
        } else {
            ed.setSpan(new StyleSpan(style), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        syncBlockText();
    }

    /** 普通 span（下划线/删除线）toggle */
    private void toggleSpan(Class<?> cls, Object span) {
        EditText et = activeTextBlock;
        if (et == null || !requireSelection(et)) {
            return;
        }
        Editable ed = et.getText();
        int start = Math.min(et.getSelectionStart(), et.getSelectionEnd());
        int end = Math.max(et.getSelectionStart(), et.getSelectionEnd());
        boolean covered = false;
        for (Object s : ed.getSpans(start, end, cls)) {
            if (ed.getSpanStart(s) <= start && ed.getSpanEnd(s) >= end) {
                covered = true;
                break;
            }
        }
        if (covered) {
            for (Object s : ed.getSpans(start, end, cls)) {
                ed.removeSpan(s);
            }
        } else {
            ed.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        syncBlockText();
    }

    /** 校验焦点文本块已选中文字（未选中则提示） */
    private boolean requireSelection(EditText et) {
        int start = et.getSelectionStart();
        int end = et.getSelectionEnd();
        if (start < 0 || end < 0 || start == end) {
            Toast.makeText(this, getString(R.string.note_edit_select_text_first), Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    /** 工具栏改 span 后把块的 markdown 同步回去（setSpan 不触发 TextWatcher） */
    private void syncBlockText() {
        if (activeTextBlock == null || activeBlockPosition < 0 || activeBlockPosition >= blocks.size()) {
            return;
        }
        NoteBlocks.Block b = blocks.get(activeBlockPosition);
        String md = BlockRichText.toMarkdown(activeTextBlock.getText());
        if (!md.equals(b.text)) {
            b.text = md;
            dirty = true;
        }
        activeTextBlock.invalidate();
    }

    /** 浮动格式条：显示并定位到焦点块上方偏左 */
    private void showFloatingBar() {
        if (activeTextBlock == null) {
            floatBar.setVisibility(View.GONE);
            return;
        }
        floatBar.setVisibility(View.VISIBLE);
        floatBar.post(this::positionFloatingBar);
    }

    /** 按焦点块当前屏幕位置重算浮动条坐标（滚动/布局变化时跟随） */
    private void positionFloatingBar() {
        if (activeTextBlock == null || floatBar.getVisibility() != View.VISIBLE) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        int barH = floatBar.getMeasuredHeight();
        if (barH <= 0) {
            barH = Math.round(44 * density);
        }
        int[] etPos = new int[2];
        int[] framePos = new int[2];
        activeTextBlock.getLocationOnScreen(etPos);
        blockFrame.getLocationOnScreen(framePos);
        int x = etPos[0] - framePos[0];
        int y = etPos[1] - framePos[1] - barH - Math.round(8 * density);
        y = Math.max(Math.round(4 * density), y);
        floatBar.setX(x);
        floatBar.setY(y);
    }

    // ==================== 媒体插入 ====================

    /** 图片/画画：写本地文件并追加图片块（local://，同步时上传改写） */
    private void addImageBlock(byte[] bytes, String fileName, String mime) {
        try {
            String unique = System.currentTimeMillis() + "_" + fileName;
            File f = new File(MediaFiles.localMediaDir(this), unique);
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(bytes);
            }
            mainHandler.post(() -> {
                NoteBlocks.Block b = new NoteBlocks.Block();
                b.type = NoteBlocks.IMAGE;
                b.mediaUrl = MediaFiles.localUrl(unique);
                b.alt = getString(R.string.note_draw_title);
                insertOrAppendMedia(b);
            });
        } catch (IOException e) {
            mainHandler.post(() -> Toast.makeText(this, getString(R.string.note_edit_image_save_failed), Toast.LENGTH_SHORT).show());
        }
    }

    /** 追加媒体块（不再自动补文本块，文本由用户手动插入） */
    private void appendMediaBlock(NoteBlocks.Block media) {
        blocks.add(media);
        blockAdapter.notifyItemInserted(blocks.size() - 1);
        scrollToBlock(blocks.size() - 1);   // 追加后界面滚动过去
        dirty = true;
    }

    /** 录音对话框：MediaRecorder 录音 → 追加音频块 */
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

        btn.setOnClickListener(v -> {
            if (!recording[0]) {
                try {
                    recordingFile[0] = new File(getCacheDir(), "record_" + System.currentTimeMillis() + ".m4a");
                    MediaRecorder r = Build.VERSION.SDK_INT >= 31
                            ? new MediaRecorder(this)
                            : new MediaRecorder();
                    r.setAudioSource(MediaRecorder.AudioSource.MIC);
                    r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                    r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                    r.setAudioSamplingRate(44100);
                    r.setOutputFile(recordingFile[0].getAbsolutePath());
                    r.prepare();
                    r.start();
                    activeRecorder[0] = r;
                    recording[0] = true;
                    recordingStartMs[0] = System.currentTimeMillis();
                    status.setText(getString(R.string.note_edit_recording_status));
                    btn.setText(getString(R.string.note_edit_record_stop));
                } catch (Exception e) {
                    Toast.makeText(this, getString(R.string.note_edit_recording_start_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
                }
            } else {
                try {
                    activeRecorder[0].stop();
                } catch (Exception ignored) {
                }
                try {
                    activeRecorder[0].release();
                } catch (Exception ignored) {
                }
                activeRecorder[0] = null;
                recording[0] = false;
                long dur = System.currentTimeMillis() - recordingStartMs[0];
                btn.setEnabled(false);
                dialog.dismiss();
                addAudioBlock(recordingFile[0], dur);
            }
        });
        dialog.setOnDismissListener(d -> {
            if (recording[0] && activeRecorder[0] != null) {
                try { activeRecorder[0].stop(); } catch (Exception ignored) { }
                try { activeRecorder[0].release(); } catch (Exception ignored) { }
                activeRecorder[0] = null;
                recording[0] = false;
            }
        });
        dialog.show();
    }

    /** 录音文件 → 本地 note_media + 音频块（带时长；优先用墙钟时长，避免 MediaPlayer/Retriever 误读） */
    private void addAudioBlock(File rec, long wallDurationMs) {
        if (rec == null || !rec.exists()) return;
        ioExecutor.execute(() -> {
            try {
                byte[] bytes = readAllFile(rec);
                if (bytes.length == 0) {
                    mainHandler.post(() -> Toast.makeText(this,
                            getString(R.string.note_edit_recording_empty), Toast.LENGTH_SHORT).show());
                    return;
                }
                String fileName = System.currentTimeMillis() + "_recording.m4a";
                File f = new File(MediaFiles.localMediaDir(this), fileName);
                try (FileOutputStream out = new FileOutputStream(f)) {
                    out.write(bytes);
                }
                long dur = wallDurationMs > 0 ? wallDurationMs : audioDurationMs(f.getAbsolutePath());
                String url = MediaFiles.localUrl(fileName) + "?mediaType=audio";
                if (dur > 0) {
                    url += "&duration=" + dur;
                }
                final String finalUrl = url;
                final long finalDur = dur;
                mainHandler.post(() -> {
                    NoteBlocks.Block b = new NoteBlocks.Block();
                    b.type = NoteBlocks.AUDIO;
                    b.mediaUrl = finalUrl;
                    b.duration = finalDur;
                    insertOrAppendMedia(b);
                });
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(this,
                        getString(R.string.note_edit_recording_upload_failed), Toast.LENGTH_SHORT).show());
            }
        });
    }

    /** 用 MediaMetadataRetriever 读音频时长（ms） */
    private long audioDurationMs(String path) {
        try {
            android.media.MediaMetadataRetriever mmr = new android.media.MediaMetadataRetriever();
            try {
                mmr.setDataSource(path);
                String d = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
                return d != null ? Long.parseLong(d) : -1;
            } finally {
                try { mmr.release(); } catch (Exception ignored) { }
            }
        } catch (Exception e) {
            return -1;
        }
    }

    private byte[] readAll(Uri uri) throws IOException {
        java.io.InputStream in = getContentResolver().openInputStream(uri);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        in.close();
        return out.toByteArray();
    }

    private byte[] readAllFile(File f) throws IOException {
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    // ==================== 加载 / 保存 ====================

    private void loadNote(long localId) {
        LocalNote n = dao.getById(localId);
        if (n == null) {
            finish();
            return;
        }
        currentNote = n;
        if (etTitle.getText().toString().isEmpty()) {
            etTitle.setText(n.title != null ? n.title : "");
        }
        blocks.clear();
        blocks.addAll(NoteBlocks.fromDoc(MarkdownParser.parse(n.content != null ? n.content : "")));
        blockAdapter.notifyDataSetChanged();
        dirty = false;
        resumeNoteProgress();
        if (n.conflict) {
            showConflictDialog(false);
        }
    }

    private void save(final boolean finishAfter) {
        if (saving) return;
        saving = true;
        String title = etTitle.getText().toString().trim();
        String content = MarkdownEmitter.emit(NoteBlocks.toDoc(blocks));

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
        if (finishAfter) {
            saveNoteProgress();
            finish();
        }
    }

    /** 乐观并发冲突弹窗：覆盖 / 重新加载 */
    private void showConflictDialog(final boolean finishAfter) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.note_edit_conflict_title))
                .setMessage(getString(R.string.note_edit_conflict_message))
                .setPositiveButton(getString(R.string.note_edit_overwrite), (d, w) -> {
                    if (currentNote != null) {
                        currentNote.baseUpdatedAt = null;
                        currentNote.conflict = false;
                    }
                    save(finishAfter);
                })
                .setNegativeButton(getString(R.string.note_edit_reload), (d, w) -> reloadNote(finishAfter))
                .setCancelable(false)
                .show();
    }

    /** 重新加载服务端最新内容（冲突「重载」） */
    private void reloadNote(final boolean finishAfter) {
        if (currentNote == null || currentNote.serverId == null) {
            finish();
            return;
        }
        client.getNote(currentNote.serverId).enqueue(new com.baiflow.android.network.UiCallback<com.baiflow.android.model.ApiResponse<com.baiflow.android.model.NoteDetail>>(this) {
            @Override
            protected void onUiResponse(retrofit2.Call<com.baiflow.android.model.ApiResponse<com.baiflow.android.model.NoteDetail>> call,
                                        retrofit2.Response<com.baiflow.android.model.ApiResponse<com.baiflow.android.model.NoteDetail>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isOk()
                        && response.body().getData() != null) {
                    com.baiflow.android.model.NoteDetail detail = response.body().getData();
                    currentNote.title = detail.getTitle() != null ? detail.getTitle() : "";
                    currentNote.content = detail.getContent() != null ? detail.getContent() : "";
                    currentNote.baseUpdatedAt = detail.getUpdatedAt();
                    currentNote.dirty = false;
                    currentNote.conflict = false;
                    currentNote.updatedAt = System.currentTimeMillis();
                    dao.update(currentNote);
                    etTitle.setText(currentNote.title);
                    blocks.clear();
                    blocks.addAll(NoteBlocks.fromDoc(MarkdownParser.parse(currentNote.content)));
                    blockAdapter.notifyDataSetChanged();
                    dirty = false;
                    Toast.makeText(NoteEditActivity.this, getString(R.string.note_edit_reloaded), Toast.LENGTH_SHORT).show();
                    if (finishAfter) finish();
                }
            }

            @Override
            protected void onUiFailure(retrofit2.Call<com.baiflow.android.model.ApiResponse<com.baiflow.android.model.NoteDetail>> call, Throwable t) {
            }
        });
    }

    // ==================== 阅读进度 ====================

    private void resumeNoteProgress() {
        if (currentNote == null || currentNote.serverId == null) return;
        String noteId = currentNote.serverId;
        new Thread(() -> {
            double pct = ProgressReporter.fetchNoteProgress(client, noteId);
            mainHandler.post(() -> applyNoteProgress(pct));
        }).start();
    }

    /** 恢复到上次阅读位置：滚动块列表 */
    private void applyNoteProgress(double pct) {
        if (pct <= 0.01) return;
        recyclerBlocks.post(() -> {
            int max = recyclerBlocks.computeVerticalScrollRange() - recyclerBlocks.computeVerticalScrollExtent();
            if (max > 0) {
                recyclerBlocks.scrollBy(0, (int) (pct * max));
                Toast.makeText(this, R.string.progress_resumed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** 保存当前阅读位置（块列表滚动比例） */
    private void saveNoteProgress() {
        if (currentNote == null || currentNote.serverId == null) return;
        int range = recyclerBlocks.computeVerticalScrollRange();
        int extent = recyclerBlocks.computeVerticalScrollExtent();
        int max = range - extent;
        if (max <= 0) return;
        int offset = recyclerBlocks.computeVerticalScrollOffset();
        double pct = Math.min(1, Math.max(0, (double) offset / max));
        ProgressReporter.saveNoteProgress(client, currentNote.serverId, pct);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (progressScrollTimer != null) mainHandler.removeCallbacks(progressScrollTimer);
        ioExecutor.shutdown();
    }
}
