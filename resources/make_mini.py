# -*- coding: utf-8 -*-
"""Mini review sheet: last 3 replacements + launcher icon."""
import os
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

BASE = Path(__file__).resolve().parent
NAMES = [("note", "image_11.jpg"), ("goods", "goods_3.jpg"),
         ("goods", "goods_12.jpg"), ("_avatar_build/_icon", "square512.png")]
CW, CH = 300, 400
img = Image.new("RGB", (4 * (CW + 8) + 8, CH + 40), (24, 24, 24))
d = ImageDraw.Draw(img)
try:
    font = ImageFont.truetype("C:/Windows/Fonts/consola.ttf", 15)
except Exception:
    font = ImageFont.load_default()
x = 8
for sub, n in NAMES:
    p = BASE / sub / n
    im = Image.open(p)
    im.thumbnail((CW, CH))
    img.paste(im, (x + (CW - im.width) // 2, (CH - im.height) // 2))
    d.text((x + 4, CH + 8), n, fill=(255, 220, 120), font=font)
    x += CW + 8
img.save(BASE / "_sheet_mini.jpg", "JPEG", quality=88)
print("ok")
