#!/usr/bin/env python3
"""Writes the README's static badges (art/badges/*.svg), so the README loads no image from an
outside service for them. Run from anywhere: python3 art/badges/generate.py

Flat, two-part badges (a grey label, a coloured message), 20 px high, Verdana 11 px like the usual
shields. Text widths are measured with DejaVu Sans (Verdana's metric twin) and fixed with
`textLength`, so every renderer draws the same box. Needs Pillow.
"""

from pathlib import Path
from xml.sax.saxutils import escape

from PIL import ImageFont

FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
HEIGHT = 20
PAD = 6
LABEL_COLOR = "#555"
FONT_SIZE = 11
SCALE = 10  # SVG text is drawn at 10x and scaled down, for sub-pixel placement

# file name -> (label, message, message colour)
BADGES = {
    "license": ("license", "GPL-3.0-or-later", "#007ec6"),
    "android": ("Android", "13+", "#2e9e5b"),
}


def text_width(font: ImageFont.FreeTypeFont, text: str) -> int:
    return round(font.getlength(text))


def badge(label: str, message: str, color: str) -> str:
    font = ImageFont.truetype(FONT, FONT_SIZE)
    label_w = text_width(font, label) + 2 * PAD
    message_w = text_width(font, message) + 2 * PAD
    total = label_w + message_w
    name = escape(f"{label}: {message}")

    def part(text: str, x: float, width: int) -> str:
        t = escape(text)
        args = f'x="{x * SCALE:.0f}" textLength="{width * SCALE}" transform="scale(.1)"'
        return (
            f'<text aria-hidden="true" {args} y="150" fill="#010101" fill-opacity=".3">{t}</text>'
            f'<text {args} y="140">{t}</text>'
        )

    label_text = part(label, label_w / 2, label_w - 2 * PAD)
    message_text = part(message, label_w + message_w / 2, message_w - 2 * PAD)
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{total}" height="{HEIGHT}" role="img" aria-label="{name}">\n'
        f"<title>{name}</title>\n"
        '<linearGradient id="s" x2="0" y2="100%"><stop offset="0" stop-color="#bbb" stop-opacity=".1"/>'
        '<stop offset="1" stop-opacity=".1"/></linearGradient>\n'
        f'<clipPath id="r"><rect width="{total}" height="{HEIGHT}" rx="3" fill="#fff"/></clipPath>\n'
        f'<g clip-path="url(#r)"><rect width="{label_w}" height="{HEIGHT}" fill="{LABEL_COLOR}"/>'
        f'<rect x="{label_w}" width="{message_w}" height="{HEIGHT}" fill="{color}"/>'
        f'<rect width="{total}" height="{HEIGHT}" fill="url(#s)"/></g>\n'
        '<g fill="#fff" text-anchor="middle" font-family="Verdana,Geneva,DejaVu Sans,sans-serif" '
        f'text-rendering="geometricPrecision" font-size="{FONT_SIZE * SCALE}">'
        f"{label_text}{message_text}</g>\n</svg>\n"
    )


if __name__ == "__main__":
    out = Path(__file__).parent
    for file, (label, message, color) in BADGES.items():
        (out / f"{file}.svg").write_text(badge(label, message, color), encoding="utf-8")
