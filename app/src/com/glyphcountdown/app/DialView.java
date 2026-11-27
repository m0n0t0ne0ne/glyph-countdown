package com.glyphcountdown.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import com.nothing.ketchum.Common;

/**
 * The glyph face is the dial. A live preview of the Glyph Matrix, in the countdown style picked in
 * Settings, sits inside a ring of 30 thirds (10 pills of 30°, 3 thirds each, as on the widgets).
 * While setting, drag round the ring to set the picked unit: the ring fills clockwise from 12 to
 * the value and a handle rides on it (red while you drag). While the timer runs the ring and the
 * face drain back toward 12, the same way the matrix on the back does.
 */
public class DialView extends View {
    public interface Listener {
        void onValueChanged(int unit, int value);
        void onUnitPicked(int unit);
    }

    private static final int RED = 0xFFE8202A;
    private final int FG, BG, GREY, TRACK, RED_TEXT;

    private final TimerState st = TimerState.get();
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final int matrixSize;

    private Listener listener;
    private int unit = TimerState.UNIT_M;
    private boolean dragging;
    private boolean typing;
    private long previewSeconds = -1;   // while typing: the typed time, shown on the face

    public DialView(Context c) {
        super(c);
        Palette pal = Palette.of(c);
        FG = pal.fg; BG = pal.bg; GREY = pal.grey; RED_TEXT = pal.redText;
        TRACK = pal.dark ? 0xFF2C3236 : 0xFFD3D7DA;
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.BUTT);
        int n = MatrixRenderer.SIZE;
        try { if (Common.is25111p()) n = MatrixRenderer.SIZE_SMALL; } catch (Throwable ignored) {}
        matrixSize = n;
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    public void setListener(Listener l) { listener = l; }

    public void setUnit(int u) { unit = u; invalidate(); }

    public int getUnit() { return unit; }

    /** While typing, the face shows the typed time and the ring stays full; the dial ignores drags. */
    public void setTyping(boolean on, long seconds) {
        typing = on;
        previewSeconds = on ? seconds : -1;
        invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        setMeasuredDimension(w, w); // always square
    }

    @Override
    protected void onDraw(Canvas c) {
        float size = getWidth(), k = size / 300f, cx = size / 2f, cy = size / 2f;
        int mode = st.mode;
        boolean setting = mode == TimerState.SET;
        boolean done = mode == TimerState.DONE;
        boolean pulseOff = done && !st.blink;

        // ---- ring of thirds
        double frac;
        int lit;
        int litColor = FG;
        if (typing) {
            frac = 1; lit = 30;
        } else if (setting) {
            frac = (double) st.get(unit) / TimerState.maxFor(unit);
            lit = (int) Math.round(frac * 30);
        } else if (done) {
            frac = 1; lit = 30; litColor = RED_TEXT;
        } else {
            frac = st.totalMs > 0 ? (double) st.remainingNow() / st.totalMs : 0;
            lit = (int) Math.ceil(frac * 30);
            if (mode == TimerState.PAUSE) litColor = GREY;
        }
        float R = 141 * k;
        arc.setStrokeWidth(8 * k);
        oval.set(cx - R, cy - R, cx + R, cy + R);
        float span = (30f - 4f) / 3f;
        for (int i = 0; i < 30; i++) {
            int pill = i / 3, j = i % 3;
            float a0 = pill * 36 + 3 + j * (span + 2);
            boolean on = i < lit;
            arc.setColor(on ? (pulseOff ? (litColor & 0x00FFFFFF) | 0x5A000000 : litColor) : TRACK);
            c.drawArc(oval, a0 - 90, span, false, arc);
        }

        // ---- the face: a live preview of the Glyph Matrix in the chosen style
        float mr = 120 * k;
        fill.setColor(0xFF060606);
        c.drawCircle(cx, cy, mr, fill);
        int n = matrixSize;
        double[] v = face(n);
        float pitch = 2 * mr / n, x0 = cx - mr, y0 = cy - mr, rad = pitch * 0.36f;
        for (int r = 0; r < n; r++) {
            for (int col = 0; col < n; col++) {
                if (!MorphEngine.onMatrix(n, r, col)) continue;
                double b = v[r * n + col];
                int g = b <= 0.02 ? 0x1B : (int) Math.round(40 + 215 * Math.min(1, b));
                fill.setColor(0xFF000000 | (g << 16) | (g << 8) | g);
                c.drawCircle(x0 + (col + 0.5f) * pitch, y0 + (r + 0.5f) * pitch, rad, fill);
            }
        }

        // ---- the handle, while setting
        if (setting && !typing) {
            double a = frac * 2 * Math.PI;
            float hx = cx + (float) Math.sin(a) * R, hy = cy - (float) Math.cos(a) * R;
            float hr = (dragging ? 16 : 14) * k;
            fill.setColor(BG);
            c.drawCircle(hx, hy, hr, fill);
            fill.setColor(dragging ? RED : (st.setSeconds() <= 0 ? GREY : FG));
            c.drawCircle(hx, hy, hr - 4 * k, fill);
        }

        setContentDescription(describe());
    }

    /** One frame of the chosen countdown style, as perceived brightness 0..1 per LED. */
    private double[] face(int n) {
        String style = Prefs.style(getContext());
        long totalMs, remMs;
        boolean paused = st.mode == TimerState.PAUSE;
        if (st.mode == TimerState.DONE) {
            int[] px = MatrixRenderer.doneFrame(n, st.blink);
            return toBrightness(px);
        } else if (typing || st.mode == TimerState.SET) {
            long secs = typing ? Math.max(0, previewSeconds) : st.setSeconds();
            remMs = secs * 1000; totalMs = Math.max(1, remMs);
        } else {
            remMs = st.remainingNow(); totalMs = Math.max(1, st.totalMs);
        }
        if (CountdownStyles.RING.equals(style)) {
            return toBrightness(MatrixRenderer.runFrame(n, remMs, totalMs, paused));
        }
        CountdownStyles.Moment m = new CountdownStyles.Moment();
        m.remExact = remMs / 1000.0;
        m.frac = Math.min(1, (double) remMs / totalMs);
        m.wallSec = SystemClock.elapsedRealtime() / 1000.0;
        m.paused = paused;
        return CountdownStyles.draw(style, n, m);
    }

    private static double[] toBrightness(int[] px) {
        double[] v = new double[px.length];
        for (int i = 0; i < px.length; i++) v[i] = px[i] / 4095.0;
        return v;
    }

    // ---- touch: drag round the ring to set the picked unit -----------------------------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (st.mode != TimerState.SET || typing) return false;
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                float dx = x - getWidth() / 2f, dy = y - getHeight() / 2f;
                if (Math.hypot(dx, dy) < getWidth() * 0.15) return false;
                dragging = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                applyTouch(x, y);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging) applyTouch(x, y);
                return true;
            case MotionEvent.ACTION_UP:
                performClick();
                // fall through
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }

    @Override public boolean performClick() { return super.performClick(); }

    private void applyTouch(float x, float y) {
        float dx = x - getWidth() / 2f, dy = y - getHeight() / 2f;
        if (Math.hypot(dx, dy) < getWidth() * 0.08) return;
        double a = Math.toDegrees(Math.atan2(dx, -dy));
        if (a < 0) a += 360;
        int max = TimerState.maxFor(unit);
        int v = (int) Math.round(a / 360 * max) % max;
        if (v != st.get(unit)) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (listener != null) listener.onValueChanged(unit, v);
        }
        invalidate();
    }

    // ---- accessibility: swipe up/down with TalkBack to change the picked unit ----------

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.SeekBar");
        if (st.mode == TimerState.SET && !typing) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        }
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle args) {
        if (st.mode == TimerState.SET && !typing && listener != null
                && (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
            int max = TimerState.maxFor(unit);
            int step = action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? 1 : max - 1;
            listener.onValueChanged(unit, (st.get(unit) + step) % max);
            return true;
        }
        return super.performAccessibilityAction(action, args);
    }

    private String describe() {
        long secs = st.displaySeconds();
        String t = (secs / 3600) + " hours " + ((secs % 3600) / 60) + " minutes " + (secs % 60) + " seconds";
        if (st.mode == TimerState.SET) {
            return "Timer face, " + t + ". Drag round it to set the "
                    + (unit == 0 ? "hours" : unit == 1 ? "minutes" : "seconds");
        }
        return t + " remaining";
    }
}
