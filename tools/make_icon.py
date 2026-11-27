"""Draws the Parallax app icon: half a solid disc beside half a disc of matrix dots, a step out of line.
Writes the adaptive-icon layers (light and dark foreground, monochrome for themed icons) and the
legacy launcher PNG. Run from the repository root: python3 tools/make_icon.py"""
import math
from PIL import Image, ImageDraw

SS = 4          # supersampling
FG = 432        # adaptive layer, 108 dp at xxxhdpi
LEGACY = 192    # mipmap-xxxhdpi launcher icon

# design grid: the 72 dp visible area of the adaptive icon (the 108 dp layer adds 18 dp all round)
R = 22.0        # disc radius
STEP = 3.0      # how far each half is pushed out of line (up-left / down-right)
N = 13          # matrix size, like the Phone (4a) Pro
DOT = 0.36      # dot radius as a share of the pitch


def shapes():
    """The solid half (as a polygon) and the dots, centred by ink in the 72 grid."""
    pitch = 2 * R / N
    mid = (N - 1) / 2
    reach = N / 2 + 0.1
    dots = []
    for r in range(N):
        for c in range(N):
            dr, dc = r - mid, c - mid
            if dr * dr + dc * dc > reach * reach or dc <= 0:
                continue
            dots.append((dc * pitch + STEP / 2, dr * pitch + STEP))
    half = [(-0.0 - STEP / 2 + R * math.cos(a), -STEP + R * math.sin(a))
            for a in [math.pi / 2 + math.pi * i / 180 for i in range(0, 181)]]
    # centre by ink: the solid half weighs its area, each dot its own area
    half_area = math.pi * R * R / 2
    half_cx = -STEP / 2 - 4 * R / (3 * math.pi)
    half_cy = -STEP
    dot_area = math.pi * (pitch * DOT) ** 2
    ax = half_area * half_cx + sum(x for x, _ in dots) * dot_area
    ay = half_area * half_cy + sum(y for _, y in dots) * dot_area
    total = half_area + dot_area * len(dots)
    ox, oy = 36 - ax / total, 35.5 - ay / total
    half = [(x + ox, y + oy) for x, y in half]
    dots = [(x + ox, y + oy) for x, y in dots]
    return half, dots, pitch * DOT


def draw(size, grid_offset, grid_scale, ink, bg=None, circle_mask=False):
    big = size * SS
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0) if bg is None else bg)
    d = ImageDraw.Draw(img)
    k = SS * grid_scale
    o = grid_offset
    half, dots, rd = shapes()
    d.polygon([((x + o) * k, (y + o) * k) for x, y in half], fill=ink)
    for x, y in dots:
        cx, cy, r = (x + o) * k, (y + o) * k, rd * k
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=ink)
    if circle_mask:
        mask = Image.new("L", (big, big), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, big - 1, big - 1], fill=255)
        out = Image.new("RGBA", (big, big), (0, 0, 0, 0))
        out.paste(img, (0, 0), mask)
        img = out
    return img.resize((size, size), Image.LANCZOS)


def main():
    s108 = FG / 108.0
    # adaptive foreground layers: 72 grid sits 18 dp in from the edge of the 108 dp layer
    draw(FG, 18, s108, (0x18, 0x1C, 0x1E, 255)).save("app/res/drawable-nodpi/ic_launcher_fg.png")
    draw(FG, 18, s108, (0xFF, 0xFF, 0xFF, 255)).save("app/res/drawable-night-nodpi/ic_launcher_fg.png")
    draw(FG, 18, s108, (0xFF, 0xFF, 0xFF, 255)).save("app/res/drawable-nodpi/ic_launcher_mono.png")
    # legacy icon: the 72 grid fills the circle
    draw(LEGACY, 0, LEGACY / 72.0, (0xFF, 0xFF, 0xFF, 255), bg=(0, 0, 0, 255), circle_mask=True) \
        .save("app/res/mipmap-xxxhdpi/ic_launcher.png")
    # a large copy for the website and README
    draw(512, 0, 512 / 72.0, (0xFF, 0xFF, 0xFF, 255), bg=(0, 0, 0, 255), circle_mask=True) \
        .save("docs/icon.png")
    draw(512, 0, 512 / 72.0, (0x18, 0x1C, 0x1E, 255), bg=(0xF2, 0xF3, 0xF4, 255), circle_mask=True)         .save("docs/icon-light.png")
    print("icon written")


if __name__ == "__main__":
    main()
