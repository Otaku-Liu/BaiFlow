package com.baiflow.android.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.animation.DecelerateInterpolator;

import androidx.appcompat.widget.AppCompatTextView;

/**
 * 按压时文字颜色渐变过渡的文本（配合 {@code @color/text_accent_selector} 使用）。
 * 通过 {@code duplicateParentState} 继承父按钮按压态，实现与父按钮同步的渐变。
 * <p>首次解析一次原色缓存，避免动画 setTextColor 覆盖 selector 后二次按压失效。
 */
public class AnimatedTextLabel extends AppCompatTextView {

    private ValueAnimator animator;
    private int normalColor = Color.TRANSPARENT;
    private int pressedColor = Color.TRANSPARENT;
    private int currentColor = Color.TRANSPARENT;
    private boolean resolved;

    public AnimatedTextLabel(Context context) { this(context, null); }
    public AnimatedTextLabel(Context context, AttributeSet attrs) { this(context, attrs, 0); }
    public AnimatedTextLabel(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        resolveOnce();
        animateColor(hasState(getDrawableState(), android.R.attr.state_pressed) ? pressedColor : normalColor);
    }

    private void resolveOnce() {
        if (resolved) return;
        ColorStateList csl = getTextColors();
        if (csl != null) {
            normalColor = csl.getColorForState(new int[0], normalColor);
            pressedColor = csl.getColorForState(new int[]{android.R.attr.state_pressed}, pressedColor);
            currentColor = normalColor;
            resolved = true;
        }
    }

    private boolean hasState(int[] state, int attr) {
        for (int s : state) if (s == attr) return true;
        return false;
    }

    private void animateColor(int target) {
        if (currentColor == target) return;
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofArgb(currentColor, target);
        animator.setDuration(180);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            currentColor = (int) a.getAnimatedValue();
            setTextColor(currentColor);
        });
        animator.start();
    }
}
