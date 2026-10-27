"""Render docs/icon-512.png (README / store image) from the "Ledger F" launcher icon geometry.

The geometry mirrors app/src/main/res/drawable/ic_launcher_foreground.xml (108-unit adaptive
viewport). Keep the two in sync. The adaptive icon is the only launcher icon shipped (minSdk 26),
so no legacy mipmap rasters are produced.

Usage: python3 -I scripts/render_launcher_icon.py   (needs Pillow)
"""

from pathlib import Path

from PIL import Image, ImageDraw

SIZE = 512
SUPERSAMPLE = 4
VISIBLE = 72  # visible part of the 108-unit adaptive viewport
OFFSET = 18  # (108 - 72) / 2
RADIUS = 2  # bar corner radius, in viewport units
BACKGROUND = (0, 0, 0, 255)
WHITE = (255, 255, 255, 255)
GOLD = (255, 176, 32, 255)

# (x, y, w, h) in viewport units: stem, top bar, middle bar.
BARS = [(36, 33.5, 9, 40), (36, 33.5, 34, 9), (36, 49.5, 24, 9)]
DOT_CENTER = (67, 69.5)
DOT_RADIUS = 5


def render(size: int) -> Image.Image:
    big = size * SUPERSAMPLE
    scale = big / VISIBLE

    def px(v: float) -> float:
        return (v - OFFSET) * scale

    image = Image.new("RGBA", (big, big), BACKGROUND)
    draw = ImageDraw.Draw(image)
    for x, y, w, h in BARS:
        draw.rounded_rectangle([px(x), px(y), px(x + w) - 1, px(y + h) - 1], radius=RADIUS * scale, fill=WHITE)
    cx, cy = DOT_CENTER
    r = DOT_RADIUS * scale
    draw.ellipse([px(cx) - r, px(cy) - r, px(cx) + r, px(cy) + r], fill=GOLD)
    return image.resize((size, size), Image.LANCZOS)


def main() -> None:
    root = Path(__file__).resolve().parent.parent
    out = root / "docs" / "icon-512.png"
    out.parent.mkdir(exist_ok=True)
    render(SIZE).save(out)
    print(f"wrote {out}")


if __name__ == "__main__":
    main()
