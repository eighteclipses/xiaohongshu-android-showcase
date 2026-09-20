# -*- coding: utf-8 -*-
"""Generate warm pastel gradient text-card backgrounds (1080x1440)."""
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "textbg")
os.makedirs(OUT, exist_ok=True)
W, H = 1080, 1440

PALETTES = [
    ((250, 246, 240), (240, 229, 216)),  # 米白
    ((253, 238, 227), (248, 222, 206)),  # 浅杏
    ((245, 239, 230), (233, 221, 206)),  # 淡奶咖
    ((255, 248, 240), (245, 230, 224)),  # 暖白粉
    ((247, 243, 236), (232, 226, 213)),  # 燕麦
    ((252, 239, 233), (242, 221, 213)),  # 浅珊瑚奶
]

for i, (c1, c2) in enumerate(PALETTES, 1):
    row = []
    for y in range(H):
        t = y / (H - 1)
        row.append((int(c1[0] + (c2[0] - c1[0]) * t),
                    int(c1[1] + (c2[1] - c1[1]) * t),
                    int(c1[2] + (c2[2] - c1[2]) * t)))
    img = Image.new("RGB", (W, H))
    px = img.load()
    for y in range(H):
        color = row[y]
        for x in range(W):
            px[x, y] = color
    path = os.path.join(OUT, "text_bg_%d.jpg" % i)
    img.save(path, "JPEG", quality=88, optimize=True)
    print("text_bg_%d.jpg %dKB" % (i, os.path.getsize(path) // 1024))
