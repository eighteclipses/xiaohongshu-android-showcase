# -*- coding: utf-8 -*-
"""Integrate staged images into app drawable-nodpi and remove superseded drawables."""
import os
import shutil
from pathlib import Path

BASE = Path(__file__).resolve().parents[1]
RES = BASE / "app" / "src" / "main" / "res"
SRC = BASE / "resources"
NODPI = RES / "drawable-nodpi"
os.makedirs(NODPI, exist_ok=True)

copied = 0
for i in range(1, 16):
    src = SRC / "note" / ("image_%d.jpg" % i)
    if src.exists():
        shutil.copy2(src, NODPI / ("image_%d.jpg" % i)); copied += 1
for i in range(1, 21):
    src = SRC / "goods" / ("goods_%d.jpg" % i)
    if src.exists():
        shutil.copy2(src, NODPI / ("goods_%d.jpg" % i)); copied += 1
for i in range(1, 17):
    src = SRC / "avatar" / ("p%d.png" % i)
    if src.exists():
        shutil.copy2(src, NODPI / ("p%d.png" % i)); copied += 1
for i in range(1, 7):
    src = os.path.join(SRC, "textbg", "text_bg_%d.jpg" % i)
    if os.path.exists(src):
        shutil.copy2(src, os.path.join(NODPI, "text_bg_%d.jpg" % i)); copied += 1

# remove superseded originals from drawable/
removed = 0
names = ["p%d.jpeg" % i for i in range(1, 12)]
names += ["image_%d.jpg" % i for i in range(1, 16)] + ["image_%d.jpeg" % i for i in (7, 8, 10, 11)]
names += ["goods_%d.jpg" % i for i in range(1, 21)]
for n in names:
    p = os.path.join(RES, "drawable", n)
    if os.path.exists(p):
        os.remove(p); removed += 1

print("copied:", copied, "removed:", removed)

# verify no same-name resource in both folders
d1 = {os.path.splitext(f)[0] for f in os.listdir(os.path.join(RES, "drawable"))}
d2 = {os.path.splitext(f)[0] for f in os.listdir(NODPI)}
clash = sorted(d1 & d2)
print("name clashes:", clash if clash else "none")

# size audit of new nodpi images
big = []
for f in sorted(os.listdir(NODPI)):
    p = os.path.join(NODPI, f)
    kb = os.path.getsize(p) // 1024
    if kb > 400:
        big.append((f, kb))
print("over-400KB files:", big if big else "none")
