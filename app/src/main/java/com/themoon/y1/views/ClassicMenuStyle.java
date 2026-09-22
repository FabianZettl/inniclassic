package com.themoon.y1.views;

import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.themoon.y1.ThemeManager;

/** Shared metrics matching the approved Classic main menu (480 x 360 reference). */
public final class ClassicMenuStyle {
    public static final int ROW_HEIGHT_DP = 33;
    public static final int STATUS_HEIGHT_DP = 26;
    public static final int PANE_WIDTH_DP = 242;
    private ClassicMenuStyle() {}

    public static void apply(LinearLayout row, TextView title, TextView arrow, int heightDp) {
        if (!ThemeManager.isClassicTheme()) return;
        float density = row.getResources().getDisplayMetrics().density;
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding((int) (8 * density), 0, (int) (8 * density), 0);
        row.setMinimumHeight((int) (heightDp * density));
        ViewGroup.LayoutParams params = row.getLayoutParams();
        if (params != null) {
            params.height = (int) (heightDp * density);
            if (params instanceof ViewGroup.MarginLayoutParams) {
                ((ViewGroup.MarginLayoutParams) params).setMargins(0, 0, 0, 0);
            }
            row.setLayoutParams(params);
        }
        title.setIncludeFontPadding(false);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, 21 * density);
        title.setTypeface(ThemeManager.getCustomFontBold());
        if (arrow != null) {
            arrow.setText("›");
            arrow.setIncludeFontPadding(false);
            arrow.setTextSize(TypedValue.COMPLEX_UNIT_PX, 21 * density);
            arrow.setTextColor(ThemeManager.getListButtonFocusedTextColor());
            arrow.setVisibility(row.isFocused() ? View.VISIBLE : View.GONE);
        }
    }
}
