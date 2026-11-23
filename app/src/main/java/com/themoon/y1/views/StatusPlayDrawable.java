package com.themoon.y1.views;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Status-bar play and pause icons in the iPod style: a solid rounded triangle, two solid bars. */
public class StatusPlayDrawable extends Drawable {
    private final boolean pause;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();

    public StatusPlayDrawable(boolean pause) {
        this.pause = pause;
        paint.setColor(0xFFFFFFFF);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    public void draw(Canvas canvas) {
        android.graphics.Rect b = getBounds();
        float s = Math.min(b.width(), b.height());
        float x = b.left + (b.width() - s) / 2f, y = b.top + (b.height() - s) / 2f;
        if (pause) {
            float bw = s * 0.26f, h = s * 0.74f, top = y + (s - h) / 2f;
            r.set(x + s * 0.15f, top, x + s * 0.15f + bw, top + h);
            canvas.drawRoundRect(r, bw * 0.2f, bw * 0.2f, paint);
            r.offset(s * 0.7f - bw, 0);
            canvas.drawRoundRect(r, bw * 0.2f, bw * 0.2f, paint);
        } else {
            // Rounded corners: a thin stroke in the fill colour softens the points
            float h = s * 0.74f, top = y + (s - h) / 2f, left = x + s * 0.2f, right = x + s * 0.88f;
            path.reset();
            path.moveTo(left, top);
            path.lineTo(right, y + s / 2f);
            path.lineTo(left, top + h);
            path.close();
            paint.setStyle(Paint.Style.FILL_AND_STROKE);
            paint.setStrokeWidth(s * 0.08f);
            canvas.drawPath(path, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setStrokeWidth(0);
        }
    }

    @Override public void setAlpha(int a) { paint.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
