package com.glyphcountdown.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The timer screen. The glyph face (a live preview of the Glyph Matrix) is the dial. Below it one
 * tall chip shows HR / MIN / SEC: tap a unit to pick it for the dial, tap it again to type a time.
 * Typing works like Google Clock: digits fill in from the right (23 → 23 s, 2315 → 23 min 15 s,
 * 7800 → 78 min, rolled over to 1 h 18 min). One row of chips opens the face picker, the time's-up
 * alert and Settings. Reset, Start and 00 sit at the bottom, where the thumb is.
 */
public class MainActivity extends Activity implements TimerState.Listener, DialView.Listener, SensorEventListener {
    // set from Palette in onCreate: BLACK is the background role, WHITE the foreground role
    private static int BLACK, WHITE, GREY, BORDER;
    private int SUB, FAINT, CHIP, KEY, KEY_PRESS, OFF;
    private static final int RED = 0xFFE8202A;
    private boolean appliedDark;

    private final TimerState st = TimerState.get();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private float dp;
    private int screenW;

    private DialView dial;
    private TextView status, interp, primary, zero, hint, faceLabel, alertLabel;
    private LinearLayout bigChip, chipRow, controls, keypad;
    private final TextView[] segDigits = new TextView[3];
    private final TextView[] segUnits = new TextView[3];
    private final LinearLayout[] segs = new LinearLayout[3];
    private TextView setKey, key0, key00;
    private ResetButton reset;
    private int bottomInset;

    // typing: digits fill in from the right into HH MM SS, at most six
    private boolean typing;
    private String typed = "";

    private SensorManager sensors;
    private Sensor gravity;
    private long faceDownSince;
    private static final long FLIP_HOLD_MS = 500;   // face-down this long starts the timer

    private final Runnable frame = new Runnable() {
        @Override public void run() {
            showTime();
            dial.invalidate(); // keeps the ring and the face draining smoothly
            if (st.mode == TimerState.RUN || st.mode == TimerState.DONE) handler.postDelayed(this, 100);
        }
    };

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Palette pal = Palette.of(this);
        appliedDark = pal.dark;
        BLACK = pal.bg; WHITE = pal.fg; GREY = pal.grey; BORDER = pal.border;
        SUB = pal.dark ? 0xFFA3A9AE : 0xFF4F555A;
        FAINT = pal.dark ? 0xFF666D72 : 0xFF868C91;
        CHIP = pal.dark ? 0xFF0B0D0E : 0xFFFFFFFF;
        KEY = pal.dark ? 0xFF1E2225 : 0xFFFFFFFF;
        KEY_PRESS = pal.dark ? 0xFF2C3236 : 0xFFD3D7DA;
        OFF = pal.dark ? 0xFF4A5055 : 0xFFB8BDC1;
        Palette.applyBars(this, pal);
        dp = getResources().getDisplayMetrics().density;
        screenW = getResources().getDisplayMetrics().widthPixels;
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

        // header: just the title
        TextView title = text("Timer", 32, WHITE, Fonts.doto(this));
        title.setLetterSpacing(0.03f);
        col.addView(title, new LinearLayout.LayoutParams(-1, px(44)));

        // the face is the dial
        dial = new DialView(this);
        dial.setListener(this);
        int size = dialSize(false);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(size, size);
        dl.topMargin = px(24);
        col.addView(dial, dl);

        // REMAINING / PAUSED / TIME'S UP while it runs
        status = text("", 12, SUB, Fonts.monoBold(this));
        status.setLetterSpacing(0.2f);
        status.setGravity(Gravity.CENTER);
        col.addView(status, new LinearLayout.LayoutParams(-1, px(32)));

        // the time: one tall chip, HR / MIN / SEC
        bigChip = new LinearLayout(this);
        bigChip.setPadding(px(6), px(6), px(6), px(6));
        String[] units = {"HR", "MIN", "SEC"};
        String[] spoken = {"Hours", "Minutes", "Seconds"};
        for (int i = 0; i < 3; i++) {
            final int u = i;
            LinearLayout s = new LinearLayout(this);
            s.setOrientation(LinearLayout.VERTICAL);
            s.setGravity(Gravity.CENTER);
            s.setClickable(true);
            s.setFocusable(true);
            s.setContentDescription(spoken[i]);
            s.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { onSegment(u); }
            });
            TextView d = text("00", 48, WHITE, Fonts.doto(this));
            d.setGravity(Gravity.CENTER);
            s.addView(d, new LinearLayout.LayoutParams(-2, -2));
            TextView un = text(units[i], 12, SUB, Fonts.monoBold(this));
            un.setLetterSpacing(0.15f);
            LinearLayout.LayoutParams ul = new LinearLayout.LayoutParams(-2, -2);
            ul.topMargin = px(8);
            s.addView(un, ul);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, -1, 1);
            if (i > 0) sl.leftMargin = px(4);
            bigChip.addView(s, sl);
            segs[i] = s; segDigits[i] = d; segUnits[i] = un;
        }
        col.addView(bigChip, new LinearLayout.LayoutParams(-1, px(104)));

        // while typing: how the digits were read
        interp = text("", 12, WHITE, Fonts.mono(this));
        interp.setLetterSpacing(0.08f);
        interp.setGravity(Gravity.CENTER);
        interp.setSingleLine(true);
        interp.setEllipsize(TextUtils.TruncateAt.END);
        interp.setVisibility(View.GONE);
        col.addView(interp, new LinearLayout.LayoutParams(-1, px(32)));

        // one row of chips: face, alert, settings
        chipRow = new LinearLayout(this);
        chipRow.setGravity(Gravity.CENTER_VERTICAL);
        // the face name is short, so its chip fits its label; the alert chip takes the rest for the sound's name
        faceLabel = chip(chipRow, new FaceIcon(this), false, new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, CountdownStyleActivity.class)); }
        });
        alertLabel = chip(chipRow, new BellIcon(this), true, new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, TimesUpActivity.class)); }
        });
        GearIcon gear = new GearIcon(this);
        gear.setContentDescription("Settings");
        gear.setBackground(pill(CHIP, BORDER));
        gear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, SettingsActivity.class)); }
        });
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(px(48), px(48));
        gl.leftMargin = px(8);
        chipRow.addView(gear, gl);
        LinearLayout.LayoutParams crl = new LinearLayout.LayoutParams(-1, px(48));
        crl.topMargin = px(24);
        col.addView(chipRow, crl);

        col.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1)); // spacer

        // reset + start + zero
        controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        reset = new ResetButton(this);
        reset.setContentDescription("Reset timer");
        reset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (st.mode != TimerState.SET) TimerService.send(MainActivity.this, TimerService.ACTION_RESET);
            }
        });
        controls.addView(reset, new LinearLayout.LayoutParams(px(56), px(56)));
        primary = text("START", 15, BLACK, Fonts.monoBold(this));
        primary.setLetterSpacing(0.25f);
        primary.setGravity(Gravity.CENTER);
        primary.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onPrimary(); }
        });
        int primaryW = Math.min(px(190), screenW - px(48) - px(56 + 16 + 16 + 56));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(primaryW, px(64));
        pl.leftMargin = px(16);
        controls.addView(primary, pl);
        zero = text("00", 15, WHITE, Fonts.monoBold(this));
        zero.setGravity(Gravity.CENTER);
        zero.setContentDescription("Zero out timer");
        zero.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onZero(); }
        });
        LinearLayout.LayoutParams zl = new LinearLayout.LayoutParams(px(56), px(56));
        zl.leftMargin = px(16);
        controls.addView(zero, zl);
        col.addView(controls, new LinearLayout.LayoutParams(-1, -2));

        hint = text("", 12, SUB, Fonts.mono(this));
        hint.setGravity(Gravity.CENTER);
        hint.setSingleLine(true); // a wrapped hint would push the buttons up
        hint.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.topMargin = px(16);
        col.addView(hint, hl);

        // the keypad: slides up while typing; the big chip above is the readout
        keypad = new LinearLayout(this);
        keypad.setOrientation(LinearLayout.VERTICAL);
        // plain keys on the page background: no sheet, no border, no key fills
        keypad.setBackgroundColor(BLACK);
        keypad.setClickable(true); // taps between keys don't fall through
        String[][] keys = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"00", "0", "DEL"}};
        for (int row = 0; row < 4; row++) {
            LinearLayout line = new LinearLayout(this);
            for (int cI = 0; cI < 3; cI++) {
                final String k = keys[row][cI];
                View key;
                if ("DEL".equals(k)) {
                    DeleteIcon del = new DeleteIcon(this);
                    del.setContentDescription("Delete");
                    key = del;
                } else {
                    TextView t = text(k, 36, WHITE, Fonts.doto(this));
                    t.setGravity(Gravity.CENTER);
                    if ("0".equals(k)) key0 = t;
                    if ("00".equals(k)) key00 = t;
                    key = t;
                }
                // pressing shows a soft circle that fades out
                RippleDrawable press = new RippleDrawable(ColorStateList.valueOf(KEY_PRESS), null, null);
                press.setRadius(px(32));
                key.setBackground(press);
                key.setClickable(true);
                key.setFocusable(true);
                key.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { onKey(k); }
                });
                LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(0, px(64), 1);
                line.addView(key, kl);
            }
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, -2);
            keypad.addView(line, ll);
        }
        setKey = text("CANCEL", 15, BLACK, Fonts.monoBold(this));
        setKey.setLetterSpacing(0.2f);
        setKey.setGravity(Gravity.CENTER);
        setKey.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onSet(); }
        });
        LinearLayout.LayoutParams skl = new LinearLayout.LayoutParams(-1, px(52));
        skl.topMargin = px(12);
        keypad.addView(setKey, skl);
        keypad.setVisibility(View.GONE);
        root.addView(keypad, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                android.graphics.Insets b = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                col.setPadding(px(24) + b.left, px(20) + b.top, px(24) + b.right, px(28) + b.bottom);
                bottomInset = b.bottom;
                keypad.setPadding(px(24) + b.left, px(12), px(24) + b.right, px(16) + b.bottom);
                return WindowInsets.CONSUMED;
            }
        });
        return root;
    }

    private int dialSize(boolean small) {
        int s = Math.min(screenW - px(90), px(300));
        return small ? Math.round(s * 0.8f) : s;
    }

    /** A 48 dp chip with an icon, a label and a ›; returns the label so it can be updated. */
    private TextView chip(LinearLayout row, View icon, boolean grow, View.OnClickListener click) {
        LinearLayout c = new LinearLayout(this);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(px(16), 0, px(14), 0);
        c.setBackground(pill(CHIP, BORDER));
        c.setClickable(true);
        c.setFocusable(true);
        c.setOnClickListener(click);
        c.addView(icon, new LinearLayout.LayoutParams(px(16), px(16)));
        TextView label = text("", 12, WHITE, Fonts.monoBold(this));
        label.setLetterSpacing(0.12f);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams ll = grow ? new LinearLayout.LayoutParams(0, -2, 1) : new LinearLayout.LayoutParams(-2, -2);
        ll.leftMargin = px(8);
        ll.rightMargin = px(8);
        c.addView(label, ll);
        c.addView(text("›", 18, SUB, Fonts.mono(this)));
        LinearLayout.LayoutParams cl = grow ? new LinearLayout.LayoutParams(0, -1, 1) : new LinearLayout.LayoutParams(-2, -1);
        if (row.getChildCount() > 0) cl.leftMargin = px(8);
        row.addView(c, cl);
        return label;
    }

    // ---- actions ---------------------------------------------------------------------

    private void onPrimary() {
        switch (st.mode) {
            case TimerState.SET: startTimer(false); break;
            case TimerState.RUN: TimerService.send(this, TimerService.ACTION_PAUSE); break;
            case TimerState.PAUSE: TimerService.send(this, TimerService.ACTION_RESUME); break;
            default: TimerService.send(this, TimerService.ACTION_RESET); break;
        }
    }

    /** Sets the time to 00:00:00. Only while setting; a running timer has Reset and Pause. */
    private void onZero() {
        if (st.mode != TimerState.SET || st.setSeconds() <= 0) return;
        st.set(TimerState.UNIT_H, 0);
        st.set(TimerState.UNIT_M, 0);
        st.set(TimerState.UNIT_S, 0);
        st.save(this);
        zero.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        refresh();
    }

    private void startTimer(boolean flipped) {
        if (st.setSeconds() <= 0) {
            dial.performHapticFeedback(HapticFeedbackConstants.REJECT);
            return;
        }
        st.save(this);
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        TimerService.start(this, flipped);
    }

    /** Tap a unit to pick it for the dial; tap the picked one again to type. While typing, a tap cancels. */
    private void onSegment(int u) {
        if (typing) { closeTyping(); return; }
        if (st.mode != TimerState.SET) return;
        if (dial.getUnit() == u) openTyping();
        else { dial.setUnit(u); refresh(); }
    }

    private void openTyping() {
        typing = true;
        typed = "";
        refresh();
    }

    private void closeTyping() {
        typing = false;
        typed = "";
        refresh();
    }

    private void onKey(String k) {
        if ("DEL".equals(k)) {
            if (!typed.isEmpty()) typed = typed.substring(0, typed.length() - 1);
        } else {
            if (typed.isEmpty() && k.startsWith("0")) return; // leading zeros do nothing, as in Google Clock
            typed = (typed + k);
            if (typed.length() > 6) typed = typed.substring(0, 6);
        }
        refresh();
    }

    private void onSet() {
        if (typed.isEmpty()) { closeTyping(); return; }
        long total = typedSeconds();
        if (total > 86399) { setKey.performHapticFeedback(HapticFeedbackConstants.REJECT); return; }
        st.set(TimerState.UNIT_H, (int) (total / 3600));
        st.set(TimerState.UNIT_M, (int) (total % 3600 / 60));
        st.set(TimerState.UNIT_S, (int) (total % 60));
        st.save(this);
        setKey.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        closeTyping();
    }

    private String raw() { return ("000000" + typed).substring(typed.length()); }

    /** The typed digits as HH MM SS, overflow rolled up (78 min = 1 h 18 min). */
    private long typedSeconds() {
        String r = raw();
        return Integer.parseInt(r.substring(0, 2)) * 3600L + Integer.parseInt(r.substring(2, 4)) * 60L
                + Integer.parseInt(r.substring(4, 6));
    }

    @Override
    public void onBackPressed() {
        if (typing) { closeTyping(); return; }
        super.onBackPressed();
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
    public void onTimerChanged() {
        if (typing && st.mode != TimerState.SET) typing = false; // started from a widget or a flip
        refresh();
    }

    private void refresh() {
        int mode = st.mode;
        boolean setting = mode == TimerState.SET;
        boolean running = !setting;
        boolean done = mode == TimerState.DONE;
        boolean empty = setting && st.setSeconds() <= 0;

        // typing layout: smaller face, keypad up, chips and controls out of the way
        int size = dialSize(typing);
        LinearLayout.LayoutParams dl = (LinearLayout.LayoutParams) dial.getLayoutParams();
        if (dl.width != size) { dl.width = size; dl.height = size; dl.topMargin = px(typing ? 8 : 24); dial.setLayoutParams(dl); }
        keypad.setVisibility(typing ? View.VISIBLE : View.GONE);
        interp.setVisibility(typing ? View.VISIBLE : View.GONE);
        chipRow.setVisibility(typing ? View.GONE : View.VISIBLE);
        controls.setVisibility(typing ? View.INVISIBLE : View.VISIBLE);
        hint.setVisibility(typing ? View.INVISIBLE : View.VISIBLE);
        status.setVisibility(typing ? View.INVISIBLE : View.VISIBLE); // keeps its 32 dp gap under the dial
        bigChip.setBackground(rounded(Color.TRANSPARENT, px(32), typing ? WHITE : BORDER, typing ? 1.5f : 1f));
        dial.setTyping(typing, typing && !typed.isEmpty() && typedSeconds() <= 86399 ? typedSeconds() : st.setSeconds());

        // status line
        status.setText(running ? (done ? "TIME'S UP" : mode == TimerState.PAUSE ? "PAUSED" : "REMAINING") : "");
        status.setTextColor(done ? RED : SUB);

        showTime();

        // typing readout
        if (typing) {
            long total = typedSeconds();
            boolean tooBig = total > 86399;
            String r = raw();
            int mIn = Integer.parseInt(r.substring(2, 4)), sIn = Integer.parseInt(r.substring(4, 6));
            long h = total / 3600, m = total % 3600 / 60, s = total % 60;
            String line;
            if (typed.isEmpty()) line = "Digits fill in from the right";
            else if (tooBig) line = "MAX IS 23:59:59";
            else if (mIn > 59 || sIn > 59) line = (mIn > 59 ? mIn + " MIN" : sIn + " SEC") + " ROLLS OVER → " + h + " HR " + m + " MIN " + s + " SEC";
            else line = "= " + h + " HR " + m + " MIN " + s + " SEC";
            interp.setText(line);
            interp.setTextColor(tooBig ? RED : typed.isEmpty() ? SUB : WHITE);
            boolean canSet = !tooBig;
            int zeroKeys = typed.isEmpty() ? OFF : WHITE; // a leading zero does nothing
            key0.setTextColor(zeroKeys);
            key00.setTextColor(zeroKeys);
            setKey.setText(typed.isEmpty() ? "CANCEL" : "SET " + h + ":" + two(m) + ":" + two(s));
            setKey.setBackground(pill(canSet ? WHITE : KEY, 0));
            setKey.setTextColor(canSet ? BLACK : OFF);
        }

        // chips
        faceLabel.setText(Prefs.style(this).toUpperCase());
        alertLabel.setText(Sounds.selectedName(this).toUpperCase());
        ((View) alertLabel.getParent()).setContentDescription("Time's up alert: " + Prefs.anim(this) + ", " + Sounds.selectedName(this) + ". Change");
        ((View) faceLabel.getParent()).setContentDescription("Countdown face: " + Prefs.style(this) + ". Change");

        // controls
        String label = setting ? "START" : mode == TimerState.RUN ? "PAUSE" : mode == TimerState.PAUSE ? "RESUME" : "DISMISS";
        primary.setText(label);
        primary.setBackground(empty ? pill(KEY, 0) : pill(done ? RED : WHITE, 0));
        primary.setTextColor(empty ? OFF : done ? 0xFFFFFFFF : BLACK);
        boolean zeroOn = setting && !empty;
        zero.setEnabled(zeroOn);
        zero.setTextColor(zeroOn ? WHITE : OFF);
        zero.setBackground(pill(Color.TRANSPARENT, zeroOn ? BORDER : (appliedDark ? 0xFF2C3236 : 0xFFD3D7DA)));
        reset.setEnabled(running);
        reset.invalidate();
        if (setting) hint.setText(empty ? "Drag the face, or tap a number to type"
                : "Tap " + new String[]{"HR", "MIN", "SEC"}[dial.getUnit()] + " again to type, or flip to start");
        else hint.setText("Flip face-down to show it on the back");

        dial.invalidate();
        handler.removeCallbacks(frame);
        if (mode == TimerState.RUN || mode == TimerState.DONE) handler.post(frame);
    }

    /** The big chip's digits: the set time, the time left, or the digits being typed. */
    private void showTime() {
        int mode = st.mode;
        boolean setting = mode == TimerState.SET, done = mode == TimerState.DONE;
        if (typing) {
            String r = raw();
            int n = typed.length();
            boolean tooBig = typedSeconds() > 86399;
            for (int i = 0; i < 3; i++) {
                android.text.SpannableString sp = new android.text.SpannableString(r.substring(i * 2, i * 2 + 2));
                for (int j = 0; j < 2; j++) {
                    boolean on = i * 2 + j >= 6 - n;
                    int c = tooBig ? RED : on ? WHITE : FAINT;
                    sp.setSpan(new android.text.style.ForegroundColorSpan(c), j, j + 1, 0);
                }
                segDigits[i].setText(sp);
                segUnits[i].setTextColor(i * 2 + 1 >= 6 - n ? SUB : FAINT);
                segs[i].setBackground(null);
            }
            return;
        }
        long secs = st.displaySeconds();
        long[] v = {secs / 3600, secs % 3600 / 60, secs % 60};
        for (int i = 0; i < 3; i++) {
            boolean picked = setting && dial.getUnit() == i;
            boolean zeroPart = v[i] == 0 && (st.setSeconds() <= 0 && setting || (i == 0 && !picked));
            int c = picked ? BLACK : zeroPart ? FAINT : i == 2 ? SUB : WHITE;
            int uc = picked ? BLACK : zeroPart ? FAINT : SUB;
            if (done) { c = RED; uc = RED; }
            segDigits[i].setText(two(v[i]));
            segDigits[i].setTextColor(c);
            segUnits[i].setTextColor(uc);
            segs[i].setBackground(picked ? rounded(WHITE, px(26), 0, 0) : null);
            segs[i].setClickable(setting);
        }
    }

    // ---- lifecycle -------------------------------------------------------------------

    @Override
    protected void onResume() {
        super.onResume();
        if (Palette.isDark(this) != appliedDark) { recreate(); return; } // theme changed in Settings
        st.addListener(this);
        if (gravity != null) sensors.registerListener(this, gravity, SensorManager.SENSOR_DELAY_UI);
        refresh();
        WidgetUpdater.updateAll(this); // picks up a new wallpaper colour for the widgets
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
        if (!faceDown || typing || st.mode != TimerState.SET || st.setSeconds() <= 0) {
            faceDownSince = 0;
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (faceDownSince == 0) {
            faceDownSince = now;
        } else if (now - faceDownSince > FLIP_HOLD_MS) {
            faceDownSince = 0;
            dial.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            startTimer(true);
        }
    }

    @Override public void onAccuracyChanged(Sensor s, int a) {}

    // ---- helpers ---------------------------------------------------------------------

    private int px(float v) { return Math.round(v * dp); }

    private static String two(long n) { return n < 10 ? "0" + n : String.valueOf(n); }

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
        return rounded(fill, px(999), stroke, 1f);
    }

    private GradientDrawable rounded(int fill, int radius) { return rounded(fill, radius, 0, 0); }

    private GradientDrawable rounded(int fill, int radius, int stroke, float strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(radius);
        d.setColor(fill);
        if (stroke != 0) d.setStroke(Math.max(1, Math.round(strokeDp * dp)), stroke);
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
            float cx = getWidth() / 2f, cy = getHeight() / 2f, u = getWidth() / 56f;
            c.drawCircle(cx, cy, 3.2f * u, p);
            c.drawCircle(cx, cy, 7f * u, p);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                float sx = (float) Math.sin(a), sy = (float) Math.cos(a);
                c.drawLine(cx + sx * 7f * u, cy + sy * 7f * u, cx + sx * 10f * u, cy + sy * 10f * u, p);
            }
        }
    }

    /** A small round face of dots, for the face chip. */
    static class FaceIcon extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d;
        FaceIcon(Context c) { super(c); d = c.getResources().getDisplayMetrics().density; p.setColor(WHITE); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), cx = w / 2, u = w / 24f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2 * d);
            c.drawCircle(cx, cx, 9.5f * u, p);
            p.setStyle(Paint.Style.FILL);
            for (int i = -1; i <= 1; i += 2) for (int j = -1; j <= 1; j += 2) c.drawCircle(cx + i * 3 * u, cx + j * 3 * u, 1.3f * u, p);
        }
    }

    /** A bell, for the time's-up alert chip. */
    static class BellIcon extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF oval = new RectF();
        BellIcon(Context c) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2 * c.getResources().getDisplayMetrics().density);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(WHITE);
        }
        @Override protected void onDraw(Canvas c) {
            float u = getWidth() / 24f;
            path.reset();
            path.moveTo(6 * u, 16 * u);
            path.lineTo(6 * u, 11 * u);
            oval.set(6 * u, 5 * u, 18 * u, 17 * u);
            path.arcTo(oval, 180, 180);
            path.lineTo(18 * u, 16 * u);
            path.lineTo(19.5f * u, 18 * u);
            path.lineTo(4.5f * u, 18 * u);
            path.close();
            c.drawPath(path, p);
            oval.set(10 * u, 18.5f * u, 14 * u, 22.5f * u);
            c.drawArc(oval, 0, 180, false, p);
        }
    }

    /** Delete key: a tag shape with a cross. */
    class DeleteIcon extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        DeleteIcon(Context c) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.8f * dp);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(WHITE);
        }
        @Override protected void onDraw(Canvas c) {
            float s = 24 * dp, x0 = (getWidth() - s) / 2, y0 = (getHeight() - s) / 2, u = s / 24f;
            path.reset();
            path.moveTo(x0 + 21 * u, y0 + 5 * u);
            path.lineTo(x0 + 9 * u, y0 + 5 * u);
            path.lineTo(x0 + 3 * u, y0 + 12 * u);
            path.lineTo(x0 + 9 * u, y0 + 19 * u);
            path.lineTo(x0 + 21 * u, y0 + 19 * u);
            path.close();
            c.drawPath(path, p);
            c.drawLine(x0 + 12 * u, y0 + 9 * u, x0 + 18 * u, y0 + 15 * u, p);
            c.drawLine(x0 + 18 * u, y0 + 9 * u, x0 + 12 * u, y0 + 15 * u, p);
        }
    }

    /** Round outlined button with a circular-arrow reset icon; dims when there's nothing to reset. */
    class ResetButton extends View {
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF oval = new RectF();

        ResetButton(Context c) {
            super(c);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(dp);
            icon.setStyle(Paint.Style.STROKE);
            icon.setStrokeWidth(2 * dp);
            icon.setStrokeCap(Paint.Cap.ROUND);
            icon.setStrokeJoin(Paint.Join.ROUND);
            setClickable(true);
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            ring.setColor(BORDER);
            icon.setColor(isEnabled() ? WHITE : OFF);
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
