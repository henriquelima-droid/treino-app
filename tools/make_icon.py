"""Gera todos os ícones Android do Treino HG: anilha preta sobre fundo vermelho."""
from PIL import Image, ImageDraw, ImageFont
import math, os

RES = "/home/claude/treino-android/android/app/src/main/res"
FONT = "/tmp/oswald700.ttf"
RED = (225, 6, 0, 255); BLK = (10, 10, 10, 255); RING = (38, 38, 38, 255)
WHITE = (255, 255, 255, 255); CLEAR = (0, 0, 0, 0)
S = 2048  # canvas de trabalho (super-amostrado)


def text_center(d, txt, cx, cy, size, fill):
    f = ImageFont.truetype(FONT, size)
    b = d.textbbox((0, 0), txt, font=f)
    d.text((cx - (b[0] + b[2]) / 2, cy - (b[1] + b[3]) / 2), txt, font=f, fill=fill)


def plate(R, silhouette=False):
    """Anilha com raio R (px) centralizada num canvas S×S transparente.
    Furos (centro e pegadores) ficam transparentes, mostrando o fundo."""
    im = Image.new("RGBA", (S, S), CLEAR)
    d = ImageDraw.Draw(im)
    c = S / 2
    k = R / 400
    body = WHITE if silhouette else BLK
    d.ellipse([c - R, c - R, c + R, c + R], fill=body)
    if silhouette:
        r = 355 * k
        d.ellipse([c - r, c - r, c + r, c + r], outline=CLEAR, width=int(28 * k))
    else:
        r = 330 * k
        d.ellipse([c - r, c - r, c + r, c + r], outline=RING, width=int(22 * k))
        r = 170 * k
        d.ellipse([c - r, c - r, c + r, c + r], fill=RING)
    # pegadores vazados
    for s in (-1, 1):
        x = c + s * 250 * k
        d.rounded_rectangle([x - 40 * k, c - 95 * k, x + 40 * k, c + 95 * k], int(40 * k), fill=CLEAR)
    # furo central
    r = 70 * k
    d.ellipse([c - r, c - r, c + r, c + r], fill=CLEAR)
    if not silhouette:
        text_center(d, "20", c, c - 250 * k, int(110 * k), WHITE)
        text_center(d, "20", c, c + 250 * k, int(110 * k), WHITE)
    return im


def on_bg(layer, shape):
    """shape: 'square' (cantos arredondados), 'circle' ou 'full'."""
    bg = Image.new("RGBA", (S, S), CLEAR)
    m = Image.new("L", (S, S), 0)
    md = ImageDraw.Draw(m)
    if shape == "square":
        md.rounded_rectangle([0, 0, S - 1, S - 1], int(S * 0.22), fill=255)
    elif shape == "circle":
        md.ellipse([0, 0, S - 1, S - 1], fill=255)
    else:
        md.rectangle([0, 0, S, S], fill=255)
    bg.paste(Image.new("RGBA", (S, S), RED), (0, 0), m)
    bg.alpha_composite(layer)
    return bg


def save(im, path, size):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.resize(size, Image.LANCZOS).save(path)


dens = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}

legacy_sq = on_bg(plate(S * 0.39), "square")
legacy_rd = on_bg(plate(S * 0.36), "circle")
fg = plate(S * 0.30)  # dentro da zona segura de 66dp do ícone adaptativo (108dp)
stat = plate(S * 0.47, silhouette=True)

for name, f in dens.items():
    n = int(48 * f)
    save(legacy_sq, f"{RES}/mipmap-{name}/ic_launcher.png", (n, n))
    save(legacy_rd, f"{RES}/mipmap-{name}/ic_launcher_round.png", (n, n))
    save(fg, f"{RES}/mipmap-{name}/ic_launcher_foreground.png", (int(108 * f), int(108 * f)))
    save(stat, f"{RES}/drawable-{name}/ic_stat_icon.png", (int(24 * f), int(24 * f)))

# splash: fundo vermelho com a anilha ao centro
for folder in os.listdir(RES):
    p = f"{RES}/{folder}/splash.png"
    if os.path.exists(p):
        w, h = Image.open(p).size
        side = min(w, h)
        logo = plate(S * 0.5).resize((int(side * 0.45), int(side * 0.45)), Image.LANCZOS)
        sp = Image.new("RGBA", (w, h), RED)
        sp.alpha_composite(logo, ((w - logo.width) // 2, (h - logo.height) // 2))
        sp.convert("RGB").save(p)

# prévias
os.makedirs("/tmp/icons", exist_ok=True)
prev = Image.new("RGBA", (1100, 420), (244, 244, 244, 255))
prev.alpha_composite(legacy_sq.resize((300, 300), Image.LANCZOS), (40, 60))
prev.alpha_composite(legacy_rd.resize((300, 300), Image.LANCZOS), (400, 60))
dark = Image.new("RGBA", (300, 300), (20, 20, 20, 255))
dark.alpha_composite(stat.resize((300, 300), Image.LANCZOS))
prev.alpha_composite(dark, (760, 60))
prev.convert("RGB").save("/tmp/icons/icone-final.png")
print("ok")
