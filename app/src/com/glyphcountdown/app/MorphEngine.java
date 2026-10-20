package com.glyphcountdown.app;

/**
 * Time's-up animations for the Glyph Matrix. Each design is a shape that changes with the loop
 * phase p (0..1), evaluated at every LED with mirror folding for symmetry, so the same maths
 * draws the 25 × 25 Phone (3) matrix and the 13 × 13 Phone (4a) Pro matrix. Every design loops
 * seamlessly. Plain Java (no Android classes), so it can be tested on any JVM.
 */
public final class MorphEngine {
    public static final String DEFAULT = "Default";
    /** Picker order. "Default" is the original flashing ring and 00, drawn by MatrixRenderer. */
    public static final String[] NAMES = {DEFAULT, "Contour", "Kaleido", "Tide", "Pulse", "Totem",
            "Lattice", "Moiré", "Weave", "Glyph", "Knot"};
    public static final String[] NOTES = {
            "Ring and 00, flashing",
            "Thin outlines that stretch, pinch and re-join",
            "A turning kaleidoscope of fragments",
            "Diamond rings roll outward over a breathing square",
            "Wavy rings ripple out from the centre",
            "Folds like origami, then opens back out",
            "Crossing waves knot a circle into a star",
            "Two drifting ring sets interfere into lace",
            "Nested squares, each ring turning the other way",
            "Crystalline fragments grow, connect and shatter",
            "Six interlocking circles pulse and turn"};

    private static final double TAU = Math.PI * 2;
    private static final int[][] BAYER = {{0, 8, 2, 10}, {12, 4, 14, 6}, {3, 11, 1, 9}, {15, 7, 13, 5}};
    private static final int[][] ROWS13 = {{4, 8}, {2, 10}, {1, 11}, {1, 11}, {0, 12}, {0, 12}, {0, 12},
            {0, 12}, {0, 12}, {1, 11}, {1, 11}, {2, 10}, {4, 8}};
    private static final double G = 2.0 / 25;     // one Phone (3) LED in u/v units

    /** LED pitch of the matrix being drawn (set per frame). */
    private static double ledW = G;

    private MorphEngine() {}

    /** Natural loop length in seconds before any sound sync. */
    public static double naturalSeconds(String name) {
        return "Totem".equals(name) ? 6.0 : 4.0;
    }

    public static boolean onMatrix(int n, int r, int c) {
        if (n == 13) return c >= ROWS13[r][0] && c <= ROWS13[r][1];
        double dr = r - 12, dc = c - 12;
        return dr * dr + dc * dc <= 156.25;
    }

    /** 1 where an LED is lit, 0 elsewhere, for design {@code name} at phase p. */
    public static int[] frame(String name, int n, double p) {
        ledW = 2.0 / n;
        boolean thin = n == 13 && "Lattice".equals(name);   // sample 4 points so thin lines survive 13×13
        double[][] sub = thin ? new double[][]{{0.25, 0.25}, {0.75, 0.25}, {0.25, 0.75}, {0.75, 0.75}}
                : new double[][]{{0.5, 0.5}};
        int[] px = new int[n * n];
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                if (!onMatrix(n, r, c)) continue;
                for (double[] s : sub) {
                    double u = (c + s[0]) / n * 2 - 1, v = (r + s[1]) / n * 2 - 1;
                    if (eval(name, u, v, p, r, c)) { px[r * n + c] = 1; break; }
                }
            }
        }
        return px;
    }

    // ---- helpers -----------------------------------------------------------------------

    private static double S(double p, double k) { return Math.sin(TAU * p * k); }
    private static double C(double p, double k) { return Math.cos(TAU * p * k); }
    private static double mod(double a, double b) { return ((a % b) + b) % b; }

    private static double balls(double u, double v, double[][] bs) {
        double f = 0;
        for (double[] b : bs) {
            double d2 = (u - b[0]) * (u - b[0]) + (v - b[1]) * (v - b[1]);
            f += b[3] * b[2] * b[2] / Math.max(d2, 1e-4);
        }
        return f;
    }

    private static double[] rot(double u, double v, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new double[]{u * c - v * s, u * s + v * c};
    }

    /** sym: 'x' mirror left/right, 'y' both axes, 'd' both axes and the diagonal. */
    private static double[] fold(double u, double v, char sym) {
        u = Math.abs(u);
        if (sym != 'x') v = Math.abs(v);
        if (sym == 'd' && v > u) { double t = u; u = v; v = t; }
        return new double[]{u, v};
    }

    private static double hash2(int ix, int iy) {
        int h = ix * 374761393 + iy * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xffff) / 65535.0;
    }

    private static double vnoise(double x, double y) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        double fx = x - ix, fy = y - iy;
        double sx = fx * fx * (3 - 2 * fx), sy = fy * fy * (3 - 2 * fy);
        double a = hash2(ix, iy), b = hash2(ix + 1, iy), c = hash2(ix, iy + 1), d = hash2(ix + 1, iy + 1);
        return a + (b - a) * sx + (c - a) * sy + (a - b - c + d) * sx * sy;
    }

    // ---- designs ------------------------------------------------------------------------

    private static boolean eval(String name, double u, double v, double p, int r, int c) {
        double[] q;
        switch (name) {
            case "Contour": {
                q = fold(u, v, 'y'); u = q[0]; v = q[1];
                double f = balls(u, v, new double[][]{{0.35 + 0.2 * S(p, 1), 0.15, 0.22, 1},
                        {0.15, 0.4 + 0.2 * C(p, 1), 0.2, 1}, {0, 0, 0.15 + 0.1 * S(p, 2), 1}});
                return Math.abs(f - 1) < 0.22 || Math.abs(f - 2.6) < 0.4;
            }
            case "Kaleido": {
                q = rot(u, v, TAU * p / 8); q = fold(q[0], q[1], 'd'); u = q[0]; v = q[1];
                double f = balls(u, v, new double[][]{{0.55 + 0.15 * S(p, 1), 0.18 + 0.12 * C(p, 1), 0.13, 1},
                        {0.25, 0.08 + 0.1 * S(p, 2), 0.10, 1}, {0.8, 0.4 * Math.abs(S(p, 1)), 0.08, 1}});
                return f > 1 && !(f > 3.2);
            }
            case "Tide": {
                q = rot(u, v, TAU * p / 4);
                double d = Math.abs(q[0]) + Math.abs(q[1]), sq = Math.max(Math.abs(u), Math.abs(v));
                boolean rings = mod(Math.floor((d - p * 0.6) * 4), 2) == 0 && d < 1.15;
                return rings != (sq < 0.32 + 0.1 * S(p, 1));
            }
            case "Pulse": {
                q = fold(u, v, 'y'); u = q[0]; v = q[1];
                double rr = Math.hypot(u, v), wob = 0.06 * Math.cos(8 * Math.atan2(v, u));
                return mod(rr + wob - p * 0.5, 0.5) < 0.14 && rr < 1;
            }
            case "Totem":
                return totem(u, v, p);
            case "Lattice": {
                q = rot(u, v, TAU * p / 8); q = fold(q[0], q[1], 'd'); u = q[0]; v = q[1];
                double k = 5.2, f = Math.cos(k * u) + Math.cos(k * v) + Math.cos(k * 0.7 * (u + v) + TAU * p)
                        + 0.8 * Math.cos(k * 1.4 * Math.hypot(u, v) - TAU * p);
                return Math.abs(f - 0.4) < 0.42 && Math.hypot(u, v) < 1.02;
            }
            case "Moiré": {
                q = fold(u, v, 'y'); u = q[0]; v = q[1];
                double a = 0.35 + 0.15 * S(p, 1), d1 = Math.hypot(u - a, v), d2 = Math.hypot(u, v - a);
                return Math.sin(18 * d1 - TAU * p) * Math.sin(18 * d2 + TAU * p) > 0.25;
            }
            case "Weave": {
                double rr = Math.max(Math.abs(u), Math.abs(v));
                int band = (int) Math.floor(rr * 5);
                q = rot(u, v, (band % 2 == 1 ? 1 : -1) * TAU * p / 4);
                double sq = Math.max(Math.abs(q[0]), Math.abs(q[1]));
                return Math.floor(sq * 5) == band && (sq * 5 - band) < 0.55 && rr < 0.98;
            }
            case "Glyph": {
                q = fold(u, v, 'd'); u = q[0]; v = q[1];
                double n = vnoise(u * 3.2 + 1.5 * C(p, 1) + 7.3, v * 3.2 + 1.5 * S(p, 1) + 3.1);
                double edge = 0.62 - 0.25 * Math.abs(u + v);
                return n > edge && !(n > edge + 0.18);
            }
            case "Knot": {
                q = rot(u, v, TAU * p / 8); u = q[0]; v = q[1];
                double a = 0.40 + 0.08 * S(p, 1), R = 0.40 + 0.10 * C(p, 1);
                for (int i = 0; i < 6; i++) {
                    double t = TAU * i / 6;
                    if (Math.abs(Math.hypot(u - a * Math.cos(t), v - a * Math.sin(t)) - R) < 0.05) return true;
                }
                return Math.hypot(u, v) < 0.08 + 0.05 * S(p, 2);
            }
            default:
                return false;
        }
    }

    // ---- Totem: origami fold ------------------------------------------------------------

    /** The open design: crescent brackets either side and a stacked centre column. */
    private static boolean totemFlat(double u, double v) {
        double au = Math.abs(u);
        if (Math.hypot(au - 1.22, v) < 0.62 && Math.hypot(au - 1.38, v) > 0.62) return true;
        double y = Math.abs(v) / G, x = au / G;
        boolean top = v < 0;
        boolean crown = top
                ? box(x, y, 1, 2, 9, 9) || box(x, y, 0, 2, 8, 8) || box(x, y, 1, 1, 7, 7)
                : box(x, y, 1, 1, 9, 10) || box(x, y, 0, 2, 8, 8) || box(x, y, 1, 2, 7, 7);
        return crown || box(x, y, 4, 5, 7, 7) || box(x, y, 0, 3, 4, 4) || box(x, y, 1, 1, 5, 5)
                || box(x, y, 3, 3, 5, 5) || box(x, y, 0, 1, 3, 3) || box(x, y, 0, 0, 2, 2);
    }

    private static boolean box(double x, double y, double x0, double x1, double y0, double y1) {
        return x >= x0 - 0.5 && x <= x1 + 0.5 && y >= y0 - 0.5 && y <= y1 + 0.5;
    }

    private interface Paper { boolean at(double u, double v); }

    /** Fold paper along creases x = ±a: outer flaps rotate by theta (0 open … π folded over).
     *  Layers show through each other; the moving flap edge is drawn as a bright line. */
    private static Paper foldX(final Paper f, final double theta, final double a, final double span) {
        final double ct = Math.cos(theta);
        return new Paper() {
            @Override public boolean at(double u, double v) {
                double au = Math.abs(u), s = u >= 0 ? 1 : -1;
                if (Math.abs(v) > span + ledW * 0.5) return false;
                double xe = a + (1 - a) * ct;
                if (theta > 0 && theta < Math.PI && Math.abs(au - xe) < ledW * 0.55) return true;
                boolean hit = false;
                if (au <= a) hit = f.at(u, v);
                if (ct < 0 && au >= xe - ledW * 0.5 && au <= a) hit = hit || f.at(s * (a + (au - a) / ct), v);
                if (ct > 0 && au > a && au <= xe) hit = f.at(s * (a + (au - a) / ct), v);
                return hit;
            }
        };
    }

    private static double ease(double x) {
        x = Math.min(1, Math.max(0, x));
        return x * x * (3 - 2 * x);
    }

    private static boolean totem(double u, double v, double p) {
        // open · sides fold in · top and bottom fold in · rest folded · unfold top/bottom · unfold sides
        double tx, ty;
        if (p < 0.08) { tx = 0; ty = 0; }
        else if (p < 0.32) { tx = ease((p - 0.08) / 0.24); ty = 0; }
        else if (p < 0.52) { tx = 1; ty = ease((p - 0.32) / 0.20); }
        else if (p < 0.60) { tx = 1; ty = 1; }
        else if (p < 0.78) { tx = 1; ty = 1 - ease((p - 0.60) / 0.18); }
        else if (p < 0.96) { tx = 1 - ease((p - 0.78) / 0.18); ty = 0; }
        else { tx = 0; ty = 0; }
        final double A = 0.36, thx = Math.PI * tx * 0.97, thy = Math.PI * ty * 0.97;
        double wx = Math.max(A, A + (1 - A) * Math.cos(thx));
        final Paper paper = foldX(new Paper() {
            @Override public boolean at(double x, double y) { return totemFlat(x, y); }
        }, thx, A, 1);
        Paper g = foldX(new Paper() {
            @Override public boolean at(double x, double y) { return paper.at(y, x); }
        }, thy, A, wx);
        return g.at(v, u);
    }

    // ---- accents (sound sync) ------------------------------------------------------------

    /** Ring of sparks around the edge, lit on a note. */
    public static boolean spark(int n, int r, int c) {
        double c0 = (n - 1) / 2.0, dr = r - c0, dc = c - c0, d2 = dr * dr + dc * dc;
        double inner = n == 13 ? 30.25 : 132;
        return onMatrix(n, r, c) && d2 >= inner && (r + c) % 2 == 0;
    }
}
