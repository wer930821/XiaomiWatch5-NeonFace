from PIL import Image, ImageDraw, ImageFilter
import math
import os
import random

W = H = 480
S = 2
WW = HH = W * S
FRAME_COUNT = 20
DURATION_MS = 160
random.seed(19)

root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
drawable = os.path.join(root, "watchface", "src", "main", "res", "drawable")
os.makedirs(drawable, exist_ok=True)

gif_path = os.path.join(drawable, "night_lake_anim.gif")
thumb_path = os.path.join(drawable, "night_lake_thumb.png")

def sc(v):
    return int(v * S)

def pts(points):
    return [(sc(x), sc(y)) for x, y in points]

# Stable star field.
stars = []
for _ in range(70):
    stars.append((
        random.randint(sc(30), sc(450)),
        random.randint(sc(45), sc(205)),
        random.choice([1, 1, 1, 2, 2]) * S,
        random.random() * math.tau
    ))

# A few fixed mountain ridges for a more photographic layered landscape.
far_ridge = [
    (0, 318),(42, 292),(78, 305),(118, 270),(152, 292),(198, 252),
    (238, 286),(282, 262),(322, 292),(368, 250),(408, 281),(448, 264),(480, 300)
]
mid_ridge = [
    (0, 352),(54, 320),(95, 344),(148, 300),(192, 340),(235, 308),
    (285, 346),(334, 314),(384, 348),(432, 320),(480, 342)
]
near_ridge = [
    (0, 387),(46, 352),(88, 380),(134, 343),(177, 374),(226, 336),
    (274, 382),(323, 348),(374, 386),(424, 355),(480, 378)
]

rgb_frames = []

for fi in range(FRAME_COUNT):
    img = Image.new("RGB", (WW, HH), (2, 6, 14))
    d = ImageDraw.Draw(img)

    # Smooth sky gradient: dark navy to cool blue near horizon.
    for y in range(HH):
        yy = y / S
        if yy < 330:
            t = yy / 330.0
            r = int(4 + 6 * t)
            g = int(10 + 20 * t)
            b = int(26 + 34 * t)
        else:
            t = min(1.0, (yy - 330) / 150.0)
            r = int(8 - 5 * t)
            g = int(26 - 15 * t)
            b = int(46 - 25 * t)
        d.line((0, y, WW, y), fill=(r, g, b))

    # Soft Milky-Way haze.
    haze = Image.new("RGBA", (WW, HH), (0,0,0,0))
    hd = ImageDraw.Draw(haze, "RGBA")
    for i in range(7):
        y0 = sc(72 + i * 18)
        hd.ellipse(
            (sc(40 - i * 6), y0, sc(440 + i * 8), y0 + sc(72)),
            fill=(95, 155, 195, max(4, 18 - i * 2))
        )
    haze = haze.filter(ImageFilter.GaussianBlur(sc(26)))
    img = Image.alpha_composite(img.convert("RGBA"), haze).convert("RGB")
    d = ImageDraw.Draw(img)

    # Stars with gentle twinkle.
    for x, y, r, phase in stars:
        tw = 0.5 + 0.5 * math.sin(phase + fi * 0.45)
        c = int(150 + 90 * tw)
        rr = max(1, int(r * (0.8 + tw * 0.35)))
        d.ellipse((x-rr, y-rr, x+rr, y+rr), fill=(c, min(255,c+12), 255))

    # Moon with glow.
    moon_glow = Image.new("RGBA", (WW, HH), (0,0,0,0))
    mg = ImageDraw.Draw(moon_glow, "RGBA")
    mg.ellipse((sc(326),sc(88),sc(408),sc(170)), fill=(170,225,255,38))
    moon_glow = moon_glow.filter(ImageFilter.GaussianBlur(sc(18)))
    img = Image.alpha_composite(img.convert("RGBA"), moon_glow).convert("RGB")
    d = ImageDraw.Draw(img)
    d.ellipse((sc(350),sc(107),sc(385),sc(142)), fill=(226,242,246))
    d.ellipse((sc(360),sc(101),sc(390),sc(131)), fill=(5,12,28))

    # Drifting soft cloud bands.
    cloud = Image.new("RGBA", (WW, HH), (0,0,0,0))
    cd = ImageDraw.Draw(cloud, "RGBA")
    shift = ((fi * 7) % 620) - 120
    for base_y, alpha, scale, off in [(176,24,1.0,0),(220,18,0.8,170)]:
        for k in range(4):
            x = shift + off + k * 190
            cd.ellipse(
                (sc(x),sc(base_y),sc(x+120*scale),sc(base_y+28*scale)),
                fill=(125,165,190,alpha)
            )
            cd.ellipse(
                (sc(x+35*scale),sc(base_y-10*scale),sc(x+160*scale),sc(base_y+25*scale)),
                fill=(150,185,205,alpha)
            )
    cloud = cloud.filter(ImageFilter.GaussianBlur(sc(12)))
    img = Image.alpha_composite(img.convert("RGBA"), cloud).convert("RGB")
    d = ImageDraw.Draw(img)

    # Layered mountain silhouettes (filled areas, not wireframe).
    d.polygon(pts(far_ridge + [(480,390),(0,390)]), fill=(16,31,44))
    d.polygon(pts(mid_ridge + [(480,410),(0,410)]), fill=(9,24,34))
    d.polygon(pts(near_ridge + [(480,430),(0,430)]), fill=(5,16,23))

    # Snow/highlight on selected peaks.
    snow = [
        [(177,274),(198,252),(218,273),(205,267),(198,259),(190,269)],
        [(350,270),(368,250),(387,269),(376,264),(369,257),(361,265)]
    ]
    for poly in snow:
        d.polygon(pts(poly), fill=(95,126,143))

    # Thin mist at the waterline.
    mist = Image.new("RGBA", (WW, HH), (0,0,0,0))
    md = ImageDraw.Draw(mist, "RGBA")
    md.rectangle((0,sc(342),WW,sc(390)), fill=(120,165,185,22))
    mist = mist.filter(ImageFilter.GaussianBlur(sc(14)))
    img = Image.alpha_composite(img.convert("RGBA"), mist).convert("RGB")
    d = ImageDraw.Draw(img)

    # Lake base.
    d.rectangle((0,sc(377),WW,HH), fill=(3,12,19))

    # Soft mirrored mountain silhouette.
    mirror = Image.new("RGBA", (WW, HH), (0,0,0,0))
    md = ImageDraw.Draw(mirror, "RGBA")
    reflected = [(x, 377 + (377-y)*0.48) for x,y in near_ridge]
    md.polygon(pts(reflected + [(480,480),(0,480)]), fill=(12,42,53,75))
    mirror = mirror.filter(ImageFilter.GaussianBlur(sc(3)))
    img = Image.alpha_composite(img.convert("RGBA"), mirror).convert("RGB")
    d = ImageDraw.Draw(img)

    # Animated water ripples / moon reflection.
    phase = fi * 0.48
    for j, yy in enumerate(range(388, 472, 7)):
        fade = max(15, 88 - j*6)
        w = max(26, 210 - j*12)
        jitter = int(10 * math.sin(phase + j*0.8))
        x0 = 240 - w//2 + jitter
        x1 = 240 + w//2 - jitter
        d.line((sc(x0),sc(yy),sc(x1),sc(yy)), fill=(18,86,104), width=max(1,S))

    for j in range(10):
        yy = 386 + j*7
        w = max(10, 62 - j*5)
        wob = int(6 * math.sin(fi*0.42 + j*0.75))
        d.line(
            (sc(368-w//2+wob),sc(yy),sc(368+w//2+wob),sc(yy)),
            fill=(126,184,195),
            width=S
        )

    # Dark readable center glass, subtle enough to keep scenery visible.
    glass = Image.new("RGBA", (WW, HH), (0,0,0,0))
    gd = ImageDraw.Draw(glass, "RGBA")
    gd.rounded_rectangle(
        (sc(78),sc(145),sc(402),sc(312)),
        radius=sc(30),
        fill=(0,0,0,78)
    )
    glass = glass.filter(ImageFilter.GaussianBlur(sc(5)))
    img = Image.alpha_composite(img.convert("RGBA"), glass).convert("RGB")

    # Downsample for cleaner photographic edges.
    img = img.resize((W,H), Image.Resampling.LANCZOS)
    rgb_frames.append(img)

# Build one shared palette for all frames for reliable Wear OS GIF decoding.
sample_w = W * len(rgb_frames)
palette_sample = Image.new("RGB", (sample_w, H))
for i, frame in enumerate(rgb_frames):
    palette_sample.paste(frame, (i * W, 0))

palette_img = palette_sample.resize(
    (max(1, W * 3), max(1, H // 3)),
    Image.Resampling.BILINEAR,
).convert("P", palette=Image.Palette.ADAPTIVE, colors=192)

frames = [
    frame.quantize(
        palette=palette_img,
        dither=Image.Dither.FLOYDSTEINBERG,
    )
    for frame in rgb_frames
]

frames[0].save(
    gif_path,
    save_all=True,
    append_images=frames[1:],
    duration=DURATION_MS,
    loop=0,
    optimize=False,
    disposal=1,
)

rgb_frames[0].save(thumb_path, optimize=True)
print(gif_path)
print(thumb_path)
