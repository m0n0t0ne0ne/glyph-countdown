package com.glyphcountdown.app;

import android.content.Context;
import android.content.SharedPreferences;

/** On/off settings from the Settings screen. */
final class Prefs {
    static final String POCKET = "pocket_mode", FLIP = "flip_to_start", TRANSLUCENT = "widget_translucent",
            VIBRATE = "alarm_vibrate";

    private Prefs() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("timer", Context.MODE_PRIVATE);
    }

    static boolean get(Context c, String key) {
        return p(c).getBoolean(key, !TRANSLUCENT.equals(key)); // pocket and flip default on
    }

    static void set(Context c, String key, boolean on) {
        p(c).edit().putBoolean(key, on).apply();
    }
}
