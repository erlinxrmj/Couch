"""Erzeugt App-Symbol und TV-Banner beim Bauen (Aufruf: python3 art/make_art.py <Inter-Bold> <Inter-Medium>)."""
import sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter

BOLD, MEDIUM = sys.argv[1], sys.argv[2]
RES = "app/src/main/res"
V = (139, 92, 246)
P = (236, 72, 153)


def grad(w, h, c1, c2):
    im = Image.new("RGB", (w, h))
    px = im.load()
    for y in range(h):
        for x in range(w):
            t = (x / w + (h - y) / h) / 2
            px[x, y] = tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))
    return im


def couch(d, cx, cy, s, fill):
    w = s
    h = s * 0.55
    x0 = cx - w / 2
    y0 = cy - h / 2
    d.rounded_rectangle([x0 + w * 0.12, y0, x0 + w * 0.88, y0 + h * 0.62], radius=s * 0.09, fill=fill)
    d.rounded_rectangle([x0, y0 + h * 0.32, x0 + w * 0.2, y0 + h * 0.95], radius=s * 0.08, fill=fill)
    d.rounded_rectangle([x0 + w * 0.8, y0 + h * 0.32, x0 + w, y0 + h * 0.95], radius=s * 0.08, fill=fill)
    d.rounded_rectangle([x0 + w * 0.1, y0 + h * 0.58, x0 + w * 0.9, y0 + h * 0.95], radius=s * 0.06, fill=fill)
    d.rectangle([x0 + w * 0.12, y0 + h * 0.95, x0 + w * 0.2, y0 + h * 1.12], fill=fill)
    d.rectangle([x0 + w * 0.8, y0 + h * 0.95, x0 + w * 0.88, y0 + h * 1.12], fill=fill)


S = 192
g = grad(S, S, V, P).convert("RGBA")
mask = Image.new("L", (S, S), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, S - 1, S - 1], radius=44, fill=255)
icon = Image.new("RGBA", (S, S), (0, 0, 0, 0))
icon.paste(g, (0, 0), mask)
couch(ImageDraw.Draw(icon), S / 2, S / 2 + 4, S * 0.56, (255, 255, 255, 255))
icon.save(RES + "/mipmap-xhdpi/ic_launcher.png")

W, H = 320, 180
b = Image.new("RGB", (W, H), (11, 11, 18))
glow = Image.new("RGB", (W, H), (0, 0, 0))
gd = ImageDraw.Draw(glow)
gd.ellipse([-80, -120, 220, 160], fill=(70, 50, 160))
gd.ellipse([200, 80, 420, 280], fill=(110, 30, 80))
glow = glow.filter(ImageFilter.GaussianBlur(50))
b = Image.blend(b, glow, 0.6).convert("RGBA")
ic = icon.resize((88, 88), Image.LANCZOS)
b.paste(ic, (36, 46), ic)
d = ImageDraw.Draw(b)
d.text((142, 52), "Couch", font=ImageFont.truetype(BOLD, 46), fill=(244, 244, 246))
d.text((144, 110), "Twitch auf dem Sofa", font=ImageFont.truetype(MEDIUM, 17), fill=(180, 170, 210))
b.convert("RGB").save(RES + "/drawable-xhdpi/banner.png")
print("Bilder erzeugt")

import runpy  # noqa: E402
import os  # noqa: E402
if os.path.exists("art/diag.py"):
    runpy.run_path("art/diag.py")
