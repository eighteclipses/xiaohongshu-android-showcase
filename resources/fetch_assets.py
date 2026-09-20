# -*- coding: utf-8 -*-
"""
Fetch free-license note/goods images for the xiaohongshu demo app.

Sources: Openverse API (via local proxy) and Wikimedia Commons API (direct).
Output : resources/note/image_N.jpg (1080x1440) and resources/goods/goods_N.jpg (1200x900)
         plus resources/credits.json with per-image attribution.

Usage:
  python fetch_assets.py            # fetch all missing slots
  python fetch_assets.py image_3 goods_7   # re-fetch only these slots
"""
import io
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

from PIL import Image, ImageOps

BASE = os.path.dirname(os.path.abspath(__file__))
UA = {"User-Agent": "XhsDemoAssetFetcher/1.0 (Android learning demo; contact: none)"}
BROWSER_UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/126.0"}
PROXY = {"http": "http://127.0.0.1:65532", "https": "http://127.0.0.1:65532"}

NOTE_W, NOTE_H = 1080, 1440
GOODS_W, GOODS_H = 1200, 900

# slot -> (kind, [queries])  3 queries per note theme, one per slot
NOTE_SLOTS = {
    "image_1":  ["brunch coffee flat lay", "breakfast table coffee"],
    "image_2":  ["cafe latte art", "coffee cup cafe"],
    "image_3":  ["dessert cake strawberry", "cake slice plate"],
    "image_4":  ["old town street europe", "city street architecture"],
    "image_5":  ["beach turquoise water", "coastal cliff sea", "santorini greece"],
    "image_6":  ["mountain lake hiking", "alpine lake landscape"],
    "image_7":  ["street fashion woman", "woman fashion outfit"],
    "image_8":  ["woman fashion dress boutique", "fashion clothing store woman"],
    "image_9":  ["lipstick makeup red", "nail polish bottles"],
    "image_10": ["cream jar cosmetics", "spa products towel"],
    "image_11": ["makeup brushes", "nail polish bottles"],
    "image_12": ["corgi dog", "welsh corgi"],
    "image_13": ["golden retriever dog", "labrador puppy"],
    "image_14": ["cat portrait", "tabby cat"],
    "image_15": ["kitten cute", "cat sleeping"],
}

GOODS_SLOTS = {
    "goods_1":  ["electronic cigarette vape", "e-cigarette device"],
    "goods_2":  ["smartphone in hand", "iphone space gray"],
    "goods_3":  ["apple earpods", "white earphones"],
    "goods_4":  ["electric shaver", "razor product"],
    "goods_5":  ["headphones product", "headphones white background"],
    "goods_6":  ["audio cable jack", "audio jack connector"],
    "goods_7":  ["usb type-c cable", "usb cable"],
    "goods_8":  ["iphone smartphone", "smartphone device"],
    "goods_9":  ["bluetooth speaker", "loudspeaker product photo"],
    "goods_10": ["electric razor shaver", "shaver"],
    "goods_11": ["laptop computer", "notebook computer product"],
    "goods_12": ["laptop on wooden desk", "open laptop computer"],
    "goods_13": ["game controller", "gamepad"],
    "goods_14": ["android phone in hand", "google pixel phone"],
    "goods_15": ["gaming laptop", "computer keyboard laptop"],
    "goods_16": ["digital camera", "dslr camera product"],
    "goods_17": ["mechanical keyboard", "computer keyboard"],
    "goods_18": ["white sedan car", "volkswagen beetle car", "toyota corolla car"],
    "goods_19": ["smartwatch on wrist", "apple watch wrist"],
    "goods_20": ["cctv camera wall", "surveillance camera outdoor"],
}

LICENSE_OK = {"cc0", "pdm", "by", "by-sa", "cc by", "cc by-sa", "cc0 1.0", "public domain"}


def http_get(url, timeout=25, binary=False):
    """Try direct first, then proxy. Returns (status, bytes)."""
    last = None
    for proxies in ({}, PROXY):
        try:
            opener = urllib.request.build_opener(urllib.request.ProxyHandler(proxies))
            req = urllib.request.Request(url, headers=UA)
            with opener.open(req, timeout=timeout) as r:
                return r.status, r.read()
        except Exception as e:
            last = e
    raise last


def strip_html(s):
    return re.sub(r"<[^>]+>", "", s or "").strip()


class Slots:
    """Tracks chosen source files so the same image is never used twice."""

    def __init__(self):
        self.path = os.path.join(BASE, "credits.json")
        if os.path.exists(self.path):
            with open(self.path, "r", encoding="utf-8") as f:
                self.credits = json.load(f)
        else:
            self.credits = {}
        self.used = {c.get("id") for c in self.credits.values()}

    def save(self):
        with open(self.path, "w", encoding="utf-8") as f:
            json.dump(self.credits, f, ensure_ascii=False, indent=2)


def openverse_candidates(query, tall):
    """Query Openverse (proxy). Returns list of dicts: id,url,title,creator,license,source_url,w,h"""
    params = {
        "q": query,
        "license": "cc0,pdm,by,by-sa",
        "page_size": "20",
        "extension": "jpg",
    }
    params["aspect_ratio"] = "tall" if tall else "wide"
    url = "https://api.openverse.org/v1/images/?" + urllib.parse.urlencode(params)
    try:
        opener = urllib.request.build_opener(urllib.request.ProxyHandler(PROXY))
        req = urllib.request.Request(url, headers=UA)
        with opener.open(req, timeout=25) as r:
            data = json.loads(r.read())
    except Exception as e:
        print("    openverse fail:", repr(e)[:80])
        return []
    out = []
    for it in data.get("results", []):
        out.append({
            "id": "ov:" + str(it.get("id")),
            "url": it.get("url"),
            "title": (it.get("title") or "")[:80],
            "creator": strip_html(it.get("creator") or "unknown"),
            "license": (it.get("license") or "") + " " + str(it.get("license_version") or ""),
            "source_url": it.get("foreign_landing_url") or "",
            "w": it.get("width") or 0,
            "h": it.get("height") or 0,
        })
    return out


def commons_candidates(query, tall):
    """Query Wikimedia Commons (direct). thumb at 1280px when possible."""
    q = query + " filetype:bitmap"
    params = {
        "action": "query",
        "generator": "search",
        "gsrsearch": q,
        "gsrnamespace": "6",
        "gsrlimit": "20",
        "prop": "imageinfo",
        "iiprop": "url|size|extmetadata",
        "iiurlwidth": "1280",
        "format": "json",
    }
    url = "https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode(params)
    try:
        st, body = http_get(url)
        data = json.loads(body)
    except Exception as e:
        print("    commons fail:", repr(e)[:80])
        return []
    pages = (data.get("query") or {}).get("pages") or {}
    out = []
    for p in pages.values():
        ii = (p.get("imageinfo") or [{}])[0]
        w, h = ii.get("width", 0), ii.get("height", 0)
        if not w or not h:
            continue
        meta = ii.get("extmetadata") or {}
        lic = strip_html((meta.get("LicenseShortName") or {}).get("value", ""))
        artist = strip_html((meta.get("Artist") or {}).get("value", "unknown"))[:60]
        if lic and not any(l in lic.lower() for l in ("cc0", "public domain", "by-sa", " by ", "attribution")):
            continue
        thumb = ii.get("thumburl") or ii.get("url")
        out.append({
            "id": "wc:" + str(p.get("pageid")),
            "url": thumb,
            "title": (p.get("title") or "")[:80],
            "creator": artist or "unknown",
            "license": lic or "see source",
            "source_url": ii.get("descriptionurl") or "",
            "w": w,
            "h": h,
        })
    # portrait preference for notes, landscape for goods
    if tall:
        out.sort(key=lambda c: -(1 if c["h"] > c["w"] * 1.1 else 0))
    else:
        out.sort(key=lambda c: -(1 if c["w"] >= c["h"] else 0))
    return out


def process_image(raw, out_path, tw, th):
    """EXIF-transpose, center-crop to tw:th, resize, save JPEG. Returns bytes written."""
    img = Image.open(io.BytesIO(raw))
    img = ImageOps.exif_transpose(img)
    if img.mode != "RGB":
        img = img.convert("RGB")
    img = ImageOps.fit(img, (tw, th), Image.LANCZOS, centering=(0.5, 0.45))
    img.save(out_path, "JPEG", quality=82, optimize=True, progressive=True)
    return os.path.getsize(out_path)


def dimension_ok(cand, tall):
    w, h = cand["w"], cand["h"]
    if not w or not h:
        return True  # unknown, let the download decide
    if tall:
        return h >= w and min(w, h) >= 500 and h >= 700
    return w >= h * 0.9 and w >= 800


def fetch_slot(slot, kind, queries, slots):
    tall = kind == "note"
    tw, th = (NOTE_W, NOTE_H) if tall else (GOODS_W, GOODS_H)
    sub = "note" if tall else "goods"
    out_dir = os.path.join(BASE, sub)
    os.makedirs(out_dir, exist_ok=True)
    out_path = os.path.join(out_dir, slot + ".jpg")

    cands = []
    for q in queries:
        cands += openverse_candidates(q, tall)
        time.sleep(0.4)
        cands += commons_candidates(q, tall)
        time.sleep(0.4)
        if len(cands) >= 40:
            break

    for c in cands:
        if c["id"] in slots.used or not c["url"]:
            continue
        if not dimension_ok(c, tall):
            continue
        try:
            st, raw = http_get(c["url"], timeout=40)
            if st != 200 or len(raw) < 15000:
                continue
            size = process_image(raw, out_path, tw, th)
            if size < 12000:
                os.remove(out_path)
                continue
            slots.used.add(c["id"])
            slots.credits[slot] = {
                "id": c["id"], "title": c["title"], "creator": c["creator"],
                "license": c["license"], "source_url": c["source_url"], "url": c["url"],
            }
            slots.save()
            print("  [ok] %s <- %s (%s, %s, %dKB)" % (slot, (c["title"] or c["url"])[:60], c["license"], c["creator"][:20], size // 1024))
            return True
        except Exception as e:
            print("    download fail:", repr(e)[:70])
            continue
    print("  [MISS] %s: no usable candidate" % slot)
    return False


def main():
    only = set(sys.argv[1:])
    slots = Slots()
    jobs = []
    for slot, queries in NOTE_SLOTS.items():
        jobs.append((slot, "note", queries))
    for slot, queries in GOODS_SLOTS.items():
        jobs.append((slot, "goods", queries))
    if only:
        jobs = [j for j in jobs if j[0] in only]
    ok = 0
    for slot, kind, queries in jobs:
        print("[%s/%s]" % (kind, slot))
        if fetch_slot(slot, kind, queries, slots):
            ok += 1
    print("done: %d/%d slots fetched" % (ok, len(jobs)))


if __name__ == "__main__":
    main()
