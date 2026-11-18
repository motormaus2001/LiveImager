package com.liveimager.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.Locale;

/** Vectorscope: "Polar Sample" (half circle) or "Lissajous". Tap to switch. Includes a correlation meter. */
final class ScopeView extends View {
    private static final int POINTS = 2048;
    private final Engine eng = Engine.get();
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] pts = new float[POINTS * 2];
    private final RectF oval = new RectF();
    private final float d;
    private float gain = 1f;
    private boolean polar = true;

    ScopeView(Context c) {
        super(c);
        d = getResources().getDisplayMetrics().density;
        dot.setColor(0xFF7FD4FF);
        dot.setAlpha(120);
        dot.setStrokeWidth(2f * d);
        dot.setStrokeCap(Paint.Cap.ROUND);
        grid.setColor(0xFF2A3342);
        grid.setStyle(Paint.Style.STROKE);
        grid.setStrokeWidth(1f * d);
        text.setColor(0xFF8B95A5);
        text.setTextSize(11f * d);
        setOnClickListener(v -> { polar = !polar; invalidate(); });
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int h = Math.min(w, (int) (getResources().getDisplayMetrics().heightPixels * 0.42f));
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        float meterH = 30 * d;
        float ph = h - meterH;
        c.drawColor(0xFF0B0E14);

        float cx = w / 2f, cy, R;
        if (polar) {
            cy = ph - 6 * d;
            R = Math.min(w / 2f - 10 * d, ph - 22 * d);
            oval.set(cx - R, cy - R, cx + R, cy + R);
            c.drawArc(oval, 180, 180, true, grid);
            oval.set(cx - R / 2, cy - R / 2, cx + R / 2, cy + R / 2);
            c.drawArc(oval, 180, 180, false, grid);
            float k = R * 0.70710677f;
            c.drawLine(cx, cy, cx - k, cy - k, grid);
            c.drawLine(cx, cy, cx + k, cy - k, grid);
            c.drawLine(cx, cy, cx, cy - R, grid);
            c.drawText("L", cx - k - 12 * d, cy - k - 4 * d, text);
            c.drawText("R", cx + k + 4 * d, cy - k - 4 * d, text);
            c.drawText("M", cx - 4 * d, cy - R - 6 * d, text);
        } else {
            cy = ph / 2f;
            R = Math.min(w, ph) / 2f - 14 * d;
            c.drawCircle(cx, cy, R, grid);
            float k = R * 0.70710677f;
            c.drawLine(cx - k, cy - k, cx + k, cy + k, grid);
            c.drawLine(cx + k, cy - k, cx - k, cy + k, grid);
            c.drawLine(cx, cy - R, cx, cy + R, grid);
            c.drawLine(cx - R, cy, cx + R, cy, grid);
            c.drawText("L", cx - k - 12 * d, cy - k - 4 * d, text);
            c.drawText("R", cx + k + 4 * d, cy - k - 4 * d, text);
        }

        // Samples: x = side, y = mid (45° rotation), folded to the top half in polar mode.
        int pos = eng.scopePos;
        float maxA = 1e-6f;
        int mask = Engine.SCOPE_SIZE - 1;
        for (int i = 0; i < POINTS; i++) {
            int idx = (pos - POINTS + i) & mask;
            float L = eng.scopeL[idx], Rr = eng.scopeR[idx];
            float x = (Rr - L) * 0.70710677f, y = (L + Rr) * 0.70710677f;
            if (polar && y < 0) { x = -x; y = -y; }
            float a = Math.abs(x) > Math.abs(y) ? Math.abs(x) : Math.abs(y);
            if (a > maxA) maxA = a;
            pts[2 * i] = x;
            pts[2 * i + 1] = y;
        }
        float target = Math.max(1f, Math.min(30f, 0.85f / maxA));
        gain = target < gain ? target : gain + (target - gain) * 0.05f;   // fast attack, slow release
        for (int i = 0; i < POINTS; i++) {
            pts[2 * i] = cx + pts[2 * i] * gain * R;
            pts[2 * i + 1] = cy - pts[2 * i + 1] * gain * R;
        }
        c.drawPoints(pts, 0, POINTS * 2, dot);

        c.drawText(polar ? "Polar sample · tap to switch" : "Lissajous · tap to switch", 8 * d, 14 * d, text);

        // Correlation meter (-1 .. +1)
        float corr = eng.imager.correlation;
        float top = ph + 6 * d, bottom = h - 8 * d, left = 40 * d, right = w - 40 * d;
        bar.setColor(0xFF161B24);
        c.drawRect(left, top, right, bottom, bar);
        float zero = (left + right) / 2f;
        float x = zero + corr * (right - left) / 2f;
        bar.setColor(corr < 0 ? 0xFFE0564F : 0xFF4FC3A1);
        c.drawRect(Math.min(zero, x), top, Math.max(zero, x), bottom, bar);
        bar.setColor(0xFF3A4558);
        c.drawRect(zero - d, top, zero + d, bottom, bar);
        c.drawText("-1", 12 * d, bottom - 3 * d, text);
        c.drawText("+1", right + 8 * d, bottom - 3 * d, text);
        String cs = String.format(Locale.US, "corr %+.2f", corr);
        c.drawText(cs, zero - text.measureText(cs) / 2f, top - 4 * d, text);
    }
}
