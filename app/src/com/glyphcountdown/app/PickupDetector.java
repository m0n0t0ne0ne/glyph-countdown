package com.glyphcountdown.app;

/**
 * Pick up to dismiss: notices the phone being picked up off a table while the alarm rings.
 *
 * <p>When the alarm starts with the phone lying on a table, its angle is noted. Picking it up
 * tilts it (a face-down phone is turned over), so a lasting change of angle dismisses the alarm.
 * Knocks, the alarm's own vibration and a loud speaker shake the phone but don't tilt it, and
 * readings are smoothed so a single jolt can't either. If the alarm starts anywhere else (in a
 * pocket, in your hand), this stays off, so walking about with the phone doesn't dismiss it.
 *
 * <p>Plain Java: fed accelerometer readings (about 25 a second), so it can be tested off the phone.
 */
final class PickupDetector {
    private static final double TILT_DEG = 20;      // this far from where it lay counts as picked up
    private static final int HOLD_READINGS = 3;     // for this many readings in a row (~120 ms)
    private static final double SMOOTH = 0.25;      // per reading: steadies shaking, still follows a lift

    private boolean armed;
    private final double[] ref = new double[3], now = new double[3];
    private int over;

    /** The alarm started. {@code onTable}: lying on a table; {@code g}: its average reading there. */
    void arm(boolean onTable, double[] g) {
        armed = onTable && g != null && length(g) > 5;
        over = 0;
        if (!armed) return;
        for (int i = 0; i < 3; i++) ref[i] = now[i] = g[i];
    }

    void disarm() { armed = false; }

    boolean armed() { return armed; }

    /** Raw accelerometer reading in m/s². Returns true once the phone has been picked up. */
    boolean accel(double x, double y, double z) {
        if (!armed) return false;
        now[0] += SMOOTH * (x - now[0]);
        now[1] += SMOOTH * (y - now[1]);
        now[2] += SMOOTH * (z - now[2]);
        double cos = (now[0] * ref[0] + now[1] * ref[1] + now[2] * ref[2]) / (length(now) * length(ref) + 1e-9);
        double deg = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos))));
        over = deg > TILT_DEG ? over + 1 : 0;
        if (over < HOLD_READINGS) return false;
        armed = false;   // once
        return true;
    }

    private static double length(double[] v) {
        return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }
}
