package com.glyphcountdown.app;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Colours for the widgets' pills and ring, read from the home-screen wallpaper itself
 * (WallpaperColors, no permission needed), not from Nothing's or Android's theme colours.
 * A picked colour is stored as-is and tuned per card: lighter on the dark widget, deeper on the
 * light one, so lit thirds always stand out.
 */
final class WidgetColours {
    private WidgetColours() {}

    /** Up to 6 of the wallpaper's most colourful main colours, at least 20° apart in hue. */
    static List<Integer> fromWallpaper(Context c) {
        List<Integer> out = new ArrayList<>();
        try {
            WallpaperColors wc = WallpaperManager.getInstance(c).getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
            if (wc == null) return out;
            List<Integer> main = mainColours(wc);
            // most colourful first; near-greys are skipped
            Collections.sort(main, new Comparator<Integer>() {
                @Override public int compare(Integer a, Integer b) {
                    return Float.compare(chroma(b), chroma(a));
                }
            });
            for (int col : main) {
                int argb = col | 0xFF000000;
                if (chroma(argb) < 0.06f) continue;
                boolean apart = true;
                for (int k : out) if (hueDistance(hsl(k)[0], hsl(argb)[0]) < 20) { apart = false; break; }
                if (apart) out.add(argb);
                if (out.size() == 6) break;
            }
        } catch (Throwable ignored) {
            // a live wallpaper or a locked-down launcher may not report colours
        }
        return out;
    }

    /**
     * All of the wallpaper's main colours. getMainColors() is blocked for apps (hidden API), and
     * the public getters give only three, but toString() lists every main colour as ARGB hex
     * ("WallpaperColors: ff195362 ff64999e ... h: 6"), so read them from there. If that format
     * ever changes, fall back to the public primary / secondary / tertiary colours.
     */
    private static List<Integer> mainColours(WallpaperColors wc) {
        List<Integer> out = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b(ff[0-9a-fA-F]{6})\\b").matcher(String.valueOf(wc));
        while (m.find()) {
            int argb = (int) Long.parseLong(m.group(1), 16);
            if (!out.contains(argb)) out.add(argb);
        }
        if (out.isEmpty()) {
            for (Color col : new Color[]{wc.getPrimaryColor(), wc.getSecondaryColor(), wc.getTertiaryColor()}) {
                if (col != null && !out.contains(col.toArgb())) out.add(col.toArgb());
            }
        }
        return out;
    }

    /** Lit thirds on the dark or light card. */
    static int lit(int colour, boolean dark) {
        float[] h = hsl(colour);
        float s = clamp(h[1] + 0.15f, 0.45f, 0.75f);
        return fromHsl(h[0], s, dark ? 0.72f : 0.38f);
    }

    /** Empty thirds: the same hue, faint. */
    static int empty(int colour, boolean dark) {
        float[] h = hsl(colour);
        float s = Math.min(clamp(h[1] + 0.15f, 0.45f, 0.75f), 0.35f);
        return fromHsl(h[0], s, dark ? 0.22f : 0.87f);
    }

    /** A plain name for the colour, from its hue. */
    static String name(int colour) {
        if (colour == 0) return "Plain";
        float hue = hsl(colour)[0];
        int[] at = {0, 20, 40, 60, 95, 150, 180, 205, 230, 265, 290, 320, 345};
        String[] names = {"Red", "Coral", "Amber", "Olive", "Sage", "Green", "Teal", "Steel", "Blue", "Indigo", "Violet", "Plum", "Rose"};
        int best = 0;
        for (int i = 1; i < at.length; i++) if (hueDistance(hue, at[i]) < hueDistance(hue, at[best])) best = i;
        return names[best];
    }

    // ---- colour maths

    private static float chroma(int c) {
        float r = Color.red(c) / 255f, g = Color.green(c) / 255f, b = Color.blue(c) / 255f;
        return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
    }

    private static float hueDistance(float a, float b) {
        float d = Math.abs(a - b) % 360;
        return Math.min(d, 360 - d);
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    /** {hue 0–360, saturation 0–1, lightness 0–1} */
    static float[] hsl(int c) {
        float r = Color.red(c) / 255f, g = Color.green(c) / 255f, b = Color.blue(c) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float l = (max + min) / 2, s = 0, h = 0;
        if (d > 0) {
            s = d / (1 - Math.abs(2 * l - 1));
            if (max == r) h = ((g - b) / d) % 6;
            else if (max == g) h = (b - r) / d + 2;
            else h = (r - g) / d + 4;
            h *= 60;
            if (h < 0) h += 360;
        }
        return new float[]{h, s, l};
    }

    static int fromHsl(float h, float s, float l) {
        float c = (1 - Math.abs(2 * l - 1)) * s, x = c * (1 - Math.abs((h / 60) % 2 - 1)), m = l - c / 2;
        float r, g, b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return Color.rgb(Math.round((r + m) * 255), Math.round((g + m) * 255), Math.round((b + m) * 255));
    }
}
