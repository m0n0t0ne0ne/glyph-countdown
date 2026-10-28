package com.glyphcountdown.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.nothing.ketchum.Common;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything that happens when time's up, on one page: the Glyph Matrix animation, the alarm
 * sound, sound sync (stutter), dimming and vibration. A preview pinned at the top plays the
 * chosen animation with the chosen sound exactly as the alarm will. Changes save as they're made.
 */
public class TimesUpActivity extends Activity {
    private static final int RED = 0xFFE8202A;
    private static final long FRAME_MS = 40;

    private int BLACK, WHITE, GREY, LIGHT, CARD, LINE, BORDER, OFF, DIVIDER, DIM, RED_TEXT;
    private float dp;
    private int size = MatrixRenderer.SIZE;

    private MatrixView preview;
    private TextView previewName, previewSound, previewMode, playButton;
    private FlowLayout chips;
    private final TextView[] tabs = new TextView[2];
    private LinearLayout soundList;
    private LinearLayout syncGroup;
    private ToggleRow syncRow, dimRow, vibrateRow, pickupRow;
    private Slider slider;
    private TextView depthValue, syncNote;
    private View sliderCard;

    private boolean alarmTab;
    private MediaPlayer player;
    private int lastPos;
    private long repeats;
    private long clock0 = SystemClock.uptimeMillis();
    private volatile SoundSync.Profile profile;
    private String profileKey;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            preview.invalidate();
            handler.postDelayed(this, FRAME_MS);
        }
    };

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Palette pal = Palette.of(this);
        BLACK = pal.bg; WHITE = pal.fg; GREY = pal.grey; LIGHT = pal.light; CARD = pal.card; LINE = pal.line;
        BORDER = pal.border; OFF = pal.switchOff; DIVIDER = pal.divider; DIM = pal.dim; RED_TEXT = pal.redText;
        Palette.applyBars(this, pal);
        dp = getResources().getDisplayMetrics().density;
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        try { if (Common.is25111p()) size = MatrixRenderer.SIZE_SMALL; } catch (Throwable ignored) {}
        alarmTab = !Sounds.SRC_NOTHING.equals(Sounds.selectedSource(this));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BLACK);
        final LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        // header and the pinned preview stay put; everything else scrolls under them
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
        header.addView(text("Time's up", 28, WHITE, Fonts.doto(this)));
        top.addView(header);
        top.addView(previewCard(), lp(-1, -2, 12));

        ScrollView scroll = new ScrollView(this);
        final LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(col, new FrameLayout.LayoutParams(-1, -2));
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                android.graphics.Insets b = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                top.setPadding(px(24) + b.left, px(20) + b.top, px(24) + b.right, px(4));
                col.setPadding(px(24) + b.left, 0, px(24) + b.right, px(24) + b.bottom);
                return WindowInsets.CONSUMED;
            }
        });

        // animation
        section(col, "ANIMATION");
        chips = new FlowLayout(this, px(6));
        col.addView(chips, lp(-1, -2, 10));

        // sound
        section(col, "SOUND");
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
                    if (alarmTab != isAlarm) { alarmTab = isAlarm; renderSounds(); }
                }
            });
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, px(44), 1);
            if (i > 0) l.leftMargin = px(6);
            seg.addView(t, l);
            tabs[i] = t;
        }
        col.addView(seg, lp(-1, -2, 10));
        soundList = new LinearLayout(this);
        soundList.setOrientation(LinearLayout.VERTICAL);
        col.addView(soundList, lp(-1, -2, 6));

        // sync
        section(col, "SYNC");
        syncGroup = new LinearLayout(this);
        syncGroup.setOrientation(LinearLayout.VERTICAL);
        col.addView(syncGroup, lp(-1, -2, 0));
        syncRow = new ToggleRow("Sync with alarm sound", Prefs.SYNC);
        syncGroup.addView(syncRow.view, lp(-1, -2, 10));
        dimRow = new ToggleRow("Dim with notes", Prefs.DIM);
        syncGroup.addView(dimRow.view, lp(-1, -2, 10));
        syncGroup.addView(sliderCard(), lp(-1, -2, 10));
        syncNote = text("", 12, GREY, Fonts.mono(this));
        syncNote.setLineSpacing(0, 1.3f);
        col.addView(syncNote, lp(-1, -2, 10));

        // alarm
        section(col, "ALARM");
        vibrateRow = new ToggleRow("Vibrate", Prefs.VIBRATE);
        col.addView(vibrateRow.view, lp(-1, -2, 10));
        pickupRow = new ToggleRow("Pick up to dismiss", Prefs.PICKUP_DISMISS);
        col.addView(pickupRow.view, lp(-1, -2, 10));

        TextView foot = text("Changes save as you make them. Previews play at alarm volume.", 12, GREY, Fonts.mono(this));
        foot.setGravity(Gravity.CENTER);
        foot.setLineSpacing(0, 1.3f);
        col.addView(foot, lp(-1, -2, 24));

        setContentView(root);
        renderChips();
        renderSounds();
        refresh();
    }

    // ---- building blocks ---------------------------------------------------------------

    private View previewCard() {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(px(12), px(12), px(14), px(12));
        card.setBackground(rounded(CARD, LINE, 20));
        preview = new MatrixView(this);
        preview.setContentDescription("Preview of the Glyph Matrix");
        card.addView(preview, new LinearLayout.LayoutParams(px(140), px(140)));
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        TextView label = text("PREVIEW", 10, GREY, Fonts.mono(this));
        label.setLetterSpacing(0.18f);
        side.addView(label);
        previewName = text("", 22, WHITE, Fonts.doto(this));
        side.addView(previewName, lp(-1, -2, 6));
        previewSound = text("", 12, LIGHT, Fonts.mono(this));
        previewSound.setSingleLine(true);
        previewSound.setEllipsize(android.text.TextUtils.TruncateAt.END);
        side.addView(previewSound, lp(-1, -2, 6));
        previewMode = text("", 11, GREY, Fonts.mono(this));
        side.addView(previewMode, lp(-1, -2, 4));
        playButton = text("", 12, BLACK, Fonts.monoBold(this));
        playButton.setLetterSpacing(0.15f);
        playButton.setGravity(Gravity.CENTER);
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (player != null) stopSound(); else playSound(); refresh(); }
        });
        side.addView(playButton, lp(-1, px(40), 10));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, -2, 1);
        sl.leftMargin = px(14);
        card.addView(side, sl);
        return card;
    }

    private View sliderCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(px(16), px(12), px(16), px(12));
        card.setBackground(rounded(CARD, LINE, 20));
        LinearLayout head = new LinearLayout(this);
        head.addView(text("Dim level", 15, WHITE, Fonts.monoBold(this)), new LinearLayout.LayoutParams(0, -2, 1));
        depthValue = text("", 15, LIGHT, Fonts.monoBold(this));
        head.addView(depthValue);
        card.addView(head);
        slider = new Slider(this);
        card.addView(slider, lp(-1, px(36), 6));
        LinearLayout ends = new LinearLayout(this);
        ends.addView(text("Subtle", 11, GREY, Fonts.mono(this)), new LinearLayout.LayoutParams(0, -2, 1));
        ends.addView(text("Deep", 11, GREY, Fonts.mono(this)));
        card.addView(ends);
        sliderCard = card;
        return card;
    }

    private void renderChips() {
        chips.removeAllViews();
        String sel = Prefs.anim(this);
        for (final String name : MorphEngine.NAMES) {
            boolean on = name.equals(sel);
            TextView c = text(name, 13, on ? BLACK : LIGHT, Fonts.monoBold(this));
            c.setGravity(Gravity.CENTER);
            c.setPadding(px(14), 0, px(14), 0);
            c.setBackground(pill(on ? WHITE : Color.TRANSPARENT, on ? WHITE : BORDER));
            c.setContentDescription("Animation " + name + (on ? ", selected" : ""));
            c.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Prefs.setAnim(TimesUpActivity.this, name);
                    clock0 = SystemClock.uptimeMillis();
                    renderChips();
                    refresh();
                }
            });
            chips.addView(c, new ViewGroup.LayoutParams(-2, px(36)));
        }
    }

    private void renderSounds() {
        for (int i = 0; i < 2; i++) {
            boolean on = (i == 1) == alarmTab;
            tabs[i].setBackground(pill(on ? WHITE : Color.TRANSPARENT, 0));
            tabs[i].setTextColor(on ? BLACK : LIGHT);
            tabs[i].setSelected(on);
        }
        soundList.removeAllViews();
        List<Sounds.Item> items = Sounds.list(this, alarmTab ? RingtoneManager.TYPE_ALARM : RingtoneManager.TYPE_NOTIFICATION);
        String selUri = Sounds.selectedUriString(this);
        boolean defaultOnThisTab = alarmTab != Sounds.SRC_NOTHING.equals(Sounds.selectedSource(this));
        for (final Sounds.Item item : items) {
            boolean selected = item.uri == null ? selUri.isEmpty() && defaultOnThisTab : item.uri.toString().equals(selUri);
            soundList.addView(soundRow(item, selected));
        }
    }

    private View soundRow(final Sounds.Item item, boolean selected) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(px(52));
        r.setPadding(px(4), 0, px(4), 0);
        r.setClickable(true);
        r.setFocusable(true);
        r.setContentDescription(item.title + (selected ? ", selected" : ""));
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { chooseSound(item); }
        });
        r.addView(new Radio(this, selected), new LinearLayout.LayoutParams(px(20), px(20)));
        TextView name = text(item.title, 15, WHITE, Fonts.mono(this));
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, -2, 1);
        nl.leftMargin = px(16);
        r.addView(name, nl);
        if (selected && player != null) {
            TextView p = text("♪ PLAYING", 11, RED_TEXT, Fonts.mono(this));
            p.setLetterSpacing(0.12f);
            r.addView(p);
        }
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(r, new LinearLayout.LayoutParams(-1, -2));
        View line = new View(this);
        line.setBackgroundColor(DIVIDER);
        wrap.addView(line, new LinearLayout.LayoutParams(-1, Math.max(1, px(1))));
        return wrap;
    }

    private void chooseSound(Sounds.Item item) {
        Sounds.save(this, item.uri == null
                ? new Sounds.Item("Device default", null, alarmTab ? Sounds.SRC_ALARM : Sounds.SRC_NOTHING) : item);
        stopSound();
        playSound(); // picking a sound plays it, with the animation in step
        refresh();
    }

    /** Update everything that depends on the saved choices. */
    private void refresh() {
        String anim = Prefs.anim(this);
        boolean isDefault = MorphEngine.DEFAULT.equals(anim);
        boolean sync = Prefs.get(this, Prefs.SYNC), dim = Prefs.get(this, Prefs.DIM);
        int depth = Prefs.dimDepth(this);
        previewName.setText(anim);
        String src = Sounds.SRC_NOTHING.equals(Sounds.selectedSource(this)) ? "Nothing" : "Alarm";
        previewSound.setText(Sounds.selectedName(this) + " · " + src);
        previewMode.setText(modeLine(this));
        playButton.setText(player != null ? "■  STOP" : "▶  PLAY");
        playButton.setTextColor(player != null ? 0xFFFFFFFF : BLACK);
        playButton.setBackground(pill(player != null ? RED : WHITE, 0));
        playButton.setContentDescription(player != null ? "Stop preview" : "Play preview with sound");

        syncRow.set(sync, sync ? "Stutters on every note; starts with the sound each loop." : "Off: the animation plays at its own pace.");
        dimRow.set(dim, dim ? "Blinks brighter and darker with the notes." : "Off: stutter only, at full brightness.");
        depthValue.setText(depth + "%");
        slider.value = depth;
        slider.invalidate();
        slider.setContentDescription("Dim level, " + depth + " percent");

        syncGroup.setAlpha(isDefault ? 0.35f : 1f);
        syncRow.enabled = !isDefault;
        dimRow.enabled = !isDefault && sync;
        dimRow.view.setAlpha(!isDefault && !sync ? 0.35f : 1f);
        slider.enabled = !isDefault && sync && dim;
        sliderCard.setAlpha(!isDefault && !(sync && dim) ? 0.35f : 1f);
        syncNote.setText(isDefault ? "Default flashes on its own, so sync is off while it's picked."
                : "Sync and dimming apply to all ten animations.");
        vibrateRow.set(Prefs.get(this, Prefs.VIBRATE), "Vibrate along with the alarm sound.");
        pickupRow.set(Prefs.get(this, Prefs.PICKUP_DISMISS),
                "Picking the phone up off the table, or turning its screen on, stops the alarm.");
        ensureProfile();
    }

    /** "Stutter · dim 80%" and so on, for the preview and the Settings summary. */
    static String modeLine(Context c) {
        if (MorphEngine.DEFAULT.equals(Prefs.anim(c))) return "Flashing ring";
        if (!Prefs.get(c, Prefs.SYNC)) return "Own pace";
        return Prefs.get(c, Prefs.DIM) ? "Stutter · dim " + Prefs.dimDepth(c) + "%" : "Stutter · full brightness";
    }

    /** One-line summary for the Settings row. */
    static String summary(Context c) {
        return Prefs.anim(c) + " · " + Sounds.selectedName(c) + " · " + modeLine(c)
                + (Prefs.get(c, Prefs.VIBRATE) ? " · Vibrate" : "");
    }

    // ---- sound and sync ------------------------------------------------------------------

    private void ensureProfile() {
        final Uri uri = Sounds.alarmUri(this);
        if (uri == null) return;
        final String key = uri.toString();
        if (key.equals(profileKey)) return;
        profileKey = key;
        profile = SoundSync.cached(uri);
        if (profile != null) return;
        final Context app = getApplicationContext();
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                final SoundSync.Profile p = SoundSync.analyze(app, uri);
                handler.post(new Runnable() {
                    @Override public void run() { if (key.equals(profileKey)) profile = p; }
                });
            }
        }, "sound-sync-preview");
        t.setDaemon(true);
        t.start();
    }

    private void playSound() {
        try {
            MediaPlayer mp = new MediaPlayer();
            try {
                mp.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                mp.setDataSource(this, Sounds.alarmUri(this));
                mp.setLooping(true);
                mp.prepare();
                mp.start();
                player = mp;
                lastPos = 0;
                repeats = 0;
            } catch (Throwable t) {
                mp.release();
            }
        } catch (Throwable ignored) {}
        renderSounds();
    }

    private void stopSound() {
        try { if (player != null) { player.stop(); player.release(); } } catch (Throwable ignored) {}
        player = null;
        clock0 = SystemClock.uptimeMillis();
        renderSounds();
    }

    /** The preview frame: follows the playing sound, or a silent clock when nothing is playing. */
    private int[] previewFrame() {
        String anim = Prefs.anim(this);
        long now = SystemClock.uptimeMillis(), elapsed = now - clock0;
        SoundSync.Profile prof = profile;
        boolean sync = Prefs.get(this, Prefs.SYNC) && prof != null;
        long pos = 0, rep = 0;
        if (sync) {
            int p = -1;
            if (player != null) try { p = player.getCurrentPosition(); } catch (Throwable ignored) {}
            if (p >= 0) {
                if (p + 300 < lastPos) repeats++;
                lastPos = p;
                pos = p;
                rep = repeats;
            } else {
                rep = elapsed / prof.lengthMs;
                pos = elapsed % prof.lengthMs;
            }
        }
        return SoundSync.frame(anim, size, prof, sync, Prefs.get(this, Prefs.DIM), Prefs.dimDepth(this) / 100.0,
                rep, pos, elapsed);
    }

    @Override protected void onResume() { super.onResume(); handler.post(tick); }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        if (player != null) { stopSound(); refresh(); }
        super.onPause();
    }

    // ---- helpers -------------------------------------------------------------------------

    private void section(LinearLayout col, String label) {
        TextView t = text(label, 11, GREY, Fonts.mono(this));
        t.setLetterSpacing(0.18f);
        col.addView(t, lp(-1, -2, 24));
    }

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

    private GradientDrawable pill(int fill, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(px(999));
        d.setColor(fill);
        if (stroke != 0) d.setStroke(Math.max(1, px(1)), stroke);
        return d;
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(px(radiusDp));
        d.setColor(fill);
        d.setStroke(Math.max(1, px(1)), stroke);
        return d;
    }

    /** A card with a title, a line of explanation and a red-when-on switch, saved to a pref. */
    private final class ToggleRow {
        final LinearLayout view;
        final TextView sub;
        final SwitchView sw;
        final String title;
        boolean enabled = true;

        ToggleRow(String title, final String key) {
            this.title = title;
            view = new LinearLayout(TimesUpActivity.this);
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setMinimumHeight(px(72));
            view.setPadding(px(16), px(14), px(16), px(14));
            view.setBackground(rounded(CARD, LINE, 20));
            view.setClickable(true);
            view.setFocusable(true);
            LinearLayout texts = new LinearLayout(TimesUpActivity.this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.addView(text(title, 15, WHITE, Fonts.monoBold(TimesUpActivity.this)));
            sub = text("", 12, GREY, Fonts.mono(TimesUpActivity.this));
            sub.setLineSpacing(0, 1.3f);
            texts.addView(sub, lp(-1, -2, 4));
            view.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
            sw = new SwitchView(TimesUpActivity.this);
            LinearLayout.LayoutParams swl = new LinearLayout.LayoutParams(px(48), px(28));
            swl.leftMargin = px(16);
            view.addView(sw, swl);
            view.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (!enabled) return;
                    Prefs.set(TimesUpActivity.this, key, !Prefs.get(TimesUpActivity.this, key));
                    refresh();
                }
            });
        }

        void set(boolean on, String subText) {
            sw.on = on;
            sw.invalidate();
            sub.setText(subText);
            view.setContentDescription(title + ", " + (on ? "on" : "off"));
        }
    }

    /** Red-when-on switch, matching the Settings screen. */
    private final class SwitchView extends View {
        boolean on;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        SwitchView(Context c) { super(c); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setColor(on ? RED : OFF);
            r.set(0, 0, w, h);
            c.drawRoundRect(r, h / 2, h / 2, p);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(on ? w - h / 2 : h / 2, h / 2, h / 2 - h * 0.14f, p);
        }
    }

    /** Dim level, 0..100 in steps of 5. Saves as you drag. */
    private final class Slider extends View {
        int value;
        boolean enabled = true;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Slider(Context c) { super(c); setFocusable(true); }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), knob = 11 * dp, x0 = knob, x1 = w - knob, cy = h / 2;
            float x = x0 + (x1 - x0) * value / 100f;
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(4 * dp);
            p.setColor(OFF);
            c.drawLine(x0, cy, x1, cy, p);
            p.setColor(RED);
            c.drawLine(x0, cy, x, cy, p);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(x, cy, knob, p);
            p.setColor(BORDER);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1 * dp);
            c.drawCircle(x, cy, knob, p);
            p.setStyle(Paint.Style.FILL);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (!enabled) return false;
            if (e.getAction() == MotionEvent.ACTION_DOWN) getParent().requestDisallowInterceptTouchEvent(true);
            float knob = 11 * dp, f = (e.getX() - knob) / (getWidth() - 2 * knob);
            int v = Math.round(Math.max(0, Math.min(1, f)) * 20) * 5;
            if (v != value) {
                value = v;
                Prefs.setDimDepth(TimesUpActivity.this, v);
                depthValue.setText(v + "%");
                previewMode.setText(modeLine(TimesUpActivity.this));
                setContentDescription("Dim level, " + v + " percent");
                invalidate();
            }
            if (e.getAction() == MotionEvent.ACTION_UP) refresh();
            return true;
        }
    }

    /** Ring with a red centre dot when selected. */
    private final class Radio extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean on;
        Radio(Context c, boolean on) { super(c); this.on = on; }
        @Override protected void onDraw(Canvas c) {
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

    /** The Glyph Matrix as it looks on the back of the phone, LED brightness included. */
    private final class MatrixView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        MatrixView(Context c) { super(c); }
        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), d = Math.min(w, h);
            p.setColor(0xFF000000);
            cv.drawCircle(w / 2, h / 2, d / 2, p);
            int n = size;
            int[] px = previewFrame();
            float cell = d * 0.9f / n, x0 = (w - cell * n) / 2, y0 = (h - cell * n) / 2, rad = cell * 0.36f;
            for (int r = 0; r < n; r++) {
                for (int c = 0; c < n; c++) {
                    if (!MorphEngine.onMatrix(n, r, c)) continue;
                    int v = px[r * n + c];
                    // show perceived brightness, as the eye sees the LED
                    int g = v == 0 ? 0x1C : Math.max(0x24, (int) Math.round(255 * SoundSync.fromLed(v)));
                    p.setColor(0xFF000000 | (g << 16) | (g << 8) | g);
                    cv.drawCircle(x0 + (c + 0.5f) * cell, y0 + (r + 0.5f) * cell, rad, p);
                }
            }
        }
    }

    /** Lays children out left to right, wrapping onto new rows. */
    private static final class FlowLayout extends ViewGroup {
        private final int gap;
        FlowLayout(Context c, int gap) { super(c); this.gap = gap; }

        @Override protected void onMeasure(int ws, int hs) {
            int width = MeasureSpec.getSize(ws), x = 0, y = 0, rowH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                v.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(v.getLayoutParams().height, MeasureSpec.EXACTLY));
                if (x > 0 && x + v.getMeasuredWidth() > width) { x = 0; y += rowH + gap; rowH = 0; }
                x += v.getMeasuredWidth() + gap;
                rowH = Math.max(rowH, v.getMeasuredHeight());
            }
            setMeasuredDimension(width, y + rowH);
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int width = r - l, x = 0, y = 0, rowH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                int vw = v.getMeasuredWidth(), vh = v.getMeasuredHeight();
                if (x > 0 && x + vw > width) { x = 0; y += rowH + gap; rowH = 0; }
                v.layout(x, y, x + vw, y + vh);
                x += vw + gap;
                rowH = Math.max(rowH, vh);
            }
        }
    }
}
