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

/** Choose what the Glyph Matrix shows when time's up, with live previews of every animation. */
public class TimesUpActivity extends Activity {
    private static final int RED = 0xFFE8202A;
    private static final long FRAME_MS = 83;

    private int BLACK, WHITE, GREY, LINE, DIM;
    private float dp;
    private int size = MatrixRenderer.SIZE;
    private String selected;
    private MatrixView hero;
    private TextView heroName, heroNote;
    private final List<MatrixView> views = new ArrayList<>();
    private final List<Radio> radios = new ArrayList<>();
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
        BLACK = pal.bg; WHITE = pal.fg; GREY = pal.grey; LINE = pal.divider; DIM = pal.dim;
        Palette.applyBars(this, pal);
        dp = getResources().getDisplayMetrics().density;
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        try { if (Common.is25111p()) size = MatrixRenderer.SIZE_SMALL; } catch (Throwable ignored) {}
        selected = Prefs.anim(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BLACK);
        ScrollView scroll = new ScrollView(this);
        final LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(col, new FrameLayout.LayoutParams(-1, -2));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                android.graphics.Insets b = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                col.setPadding(px(24) + b.left, px(20) + b.top, px(24) + b.right, px(24) + b.bottom);
                return WindowInsets.CONSUMED;
            }
        });

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 30, WHITE, Fonts.mono(this));
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Back to settings");
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(px(44), px(44));
        bl.leftMargin = -px(10);
        header.addView(back, bl);
        header.addView(text("Time's up", 28, WHITE, Fonts.doto(this)));
        col.addView(header);

        hero = new MatrixView(this, selected);
        hero.setContentDescription("Preview");
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(px(200), px(200));
        hl.gravity = Gravity.CENTER_HORIZONTAL;
        hl.topMargin = px(20);
        col.addView(hero, hl);
        heroName = text("", 22, WHITE, Fonts.doto(this));
        heroName.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
        nl.topMargin = px(16);
        col.addView(heroName, nl);
        heroNote = text("", 12, GREY, Fonts.mono(this));
        heroNote.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2);
        tl.topMargin = px(6);
        col.addView(heroNote, tl);

        TextView sec = text("ANIMATIONS", 11, GREY, Fonts.mono(this));
        sec.setLetterSpacing(0.18f);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
        sl.topMargin = px(24);
        sl.bottomMargin = px(4);
        col.addView(sec, sl);

        for (int i = 0; i < MorphEngine.NAMES.length; i++) col.addView(row(i));

        TextView foot = text("Shown on the Glyph Matrix until you dismiss the alarm. With “Sync with alarm sound” on, "
                + "each loop lines up with the sound and flashes on every note.", 12, GREY, Fonts.mono(this));
        foot.setLineSpacing(0, 1.3f);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-1, -2);
        fl.topMargin = px(16);
        col.addView(foot, fl);

        setContentView(root);
        refresh();
    }

    private View row(final int i) {
        final String name = MorphEngine.NAMES[i];
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(px(68));
        r.setPadding(0, px(8), 0, px(8));
        r.setClickable(true);
        r.setFocusable(true);
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                selected = name;
                Prefs.setAnim(TimesUpActivity.this, name);
                refresh();
            }
        });
        r.addView(new MatrixView(this, name), new LinearLayout.LayoutParams(px(48), px(48)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(name, 15, WHITE, Fonts.monoBold(this)));
        TextView s = text(MorphEngine.NOTES[i], 12, GREY, Fonts.mono(this));
        LinearLayout.LayoutParams stl = new LinearLayout.LayoutParams(-1, -2);
        stl.topMargin = px(4);
        texts.addView(s, stl);
        LinearLayout.LayoutParams xl = new LinearLayout.LayoutParams(0, -2, 1);
        xl.leftMargin = px(16);
        xl.rightMargin = px(12);
        r.addView(texts, xl);
        Radio radio = new Radio(this, name);
        radios.add(radio);
        r.addView(radio, new LinearLayout.LayoutParams(px(20), px(20)));
        r.setTag(name);

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(r, new LinearLayout.LayoutParams(-1, -2));
        View line = new View(this);
        line.setBackgroundColor(LINE);
        wrap.addView(line, new LinearLayout.LayoutParams(-1, Math.max(1, px(1))));
        return wrap;
    }

    private void refresh() {
        hero.name = selected;
        heroName.setText(selected);
        int i = 0;
        for (int k = 0; k < MorphEngine.NAMES.length; k++) if (MorphEngine.NAMES[k].equals(selected)) i = k;
        heroNote.setText(MorphEngine.NOTES[i]);
        for (Radio r : radios) {
            r.invalidate();
            ((View) r.getParent()).setContentDescription(r.name + (r.name.equals(selected) ? ", selected" : ""));
        }
    }

    @Override protected void onResume() { super.onResume(); handler.post(frame); }

    @Override protected void onPause() { handler.removeCallbacks(frame); super.onPause(); }

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

    /** The Glyph Matrix as it looks on the back of the phone: white LEDs on a black disc. */
    final class MatrixView extends View {
        String name;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        MatrixView(Context c, String name) {
            super(c);
            this.name = name;
            views.add(this);
        }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), d = Math.min(w, h);
            p.setColor(0xFF000000);
            cv.drawCircle(w / 2, h / 2, d / 2, p);
            int n = size;
            long now = SystemClock.uptimeMillis();
            int[] px;
            if (MorphEngine.DEFAULT.equals(name)) {
                px = MatrixRenderer.doneFrame(n, (now / 500) % 2 == 0);
            } else {
                double nat = MorphEngine.naturalSeconds(name) * 1000;
                px = MorphEngine.frame(name, n, (now % (long) nat) / nat);
            }
            float cell = d * 0.9f / n, x0 = (w - cell * n) / 2, y0 = (h - cell * n) / 2;
            float rad = cell * 0.36f;
            for (int r = 0; r < n; r++) {
                for (int c = 0; c < n; c++) {
                    if (!MorphEngine.onMatrix(n, r, c)) continue;
                    p.setColor(px[r * n + c] != 0 ? 0xFFFFFFFF : 0xFF1C1C1C);
                    cv.drawCircle(x0 + (c + 0.5f) * cell, y0 + (r + 0.5f) * cell, rad, p);
                }
            }
        }
    }

    /** Ring with a red centre dot when selected. */
    final class Radio extends View {
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
