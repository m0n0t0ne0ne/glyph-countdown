package com.glyphcountdown.app;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.view.WindowInsetsController;

/**
 * App colours for the chosen theme. Settings › Theme picks Phone (follow the phone's
 * Display › Appearance), Light or Dark; the choice applies to the app's screens and the widgets.
 */
final class Palette {
    static final String PHONE = "phone", LIGHT = "light", DARK = "dark";
    private static final String KEY = "theme";

    final boolean dark;
    final int bg, fg, grey, light, dim, border, card, line, divider, unlit, ring, switchOff, redText;

    private Palette(boolean dark) {
        this.dark = dark;
        if (dark) {
            bg = 0xFF000000; fg = 0xFFFFFFFF; grey = 0xFF8A8A8A; light = 0xFFBDBDBD; dim = 0xFF5A5A5A;
            border = 0xFF333333; card = 0xFF0A0A0A; line = 0xFF262626; divider = 0xFF1F1F1F;
            unlit = 0xFF2E2E2E; ring = 0xFF444444; switchOff = 0xFF333333; redText = 0xFFFF4A4A;
        } else {
            bg = 0xFFF2F3F4; fg = 0xFF181C1E; grey = 0xFF5E6468; light = 0xFF3E4448; dim = 0xFF9AA0A4;
            border = 0xFFCDD1D4; card = 0xFFFFFFFF; line = 0xFFDADDE0; divider = 0xFFE3E5E7;
            unlit = 0xFFCDD1D4; ring = 0xFFB8BDC1; switchOff = 0xFFC3C8CC; redText = 0xFFD71921;
        }
    }

    /** "phone", "light" or "dark". */
    static String choice(Context c) {
        return c.getSharedPreferences("timer", Context.MODE_PRIVATE).getString(KEY, PHONE);
    }

    static void setChoice(Context c, String value) {
        c.getSharedPreferences("timer", Context.MODE_PRIVATE).edit().putString(KEY, value).apply();
    }

    static boolean phoneIsDark(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    static boolean isDark(Context c) {
        String ch = choice(c);
        if (DARK.equals(ch)) return true;
        if (LIGHT.equals(ch)) return false;
        return phoneIsDark(c);
    }

    static Palette of(Context c) { return new Palette(isDark(c)); }

    /** Dark status and navigation bar icons on the light theme. */
    static void applyBars(Activity a, Palette p) {
        try {
            WindowInsetsController ic = a.getWindow().getInsetsController();
            int flags = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            if (ic != null) ic.setSystemBarsAppearance(p.dark ? 0 : flags, flags);
        } catch (Throwable ignored) {}
    }
}
