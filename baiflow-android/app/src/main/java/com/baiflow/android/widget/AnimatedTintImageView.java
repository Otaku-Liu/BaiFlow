package com.baiflow.android.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.animation.DecelerateInterpolator;

import androidx.appcompat.widget.AppCompatImageView;

/**
 * 按压时图标着色渐变过渡的 ImageView（配合 {@code android:tint="@color/text_accent_selector"} 使用）。
 * 通过 {@code duplicateParentState} 继承父按钮按压态。
 * <p>首次解析一次原 tint 缓存，避免动画 setImageTintList 覆盖 selector 后二次按压失效。
 */
public class AnimatedTintImageView extends AppCompatImageView {

    private ValueAnimator animator;
    private int normalColor = Color.TRANSPARENT;
    private int pressedColor = Color.TRANSPARENT;
    private int disabledColor = Color.TRANSPARENT;
    private int currentColor = Color.TRANSPARENT;
    private boolean resolved;

    public AnimatedTintImageView(Context context) { this(context, null); }
    public AnimatedTintImageView(Context context, AttributeSet attrs) { this(context, attrs, 0); }
    public AnimatedTintImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        resolveOnce();
        int target;
        if (!isEnabled()) {
            target = disabledColor;
        } else {
            target = hasState(getDrawableState(), android.R.attr.state_pressed) ? pressedColor : normalColor;
        }
        animateColor(target);
    }

    private void resolveOnce() {
        if (resolved) return;
        ColorStateList csl = getImageTintList();
        if (csl != null) {
            normalColor = csl.getColorForState(new int[0], normalColor);
            pressedColor = csl.getColorForState(new int[]{android.R.attr.state_pressed}, pressedColor);
            disabledColor = csl.getColorForState(new int[]{-android.R.attr.state_enabled}, disabledColor);
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
            setImageTintList(ColorStateList.valueOf(currentColor));
        });
        animator.start();
    }
}
