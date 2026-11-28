package com.glyphcountdown.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Draws a widget as one bitmap (home screen widgets cannot host custom views).
 * Button positions match the transparent tap areas in res/layout/widget_*.xml. The 2×2 has no
 * buttons: tapping the time starts or pauses and tapping the pills opens the app. Its three rows
 * are spread so the top of the hours lines up with the top pill and the bottom of the seconds
 * with the bottom pill (2.2, "design C").
 *
 * The time is three rows of dot-matrix digits (HR / MIN / SEC; two rows on the 1×1). Progress is
 * 10 pills, each 1/10 of the time that was set, split into thirds of 1/30: lit thirds =
 * ceil(time left / time set × 30), so the last third goes out exactly at zero. Thirds empty from
 * the top pill down, left to right; empty ones stay faint. The 1×1 wraps the 30 thirds into a ring.
 * Geometry follows the chosen mockups (docs/design/widget-suite-chosen.dc.html), drawn there at
 * 1 dp = 1.5 px: the 2×2 is 255 px square, the 2×1 255 × 120, the 1×1 120 round.
 */
final class WidgetRenderer {
    static final int K1 = 1, K2 = 2, K4 = 4;

    /** Colours for one appearance. */
    static final class Theme {
        final int card, cardTranslucent, fg, sub, dim, faint, track, btn, btnFg, line, red, redDim;
        Theme(int card, int cardTranslucent, int fg, int sub, int dim, int faint, int track,
              int btn, int btnFg, int line, int red, int redDim) {
            this.card = card; this.cardTranslucent = cardTranslucent; this.fg = fg; this.sub = sub;
            this.dim = dim; this.faint = faint; this.track = track; this.btn = btn; this.btnFg = btnFg;
            this.line = line; this.red = red; this.redDim = redDim;
        }
    }

    static final Theme DARK = new Theme(0xFF181C1E, 0x8C181C1E, 0xFFEEF1F3, 0xFFAEB4B9, 0xFF8E959A,
            0xFF3E454A, 0xFF2C3236, 0xFFEEF1F3, 0xFF181C1E, 0xFF596066, 0xFFFF4A4A, 0xFF4A2326);
    static final Theme LIGHT = new Theme(0xFFF2F3F4, 0x9EF2F3F4, 0xFF181C1E, 0xFF4F555A, 0xFF5E6468,
            0xFFC4C9CD, 0xFFD9DDE0, 0xFF181C1E, 0xFFF2F3F4, 0xFF9AA0A5, 0xFFD71921, 0xFFF6CFD1);
    private static final int ALARM_RED = 0xFFE8202A, ON_RED = 0xFFFFFFFF;

    private WidgetRenderer() {}

    /** What every size shows for the current timer state. */
    static final class Look {
        long h, m, s;
        int lit;                 // thirds lit, 0..30
        int on, off;             // colours of lit and empty thirds
        int main, dim, unit, faint;
        boolean hoursZero, done;
        int btnBg, btnFg, alpha; // alpha pulses while time's up
        String label;
        int icon;                // 0 play, 1 pause, 2 dismiss
    }

    static Look look(TimerState st, Theme t) {
        Look l = new Look();
        long secs = st.displaySeconds();
        l.h = secs / 3600; l.m = (secs % 3600) / 60; l.s = secs % 60;
        l.done = st.mode == TimerState.DONE;
        l.main = t.fg; l.dim = t.dim; l.unit = t.sub; l.faint = t.faint;
        l.on = t.fg; l.off = t.track;
        l.btnBg = t.btn; l.btnFg = t.btnFg; l.alpha = 0xFF;
        switch (st.mode) {
            case TimerState.SET:
                l.lit = 30; l.label = "START"; l.icon = 0; break;
            case TimerState.RUN:
                l.lit = thirds(st); l.label = "PAUSE"; l.icon = 1; break;
            case TimerState.PAUSE:
                l.lit = thirds(st); l.on = t.sub; l.label = "RESUME"; l.icon = 0; break;
            default: {
                // time's up: red, pulsing full strength then faded every half second
                l.alpha = st.blink ? 0xFF : 0x48;
                l.lit = 0; l.off = t.redDim;
                l.main = l.dim = l.unit = t.red;
                l.btnBg = ALARM_RED; l.btnFg = ON_RED;
                l.label = "DISMISS"; l.icon = 2;
            }
        }
        l.hoursZero = l.h == 0 && !l.done;
        return l;
    }

    private static int thirds(TimerState st) {
        if (st.totalMs <= 0) return 0;
        return (int) Math.min(30, Math.ceil(st.remainingNow() * 30.0 / st.totalMs));
    }

    /**
     * Draws one widget. colour: the colour picked in Settings › Widget colour (0 = white). While
     * setting or running, lit and empty thirds take it, tuned to stay readable on this card;
     * paused stays grey and time's up stays red.
     */
    static Bitmap render(Context c, int kind, int w, int h, TimerState st, boolean dark, boolean translucent,
                         int colour) {
        Theme t = dark ? DARK : LIGHT;
        Bitmap bm = Bitmap.createBitmap(Math.max(w, 1), Math.max(h, 1), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bm);
        Look l = look(st, t);
        if (colour != 0 && (st.mode == TimerState.SET || st.mode == TimerState.RUN)) {
            l.on = WidgetColours.lit(colour, dark);
            l.off = WidgetColours.empty(colour, dark);
        }
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(translucent ? t.cardTranslucent : t.card);

        Paint doto = new Paint(Paint.ANTI_ALIAS_FLAG);
        doto.setTypeface(Fonts.doto(c));
        Paint mono = new Paint(Paint.ANTI_ALIAS_FLAG);
        mono.setTypeface(Fonts.mono(c));
        mono.setLetterSpacing(0.15f);
        Paint bold = new Paint(Paint.ANTI_ALIAS_FLAG);
        bold.setTypeface(Fonts.monoBold(c));

        if (kind == K4) {
            float k = Math.min(w, h) / 255f;
            cv.drawRoundRect(new RectF(0, 0, w, h), 48 * k, 48 * k, p);
            // no buttons: the time fills the card (tap it to start or pause), pills on the right
            float pillsX = w - 22 * k - 64 * k, top = 22 * k, bottom = top + 10 * 15 * k + 9 * 6.2f * k;
            rowsSpread(cv, l, doto, mono, 24 * k, pillsX - 10 * k, top, bottom, 72 * k, 11 * k, 8 * k);
            pills(cv, l, pillsX, top, 64 * k, 15 * k, 6.2f * k, 3 * k);
        } else if (kind == K2) {
            float k = Math.min(h / 120f, w / 255f);
            cv.drawRoundRect(new RectF(0, 0, w, h), 36 * k, 36 * k, p);
            float top = (h - 86 * k) / 2;
            rows3(cv, l, doto, mono, 22 * k, top, 28 * k, 2 * k, 10 * k, 6 * k);
            float bw = 72 * k, bh = Math.min(80 * k, h - 32 * k);
            float bx = w - 20 * k - bw;
            pills(cv, l, bx - 13 * k - 50 * k, top, 50 * k, 6 * k, 3 * k, 2 * k);
            labelButton(cv, l, bold, new RectF(bx, (h - bh) / 2, bx + bw, (h + bh) / 2), 24 * k, 10 * k, 20 * k, k, true);
        } else {
            float size = Math.min(w, h), cx = w / 2f, cy = h / 2f, u = size / 120f;
            cv.drawCircle(cx, cy, size / 2f, p);
            ringOfThirds(cv, l, cx, cy, 51 * u, 8 * u);
            // two rows: hours over minutes from an hour up, else minutes over seconds
            String[] v = l.h > 0 ? new String[]{two(l.h), two(l.m)} : new String[]{two(l.m), two(l.s)};
            String[] un = l.h > 0 ? new String[]{"HR", "MIN"} : new String[]{"MIN", "SEC"};
            int[] col = {l.main, l.dim};
            doto.setTextSize(24 * u);
            mono.setTextSize(9 * u);
            mono.setLetterSpacing(0.1f);
            float rowH = 22 * u, gap = 3 * u, block = rowH * 2 + gap + 3 * u + 10 * u;
            float y0 = cy - block / 2;
            float dw = doto.measureText("00"), uw = mono.measureText("MIN");
            float x0 = cx - (dw + 4 * u + uw) / 2;
            for (int i = 0; i < 2; i++) {
                float base = y0 + rowH * (i + 1) + gap * i - 2 * u;
                doto.setColor(withAlpha(col[i], l.alpha));
                cv.drawText(v[i], x0, base, doto);
                mono.setColor(withAlpha(l.unit, l.alpha));
                cv.drawText(un[i], x0 + dw + 4 * u, base - u, mono);
            }
            float iy = y0 + rowH * 2 + gap + 3 * u + 5 * u;
            glyphIcon(cv, l.icon, cx, iy, 5 * u, withAlpha(l.done ? l.main : l.unit, l.alpha), u);
        }
        return bm;
    }

    /** HR / MIN / SEC stacked; hours fade when there are none, seconds a touch dimmer. */
    private static void rows3(Canvas cv, Look l, Paint doto, Paint mono, float x, float y,
                              float size, float gap, float unitSize, float unitGap) {
        doto.setTextSize(size);
        mono.setTextSize(unitSize);
        mono.setLetterSpacing(0.15f);
        String[] v = {two(l.h), two(l.m), two(l.s)};
        String[] un = {"HR", "MIN", "SEC"};
        int[] col = {l.hoursZero ? l.faint : l.main, l.main, l.dim};
        int[] ucol = {l.hoursZero ? l.faint : l.unit, l.unit, l.unit};
        float rowH = size * 0.92f, dw = doto.measureText("00");
        for (int i = 0; i < 3; i++) {
            float base = y + rowH * (i + 1) + gap * i - size * 0.08f;
            doto.setColor(withAlpha(col[i], l.alpha));
            cv.drawText(v[i], x, base, doto);
            mono.setColor(withAlpha(ucol[i], l.alpha));
            cv.drawText(un[i], x + dw + unitGap, base - size * 0.06f, mono);
        }
    }

    /**
     * HR / MIN / SEC spread between top and bottom: the top of the first row's digits sits on top,
     * the bottom of the last row's on bottom, with even gaps. The size shrinks if the digits and
     * their labels would not fit before right.
     */
    private static void rowsSpread(Canvas cv, Look l, Paint doto, Paint mono, float x, float right,
                                   float top, float bottom, float size, float unitSize, float unitGap) {
        mono.setTextSize(unitSize);
        mono.setLetterSpacing(0.15f);
        float uw = mono.measureText("MIN");
        doto.setTextSize(size);
        float need = doto.measureText("00") + unitGap + uw;
        if (need > right - x) { size *= (right - x) / need; doto.setTextSize(size); }
        android.graphics.Rect b = new android.graphics.Rect();
        doto.getTextBounds("00", 0, 2, b);
        float gh = b.height(), gap = Math.max(0, (bottom - top - 3 * gh) / 2), dw = doto.measureText("00");
        String[] v = {two(l.h), two(l.m), two(l.s)};
        String[] un = {"HR", "MIN", "SEC"};
        int[] col = {l.hoursZero ? l.faint : l.main, l.main, l.dim};
        int[] ucol = {l.hoursZero ? l.faint : l.unit, l.unit, l.unit};
        for (int i = 0; i < 3; i++) {
            float base = top + i * (gh + gap) - b.top;
            doto.setColor(withAlpha(col[i], l.alpha));
            cv.drawText(v[i], x, base, doto);
            mono.setColor(withAlpha(ucol[i], l.alpha));
            cv.drawText(un[i], x + dw + unitGap, base - unitSize * 0.1f, mono);
        }
    }

    /** 10 pills top to bottom, each split into thirds; empties from the top, left to right. */
    private static void pills(Canvas cv, Look l, float x, float y, float w, float ph, float gap, float thirdGap) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path clip = new Path();
        float tw = (w - 2 * thirdGap) / 3f;
        for (int pill = 0; pill < 10; pill++) {
            float py = y + pill * (ph + gap);
            clip.reset();
            clip.addRoundRect(new RectF(x, py, x + w, py + ph), ph / 2, ph / 2, Path.Direction.CW);
            cv.save();
            cv.clipPath(clip);
            for (int j = 0; j < 3; j++) {
                int k = pill * 3 + j;
                boolean on = k >= 30 - l.lit;
                p.setColor(on ? withAlpha(l.on, l.alpha) : (l.done ? withAlpha(l.off, l.alpha) : l.off));
                float tx = x + j * (tw + thirdGap);
                cv.drawRect(tx, py, tx + tw, py + ph, p);
            }
            cv.restore();
        }
    }

    /** The 30 thirds as a ring: 10 arcs of 30°, 6° between pills, 2° between thirds; empties clockwise from 12. */
    private static void ringOfThirds(Canvas cv, Look l, float cx, float cy, float r, float stroke) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(stroke);
        p.setStrokeCap(Paint.Cap.BUTT);
        RectF oval = new RectF(cx - r, cy - r, cx + r, cy + r);
        float span = (30f - 4f) / 3f;
        for (int k = 0; k < 30; k++) {
            int pill = k / 3, j = k % 3;
            float a0 = pill * 36 + 3 + j * (span + 2);
            boolean on = k >= 30 - l.lit;
            p.setColor(on ? withAlpha(l.on, l.alpha) : (l.done ? withAlpha(l.off, l.alpha) : l.off));
            cv.drawArc(oval, a0 - 90, span, false, p); // Canvas angles start at 3 o'clock
        }
    }

    /** A filled button with an icon and START / PAUSE / RESUME / DISMISS; stacked = icon over label. */
    private static void labelButton(Canvas cv, Look l, Paint bold, RectF r, float radius,
                                    float textSize, float iconSize, float k, boolean stacked) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(withAlpha(l.btnBg, l.alpha));
        cv.drawRoundRect(r, radius, radius, p);
        bold.setTextSize(textSize);
        bold.setLetterSpacing(stacked ? 0.08f : 0.12f);
        bold.setColor(l.btnFg);
        bold.setTextAlign(Paint.Align.LEFT);
        float tw = bold.measureText(l.label);
        Paint.FontMetrics fm = bold.getFontMetrics();
        if (stacked) {
            float iy = r.centerY() - 8 * k;
            glyphIcon(cv, l.icon, r.centerX(), iy, iconSize / 2, l.btnFg, k);
            cv.drawText(l.label, r.centerX() - tw / 2, iy + iconSize / 2 + 6 * k - fm.ascent, bold);
        } else {
            float gap = 8 * k, total = iconSize + gap + tw;
            float ix = r.centerX() - total / 2 + iconSize / 2;
            glyphIcon(cv, l.icon, ix, r.centerY(), iconSize / 2, l.btnFg, k);
            cv.drawText(l.label, ix + iconSize / 2 + gap, r.centerY() - (fm.ascent + fm.descent) / 2, bold);
        }
    }

    /** Play, pause or dismiss, drawn in a box of half-size r around (cx, cy). */
    private static void glyphIcon(Canvas cv, int icon, float cx, float cy, float r, int color, float k) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        if (icon == 0) {
            Path tri = new Path();
            tri.moveTo(cx - r * 0.55f, cy - r * 0.8f);
            tri.lineTo(cx - r * 0.55f, cy + r * 0.8f);
            tri.lineTo(cx + r * 0.8f, cy);
            tri.close();
            cv.drawPath(tri, p);
        } else if (icon == 1) {
            float bw = r * 0.38f;
            cv.drawRoundRect(cx - r * 0.65f, cy - r * 0.75f, cx - r * 0.65f + bw, cy + r * 0.75f, k, k, p);
            cv.drawRoundRect(cx + r * 0.65f - bw, cy - r * 0.75f, cx + r * 0.65f, cy + r * 0.75f, k, k, p);
        } else {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1.5f, r * 0.24f));
            p.setStrokeCap(Paint.Cap.ROUND);
            float x = r * 0.62f;
            cv.drawLine(cx - x, cy - x, cx + x, cy + x, p);
            cv.drawLine(cx + x, cy - x, cx - x, cy + x, p);
        }
    }

    private static void resetIcon(Canvas cv, float cx, float cy, float r, int color, float k) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2 * k);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(color);
        cv.drawArc(new RectF(cx - r, cy - r, cx + r, cy + r), 200, 290, false, p);
        double a = Math.toRadians(200);
        float ax = cx + (float) Math.cos(a) * r, ay = cy + (float) Math.sin(a) * r;
        Path head = new Path();
        head.moveTo(ax - r * 0.05f, ay - r * 0.75f);
        head.lineTo(ax, ay);
        head.lineTo(ax + r * 0.7f, ay - r * 0.15f);
        cv.drawPath(head, p);
    }

    private static int withAlpha(int color, int alpha) {
        if (alpha >= 0xFF) return color;
        int a = ((color >>> 24) * alpha) / 0xFF;
        return (a << 24) | (color & 0xFFFFFF);
    }

    private static String two(long n) { return n < 10 ? "0" + n : String.valueOf(n); }
}
