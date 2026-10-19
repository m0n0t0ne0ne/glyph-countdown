package com.glyphcountdown.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Process-wide timer state shared by the screen and the foreground service. */
public final class TimerState {
    public static final int SET = 0, RUN = 1, PAUSE = 2, DONE = 3;
    public static final int UNIT_H = 0, UNIT_M = 1, UNIT_S = 2;

    public interface Listener { void onTimerChanged(); }

    private static final TimerState INSTANCE = new TimerState();
    public static TimerState get() { return INSTANCE; }

    public int mode = SET;
    public int hh = 0, mm = 25, ss = 0;      // the duration being set on the dial
    public long totalMs;                      // duration of the running timer
    public long remainingMs;                  // valid while paused
    public long endAtElapsed;                 // valid while running (SystemClock.elapsedRealtime)
    public boolean blink = true;              // toggles while the alarm is going off
    public boolean matrixActive;              // Glyph Matrix connected and showing the timer

    private boolean loaded;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());

    private TimerState() {}

    public int setSeconds() { return hh * 3600 + mm * 60 + ss; }

    public int get(int unit) { return unit == UNIT_H ? hh : unit == UNIT_M ? mm : ss; }

    public void set(int unit, int value) {
        if (unit == UNIT_H) hh = value; else if (unit == UNIT_M) mm = value; else ss = value;
    }

    public static int maxFor(int unit) { return unit == UNIT_H ? 24 : 60; }

    /** Milliseconds left, live while running. */
    public long remainingNow() {
        if (mode == RUN) return Math.max(0, endAtElapsed - SystemClock.elapsedRealtime());
        if (mode == PAUSE) return remainingMs;
        if (mode == DONE) return 0;
        return setSeconds() * 1000L;
    }

    /** Whole seconds shown on screen (rounded up, like a kitchen timer). */
    public long displaySeconds() {
        return (remainingNow() + 999) / 1000;
    }

    public void load(Context c) {
        if (loaded) return;
        loaded = true;
        SharedPreferences p = c.getSharedPreferences("timer", Context.MODE_PRIVATE);
        hh = p.getInt("hh", 0);
        mm = p.getInt("mm", 25);
        ss = p.getInt("ss", 0);
    }

    public void save(Context c) {
        c.getSharedPreferences("timer", Context.MODE_PRIVATE).edit()
                .putInt("hh", hh).putInt("mm", mm).putInt("ss", ss).apply();
    }

    public void addListener(Listener l) { listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    public void notifyChanged() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            for (Listener l : listeners) l.onTimerChanged();
        } else {
            main.post(new Runnable() { @Override public void run() { notifyChanged(); } });
        }
    }
}
