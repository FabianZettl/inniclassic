package com.themoon.y1.views;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.themoon.y1.ThemeManager;

/**
 * iPod-style low-battery warnings (ported from Classy): a banner like a selected list row at 10 %
 * ("Battery low: 10 %", 3 s) and a full screen with an empty battery and "Please charge" at 2 %.
 * Plain views over the activity, so playback and focus are untouched; any key closes them.
 */
public class LowBatteryOverlay extends FrameLayout {
    private static final int BANNER_LEVEL = 10, SCREEN_LEVEL = 2;
    private final TextView banner;
    private final PleaseChargeView screen;
    private boolean bannerDone, screenDone;
    private final Runnable hideBanner = new Runnable() {
        @Override public void run() { banner.setVisibility(GONE); }
    };

    public LowBatteryOverlay(Activity a) {
        super(a);
        setClickable(false);
        setFocusable(false);
        float d = a.getResources().getDisplayMetrics().density;
        banner = new TextView(a);
        banner.setGravity(Gravity.CENTER);
        banner.setTextSize(TypedValue.COMPLEX_UNIT_PX, 18f * d);
        banner.setTypeface(ThemeManager.getCustomFontBold());
        banner.setVisibility(GONE);
        screen = new PleaseChargeView(a);
        screen.setVisibility(GONE);
        addView(screen, new LayoutParams(-1, -1));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, (int) (34 * d), Gravity.TOP);
        lp.topMargin = (int) (28 * d); // below the status bar
        addView(banner, lp);
        ((ViewGroup) a.findViewById(android.R.id.content)).addView(this, new LayoutParams(-1, -1));
    }

    /** Call with each battery broadcast. */
    public void onBattery(int pct, boolean charging) {
        if (charging || pct > BANNER_LEVEL) { // a new discharge may warn again
            bannerDone = screenDone = false;
            if (charging) hideAll();
            return;
        }
        if (pct <= SCREEN_LEVEL && !screenDone) {
            screenDone = bannerDone = true;
            hideBanner.run();
            screen.setVisibility(VISIBLE);
            bringToFront();
        } else if (pct > SCREEN_LEVEL && !bannerDone) {
            bannerDone = true;
            banner.setText("Battery low: " + pct + " %");
            banner.setBackgroundColor(ThemeManager.getListButtonFocusedBg());
            banner.setTextColor(ThemeManager.getListButtonFocusedTextColor());
            banner.setVisibility(VISIBLE);
            bringToFront();
            removeCallbacks(hideBanner);
            postDelayed(hideBanner, 3000);
        }
    }

    private void hideAll() {
        removeCallbacks(hideBanner);
        banner.setVisibility(GONE);
        screen.setVisibility(GONE);
    }

    /** True if a warning was showing and this key closed it (the key is then swallowed). */
    public boolean consumeKey() {
        if (banner.getVisibility() != VISIBLE && screen.getVisibility() != VISIBLE) return false;
        hideAll();
        return true;
    }

    private static class PleaseChargeView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        PleaseChargeView(Context c) { super(c); }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            canvas.drawColor(0xFF000000);
            float bw = w * 0.34f, bh = bw * 0.5f, x = (w - bw) / 2f, y = h * 0.34f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(bw * 0.05f);
            p.setColor(0xFFDDDDDD);
            r.set(x, y, x + bw, y + bh);
            canvas.drawRoundRect(r, bh * 0.15f, bh * 0.15f, p);
            p.setStyle(Paint.Style.FILL);
            r.set(x + bw, y + bh * 0.3f, x + bw + bw * 0.06f, y + bh * 0.7f);
            canvas.drawRoundRect(r, 3f, 3f, p);
            p.setColor(0xFFF44336); // an almost empty battery: one red sliver
            r.set(x + bw * 0.07f, y + bh * 0.14f, x + bw * 0.13f, y + bh * 0.86f);
            canvas.drawRect(r, p);
            p.setColor(0xFFFFFFFF);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(ThemeManager.getCustomFontBold());
            p.setTextSize(w * 0.075f);
            canvas.drawText("Please charge", w / 2f, y + bh + w * 0.14f, p);
        }
    }
}
