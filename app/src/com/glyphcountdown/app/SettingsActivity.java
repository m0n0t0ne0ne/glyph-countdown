package com.glyphcountdown.app;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Theme, Glyph Matrix behaviour, time's-up animation, widget translucency, alarm sound and vibration. */
public class SettingsActivity extends Activity {
    // set from Palette in onCreate: BLACK is the background role, WHITE the foreground role
    private static int BLACK, WHITE, GREY, LIGHT, CARD, LINE, OFF, BORDER;
    private static final int RED = 0xFFE8202A;

    private float dp;
    private TextView themeSub, soundValue, animValue;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Palette pal = Palette.of(this);
        BLACK = pal.bg; WHITE = pal.fg; GREY = pal.grey; LIGHT = pal.light; CARD = pal.card;
        LINE = pal.line; OFF = pal.switchOff; BORDER = pal.border;
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
        back.setContentDescription("Back to timer");
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(px(44), px(44));
        bl.leftMargin = -px(10);
        header.addView(back, bl);
        header.addView(text("Settings", 28, WHITE, Fonts.doto(this)));
        col.addView(header);

        section(col, "APPEARANCE");
        LinearLayout theme = card();
        theme.setOrientation(LinearLayout.VERTICAL);
        theme.addView(text("Theme", 15, WHITE, Fonts.monoBold(this)));
        themeSub = text("", 12, GREY, Fonts.mono(this));
        themeSub.setLineSpacing(0, 1.3f);
        LinearLayout.LayoutParams tsl = new LinearLayout.LayoutParams(-1, -2);
        tsl.topMargin = px(4);
        theme.addView(themeSub, tsl);
        LinearLayout seg = new LinearLayout(this);
        seg.setPadding(px(4), px(4), px(4), px(4));
        GradientDrawable segBg = new GradientDrawable();
        segBg.setCornerRadius(px(999));
        segBg.setStroke(Math.max(1, px(1)), BORDER);
        seg.setBackground(segBg);
        String current = Palette.choice(this);
        String[][] opts = {{Palette.PHONE, "PHONE", "Follow phone"}, {Palette.LIGHT, "LIGHT", "Light"}, {Palette.DARK, "DARK", "Dark"}};
        for (int i = 0; i < opts.length; i++) {
            final String value = opts[i][0];
            boolean sel = value.equals(current);
            TextView b = text(opts[i][1], 13, sel ? BLACK : LIGHT, Fonts.monoBold(this));
            b.setLetterSpacing(0.15f);
            b.setGravity(Gravity.CENTER);
            b.setContentDescription("Theme: " + opts[i][2] + (sel ? ", selected" : ""));
            GradientDrawable pill = new GradientDrawable();
            pill.setCornerRadius(px(999));
            pill.setColor(sel ? WHITE : Color.TRANSPARENT);
            b.setBackground(pill);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (value.equals(Palette.choice(SettingsActivity.this))) return;
                    Palette.setChoice(SettingsActivity.this, value);
                    WidgetUpdater.updateAll(SettingsActivity.this);
                    recreate(); // redraw this screen in the new theme
                }
            });
            LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(0, px(44), 1);
            if (i > 0) ol.leftMargin = px(6);
            seg.addView(b, ol);
        }
        LinearLayout.LayoutParams sgl = new LinearLayout.LayoutParams(-1, -2);
        sgl.topMargin = px(12);
        theme.addView(seg, sgl);
        col.addView(theme, rowLp());

        section(col, "GLYPH MATRIX");
        col.addView(toggle(Prefs.SCREEN_OFF_ONLY, "Only when screen is off",
                "The matrix goes dark while you're using the phone, and lights when the screen is off or the phone is face-down."), rowLp());
        col.addView(toggle(Prefs.POCKET, "Pocket mode",
                "Turns the Glyph Matrix off while the phone is in a pocket or bag. Face-down on a table still shows it."), rowLp());
        LinearLayout anim = card();
        anim.setGravity(Gravity.CENTER_VERTICAL);
        anim.setClickable(true);
        anim.setFocusable(true);
        anim.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, TimesUpActivity.class));
            }
        });
        LinearLayout at = new LinearLayout(this);
        at.setOrientation(LinearLayout.VERTICAL);
        TextView al = text("TIME'S UP ANIMATION", 10, GREY, Fonts.mono(this));
        al.setLetterSpacing(0.18f);
        at.addView(al);
        animValue = text("", 15, WHITE, Fonts.monoBold(this));
        LinearLayout.LayoutParams avl = new LinearLayout.LayoutParams(-1, -2);
        avl.topMargin = px(4);
        at.addView(animValue, avl);
        anim.addView(at, new LinearLayout.LayoutParams(0, -2, 1));
        anim.addView(text("›", 22, GREY, Fonts.mono(this)));
        col.addView(anim, rowLp());
        col.addView(toggle(Prefs.SYNC, "Sync with alarm sound",
                "Paces the animation to the alarm sound and flashes on every note."), rowLp());

        section(col, "WIDGETS");
        col.addView(toggle(Prefs.TRANSLUCENT, "Translucent background",
                "See-through widgets, like Nothing OS 5.0’s translucent style."), rowLp());

        section(col, "ALARM");
        LinearLayout sound = card();
        sound.setGravity(Gravity.CENTER_VERTICAL);
        sound.setClickable(true);
        sound.setFocusable(true);
        sound.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, SoundActivity.class));
            }
        });
        LinearLayout st = new LinearLayout(this);
        st.setOrientation(LinearLayout.VERTICAL);
        TextView sl = text("ALARM SOUND", 10, GREY, Fonts.mono(this));
        sl.setLetterSpacing(0.18f);
        st.addView(sl);
        soundValue = text("", 15, WHITE, Fonts.monoBold(this));
        LinearLayout.LayoutParams svl = new LinearLayout.LayoutParams(-1, -2);
        svl.topMargin = px(4);
        st.addView(soundValue, svl);
        sound.addView(st, new LinearLayout.LayoutParams(0, -2, 1));
        sound.addView(text("›", 22, GREY, Fonts.mono(this)));
        col.addView(sound, rowLp());
        col.addView(toggle(Prefs.VIBRATE, "Vibrate",
                "Vibrate along with the alarm sound when time's up."), rowLp());

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String ch = Palette.choice(this);
        themeSub.setText(Palette.PHONE.equals(ch)
                ? "For the app and widgets. Following your phone (Display › Appearance): "
                  + (Palette.phoneIsDark(this) ? "dark" : "light") + " right now."
                : "For the app and widgets. Always " + (Palette.DARK.equals(ch) ? "dark" : "light")
                  + ", whatever your phone is set to.");
        String src = Sounds.SRC_NOTHING.equals(Sounds.selectedSource(this)) ? "Nothing" : "Alarm";
        soundValue.setText(Sounds.selectedName(this) + "  · " + src);
        animValue.setText(Prefs.anim(this));
        animValue.setContentDescription("Time's up animation: " + Prefs.anim(this));
    }

    private void section(LinearLayout col, String label) {
        TextView t = text(label, 11, GREY, Fonts.mono(this));
        t.setLetterSpacing(0.18f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = px(28);
        col.addView(t, lp);
    }

    private LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = px(10);
        return lp;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setMinimumHeight(px(64));
        l.setPadding(px(16), px(14), px(16), px(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(20));
        bg.setColor(CARD);
        bg.setStroke(Math.max(1, px(1)), LINE);
        l.setBackground(bg);
        return l;
    }

    private View toggle(final String key, String title, String sub) {
        final LinearLayout row = card();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(px(72));
        row.setClickable(true);
        row.setFocusable(true);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(title, 15, WHITE, Fonts.monoBold(this)));
        TextView s = text(sub, 12, GREY, Fonts.mono(this));
        s.setLineSpacing(0, 1.3f);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
        sl.topMargin = px(4);
        texts.addView(s, sl);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        final Switch sw = new Switch(this, Prefs.get(this, key));
        LinearLayout.LayoutParams swl = new LinearLayout.LayoutParams(px(48), px(28));
        swl.leftMargin = px(16);
        row.addView(sw, swl);
        final String label = title;
        row.setContentDescription(label + ", " + (sw.on ? "on" : "off"));
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                sw.on = !sw.on;
                sw.invalidate();
                Prefs.set(SettingsActivity.this, key, sw.on);
                row.setContentDescription(label + ", " + (sw.on ? "on" : "off"));
                if (Prefs.TRANSLUCENT.equals(key)) WidgetUpdater.updateAll(SettingsActivity.this);
            }
        });
        return row;
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

    /** Red-when-on switch drawn to match the mockup. */
    static class Switch extends View {
        boolean on;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        Switch(android.content.Context c, boolean on) { super(c); this.on = on; }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setColor(on ? RED : OFF);
            r.set(0, 0, w, h);
            c.drawRoundRect(r, h / 2, h / 2, p);
            p.setColor(0xFFFFFFFF); // knob stays white in both themes
            float kr = h / 2 - h * 0.14f;
            c.drawCircle(on ? w - h / 2 : h / 2, h / 2, kr, p);
        }
    }
}
