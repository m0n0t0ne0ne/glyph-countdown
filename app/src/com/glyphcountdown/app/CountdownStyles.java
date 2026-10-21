package com.glyphcountdown.app;

/**
 * What the Glyph Matrix shows while the timer counts down. "Ring" is the original draining ring
 * (drawn by MatrixRenderer); the other ten are drawn here, each a brightness 0..1 per LED that
 * works on the 25 × 25 Phone (3) and 13 × 13 Phone (4a) Pro matrices. Everything drains the same
 * way as the ring, back toward 12 o'clock. Plain Java, matching the canvas mockups exactly.
 */
public final class CountdownStyles {
    public static final String RING = "Ring";
    public static final String[] NAMES = {RING, "Pie", "Iris", "Tide", "Spokes", "Ticks", "Orbit", "Big",
            "Spiral", "Radar", "Rings"};
    public static final String[] NOTES = {
            "The draining ring around the time",
            "Solid pie, numbers cut out; they light up again as slices go",
            "A disc shrinking to the centre; numbers invert inside it",
            "Water level falling, with a moving wave",
            "Twelve spokes, like a clock face, going out one by one",
            "Sixty dots round the edge, like the app's dial",
            "A dot circles once a minute round the minutes arc",
            "Giant minutes, seconds sweeping the edge",
            "A spiral of dots eaten from the outside in",
            "A bright hand over a dotted field of time left",
            "Total, minute and second rings, no numbers"};

    private static final double TAU = Math.PI * 2;
    private static final String[][] DIG = new String[128][];
    static {
        DIG['0'] = new String[]{"111", "101", "101", "101", "111"};
        DIG['1'] = new String[]{"010", "110", "010", "010", "111"};
        DIG['2'] = new String[]{"111", "001", "111", "100", "111"};
        DIG['3'] = new String[]{"111", "001", "111", "001", "111"};
        DIG['4'] = new String[]{"101", "101", "111", "001", "001"};
        DIG['5'] = new String[]{"111", "100", "111", "001", "111"};
        DIG['6'] = new String[]{"111", "100", "111", "101", "111"};
        DIG['7'] = new String[]{"111", "001", "001", "001", "001"};
        DIG['8'] = new String[]{"111", "101", "111", "101", "111"};
        DIG['9'] = new String[]{"111", "101", "111", "001", "111"};
        DIG[':'] = new String[]{"0", "1", "0", "1", "0"};
        DIG[' '] = new String[]{"0", "0", "0", "0", "0"};
        DIG['h'] = new String[]{"100", "100", "111", "101", "101"};
    }

    /** The moment being drawn. */
    public static final class Moment {
        public double frac;       // time left / total, 1 → 0
        public double remExact;   // seconds left, with fractions
        public double wallSec;    // seconds of real time (for waves and blinking)
        public boolean paused;
    }

    private CountdownStyles() {}

    public static boolean known(String name) {
        for (String n : NAMES) if (n.equals(name)) return true;
        return false;
    }

    /** Brightness 0..1 per LED (0 where there is no LED). */
    public static double[] draw(String name, int n, Moment m) {
        int secs = (int) Math.ceil(m.remExact);
        double frac = m.frac, sx = m.remExact;
        boolean blink = Math.floor(m.wallSec * 2) % 2 == 0;
        boolean colon = secs < 3600 || secs % 2 == 0;
        double pulse = 0.5 + 0.5 * Math.cos(TAU * (sx % 1));

        boolean noText = "Spiral".equals(name) || "Rings".equals(name), big = "Big".equals(name);
        String txt;
        int h = secs / 3600, mi = secs % 3600 / 60, s = secs % 60;
        if (noText) txt = "";
        else if (big) txt = h > 0 ? h + "h" : secs >= 60 ? pad(mi) : pad(s);
        else txt = label(n, secs, colon);
        int scale = big && n == 25 ? 2 : 1;
        boolean[] tx = new boolean[n * n], halo = new boolean[n * n];
        if (!txt.isEmpty()) textMask(n, txt, scale, tx);
        for (int i = 0; i < n * n; i++) {
            if (!tx[i]) continue;
            int r0 = i / n, c0 = i % n;
            for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
                int k = (r0 + a) * n + c0 + b;
                if (k >= 0 && k < n * n) halo[k] = true;
            }
        }
        double[] out = new double[n * n];
        double c0 = (n - 1) / 2.0, kk = 12 / c0;
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                if (!MorphEngine.onMatrix(n, r, c)) continue;
                int i = r * n + c;
                double dy = (r - c0) * kk, dx = (c - c0) * kk, d = Math.hypot(dx, dy);
                double ang = Math.atan2(dx, -dy) / TAU;
                if (ang < 0) ang += 1;
                double v = 0;
                switch (name) {
                    case "Pie": v = ((ang < frac) ^ tx[i]) ? 1 : 0; break;
                    case "Iris": v = ((d <= 12.5 * Math.sqrt(frac)) ^ tx[i]) ? 1 : 0; break;
                    case "Tide": {
                        double level = 12.5 - 25 * frac + 0.9 * Math.sin(dx * 0.55 + m.wallSec * 3);
                        v = ((dy >= level) ^ tx[i]) ? 1 : 0;
                        break;
                    }
                    case "Spokes": {
                        if (tx[i]) { v = 1; break; }
                        if (halo[i] || d < 6.2) break;
                        int k = (int) Math.round(ang * 12) % 12;
                        double a = k / 12.0 * TAU, off = Math.abs(dx * Math.cos(a) + dy * Math.sin(a));
                        if (off > 0.75) break;
                        double left = frac * 12;
                        v = k < Math.floor(left) ? 1 : k < left ? 0.25 + 0.75 * (left - k) : 0;
                        break;
                    }
                    case "Ticks": {
                        if (tx[i]) { v = 1; break; }
                        if (d < (n == 13 ? 10.4 : 11.1)) break;
                        int k = (int) Math.floor(ang * 60);
                        double left = frac * 60;
                        if (k < Math.floor(left)) v = (r + c) % 2 == 0 || n == 13 ? 1 : 0.35;
                        else if (k == Math.floor(left) && frac < 1) v = blink ? 1 : 0.15;
                        break;
                    }
                    case "Orbit": {
                        if (tx[i]) { v = 1; break; }
                        if (d >= 11.2) { v = ang < frac ? 0.3 : 0; break; }
                        double sa = (sx % 60) / 60, mx = 8.6 * Math.sin(sa * TAU), my = -8.6 * Math.cos(sa * TAU);
                        v = Math.hypot(dx - mx, dy - my) < (n == 13 ? 1.9 : 1.6) ? 1 : 0;
                        break;
                    }
                    case "Big":
                        if (tx[i]) { v = 1; break; }
                        if (d < 11.2) break;
                        v = ang <= (sx % 60) / 60 ? 1 : 0;
                        break;
                    case "Spiral": {
                        int turns = 3;
                        double b = 12.2 / (turns * TAU), th = ang * TAU, best = 1e9, along = 0;
                        for (int k = 0; k <= turns; k++) {
                            double rr = b * (th + k * TAU), e = Math.abs(d - rr);
                            if (e < best) { best = e; along = (th + k * TAU) / (turns * TAU + TAU); }
                        }
                        if (best > (n == 13 ? 1.4 : 0.85) || along > frac) break;
                        v = frac - along < 0.03 ? 0.4 + 0.6 * pulse : 1;
                        break;
                    }
                    case "Radar": {
                        if (tx[i]) { v = 1; break; }
                        if (halo[i]) break;
                        double da = Math.abs(ang - frac);
                        da = Math.min(da, 1 - da);
                        if (frac < 1 && da * TAU * Math.max(d, 1) < 0.8 && d > 1.5) { v = 1; break; }
                        v = ang < frac ? ((r + c) % 2 == 0 ? 0.45 : 0) : 0;
                        break;
                    }
                    case "Rings":
                        if (d >= 10.6) v = ang < frac ? 1 : 0.08;
                        else if (d >= 6.6 && d <= 8.4) v = ang < (sx % 60) / 60 ? 0.75 : 0.08;
                        else if (d <= 2.6) v = pulse;
                        break;
                    default:
                        break;
                }
                out[i] = m.paused ? v * 0.6 : v;
            }
        }
        return out;
    }

    /** The text today's renderer shows: H:MM / MM:SS on 25 × 25, one number on 13 × 13. */
    private static String label(int n, int secs, boolean colonOn) {
        int h = secs / 3600, m = secs % 3600 / 60, s = secs % 60;
        if (n == 13) return h > 0 ? h + "h" : secs >= 60 ? pad(m) : pad(s);
        String t = h > 0 ? h + ":" + pad(m) : pad(m) + ":" + pad(s);
        return colonOn ? t : t.replace(':', ' ');
    }

    private static void textMask(int n, String text, int scale, boolean[] mask) {
        int w = 0;
        for (int k = 0; k < text.length(); k++) {
            char ch = text.charAt(k);
            w += (ch == ':' || ch == ' ' ? 1 : 3) * scale + (k < text.length() - 1 ? scale : 0);
        }
        int col = (int) Math.floor((n - w) / 2.0), top = (int) Math.floor((n - 5 * scale) / 2.0);
        for (int k = 0; k < text.length(); k++) {
            String[] g = DIG[text.charAt(k)];
            for (int r = 0; r < 5; r++)
                for (int c = 0; c < g[r].length(); c++)
                    if (g[r].charAt(c) == '1')
                        for (int a = 0; a < scale; a++)
                            for (int b = 0; b < scale; b++) {
                                int idx = (top + r * scale + a) * n + col + c * scale + b;
                                if (idx >= 0 && idx < n * n) mask[idx] = true;
                            }
            col += g[0].length() * scale + scale;
        }
    }

    private static String pad(int x) { return x < 10 ? "0" + x : String.valueOf(x); }
}
