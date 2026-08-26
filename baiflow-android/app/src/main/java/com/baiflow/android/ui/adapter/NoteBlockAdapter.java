package com.baiflow.android.ui.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.baiflow.android.R;
import com.baiflow.android.data.MediaFiles;
import com.baiflow.android.editor.BlockRichText;
import com.baiflow.android.editor.EditorStyle;
import com.baiflow.android.editor.NoteBlocks;
import com.baiflow.android.network.ApiClient;
import com.baiflow.android.ui.view.NoteAudioPlayerView;
import com.baiflow.android.widget.DropdownMenu;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * 笔记块编辑器适配器（与 Web 块编辑器一致）：
 * 每块一个卡片（浅底圆角边框），左侧拖动柄（长按拖动排序）+ 文本块左上角类型标识（点击切换），
 * 右上角 × 删除；空文本块按删除键删除本块；媒体块是真实 View。
 */
public class NoteBlockAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        void onChanged();

        void onDelete(int position);

        void onSwitchType(int position, int type, int level);

        void onStartDrag(RecyclerView.ViewHolder holder);

        /** 文本块 EditText 获得焦点（记录最近聚焦块，供工具栏/格式栏 B/I/U/S 操作） */
        void onTextBlockFocused(int position, EditText et);

        /** 点击块顶部「＋」：在该块上方插入新块（anchor 为 ＋ 按钮，供弹出菜单定位） */
        void onInsertAbove(int position, View anchor);
    }

    private final List<NoteBlocks.Block> blocks;
    private final Listener listener;
    private final ApiClient client;
    private final EditorStyle editorStyle;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    // 图片位图缓存（mediaUrl → Bitmap）：滑动复用 ViewHolder 时直接命中，避免反复异步加载导致闪烁
    private final android.util.LruCache<String, Bitmap> imageCache = new android.util.LruCache<>(32);

    public NoteBlockAdapter(List<NoteBlocks.Block> blocks, Listener listener, ApiClient client,
                            EditorStyle editorStyle) {
        this.blocks = blocks;
        this.listener = listener;
        this.client = client;
        this.editorStyle = editorStyle;
    }

    @Override
    public int getItemViewType(int position) {
        return blocks.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context ctx = parent.getContext();
        // 卡片根：FrameLayout 承载内容 + 右上角删除（显式撑满宽度，避免按内容收缩）
        FrameLayout root = new FrameLayout(ctx);
        root.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.setBackgroundResource(R.drawable.bg_block_card);
        root.setPadding(dp(ctx, 12), dp(ctx, 6), dp(ctx, 12), dp(ctx, 8));

        // 插入条（卡片顶部，全宽，点击在块上方插入）：居中蓝色「＋」。
        // ＋ 为普通 TextView 文字绘制，不依赖 drawable 背景，保证可见；
        // 整条可点，置于卡内顶部、不凸出，避免被相邻卡片遮挡或裁剪
        FrameLayout insertBar = new FrameLayout(ctx);
        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 28));
        barLp.gravity = Gravity.TOP;
        barLp.rightMargin = dp(ctx, 30);   // 给右上角删除 × 让位
        root.addView(insertBar, barLp);

        TextView insert = new TextView(ctx);
        insert.setText("＋");
        insert.setTextColor(ctx.getColor(R.color.accent));
        insert.setTextSize(20f);
        insert.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams insLp = new FrameLayout.LayoutParams(dp(ctx, 28), dp(ctx, 28));
        insLp.gravity = Gravity.CENTER;
        insertBar.addView(insert, insLp);

        // 内容行：拖动柄 + 具体内容（位于插入条下方）
        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 拖动柄（⋮⋮⋮，长按拖动排序）
        TextView drag = new TextView(ctx);
        drag.setText("⋮⋮⋮");
        drag.setTextColor(0xFFC7C7CC);
        drag.setTextSize(13f);
        drag.setPadding(dp(ctx, 0), 0, dp(ctx, 4), 0);
        drag.setGravity(Gravity.CENTER);
        content.addView(drag, new LinearLayout.LayoutParams(dp(ctx, 24), ViewGroup.LayoutParams.WRAP_CONTENT));

        View body;
        if (viewType == NoteBlocks.IMAGE) {
            ImageView iv = new ImageView(ctx);
            iv.setAdjustViewBounds(true);
            iv.setMaxWidth(dp(ctx, 240));
            content.addView(iv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            body = iv;
        } else if (viewType == NoteBlocks.AUDIO) {
            NoteAudioPlayerView pv = new NoteAudioPlayerView(ctx);
            pv.setClient(client);
            // 录音块宽度拉满整块，右侧留出空间给右上角删除 ×（不被遮挡）
            LinearLayout.LayoutParams pvLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            pvLp.setMarginEnd(dp(ctx, 32));
            content.addView(pv, pvLp);
            body = pv;
        } else {
            // 文本类块：类型标识 + EditText
            TextView typeBtn = new TextView(ctx);
            typeBtn.setTextColor(0xFF86868B);
            typeBtn.setTextSize(12f);
            typeBtn.setGravity(Gravity.CENTER);
            typeBtn.setPadding(0, dp(ctx, 4), dp(ctx, 4), 0);
            content.addView(typeBtn, new LinearLayout.LayoutParams(dp(ctx, 40), ViewGroup.LayoutParams.WRAP_CONTENT));

            EditText et = new EditText(ctx);
            et.setBackground(null);
            et.setTextColor(0xFF1D1D1F);
            et.setTextSize(15f);
            et.setSingleLine(false);
            et.setTag("block_text");   // 供「在上方插入」后聚焦新块用
            content.addView(et, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            body = et;
        }

        FrameLayout.LayoutParams contentLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        contentLp.topMargin = dp(ctx, 28);   // 位于插入条下方
        root.addView(content, contentLp);

        // 右上角删除 ×（常显，点击删除本块）
        TextView del = new TextView(ctx);
        del.setText("×");
        del.setTextColor(0xFF86868B);
        del.setTextSize(16f);
        del.setGravity(Gravity.CENTER);
        del.setPadding(dp(ctx, 6), dp(ctx, 2), dp(ctx, 6), dp(ctx, 2));
        FrameLayout.LayoutParams delLp = new FrameLayout.LayoutParams(dp(ctx, 26), dp(ctx, 26));
        delLp.gravity = Gravity.TOP | Gravity.END;
        root.addView(del, delLp);

        root.setClipChildren(false);

        Holder holder;
        if (viewType == NoteBlocks.IMAGE) {
            holder = new ImageHolder(root, content, (ImageView) body, drag, del, insertBar);
        } else if (viewType == NoteBlocks.AUDIO) {
            holder = new AudioHolder(root, content, (NoteAudioPlayerView) body, drag, del, insertBar);
        } else {
            holder = new TextHolder(root, content, (EditText) body, drag, del, insertBar);
        }
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        NoteBlocks.Block b = blocks.get(position);
        Holder h = (Holder) holder;
        h.del.setOnClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p >= 0) listener.onDelete(p);
        });
        h.insert.setOnClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p >= 0) listener.onInsertAbove(p, h.insert);
        });
        // 长按拖动柄 → 拖动排序
        h.drag.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                listener.onStartDrag(holder);
            }
            return false;
        });
        if (holder instanceof TextHolder) {
            ((TextHolder) holder).bind(b);
        } else if (holder instanceof ImageHolder) {
            ((ImageHolder) holder).bind(b);
        } else if (holder instanceof AudioHolder) {
            ((AudioHolder) holder).bind(b);
        }
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof AudioHolder) {
            ((AudioHolder) holder).release();
        }
    }

    @Override
    public int getItemCount() {
        return blocks.size();
    }

    /** ItemTouchHelper 拖动时移动块 */
    public void moveItem(int from, int to) {
        NoteBlocks.Block b = blocks.remove(from);
        blocks.add(to, b);
        notifyItemMoved(from, to);
        listener.onChanged();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final LinearLayout content;
        final View body;
        final TextView drag;
        final TextView del;
        final View insert;   // 插入条（整条可点，anchor 用）

        Holder(FrameLayout root, LinearLayout content, View body, TextView drag, TextView del, View insert) {
            super(root);
            this.content = content;
            this.body = body;
            this.drag = drag;
            this.del = del;
            this.insert = insert;
        }
    }

    /** 文本类块：类型标识（点击切换）+ EditText（空块按删除键删块） */
    class TextHolder extends Holder {
        private final TextView typeBtn;
        private final EditText et;
        private boolean suppress;

        TextHolder(FrameLayout root, LinearLayout content, EditText et, TextView drag, TextView del, View insert) {
            super(root, content, et, drag, del, insert);
            this.typeBtn = (TextView) content.getChildAt(1);
            this.et = et;
            typeBtn.setOnClickListener(v -> showTypeMenu(v, getBindingAdapterPosition()));
            et.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    listener.onTextBlockFocused(getBindingAdapterPosition(), et);
                }
            });
            et.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void afterTextChanged(Editable s) {
                    if (suppress) return;
                    NoteBlocks.Block bl = blocks.get(getBindingAdapterPosition());
                    if (bl == null) return;
                    // 编辑后把 Spannable 还原为行内 markdown 存回块
                    bl.text = BlockRichText.toMarkdown(s);
                    listener.onChanged();
                }
            });
            // 空文本块按删除键 → 删除本块
            et.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN
                        && et.getText().length() == 0) {
                    int pos = getBindingAdapterPosition();
                    if (pos >= 0) {
                        listener.onDelete(pos);
                        return true;
                    }
                }
                return false;
            });
        }

        void bind(NoteBlocks.Block b) {
            typeBtn.setText(typeLabel(b));
            typeBtn.setHint("");
            // 按块类型应用字体样式（类型切换后重新应用，保证标题显示为标题样式）
            if (b.type == NoteBlocks.HEADING) {
                et.setTextSize(20f);
                et.setTypeface(et.getTypeface(), android.graphics.Typeface.BOLD);
            } else {
                et.setTextSize(15f);
                et.setTypeface(et.getTypeface(), android.graphics.Typeface.NORMAL);
            }
            suppress = true;
            // 所见即所得：渲染行内 markdown 的格式效果（加粗/斜体/下划线/删除线等）。
            // 文本未变时跳过 setText，避免重绑（滚动/键盘 resize 等）把正在编辑块的选中/光标重置
            Spannable rendered = BlockRichText.toSpannable(b.text, editorStyle);
            if (!rendered.toString().equals(et.getText().toString())) {
                et.setText(rendered);
            }
            suppress = false;
        }

        private String typeLabel(NoteBlocks.Block b) {
            Context ctx = et.getContext();
            return b.type == NoteBlocks.HEADING
                    ? ctx.getString(R.string.note_block_heading)
                    : ctx.getString(R.string.note_block_text);
        }

        private void showTypeMenu(View anchor, int pos) {
            java.util.List<DropdownMenu.Option> options = new java.util.ArrayList<>();
            options.add(new DropdownMenu.Option(et.getContext().getString(R.string.note_block_text),
                    () -> listener.onSwitchType(pos, NoteBlocks.TEXT, 1)));
            options.add(new DropdownMenu.Option(et.getContext().getString(R.string.note_block_heading),
                    () -> listener.onSwitchType(pos, NoteBlocks.HEADING, 1)));
            DropdownMenu.show(et.getContext(), anchor, options);
        }
    }

    class ImageHolder extends Holder {
        private final ImageView iv;
        private String boundUrl;   // 当前绑定的图片 URL（异步回填前校验，防止复用错图）

        ImageHolder(FrameLayout root, LinearLayout content, ImageView iv, TextView drag, TextView del, View insert) {
            super(root, content, iv, drag, del, insert);
            this.iv = iv;
        }

        void bind(NoteBlocks.Block b) {
            boundUrl = b.mediaUrl;
            String key = b.mediaUrl == null ? "" : b.mediaUrl;
            Bitmap cached = imageCache.get(key);
            if (cached != null) {
                iv.setImageBitmap(cached);
                return;
            }
            File local = MediaFiles.resolveLocal(iv.getContext(), b.mediaUrl);
            if (local != null && local.exists()) {
                Bitmap bmp = BitmapFactory.decodeFile(local.getAbsolutePath());
                if (bmp != null) {
                    imageCache.put(key, bmp);
                    iv.setImageBitmap(bmp);
                    return;
                }
            }
            String mediaId = mediaIdFrom(b.mediaUrl);
            if (mediaId == null || mediaId.isEmpty()) {
                return;
            }
            // 未加载：先占位，异步加载后缓存并回填（校验 holder 未被复用于其他块，避免错图/闪烁）
            iv.setImageDrawable(editorStyle.newImagePlaceholder());
            ioExecutor.execute(() -> {
                try {
                    Response<ResponseBody> resp = client.getNoteMedia(mediaId).execute();
                    if (resp.isSuccessful() && resp.body() != null) {
                        byte[] bytes = resp.body().bytes();
                        Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        if (bmp != null) {
                            iv.post(() -> {
                                if (!key.equals(boundUrl)) {
                                    return;
                                }
                                imageCache.put(key, bmp);
                                iv.setImageBitmap(bmp);
                            });
                        }
                    }
                } catch (Exception ignored) {
                }
            });
        }
    }

    class AudioHolder extends Holder {
        private final NoteAudioPlayerView pv;

        AudioHolder(FrameLayout root, LinearLayout content, NoteAudioPlayerView pv, TextView drag, TextView del, View insert) {
            super(root, content, pv, drag, del, insert);
            this.pv = pv;
        }

        void bind(NoteBlocks.Block b) {
            pv.bind(b.mediaUrl, mediaIdFrom(b.mediaUrl), b.duration);
        }

        void release() {
            pv.release();
        }
    }

    private static String mediaIdFrom(String url) {
        if (url == null) return null;
        int idx = url.indexOf("/api/notes/media/");
        if (idx < 0) return null;
        String rest = url.substring(idx + "/api/notes/media/".length());
        int q = rest.indexOf('?');
        if (q >= 0) rest = rest.substring(0, q);
        return rest.isEmpty() ? null : rest;
    }

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
