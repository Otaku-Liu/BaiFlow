package com.baiflow.android.editor;

import android.content.Context;
import android.text.Spannable;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.appcompat.widget.AppCompatEditText;

/**
 * 富文本笔记正文编辑框 — 承载 {@link ListKeyListener}（回车延续列表）与
 * 媒体 span 点击拦截（图片查看/替换/删除、音频播放）。
 * <p>
 * 媒体点击在 {@link #onTouchEvent} 中按落点 offset 命中 span 处理，其余事件
 * 交还父类，不破坏光标定位与长按选择。
 */
public class RichEditText extends AppCompatEditText {

    /** 媒体 span 点击回调 */
    public interface OnMediaTapListener {
        void onImageTapped(NoteImageSpan span);

        void onAudioTapped(NoteAudioSpan span);
    }

    private OnMediaTapListener mediaTapListener;

    public RichEditText(Context context) {
        super(context);
        init();
    }

    public RichEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RichEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        addTextChangedListener(new ListKeyListener());
    }

    public void setMediaTapListener(OnMediaTapListener listener) {
        this.mediaTapListener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP && mediaTapListener != null && isFocused()) {
            int offset = getOffsetForPosition(event.getX(), event.getY());
            if (offset >= 0 && offset <= getText().length()) {
                Spannable sp = getText();
                NoteAudioSpan audio = first(sp, offset, NoteAudioSpan.class);
                if (audio != null) {
                    mediaTapListener.onAudioTapped(audio);
                    return true;
                }
                NoteImageSpan image = first(sp, offset, NoteImageSpan.class);
                if (image != null) {
                    mediaTapListener.onImageTapped(image);
                    return true;
                }
            }
        }
        return super.onTouchEvent(event);
    }

    private static <T> T first(Spannable s, int offset, Class<T> cls) {
        // 折叠光标用 [p, p+1) 查，规避 getSpans(p,p) 的 off-by-one
        T[] spans = s.getSpans(offset, Math.min(offset + 1, s.length()), cls);
        return spans.length > 0 ? spans[0] : null;
    }
}
