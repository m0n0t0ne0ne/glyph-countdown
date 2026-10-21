package com.glyphcountdown.app;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;

/**
 * Spots two quick taps on the back of the phone using the motion sensor. A tap on the back is a
 * short, sharp jolt mostly along the axis through the screen; the threshold rises with background
 * movement (including the alarm's own vibration) so steady shaking isn't mistaken for taps.
 */
final class BackTapDetector implements SensorEventListener {
    interface Listener { void onDoubleTap(); }

    private static final float MIN_JOLT = 2.5f;        // m/s², weakest tap that counts
    private static final float NOISE_FACTOR = 5f;      // a tap must stand this far above background
    private static final long REFRACTORY_MS = 100;     // one tap rings for a few tens of ms
    private static final long GAP_MIN_MS = 120, GAP_MAX_MS = 600;
    private static final long SETTLE_MS = 500;

    private final SensorManager sensors;
    private final Listener listener;
    private boolean linear, running;
    private float noise = 0.3f;
    private final float[] gravity = new float[3];
    private long startedAt, lastTap = -10_000, lastSpike = -10_000;

    BackTapDetector(Context c, Listener listener) {
        this.sensors = c.getSystemService(SensorManager.class);
        this.listener = listener;
    }

    void start() {
        if (running || sensors == null) return;
        Sensor s = sensors.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        linear = s != null;
        if (s == null) s = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (s == null) return;
        startedAt = SystemClock.elapsedRealtime();
        lastTap = lastSpike = -10_000;
        sensors.registerListener(this, s, SensorManager.SENSOR_DELAY_FASTEST);
        running = true;
    }

    void stop() {
        if (running) sensors.unregisterListener(this);
        running = false;
    }

    @Override public void onSensorChanged(SensorEvent e) {
        float x = e.values[0], y = e.values[1], z = e.values[2];
        if (!linear) { // remove gravity with a slow low-pass
            for (int i = 0; i < 3; i++) gravity[i] = 0.9f * gravity[i] + 0.1f * e.values[i];
            x -= gravity[0]; y -= gravity[1]; z -= gravity[2];
        }
        long now = SystemClock.elapsedRealtime();
        float az = Math.abs(z), side = Math.max(Math.abs(x), Math.abs(y));
        boolean spike = az > Math.max(MIN_JOLT, NOISE_FACTOR * noise) && az > 1.5f * side;
        if (!spike) noise = 0.98f * noise + 0.02f * Math.max(az, side);
        if (!spike || now - startedAt < SETTLE_MS || now - lastSpike < REFRACTORY_MS) {
            if (spike) lastSpike = now;
            return;
        }
        lastSpike = now;
        long gap = now - lastTap;
        if (gap >= GAP_MIN_MS && gap <= GAP_MAX_MS) {
            lastTap = -10_000;
            listener.onDoubleTap();
        } else {
            lastTap = now;
        }
    }

    @Override public void onAccuracyChanged(Sensor s, int a) {}
}
