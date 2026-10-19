package com.glyphcountdown.app;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.SystemFonts;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Display font: Nothing's NDot when the phone has it installed (Nothing OS ships it), so the
 * timer matches the rest of the system. NDot is Nothing's proprietary font, so it is never
 * bundled with this app; it is only read from the phone it's already on. Elsewhere the app
 * falls back to Doto, an open-source dot-matrix font bundled under assets/fonts (SIL OFL).
 */
final class Fonts {
    private static Typeface dot, mono, monoBold;
    private static boolean usingNdot;

    private Fonts() {}

    static Typeface doto(Context c) {
        if (dot == null) {
            dot = systemNdot();
            usingNdot = dot != null;
            if (dot == null) {
                try {
                    dot = new Typeface.Builder(c.getAssets(), "fonts/Doto.ttf")
                            .setFontVariationSettings("'wght' 900, 'ROND' 0")
                            .build();
                } catch (Throwable t) {
                    dot = Typeface.MONOSPACE;
                }
            }
        }
        return dot;
    }

    /** True when the phone's own NDot font is in use. */
    static boolean isNdot(Context c) { doto(c); return usingNdot; }

    static Typeface mono(Context c) {
        if (mono == null) mono = load(c, "fonts/SpaceMono-Regular.ttf");
        return mono;
    }

    static Typeface monoBold(Context c) {
        if (monoBold == null) monoBold = load(c, "fonts/SpaceMono-Bold.ttf");
        return monoBold;
    }

    /** Finds an NDot font file installed on the phone; prefers NDot 57, then 55. */
    private static Typeface systemNdot() {
        List<File> files = new ArrayList<>();
        try {
            for (Font f : SystemFonts.getAvailableFonts()) {
                if (f.getFile() != null) files.add(f.getFile());
            }
        } catch (Throwable ignored) {}
        for (String dir : new String[]{"/system/fonts", "/product/fonts", "/system_ext/fonts", "/vendor/fonts"}) {
            try {
                File[] list = new File(dir).listFiles();
                if (list != null) for (File f : list) files.add(f);
            } catch (Throwable ignored) {}
        }
        File best = null;
        int bestScore = 0;
        for (File f : files) {
            String n = f.getName().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "").replace(" ", "");
            if (!n.contains("ndot") || !(n.endsWith(".ttf") || n.endsWith(".otf"))) continue;
            int score = n.contains("57") ? 3 : n.contains("55") ? 2 : 1;
            if (score > bestScore && f.canRead()) { best = f; bestScore = score; }
        }
        if (best == null) return null;
        try {
            return Typeface.createFromFile(best);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Typeface load(Context c, String path) {
        try {
            return Typeface.createFromAsset(c.getAssets(), path);
        } catch (Throwable t) {
            return Typeface.MONOSPACE;
        }
    }
}
