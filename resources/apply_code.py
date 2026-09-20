# -*- coding: utf-8 -*-
"""
Code integration for new avatar set (p1..p16), placeholders and text-card gradients.

- Extend every avatar array/list from p1..p11 to p1..p16
- Extend avatar switch mappers (DatabaseInitializer, MessageDataRepository) with cases 12..16
- Replace hardcoded "% 11" avatar rotation with "% 16"
- Swap loading-failure fallbacks (p1/image_1) to placeholder drawables
- Point TextToImageConverter at the new pastel gradient backgrounds
"""
import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "java" / "com" / "xiaohongshu"
changed = []

def load(rel):
    with open(ROOT / rel, encoding="utf-8") as f:
        return f.read()

def save(rel, src):
    with open(ROOT / rel, "w", encoding="utf-8", newline="") as f:
        f.write(src)

def note(rel, what, n):
    if n:
        changed.append("%-45s %s x%d" % (rel, what, n))

# 1. Avatar arrays -----------------------------------------------------------
RE_SHORT = re.compile(r"(?m)^(\s*)R\.drawable\.p9, R\.drawable\.p10, R\.drawable\.p11(?!\d)(?!,\s*\n\s*R\.drawable\.p12)")
def sub_short(m):
    ind = m.group(1)
    return (m.group(0) + ",\n" + ind + "R.drawable.p12, R.drawable.p13, R.drawable.p14,\n"
            + ind + "R.drawable.p15, R.drawable.p16")

RE_SINGLE = re.compile(r"R\.drawable\.p10, R\.drawable\.p11\}(?!,)")

RE_FQ = re.compile(r"(?m)^(\s*)(com\.xiaohongshu\.R\.drawable\.p9, com\.xiaohongshu\.R\.drawable\.p10,\s*\n\s*com\.xiaohongshu\.R\.drawable\.p11)(?!\d)(?<!return com\.xiaohongshu\.R\.drawable\.p11)(?!,\s*\n\s*com\.xiaohongshu\.R\.drawable\.p12)")
def sub_fq(m):
    ind = m.group(1)
    return (m.group(2) + ",\n" + ind + "com.xiaohongshu.R.drawable.p12, com.xiaohongshu.R.drawable.p13, com.xiaohongshu.R.drawable.p14,\n"
            + ind + "com.xiaohongshu.R.drawable.p15, com.xiaohongshu.R.drawable.p16")

ARRAY_FILES = [
    "activity/graphic/CommentAdapter.java",
    "activity/graphic/GraphicActivity.java",
    "activity/graphic/GraphicViewModel.java",
    "mock/UserMock.java",
    "ui/cart/../cart/CartAdapter.java",  # placeholder, removed below
]
ARRAY_FILES = [
    "activity/graphic/CommentAdapter.java",
    "activity/graphic/GraphicActivity.java",
    "activity/graphic/GraphicViewModel.java",
    "mock/UserMock.java",
    "ui/comments/MyCommentsAdapter.java",
    "ui/discover/DiscoverFriendsAdapter.java",
    "ui/home/HomeDataRepository.java",
    "ui/home/discovery/DiscoveryAdapter.java",
    "ui/home/follow/FollowPageFragment.java",
    "ui/login/LoginDataRepository.java",
    "ui/message/MessageAdapter.java",
    "ui/message/RecommendFriendAdapter.java",
    "ui/mine/EditProfileActivity.java",
    "ui/mine/FollowListAdapter.java",
    "ui/mine/MineDataRepository.java",
    "ui/mine/MineFragment.java",
    "ui/mine/viewmodel/MineViewModel.java",
    "ui/profile/UserProfileActivity.java",
    "ui/publish/repository/NoteRepository.java",
    "ui/search/SearchResultActivity.java",
]

for rel in ARRAY_FILES:
    src = load(rel)
    orig = src
    n1 = len(RE_SHORT.findall(src))
    src = RE_SHORT.sub(sub_short, src)
    n2 = len(RE_FQ.findall(src))
    src = RE_FQ.sub(sub_fq, src)
    n3 = len(RE_SINGLE.findall(src))
    src = RE_SINGLE.sub(lambda m: "R.drawable.p10, R.drawable.p11, R.drawable.p12, R.drawable.p13, R.drawable.p14, R.drawable.p15, R.drawable.p16}", src)
    if src != orig:
        save(rel, src)
        note(rel, "arrays(short/fq/single)", n1 + n2 + n3)

# 2. Switch mappers ----------------------------------------------------------
rel = "database/DatabaseInitializer.java"
src = load(rel)
add = "".join("            case %d: return R.drawable.p%d;\n" % (i, i) for i in range(12, 17))
if "case 12: return R.drawable.p12;" not in src:
    src2, n = re.subn(r"(case 11: return R\.drawable\.p11;\n)", r"\1" + add, src)
    if n:
        save(rel, src2)
        note(rel, "switch cases 12-16", n)

rel = "ui/message/MessageDataRepository.java"
src = load(rel)
add = "".join("            case %d: return com.xiaohongshu.R.drawable.p%d;\n" % (i, i) for i in range(12, 17))
if "case 12: return com.xiaohongshu.R.drawable.p12;" not in src:
    src2, n = re.subn(r"(case 11: return com\.xiaohongshu\.R\.drawable\.p11;\n)", r"\1" + add, src)
    if n:
        save(rel, src2)
        note(rel, "switch cases 12-16", n)

# 3. % 11 -> % 16 ------------------------------------------------------------
RE_MOD = re.compile(r"% 11 \+ 1|\(i % 11\) \+ 1")
for rel in ARRAY_FILES + ["database/DatabaseInitializer.java"]:
    src = load(rel)
    src2, n = RE_MOD.subn(lambda m: m.group(0).replace("11", "16"), src)
    if n:
        save(rel, src2)
        note(rel, "mod 11 -> 16", n)

# 4. Loading fallbacks -> placeholders ---------------------------------------
def sub_load(m):
    token = m.group(2)
    ph = "placeholder_avatar" if token.endswith("p1") else "placeholder_image"
    return m.group(1) + ph + ");"

RE_LOAD = re.compile(r"(ImageLoader\.load\(.{1,150}?)(R\.drawable\.p1|R\.drawable\.image_1|com\.xiaohongshu\.R\.drawable\.p1)\);")

FALLBACK_FILES = [
    "activity/graphic/GraphicActivity.java",
    "ui/home/discovery/DiscoveryAdapter.java",
    "ui/home/follow/FollowPageFragment.java",
    "ui/mine/EditProfileActivity.java",
    "ui/mine/MineFragment.java",
    "ui/profile/UserProfileActivity.java",
]
for rel in FALLBACK_FILES:
    src = load(rel)
    src2, n = RE_LOAD.subn(sub_load, src)
    if n:
        save(rel, src2)
        note(rel, "load fallback -> placeholder", n)

rel = "ui/home/discovery/DiscoveryAdapter.java"
src = load(rel)
if "setImageResource(R.drawable.image_1);" in src:
    src = src.replace("imageView.setImageResource(R.drawable.image_1);",
                      "imageView.setImageResource(R.drawable.placeholder_image);")
    save(rel, src)
    note(rel, "setImageResource -> placeholder_image", 1)

rel = "ui/comments/MyCommentsAdapter.java"
src = load(rel)
if "userAvatar.setImageResource(R.drawable.p1);" in src:
    src = src.replace("userAvatar.setImageResource(R.drawable.p1);",
                      "userAvatar.setImageResource(R.drawable.placeholder_avatar);")
    save(rel, src)
    note(rel, "setImageResource -> placeholder_avatar", 1)

# 5. TextToImageConverter -> gradient backgrounds ----------------------------
rel = "ui/publish/TextToImageConverter.java"
src = load(rel)
old_array = """    private static final int[] BACKGROUND_RESOURCES = {
        R.drawable.image_1, R.drawable.image_3, R.drawable.image_5,
        R.drawable.image_8, R.drawable.image_12, R.drawable.image_14
    };"""
new_array = """    private static final int[] BACKGROUND_RESOURCES = {
        R.drawable.text_bg_1, R.drawable.text_bg_2, R.drawable.text_bg_3,
        R.drawable.text_bg_4, R.drawable.text_bg_5, R.drawable.text_bg_6
    };"""
if old_array in src:
    src = src.replace(old_array, new_array)
    note(rel, "gradient backgrounds", 1)
old_overlay = """                Paint overlay = new Paint(Paint.ANTI_ALIAS_FLAG);
                overlay.setColor(Color.argb(92, 0, 0, 0));
                canvas.drawRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT, overlay);
"""
if old_overlay in src:
    src = src.replace(old_overlay, "")
    note(rel, "remove dark overlay", 1)
save(rel, src)

print("\n".join(changed) if changed else "NO CHANGES")
print("total files:", len(changed))
