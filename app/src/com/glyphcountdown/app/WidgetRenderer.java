package com.glyphcountdown.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Draws a widget as one bitmap (home screen widgets cannot host custom views). Since 2.3 every
 * size is the Isometric Pad (IsoPad): the time as a wireframe solid whose depth is the time left.
 * Tap areas are transparent views in res/layout/widget_*.xml: on the 2×2 and 2×1 the time starts
 * or pauses and the rest opens the app; the 1×1 toggles anywhere.
 */
final class WidgetRenderer {
    static final int K1 = 1, K2 = 2, K4 = 4;

    /** Colours for one appearance. */
    static final class Theme {
        final int card, cardTranslucent, fg, sub, dim, faint, track, btn, btnFg, line, red, redDim;
        Theme(int card, int cardTranslucent, int fg, int sub, int dim, int faint, int track,
              int btn, int btnFg, int line, int red, int redDim) {
            this.card = card; this.cardTranslucent = cardTranslucent; this.fg = fg; this.sub = sub;
            this.dim = dim; this.faint = faint; this.track = track; this.btn = btn; this.btnFg = btnFg;
            this.line = line; this.red = red; this.redDim = redDim;
        }
    }

    static final Theme DARK = new Theme(0xFF181C1E, 0x8C181C1E, 0xFFEEF1F3, 0xFFAEB4B9, 0xFF8E959A,
            0xFF3E454A, 0xFF2C3236, 0xFFEEF1F3, 0xFF181C1E, 0xFF596066, 0xFFFF4A4A, 0xFF4A2326);
    static final Theme LIGHT = new Theme(0xFFF2F3F4, 0x9EF2F3F4, 0xFF181C1E, 0xFF4F555A, 0xFF5E6468,
            0xFFC4C9CD, 0xFFD9DDE0, 0xFF181C1E, 0xFFF2F3F4, 0xFF9AA0A5, 0xFFD71921, 0xFFF6CFD1);
    private static final int ALARM_RED = 0xFFE8202A, ON_RED = 0xFFFFFFFF;

    private WidgetRenderer() {}

    /**
     * Draws one widget. colour: the colour picked in Settings › Widget colour (0 = the plain
     * drafting colours). It retints the grid, back edges and dimension; paused stays grey and
     * time's up stays red.
     */
    static Bitmap render(Context c, int kind, int w, int h, TimerState st, boolean dark, boolean translucent,
                         int colour) {
        Bitmap bm = Bitmap.createBitmap(Math.max(w, 1), Math.max(h, 1), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bm);
        // 2.3: every size is drawn as the Isometric Pad (see IsoPad)
        IsoPad.draw(c, cv, kind, w, h, st, IsoPad.ink(dark, colour, translucent, st));
        return bm;
    }

}
