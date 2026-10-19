"""Draws the widget picker previews (idle state, 25:00) in light and dark.
Geometry mirrors WidgetRenderer.java. Run from the repository root: python3 tools/make_previews.py"""
import math
from PIL import Image, ImageDraw, ImageFont

THEMES = {
    "dark":  dict(card=(0x18, 0x1C, 0x1E), fg=(0xEE, 0xF1, 0xF3), grey=(0x8A, 0x8F, 0x93),
                  unlit=(0x3A, 0x3F, 0x42), ring=(0x4A, 0x50, 0x54)),
    "light": dict(card=(0xF2, 0xF3, 0xF4), fg=(0x18, 0x1C, 0x1E), grey=(0x5E, 0x64, 0x68),
                  unlit=(0xCD, 0xD1, 0xD4), ring=(0xB8, 0xBD, 0xC1)),
}
S = 2  # supersampling


def doto(size):
    f = ImageFont.truetype("app/assets/fonts/Doto.ttf", int(size))
    try:
        f.set_variation_by_name(b"Black")
    except Exception:
        pass
    return f


def mono(size):
    return ImageFont.truetype("app/assets/fonts/SpaceMono-Regular.ttf", int(size))


def ring(d, cx, cy, outer, n, maj, mn, st, color):
    every = n // 12
    for i in range(n):
        ln = maj if i % every == 0 else mn
        a = 2 * math.pi * i / n
        sx, sy = math.sin(a), -math.cos(a)
        d.line([cx + sx * outer, cy + sy * outer, cx + sx * (outer - ln), cy + sy * (outer - ln)],
               fill=color, width=max(2, int(st)))


def play(d, cx, cy, r, bg, fg):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=bg)
    s = r / 3
    d.polygon([(cx - s * .7, cy - s), (cx - s * .7, cy + s), (cx + s, cy)], fill=fg)


def make(theme, suffix):
    t = THEMES[theme]
    # 1×1 circle
    k = 3; w = h = 240
    im = Image.new("RGBA", (w * S, h * S), (0, 0, 0, 0)); d = ImageDraw.Draw(im); c = w * S / 2
    d.ellipse([0, 0, w * S - 1, h * S - 1], fill=t["card"])
    ring(d, c, c, (w / 2 - 5 * k) * S, 30, 8 * k * S, 6 * k * S, 2 * k * S, t["unlit"])
    d.text((c, c - 3 * k * S), "25", font=doto(24 * k * S), fill=t["fg"], anchor="mm")
    d.text((c, c + 14 * k * S), "MIN", font=mono(9 * k * S), fill=t["grey"], anchor="mm")
    im.resize((w, h), Image.LANCZOS).save(f"app/res/drawable-nodpi/widget_prev{suffix}_1.png")
    # 2×1 pill
    k = 3; w, h = 510, 240
    im = Image.new("RGBA", (w * S, h * S), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, w * S - 1, h * S - 1], radius=h * S / 2, fill=t["card"])
    rw = w * .4; rr = min(rw, h) * .4; cxu = h / 2 if h / 2 + rr <= rw else rw / 2
    cx, cy, rrs = cxu * S, h / 2 * S, rr * S
    ring(d, cx, cy, rrs, 30, 7 * k * S, 5 * k * S, 2 * k * S, t["unlit"])
    play(d, cx, cy, 18 * k * S, t["fg"], t["card"])
    x0 = max(rw * S, cx + rrs + 8 * k * S)
    d.text((x0, cy - k * S), "25:00", font=doto(26 * k * S), fill=t["fg"], anchor="ls")
    d.text((x0, cy + 16 * k * S), "TAP TO START", font=mono(9 * k * S), fill=t["grey"], anchor="ls")
    im.resize((w, h), Image.LANCZOS).save(f"app/res/drawable-nodpi/widget_prev{suffix}_2.png")
    # 2×2 rounded square
    k = 3; w = h = 510
    im = Image.new("RGBA", (w * S, h * S), (0, 0, 0, 0)); d = ImageDraw.Draw(im); c = w * S / 2
    d.rounded_rectangle([0, 0, w * S - 1, h * S - 1], radius=w * S * 0.19, fill=t["card"])
    ring(d, c, c, (w / 2 - 6 * k) * S, 60, 12 * k * S, 8 * k * S, 2 * k * S, t["unlit"])
    d.text((c, c - 20 * k * S), "25:00", font=doto(30 * k * S), fill=t["fg"], anchor="mm")
    d.text((c, c + 2 * k * S), "TAP TO START", font=mono(9 * k * S), fill=t["grey"], anchor="mm")
    by = h * .70 * S; br = 16 * k * S; x1 = w * .375 * S
    d.ellipse([x1 - br, by - br, x1 + br, by + br], outline=t["ring"], width=max(2, k * S))
    r = 6 * k * S
    d.arc([x1 - r, by - r, x1 + r, by + r], 200, 130, fill=t["fg"], width=2 * k * S)
    play(d, w * .625 * S, by, br, t["fg"], t["card"])
    im.resize((w, h), Image.LANCZOS).save(f"app/res/drawable-nodpi/widget_prev{suffix}_4.png")


make("dark", "")
make("light", "_light")
print("previews written")
