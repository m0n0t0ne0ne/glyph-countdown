package com.glyphcountdown.app;

/**
 * Draws the timer for the Glyph Matrix. LED values run 0 (off) to 4095 (full).
 *
 * Phone (3): 25 × 25 round matrix. The outer ring drains as time runs out; the middle shows
 * H:MM (an hour or more left) or MM:SS in 3 × 5 pixel digits.
 *
 * Phone (4a) Pro: 13 × 13 round matrix (137 LEDs). Too small for MM:SS, so it shows one
 * number — hours with an "h", else minutes, else seconds in the last minute — inside a
 * one-LED progress ring.
 */
public final class MatrixRenderer {
    public static final int SIZE = 25;
    public static final int SIZE_SMALL = 13;
    private static final int FULL = 4095;
    private static final int DIM = 1400;

    /** Lit columns per row of the Phone (4a) Pro matrix (from Nothing's LED allocation diagram). */
    private static final int[][] SMALL_ROWS = {
            {4, 8}, {2, 10}, {1, 11}, {1, 11}, {0, 12}, {0, 12}, {0, 12},
            {0, 12}, {0, 12}, {1, 11}, {1, 11}, {2, 10}, {4, 8}};

    private static final String[][] GLYPHS = new String[12][];
    static {
        GLYPHS[0] = new String[]{"111", "101", "101", "101", "111"};
        GLYPHS[1] = new String[]{"010", "110", "010", "010", "111"};
        GLYPHS[2] = new String[]{"111", "001", "111", "100", "111"};
        GLYPHS[3] = new String[]{"111", "001", "111", "001", "111"};
        GLYPHS[4] = new String[]{"101", "101", "111", "001", "001"};
        GLYPHS[5] = new String[]{"111", "100", "111", "001", "111"};
        GLYPHS[6] = new String[]{"111", "100", "111", "101", "111"};
        GLYPHS[7] = new String[]{"111", "001", "001", "001", "001"};
        GLYPHS[8] = new String[]{"111", "101", "111", "101", "111"};
        GLYPHS[9] = new String[]{"111", "101", "111", "001", "111"};
        GLYPHS[10] = new String[]{"0", "1", "0", "1", "0"};           // colon
        GLYPHS[11] = new String[]{"100", "100", "111", "101", "101"}; // h
    }

    private MatrixRenderer() {}

    public static int[] render(TimerState st) { return render(st, SIZE); }

    public static int[] render(TimerState st, int size) {
        return render(st.mode, st.remainingNow(), st.totalMs, st.blink, size);
    }

    /** The ring style while running (or paused) with {@code remMs} of {@code totalMs} left. */
    public static int[] runFrame(int size, long remMs, long totalMs, boolean paused) {
        return render(paused ? TimerState.PAUSE : TimerState.RUN, remMs, totalMs, true, size);
    }

    /** The default time's-up frame: full ring and 00:00, shown when {@code on}. */
    public static int[] doneFrame(int size, boolean on) {
        return render(TimerState.DONE, 0, 1, on, size);
    }

    private static int[] render(int mode, long remMs, long totalMs, boolean blink, int size) {
        int[] px = new int[size * size];
        long secs = (remMs + 999) / 1000;
        boolean small = size == SIZE_SMALL;

        boolean showDigits = mode != TimerState.DONE || blink;
        int level = mode == TimerState.PAUSE ? DIM : FULL;

        // Ring: fraction of time remaining, clockwise from 12 o'clock.
        double frac;
        if (mode == TimerState.DONE) frac = blink ? 1 : 0;
        else frac = totalMs > 0 ? (double) remMs / totalMs : 0;

        double c = (size - 1) / 2.0;
        double ringIn = small ? 30.25 : 110.25;   // squared inner radius of the ring band
        // 4a Pro, 10 h or more: "23h" fills the width, so the ring's sides make way for it
        boolean wide = small && mode != TimerState.DONE && secs >= 36000;
        for (int r = 0; r < size; r++) {
            for (int col = 0; col < size; col++) {
                if (!exists(size, r, col)) continue;
                if (wide && r >= 3 && r <= 9) continue;
                double dr = r - c, dc = col - c;
                double d2 = dr * dr + dc * dc;
                if (d2 < ringIn) continue;
                double a = Math.atan2(dc, -dr) / (2 * Math.PI);
                if (a < 0) a += 1;
                if (a < frac) px[r * size + col] = level;
            }
        }

        if (showDigits) {
            String text;
            boolean colonOn = true;
            long h = secs / 3600, m = (secs % 3600) / 60, s = secs % 60;
            if (small) {
                // one number: "2h", minutes, or seconds in the last minute
                text = h > 0 ? h + "h" : secs >= 60 ? pad(m) : pad(s);
            } else if (h > 0) {
                text = h + ":" + pad(m);
                colonOn = mode != TimerState.RUN || (secs % 2 == 0); // blink colon so you can see it ticking
            } else {
                text = pad(m) + ":" + pad(s);
            }
            int width = 0;
            for (int i = 0; i < text.length(); i++) {
                width += glyph(text.charAt(i))[0].length() + (i < text.length() - 1 ? 1 : 0);
            }
            int col = (size - width) / 2;
            int top = (size - 5) / 2;
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                String[] g = glyph(ch);
                boolean draw = ch != ':' || colonOn;
                for (int rr = 0; rr < 5 && draw; rr++) {
                    for (int cc = 0; cc < g[rr].length(); cc++) {
                        int x = col + cc, y = top + rr;
                        if (g[rr].charAt(cc) == '1' && x >= 0 && x < size) px[y * size + x] = level;
                    }
                }
                col += g[0].length() + 1;
            }
        }
        return px;
    }

    /** Whether an LED is physically present at this grid position. */
    private static boolean exists(int size, int r, int col) {
        if (size == SIZE_SMALL) return col >= SMALL_ROWS[r][0] && col <= SMALL_ROWS[r][1];
        double dr = r - 12, dc = col - 12;
        return dr * dr + dc * dc <= 156.25;
    }

    private static String[] glyph(char ch) {
        if (ch == ':') return GLYPHS[10];
        if (ch == 'h') return GLYPHS[11];
        return GLYPHS[ch - '0'];
    }

    private static String pad(long n) { return n < 10 ? "0" + n : String.valueOf(n); }
}
