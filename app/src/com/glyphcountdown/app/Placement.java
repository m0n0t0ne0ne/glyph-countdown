package com.glyphcountdown.app;

/**
 * Works out where the phone is from its motion and proximity sensors, to decide whether the Glyph
 * Matrix should light. A table is the only place a phone lies both flat and perfectly still;
 * a body (even sitting, with the phone nearly flat in a pocket) always moves a little: jitter,
 * or a slow drift in its angle as you breathe or shift.
 *
 * <ul>
 *   <li>{@link #TABLE_DOWN}: flat, screen down, still: the matrix faces up, so it lights.</li>
 *   <li>{@link #TABLE_UP}: flat, screen up, still: the matrix faces the table, so it stays dark.</li>
 *   <li>{@link #COVERED}: the front sensor is covered and it isn't on a table: pocket or bag, dark.</li>
 *   <li>{@link #OPEN}: anything else, such as in your hand: lights (unless the screen is on).</li>
 * </ul>
 * Plain Java: fed readings with timestamps, so it can be tested off the phone.
 */
final class Placement {
    static final int OPEN = 0, TABLE_DOWN = 1, TABLE_UP = 2, COVERED = 3;

    private static final double DOWN_XY = 0.9;       // face-down: within about 5° of flat (it rests on its screen)
    private static final double UP_XY = 1.5;         // face-up: about 9°, the camera bump tilts it a little
    private static final double FLAT_Z = 9.0;
    private static final double STILL_WOBBLE = 0.04, MOVING_WOBBLE = 0.09;  // m/s², quick jitter
    private static final double STILL_DRIFT = 0.05, MOVING_DRIFT = 0.12;    // m/s², slow change of angle
    private static final long WINDOW_MS = 3000;
    private static final long SETTLE_MS = 500, COVER_SETTLE_MS = 1000;  // ignore a hand brushing the sensor
    private static final int N = 512, SPIKE = 9;     // buffer size; readings in the knock filter
    private static final long FLIP_SETTLE_MS = 1000;    // after flip to start: the hand lets go, the phone rocks
    private static final long RING_SETTLE_MS = 300;     // after the alarm stops shaking it
    private static final int RING_AVERAGE = 25;          // while it rings, the angle is averaged over ~1 s
    // Readings are kept at about 25 a second whatever rate they arrive at: another listener on the
    // same sensor (double-tap to dismiss asks for the fastest rate) makes Android deliver them faster,
    // and the buffer must still hold the full 3 s window.
    private static final long MIN_GAP_MS = 35;

    private final long[] times = new long[N];
    private final double[] rx = new double[N], ry = new double[N], rz = new double[N];   // raw readings
    private final double[] gx = new double[N], gy = new double[N], gz = new double[N];   // de-spiked angle
    private final double[] mags = new double[N];
    private int head, count;
    private boolean near, still;
    private int state = OPEN, pending = OPEN;
    private long pendingSince;
    private long ignoreBefore;   // readings before this don't count towards stillness (settling, buzzing)
    private boolean ringing;     // the alarm is ringing: its sound and vibration shake the phone

    int state() { return state; }

    /**
     * Flip to start: the app has just seen the phone held face-down and flat, so take it as lying
     * face-down on a table straight away instead of waiting the usual 3 s to prove it is still.
     * The first second is ignored while the hand lets go and the phone settles; after that, readings
     * overrule this as usual: if it turns out to be moving, tilted or face-up, it changes.
     */
    void assumeFaceDown(long t) {
        head = count = 0;   // older readings may include the flip itself
        state = pending = TABLE_DOWN;
        still = true;
        ignoreBefore = t + FLIP_SETTLE_MS;
    }

    /**
     * The alarm starts (or stops) ringing. Its vibration, and a loud speaker, shake the phone on the
     * table, which isn't the phone being moved, so stillness is left as it was meanwhile. Being
     * picked up still shows, as a change in its angle (averaged over a second, so shaking doesn't jolt it).
     */
    void ringing(long t, boolean on) {
        if (ringing && !on) ignoreBefore = Math.max(ignoreBefore, t + RING_SETTLE_MS);
        ringing = on;
    }

    /** Whether the matrix should light here (before the screen-on rule). */
    static boolean lights(int state) { return state == OPEN || state == TABLE_DOWN; }

    /** Proximity reading. Returns true if the settled state changed. */
    boolean proximity(long t, boolean isNear) {
        near = isNear;
        return update(t);
    }

    /** Raw accelerometer reading in m/s². Returns true if the settled state changed. */
    boolean accel(long t, double x, double y, double z) {
        if (count > 0 && t - times[(head - 1 + N) % N] < MIN_GAP_MS) return update(t);
        times[head] = t; rx[head] = x; ry[head] = y; rz[head] = z;
        mags[head] = Math.sqrt(x * x + y * y + z * z);
        if (count < N) count++;
        // the angle uses the median of the last few readings, so a knock or tap can't jolt it
        int m = Math.min(SPIKE, count);
        gx[head] = recentMedian(rx, m); gy[head] = recentMedian(ry, m); gz[head] = recentMedian(rz, m);
        head = (head + 1) % N;
        judgeStillness(t);
        return update(t);
    }

    private double recentMedian(double[] a, int m) {
        double[] v = new double[m];
        for (int k = 0; k < m; k++) v[k] = a[(head - k + N) % N];
        return median(v, m);
    }

    /**
     * Still means both: little quick jitter (median deviation, so single knocks don't count) and
     * no slow drift in the phone's angle over 3 s (a body breathing or shifting always drifts).
     */
    private void judgeStillness(long t) {
        if (ringing) return;   // the alarm shaking the phone isn't movement
        double[] v = new double[count];
        double loX = 1e9, hiX = -1e9, loY = 1e9, hiY = -1e9, loZ = 1e9, hiZ = -1e9;
        int n = 0;
        long oldest = t;
        for (int k = 0; k < count; k++) {
            int i = (head - 1 - k + N) % N;
            if (t - times[i] > WINDOW_MS || times[i] < ignoreBefore) break;
            v[n++] = mags[i];
            loX = Math.min(loX, gx[i]); hiX = Math.max(hiX, gx[i]);
            loY = Math.min(loY, gy[i]); hiY = Math.max(hiY, gy[i]);
            loZ = Math.min(loZ, gz[i]); hiZ = Math.max(hiZ, gz[i]);
            oldest = times[i];
        }
        boolean warming = n < 20 || t - oldest < WINDOW_MS * 0.8;
        // a short window because readings were set aside (settling, buzzing) keeps what was judged
        // before, until enough new readings show it moving; otherwise a short window proves nothing
        boolean resumed = t - WINDOW_MS < ignoreBefore;
        if (warming && !resumed) { still = false; return; }
        if (n < 5) return;
        double med = median(v, n);
        for (int i = 0; i < n; i++) v[i] = Math.abs(v[i] - med);
        double wobble = 1.4826 * median(v, n);
        double drift = Math.max(hiX - loX, Math.max(hiY - loY, hiZ - loZ));
        if (still && (wobble > MOVING_WOBBLE || drift > MOVING_DRIFT)) still = false;
        else if (!still && !warming && wobble < STILL_WOBBLE && drift < STILL_DRIFT) still = true;
    }

    private double recentMean(double[] a, int m) {
        double sum = 0;
        for (int k = 0; k < m; k++) sum += a[(head - 1 - k + N) % N];
        return sum / m;
    }

    private static double median(double[] v, int n) {
        double[] c = java.util.Arrays.copyOf(v, n);
        java.util.Arrays.sort(c);
        return n % 2 == 1 ? c[n / 2] : (c[n / 2 - 1] + c[n / 2]) / 2;
    }

    private int raw() {
        if (count > 0) {
            int i = (head - 1 + N) % N;
            double ax = Math.abs(gx[i]), ay = Math.abs(gy[i]), z = gz[i];
            if (ringing) {   // shaking jolts single readings; their average still shows the angle
                int m = Math.min(RING_AVERAGE, count);
                ax = Math.abs(recentMean(rx, m)); ay = Math.abs(recentMean(ry, m)); z = recentMean(rz, m);
            }
            if (still && z < -FLAT_Z && ax < DOWN_XY && ay < DOWN_XY) return TABLE_DOWN;
            if (still && z > FLAT_Z && ax < UP_XY && ay < UP_XY) return TABLE_UP;
        }
        if (near) return COVERED;
        return OPEN;
    }

    private boolean update(long t) {
        int r = raw();
        if (r == state) { pending = r; return false; }
        if (r != pending) { pending = r; pendingSince = t; return false; }
        if (t - pendingSince < (r == COVERED ? COVER_SETTLE_MS : SETTLE_MS)) return false;
        state = r;
        return true;
    }
}
