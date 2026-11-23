package com.themoon.y1.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.themoon.y1.ThemeManager;

/**
 * Drawn list marks ported from Classy: the thin disclosure chevron of a setting row and the tick
 * column of single-choice lists. Both take their size from the capital letters of the row text.
 */
public final class ListMarks {
    private ListMarks() {}

    // ---- Disclosure chevron (Classy DisclosureChevron) ----
    private static final float HEIGHT_RATIO = 0.9f;
    private static final float WIDTH_RATIO = 0.55f;
    private static final float STROKE_RATIO = 0.13f;
    private static final float GAP_RATIO = 0.5f;

    /** True if a row value ends with the old text chevron ("Edit 〉", "〉 "). */
    public static boolean hasChevron(String value) {
        String t = value.trim();
        return !t.isEmpty() && (t.charAt(t.length() - 1) == '〉' || t.charAt(t.length() - 1) == '›');
    }

    /** The value without its trailing text chevron. */
    public static String stripChevron(String value) {
        String t = value.trim();
        return t.substring(0, t.length() - 1).trim();
    }

    private static float capHeight(TextView title, Rect bounds) {
        title.getPaint().getTextBounds("H", 0, 1, bounds);
        return bounds.height();
    }

    /** The chevron at the right of a row: grey normally, the selected text colour while the row has focus. */
    public static class ChevronView extends View {
        private final TextView title;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final Rect bounds = new Rect();

        public ChevronView(Context context, TextView title) {
            super(context);
            this.title = title;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            setDuplicateParentStateEnabled(true);
            setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }

        @Override
        protected void onMeasure(int w, int h) {
            float cap = capHeight(title, bounds);
            setMeasuredDimension((int) Math.ceil(cap * GAP_RATIO + cap * WIDTH_RATIO),
                    getDefaultSize(getSuggestedMinimumHeight(), h));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cap = capHeight(title, bounds);
            if (cap <= 0f) return;
            float stroke = Math.max(1f, cap * STROKE_RATIO);
            float half = stroke / 2;
            float w = cap * WIDTH_RATIO;
            float h = cap * HEIGHT_RATIO;
            paint.setStrokeWidth(stroke);
            path.reset();
            path.moveTo(half, half);
            path.lineTo(w - half, h / 2);
            path.lineTo(half, h - half);
            boolean selected = getParent() instanceof View && ((View) getParent()).isFocused();
            paint.setColor(selected ? ThemeManager.getListButtonFocusedTextColor()
                    : ThemeManager.getTextColorSecondary());
            // Centred on the cap band of the title (same row)
            float baseline = title.getTop() + title.getBaseline() - getTop();
            canvas.save();
            canvas.translate(getWidth() - w, Math.round(baseline - cap + (cap - h) / 2));
            canvas.drawPath(path, paint);
            canvas.restore();
        }
    }


    /**
     * Drop-in replacement for the text arrow (a TextView): same API (visibility, text colour, text size),
     * but it draws the thin chevron instead of the "〉" glyph.
     */
    public static class ChevronText extends TextView {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        public ChevronText(Context context) {
            super(context);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            setText("〉"); // keeps measuring and callers' setText harmless
        }

        private float cap() { return getTextSize() * 0.72f; }

        @Override
        protected void onMeasure(int w, int h) {
            super.onMeasure(w, h);
            float cap = cap();
            setMeasuredDimension((int) Math.ceil(cap * WIDTH_RATIO + 2), getMeasuredHeight());
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cap = cap();
            float stroke = Math.max(1f, cap * STROKE_RATIO * 1.4f);
            float half = stroke / 2, w = cap * WIDTH_RATIO, h = cap * HEIGHT_RATIO;
            paint.setStrokeWidth(stroke);
            paint.setColor(getCurrentTextColor());
            path.reset();
            path.moveTo(half, half);
            path.lineTo(w - half, h / 2);
            path.lineTo(half, h - half);
            canvas.save();
            canvas.translate(getWidth() - w - 1, (getHeight() - h) / 2f);
            canvas.drawPath(path, paint);
            canvas.restore();
        }
    }

    // ---- Tick (Classy CheckMark) ----
    private static final float TICK_STROKE_RATIO = 0.19f;
    private static final float TICK_COLUMN_DP = 28f;

    /**
     * A tick in a fixed-width column, as a left compound drawable. With chosen=false it draws nothing
     * but keeps the column, so the texts of all rows start at the same x.
     */
    public static class TickDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final int width, height;
        private final boolean chosen;
        private final int normalColor;
        private boolean focused;

        TickDrawable(Context c, float textSizePx, int color, boolean chosen) {
            this.chosen = chosen;
            width = Math.round(TICK_COLUMN_DP * c.getResources().getDisplayMetrics().density);
            height = Math.round(textSizePx * 0.72f); // about the cap height of the row text
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            normalColor = color;
            setBounds(0, 0, width, height);
        }

        @Override public int getIntrinsicWidth() { return width; }
        @Override public int getIntrinsicHeight() { return height; }

        @Override
        public void draw(Canvas canvas) {
            if (!chosen) return;
            float cap = height;
            float stroke = cap * TICK_STROKE_RATIO;
            float h = cap - stroke;
            float o = stroke / 2;
            paint.setStrokeWidth(stroke);
            paint.setColor(focused ? ThemeManager.getListButtonFocusedTextColor() : normalColor);
            path.reset();
            path.moveTo(o, o + 0.55f * h);
            path.lineTo(o + 0.42f * h, o + h);
            path.lineTo(o + 1.25f * h, o);
            canvas.save();
            canvas.translate(getBounds().left, getBounds().top);
            canvas.drawPath(path, paint);
            canvas.restore();
        }

        @Override public boolean isStateful() { return true; }

        @Override
        protected boolean onStateChange(int[] state) {
            boolean f = false;
            for (int s : state) if (s == android.R.attr.state_focused) f = true;
            boolean changed = f != focused;
            focused = f;
            return changed;
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** Puts the tick column left of a list button's text; the tick shows only when chosen. */
    public static void markChosen(android.widget.TextView btn, boolean chosen) {
        int color = ThemeManager.getTextColorPrimary();
        btn.setCompoundDrawables(new TickDrawable(btn.getContext(), btn.getTextSize(), color, chosen),
                null, null, null);
    }
}
