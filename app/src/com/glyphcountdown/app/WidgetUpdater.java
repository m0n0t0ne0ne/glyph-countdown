package com.glyphcountdown.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.RemoteViews;

/** Redraws every placed widget from the current timer state. */
final class WidgetUpdater {
    private WidgetUpdater() {}

    static void updateAll(Context c) {
        try {
            TimerState.get().load(c);
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            update(c, m, Widget1x1.class, WidgetRenderer.K1);
            update(c, m, Widget2x1.class, WidgetRenderer.K2);
            update(c, m, Widget2x2.class, WidgetRenderer.K4);
        } catch (Throwable ignored) {
            // a widget failing to draw must never stop the timer
        }
    }

    private static void update(Context c, AppWidgetManager m, Class<?> cls, int kind) {
        int[] ids = m.getAppWidgetIds(new ComponentName(c, cls));
        for (int id : ids) {
            Bundle o = m.getAppWidgetOptions(id);
            int wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
            int hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
            if (wDp <= 0 || hDp <= 0) {
                wDp = kind == WidgetRenderer.K1 ? 80 : 170;
                hDp = kind == WidgetRenderer.K4 ? 170 : 80;
            }
            float d = c.getResources().getDisplayMetrics().density;
            float scale = Math.min(1f, 520f / Math.max(wDp * d, hDp * d)); // two bitmaps per update: keep them small
            int w = Math.round(wDp * d * scale), h = Math.round(hDp * d * scale);

            TimerState st = TimerState.get();
            boolean translucent = Prefs.get(c, Prefs.TRANSLUCENT);
            // Both appearances are sent; the layout and layout-night variants each show one, so the
            // widget follows Settings › Display › Appearance even when this app isn't running.
            // Theme chosen in Settings: Light or Dark fills both slots with that theme, so the
            // phone's appearance no longer switches it; Phone keeps one of each.
            String theme = Palette.choice(c);
            Bitmap light, dark;
            if (Palette.PHONE.equals(theme)) {
                light = WidgetRenderer.render(c, kind, w, h, st, false, translucent);
                dark = WidgetRenderer.render(c, kind, w, h, st, true, translucent);
            } else {
                light = dark = WidgetRenderer.render(c, kind, w, h, st, Palette.DARK.equals(theme), translucent);
            }
            String pkg = c.getPackageName();
            int layout = res(c, "layout", kind == WidgetRenderer.K1 ? "widget_1x1"
                    : kind == WidgetRenderer.K2 ? "widget_2x1" : "widget_2x2");
            RemoteViews rv = new RemoteViews(pkg, layout);
            for (String n : new String[]{"w_image_light", "w_image_dark"}) {
                int image = res(c, "id", n);
                rv.setImageViewBitmap(image, n.endsWith("dark") ? dark : light);
                rv.setContentDescription(image, describe(st));
            }

            PendingIntent toggle = PendingIntent.getForegroundService(c, 100,
                    new Intent(c, TimerService.class).setAction(TimerService.ACTION_TOGGLE)
                            .putExtra(TimerService.EXTRA_FG, true),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent open = PendingIntent.getActivity(c, 102,
                    new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            rv.setOnClickPendingIntent(res(c, "id", "w_toggle"), toggle);
            rv.setContentDescription(res(c, "id", "w_toggle"), toggleLabel(st));
            if (kind == WidgetRenderer.K2) {
                rv.setOnClickPendingIntent(res(c, "id", "w_open"), open);
            } else if (kind == WidgetRenderer.K4) {
                PendingIntent reset = PendingIntent.getForegroundService(c, 101,
                        new Intent(c, TimerService.class).setAction(TimerService.ACTION_RESET)
                                .putExtra(TimerService.EXTRA_FG, true),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                rv.setOnClickPendingIntent(res(c, "id", "w_reset"), reset);
                for (String n : new String[]{"w_open", "w_open2", "w_open3", "w_open4"}) {
                    rv.setOnClickPendingIntent(res(c, "id", n), open);
                }
            }
            m.updateAppWidget(id, rv);
        }
    }

    private static String toggleLabel(TimerState st) {
        switch (st.mode) {
            case TimerState.SET: return "Start timer";
            case TimerState.RUN: return "Pause timer";
            case TimerState.PAUSE: return "Resume timer";
            default: return "Dismiss alarm";
        }
    }

    private static String describe(TimerState st) {
        long s = st.displaySeconds();
        String t = (s / 3600) + " hours " + ((s % 3600) / 60) + " minutes " + (s % 60) + " seconds";
        switch (st.mode) {
            case TimerState.SET: return "Timer set to " + t;
            case TimerState.RUN: return t + " remaining";
            case TimerState.PAUSE: return "Paused, " + t + " remaining";
            default: return "Time's up";
        }
    }

    private static int res(Context c, String type, String name) {
        return c.getResources().getIdentifier(name, type, c.getPackageName());
    }
}
