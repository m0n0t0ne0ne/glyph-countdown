package com.glyphcountdown.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

/** The alarm sound choice and the lists of sounds installed on the phone. */
final class Sounds {
    static final String SRC_DEFAULT = "Default", SRC_NOTHING = "Nothing", SRC_ALARM = "Alarm";

    static final class Item {
        final String title; final Uri uri; final String source;
        Item(String title, Uri uri, String source) { this.title = title; this.uri = uri; this.source = source; }
    }

    private Sounds() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("timer", Context.MODE_PRIVATE);
    }

    static void save(Context c, Item item) {
        prefs(c).edit()
                .putString("sound_uri", item.uri == null ? "" : item.uri.toString())
                .putString("sound_name", item.title)
                .putString("sound_src", item.source)
                .apply();
    }

    static String selectedName(Context c) { return prefs(c).getString("sound_name", "Device default"); }

    static String selectedSource(Context c) { return prefs(c).getString("sound_src", SRC_DEFAULT); }

    /** Saved URI string, or "" for the device's default alarm. */
    static String selectedUriString(Context c) { return prefs(c).getString("sound_uri", ""); }

    /** The sound the alarm should play, falling back to the device default alarm. */
    static Uri alarmUri(Context c) {
        String s = selectedUriString(c);
        if (!s.isEmpty()) return Uri.parse(s);
        Uri uri = RingtoneManager.getActualDefaultRingtoneUri(c, RingtoneManager.TYPE_ALARM);
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        return uri;
    }

    /** Sounds of one type installed on the phone (TYPE_NOTIFICATION or TYPE_ALARM). */
    static List<Item> list(Context c, int type) {
        String src = type == RingtoneManager.TYPE_ALARM ? SRC_ALARM : SRC_NOTHING;
        List<Item> out = new ArrayList<>();
        out.add(new Item("Device default", null, SRC_DEFAULT));
        try {
            RingtoneManager rm = new RingtoneManager(c);
            rm.setType(type);
            Cursor cur = rm.getCursor();
            List<String> seen = new ArrayList<>();
            while (cur.moveToNext()) {
                String title = cur.getString(RingtoneManager.TITLE_COLUMN_INDEX);
                Uri uri = rm.getRingtoneUri(cur.getPosition());
                if (title == null || uri == null || seen.contains(title)) continue;
                seen.add(title);
                out.add(new Item(title, uri, src));
            }
        } catch (Throwable ignored) {
            // the list may be unavailable; "Device default" still works
        }
        return out;
    }

    static Ringtone ringtone(Context c, Uri uri) {
        Ringtone r = RingtoneManager.getRingtone(c, uri != null ? uri : alarmUri(c));
        if (r != null) {
            r.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
        }
        return r;
    }
}
