package com.glyphcountdown.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity implements TimerState.Listener, DialView.Listener, SensorEventListener {
    // set from Palette in onCreate: BLACK is the background role, WHITE the foreground role
    private static int BLACK, WHITE, GREY, LIGHT, DARK, BORDER, CARD, RING;
    private static final int RED = 0xFFE8202A;
    private boolean appliedDark;

    private final TimerState st = TimerState.get();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private float dp;

    private DialView dial;
    private TextView[] unitButtons = new TextView[3];
    private TextView primary, hint, chipText, soundValue;
    private View soundRowView;
    private View chipDot;
    private SensorManager sensors;
    private Sensor gravity;
    private long faceDownSince;

    private final Runnable frame = new Runnable() {
        @Override public void run() {
            dial.invalidate(); // keeps the tick ring draining smoothly
            if (st.mode == TimerState.RUN) handler.postDelayed(this, 100);
        }
    };

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Palette pal = Palette.of(this);
        appliedDark = pal.dark;
        BLACK = pal.bg; WHITE = pal.fg; GREY = pal.grey; LIGHT = pal.light; DARK = pal.dim;
        BORDER = pal.border; CARD = pal.card; RING = pal.ring;
        Palette.applyBars(this, pal);
        dp = getResources().getDisplayMetrics().density;
        st.load(this);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        setContentView(buildUi());
        sensors = getSystemService(SensorManager.class);
        gravity = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY);
        if (gravity == null) gravity = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BLACK);

        final LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                android.graphics.Insets b = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                col.setPadding(px(24) + b.left, px(20) + b.top, px(24) + b.right, px(28) + b.bottom);
                return WindowInsets.CONSUMED;
            }
        });

        // header: "Timer" + Glyph Matrix chip
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Timer", 32, WHITE, Fonts.doto(this));
        title.setLetterSpacing(0.03f);
        header.addView(title, new LinearLayout.LayoutParams(0, px(44), 1));
        LinearLayout chip = new LinearLayout(this);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(px(12), px(6), px(12), px(6));
        chip.setBackground(pill(Color.TRANSPARENT, BORDER));
        chipDot = new View(this);
        chipDot.setBackground(circle(DARK));
        chip.addView(chipDot, new LinearLayout.LayoutParams(px(8), px(8)));
        chipText = text("GLYPH MATRIX", 11, LIGHT, Fonts.mono(this));
        chipText.setLetterSpacing(0.14f);
        LinearLayout.LayoutParams ctl = new LinearLayout.LayoutParams(-2, -2);
        ctl.leftMargin = px(8);
        chip.addView(chipText, ctl);
        header.addView(chip);
        GearIcon gear = new GearIcon(this);
        gear.setContentDescription("Settings");
        gear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new android.content.Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(px(44), px(44));
        gl.leftMargin = px(4);
        gl.rightMargin = -px(10);
        header.addView(gear, gl);
        col.addView(header, new LinearLayout.LayoutParams(-1, -2));

        // dial
        dial = new DialView(this);
        dial.setListener(this);
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int dialSize = Math.min(screenW - px(48), px(340));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dialSize, dialSize);
        dl.topMargin = px(36);
        col.addView(dial, dl);

        // HRS / MIN / SEC switch
        LinearLayout seg = new LinearLayout(this);
        seg.setPadding(px(4), px(4), px(4), px(4));
        seg.setBackground(pill(Color.TRANSPARENT, BORDER));
        String[] labels = {"HRS", "MIN", "SEC"};
        String[] spoken = {"Hours", "Minutes", "Seconds"};
        for (int i = 0; i < 3; i++) {
            final int u = i;
            TextView b = text(labels[i], 13, LIGHT, Fonts.monoBold(this));
            b.setLetterSpacing(0.15f);
            b.setGravity(Gravity.CENTER);
            b.setContentDescription(spoken[i]);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { onUnitPicked(u); }
            });
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(px(92), px(44));
            if (i > 0) bl.leftMargin = px(6);
            seg.addView(b, bl);
            unitButtons[i] = b;
        }
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.topMargin = px(32);
        col.addView(seg, sl);

        // alarm sound row
        LinearLayout soundRow = new LinearLayout(this);
        soundRow.setGravity(Gravity.CENTER_VERTICAL);
        soundRow.setPadding(px(18), 0, px(14), 0);
        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setCornerRadius(px(20));
        rowBg.setColor(CARD);
        rowBg.setStroke(px(1), BORDER);
        soundRow.setBackground(rowBg);
        soundRow.setClickable(true);
        soundRow.setFocusable(true);
        soundRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new android.content.Intent(MainActivity.this, SoundActivity.class));
            }
        });
        soundRow.addView(new SpeakerIcon(this), new LinearLayout.LayoutParams(px(22), px(22)));
        LinearLayout soundText = new LinearLayout(this);
        soundText.setOrientation(LinearLayout.VERTICAL);
        TextView soundLabel = text("ALARM SOUND", 10, GREY, Fonts.mono(this));
        soundLabel.setLetterSpacing(0.18f);
        soundText.addView(soundLabel);
        soundValue = text("", 15, WHITE, Fonts.monoBold(this));
        soundValue.setSingleLine(true);
        soundValue.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams svl = new LinearLayout.LayoutParams(-1, -2);
        svl.topMargin = px(4);
        soundText.addView(soundValue, svl);
        LinearLayout.LayoutParams stl = new LinearLayout.LayoutParams(0, -2, 1);
        stl.leftMargin = px(14);
        soundRow.addView(soundText, stl);
        soundRow.addView(text("›", 22, GREY, Fonts.mono(this)));
        soundRowView = soundRow;
        LinearLayout.LayoutParams srl = new LinearLayout.LayoutParams(-1, px(64));
        srl.topMargin = px(16);
        col.addView(soundRow, srl);

        col.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1)); // spacer

        // reset + start
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        ResetButton reset = new ResetButton(this);
        reset.setContentDescription("Reset timer");
        reset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (st.mode != TimerState.SET) TimerService.send(MainActivity.this, TimerService.ACTION_RESET);
            }
        });
        controls.addView(reset, new LinearLayout.LayoutParams(px(56), px(56)));
        primary = text("START", 15, BLACK, Fonts.monoBold(this));
        primary.setLetterSpacing(0.2f);
        primary.setGravity(Gravity.CENTER);
        primary.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onPrimary(); }
        });
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(px(220), px(56));
        pl.leftMargin = px(16);
        controls.addView(primary, pl);
        col.addView(controls, new LinearLayout.LayoutParams(-1, -2));

        hint = text("Flip face-down to start on the Glyph Matrix", 12, GREY, Fonts.mono(this));
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.topMargin = px(18);
        col.addView(hint, hl);
        return root;
    }

    // ---- actions ---------------------------------------------------------------------

    private void onPrimary() {
        switch (st.mode) {
            case TimerState.SET: startTimer(); break;
            case TimerState.RUN: TimerService.send(this, TimerService.ACTION_PAUSE); break;
            case TimerState.PAUSE: TimerService.send(this, TimerService.ACTION_RESUME); break;
            default: TimerService.send(this, TimerService.ACTION_RESET); break;
        }
    }

    private void startTimer() {
        if (st.setSeconds() <= 0) {
            dial.performHapticFeedback(HapticFeedbackConstants.REJECT);
            return;
        }
        st.save(this);
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        TimerService.send(this, TimerService.ACTION_START);
    }

    @Override
    public void onValueChanged(int unit, int value) {
        if (st.mode != TimerState.SET) return;
        st.set(unit, value);
        refresh();
    }

    @Override
    public void onUnitPicked(int unit) {
        if (st.mode != TimerState.SET) return;
        dial.setUnit(unit);
        refresh();
    }

    @Override
    public void onTimerChanged() { refresh(); }

    private void refresh() {
        int mode = st.mode;
        boolean setting = mode == TimerState.SET;
        for (int i = 0; i < 3; i++) {
            boolean sel = setting && dial.getUnit() == i;
            unitButtons[i].setBackground(pill(sel ? WHITE : Color.TRANSPARENT, 0));
            unitButtons[i].setTextColor(sel ? BLACK : setting ? LIGHT : DARK);
            unitButtons[i].setSelected(sel);
            unitButtons[i].setEnabled(setting);
        }
        String label = setting ? "START" : mode == TimerState.RUN ? "PAUSE" : mode == TimerState.PAUSE ? "RESUME" : "DISMISS";
        primary.setText(label);
        boolean done = mode == TimerState.DONE;
        primary.setBackground(pill(done ? RED : WHITE, 0));
        primary.setTextColor(done ? 0xFFFFFFFF : BLACK);
        hint.setVisibility(setting ? View.VISIBLE : View.INVISIBLE);
        String src = Sounds.selectedSource(this);
        String srcLabel = Sounds.SRC_NOTHING.equals(src) ? "Nothing" : "Alarm";
        android.text.SpannableString sv = new android.text.SpannableString(Sounds.selectedName(this) + "  · " + srcLabel);
        sv.setSpan(new android.text.style.ForegroundColorSpan(GREY), sv.length() - srcLabel.length() - 3, sv.length(), 0);
        soundValue.setText(sv);
        soundRowView.setContentDescription("Alarm sound: " + Sounds.selectedName(this) + ". Change");
        chipDot.setBackground(circle(st.matrixActive && !setting ? RED : DARK));
        dial.invalidate();
        handler.removeCallbacks(frame);
        if (mode == TimerState.RUN) handler.post(frame);
    }

    // ---- lifecycle -------------------------------------------------------------------

    @Override
    protected void onResume() {
        super.onResume();
        if (Palette.isDark(this) != appliedDark) { recreate(); return; } // theme changed in Settings
        st.addListener(this);
        if (gravity != null) sensors.registerListener(this, gravity, SensorManager.SENSOR_DELAY_UI);
        refresh();
    }

    @Override
    protected void onPause() {
        st.removeListener(this);
        sensors.unregisterListener(this);
        handler.removeCallbacks(frame);
        faceDownSince = 0;
        if (st.mode == TimerState.SET) {
            st.save(this);
            WidgetUpdater.updateAll(this); // widgets show the duration you just set
        }
        super.onPause();
    }

    // ---- flip face-down to start -----------------------------------------------------

    @Override
    public void onSensorChanged(SensorEvent e) {
        float x = e.values[0], y = e.values[1], z = e.values[2];
        boolean faceDown = z < -8.5f && Math.abs(x) < 2.5f && Math.abs(y) < 2.5f;
        if (!faceDown || st.mode != TimerState.SET || st.setSeconds() <= 0) {
            faceDownSince = 0;
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (faceDownSince == 0) {
            faceDownSince = now;
        } else if (now - faceDownSince > 800) {
            faceDownSince = 0;
            dial.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            startTimer();
        }
    }

    @Override public void onAccuracyChanged(Sensor s, int a) {}

    // ---- helpers ---------------------------------------------------------------------

    private int px(float v) { return Math.round(v * dp); }

    private TextView text(String s, float sp, int color, android.graphics.Typeface tf) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private GradientDrawable pill(int fill, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(px(999));
        d.setColor(fill);
        if (stroke != 0) d.setStroke(px(1), stroke);
        return d;
    }

    private GradientDrawable circle(int fill) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(fill);
        return d;
    }

    /** Outlined gear that opens Settings. */
    static class GearIcon extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        GearIcon(Context c) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2 * c.getResources().getDisplayMetrics().density);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(WHITE);
            setClickable(true);
            setFocusable(true);
        }
        @Override protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, u = getWidth() / 44f;
            c.drawCircle(cx, cy, 3.2f * u, p);
            c.drawCircle(cx, cy, 7f * u, p);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                float sx = (float) Math.sin(a), sy = (float) Math.cos(a);
                c.drawLine(cx + sx * 7f * u, cy + sy * 7f * u, cx + sx * 10f * u, cy + sy * 10f * u, p);
            }
        }
    }

    /** Small outlined speaker for the alarm sound row. */
    static class SpeakerIcon extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path body = new Path();
        private final RectF oval = new RectF();
        SpeakerIcon(Context c) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2 * c.getResources().getDisplayMetrics().density);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(WHITE);
        }
        @Override protected void onDraw(Canvas c) {
            float u = getWidth() / 24f;
            body.reset();
            body.moveTo(11 * u, 5 * u); body.lineTo(6 * u, 9 * u); body.lineTo(3 * u, 9 * u);
            body.lineTo(3 * u, 15 * u); body.lineTo(6 * u, 15 * u); body.lineTo(11 * u, 19 * u); body.close();
            c.drawPath(body, p);
            oval.set(10.5f * u, 8.5f * u, 17.5f * u, 15.5f * u);
            c.drawArc(oval, -50, 100, false, p);
            oval.set(9.5f * u, 5.5f * u, 21.5f * u, 18.5f * u);
            c.drawArc(oval, -50, 100, false, p);
        }
    }

    /** Round outlined button with a circular-arrow reset icon. */
    static class ResetButton extends View {
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF oval = new RectF();

        ResetButton(Context c) {
            super(c);
            float d = c.getResources().getDisplayMetrics().density;
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(d);
            ring.setColor(RING);
            icon.setStyle(Paint.Style.STROKE);
            icon.setStrokeWidth(2 * d);
            icon.setStrokeCap(Paint.Cap.ROUND);
            icon.setStrokeJoin(Paint.Join.ROUND);
            icon.setColor(WHITE);
            setClickable(true);
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            c.drawCircle(w / 2, h / 2, Math.min(w, h) / 2 - ring.getStrokeWidth(), ring);
            float s = Math.min(w, h) * 0.18f; // icon radius
            float cx = w / 2, cy = h / 2;
            oval.set(cx - s, cy - s, cx + s, cy + s);
            c.drawArc(oval, 200, 290, false, icon);
            // arrow head at the arc's start (pointing back toward the top-left)
            double a = Math.toRadians(200);
            float ax = cx + (float) Math.cos(a) * s, ay = cy + (float) Math.sin(a) * s;
            path.reset();
            path.moveTo(ax - s * 0.05f, ay - s * 0.75f);
            path.lineTo(ax, ay);
            path.lineTo(ax + s * 0.7f, ay - s * 0.15f);
            c.drawPath(path, icon);
        }
    }
}
