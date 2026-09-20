# -*- coding: utf-8 -*-
"""Verify the built APK contains the new resources."""
import os
import zipfile
import time
from pathlib import Path

APK = Path(__file__).resolve().parents[1] / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
print("apk time:", time.strftime("%Y-%m-%d %H:%M", time.localtime(os.path.getmtime(APK))),
      "size: %.1fMB" % (os.path.getsize(APK) / 1048576))

z = zipfile.ZipFile(APK)
names = z.namelist()
hits = {"image_1.": 0, "image_15.": 0, "goods_20.": 0, "p16": 0, "text_bg_6": 0}
for n in names:
    for k in hits:
        if k in n:
            hits[k] += 1
print("resource hits in APK:", hits)
