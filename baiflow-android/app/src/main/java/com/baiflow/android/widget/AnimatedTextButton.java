package com.baiflow.android.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.animation.DecelerateInterpolator;

import com.google.android.material.button.MaterialButton;

/**
 * 按压时文字颜色渐变过渡的按钮（配合 {@code @color/text_accent_selector} 使用，
 * 正常 accent → 按压 accent_light 平滑过渡）。仅当 textColor 是有按压态差别的 selector 时才生效。
 * <p>注意：动画过程中 setTextColor(int) 会覆盖原 selector，故只在首次解析一次原色并缓存，避免二次按压失效。
 */
public class AnimatedTextButton extends MaterialButton {

    private ValueAnimator animator;
    private int normalColor = Color.TRANSPARENT;
    private int pressedColor = Color.TRANSPARENT;
    private int currentColor = Color.TRANSPARENT;
    private boolean resolved;

    public AnimatedTextButton(Context context) { this(context, null); }
    public AnimatedTextButton(Context context, AttributeSet attrs) { this(context, attrs, 0); }
    public AnimatedTextButton(Context context, AttributeSet attrs, int defStyleAttr) {
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

    /** 强制回到常态颜色（弹窗/下拉打开后按压态可能未清除时调用） */
    public void resetColor() {
        animateColor(normalColor);
    }

    private void animateColor(int target) {
        if (currentColor == target) return;
        if (animator != null) animator.cancel();
        // currentColor 每帧跟踪实际渲染色：drawableStateChanged 连续触发时从当前色继续平滑过渡，
        // 不会把动画取消冻结在半途（此前 bug：先置 target 再 cancel 导致「点击完才变色」）
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
