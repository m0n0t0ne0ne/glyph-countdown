package com.glyphcountdown.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Icon;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import com.nothing.ketchum.Common;
import com.nothing.ketchum.Glyph;
import com.nothing.ketchum.GlyphMatrixManager;

/** Runs the countdown, keeps the notification current and drives the Glyph Matrix. */
public class TimerService extends Service {
    public static final String ACTION_START = "com.glyphcountdown.app.START";
    public static final String ACTION_PAUSE = "com.glyphcountdown.app.PAUSE";
    public static final String ACTION_RESUME = "com.glyphcountdown.app.RESUME";
    public static final String ACTION_RESET = "com.glyphcountdown.app.RESET";
    /** Start, pause, resume or dismiss depending on the current state (widget taps). */
    public static final String ACTION_TOGGLE = "com.glyphcountdown.app.TOGGLE";
    /** Set on intents sent with startForegroundService / getForegroundService. */
    public static final String EXTRA_FG = "fg";

    private static final String CH_RUN = "timer_running";
    private static final String CH_DONE = "timer_done";
    private static final int NOTIF_ID = 25;
    private static final long ALARM_SOUND_MAX_MS = 3 * 60_000L;

    private final TimerState st = TimerState.get();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wakeLock;
    private GlyphMatrixManager gm;
    private boolean gmReady;
    private int matrixSize = MatrixRenderer.SIZE; // 25 on Phone (3), 13 on Phone (4a) Pro
    private Ringtone ringtone;       // fallback if the sound can't be opened with MediaPlayer
    private MediaPlayer player;      // alarm sound; its position paces the synced animation
    private Vibrator vibrator;
    private long doneAt;

    // ---- time's-up animation and sound sync
    private static final long FRAME_MS = 40;  // 25 frames a second, so the stutter reads cleanly
    private String anim = MorphEngine.DEFAULT;
    private volatile SoundSync.Profile profile;
    private String profileUri;
    private int lastPos;
    private long soundRepeats;

    // ---- double-tap the back to dismiss the alarm
    private BackTapDetector backTap;

    // ---- "only when screen is off": the matrix goes dark while the screen is on
    private boolean screenOn;
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            screenOn = Intent.ACTION_SCREEN_ON.equals(i.getAction());
            pushMatrix();
        }
    };

    // ---- where the phone is: the matrix lights face-down on a table or in your hand with the screen
    //      off, and stays dark face-up on a table or covered (pocket or bag). See Placement.
    private SensorManager sensors;
    private final Placement placement = new Placement();
    private boolean sensorsOn;
    private final SensorEventListener placementListener = new SensorEventListener() {
        @Override public void onSensorChanged(SensorEvent e) {
            long t = SystemClock.elapsedRealtime();
            boolean changed;
            if (e.sensor.getType() == Sensor.TYPE_PROXIMITY) {
                float max = e.sensor.getMaximumRange();
                changed = placement.proximity(t, e.values[0] < Math.min(max, 5f));
            } else {
                changed = placement.accel(t, e.values[0], e.values[1], e.values[2]);
            }
            if (changed) pushMatrix();
        }
        @Override public void onAccuracyChanged(Sensor s, int a) {}
    };

    private void startPlacementSensors() {
        if (sensorsOn) return;
        sensors = getSystemService(SensorManager.class);
        Sensor prox = sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        Sensor acc = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (acc != null) sensors.registerListener(placementListener, acc, 40_000);   // 25 readings a second
        if (prox != null) sensors.registerListener(placementListener, prox, SensorManager.SENSOR_DELAY_NORMAL);
        sensorsOn = true;
    }

    private void stopPlacementSensors() {
        if (sensorsOn && sensors != null) sensors.unregisterListener(placementListener);
        sensorsOn = false;
    }

    public static void send(Context c, String action) {
        Intent i = new Intent(c, TimerService.class).setAction(action);
        if (ACTION_START.equals(action)) c.startForegroundService(i.putExtra(EXTRA_FG, true));
        else c.startService(i);
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        st.load(this);
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel run = new NotificationChannel(CH_RUN, "Running timer", NotificationManager.IMPORTANCE_LOW);
        run.setShowBadge(false);
        NotificationChannel done = new NotificationChannel(CH_DONE, "Timer finished", NotificationManager.IMPORTANCE_HIGH);
        done.setSound(null, null); // the service plays the alarm itself
        done.enableVibration(false);
        nm.createNotificationChannel(run);
        nm.createNotificationChannel(done);
        screenOn = getSystemService(PowerManager.class).isInteractive();
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (intent != null && intent.getBooleanExtra(EXTRA_FG, false)) {
            // started with startForegroundService: Android requires startForeground() right away
            startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        }
        if (ACTION_TOGGLE.equals(action)) {
            if (st.mode == TimerState.SET) action = ACTION_START;
            else if (st.mode == TimerState.RUN) action = ACTION_PAUSE;
            else if (st.mode == TimerState.PAUSE) action = ACTION_RESUME;
            else action = ACTION_RESET;
        }
        if (ACTION_START.equals(action)) start();
        else if (ACTION_PAUSE.equals(action)) pause();
        else if (ACTION_RESUME.equals(action)) resume();
        else if (ACTION_RESET.equals(action)) reset();
        else if (st.mode == TimerState.SET) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); }
        return START_NOT_STICKY;
    }

    // ---- timer control ---------------------------------------------------------------

    private void start() {
        int secs = st.setSeconds();
        if (secs <= 0) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return; }
        stopAlarm();
        if (backTap != null) backTap.stop();
        st.totalMs = secs * 1000L;
        st.endAtElapsed = SystemClock.elapsedRealtime() + st.totalMs;
        st.mode = TimerState.RUN;
        anim = Prefs.anim(this);
        prepareSync(); // analyse the alarm sound now, so the animation is in step from the first frame
        startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        acquireWake();
        initMatrix();
        startPlacementSensors();
        handler.removeCallbacks(tick); handler.removeCallbacks(blink); handler.removeCallbacks(animTick);
        handler.post(tick);
        st.notifyChanged();
        WidgetUpdater.updateAll(this);
    }

    private void pause() {
        if (st.mode != TimerState.RUN) return;
        st.remainingMs = st.remainingNow();
        st.mode = TimerState.PAUSE;
        handler.removeCallbacks(tick); handler.removeCallbacks(blink); handler.removeCallbacks(animTick);
        releaseWake();
        pushMatrix();
        postNotification();
        st.notifyChanged();
        WidgetUpdater.updateAll(this);
    }

    private void resume() {
        if (st.mode != TimerState.PAUSE) return;
        st.endAtElapsed = SystemClock.elapsedRealtime() + st.remainingMs;
        st.mode = TimerState.RUN;
        acquireWake();
        handler.removeCallbacks(tick); handler.removeCallbacks(blink); handler.removeCallbacks(animTick);
        handler.post(tick);
        postNotification();
        st.notifyChanged();
        WidgetUpdater.updateAll(this);
    }

    private void reset() {
        handler.removeCallbacksAndMessages(null);
        if (backTap != null) backTap.stop();
        stopPlacementSensors();
        stopAlarm();
        releaseWake();
        closeMatrix();
        st.mode = TimerState.SET;
        st.blink = true;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
        st.notifyChanged();
        WidgetUpdater.updateAll(this);
    }

    private void finish() {
        st.mode = TimerState.DONE;
        st.blink = true;
        doneAt = SystemClock.elapsedRealtime();
        anim = Prefs.anim(this);
        prepareSync();
        startAlarm();
        if (Prefs.get(this, Prefs.TAP_DISMISS)) {
            if (backTap == null) backTap = new BackTapDetector(this, new BackTapDetector.Listener() {
                @Override public void onDoubleTap() {
                    handler.post(new Runnable() {
                        @Override public void run() { if (st.mode == TimerState.DONE) reset(); }
                    });
                }
            });
            backTap.start();
        }
        postNotification();
        handler.removeCallbacks(tick); handler.removeCallbacks(blink); handler.removeCallbacks(animTick);
        handler.post(blink);
        if (!MorphEngine.DEFAULT.equals(anim)) handler.post(animTick);
        st.notifyChanged();
        WidgetUpdater.updateAll(this);
    }

    private final Runnable tick = new Runnable() {
        private long lastSecond = -1;
        @Override public void run() {
            if (st.mode != TimerState.RUN) return;
            long rem = st.remainingNow();
            if (rem <= 0) { finish(); return; }
            pushMatrix(); // the ring drains smoothly, so refresh every tick
            long sec = (rem + 999) / 1000;
            if (sec != lastSecond) {
                lastSecond = sec;
                st.notifyChanged();
                WidgetUpdater.updateAll(TimerService.this);
            }
            // the styles move smoothly (waves, orbits, blinking), so they refresh faster than the ring
            handler.postDelayed(this, CountdownStyles.RING.equals(Prefs.style(TimerService.this)) ? 200 : 100);
        }
    };

    private final Runnable blink = new Runnable() {
        @Override public void run() {
            if (st.mode != TimerState.DONE) return;
            st.blink = !st.blink;
            if (MorphEngine.DEFAULT.equals(anim)) pushMatrix(); // otherwise animTick draws the matrix
            st.notifyChanged();
            WidgetUpdater.updateAll(TimerService.this); // red pulses on the widgets
            if ((ringtone != null || player != null) && SystemClock.elapsedRealtime() - doneAt > ALARM_SOUND_MAX_MS) stopAlarm();
            handler.postDelayed(this, 500);
        }
    };

    private final Runnable animTick = new Runnable() {
        @Override public void run() {
            if (st.mode != TimerState.DONE || MorphEngine.DEFAULT.equals(anim)) return;
            pushMatrix();
            handler.postDelayed(this, FRAME_MS);
        }
    };

    // ---- Glyph Matrix ----------------------------------------------------------------

    private void initMatrix() {
        if (gm != null) { pushMatrix(); return; }
        try {
            gm = GlyphMatrixManager.getInstance(getApplicationContext());
            gm.init(new GlyphMatrixManager.Callback() {
                @Override public void onServiceConnected(ComponentName name) {
                    try {
                        // Phone (4a) Pro has a 13×13 matrix and its own device id; Phone (3) is 25×25
                        int len = MatrixRenderer.SIZE;
                        try { len = Common.getDeviceMatrixLength(); } catch (Throwable ignored) {}
                        boolean pro4a = false;
                        try { pro4a = Common.is25111p(); } catch (Throwable ignored) {}
                        if (pro4a || len == MatrixRenderer.SIZE_SMALL) {
                            matrixSize = MatrixRenderer.SIZE_SMALL;
                            gm.register(Glyph.DEVICE_25111p);
                        } else {
                            matrixSize = MatrixRenderer.SIZE;
                            gm.register(Glyph.DEVICE_23112);
                        }
                        try { gm.setGlyphMatrixTimeout(false); } catch (Throwable ignored) {}
                        gmReady = true;
                        st.matrixActive = true;
                        handler.post(new Runnable() {
                            @Override public void run() { pushMatrix(); st.notifyChanged(); }
                        });
                    } catch (Throwable t) {
                        gmReady = false;
                    }
                }
                @Override public void onServiceDisconnected(ComponentName name) {
                    gmReady = false;
                    st.matrixActive = false;
                    st.notifyChanged();
                }
            });
        } catch (Throwable t) {
            gm = null; // not a Nothing phone with a Glyph Matrix: the timer still works
        }
    }

    private void pushMatrix() {
        if (!gmReady || gm == null) return;
        try {
            gm.setAppMatrixFrame(matrixFrame());
        } catch (Throwable ignored) {}
    }

    private int[] matrixFrame() {
        int n = matrixSize;
        // face-down on a table counts as screen off: nobody can see the screen (flip to start)
        int where = placement.state();
        boolean hidden = screenOn && where != Placement.TABLE_DOWN && Prefs.get(this, Prefs.SCREEN_OFF_ONLY);
        if (!Placement.lights(where) || hidden) return new int[n * n];
        if (st.mode == TimerState.DONE && !MorphEngine.DEFAULT.equals(anim)) return animFrame(n);
        String style = Prefs.style(this);
        if ((st.mode == TimerState.RUN || st.mode == TimerState.PAUSE) && !CountdownStyles.RING.equals(style)) {
            CountdownStyles.Moment m = new CountdownStyles.Moment();
            m.remExact = st.remainingNow() / 1000.0;
            m.frac = st.totalMs > 0 ? Math.min(1, m.remExact * 1000 / st.totalMs) : 0;
            m.wallSec = SystemClock.elapsedRealtime() / 1000.0;
            m.paused = st.mode == TimerState.PAUSE;
            double[] v = CountdownStyles.draw(style, n, m);
            int[] px = new int[n * n];
            for (int i = 0; i < px.length; i++) px[i] = SoundSync.toLed(v[i]);
            return px;
        }
        return MatrixRenderer.render(st, n);
    }

    /** One frame of the time's-up animation. Synced: paced by the sound, brighter on each note. */
    private int[] animFrame(int n) {
        SoundSync.Profile prof = profile;
        boolean sync = prof != null && player != null && Prefs.get(this, Prefs.SYNC);
        int pos = -1;
        if (sync) {
            try { pos = player.getCurrentPosition(); } catch (Throwable ignored) {}
            if (pos >= 0) {
                if (pos + 300 < lastPos) soundRepeats++; // the sound looped back to the start
                lastPos = pos;
            } else {
                sync = false;
            }
        }
        return SoundSync.frame(anim, n, sync ? prof : null, sync, Prefs.get(this, Prefs.DIM),
                Prefs.dimDepth(this) / 100.0, soundRepeats, Math.max(0, pos),
                SystemClock.elapsedRealtime() - doneAt);
    }

    /** Start analysing the chosen alarm sound in the background (once per sound). */
    private void prepareSync() {
        if (MorphEngine.DEFAULT.equals(anim) || !Prefs.get(this, Prefs.SYNC)) return;
        final Uri uri = Sounds.alarmUri(this);
        if (uri == null) return;
        final String key = uri.toString();
        if (key.equals(profileUri) && profile != null) return;
        profileUri = key;
        profile = SoundSync.cached(uri);
        if (profile != null) return;
        final Context app = getApplicationContext();
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                final SoundSync.Profile p = SoundSync.analyze(app, uri);
                handler.post(new Runnable() {
                    @Override public void run() { if (key.equals(profileUri)) profile = p; }
                });
            }
        }, "sound-sync");
        t.setDaemon(true);
        t.start();
    }

    private void closeMatrix() {
        if (gm == null) return;
        try { if (gmReady) gm.closeAppMatrix(); } catch (Throwable ignored) {}
        try { gm.unInit(); } catch (Throwable ignored) {}
        gm = null;
        gmReady = false;
        st.matrixActive = false;
    }

    // ---- alarm -----------------------------------------------------------------------

    private void startAlarm() {
        lastPos = 0;
        soundRepeats = 0;
        try {
            MediaPlayer mp = new MediaPlayer();
            try {
                mp.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                mp.setDataSource(this, Sounds.alarmUri(this)); // the sound chosen in the picker
                mp.setLooping(true);
                mp.prepare();
                mp.start();
                player = mp;
            } catch (Throwable t) {
                mp.release();
            }
        } catch (Throwable ignored) {}
        if (player == null) {
            try {
                ringtone = Sounds.ringtone(this, null);
                if (ringtone == null) ringtone = Sounds.ringtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
                if (ringtone != null) {
                    ringtone.setLooping(true);
                    ringtone.play();
                }
            } catch (Throwable ignored) {}
        }
        if (!Prefs.get(this, Prefs.VIBRATE)) return;
        try {
            vibrator = getSystemService(VibratorManager.class).getDefaultVibrator();
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 400, 300, 400, 900}, 0),
                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM));
        } catch (Throwable ignored) {}
    }

    private void stopAlarm() {
        try { if (ringtone != null) ringtone.stop(); } catch (Throwable ignored) {}
        ringtone = null;
        try { if (player != null) { player.stop(); player.release(); } } catch (Throwable ignored) {}
        player = null;
        try { if (vibrator != null) vibrator.cancel(); } catch (Throwable ignored) {}
        vibrator = null;
    }

    // ---- wake lock -------------------------------------------------------------------

    private void acquireWake() {
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager.class)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GlyphCountdown:timer");
            wakeLock.setReferenceCounted(false);
        }
        long rem = st.remainingNow();
        wakeLock.acquire(rem + 10 * 60_000L);
    }

    private void releaseWake() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }

    // ---- notification ----------------------------------------------------------------

    private void postNotification() {
        getSystemService(NotificationManager.class).notify(NOTIF_ID, buildNotification());
    }

    private Notification buildNotification() {
        boolean done = st.mode == TimerState.DONE;
        Notification.Builder b = new Notification.Builder(this, done ? CH_DONE : CH_RUN)
                .setSmallIcon(smallIcon())
                .setColor(0xFFE8202A)
                .setOnlyAlertOnce(!done)
                .setShowWhen(false)
                .setContentIntent(PendingIntent.getActivity(this, 0,
                        new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));

        if (st.mode == TimerState.RUN) {
            b.setContentTitle("Timer running")
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setShowWhen(true)
                    .setWhen(System.currentTimeMillis() + st.remainingNow())
                    .setOngoing(true)
                    .addAction(action("Pause", ACTION_PAUSE, 1))
                    .addAction(action("Reset", ACTION_RESET, 2));
        } else if (st.mode == TimerState.PAUSE) {
            b.setContentTitle("Timer paused")
                    .setContentText(format(st.displaySeconds()) + " left")
                    .setOngoing(true)
                    .addAction(action("Resume", ACTION_RESUME, 3))
                    .addAction(action("Reset", ACTION_RESET, 2));
        } else if (st.mode == TimerState.SET) {
            b.setContentTitle("Timer");
        } else {
            b.setContentTitle("Time's up")
                    .setContentText(format(st.totalMs / 1000) + " timer finished")
                    .setCategory(Notification.CATEGORY_ALARM)
                    .setOngoing(true)
                    .addAction(action("Dismiss", ACTION_RESET, 4));
        }
        return b.build();
    }

    private Notification.Action action(String label, String act, int req) {
        PendingIntent pi = PendingIntent.getService(this, req,
                new Intent(this, TimerService.class).setAction(act),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Action.Builder(smallIcon(), label, pi).build();
    }

    private Icon cachedIcon;
    private Icon smallIcon() {
        if (cachedIcon != null) return cachedIcon;
        int s = 96;
        Bitmap bm = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bm);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFFFFFFF);
        // ring of dots with one solid dot: a tiny version of the dial
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            float x = (float) (s / 2 + Math.sin(a) * s * 0.38);
            float y = (float) (s / 2 - Math.cos(a) * s * 0.38);
            cv.drawCircle(x, y, s * 0.055f, p);
        }
        cv.drawCircle(s / 2f, s / 2f, s * 0.16f, p);
        cachedIcon = Icon.createWithBitmap(bm);
        return cachedIcon;
    }

    static String format(long secs) {
        long h = secs / 3600, m = (secs % 3600) / 60, s = secs % 60;
        return (h > 0 ? h + ":" + two(m) : String.valueOf(m)) + ":" + two(s);
    }

    private static String two(long n) { return n < 10 ? "0" + n : String.valueOf(n); }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        try { unregisterReceiver(screenReceiver); } catch (Throwable ignored) {}
        if (backTap != null) backTap.stop();
        stopPlacementSensors();
        stopAlarm();
        releaseWake();
        closeMatrix();
        super.onDestroy();
    }
}
