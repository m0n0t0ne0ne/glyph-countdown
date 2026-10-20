package com.glyphcountdown.app;

import android.content.Context;
import android.content.SharedPreferences;

/** On/off settings from the Settings screen. */
final class Prefs {
    static final String POCKET = "pocket_mode", TRANSLUCENT = "widget_translucent",
            VIBRATE = "alarm_vibrate", SCREEN_OFF_ONLY = "matrix_screen_off_only", SYNC = "anim_sync",
            DIM = "anim_dim";
    private static final String ANIM = "times_up_animation";

    private Prefs() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("timer", Context.MODE_PRIVATE);
    }

    static boolean get(Context c, String key) {
        return p(c).getBoolean(key, !TRANSLUCENT.equals(key)); // everything but translucency defaults on
    }

    static void set(Context c, String key, boolean on) {
        p(c).edit().putBoolean(key, on).apply();
    }

    /** The time's-up animation chosen in Settings (one of MorphEngine.NAMES). */
    static String anim(Context c) {
        String a = p(c).getString(ANIM, MorphEngine.DEFAULT);
        for (String n : MorphEngine.NAMES) if (n.equals(a)) return a;
        return MorphEngine.DEFAULT;
    }

    static void setAnim(Context c, String name) {
        p(c).edit().putString(ANIM, name).apply();
    }

    /** How far "Dim with notes" dims, 0..100 (default 80). */
    static int dimDepth(Context c) {
        return Math.max(0, Math.min(100, p(c).getInt("anim_dim_depth", 80)));
    }

    static void setDimDepth(Context c, int pct) {
        p(c).edit().putInt("anim_dim_depth", Math.max(0, Math.min(100, pct))).apply();
    }
}
