import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONT_DIRS = ["/usr/share/fonts/truetype/google-fonts/ofl", "/usr/share/fonts/opentype", "/usr/share/fonts/truetype",
             "C:/Windows/Fonts", "/Library/Fonts"]

LIME = (124, 255, 79)
CYAN = (42, 212, 255)
NAVY = (9, 13, 28)
SLATE = (24, 32, 50)


def find_font(names, size):
    for d in FONT_DIRS:
        for dirpath, _, files in os.walk(d):
            for n in names:
                if n in files:
                    return ImageFont.truetype(os.path.join(dirpath, n), size)
    return ImageFont.load_default()


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))


def gradient(size, c1, c2, horizontal=False):
    n = 256
    small = Image.new("RGB", (n, n))
    px = small.load()
    for y in range(n):
        for x in range(n):
            t = x / (n - 1) if horizontal else (x + y) / (2 * n - 2)
            px[x, y] = lerp(c1, c2, t)
    return small.resize(size, Image.BICUBIC)


def rounded_mask(size, radius):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, size[0] - 1, size[1] - 1], radius=radius, fill=255)
    return m


def draw_icon(S=2048, tile=True):
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))


    bg = Image.new("RGB", (S, S))
    bpx = ImageDraw.Draw(bg)
    for y in range(S):
        t = y / S
        bpx.line([(0, y), (S, y)], fill=lerp((14, 22, 44), NAVY, t))
    if tile:
        img.paste(bg, (0, 0), rounded_mask((S, S), int(S * 0.22)))


    glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([S * 0.18, S * 0.18, S * 0.82, S * 0.82], fill=(60, 255, 170, 120))
    glow = glow.filter(ImageFilter.GaussianBlur(S * 0.08))
    img = Image.alpha_composite(img, glow)

    d = ImageDraw.Draw(img)
    chip = [S * 0.25, S * 0.25, S * 0.75, S * 0.75]
    pin_len, pin_w, pins = S * 0.075, S * 0.034, 5


    for i in range(pins):
        c = S * 0.25 + (S * 0.5) * (i + 0.5) / pins
        col = lerp(LIME, CYAN, i / (pins - 1))
        d.rounded_rectangle([c - pin_w / 2, chip[1] - pin_len, c + pin_w / 2, chip[1] + 4], radius=pin_w / 3, fill=col)
        d.rounded_rectangle([c - pin_w / 2, chip[3] - 4, c + pin_w / 2, chip[3] + pin_len], radius=pin_w / 3, fill=col)
        d.rounded_rectangle([chip[0] - pin_len, c - pin_w / 2, chip[0] + 4, c + pin_w / 2], radius=pin_w / 3, fill=col)
        d.rounded_rectangle([chip[2] - 4, c - pin_w / 2, chip[2] + pin_len, c + pin_w / 2], radius=pin_w / 3, fill=col)


    border = gradient((int(S * 0.5), int(S * 0.5)), LIME, CYAN)
    img.paste(border, (int(chip[0]), int(chip[1])), rounded_mask(border.size, int(S * 0.07)))
    inner_pad = int(S * 0.03)
    inner = Image.new("RGB", (border.size[0] - 2 * inner_pad, border.size[1] - 2 * inner_pad), SLATE)
    img.paste(inner, (int(chip[0]) + inner_pad, int(chip[1]) + inner_pad), rounded_mask(inner.size, int(S * 0.05)))


    cx, cy = S * 0.5, S * 0.5
    u = S * 0.5 / 10.0
    bolt = [(cx + 0.9 * u, cy - 4.0 * u), (cx - 2.4 * u, cy + 0.6 * u), (cx - 0.2 * u, cy + 0.6 * u),
            (cx - 1.0 * u, cy + 4.0 * u), (cx + 2.4 * u, cy - 0.9 * u), (cx + 0.2 * u, cy - 0.9 * u)]
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).polygon(bolt, fill=255)
    fill = gradient((S, S), LIME, CYAN)
    bolt_glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    bolt_glow.paste((140, 255, 200, 200), (0, 0), mask.filter(ImageFilter.GaussianBlur(S * 0.02)))
    img = Image.alpha_composite(img, bolt_glow)
    img.paste(fill, (0, 0), mask)
    return img


def draw_banner(icon, W=1920, H=600):
    img = Image.new("RGB", (W, H), NAVY)
    d = ImageDraw.Draw(img)
    for y in range(H):
        d.line([(0, y), (W, y)], fill=lerp((16, 24, 48), (6, 9, 20), y / H))


    rnd = random.Random(26)
    bars = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    bd = ImageDraw.Draw(bars)
    base = H - 40
    for x in range(0, W, 6):
        t = x / W
        h = 45 + 25 * math.sin(t * 9) + rnd.randint(0, 20)
        if rnd.random() < 0.03:
            h += 45
        col = lerp(LIME, CYAN, t) + (70,)
        bd.rectangle([x, base - h, x + 3, base], fill=col)
    bars = bars.filter(ImageFilter.GaussianBlur(1))
    img.paste(bars, (0, 0), bars)
    line = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ld = ImageDraw.Draw(line)
    for x in range(0, W, 14):
        ld.line([(x, base - 70), (x + 7, base - 70)], fill=(255, 255, 255, 90), width=2)
    img.paste(line, (0, 0), line)


    ic = icon.resize((380, 380), Image.LANCZOS)
    img.paste(ic, (110, (H - 380) // 2 - 10), ic)

    title = find_font(["ArchivoBlack-Regular.ttf", "Inter-ExtraBold.otf", "DejaVuSans-Bold.ttf"], 150)
    sub = find_font(["Inter-SemiBold.otf", "Inter-Medium.otf", "DejaVuSans.ttf"], 46)
    small = find_font(["Inter-Medium.otf", "Inter-Regular.otf", "DejaVuSans.ttf"], 34)

    tx = 560

    tw = int(d.textlength("GpuBoost", font=title)) + 10
    text_mask = Image.new("L", (tw, 200), 0)
    ImageDraw.Draw(text_mask).text((0, 0), "GpuBoost", font=title, fill=255)
    img.paste(gradient((tw, 200), LIME, CYAN, horizontal=True), (tx, 120), text_mask)

    d.text((tx + 6, 320), "Measured FPS. Real bottlenecks. Smarter rendering.", font=sub, fill=(232, 240, 255))
    d.text((tx + 6, 390), "Fabric  \u2022  Minecraft Java 26.3  \u2022  works with Sodium", font=small, fill=(150, 170, 200))
    d.text((W - 330, H - 70), "by vConceited", font=small, fill=(150, 170, 200))
    return img


def main():
    icon = draw_icon()
    icon512 = icon.resize((512, 512), Image.LANCZOS)
    icon128 = icon.resize((128, 128), Image.LANCZOS)
    os.makedirs(os.path.join(ROOT, "branding"), exist_ok=True)
    icon512.save(os.path.join(ROOT, "branding", "icon-512.png"), optimize=True)
    asset = os.path.join(ROOT, "src", "main", "resources", "assets", "gpuboost", "icon.png")
    os.makedirs(os.path.dirname(asset), exist_ok=True)
    icon128.save(asset, optimize=True)
    draw_banner(draw_icon(tile=False)).save(os.path.join(ROOT, "branding", "banner.png"), optimize=True)
    print("wrote icon-512.png, icon.png (128) and banner.png")


if __name__ == "__main__":
    main()
