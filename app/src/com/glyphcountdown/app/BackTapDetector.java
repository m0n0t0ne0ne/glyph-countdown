package com.glyphcountdown.app;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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
    private static final float MIN_JOLT_FLAT = 0.8f;   // lying flat: recorded table taps peak at 2.4-5.7, a still table under 0.1
    private static final float FLAT_Z = 9.0f;          // gravity this much along the screen axis: flat (about 25°)
    private static final float NOISE_FACTOR = 5f;      // a tap must stand this far above background
    private static final float SIDE_RATIO = 1.5f;      // mostly through the screen, not sideways
    private static final float BASE_RATE = 0.08f;      // baseline follows within ~30 ms: removes gravity and slow motion
    private static final float GRAVITY_RATE = 0.02f;   // slow average: which way up the phone is
    private static final long REFRACTORY_MS = 100;     // one tap rings for a few tens of ms
    private static final long GAP_MIN_MS = 120, GAP_MAX_MS = 600;
    private static final long SETTLE_MS = 500;

    private final Context app;
    private final SensorManager sensors;
    private final Listener listener;
    private boolean running;
    private HandlerThread thread;
    private final Core core = new Core();

    BackTapDetector(Context c, Listener listener) {
        this.app = c;
        this.sensors = c.getSystemService(SensorManager.class);
        this.listener = listener;
    }

    // ---- "Record taps" (troubleshooting): every reading and the detector's view of it, saved to
    //      Downloads as CSV when the alarm stops, so detection can be tuned on real taps.
    private static final int REC_MAX = 60 * 500;   // about a minute at the fastest rate
    private Recording rec;

    /** buzzFrom: when the alarm started vibrating with {@code buzz} (-1 if it doesn't vibrate). */
    void start(long buzzFrom, long[] buzz, boolean record, String note) {
        if (running || sensors == null) return;
        Sensor s = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (s == null) return;
        core.reset(SystemClock.elapsedRealtime());
        if (buzzFrom >= 0) core.buzz(buzzFrom, buzz);
        rec = record ? new Recording(s, note, core.startedAt) : null;
        thread = new HandlerThread("back-tap", android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY);
        thread.start();
        sensors.registerListener(this, s, SensorManager.SENSOR_DELAY_FASTEST, new Handler(thread.getLooper()));
        running = true;
    }

    /** The alarm's vibration stopped while still listening for taps. */
    void buzzStopped() { core.buzzStopped(); }

    void stop() {
        if (running) sensors.unregisterListener(this);
        final HandlerThread t = thread;
        thread = null;
        running = false;
        final Recording r = rec;
        rec = null;
        if (t == null) return;
        if (r == null) { t.quitSafely(); return; }
        // save on the back-tap thread, after any reading still being handled, then let it end
        new Handler(t.getLooper()).post(new Runnable() {
            @Override public void run() { r.save(app); t.quitSafely(); }
        });
    }

    /** On the back-tap thread. The listener is called from here too. */
    @Override public void onSensorChanged(SensorEvent e) {
        long measuredAt = e.timestamp / 1_000_000;   // ns since boot, the same clock as elapsedRealtime
        boolean tapped = core.accel(measuredAt, e.values[0], e.values[1], e.values[2]);
        Recording r = rec;
        if (r != null) r.add(e.timestamp, SystemClock.elapsedRealtimeNanos(), e.values, core, tapped);
        if (tapped) listener.onDoubleTap();
    }

    @Override public void onAccuracyChanged(Sensor s, int a) {}

    /** Readings kept in memory while the alarm rings, written out as CSV when it stops. */
    private static final class Recording {
        final String header;
        final long[] t = new long[REC_MAX], got = new long[REC_MAX];
        final float[] x = new float[REC_MAX], y = new float[REC_MAX], z = new float[REC_MAX];
        final float[] jolt = new float[REC_MAX], noise = new float[REC_MAX], need = new float[REC_MAX];
        final byte[] flags = new byte[REC_MAX];
        int n;

        Recording(Sensor s, String note, long startedAt) {
            header = "# Glyph Countdown tap recording\n"
                    + "# sensor=" + s.getName() + " vendor=" + s.getVendor() + " minDelayUs=" + s.getMinDelay()
                    + " fifo=" + s.getFifoMaxEventCount() + " device=" + android.os.Build.MODEL + "\n"
                    + "# " + note + " startedAtMs=" + startedAt + "\n"
                    + "# flags: 1=flat 2=spike 4=tap-counted 8=double-tap\n"
                    + "t_ns,delivered_ns,x,y,z,jolt,noise,needed,flags\n";
        }

        void add(long ts, long gotNs, float[] v, Core c, boolean tapped) {
            if (n >= REC_MAX) return;
            t[n] = ts; got[n] = gotNs; x[n] = v[0]; y[n] = v[1]; z[n] = v[2];
            jolt[n] = c.lastJolt; noise[n] = c.noise; need[n] = c.lastNeeded;
            flags[n] = (byte) ((c.lastFlat ? 1 : 0) | (c.lastSpikeSeen ? 2 : 0) | (c.lastCounted ? 4 : 0) | (tapped ? 8 : 0));
            n++;
        }

        void save(Context c) {
            if (n == 0) return;
            try {
                String name = "glyph-taps-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".csv";
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) return;
                OutputStream os = c.getContentResolver().openOutputStream(uri);
                if (os == null) return;
                try {
                    StringBuilder b = new StringBuilder(header);
                    for (int i = 0; i < n; i++) {
                        b.append(t[i]).append(',').append(got[i]).append(',')
                                .append(x[i]).append(',').append(y[i]).append(',').append(z[i]).append(',')
                                .append(jolt[i]).append(',').append(noise[i]).append(',').append(need[i]).append(',')
                                .append(flags[i]).append('\n');
                        if (b.length() > 64_000) { os.write(b.toString().getBytes("UTF-8")); b.setLength(0); }
                    }
                    os.write(b.toString().getBytes("UTF-8"));
                } finally {
                    os.close();
                }
            } catch (Throwable ignored) {}
        }
    }

    /** The detection itself, in plain Java so it can be fed recorded or simulated readings. */
    static final class Core {
        private final float[] base = new float[3], gravity = new float[3];
        private boolean primed;
        private float noise = 0.3f;
        private long startedAt, lastTap = -10_000, lastSpike = -10_000;
        // the last reading as the detector saw it (for "Record taps")
        float lastJolt, lastNeeded;
        boolean lastFlat, lastSpikeSeen, lastCounted;

        // The alarm's vibration pattern, so the jolt of its motor starting or stopping is never taken
        // for a tap (lying flat, any sharp jolt counts). Times in ms on the same clock as readings.
        private volatile long buzzFrom = -1;   // set from the main thread, read on the sensor thread
        private volatile long[] buzzPattern;
        private static final long BUZZ_EDGE_MS = 60;

        /** The alarm started vibrating at {@code from} with this repeating waveform (off, on, off, on…). */
        void buzz(long from, long[] pattern) { buzzPattern = pattern; buzzFrom = from; }

        /** The vibration stopped (the alarm's 3-minute limit): nothing to set aside any more. */
        void buzzStopped() { buzzFrom = -1; }

        private boolean nearBuzzEdge(long now) {
            long buzzFrom = this.buzzFrom;
            long[] buzzPattern = this.buzzPattern;
            if (buzzFrom < 0 || buzzPattern == null || now < buzzFrom - BUZZ_EDGE_MS) return false;
            long cycle = 0;
            for (long d : buzzPattern) cycle += d;
            if (cycle <= 0) return false;
            long into = (now - buzzFrom) % cycle, edge = 0;
            if (into < BUZZ_EDGE_MS || cycle - into < BUZZ_EDGE_MS) return true;
            for (long d : buzzPattern) {
                edge += d;
                if (Math.abs(into - edge) < BUZZ_EDGE_MS) return true;
            }
            return false;
        }

        void reset(long now) {
            buzzFrom = -1;
            buzzPattern = null;
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
            // In the hand a tap pushes the phone through the screen. Lying on a table, the table
            // stops that, and the tap comes out as a sharp jolt along the phone instead (recorded:
            // 2.5-5.5 m/s² along its length, ~0.1 through the screen), so any direction counts.
            float jolt = flat ? (float) Math.sqrt(dx * dx + dy * dy + dz * dz) : az;
            float background = flat ? jolt : Math.max(az, side);
            float needed = Math.max(minJolt, NOISE_FACTOR * noise);
            boolean spike = jolt > needed && (flat || az > SIDE_RATIO * side);
            lastJolt = jolt; lastNeeded = needed; lastFlat = flat; lastSpikeSeen = spike; lastCounted = false;
            // a tap rings on for tens of ms: that's the tap, not background movement
            if (!spike && now - lastSpike >= REFRACTORY_MS) noise = 0.98f * noise + 0.02f * background;
            if (spike && nearBuzzEdge(now)) return false;   // the alarm's motor starting or stopping
            if (!spike || now - startedAt < SETTLE_MS || now - lastSpike < REFRACTORY_MS) {
                if (spike) lastSpike = now;
                return false;
            }
            lastSpike = now;
            lastCounted = true;
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
