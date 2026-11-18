package com.liveimager.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

/**
 * Ozone-style band display. Drag a vertical line to move a crossover,
 * drag up/down inside a band to set its width. Shaded bars show measured stereo width.
 */
final class BandView extends View {
    interface Listener { void onChanged(); }

    static final int[] COLORS = {0xFFE0564F, 0xFFE8B04A, 0xFF4FC3A1, 0xFF5B8DEF};
    private final StereoImager im = Engine.get().imager;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;
    private Listener listener;
    private int dragX = -1, dragBand = -1;

    BandView(Context c) {
        super(c);
        d = getResources().getDisplayMetrics().density;
        line.setStrokeWidth(3 * d);
        line.setStrokeCap(Paint.Cap.ROUND);
        text.setTextSize(11 * d);
        grid.setColor(0xFF2A3342);
        grid.setStrokeWidth(d);
    }

    void setListener(Listener l) { listener = l; }

    private float fx(float f) { return (float) (Math.log(f / 20.0) / Math.log(1000.0)) * getWidth(); }

    private float xf(float x) {
        double t = Math.max(0, Math.min(1, x / getWidth()));
        return (float) (20.0 * Math.pow(1000.0, t));
    }

    private float top() { return 20 * d; }
    private float bottom() { return getHeight() - 18 * d; }

    static String hz(float f) {
        return f < 1000 ? String.format(Locale.US, "%d Hz", Math.round(f))
                : String.format(Locale.US, "%.1f kHz", f / 1000f);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth();
        float t = top(), b = bottom(), ph = b - t, mid = (t + b) / 2f;
        c.drawColor(0xFF0B0E14);
        c.drawLine(0, mid, w, mid, grid);

        for (int i = 0; i < StereoImager.BANDS; i++) {
            float l = i == 0 ? 0 : fx(im.xover[i - 1]);
            float r = i == 3 ? w : fx(im.xover[i]);
            int col = COLORS[i];
            float mw = Math.min(1f, im.meterWidth[i]);
            fill.setColor(col);
            fill.setAlpha(55);
            c.drawRect(l, b - mw * ph, r, b, fill);
            float y = mid - im.width[i] * ph / 2f;
            line.setColor(col);
            c.drawLine(l + 6 * d, y, r - 6 * d, y, line);
            String s = String.format(Locale.US, "%+d", Math.round(im.width[i] * 100));
            text.setColor(col);
            c.drawText(s, (l + r) / 2f - text.measureText(s) / 2f, t - 6 * d, text);
        }
        text.setColor(0xFF8B95A5);
        for (int i = 0; i < 3; i++) {
            float x = fx(im.xover[i]);
            grid.setColor(0xFF8B95A5);
            c.drawLine(x, t, x, b, grid);
            grid.setColor(0xFF2A3342);
            String s = hz(im.xover[i]);
            float tx = Math.max(0, Math.min(w - text.measureText(s), x - text.measureText(s) / 2f));
            c.drawText(s, tx, getHeight() - 4 * d, text);
        }
    }

    private int bandAt(float x) {
        for (int i = 0; i < 3; i++) if (x < fx(im.xover[i])) return i;
        return 3;
    }

    private void apply(float x, float y) {
        if (dragX >= 0) {
            im.setCrossover(dragX, xf(x));
        } else if (dragBand >= 0) {
            float half = (bottom() - top()) / 2f;
            float v = ((top() + bottom()) / 2f - y) / half;
            v = Math.max(-1f, Math.min(1f, v));
            if (Math.abs(v) < 0.05f) v = 0;
            im.width[dragBand] = v;
            im.changed();
        }
        if (listener != null) listener.onChanged();
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                dragX = -1;
                dragBand = -1;
                float best = 24 * d;
                for (int i = 0; i < 3; i++) {
                    float dx = Math.abs(x - fx(im.xover[i]));
                    if (dx < best) { best = dx; dragX = i; }
                }
                if (dragX < 0) dragBand = bandAt(x);
                apply(x, y);
                return true;
            case MotionEvent.ACTION_MOVE:
                apply(x, y);
                return true;
            case MotionEvent.ACTION_UP:
                performClick();
                dragX = -1;
                dragBand = -1;
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragX = -1;
                dragBand = -1;
                return true;
        }
        return super.onTouchEvent(e);
    }

    @Override public boolean performClick() { return super.performClick(); }
}
