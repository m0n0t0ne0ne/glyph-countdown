package com.glyphcountdown.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * "Isometric Pad" widgets (2.3): the time drawn as a hairline wireframe solid on an isometric
 * drafting pad. The extrusion depth is the time left, so the digits flatten into the page as the
 * timer runs; on the 2×2 a red dimension line (Z) measures it and a ruled table gives LEFT / SET.
 *
 * Geometry is in design units: the 2×2 is 170 square, the 2×1 170 × 80, the 1×1 80 round, scaled
 * by k to the real widget. Lettering is seven-segment, drawn as lines, so it stays crisp at any size.
 * Direction contract: .impeccable/surfaces/app-src-com-glyphcountdown-app-widgetrenderer-java.md
 */
final class IsoPad {
    private IsoPad() {}

    /** Drafting colours for one theme, optionally retinted from the picked widget colour. */
    static final class Ink {
        int pad, ink, grid, back, acc, sub, alpha = 0xFF;
    }

    static Ink ink(boolean dark, int colour, boolean translucent, TimerState st) {
        Ink c = new Ink();
        if (colour == 0) {
            if (dark) { c.pad = 0xFF14191B; c.ink = 0xFFE9EEF0; c.grid = 0xFF24434D; c.back = 0xFF4F8797; c.acc = 0xFFFF7A52; c.sub = 0xFF8E9AA0; }
            else { c.pad = 0xFFEEF2F3; c.ink = 0xFF1F2427; c.grid = 0xFFA9D3E0; c.back = 0xFF7FB3C4; c.acc = 0xFFD64B2A; c.sub = 0xFF5A6468; }
        } else {
            float[] hsl = WidgetColours.hsl(colour);
            float h = hsl[0], s = Math.max(.45f, Math.min(.75f, hsl[1] + .15f));
            if (dark) {
                c.pad = WidgetColours.fromHsl(h, .15f, .09f); c.ink = 0xFFE9EEF0; c.grid = WidgetColours.fromHsl(h, .35f, .24f);
                c.back = WidgetColours.fromHsl(h, s, .48f); c.acc = WidgetColours.fromHsl(h, s, .66f); c.sub = 0xFF8E9AA0;
            } else {
                c.pad = WidgetColours.fromHsl(h, .18f, .95f); c.ink = 0xFF1F2427; c.grid = WidgetColours.fromHsl(h, .45f, .80f);
                c.back = WidgetColours.fromHsl(h, s, .62f); c.acc = WidgetColours.fromHsl(h, s, .40f); c.sub = 0xFF5A6468;
            }
        }
        if (st.mode == TimerState.PAUSE) { c.ink = c.sub; c.acc = c.sub; c.back = dark ? 0xFF3A4448 : 0xFFB9C2C6; }
        if (st.mode == TimerState.DONE) {
            int red = dark ? 0xFFFF5A5A : 0xFFD62E2E;
            c.ink = red; c.acc = red; c.back = dark ? 0xFF7A2A2A : 0xFFF0A3A3;
            c.alpha = st.blink ? 0xFF : 0x48;   // pulses with the alarm
        }
        if (translucent) c.pad = (c.pad & 0x00FFFFFF) | 0xA0000000;
        return c;
    }

    /** Fraction of the time left: full while setting, empty at time's up. */
    static float frac(TimerState st) {
        if (st.mode == TimerState.SET) return 1f;
        if (st.mode == TimerState.DONE || st.totalMs <= 0) return 0f;
        return Math.max(0f, Math.min(1f, st.remainingNow() / (float) st.totalMs));
    }

    static void draw(Context ctx, Canvas cv, int kind, int w, int h, TimerState st, Ink c) {
        long secs = st.displaySeconds();
        long hh = secs / 3600, mm = (secs % 3600) / 60, ss = secs % 60;
        String time = hh > 0 ? hh + ":" + two(mm) : mm + ":" + two(ss);
        long set = st.mode == TimerState.SET ? st.setSeconds() : st.totalMs / 1000;
        float f = frac(st);
        Paint mono = new Paint(Paint.ANTI_ALIAS_FLAG);
        mono.setTypeface(Fonts.mono(ctx));

        if (kind == WidgetRenderer.K4) {
            float k = Math.min(w, h) / 170f, ox = (w - 170 * k) / 2, oy = (h - 170 * k) / 2;
            pad(cv, w, h, 22 * Math.min(w, h) / 170f, c, k, false);
            float maxD = 18, s = fit(time, 24, 150 - 20 - maxD * .866f - 10);
            float d = maxD * f, x0 = 20, y0 = 46;
            wire(cv, time, ox + x0 * k, oy + y0 * k, s * k, d * k, 2.6f * k, c);
            float dx = ox + (x0 + width(time, s) + 5) * k;
            if (d > .5f) dimension(cv, dx, oy + y0 * k, dx + d * .866f * k, oy + (y0 - d * .5f) * k, k, c, mono);
            // ruled table: LEFT and SET, right-aligned like a running-time list
            text(cv, mono, "LEFT", ox + 20 * k, oy + 124 * k, 8 * k, c.ink, c.alpha, false);
            text(cv, mono, time, ox + 150 * k, oy + 124 * k, 8 * k, c.ink, c.alpha, true);
            rule(cv, ox + 20 * k, ox + 150 * k, oy + 128 * k, k, c);
            text(cv, mono, "SET", ox + 20 * k, oy + 140 * k, 8 * k, c.sub, c.alpha, false);
            text(cv, mono, fmt(set), ox + 150 * k, oy + 140 * k, 8 * k, c.sub, c.alpha, true);
            rule(cv, ox + 20 * k, ox + 150 * k, oy + 144 * k, k, c);
            text(cv, mono, "Z = TIME LEFT", ox + 20 * k, oy + 160 * k, 7 * k, c.sub, c.alpha, false);
        } else if (kind == WidgetRenderer.K2) {
            float k = Math.min(w / 170f, h / 80f), ox = (w - 170 * k) / 2, oy = (h - 80 * k) / 2;
            pad(cv, w, h, 18 * Math.min(w / 170f, h / 80f), c, k, false);
            float maxD = 12, s = fit(time, 18, 110 - 14 - maxD * .866f);
            wire(cv, time, ox + 14 * k, oy + 20 * k, s * k, maxD * f * k, 2.2f * k, c);
            text(cv, mono, "SET", ox + 156 * k, oy + 34 * k, 7 * k, c.sub, c.alpha, true);
            text(cv, mono, fmt(set), ox + 156 * k, oy + 48 * k, 8 * k, c.sub, c.alpha, true);
        } else {
            float size = Math.min(w, h), k = size / 80f, ox = (w - size) / 2, oy = (h - size) / 2;
            pad(cv, w, h, size / 2, c, k, true);
            // one number: hours, else minutes, else seconds in the last minute
            String one = hh > 0 ? String.valueOf(hh) : mm > 0 ? String.valueOf(mm) : String.valueOf(ss);
            float s = one.length() == 1 ? 18 : 12, d = 12 * f;
            float x = 40 - (width(one, s) + d * .866f) / 2;
            wire(cv, one, ox + x * k, oy + (22 + d * .25f) * k, s * k, d * k, 2.4f * k, c);
            String unit = hh > 0 ? "HR" : mm > 0 ? "MIN" : "SEC";
            mono.setTextSize(6.5f * k);
            mono.setTextAlign(Paint.Align.CENTER);
            mono.setColor(withAlpha(c.sub, c.alpha));
            cv.drawText(unit, ox + 40 * k, oy + 70 * k, mono);
        }
    }

    // ---- the pad: card, isometric grid ----------------------------------------------------

    private static void pad(Canvas cv, int w, int h, float r, Ink c, float k, boolean round) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(c.pad);
        Path clip = new Path();
        if (round) clip.addCircle(w / 2f, h / 2f, Math.min(w, h) / 2f, Path.Direction.CW);
        else clip.addRoundRect(new RectF(0, 0, w, h), r, r, Path.Direction.CW);
        cv.drawPath(clip, p);
        cv.save();
        cv.clipPath(clip);
        p.setColor(c.grid);
        p.setAlpha(150);
        p.setStrokeWidth(Math.max(1f, .5f * k));
        float step = 10 * k, tan = 1.732f;
        for (float x = -h * tan; x < w + h * tan; x += step) {
            cv.drawLine(x, 0, x + h * tan, h, p);
            cv.drawLine(x, 0, x - h * tan, h, p);
        }
        cv.restore();
    }

    // ---- seven-segment lettering drawn as an extruded wireframe ----------------------------

    private static final float[][] SEG = {
            {0, 0, 1, 0}, {1, 0, 1, 1}, {1, 1, 1, 2}, {0, 2, 1, 2}, {0, 1, 0, 2}, {0, 0, 0, 1}, {0, 1, 1, 1}}; // a..g
    private static final String[] DIG = {"abcdef", "bc", "abged", "abgcd", "fgbc", "afgcd", "afgedc", "abc", "abcdefg", "abcdfg"};

    static float width(String t, float s) {
        float w = 0;
        for (char ch : t.toCharArray()) w += ch == ':' ? .55f * s : 1.45f * s;
        return w - .45f * s;
    }

    private static float fit(String t, float s, float room) {
        return Math.min(s, s * room / width(t, s));
    }

    /** Front edges heavy in ink, back edges and the connectors between them in pale blue. */
    private static void wire(Canvas cv, String t, float x0, float y0, float s, float d, float fw, Ink c) {
        float dx = d * .866f, dy = -d * .5f;
        Paint back = stroke(withAlpha(c.back, c.alpha), Math.max(1f, fw * .35f));
        Paint conn = stroke(withAlpha(c.back, c.alpha), Math.max(1f, fw * .23f));
        Paint front = stroke(withAlpha(c.ink, c.alpha), fw);
        front.setStrokeCap(Paint.Cap.SQUARE);
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(withAlpha(c.ink, c.alpha));
        for (int pass = 0; pass < 3; pass++) {
            float x = x0;
            for (char ch : t.toCharArray()) {
                if (ch == ':') {
                    if (pass == 2) {
                        cv.drawCircle(x + .15f * s, y0 + .6f * s, fw * .8f, dot);
                        cv.drawCircle(x + .15f * s, y0 + 1.4f * s, fw * .8f, dot);
                    }
                    x += .55f * s;
                    continue;
                }
                for (char sg : DIG[ch - '0'].toCharArray()) {
                    float[] q = SEG[sg - 'a'];
                    float ax = x + q[0] * s, ay = y0 + q[1] * s, bx = x + q[2] * s, by = y0 + q[3] * s;
                    if (pass == 0 && d > .3f) cv.drawLine(ax + dx, ay + dy, bx + dx, by + dy, back);
                    if (pass == 1 && d > .3f) { cv.drawLine(ax, ay, ax + dx, ay + dy, conn); cv.drawLine(bx, by, bx + dx, by + dy, conn); }
                    if (pass == 2) cv.drawLine(ax, ay, bx, by, front);
                }
                x += 1.45f * s;
            }
        }
    }

    /** A drafting dimension: line, end ticks, and the label Z. */
    private static void dimension(Canvas cv, float x1, float y1, float x2, float y2, float k, Ink c, Paint mono) {
        Paint p = stroke(withAlpha(c.acc, c.alpha), Math.max(1f, .8f * k));
        double a = Math.atan2(y2 - y1, x2 - x1);
        float nx = (float) -Math.sin(a) * 3 * k, ny = (float) Math.cos(a) * 3 * k;
        cv.drawLine(x1, y1, x2, y2, p);
        cv.drawLine(x1 - nx, y1 - ny, x1 + nx, y1 + ny, p);
        cv.drawLine(x2 - nx, y2 - ny, x2 + nx, y2 + ny, p);
        text(cv, mono, "Z", (x1 + x2) / 2 + 4 * k, (y1 + y2) / 2 - 3 * k, 6.5f * k, c.acc, c.alpha, false);
    }

    private static void rule(Canvas cv, float x1, float x2, float y, float k, Ink c) {
        cv.drawLine(x1, y, x2, y, stroke(withAlpha(c.ink, c.alpha), Math.max(1f, .5f * k)));
    }

    private static void text(Canvas cv, Paint p, String s, float x, float y, float size, int col, int alpha, boolean right) {
        p.setTextSize(size);
        p.setColor(withAlpha(col, alpha));
        p.setTextAlign(right ? Paint.Align.RIGHT : Paint.Align.LEFT);
        cv.drawText(s, x, y, p);
    }

    private static Paint stroke(int col, float wd) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(col);
        p.setStrokeWidth(wd);
        return p;
    }

    private static int withAlpha(int color, int alpha) {
        if (alpha >= 0xFF) return color;
        return (((color >>> 24) * alpha / 0xFF) << 24) | (color & 0xFFFFFF);
    }

    private static String two(long n) { return n < 10 ? "0" + n : String.valueOf(n); }

    static String fmt(long sec) {
        long h = sec / 3600, m = (sec % 3600) / 60, s = sec % 60;
        return h > 0 ? h + ":" + two(m) + ":" + two(s) : m + ":" + two(s);
    }
}
