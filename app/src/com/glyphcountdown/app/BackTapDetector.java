package com.glyphcountdown.app;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

/**
 * Spots two quick taps on the back of the phone using the motion sensor, whichever way up it is.
 * A tap is a short, sharp jolt mostly along the axis through the screen; the threshold rises with
 * background movement (including the alarm's own vibration) so steady shaking isn't mistaken for taps.
 *
 * <p>Lying on a table, a tap barely moves the phone: the table pushes back, so the jolt is small
 * and lasts only a few milliseconds. So it reads the raw accelerometer (the fused "linear
 * acceleration" sensor is filtered and can smooth such a short jolt away), measures each reading
 * against a quick-moving baseline rather than a slow gravity estimate, and accepts a smaller jolt
 * while the phone lies flat, where the background is calm and little else jolts it.
 *
 * <p>Readings arrive hundreds of times a second and a tap on a table lasts a few milliseconds, so
 * they are handled on a thread of their own, away from the main thread (which draws the time's-up
 * animation), and timed by when each was measured rather than when it was delivered.
 */
final class BackTapDetector implements SensorEventListener {
    interface Listener { void onDoubleTap(); }

    private static final float MIN_JOLT_HELD = 2.5f;   // m/s², weakest tap that counts in the hand
    private static final float MIN_JOLT_FLAT = 0.5f;   // lying flat on a surface, a tap moves it far less
    private static final float FLAT_Z = 9.0f;          // gravity this much along the screen axis: flat (about 25°)
    private static final float NOISE_FACTOR = 5f;      // a tap must stand this far above background
    private static final float SIDE_RATIO = 1.5f;      // mostly through the screen, not sideways
    private static final float BASE_RATE = 0.08f;      // baseline follows within ~30 ms: removes gravity and slow motion
    private static final float GRAVITY_RATE = 0.02f;   // slow average: which way up the phone is
    private static final long REFRACTORY_MS = 100;     // one tap rings for a few tens of ms
    private static final long GAP_MIN_MS = 120, GAP_MAX_MS = 600;
    private static final long SETTLE_MS = 500;

    private final SensorManager sensors;
    private final Listener listener;
    private boolean running;
    private HandlerThread thread;
    private final Core core = new Core();

    BackTapDetector(Context c, Listener listener) {
        this.sensors = c.getSystemService(SensorManager.class);
        this.listener = listener;
    }

    void start() {
        if (running || sensors == null) return;
        Sensor s = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (s == null) return;
        core.reset(SystemClock.elapsedRealtime());
        thread = new HandlerThread("back-tap", android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY);
        thread.start();
        sensors.registerListener(this, s, SensorManager.SENSOR_DELAY_FASTEST, new Handler(thread.getLooper()));
        running = true;
    }

    void stop() {
        if (running) sensors.unregisterListener(this);
        if (thread != null) thread.quitSafely();
        thread = null;
        running = false;
    }

    /** On the back-tap thread. The listener is called from here too. */
    @Override public void onSensorChanged(SensorEvent e) {
        long measuredAt = e.timestamp / 1_000_000;   // ns since boot, the same clock as elapsedRealtime
        if (core.accel(measuredAt, e.values[0], e.values[1], e.values[2])) {
            listener.onDoubleTap();
        }
    }

    @Override public void onAccuracyChanged(Sensor s, int a) {}

    /** The detection itself, in plain Java so it can be fed recorded or simulated readings. */
    static final class Core {
        private final float[] base = new float[3], gravity = new float[3];
        private boolean primed;
        private float noise = 0.3f;
        private long startedAt, lastTap = -10_000, lastSpike = -10_000;

        void reset(long now) {
            startedAt = now;
            lastTap = lastSpike = -10_000;
            primed = false;
            noise = 0.3f;
        }

        /** Raw accelerometer reading in m/s². Returns true when it completes a double tap. */
        boolean accel(long now, float x, float y, float z) {
            if (!primed) {
                base[0] = gravity[0] = x; base[1] = gravity[1] = y; base[2] = gravity[2] = z;
                primed = true;
                return false;
            }
            float dx = x - base[0], dy = y - base[1], dz = z - base[2];
            base[0] += BASE_RATE * dx; base[1] += BASE_RATE * dy; base[2] += BASE_RATE * dz;
            gravity[0] += GRAVITY_RATE * (x - gravity[0]);
            gravity[1] += GRAVITY_RATE * (y - gravity[1]);
            gravity[2] += GRAVITY_RATE * (z - gravity[2]);

            boolean flat = Math.abs(gravity[2]) > FLAT_Z;   // face down or face up
            float minJolt = flat ? MIN_JOLT_FLAT : MIN_JOLT_HELD;
            float az = Math.abs(dz), side = Math.max(Math.abs(dx), Math.abs(dy));
            boolean spike = az > Math.max(minJolt, NOISE_FACTOR * noise) && az > SIDE_RATIO * side;
            if (!spike) noise = 0.98f * noise + 0.02f * Math.max(az, side);
            if (!spike || now - startedAt < SETTLE_MS || now - lastSpike < REFRACTORY_MS) {
                if (spike) lastSpike = now;
                return false;
            }
            lastSpike = now;
            long gap = now - lastTap;
            if (gap >= GAP_MIN_MS && gap <= GAP_MAX_MS) {
                lastTap = -10_000;
                return true;
            }
            lastTap = now;
            return false;
        }
    }
}
