"""Draws the widget picker previews (idle state, 25:00) in light and dark.
Geometry mirrors WidgetRenderer.java (design units: 2×2 255 square, 2×1 255 × 120, 1×1 120 round).
Run from the repository root: python3 tools/make_previews.py"""
import math
from PIL import Image, ImageDraw, ImageFont

THEMES = {
    "dark":  dict(card=(0x18, 0x1C, 0x1E), fg=(0xEE, 0xF1, 0xF3), sub=(0xAE, 0xB4, 0xB9), dim=(0x8E, 0x95, 0x9A),
                  faint=(0x3E, 0x45, 0x4A), line=(0x59, 0x60, 0x66), btn=(0xEE, 0xF1, 0xF3), btnfg=(0x18, 0x1C, 0x1E)),
    "light": dict(card=(0xF2, 0xF3, 0xF4), fg=(0x18, 0x1C, 0x1E), sub=(0x4F, 0x55, 0x5A), dim=(0x5E, 0x64, 0x68),
                  faint=(0xC4, 0xC9, 0xCD), line=(0x9A, 0xA0, 0xA5), btn=(0x18, 0x1C, 0x1E), btnfg=(0xF2, 0xF3, 0xF4)),
}
S = 2  # supersampling


def doto(size):
    f = ImageFont.truetype("app/assets/fonts/Doto.ttf", int(size))
    try:
        f.set_variation_by_name(b"Black")
    except Exception:
        pass
    return f


def mono(size, bold=False):
    return ImageFont.truetype("app/assets/fonts/SpaceMono-%s.ttf" % ("Bold" if bold else "Regular"), int(size))


def rows3(d, t, x, y, size, gap, usize, ugap):
    vals, units = ["00", "25", "00"], ["HR", "MIN", "SEC"]
    cols = [t["faint"], t["fg"], t["dim"]]
    ucols = [t["faint"], t["sub"], t["sub"]]
    f, uf = doto(size), mono(usize)
    dw = d.textlength("00", font=f)
    row = size * 0.92
    for i in range(3):
        base = y + row * (i + 1) + gap * i - size * 0.08
        d.text((x, base), vals[i], font=f, fill=cols[i], anchor="ls")
        d.text((x + dw + ugap, base - size * 0.06), units[i], font=uf, fill=ucols[i], anchor="ls")


def rows_spread(d, t, x, right, top, bottom, size, usize, ugap):
    """HR / MIN / SEC spread so the first digits' top sits on top and the last's bottom on bottom."""
    vals, units = ["00", "25", "00"], ["HR", "MIN", "SEC"]
    cols = [t["faint"], t["fg"], t["dim"]]
    ucols = [t["faint"], t["sub"], t["sub"]]
    uf = mono(usize)
    f = doto(size)
    need = d.textlength("00", font=f) + ugap + d.textlength("MIN", font=uf)
    if need > right - x:
        size *= (right - x) / need
        f = doto(size)
    l, tp, r, b = d.textbbox((0, 0), "00", font=f, anchor="ls")
    gh = b - tp
    gap = (bottom - top - 3 * gh) / 2
    dw = d.textlength("00", font=f)
    for i in range(3):
        base = top + i * (gh + gap) - tp
        d.text((x, base), vals[i], font=f, fill=cols[i], anchor="ls")
        d.text((x + dw + ugap, base - usize * 0.1), units[i], font=uf, fill=ucols[i], anchor="ls")


def pills(d, t, x, y, w, ph, gap, tgap):
    tw = (w - 2 * tgap) / 3
    for p in range(10):
        py = y + p * (ph + gap)
        # all thirds lit in the idle state; round the pill's two ends
        d.rounded_rectangle([x, py, x + w, py + ph], radius=ph / 2, fill=t["fg"])
        for j in (1, 2):
            gx = x + j * tw + (j - 1) * tgap
            d.rectangle([gx, py, gx + tgap, py + ph], fill=t["card"])


def play(d, cx, cy, r, color):
    d.polygon([(cx - r * .55, cy - r * .8), (cx - r * .55, cy + r * .8), (cx + r * .8, cy)], fill=color)


def make(theme, suffix):
    t = THEMES[theme]
    # 2×2
    w = h = 510; k = w / 255 * S
    im = Image.new("RGBA", (w * S, h * S), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, w * S - 1, h * S - 1], radius=48 * k, fill=t["card"])
    # no buttons: tap the time to start or pause, the pills to open the app (Reset shows only when paused)
    px = w * S - 22 * k - 64 * k
    rows_spread(d, t, 24 * k, px - 10 * k, 22 * k, 22 * k + 150 * k + 9 * 6.2 * k, 72 * k, 11 * k, 8 * k)
    pills(d, t, px, 22 * k, 64 * k, 15 * k, 6.2 * k, 3 * k)
    im.resize((w, h), Image.LANCZOS).save(f"app/res/drawable-nodpi/widget_prev{suffix}_4.png")
    # 2×1
    w, h = 510, 240; k = min(h / 120, w / 255) * S
    im = Image.new("RGBA", (w * S, h * S), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, w * S - 1, h * S - 1], radius=36 * k, fill=t["card"])
    top = (h * S - 86 * k) / 2
    rows3(d, t, 22 * k, top, 28 * k, 2 * k, 10 * k, 6 * k)
    bw, bh = 72 * k, min(80 * k, h * S - 32 * k)
    bx = w * S - 20 * k - bw
    pills(d, t, bx - 13 * k - 50 * k, top, 50 * k, 6 * k, 3 * k, 2 * k)
    by = (h * S - bh) / 2
    d.rounded_rectangle([bx, by, bx + bw, by + bh], radius=24 * k, fill=t["btn"])
    play(d, bx + bw / 2, by + bh / 2 - 8 * k, 10 * k, t["btnfg"])
    d.text((bx + bw / 2, by + bh / 2 + 10 * k), "START", font=mono(10 * k, True), fill=t["btnfg"], anchor="mt")
    im.resize((w, h), Image.LANCZOS).save(f"app/res/drawable-nodpi/widget_prev{suffix}_2.png")
    # 1×1
    w = h = 240; u = w / 120 * S; c = w * S / 2
    im = Image.new("RGBA", (w * S, h * S), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.ellipse([0, 0, w * S - 1, h * S - 1], fill=t["card"])
    R, st = 51 * u, 8 * u
    span = (30 - 4) / 3
    for kk in range(30):
        p, j = divmod(kk, 3)
        a0 = p * 36 + 3 + j * (span + 2)
        d.arc([c - R, c - R, c + R, c + R], a0 - 90, a0 - 90 + span, fill=t["fg"], width=int(st))
    f, uf = doto(24 * u), mono(9 * u)
    dw = d.textlength("00", font=f); uw = d.textlength("MIN", font=uf)
    x0 = c - (dw + 4 * u + uw) / 2
    y0 = c - (22 * u * 2 + 3 * u + 13 * u) / 2
    for i, (v, un, col) in enumerate([("25", "MIN", t["fg"]), ("00", "SEC", t["dim"])]):
        base = y0 + 22 * u * (i + 1) + 3 * u * i - 2 * u
        d.text((x0, base), v, font=f, fill=col, anchor="ls")
        d.text((x0 + dw + 4 * u, base - u), un, font=uf, fill=t["sub"], anchor="ls")
    play(d, c, y0 + 22 * u * 2 + 3 * u + 8 * u, 5 * u, t["sub"])
    im.resize((w, h), Image.LANCZOS).save(f"app/res/drawable-nodpi/widget_prev{suffix}_1.png")


make("dark", "")
make("light", "_light")
print("previews written")
