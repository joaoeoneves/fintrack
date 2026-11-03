#!/usr/bin/env python3
"""Builds the README's composite images from the screenshots in docs/screenshots/.

Usage: readme_images.py   (run from anywhere; needs Pillow)

- docs/hero.png: Home, Income (year) and Spending (year) side by side, framed, on the app's dark background.
- docs/social-preview.png: 1280x640 card for GitHub's social preview (Settings > Social preview, uploaded by hand).
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
SHOTS = ROOT / "docs" / "screenshots"
FONTS = ROOT / "core" / "designsystem" / "src" / "main" / "res" / "font"
BG = (0, 0, 0)
SURFACE = (26, 26, 29)
GOLD = (255, 176, 32)
TEXT = (255, 255, 255)
MUTED = (160, 160, 166)


def phone(name, width):
    """A screenshot scaled to [width], with rounded corners and a thin frame."""
    shot = Image.open(SHOTS / f"{name}.png").convert("RGB")
    shot = shot.resize((width, round(shot.height * width / shot.width)), Image.Resampling.LANCZOS)
    radius = width // 11
    border = max(2, width // 90)
    framed = Image.new("RGBA", (shot.width + 2 * border, shot.height + 2 * border), (0, 0, 0, 0))
    ImageDraw.Draw(framed).rounded_rectangle(
        [0, 0, framed.width - 1, framed.height - 1], radius + border, fill=SURFACE + (255,))
    mask = Image.new("L", shot.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, shot.width - 1, shot.height - 1], radius, fill=255)
    framed.paste(shot, (border, border), mask)
    return framed


def hero():
    names = ["home", "income-year", "spending-year"]
    width, gap, pad = 420, 56, 64
    phones = [phone(n, width) for n in names]
    h = max(p.height for p in phones)
    canvas = Image.new("RGB", (sum(p.width for p in phones) + gap * 2 + pad * 2, h + pad * 2), BG)
    x = pad
    for p in phones:
        canvas.paste(p, (x, pad), p)
        x += p.width + gap
    canvas.save(ROOT / "docs" / "hero.png", optimize=True)


def social_preview():
    canvas = Image.new("RGB", (1280, 640), BG)
    draw = ImageDraw.Draw(canvas)
    icon = Image.open(ROOT / "docs" / "icon-512.png").convert("RGBA").resize((112, 112), Image.Resampling.LANCZOS)
    canvas.paste(icon, (80, 150), icon)
    bold = ImageFont.truetype(str(FONTS / "inter_bold.ttf"), 76)
    regular = ImageFont.truetype(str(FONTS / "inter_regular.ttf"), 32)
    draw.text((80, 290), "FinTrack", font=bold, fill=TEXT)
    lines = ["Real income vs expected.", "Real spending, month by month.", "No bank login, no account needed."]
    for i, line in enumerate(lines):
        draw.text((80, 400 + i * 46), line, font=regular, fill=MUTED if i else GOLD)
    for i, name in enumerate(["income-year", "home"]):
        p = phone(name, 250)
        canvas.paste(p, (720 + i * 270, 60 + (1 - i) * 40), p)
    canvas.save(ROOT / "docs" / "social-preview.png", optimize=True)


if __name__ == "__main__":
    hero()
    social_preview()
    print("docs/hero.png, docs/social-preview.png")
