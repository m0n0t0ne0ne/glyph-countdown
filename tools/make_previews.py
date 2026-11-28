"""Draws the widget picker previews (idle state, 25:00 set) in light and dark: the Isometric Pad.
Geometry mirrors IsoPad.java (design units: 2x2 170 square, 2x1 170 x 80, 1x1 80 round).
Run from the repository root: python3 tools/make_previews.py"""
from PIL import Image, ImageDraw, ImageFont

THEMES = {
    "dark":  dict(pad=(0x14, 0x19, 0x1B), ink=(0xE9, 0xEE, 0xF0), grid=(0x24, 0x43, 0x4D), back=(0x4F, 0x87, 0x97), acc=(0xFF, 0x7A, 0x52), sub=(0x8E, 0x9A, 0xA0)),
    "light": dict(pad=(0xEE, 0xF2, 0xF3), ink=(0x1F, 0x24, 0x27), grid=(0xA9, 0xD3, 0xE0), back=(0x7F, 0xB3, 0xC4), acc=(0xD6, 0x4B, 0x2A), sub=(0x5A, 0x64, 0x68)),
}
S = 3  # supersampling
SEG = {"a": (0, 0, 1, 0), "b": (1, 0, 1, 1), "c": (1, 1, 1, 2), "d": (0, 2, 1, 2), "e": (0, 1, 0, 2), "f": (0, 0, 0, 1), "g": (0, 1, 1, 1)}
DIG = {"0": "abcdef", "1": "bc", "2": "abged", "3": "abgcd", "4": "fgbc", "5": "afgcd", "6": "afgedc", "7": "abc", "8": "abcdefg", "9": "abcdfg"}


def mono(size):
    return ImageFont.truetype("app/assets/fonts/SpaceMono-Regular.ttf", max(1, int(size)))


def width(t, s):
    return sum(.55 * s if ch == ":" else 1.45 * s for ch in t) - .45 * s


def pad(im, d, w, h, r, t, k, round_=False):
    if round_:
        d.ellipse([0, 0, w - 1, h - 1], fill=t["pad"])
    else:
        d.rounded_rectangle([0, 0, w - 1, h - 1], radius=r, fill=t["pad"])
    grid = Image.new("RGBA", (w, h), (0, 0, 0, 0)); g = ImageDraw.Draw(grid)
    step, lw, x = 10 * k, max(1, round(.5 * k)), -h * 1.732
    while x < w + h * 1.732:
        g.line([(x, 0), (x + h * 1.732, h)], fill=t["grid"] + (150,), width=lw)
        g.line([(x, 0), (x - h * 1.732, h)], fill=t["grid"] + (150,), width=lw)
        x += step
    mask = Image.new("L", (w, h), 0); md = ImageDraw.Draw(mask)
    (md.ellipse if round_ else (lambda b, **kw: md.rounded_rectangle(b, radius=r, **kw)))([0, 0, w - 1, h - 1], fill=255)
    im.paste(grid, (0, 0), Image.composite(grid, Image.new("RGBA", (w, h)), mask))


def wire(d, txt, x0, y0, s, dep, fw, t):
    dx, dy = dep * .866, -dep * .5
    for p in range(3):
        x = x0
        for ch in txt:
            if ch == ":":
                if p == 2:
                    for yy in (.6 * s, 1.4 * s):
                        r = fw * .8; d.ellipse([x + .15 * s - r, y0 + yy - r, x + .15 * s + r, y0 + yy + r], fill=t["ink"])
                x += .55 * s; continue
            for sg in DIG[ch]:
                a, b, c2, e = SEG[sg]; ax, ay, bx, by = x + a * s, y0 + b * s, x + c2 * s, y0 + e * s
                if p == 0: d.line([(ax + dx, ay + dy), (bx + dx, by + dy)], fill=t["back"], width=max(1, round(fw * .35)))
                if p == 1:
                    for px, py in ((ax, ay), (bx, by)): d.line([(px, py), (px + dx, py + dy)], fill=t["back"], width=max(1, round(fw * .23)))
                if p == 2: d.line([(ax, ay), (bx, by)], fill=t["ink"], width=round(fw))
            x += 1.45 * s


def make(theme, suffix):
    t = THEMES[theme]
    # 2x2
    W = 510 * S // 2; k = W / 170
    im = Image.new("RGBA", (W, W), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    pad(im, d, W, W, 22 * k, t, k); d = ImageDraw.Draw(im)
    s = 24; wire(d, "25:00", 20 * k, 46 * k, min(s, s * 104.4 / width("25:00", s)) * k, 18 * k, 2.6 * k, t)
    tw = width("25:00", min(s, s * 104.4 / width("25:00", s)))
    x1 = (20 + tw + 5) * k; d.line([(x1, 46 * k), (x1 + 18 * .866 * k, (46 - 9) * k)], fill=t["acc"], width=max(1, round(.8 * k)))
    f = mono(8 * k)
    d.text((20 * k, 124 * k), "LEFT", font=f, fill=t["ink"], anchor="ls"); d.text((150 * k, 124 * k), "25:00", font=f, fill=t["ink"], anchor="rs")
    d.line([(20 * k, 128 * k), (150 * k, 128 * k)], fill=t["ink"], width=max(1, round(.5 * k)))
    d.text((20 * k, 140 * k), "SET", font=f, fill=t["sub"], anchor="ls"); d.text((150 * k, 140 * k), "25:00", font=f, fill=t["sub"], anchor="rs")
    d.line([(20 * k, 144 * k), (150 * k, 144 * k)], fill=t["ink"], width=max(1, round(.5 * k)))
    d.text((20 * k, 160 * k), "Z = TIME LEFT", font=mono(7 * k), fill=t["sub"], anchor="ls")
    im.resize((510, 510), Image.LANCZOS).save(f"app/res/drawable-nodpi/widget_prev{suffix}_4.png")
    # 2x1
    W, H = 510 * S // 2, 240 * S // 2; k = W / 170
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    pad(im, d, W, H, 18 * k, t, k); d = ImageDraw.Draw(im)
    s = min(18, 18 * (110 - 14 - 12 * .866) / width("25:00", 18))
    wire(d, "25:00", 14 * k, 20 * k, s * k, 12 * k, 2.2 * k, t)
    d.text((156 * k, 34 * k), "SET", font=mono(7 * k), fill=t["sub"], anchor="rs"); d.text((156 * k, 48 * k), "25:00", font=mono(8 * k), fill=t["sub"], anchor="rs")
    im.resize((510, 240), Image.LANCZOS).save(f"app/res/drawable-nodpi/widget_prev{suffix}_2.png")
    # 1x1
    W = 240 * S // 2; k = W / 80
    im = Image.new("RGBA", (W, W), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    pad(im, d, W, W, W / 2, t, k, True); d = ImageDraw.Draw(im)
    s, dep = 12, 12; x = 40 - (width("25", s) + dep * .866) / 2
    wire(d, "25", x * k, (22 + dep * .25) * k, s * k, dep * k, 2.4 * k, t)
    d.text((40 * k, 70 * k), "MIN", font=mono(6.5 * k), fill=t["sub"], anchor="ms")
    im.resize((240, 240), Image.LANCZOS).save(f"app/res/drawable-nodpi/widget_prev{suffix}_1.png")


make("dark", "")
make("light", "_light")
print("previews written")
