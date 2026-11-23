"""The birthday icon for 2.3.1 (Birthday Bash): MargyT's mint note in a burst of confetti.

Writes icons/party/{background,foreground,full,monochrome}.png at 1024 px, in the 108-unit adaptive-icon square:
the background is the mint, the note keeps its own place, the confetti stays inside the middle 72 units a launcher
never cuts, and the monochrome layer is the note with a few of the bits for phones that tint icons.

python3 assets/make_confetti_icon.py
"""
import math
import os
import random

from PIL import Image, ImageChops, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
S = 1024
OUT = os.path.join(ROOT, "icons", "party")
MINT = (0x8D, 0xD1, 0xB0, 255)
COLOURS = [(242, 96, 124), (255, 206, 84), (110, 140, 250), (186, 120, 246), (255, 150, 70), (255, 255, 255)]
SAFE = S * 36 / 108          # the safe zone's radius: 36 of 108 units


def bit(kind, colour, size, angle):
    """One piece of confetti, drawn big and turned."""
    w = int(size * 3)
    im = Image.new("RGBA", (w, w), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = w // 2
    fill = colour + (255,)
    if kind == "strip":
        d.rounded_rectangle([c - size * 0.9, c - size * 0.26, c + size * 0.9, c + size * 0.26], radius=size * 0.2, fill=fill)
    elif kind == "dot":
        d.ellipse([c - size * 0.42, c - size * 0.42, c + size * 0.42, c + size * 0.42], fill=fill)
    elif kind == "square":
        d.rectangle([c - size * 0.38, c - size * 0.38, c + size * 0.38, c + size * 0.38], fill=fill)
    elif kind == "triangle":
        d.polygon([(c, c - size * 0.55), (c + size * 0.5, c + size * 0.35), (c - size * 0.5, c + size * 0.35)], fill=fill)
    elif kind == "star":
        pts = []
        for k in range(10):
            r = size * (0.6 if k % 2 == 0 else 0.26)
            a = math.pi / 5 * k - math.pi / 2
            pts.append((c + math.cos(a) * r, c + math.sin(a) * r))
        d.polygon(pts, fill=fill)
    elif kind == "curl":
        d.arc([c - size * 0.75, c - size * 0.75, c + size * 0.75, c + size * 0.75], 200, 340, fill=fill, width=int(size * 0.3))
    else:  # a squiggle
        pts = [(c - size + t * size / 6, c + math.sin(t * 1.3) * size * 0.25) for t in range(13)]
        d.line(pts, fill=fill, width=int(size * 0.24), joint="curve")
    return im.rotate(angle, resample=Image.BICUBIC)


KINDS = ["strip", "dot", "square", "triangle", "star", "curl", "squiggle"]


def main():
    os.makedirs(OUT, exist_ok=True)
    glyph = Image.open(os.path.join(ROOT, "icon_out", "margyt-1024-glyph.png")).convert("RGBA")
    # a little smaller than the plain icon's, so the confetti has room round it
    k = 0.86
    note = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    g = glyph.resize((int(S * k), int(S * k)), Image.LANCZOS)
    note.alpha_composite(g, ((S - g.width) // 2, (S - g.height) // 2))
    note_alpha = note.split()[3]
    keep_off = note_alpha.filter(ImageFilter.MaxFilter(75))
    # the one-colour icon is read small and in one tint: its bits stand well clear of the note, or they melt into it
    mono_off = note_alpha.filter(ImageFilter.MaxFilter(151))

    rnd = random.Random(2411)
    # the confetti is the background: it fills the whole square, corners too, since some launchers show a square;
    # the note sits on top in the foreground. Everywhere but right against the note, its hollow included
    confetti = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    mono_bits = Image.new("L", (S, S), 0)
    placed = []
    tries = 0
    while tries < 20000:
        tries += 1
        x, y = rnd.uniform(20, S - 20), rnd.uniform(20, S - 20)
        size = rnd.uniform(56, 86)
        if keep_off.getpixel((int(x), int(y))) > 0:
            continue
        if any(math.hypot(x - px, y - py) < (size + ps) * 1.12 for px, py, ps in placed):
            continue
        kind = KINDS[len(placed) % len(KINDS)]
        colour = COLOURS[(len(placed) * 3) % 5]
        piece = bit(kind, colour, size, rnd.uniform(0, 360))
        confetti.alpha_composite(piece, (int(x - piece.width / 2), int(y - piece.height / 2)))
        # the one-colour icon keeps a few, only inside the part every launcher shows
        placed.append((x, y, size))

    # the one-colour icon gets its own few bits: a ring round the note inside the safe zone, each well clear of it
    shapes = ["star", "dot", "strip", "triangle", "square", "star", "dot", "strip"]
    taken = []
    for step in range(48):
        if len(taken) >= 7:
            break
        a = math.radians(-90 + step * 47 % 360)
        r = SAFE * 0.8
        x, y = S / 2 + math.cos(a) * r, S / 2 + math.sin(a) * r
        if mono_off.getpixel((int(x), int(y))) > 0 or any(math.hypot(x - px, y - py) < 150 for px, py in taken):
            continue
        piece = bit(shapes[len(taken)], (255, 255, 255), 58, (step * 37) % 180)
        mono_bits.paste(255, (int(x - piece.width / 2), int(y - piece.height / 2)), piece.split()[3])
        taken.append((x, y))

    fg = note
    bg = Image.new("RGBA", (S, S), MINT)
    bg.alpha_composite(confetti)
    full = bg.copy()
    full.alpha_composite(fg)
    mono = Image.new("RGBA", (S, S), (255, 255, 255, 0))
    mono.putalpha(ImageChops.lighter(note_alpha, mono_bits))
    for name, im in (("background", bg), ("foreground", fg), ("full", full), ("monochrome", mono)):
        im.save(os.path.join(OUT, name + ".png"))
    print("written to", OUT, "with", len(placed), "bits")


if __name__ == "__main__":
    main()
