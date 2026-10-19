package com.glyphcountdown.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Draws a widget as one bitmap (home screen widgets cannot host custom views).
 * Button positions match the transparent tap areas in res/layout/widget_*.xml.
 */
final class WidgetRenderer {
    static final int K1 = 1, K2 = 2, K4 = 4;

    /** Colours for one appearance. Dark values were sampled from Nothing OS widgets on a Phone (3). */
    static final class Theme {
        final int card, cardTranslucent, fg, grey, unlit, ring, btnBg, btnFg, red;
        Theme(int card, int cardTranslucent, int fg, int grey, int unlit, int ring, int btnBg, int btnFg, int red) {
            this.card = card; this.cardTranslucent = cardTranslucent; this.fg = fg; this.grey = grey;
            this.unlit = unlit; this.ring = ring; this.btnBg = btnBg; this.btnFg = btnFg; this.red = red;
        }
    }

    static final Theme DARK = new Theme(0xFF181C1E, 0x8C181C1E, 0xFFEEF1F3, 0xFF8A8F93, 0xFF3A3F42,
            0xFF4A5054, 0xFFEEF1F3, 0xFF181C1E, 0xFFFF4A4A);
    static final Theme LIGHT = new Theme(0xFFF2F3F4, 0x9EF2F3F4, 0xFF181C1E, 0xFF5E6468, 0xFFCDD1D4,
            0xFFB8BDC1, 0xFF181C1E, 0xFFF2F3F4, 0xFFD71921);
    private static final int ALARM_RED = 0xFFE8202A, ON_RED = 0xFFFFFFFF;

    private WidgetRenderer() {}

    /** What every widget size shows for the current timer state. */
    static final class Look {
        double frac; int tickColor; String time, big, small, caption; int fg, capColor, btnBg, btnFg;
        int icon; // 0 play, 1 pause, 2 dismiss
    }

    static Look look(TimerState st, Theme t) {
        Look l = new Look();
        long secs = st.displaySeconds();
        long h = secs / 3600, m = (secs % 3600) / 60, s = secs % 60;
        l.time = h > 0 ? h + ":" + two(m) + ":" + two(s) : two(m) + ":" + two(s);
        // 1×1 shows one number: H:MM from an hour up, whole minutes, then seconds in the last minute
        if (h > 0) { l.big = h + ":" + two(m); l.small = "H:MM"; }
        else if (secs >= 60) { l.big = String.valueOf(m); l.small = "MIN"; }
        else { l.big = two(s); l.small = "SEC"; }
        l.fg = t.fg; l.capColor = t.grey; l.btnBg = t.btnBg; l.btnFg = t.btnFg; l.tickColor = t.fg;
        switch (st.mode) {
            case TimerState.SET:
                l.frac = 0; l.caption = "TAP TO START"; l.icon = 0; break;
            case TimerState.RUN:
                l.frac = st.totalMs > 0 ? (double) st.remainingNow() / st.totalMs : 0;
                l.caption = "REMAINING"; l.icon = 1; break;
            case TimerState.PAUSE:
                l.frac = st.totalMs > 0 ? (double) st.remainingNow() / st.totalMs : 0;
                l.tickColor = t.grey; l.caption = "PAUSED"; l.icon = 0; break;
            default: {
                // time's up: the red pulses — full strength, then faded, every half second
                int a = st.blink ? 0xFF : 0x48;
                int red = (a << 24) | (t.red & 0xFFFFFF);
                l.frac = 1; l.tickColor = red; l.time = "00:00"; l.big = "00"; l.small = "DONE";
                l.caption = "TIME'S UP"; l.fg = red; l.capColor = t.red;
                l.btnBg = (a << 24) | (ALARM_RED & 0xFFFFFF); l.btnFg = ON_RED; l.icon = 2;
            }
        }
        return l;
    }

    static Bitmap render(Context c, int kind, int w, int h, TimerState st, boolean dark, boolean translucent) {
        Theme t = dark ? DARK : LIGHT;
        Bitmap bm = Bitmap.createBitmap(Math.max(w, 1), Math.max(h, 1), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bm);
        Look l = look(st, t);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        float base = kind == K4 ? 170f : 80f;
        float k = (kind == K2 ? h : Math.min(w, h)) / base;

        // card, shaped like Nothing's widgets: 1×1 circle, 2×1 pill, 2×2 rounded square
        p.setColor(translucent ? t.cardTranslucent : t.card);
        if (kind == K1) {
            cv.drawCircle(w / 2f, h / 2f, Math.min(w, h) / 2f, p);
        } else if (kind == K2) {
            cv.drawRoundRect(new RectF(0, 0, w, h), h / 2f, h / 2f, p);
        } else {
            float radius = Math.min(w, h) * 0.19f; // same proportion as Nothing's square widgets
            cv.drawRoundRect(new RectF(0, 0, w, h), radius, radius, p);
        }

        Paint doto = new Paint(Paint.ANTI_ALIAS_FLAG);
        doto.setTypeface(Fonts.doto(c));
        doto.setTextAlign(Paint.Align.CENTER);
        Paint mono = new Paint(Paint.ANTI_ALIAS_FLAG);
        mono.setTypeface(Fonts.mono(c));
        mono.setTextAlign(Paint.Align.CENTER);
        mono.setLetterSpacing(0.15f);

        if (kind == K1) {
            float cx = w / 2f, cy = h / 2f, R = Math.min(w, h) / 2f;
            ring(cv, cx, cy, R - 5 * k, 30, l.frac, 8 * k, 6 * k, 2 * k, l.tickColor, t.unlit);
            doto.setColor(l.fg);
            fitText(doto, l.big, 24 * k, 2 * (R - 18 * k));
            drawCentered(cv, doto, l.big, cx, cy - 3 * k);
            mono.setColor(l.capColor);
            mono.setTextSize(9 * k);
            drawCentered(cv, mono, l.small, cx, cy + 14 * k);
        } else if (kind == K2) {
            float regionW = w * 0.4f;
            float cy = h / 2f;
            float rr = Math.min(regionW, h) * 0.4f;
            // sit the ring in the pill's rounded end when it fits inside the button's tap area
            float cx = (h / 2f + rr <= regionW) ? h / 2f : regionW / 2f;
            ring(cv, cx, cy, rr, 30, l.frac, 7 * k, 5 * k, 2 * k, l.tickColor, t.unlit);
            button(cv, cx, cy, 18 * k, l.btnBg, l.btnFg, l.icon, k);
            float x0 = Math.max(regionW, cx + rr + 8 * k);
            float maxW = w - x0 - 12 * k;
            doto.setTextAlign(Paint.Align.LEFT);
            doto.setColor(l.fg);
            fitText(doto, l.time, 26 * k, maxW);
            Paint.FontMetrics fm = doto.getFontMetrics();
            cv.drawText(l.time, x0, cy - 1 * k - fm.descent * 0.2f, doto);
            mono.setTextAlign(Paint.Align.LEFT);
            mono.setColor(l.capColor);
            fitText(mono, l.caption, 9 * k, maxW);
            cv.drawText(l.caption, x0, cy + 16 * k, mono);
        } else {
            float cx = w / 2f, cy = h / 2f, R = Math.min(w, h) / 2f;
            ring(cv, cx, cy, R - 6 * k, 60, l.frac, 12 * k, 8 * k, 2 * k, l.tickColor, t.unlit);
            doto.setColor(l.fg);
            fitText(doto, l.time, 30 * k, 2 * (R - 26 * k));
            drawCentered(cv, doto, l.time, cx, cy - 20 * k);
            mono.setColor(l.capColor);
            mono.setTextSize(9 * k);
            drawCentered(cv, mono, l.caption, cx, cy + 2 * k);
            // smaller buttons, raised to 70% height so they stay clear of the tick ring;
            // they sit in the 62%–80% band, columns 2 and 3 of 4 (see widget_2x2.xml)
            float by = h * 0.70f;
            float br = 16 * k;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1, k));
            p.setColor(t.ring);
            cv.drawCircle(w * 0.375f, by, br - k / 2, p);
            resetIcon(cv, w * 0.375f, by, 6 * k, t.fg, k);
            button(cv, w * 0.625f, by, br, l.btnBg, l.btnFg, l.icon, k);
        }
        return bm;
    }

    private static void ring(Canvas cv, float cx, float cy, float outer, int n, double frac,
                             float maj, float min, float stroke, int litColor, int unlitColor) {
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setStrokeCap(Paint.Cap.ROUND);
        t.setStrokeWidth(stroke);
        int lit = (int) Math.ceil(frac * n);
        int every = n / 12;
        for (int i = 0; i < n; i++) {
            float len = (i % every == 0) ? maj : min;
            double a = 2 * Math.PI * i / n;
            float sx = (float) Math.sin(a), sy = (float) -Math.cos(a);
            t.setColor(i < lit ? litColor : unlitColor);
            cv.drawLine(cx + sx * outer, cy + sy * outer, cx + sx * (outer - len), cy + sy * (outer - len), t);
        }
    }

    private static void button(Canvas cv, float cx, float cy, float r, int bg, int fg, int icon, float k) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(bg);
        cv.drawCircle(cx, cy, r, p);
        p.setColor(fg);
        float s = r / 3f;
        if (icon == 0) {
            Path tri = new Path();
            tri.moveTo(cx - s * 0.7f, cy - s);
            tri.lineTo(cx - s * 0.7f, cy + s);
            tri.lineTo(cx + s, cy);
            tri.close();
            cv.drawPath(tri, p);
        } else if (icon == 1) {
            cv.drawRoundRect(cx - s * 0.85f, cy - s, cx - s * 0.25f, cy + s, k, k, p);
            cv.drawRoundRect(cx + s * 0.25f, cy - s, cx + s * 0.85f, cy + s, k, k, p);
        } else {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.5f * k);
            p.setStrokeCap(Paint.Cap.ROUND);
            float x = s * 0.8f;
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

    private static void fitText(Paint p, String s, float size, float maxW) {
        p.setTextSize(size);
        float tw = p.measureText(s);
        if (maxW > 0 && tw > maxW) p.setTextSize(size * maxW / tw);
    }

    private static void drawCentered(Canvas cv, Paint p, String s, float cx, float cy) {
        Paint.FontMetrics fm = p.getFontMetrics();
        cv.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2, p);
    }

    private static String two(long n) { return n < 10 ? "0" + n : String.valueOf(n); }
}
