from PIL import Image, ImageDraw, ImageFilter
import math, os, random

W = H = 480
S = 2
WW = HH = W * S
FRAMES = 18
DURATION = 150
random.seed(77)

root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
out = os.path.join(root, "cyberwatchface", "src", "main", "res", "drawable")
os.makedirs(out, exist_ok=True)
gif_path = os.path.join(out, "cyber_city_anim.gif")
thumb_path = os.path.join(out, "cyber_city_thumb.png")

def sc(v): return int(v * S)

# fixed skyline blocks: x, w, h, accent
buildings = []
x = 15
while x < 465:
    w = random.randint(16, 34)
    h = random.randint(45, 135)
    accent = random.choice([(0,220,255),(255,42,210),(116,70,255)])
    buildings.append((x,w,h,accent))
    x += w + random.randint(4,9)

stars = [(random.randint(40,440), random.randint(35,240), random.random()*6.28) for _ in range(50)]
frames = []

for fi in range(FRAMES):
    img = Image.new("RGB",(WW,HH),(1,2,8))
    d = ImageDraw.Draw(img)

    # sky gradient
    for y in range(HH):
        yy = y / S
        t = min(1.0, yy / 360)
        col = (int(3+6*t), int(4+2*t), int(17+18*t))
        d.line((0,y,WW,y), fill=col)

    # faint HUD grid
    for gx in range(40, 461, 40):
        d.line((sc(gx),sc(110),sc(gx),sc(335)), fill=(12,28,55), width=1)
    for gy in range(120, 336, 32):
        d.line((sc(35),sc(gy),sc(445),sc(gy)), fill=(20,22,48), width=1)

    # stars / digital particles
    for x,y,p in stars:
        a = int(120 + 100*(0.5+0.5*math.sin(p + fi*.45)))
        c = (min(255,a), 80, 255) if int(p*10)%2 else (40,min(255,a+50),255)
        r = 1 if a < 180 else 2
        d.ellipse((sc(x-r),sc(y-r),sc(x+r),sc(y+r)), fill=c)

    # magenta sun with scan stripes
    sun = Image.new("RGBA",(WW,HH),(0,0,0,0))
    sd = ImageDraw.Draw(sun,"RGBA")
    sd.ellipse((sc(284),sc(236),sc(416),sc(368)), fill=(255,35,197,80))
    for k in range(9):
        yy = 255 + k*12
        sd.rectangle((sc(291),sc(yy),sc(409),sc(yy+3)), fill=(8,6,20,110))
    sun = sun.filter(ImageFilter.GaussianBlur(sc(5)))
    img = Image.alpha_composite(img.convert("RGBA"),sun).convert("RGB")
    d = ImageDraw.Draw(img)

    # city skyline
    horizon = 350
    for bx,bw,bh,accent in buildings:
        top = horizon-bh
        base = (4,9,18)
        d.rectangle((sc(bx),sc(top),sc(bx+bw),sc(horizon)), fill=base)
        d.rectangle((sc(bx),sc(top),sc(bx+2),sc(horizon)), fill=accent)
        # rooftop antennas
        if bw > 22:
            d.line((sc(bx+bw//2),sc(top-15),sc(bx+bw//2),sc(top)), fill=accent, width=sc(1))
        # windows
        for wy in range(top+12,horizon-8,14):
            for wx in range(bx+6,bx+bw-4,10):
                if (wx+wy+fi)%3 != 0:
                    cc = accent if (wx+wy)%2==0 else (90,110,180)
                    d.rectangle((sc(wx),sc(wy),sc(wx+2),sc(wy+4)), fill=cc)

    # elevated light rails and flying streaks
    shift = (fi*18)%560 - 80
    d.line((sc(10),sc(327),sc(470),sc(310)), fill=(0,156,255), width=sc(2))
    d.line((sc(12),sc(333),sc(468),sc(316)), fill=(255,40,205), width=sc(1))
    for off,col in [(0,(0,240,255)),(170,(255,42,210)),(340,(124,86,255))]:
        xx = shift + off
        d.rounded_rectangle((sc(xx),sc(275+(off//170)*14),sc(xx+44),sc(281+(off//170)*14)),
                            radius=sc(3), fill=col)

    # wet reflective foreground
    d.rectangle((0,sc(350),WW,HH), fill=(1,4,11))
    phase = fi*.45
    for j in range(22):
        yy = 354 + j*6
        x0 = random.randint(20,180)
        w = random.randint(25,110)
        wob = int(12*math.sin(phase+j*.7))
        col = [(0,205,255),(255,35,202),(112,70,255)][j%3]
        alpha = max(40,130-j*4)
        layer = Image.new("RGBA",(WW,HH),(0,0,0,0))
        ld = ImageDraw.Draw(layer,"RGBA")
        ld.rounded_rectangle((sc(x0+wob),sc(yy),sc(min(470,x0+w+wob)),sc(yy+2)),
                             radius=sc(1), fill=(*col,alpha))
        img = Image.alpha_composite(img.convert("RGBA"),layer).convert("RGB")

    # dark readability zones matching generated concept
    glass = Image.new("RGBA",(WW,HH),(0,0,0,0))
    gd = ImageDraw.Draw(glass,"RGBA")
    gd.rounded_rectangle((sc(42),sc(54),sc(438),sc(312)), radius=sc(30), fill=(0,0,0,52))
    gd.rounded_rectangle((sc(28),sc(338),sc(452),sc(438)), radius=sc(24), fill=(0,0,0,76))
    glass = glass.filter(ImageFilter.GaussianBlur(sc(4)))
    img = Image.alpha_composite(img.convert("RGBA"),glass).convert("RGB")

    img = img.resize((W,H), Image.Resampling.LANCZOS)
    frames.append(img)

# shared palette for stable GIF decoding
sheet = Image.new("RGB",(W*len(frames),H))
for i,f in enumerate(frames): sheet.paste(f,(i*W,0))
palette = sheet.resize((W*3,max(1,H//3)),Image.Resampling.BILINEAR).convert("P",palette=Image.Palette.ADAPTIVE,colors=192)
pframes = [f.quantize(palette=palette,dither=Image.Dither.FLOYDSTEINBERG) for f in frames]
pframes[0].save(gif_path,save_all=True,append_images=pframes[1:],duration=DURATION,loop=0,optimize=False,disposal=1)
frames[0].save(thumb_path,optimize=True)
print(gif_path)
print(thumb_path)
