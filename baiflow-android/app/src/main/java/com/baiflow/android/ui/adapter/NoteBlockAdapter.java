package com.baiflow.android.ui.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.Editable;
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
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.baiflow.android.R;
import com.baiflow.android.data.MediaFiles;
import com.baiflow.android.editor.BlockRichText;
import com.baiflow.android.editor.EditorStyle;
import com.baiflow.android.editor.NoteBlocks;
import com.baiflow.android.network.ApiClient;
import com.baiflow.android.ui.view.BlockEditText;
import com.baiflow.android.ui.view.NoteAudioPlayerView;

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

        /** 文本块 EditText 获得焦点（浮动格式条跟随块显示；position 供块类型切换用） */
        void onTextBlockFocused(int position, EditText et);

        /** 文本块 EditText 失去焦点（浮动格式条隐藏） */
        void onTextBlockFocusLost();

        /** 点击块顶部「＋」：在该块上方插入新块（anchor 为 ＋ 按钮，供弹出菜单定位） */
        void onInsertAbove(int position, View anchor);

        /** 文本选中菜单（ActionMode）开合：开合时隐藏/恢复浮动格式条 */
        void onTextSelectionChanged(boolean selecting);
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
        root.setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 8));

        // 内容行：拖动柄 + 具体内容
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

            BlockEditText et = new BlockEditText(ctx);
            et.setBackground(null);
            et.setTextColor(0xFF1D1D1F);
            et.setTextSize(15f);
            et.setSingleLine(false);
            et.setTag("block_text");   // 供「在上方插入」后聚焦新块用
            content.addView(et, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            body = et;
        }

        root.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

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

        // 顶部居中「＋」（白底圆）：在该块上方插入，悬在两卡之间的分隔处，完整可见
        TextView insert = new TextView(ctx);
        insert.setText("＋");
        insert.setTextColor(0xFF007AFF);
        insert.setTextSize(14f);
        insert.setGravity(Gravity.CENTER);
        insert.setBackgroundResource(R.drawable.bg_insert_plus);
        FrameLayout.LayoutParams insLp = new FrameLayout.LayoutParams(dp(ctx, 22), dp(ctx, 22));
        insLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        insLp.topMargin = -dp(ctx, 22);
        root.addView(insert, insLp);
        root.setClipChildren(false);

        Holder holder;
        if (viewType == NoteBlocks.IMAGE) {
            holder = new ImageHolder(root, content, (ImageView) body, drag, del, insert);
        } else if (viewType == NoteBlocks.AUDIO) {
            holder = new AudioHolder(root, content, (NoteAudioPlayerView) body, drag, del, insert);
        } else {
            holder = new TextHolder(root, content, (EditText) body, drag, del, insert);
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
        final TextView insert;

        Holder(FrameLayout root, LinearLayout content, View body, TextView drag, TextView del, TextView insert) {
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

        TextHolder(FrameLayout root, LinearLayout content, EditText et, TextView drag, TextView del, TextView insert) {
            super(root, content, et, drag, del, insert);
            this.typeBtn = (TextView) content.getChildAt(1);
            this.et = et;
            if (et instanceof BlockEditText) {
                BlockEditText bet = (BlockEditText) et;
                // 选中菜单里点加粗/斜体等 → span 已改 → 回写 markdown
                bet.setOnSpanAppliedListener(() -> {
                    NoteBlocks.Block bl = blocks.get(getBindingAdapterPosition());
                    if (bl == null) return;
                    bl.text = BlockRichText.toMarkdown(bet.getText());
                    listener.onChanged();
                });
                // 选中菜单开合 → 隐藏/恢复浮动格式条
                bet.setActionModeStateListener(listener::onTextSelectionChanged);
            }
            typeBtn.setOnClickListener(v -> showTypeMenu(v, getBindingAdapterPosition()));
            et.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    listener.onTextBlockFocused(getBindingAdapterPosition(), et);
                } else {
                    listener.onTextBlockFocusLost();
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
            // 所见即所得：渲染行内 markdown 的格式效果（加粗/斜体/下划线/删除线等）
            et.setText(BlockRichText.toSpannable(b.text, editorStyle));
            suppress = false;
        }

        private String typeLabel(NoteBlocks.Block b) {
            Context ctx = et.getContext();
            return b.type == NoteBlocks.HEADING
                    ? ctx.getString(R.string.note_block_heading)
                    : ctx.getString(R.string.note_block_text);
        }

        private void showTypeMenu(View anchor, int pos) {
            PopupMenu menu = new PopupMenu(et.getContext(), anchor, Gravity.NO_GRAVITY, 0, R.style.Ios_PopupMenu);
            menu.getMenu().add(0, 1, 0, et.getContext().getString(R.string.note_block_text));
            menu.getMenu().add(0, 2, 0, et.getContext().getString(R.string.note_block_heading));
            menu.setOnMenuItemClickListener(item -> {
                int type = item.getItemId() == 2 ? NoteBlocks.HEADING : NoteBlocks.TEXT;
                listener.onSwitchType(pos, type, 1);
                return true;
            });
            menu.show();
        }
    }

    class ImageHolder extends Holder {
        private final ImageView iv;
        private String boundUrl;   // 当前绑定的图片 URL（异步回填前校验，防止复用错图）

        ImageHolder(FrameLayout root, LinearLayout content, ImageView iv, TextView drag, TextView del, TextView insert) {
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

        AudioHolder(FrameLayout root, LinearLayout content, NoteAudioPlayerView pv, TextView drag, TextView del, TextView insert) {
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
