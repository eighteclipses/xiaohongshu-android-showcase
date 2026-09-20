# -*- coding: utf-8 -*-
"""Resize rendered 512px launcher icons to legacy mipmap densities with masks."""
import os
from pathlib import Path
from PIL import Image, ImageDraw

BASE = Path(__file__).resolve().parents[1]
RES = BASE / "app" / "src" / "main" / "res"
SRC = BASE / "resources" / "_avatar_build" / "_icon"
DENSITIES = {"mipmap-mdpi": 48, "mipmap-hdpi": 72, "mipmap-xhdpi": 96,
             "mipmap-xxhdpi": 144, "mipmap-xxxhdpi": 192}

for d, size in DENSITIES.items():
    folder = RES / d
    os.makedirs(folder, exist_ok=True)
    # remove old webp icons
    for f in ("ic_launcher.webp", "ic_launcher_round.webp"):
        p = folder / f
        if os.path.exists(p):
            os.remove(p)

    # square (rounded corners ~18%)
    img = Image.open(SRC / "square512.png").convert("RGBA")
    img = img.resize((size, size), Image.LANCZOS)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * 0.18), fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    out.save(os.path.join(folder, "ic_launcher.png"))

    # round
    img = Image.open(os.path.join(SRC, "round512.png")).convert("RGBA")
    img = img.resize((size, size), Image.LANCZOS)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    out.save(os.path.join(folder, "ic_launcher_round.png"))
    print(d, size, "ok")
print("legacy icons done")
