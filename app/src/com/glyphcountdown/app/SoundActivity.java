package com.glyphcountdown.app;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Choose the alarm sound: Nothing's notification sounds or the phone's alarm sounds. */
public class SoundActivity extends Activity {
    // set from Palette in onCreate: BLACK is the background role, WHITE the foreground role
    private static int BLACK, WHITE, GREY, LIGHT, BORDER, LINE, RED_TEXT, DIM;
    private static final int RED = 0xFFE8202A;
    private static final long PREVIEW_MS = 4000;

    private float dp;
    private boolean alarmTab;
    private final TextView[] tabs = new TextView[2];
    private TextView note;
    private LinearLayout list;
    private Ringtone preview;
    private String playingTitle;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable stopPreview = new Runnable() {
        @Override public void run() { stopPreview(); render(); }
    };

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Palette pal = Palette.of(this);
        BLACK = pal.bg; WHITE = pal.fg; GREY = pal.grey; LIGHT = pal.light; BORDER = pal.border;
        LINE = pal.divider; RED_TEXT = pal.redText; DIM = pal.dim;
        Palette.applyBars(this, pal);
        dp = getResources().getDisplayMetrics().density;
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        alarmTab = !Sounds.SRC_NOTHING.equals(Sounds.selectedSource(this));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BLACK);
        final LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                android.graphics.Insets b = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                col.setPadding(px(24) + b.left, px(20) + b.top, px(24) + b.right, px(20) + b.bottom);
                return WindowInsets.CONSUMED;
            }
        });

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 30, WHITE, Fonts.mono(this));
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Back to timer");
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(px(44), px(44));
        bl.leftMargin = -px(10);
        header.addView(back, bl);
        TextView title = text("Alarm sound", 28, WHITE, Fonts.doto(this));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.leftMargin = px(4);
        header.addView(title, tl);
        col.addView(header);

        LinearLayout seg = new LinearLayout(this);
        seg.setPadding(px(4), px(4), px(4), px(4));
        seg.setBackground(pill(Color.TRANSPARENT, BORDER));
        String[] labels = {"NOTHING", "ALARMS"};
        for (int i = 0; i < 2; i++) {
            final boolean isAlarm = i == 1;
            TextView t = text(labels[i], 13, LIGHT, Fonts.monoBold(this));
            t.setLetterSpacing(0.15f);
            t.setGravity(Gravity.CENTER);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (alarmTab != isAlarm) { alarmTab = isAlarm; stopPreview(); render(); }
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, px(44), 1);
            if (i > 0) lp.leftMargin = px(6);
            seg.addView(t, lp);
            tabs[i] = t;
        }
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
        sl.topMargin = px(20);
        col.addView(seg, sl);

        note = text("", 12, GREY, Fonts.mono(this));
        note.setLineSpacing(0, 1.3f);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
        nl.topMargin = px(12);
        col.addView(note, nl);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams scl = new LinearLayout.LayoutParams(-1, 0, 1);
        scl.topMargin = px(12);
        col.addView(scroll, scl);

        LinearLayout foot = new LinearLayout(this);
        foot.addView(text("Tap a sound to preview it", 12, GREY, Fonts.mono(this)), new LinearLayout.LayoutParams(0, -2, 1));
        foot.addView(text("Plays at alarm volume", 12, GREY, Fonts.mono(this)));
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-1, -2);
        fl.topMargin = px(16);
        col.addView(foot, fl);

        setContentView(root);
        render();
    }

    private void render() {
        for (int i = 0; i < 2; i++) {
            boolean on = (i == 1) == alarmTab;
            tabs[i].setBackground(pill(on ? WHITE : Color.TRANSPARENT, 0));
            tabs[i].setTextColor(on ? BLACK : LIGHT);
            tabs[i].setSelected(on);
        }
        note.setText(alarmTab
                ? "Android and Google alarm sounds installed on your phone."
                : "Nothing’s own notification sounds, read from your phone.");

        list.removeAllViews();
        List<Sounds.Item> items = Sounds.list(this, alarmTab ? RingtoneManager.TYPE_ALARM : RingtoneManager.TYPE_NOTIFICATION);
        String selUri = Sounds.selectedUriString(this);
        String selSrc = Sounds.selectedSource(this);
        // "Device default" belongs to whichever tab it was picked on (the Alarms tab at first).
        boolean defaultOnThisTab = alarmTab != Sounds.SRC_NOTHING.equals(selSrc);
        for (final Sounds.Item item : items) {
            boolean selected = item.uri == null
                    ? selUri.isEmpty() && defaultOnThisTab
                    : item.uri.toString().equals(selUri);
            list.addView(row(item, selected, item.title.equals(playingTitle)));
        }
    }

    private View row(final Sounds.Item item, boolean selected, boolean playing) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(px(56));
        r.setPadding(px(4), 0, px(4), 0);
        r.setClickable(true);
        r.setFocusable(true);
        r.setContentDescription(item.title + (selected ? ", selected" : ""));
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { choose(item); }
        });
        r.addView(new Radio(this, selected), new LinearLayout.LayoutParams(px(20), px(20)));
        TextView name = text(item.title, 15, WHITE, Fonts.mono(this));
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, -2, 1);
        nl.leftMargin = px(16);
        r.addView(name, nl);
        if (playing) {
            TextView p = text("♪ PREVIEW", 11, RED_TEXT, Fonts.mono(this));
            p.setLetterSpacing(0.12f);
            r.addView(p);
        }
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(r, new LinearLayout.LayoutParams(-1, -2));
        View line = new View(this);
        line.setBackgroundColor(LINE);
        wrap.addView(line, new LinearLayout.LayoutParams(-1, Math.max(1, px(1))));
        return wrap;
    }

    private void choose(Sounds.Item item) {
        Sounds.Item saved = item.uri == null
                ? new Sounds.Item("Device default", null, alarmTab ? Sounds.SRC_ALARM : Sounds.SRC_NOTHING)
                : item;
        Sounds.save(this, saved);
        stopPreview();
        try {
            preview = Sounds.ringtone(this, item.uri);
            if (preview != null) preview.play();
            playingTitle = item.title;
            handler.postDelayed(stopPreview, PREVIEW_MS);
        } catch (Throwable ignored) {}
        render();
    }

    private void stopPreview() {
        handler.removeCallbacks(stopPreview);
        try { if (preview != null) preview.stop(); } catch (Throwable ignored) {}
        preview = null;
        playingTitle = null;
    }

    @Override
    protected void onPause() {
        stopPreview();
        super.onPause();
    }

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

    /** Ring with a red centre dot when selected. */
    static class Radio extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean on;
        Radio(android.content.Context c, boolean on) { super(c); this.on = on; }
        @Override protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density;
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2 * d);
            p.setColor(on ? RED : DIM);
            c.drawCircle(cx, cy, Math.min(cx, cy) - d, p);
            if (on) {
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(cx, cy, 5 * d, p);
            }
        }
    }
}
