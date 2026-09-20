# -*- coding: utf-8 -*-
"""Regenerate resources/资源清单.md with current manifest + attribution table."""
import json
import os

BASE = os.path.dirname(os.path.abspath(__file__))
with open(os.path.join(BASE, "credits.json"), encoding="utf-8") as f:
    credits = json.load(f)

def row(slot, c):
    title = (c.get("title") or c.get("url", ""))[:60].replace("|", "/")
    creator = (c.get("creator") or "unknown")[:24]
    lic = (c.get("license") or "").strip() or "see source"
    src = c.get("source_url") or c.get("url", "")
    return "| %s | %s | %s | %s | [来源](%s) |" % (slot, title, creator, lic, src)

note_rows = "\n".join(row("image_%d" % i, credits["image_%d" % i]) for i in range(1, 16) if "image_%d" % i in credits)
goods_rows = "\n".join(row("goods_%d" % i, credits["goods_%d" % i]) for i in range(1, 21) if "goods_%d" % i in credits)

doc = """# 小红书 Demo 项目 — 美术资源清单

> 本文件由资源管线自动生成/维护。所有图片均为**可商用自由许可**（CC0 / Public Domain / CC BY / CC BY-SA），
> 已替换早期来自千图网/视觉中国等来源的版权存疑图片。学习 Demo 可直接使用；
> 若日后公开分发，请在"关于"页保留下方作者署名表（CC BY / BY-SA 要求署名）。

## 资源总览

| 类别 | 文件 | 尺寸 | 来源 | 许可 |
|---|---|---|---|---|
| 用户头像 p1~p16 | drawable-nodpi/p1..p16.png | 512×512 | DiceBear「Notionists」本地渲染（@dicebear/core + resvg-js，零网络） | CC0（原作 Zoish） |
| 首页笔记图 image_1~15 | drawable-nodpi/image_1..15.jpg | 1080×1440（3:4） | Openverse / Wikimedia Commons 检索（Flickr、Commons 等） | CC0 / CC BY / CC BY-SA |
| 商品图 goods_1~20 | drawable-nodpi/goods_1..20.jpg | 1200×900（4:3） | 同上 | 同上 |
| 文字卡片渐变背景 text_bg_1~6 | drawable-nodpi/text_bg_1..6.jpg | 1080×1440 | 本地 PIL 生成（米白/浅杏/淡奶咖等 6 色渐变） | 无版权约束（自制） |
| 占位图 | drawable/placeholder_avatar.xml（灰圆）、placeholder_image.xml（灰矩形） | 矢量 shape | 手写 | — |
| 启动图标 | mipmap-*/ic_launcher(.round).png + mipmap-anydpi-v26 自适应 + drawable/ic_launcher_foreground.xml | 48~192px | 本地 resvg/PIL 生成（#FF2442 底 + 白色对话气泡） | 自绘，无商标风险（非官方图形） |

## 主题映射（笔记图）

image_1~3 美食（咖啡/甜点）、image_4~6 旅行（城市/海岸/山湖）、image_7~8 穿搭、image_9~11 美妆、image_12~15 萌宠。
首页标题与图片为随机配对，如需图题强关联，可在 `HomeDataRepository` 里按主题分组取图。

## 头像扩容说明

头像由 11 张扩至 **16 张**（p12~p16 为新增）。相关代码已同步修改：
- 20 处头像数组/成员校验扩到 p16（`R.drawable.p12...p16` / 全限定名变体）；
- 2 处 switch 映射（`DatabaseInitializer`、`MessageDataRepository`）补 case 12~16；
- 7 处 `% 11` 取模改 `% 16`；
- 图片加载失败回退统一改为 `R.drawable.placeholder_avatar` / `R.drawable.placeholder_image`；
- 纯文字笔记海报背景改用 `text_bg_1~6` 渐变（`TextToImageConverter.BACKGROUND_RESOURCES`），并去掉旧照片上的深色遮罩。

## 笔记图 / 商品图署名表（CC BY / BY-SA 需要保留）

### 笔记图 image_1~15

| 槽位 | 原图标题 | 作者 | 许可 | 来源 |
|---|---|---|---|---|
@@NOTES@@

### 商品图 goods_1~20

| 槽位 | 原图标题 | 作者 | 许可 | 来源 |
|---|---|---|---|---|
@@GOODS@@

## 重新生成 / 替换图片

```powershell
# 1. 头像（需 node，包已装在 resources/_avatar_build）
cd resources\\_avatar_build && node render_avatars.mjs

# 2. 笔记图 / 商品图（Openverse + Commons 检索下载，自动写 credits.json）
python resources\\fetch_assets.py              # 全部
python resources\\fetch_assets.py image_3 goods_7  # 指定槽位重抓

# 3. 文字渐变背景
python resources\\make_textbg.py

# 4. 启动图标
cd resources\\_avatar_build && node render_icons.mjs

# 5. 接入 app（复制到 drawable-nodpi 并清理 drawable 旧图）
python resources\\integrate.py

# 6. 生成审阅拼图（人工目检）
python resources\\make_sheets.py
```

## 备注

- `download_resources.ps1` 是旧管线的遗留脚本（来源为版权存疑的中文站点，链接多已失效），已废弃不再使用。
- 检索源：Openverse API（api.openverse.org，CC 授权过滤）与 Wikimedia Commons API；
  图片统一经 PIL 中心裁剪到目标比例并压缩（quality 82，单张 <400KB）。
- 未采用需 API Key / 被墙的源（DiceBear 在线 API、Unsplash 搜索、Pexels 搜索页）；
  其中 Unsplash/Pexels 图片 CDN 可直连，如需更高质量可手动替换对应槽位文件。
- 视频封面未单独制作：`VideoMock` 仅引用 raw 视频，无封面图引用点；若将来需要，可用 ffmpeg 抽帧。
"""
with open(os.path.join(BASE, "资源清单.md"), "w", encoding="utf-8") as f:
    f.write(doc.replace("@@NOTES@@", note_rows).replace("@@GOODS@@", goods_rows))
print("资源清单.md rewritten, credits:", len(credits))
