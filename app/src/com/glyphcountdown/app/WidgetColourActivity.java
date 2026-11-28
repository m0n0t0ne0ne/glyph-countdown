package com.glyphcountdown.app;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings › Widget colour: the colours of the home-screen wallpaper as swatches, plus White, with
 * a live 2×2 preview. Read from the wallpaper itself, so Nothing's dynamic theme doesn't affect it.
 */
public class WidgetColourActivity extends Activity {
    private int BLACK, WHITE, GREY;
    private boolean dark;
    private float dp;
    private ImageView preview;
    private final List<Swatch> swatches = new ArrayList<>();

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Palette pal = Palette.of(this);
        BLACK = pal.bg; WHITE = pal.fg; GREY = pal.grey; dark = pal.dark;
        Palette.applyBars(this, pal);
        dp = getResources().getDisplayMetrics().density;
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

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
        header.addView(text("Widget colour", 28, WHITE, Fonts.doto(this)));
        col.addView(header);

        // live preview of the 2×2 in the current timer state
        preview = new ImageView(this);
        preview.setContentDescription("Preview of the 2×2 widget");
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(px(200), px(200));
        pl.gravity = Gravity.CENTER_HORIZONTAL;
        pl.topMargin = px(20);
        col.addView(preview, pl);

        int picked = Prefs.widgetColour(this);
        List<Integer> wall = WidgetColours.fromWallpaper(this);
        // a colour picked from an earlier wallpaper stays on offer
        if (picked != 0 && !wall.contains(picked)) wall.add(0, picked);

        section(col, "FROM YOUR WALLPAPER");
        if (wall.isEmpty()) {
            TextView none = text("Your wallpaper didn't give any colours. Live wallpapers sometimes don't.", 12, GREY, Fonts.mono(this));
            none.setLineSpacing(0, 1.3f);
            col.addView(none, new LinearLayout.LayoutParams(-1, -2));
        } else {
            grid(col, wall);
        }
        section(col, "PLAIN");
        List<Integer> plain = new ArrayList<>();
        plain.add(0);
        grid(col, plain);

        TextView note = text("Used while the timer is set or running. Paused stays grey and time's up stays red. "
                + "Your pick stays when you change wallpaper; come back here to see the new wallpaper's colours.",
                12, GREY, Fonts.mono(this));
        note.setLineSpacing(0, 1.3f);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
        nl.topMargin = px(24);
        col.addView(note, nl);

        setContentView(root);
        refresh();
    }

    private void grid(LinearLayout col, List<Integer> colours) {
        LinearLayout row = null;
        for (int i = 0; i < colours.size(); i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(this);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
                rl.topMargin = i == 0 ? 0 : px(14);
                col.addView(row, rl);
            }
            final int colour = colours.get(i);
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setClickable(true);
            cell.setFocusable(true);
            Swatch sw = new Swatch(colour);
            cell.addView(sw, new LinearLayout.LayoutParams(px(64), px(64)));
            TextView label = text(WidgetColours.name(colour).toUpperCase(), 10, GREY, Fonts.monoBold(this));
            label.setLetterSpacing(0.12f);
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-2, -2);
            ll.topMargin = px(8);
            cell.addView(label, ll);
            sw.label = label;
            sw.cell = cell;
            cell.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Prefs.setWidgetColour(WidgetColourActivity.this, colour);
                    WidgetUpdater.updateAll(WidgetColourActivity.this);
                    refresh();
                }
            });
            row.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
            swatches.add(sw);
        }
        // keep the last row's cells the same width as full rows
        while (row != null && row.getChildCount() < 4) row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
    }

    private void refresh() {
        int picked = Prefs.widgetColour(this);
        for (Swatch s : swatches) {
            boolean on = s.colour == picked;
            s.selected = on;
            s.invalidate();
            s.label.setTextColor(on ? WHITE : GREY);
            s.cell.setContentDescription(WidgetColours.name(s.colour) + (on ? ", selected" : ""));
        }
        TimerState st = TimerState.get();
        st.load(this);
        int size = px(200);
        preview.setImageBitmap(WidgetRenderer.render(this, WidgetRenderer.K4, size, size, st, dark,
                Prefs.get(this, Prefs.TRANSLUCENT), picked));
    }

    private void section(LinearLayout col, String label) {
        TextView t = text(label, 11, GREY, Fonts.mono(this));
        t.setLetterSpacing(0.18f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = px(28);
        lp.bottomMargin = px(12);
        col.addView(t, lp);
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

    /** A round swatch: left half as it shows on the dark widget, right half on the light one. */
    class Swatch extends View {
        final int colour;
        boolean selected;
        TextView label;
        View cell;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Swatch(int colour) {
            super(WidgetColourActivity.this);
            this.colour = colour;
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), cx = w / 2, cy = h / 2;
            float ring = 2 * dp, gap = 3 * dp, rad = Math.min(w, h) / 2 - ring - gap;
            r.set(cx - rad, cy - rad, cx + rad, cy + rad);
            p.setStyle(Paint.Style.FILL);
            p.setColor(colour == 0 ? 0xFF4F8797 : WidgetColours.lit(colour, true));
            c.drawArc(r, 90, 180, true, p);
            p.setColor(colour == 0 ? 0xFF7FB3C4 : WidgetColours.lit(colour, false));
            c.drawArc(r, 270, 180, true, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1, dp));
            p.setColor(0x33FFFFFF & (dark ? 0xFFFFFFFF : 0xFF000000) | 0x33000000);
            c.drawCircle(cx, cy, rad, p);
            if (selected) {
                p.setStrokeWidth(ring);
                p.setColor(WHITE);
                c.drawCircle(cx, cy, rad + gap + ring / 2, p);
            }
        }
    }
}
