package com.baiflow.android.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.baiflow.android.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 手写画布 — 记录笔画路径与画笔，支持颜色/粗细切换、撤销、清空，可导出 PNG 位图。
 */
public class NoteDrawView extends View {

    private final List<Stroke> strokes = new ArrayList<>();
    private Stroke current;
    private int strokeColor = 0xFF1D1D1F;
    private float strokeWidth = 6f;

    public NoteDrawView(Context context) {
        super(context);
        strokeColor = context.getColor(R.color.text_primary);
    }

    public NoteDrawView(Context context, AttributeSet attrs) {
        super(context, attrs);
        strokeColor = context.getColor(R.color.text_primary);
    }

    public void setStrokeColor(int color) {
        this.strokeColor = color;
    }

    public void setStrokeWidth(float width) {
        this.strokeWidth = Math.max(2f, width);
    }

    public boolean hasContent() {
        return !strokes.isEmpty() || current != null;
    }

    /** 撤销上一笔 */
    public void undo() {
        if (!strokes.isEmpty()) {
            strokes.remove(strokes.size() - 1);
            invalidate();
        }
    }

    /** 清空画布 */
    public void clear() {
        strokes.clear();
        current = null;
        invalidate();
    }

    /** 渲染当前画布为 PNG 位图（白底） */
    public Bitmap toBitmap() {
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Color.WHITE);
        for (Stroke s : strokes) {
            c.drawPath(s.path, s.paint);
        }
        return bmp;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                current = new Stroke(strokeColor, strokeWidth);
                current.path.moveTo(x, y);
                getParent().requestDisallowInterceptTouchEvent(true);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (current != null) {
                    current.path.lineTo(x, y);
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                if (current != null) {
                    strokes.add(current);
                    current = null;
                    invalidate();
                }
                return true;
            }
            default:
                return true;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (Stroke s : strokes) {
            canvas.drawPath(s.path, s.paint);
        }
        if (current != null) {
            canvas.drawPath(current.path, current.paint);
        }
    }

    private static final class Stroke {
        final Path path = new Path();
        final Paint paint;

        Stroke(int color, float width) {
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(width);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(color);
        }
    }
}
