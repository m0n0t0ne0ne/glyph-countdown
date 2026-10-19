package com.glyphcountdown.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * The dial: 60 tick marks round the edge, a solid dot riding just inside them that you drag
 * to set the selected unit, and the time in the middle. Tap a digit pair to choose which unit
 * the dot edits.
 */
public class DialView extends View {
    public interface Listener {
        void onValueChanged(int unit, int value);
        void onUnitPicked(int unit);
    }

    // from the current Palette (set in the constructor); WHITE is the foreground role
    private int WHITE, GREY, UNLIT, RED_TEXT, COLON;
    private static final int RED = 0xFFE8202A;

    private final TimerState st = TimerState.get();
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint digits = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint caption = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF[] pairRects = {new RectF(), new RectF(), new RectF()};
    private final float dp;
    private final int touchSlop;

    private Listener listener;
    private int unit = TimerState.UNIT_M;
    private boolean dragging;
    private int pendingTap = -1;
    private float downX, downY;

    public DialView(Context c) {
        super(c);
        Palette pal = Palette.of(c);
        WHITE = pal.fg; GREY = pal.grey; UNLIT = pal.unlit; RED_TEXT = pal.redText; COLON = pal.dim;
        dp = getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(c).getScaledTouchSlop();
        tick.setStrokeCap(Paint.Cap.ROUND);
        digits.setTypeface(Fonts.doto(c));
        digits.setTextAlign(Paint.Align.LEFT);
        caption.setTypeface(Fonts.mono(c));
        caption.setTextAlign(Paint.Align.CENTER);
        caption.setLetterSpacing(0.18f);
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    public void setListener(Listener l) { listener = l; }

    public void setUnit(int u) { unit = u; invalidate(); }

    public int getUnit() { return unit; }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        setMeasuredDimension(w, w); // always square
    }

    @Override
    protected void onDraw(Canvas c) {
        float size = getWidth();
        float k = size / 320f; // the mockup was drawn at 320 px
        float cx = size / 2f, cy = size / 2f, R = size / 2f;
        int mode = st.mode;
        boolean setting = mode == TimerState.SET;

        // ---- ticks
        int max = TimerState.maxFor(unit);
        int val = st.get(unit);
        double frac;
        if (setting) frac = (double) val / max;
        else if (mode == TimerState.DONE) frac = 1;
        else frac = st.totalMs > 0 ? (double) st.remainingNow() / st.totalMs : 0;
        int lit = setting ? (int) Math.round(frac * 60) : (int) Math.ceil(frac * 60);
        int litColor = mode == TimerState.DONE ? RED_TEXT : mode == TimerState.PAUSE ? GREY : WHITE;

        tick.setStrokeWidth(2 * k);
        float outer = R - 6 * k;
        for (int i = 0; i < 60; i++) {
            float len = (i % 5 == 0 ? 18 : 12) * k;
            double a = Math.toRadians(i * 6);
            float sx = (float) Math.sin(a), sy = (float) -Math.cos(a);
            tick.setColor(i < lit ? litColor : UNLIT);
            c.drawLine(cx + sx * outer, cy + sy * outer, cx + sx * (outer - len), cy + sy * (outer - len), tick);
        }

        // ---- dot (only while setting)
        if (setting) {
            double a = (double) val / max * 2 * Math.PI;
            float track = 116 * k;
            float dr = (dragging ? 12 : 9) * k;
            dot.setColor(dragging ? RED : WHITE);
            c.drawCircle(cx + (float) Math.sin(a) * track, cy - (float) Math.cos(a) * track, dr, dot);
        }

        // ---- time: HH : MM : SS
        long secs = st.displaySeconds();
        String[] parts = {two(secs / 3600), two((secs % 3600) / 60), two(secs % 60)};
        digits.setTextSize(42 * k);
        float pairW = digits.measureText("00");
        float colonW = digits.measureText(":");
        float gap = 4 * k;
        float total = pairW * 3 + colonW * 2 + gap * 4;
        float x = cx - total / 2;
        Paint.FontMetrics fm = digits.getFontMetrics();
        float baseline = cy - 4 * k - (fm.ascent + fm.descent) / 2;
        float hitHalf = Math.max(22 * dp, (fm.descent - fm.ascent) / 2 + 6 * k);
        for (int i = 0; i < 3; i++) {
            int color;
            if (mode == TimerState.DONE) color = RED_TEXT;
            else if (!setting) color = WHITE;
            else color = i == unit ? WHITE : GREY;
            digits.setColor(color);
            c.drawText(parts[i], x, baseline, digits);
            pairRects[i].set(x - gap, cy - 4 * k - hitHalf, x + pairW + gap, cy - 4 * k + hitHalf);
            x += pairW + gap;
            if (i < 2) {
                digits.setColor(COLON);
                c.drawText(":", x, baseline, digits);
                x += colonW + gap;
            }
        }

        // ---- caption
        String cap;
        if (setting) cap = "DRAG TO SET " + (unit == 0 ? "HOURS" : unit == 1 ? "MINUTES" : "SECONDS");
        else if (mode == TimerState.RUN) cap = "REMAINING";
        else if (mode == TimerState.PAUSE) cap = "PAUSED";
        else cap = "TIME'S UP";
        caption.setTextSize(11 * k);
        caption.setColor(mode == TimerState.DONE ? RED_TEXT : GREY);
        c.drawText(cap, cx, cy + 40 * k, caption);

        setContentDescription(describe(secs));
    }

    // ---- touch -------------------------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = x; downY = y;
                pendingTap = -1;
                if (st.mode == TimerState.SET) {
                    for (int i = 0; i < 3; i++) if (pairRects[i].contains(x, y)) pendingTap = i;
                }
                if (pendingTap < 0) {
                    if (st.mode != TimerState.SET) return false;
                    float dx = x - getWidth() / 2f, dy = y - getHeight() / 2f;
                    if (Math.hypot(dx, dy) < getWidth() * 0.12) return false;
                    dragging = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    applyTouch(x, y);
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (pendingTap >= 0 && Math.hypot(x - downX, y - downY) > touchSlop) {
                    // a drag that started on the digits still moves the dot
                    pendingTap = -1;
                    dragging = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (dragging) applyTouch(x, y);
                return true;
            case MotionEvent.ACTION_UP:
                if (pendingTap >= 0) {
                    performClick();
                    if (listener != null) listener.onUnitPicked(pendingTap);
                }
                // fall through
            case MotionEvent.ACTION_CANCEL:
                pendingTap = -1;
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

    // ---- accessibility: swipe up/down with TalkBack to change the selected unit --------

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.SeekBar");
        if (st.mode == TimerState.SET) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        }
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle args) {
        if (st.mode == TimerState.SET && listener != null
                && (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
            int max = TimerState.maxFor(unit);
            int step = action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? 1 : max - 1;
            listener.onValueChanged(unit, (st.get(unit) + step) % max);
            return true;
        }
        return super.performAccessibilityAction(action, args);
    }

    private String describe(long secs) {
        String t = (secs / 3600) + " hours " + ((secs % 3600) / 60) + " minutes " + (secs % 60) + " seconds";
        if (st.mode == TimerState.SET) {
            return "Timer dial, " + t + ". Editing " + (unit == 0 ? "hours" : unit == 1 ? "minutes" : "seconds");
        }
        return t + " remaining";
    }

    private static String two(long n) { return n < 10 ? "0" + n : String.valueOf(n); }
}
