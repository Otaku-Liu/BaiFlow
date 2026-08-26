package com.baiflow.android.widget;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.baiflow.android.R;

import java.util.List;

/**
 * 统一下拉菜单（替换系统 PopupMenu，样式与设置页选项卡一致）：
 * 固定宽度、每行 44dp、行间**整行**浅灰分隔线（@color/divider）、
 * 可选左侧 √（选中项显示，未选中占位）+ 右侧图标槽（如排序方向箭头）。
 */
public final class DropdownMenu {

    private static final int ROW_HEIGHT_DP = 44;
    private static final int POPUP_WIDTH_DP = 160;

    public static class Option {
        final CharSequence label;
        final boolean checked;
        final int rightIconRes;
        final Runnable action;

        public Option(CharSequence label, Runnable action) {
            this(label, false, 0, action);
        }

        public Option(CharSequence label, boolean checked, int rightIconRes, Runnable action) {
            this.label = label;
            this.checked = checked;
            this.rightIconRes = rightIconRes;
            this.action = action;
        }
    }

    public static void show(Context context, View anchor, List<Option> options) {
        if (options == null || options.isEmpty()) {
            return;
        }
        boolean anyChecked = false;
        boolean anyRightIcon = false;
        for (Option o : options) {
            anyChecked |= o.checked;
            anyRightIcon |= o.rightIconRes != 0;
        }

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        // 浅灰底 + 细边框 + 投影：与白色卡片/工具栏区分（iOS 上下文菜单风）
        content.setBackground(ContextCompat.getDrawable(context, R.drawable.bg_dropdown_rounded));
        content.setElevation(dp(context, 8));
        int padV = dp(context, 6);
        content.setPadding(0, padV, 0, padV);

        final PopupWindow popup = new PopupWindow(content,
                dp(context, POPUP_WIDTH_DP), ViewGroup.LayoutParams.WRAP_CONTENT);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setFocusable(true);
        popup.setElevation(dp(context, 8));

        for (int i = 0; i < options.size(); i++) {
            if (i > 0) {
                View divider = new View(context);
                divider.setBackgroundColor(ContextCompat.getColor(context, R.color.divider));
                divider.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1)));
                content.addView(divider);
            }
            content.addView(buildRow(context, options.get(i), anyChecked, anyRightIcon, popup));
        }

        popup.showAsDropDown(anchor);
    }

    private static View buildRow(Context context, Option opt, boolean anyChecked, boolean anyRightIcon,
                                 PopupWindow popup) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, ROW_HEIGHT_DP)));
        row.setPadding(dp(context, 16), 0, dp(context, 12), 0);

        // 左侧 √ 槽：选中显示黑色、未选中占位（保持文字对齐）
        if (anyChecked) {
            TextView check = new TextView(context);
            check.setText("✓");
            check.setTextSize(16);
            check.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
            check.setVisibility(opt.checked ? View.VISIBLE : View.INVISIBLE);
            check.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 24), dp(context, 24)));
            row.addView(check);
        }

        TextView label = new TextView(context);
        label.setText(opt.label);
        label.setTextSize(15);
        label.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(label);

        // 右侧图标槽：排序方向箭头等（20dp）；无则占位保持右对齐
        if (anyRightIcon) {
            View right;
            if (opt.rightIconRes != 0) {
                ImageView icon = new ImageView(context);
                icon.setImageResource(opt.rightIconRes);
                icon.setColorFilter(ContextCompat.getColor(context, R.color.text_secondary));
                icon.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 20), dp(context, 20)));
                right = icon;
            } else {
                right = new View(context);
                right.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 20), dp(context, 20)));
            }
            row.addView(right);
        }

        row.setOnClickListener(v -> {
            popup.dismiss();
            if (opt.action != null) {
                opt.action.run();
            }
        });
        return row;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
