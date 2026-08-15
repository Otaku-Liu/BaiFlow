package com.baiflow.android.ui.view;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.Spanned;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.widget.AppCompatEditText;

import com.baiflow.android.R;

/**
 * 块文本编辑框 — 自定义文本选中菜单（ActionMode）：
 * 在系统「剪切/复制/粘贴/全选」旁加入 加粗/斜体/下划线/删除线，
 * 避免系统菜单遮挡自定义浮动格式条。格式改动后通过 {@link OnSpanAppliedListener} 通知回写 markdown。
 */
public class BlockEditText extends AppCompatEditText {

    private static final int MENU_BOLD = 0x1F001;
    private static final int MENU_ITALIC = 0x1F002;
    private static final int MENU_UNDERLINE = 0x1F003;
    private static final int MENU_STRIKE = 0x1F004;

    /** 格式 span 已改动（需要把块的 markdown 同步回去） */
    public interface OnSpanAppliedListener {
        void onSpanApplied();
    }

    /** 文本选中菜单开合（用于隐藏/恢复浮动格式条） */
    public interface ActionModeStateListener {
        void onTextSelectionChanged(boolean selecting);
    }

    private OnSpanAppliedListener spanListener;
    private ActionModeStateListener modeListener;

    public BlockEditText(Context context) {
        super(context);
        init();
    }

    public BlockEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BlockEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                addTextItem(menu, android.R.id.cut, android.R.string.cut);
                addTextItem(menu, android.R.id.copy, android.R.string.copy);
                addTextItem(menu, android.R.id.paste, android.R.string.paste);
                addTextItem(menu, android.R.id.selectAll, android.R.string.selectAll);
                addFormatItem(menu, MENU_BOLD, R.string.note_edit_bold, R.drawable.ic_tool_bold);
                addFormatItem(menu, MENU_ITALIC, R.string.note_edit_italic, R.drawable.ic_tool_italic);
                addFormatItem(menu, MENU_UNDERLINE, R.string.note_edit_underline, R.drawable.ic_tool_underline);
                addFormatItem(menu, MENU_STRIKE, R.string.note_edit_strike, R.drawable.ic_tool_strike);
                if (modeListener != null) {
                    modeListener.onTextSelectionChanged(true);
                }
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                int id = item.getItemId();
                if (id == android.R.id.cut || id == android.R.id.copy
                        || id == android.R.id.paste || id == android.R.id.selectAll) {
                    onTextContextMenuItem(id);
                    mode.finish();
                    return true;
                }
                if (id == MENU_BOLD) {
                    applyStyle(Typeface.BOLD);
                    mode.finish();
                    return true;
                }
                if (id == MENU_ITALIC) {
                    applyStyle(Typeface.ITALIC);
                    mode.finish();
                    return true;
                }
                if (id == MENU_UNDERLINE) {
                    applySpan(UnderlineSpan.class, new UnderlineSpan());
                    mode.finish();
                    return true;
                }
                if (id == MENU_STRIKE) {
                    applySpan(StrikethroughSpan.class, new StrikethroughSpan());
                    mode.finish();
                    return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                if (modeListener != null) {
                    modeListener.onTextSelectionChanged(false);
                }
            }
        });
    }

    private void addTextItem(Menu menu, int id, int strRes) {
        menu.add(Menu.NONE, id, 0, getContext().getString(strRes))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    }

    private void addFormatItem(Menu menu, int id, int strRes, int iconRes) {
        menu.add(Menu.NONE, id, 0, getContext().getString(strRes))
                .setIcon(iconRes)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    }

    /** 加粗/斜体 toggle：整段被同款式覆盖则移除，否则加样式 */
    private void applyStyle(int style) {
        Editable ed = getText();
        int start = Math.min(getSelectionStart(), getSelectionEnd());
        int end = Math.max(getSelectionStart(), getSelectionEnd());
        if (start < 0 || end < 0 || start == end) {
            return;
        }
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
        notifySpanApplied();
    }

    /** 下划线/删除线 toggle */
    private void applySpan(Class<?> cls, Object span) {
        Editable ed = getText();
        int start = Math.min(getSelectionStart(), getSelectionEnd());
        int end = Math.max(getSelectionStart(), getSelectionEnd());
        if (start < 0 || end < 0 || start == end) {
            return;
        }
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
        notifySpanApplied();
    }

    private void notifySpanApplied() {
        if (spanListener != null) {
            spanListener.onSpanApplied();
        }
        invalidate();
    }

    public void setOnSpanAppliedListener(OnSpanAppliedListener l) {
        this.spanListener = l;
    }

    public void setActionModeStateListener(ActionModeStateListener l) {
        this.modeListener = l;
    }
}
