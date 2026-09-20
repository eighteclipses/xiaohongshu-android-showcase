# -*- coding: utf-8 -*-
"""Build labeled contact sheets of downloaded note/goods images for review."""
import os
from PIL import Image, ImageDraw, ImageFont

BASE = os.path.dirname(os.path.abspath(__file__))

def sheet(sub, names, cols, cell_w, out):
    cell_h = int(cell_w * 4 / 3)
    rows = (len(names) + cols - 1) // cols
    pad = 26
    img = Image.new("RGB", (cols * cell_w + (cols + 1) * 8, rows * (cell_h + pad) + 8), (24, 24, 24))
    d = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("C:/Windows/Fonts/consola.ttf", 15)
    except Exception:
        font = ImageFont.load_default()
    for i, n in enumerate(names):
        p = os.path.join(BASE, sub, n + ".jpg")
        if not os.path.exists(p):
            continue
        im = Image.open(p)
        im.thumbnail((cell_w, cell_h))
        x = 8 + (i % cols) * (cell_w + 8)
        y = 8 + (i // cols) * (cell_h + pad)
        img.paste(im, (x + (cell_w - im.width) // 2, y + (cell_h - im.height) // 2))
        d.text((x + 4, y + cell_h + 3), n, fill=(255, 220, 120), font=font)
    img.save(out, "JPEG", quality=85)
    print(out, img.size)

notes = ["image_%d" % i for i in range(1, 16)]
goods = ["goods_%d" % i for i in range(1, 21)]
sheet("note", notes, 5, 220, os.path.join(BASE, "_sheet_notes.jpg"))
sheet("goods", goods, 5, 220, os.path.join(BASE, "_sheet_goods.jpg"))

# avatar sheet too, for the final record
avs = ["p%d" % i for i in range(1, 17)]
cell = 150
img = Image.new("RGB", (8 * cell + 72, 2 * (cell + 24) + 8), (24, 24, 24))
d = ImageDraw.Draw(img)
for i, n in enumerate(avs):
    p = os.path.join(BASE, "avatar", n + ".png")
    im = Image.open(p).convert("RGB")
    im.thumbnail((cell, cell))
    x = 8 + (i % 8) * (cell + 8)
    y = 8 + (i // 8) * (cell + 24)
    img.paste(im, (x, y))
    d.text((x + 4, y + cell + 2), n, fill=(255, 220, 120))
img.save(os.path.join(BASE, "_sheet_avatars.jpg"), "JPEG", quality=85)
print("avatar sheet done")
