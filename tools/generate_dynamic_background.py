from PIL import Image, ImageDraw, ImageFilter
import math
import os
import random
import sys

W = H = 480
FRAME_COUNT = 18
DURATION_MS = 140
random.seed(7)

root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
drawable = os.path.join(root, "watchface", "src", "main", "res", "drawable")
os.makedirs(drawable, exist_ok=True)

gif_path = os.path.join(drawable, "night_lake_anim.gif")
thumb_path = os.path.join(drawable, "night_lake_thumb.png")

stars = [
    (
        random.randint(35, 445),
        random.randint(55, 185),
        random.choice([1, 1, 1, 2]),
        random.random() * math.tau,
    )
    for _ in range(45)
]

frames = []

for fi in range(FRAME_COUNT):
    img = Image.new("RGBA", (W, H), (2, 5, 12, 255))
    d = ImageDraw.Draw(img, "RGBA")

    # Deep blue night-sky gradient.
    for y in range(H):
        if y < 330:
            t = y / 330.0
            color = (
                int(3 + 4 * t),
                int(8 + 20 * t),
                int(20 + 20 * t),
                255,
            )
        else:
            t = (y - 330) / 150.0
            color = (
                int(5 - 4 * t),
                int(18 - 10 * t),
                int(28 - 14 * t),
                255,
            )
        d.line((0, y, W, y), fill=color)

    # Twinkling stars.
    for x, y, r, phase in stars:
        alpha = int(90 + 110 * (0.5 + 0.5 * math.sin(phase + fi * 0.7)))
        d.ellipse((x - r, y - r, x + r, y + r), fill=(150, 235, 255, alpha))

    # Moon and soft glow.
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow, "RGBA")
    gd.ellipse((330, 88, 394, 152), fill=(160, 240, 255, 40))
    glow = glow.filter(ImageFilter.GaussianBlur(12))
    img = Image.alpha_composite(img, glow)
    d = ImageDraw.Draw(img, "RGBA")
    d.ellipse((346, 104, 378, 136), fill=(225, 250, 255, 240))
    d.ellipse((355, 98, 384, 127), fill=(3, 9, 20, 255))

    # Slowly drifting clouds.
    cloud = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    cd = ImageDraw.Draw(cloud, "RGBA")
    shift = (fi * 8) % 560 - 80
    for base_y, scale, alpha, offset in [
        (160, 1.0, 28, 0),
        (205, 0.78, 22, 150),
    ]:
        start_x = (shift + offset) % 560 - 100
        for k in range(4):
            x = start_x + k * 180
            cd.ellipse(
                (x, base_y, x + 90 * scale, base_y + 24 * scale),
                fill=(90, 190, 220, alpha),
            )
            cd.ellipse(
                (
                    x + 30 * scale,
                    base_y - 11 * scale,
                    x + 120 * scale,
                    base_y + 22 * scale,
                ),
                fill=(110, 210, 235, alpha),
            )
    cloud = cloud.filter(ImageFilter.GaussianBlur(8))
    img = Image.alpha_composite(img, cloud)
    d = ImageDraw.Draw(img, "RGBA")

    # Distant mountains.
    far = [
        (0, 340),
        (55, 300),
        (110, 327),
        (167, 278),
        (223, 322),
        (285, 292),
        (340, 330),
        (404, 285),
        (480, 330),
        (480, 480),
        (0, 480),
    ]
    d.polygon(far, fill=(4, 19, 29, 255))
    for i in range(8):
        d.line([far[i], far[i + 1]], fill=(13, 93, 112, 210), width=2)

    # Near mountains.
    near = [
        (0, 390),
        (65, 345),
        (125, 380),
        (185, 325),
        (248, 377),
        (310, 338),
        (370, 382),
        (430, 350),
        (480, 372),
        (480, 480),
        (0, 480),
    ]
    d.polygon(near, fill=(2, 11, 18, 255))
    for i in range(8):
        d.line([near[i], near[i + 1]], fill=(19, 195, 216, 190), width=2)

    # Lake and animated cyan reflections.
    d.rectangle((0, 382, 480, 480), fill=(1, 9, 14, 220))
    phase = fi * 0.55
    for j, yy in enumerate(range(392, 468, 8)):
        width = max(30, 190 - j * 10)
        jitter = int(8 * math.sin(phase + j * 0.8))
        x0 = 240 - width // 2 + jitter
        alpha = max(14, 70 - j * 6)
        d.line(
            (x0, yy, x0 + width, yy),
            fill=(22, 190, 215, alpha),
            width=2 if j < 4 else 1,
        )

    # Moon reflection.
    for j in range(8):
        yy = 388 + j * 8
        width = max(8, 50 - j * 5)
        wobble = int(4 * math.sin(fi * 0.6 + j))
        d.line(
            (
                362 - width // 2 + wobble,
                yy,
                362 + width // 2 + wobble,
                yy,
            ),
            fill=(140, 238, 248, max(15, 70 - j * 7)),
            width=2,
        )

    # Dark glass area behind the clock so text stays readable.
    glass = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    glass_draw = ImageDraw.Draw(glass, "RGBA")
    glass_draw.rounded_rectangle(
        (65, 138, 415, 318),
        radius=34,
        fill=(0, 0, 0, 82),
    )
    glass = glass.filter(ImageFilter.GaussianBlur(4))
    img = Image.alpha_composite(img, glass)

    frames.append(
        img.convert(
            "P",
            palette=Image.Palette.ADAPTIVE,
            colors=128,
        )
    )

frames[0].save(
    gif_path,
    save_all=True,
    append_images=frames[1:],
    duration=DURATION_MS,
    loop=0,
    optimize=True,
    disposal=2,
)
frames[0].convert("RGB").save(thumb_path, optimize=True)

print(gif_path)
print(thumb_path)
