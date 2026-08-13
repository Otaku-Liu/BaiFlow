package com.baiflow.android.util;

import android.app.Activity;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

/**
 * 键盘工具：点击页面空白区域（非输入框）时收起软键盘并让当前输入框失焦。
 * <p>用法：Activity 重写 {@code dispatchTouchEvent}，在 {@code ACTION_DOWN} 时调用
 * {@link #hideOnTouchOutside}，例如：{@code KeyboardUtil.hideOnTouchOutside(this, ev);}
 * 再 {@code super.dispatchTouchEvent(ev)}。仅在有输入框聚焦且点击点不在任何 EditText 内时收起，
 * 点击其它输入框（如标题↔正文切换）不收起。
 */
public final class KeyboardUtil {

    private KeyboardUtil() {
    }

    /**
     * 处理按下事件：当前聚焦的是输入框且点击点不在任何输入框内 → 失焦并隐藏键盘。
     * 返回是否执行了收起动作；无论是否收起，调用方都应把事件继续传给 super.dispatchTouchEvent。
     */
    public static boolean hideOnTouchOutside(Activity activity, MotionEvent ev) {
        if (ev.getAction() != MotionEvent.ACTION_DOWN) return false;
        View focused = activity.getCurrentFocus();
        if (focused == null || !(focused instanceof EditText)) return false;
        if (isPointInsideAnyEditText(activity.getWindow().getDecorView(),
                ev.getRawX(), ev.getRawY())) {
            return false;
        }
        focused.clearFocus();
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        }
        return true;
    }

    /** 递归判断屏幕坐标点是否落在任一 EditText（含 RichEditText 子类）内 */
    private static boolean isPointInsideAnyEditText(View view, float x, float y) {
        if (view instanceof EditText) {
            int[] loc = new int[2];
            view.getLocationOnScreen(loc);
            if (x >= loc[0] && x <= loc[0] + view.getWidth()
                    && y >= loc[1] && y <= loc[1] + view.getHeight()) {
                return true;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (isPointInsideAnyEditText(group.getChildAt(i), x, y)) return true;
            }
        }
        return false;
    }
}
