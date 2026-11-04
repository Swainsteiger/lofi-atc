"""Draws the Lofi ATC icon: a plane over a night-sky tile with a music note.
Writes pc/lofiatc.ico (desktop shortcut) and icon_512.png (source for the TV app's banner/icon)."""
import os

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
S = 512
img = Image.new("RGBA", (S, S), (0, 0, 0, 0))

# Night gradient tile with rounded corners.
grad = Image.new("RGBA", (S, S))
gd = ImageDraw.Draw(grad)
for y in range(S):
    t = y / S
    gd.line([(0, y), (S, y)], fill=(int(11 + 47 * t), int(16 + 19 * t), int(32 + 48 * t), 255))
mask = Image.new("L", (S, S), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, S - 1, S - 1], radius=110, fill=255)
img.paste(grad, (0, 0), mask)
d = ImageDraw.Draw(img)

for x, y, r in [(90, 90, 5), (400, 70, 4), (440, 200, 3), (70, 260, 3), (300, 120, 3), (170, 50, 3)]:
    d.ellipse([x - r, y - r, x + r, y + r], fill=(255, 255, 255, 200))

# Plane, nose up-right: fuselage, wings, tail.
def rot(pts, cx, cy):
    import math
    a = math.radians(-35)
    return [(cx + (px * math.cos(a) - py * math.sin(a)), cy + (px * math.sin(a) + py * math.cos(a))) for px, py in pts]

white = (240, 238, 250, 255)
cx, cy = 236, 250
d.polygon(rot([(-170, -16), (140, -16), (175, 0), (140, 16), (-170, 16)], cx, cy), fill=white)
d.polygon(rot([(-20, -16), (40, -16), (-40, -150), (-75, -150)], cx, cy), fill=white)
d.polygon(rot([(-20, 16), (40, 16), (-40, 150), (-75, 150)], cx, cy), fill=white)
d.polygon(rot([(-150, -16), (-120, -16), (-160, -70), (-180, -70)], cx, cy), fill=white)
d.polygon(rot([(-150, 16), (-120, 16), (-160, 70), (-180, 70)], cx, cy), fill=white)

# Music note, bottom right.
amber = (255, 184, 107, 255)
d.ellipse([318, 378, 382, 430], fill=amber)
d.rectangle([366, 270, 382, 405], fill=amber)
d.polygon([(366, 270), (440, 300), (440, 330), (382, 306)], fill=amber)

img.save(os.path.join(HERE, "icon_512.png"))
img.save(os.path.join(HERE, "pc", "lofiatc.ico"), sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
print("icon written")

# TV: launcher banner (320x180, the Android TV size) and app icon.
from PIL import ImageFont

TV = os.path.join(HERE, "tv", "app", "src", "main", "res")
W, H = 640, 360
ban = Image.new("RGBA", (W, H))
bd = ImageDraw.Draw(ban)
for y in range(H):
    t = y / H
    bd.line([(0, y), (W, y)], fill=(int(11 + 47 * t), int(16 + 19 * t), int(32 + 48 * t), 255))
for x, y, r in [(40, 40, 3), (600, 30, 3), (520, 300, 2), (300, 50, 2), (580, 150, 2), (120, 320, 2)]:
    bd.ellipse([x - r, y - r, x + r, y + r], fill=(255, 255, 255, 190))
ban.alpha_composite(img.resize((230, 230)).crop((0, 0, 230, 230)), (36, 65))
font = ImageFont.truetype(os.path.join(HERE, "outfit.ttf"), 76)
font.set_variation_by_axes([700]) if hasattr(font, "set_variation_by_axes") else None
small = ImageFont.truetype(os.path.join(HERE, "outfit.ttf"), 30)
bd.text((290, 105), "Lofi ATC", font=font, fill=(240, 238, 250))
bd.text((294, 200), "beats + live tower radio", font=small, fill=(255, 184, 107))
ban.resize((320, 180), Image.LANCZOS).save(os.path.join(TV, "drawable-nodpi", "banner.png"))
img.resize((96, 96), Image.LANCZOS).save(os.path.join(TV, "mipmap-xhdpi", "ic_launcher.png"))
print("tv art written")
