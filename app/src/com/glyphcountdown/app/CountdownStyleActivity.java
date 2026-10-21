package com.glyphcountdown.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.nothing.ketchum.Common;

import java.util.ArrayList;
import java.util.List;

/** Choose what the Glyph Matrix shows while counting down, with every style running live. */
public class CountdownStyleActivity extends Activity {
    private static final int RED = 0xFFE8202A;
    private static final long FRAME_MS = 83;
    private static final double DEMO_TOTAL = 90, DEMO_SPEED = 10;   // a 1:30 timer at 10× speed

    private int BLACK, WHITE, GREY, LIGHT, CARD, LINE, DIVIDER, DIM;
    private float dp;
    private int size = MatrixRenderer.SIZE;
    private final long t0 = SystemClock.uptimeMillis();
    private String selected;
    private MatrixView hero;
    private TextView heroName, heroNote;
    private final List<MatrixView> views = new ArrayList<>();
    private final List<View> radios = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable frame = new Runnable() {
        @Override public void run() {
            for (MatrixView v : views) v.invalidate();
            handler.postDelayed(this, FRAME_MS);
        }
    };

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Palette pal = Palette.of(this);
        BLACK = pal.bg; WHITE = pal.fg; GREY = pal.grey; LIGHT = pal.light; CARD = pal.card; LINE = pal.line;
        DIVIDER = pal.divider; DIM = pal.dim;
        Palette.applyBars(this, pal);
        dp = getResources().getDisplayMetrics().density;
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        try { if (Common.is25111p()) size = MatrixRenderer.SIZE_SMALL; } catch (Throwable ignored) {}
        selected = Prefs.style(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BLACK);
        final LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        final LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        page.addView(top);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 30, WHITE, Fonts.mono(this));
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Back");
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(px(44), px(44));
        bl.leftMargin = -px(10);
        header.addView(back, bl);
        header.addView(text("Countdown style", 28, WHITE, Fonts.doto(this)));
        top.addView(header);

        // pinned preview
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(px(12), px(12), px(14), px(12));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(px(20));
        bg.setColor(CARD);
        bg.setStroke(Math.max(1, px(1)), LINE);
        card.setBackground(bg);
        hero = new MatrixView(this, selected);
        hero.setContentDescription("Preview of the Glyph Matrix");
        card.addView(hero, new LinearLayout.LayoutParams(px(140), px(140)));
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        TextView label = text("PREVIEW · 1:30 AT 10×", 10, GREY, Fonts.mono(this));
        label.setLetterSpacing(0.15f);
        side.addView(label);
        heroName = text("", 22, WHITE, Fonts.doto(this));
        side.addView(heroName, lp(-1, -2, 8));
        heroNote = text("", 12, GREY, Fonts.mono(this));
        heroNote.setLineSpacing(0, 1.3f);
        side.addView(heroNote, lp(-1, -2, 6));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, -2, 1);
        sl.leftMargin = px(14);
        card.addView(side, sl);
        top.addView(card, lp(-1, -2, 12));

        ScrollView scroll = new ScrollView(this);
        final LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(col, new FrameLayout.LayoutParams(-1, -2));
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                android.graphics.Insets b = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                top.setPadding(px(24) + b.left, px(20) + b.top, px(24) + b.right, px(4));
                col.setPadding(px(24) + b.left, px(12), px(24) + b.right, px(24) + b.bottom);
                return WindowInsets.CONSUMED;
            }
        });
        for (int i = 0; i < CountdownStyles.NAMES.length; i++) col.addView(row(i));
        TextView foot = text("Shown on the Glyph Matrix while the timer runs. Every style dims when paused "
                + "and drains back toward 12 o'clock, like the ring.", 12, GREY, Fonts.mono(this));
        foot.setLineSpacing(0, 1.3f);
        col.addView(foot, lp(-1, -2, 16));

        setContentView(root);
        refresh();
    }

    private View row(int i) {
        final String name = CountdownStyles.NAMES[i];
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(px(68));
        r.setPadding(0, px(8), 0, px(8));
        r.setClickable(true);
        r.setFocusable(true);
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                selected = name;
                Prefs.setStyle(CountdownStyleActivity.this, name);
                refresh();
            }
        });
        r.addView(new MatrixView(this, name), new LinearLayout.LayoutParams(px(48), px(48)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(name, 15, WHITE, Fonts.monoBold(this)));
        TextView s = text(CountdownStyles.NOTES[i], 12, GREY, Fonts.mono(this));
        s.setLineSpacing(0, 1.25f);
        texts.addView(s, lp(-1, -2, 4));
        LinearLayout.LayoutParams xl = new LinearLayout.LayoutParams(0, -2, 1);
        xl.leftMargin = px(16);
        xl.rightMargin = px(12);
        r.addView(texts, xl);
        Radio radio = new Radio(this, name);
        radios.add(radio);
        r.addView(radio, new LinearLayout.LayoutParams(px(20), px(20)));
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(r, new LinearLayout.LayoutParams(-1, -2));
        View line = new View(this);
        line.setBackgroundColor(DIVIDER);
        wrap.addView(line, new LinearLayout.LayoutParams(-1, Math.max(1, px(1))));
        return wrap;
    }

    private void refresh() {
        hero.name = selected;
        heroName.setText(selected);
        for (int k = 0; k < CountdownStyles.NAMES.length; k++)
            if (CountdownStyles.NAMES[k].equals(selected)) heroNote.setText(CountdownStyles.NOTES[k]);
        for (View r : radios) {
            r.invalidate();
            String n = ((Radio) r).name;
            ((View) r.getParent()).setContentDescription(n + (n.equals(selected) ? ", selected" : ""));
        }
    }

    @Override protected void onResume() { super.onResume(); handler.post(frame); }

    @Override protected void onPause() { handler.removeCallbacks(frame); super.onPause(); }

    private LinearLayout.LayoutParams lp(int w, int h, int topDp) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(w, h);
        l.topMargin = px(topDp);
        return l;
    }

    private int px(float v) { return Math.round(v * dp); }

    private TextView text(String s, float sp, int color, android.graphics.Typeface tf) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** One frame of a style for the looping demo countdown, as perceived brightness 0..1. */
    private double[] demoFrame(String name, int n) {
        double wall = (SystemClock.uptimeMillis() - t0) / 1000.0;
        double elapsed = (wall * DEMO_SPEED) % DEMO_TOTAL, rem = DEMO_TOTAL - elapsed;
        if (CountdownStyles.RING.equals(name)) {
            int[] px = MatrixRenderer.runFrame(n, (long) (rem * 1000), (long) (DEMO_TOTAL * 1000), false);
            double[] v = new double[px.length];
            for (int i = 0; i < px.length; i++) v[i] = px[i] / 4095.0;
            return v;
        }
        CountdownStyles.Moment m = new CountdownStyles.Moment();
        m.remExact = rem;
        m.frac = rem / DEMO_TOTAL;
        m.wallSec = wall;
        return CountdownStyles.draw(name, n, m);
    }

    /** The Glyph Matrix as it looks on the back of the phone. */
    private final class MatrixView extends View {
        String name;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        MatrixView(Context c, String name) { super(c); this.name = name; views.add(this); }
        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), d = Math.min(w, h);
            p.setColor(0xFF000000);
            cv.drawCircle(w / 2, h / 2, d / 2, p);
            int n = size;
            double[] v = demoFrame(name, n);
            float cell = d * 0.9f / n, x0 = (w - cell * n) / 2, y0 = (h - cell * n) / 2, rad = cell * 0.36f;
            for (int r = 0; r < n; r++) {
                for (int c = 0; c < n; c++) {
                    if (!MorphEngine.onMatrix(n, r, c)) continue;
                    double b = v[r * n + c];
                    int g = b <= 0.02 ? 0x1C : (int) Math.round(40 + 215 * Math.min(1, b));
                    p.setColor(0xFF000000 | (g << 16) | (g << 8) | g);
                    cv.drawCircle(x0 + (c + 0.5f) * cell, y0 + (r + 0.5f) * cell, rad, p);
                }
            }
        }
    }

    /** Ring with a red centre dot when selected. */
    private final class Radio extends View {
        final String name;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Radio(Context c, String name) { super(c); this.name = name; }
        @Override protected void onDraw(Canvas c) {
            boolean on = name.equals(selected);
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2 * dp);
            p.setColor(on ? RED : DIM);
            c.drawCircle(cx, cy, Math.min(cx, cy) - dp, p);
            if (on) {
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(cx, cy, 5 * dp, p);
            }
        }
    }
}
