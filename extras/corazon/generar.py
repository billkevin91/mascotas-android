"""
Genera el corazón con audífonos (dibujos y animations.xml) para Mascotas en pantalla / desktopPet.
Dibujo original: un corazón rojo con cara alegre, audífonos azules, guantes blancos y tenis azules.
Uso: python3 generar.py   (crea sheet.png, icon.png, animations.xml, preview.png y demo.gif aquí mismo)
"""
import base64, io, os
from PIL import Image, ImageDraw

W, H = 48, 56
COLS, ROWS = 8, 4
HERE = os.path.dirname(os.path.abspath(__file__))

OUT = (90, 10, 20, 255)
RED = (230, 40, 50, 255)
RED_D = (170, 20, 35, 255)
RED_L = (255, 120, 120, 255)
PINK = (255, 150, 160, 255)
WHITE = (255, 255, 255, 255)
BLACK = (25, 20, 30, 255)
MOUTH = (120, 10, 25, 255)
TONGUE = (255, 110, 130, 255)
BLUE = (40, 90, 210, 255)
BLUE_D = (20, 50, 140, 255)
BLUE_L = (110, 160, 250, 255)
GLOVE_D = (190, 195, 210, 255)
NOTE = (40, 90, 210, 255)
STAR = (255, 210, 40, 255)
ZZ = (60, 104, 140, 255)
TRANS = (0, 0, 0, 0)

CX = 24        # centro
TOP = 10       # arriba del corazón (sin audífonos)


def heart(d, dy=0, sx=1.0):
    """Cuerpo del corazón: dos círculos y un triángulo."""
    def X(x):
        return CX + (x - CX) * sx
    r = 9 * sx
    for cx in (X(17), X(31)):
        d.ellipse((cx - r - 1, 18 + dy - 10, cx + r + 1, 18 + dy + 10), fill=OUT)
    d.polygon([(X(7), 21 + dy), (X(41), 21 + dy), (CX, 42 + dy)], fill=OUT)
    for cx in (X(17), X(31)):
        d.ellipse((cx - r, 18 + dy - 9, cx + r, 18 + dy + 9), fill=RED)
    d.polygon([(X(8), 21 + dy), (X(40), 21 + dy), (CX, 40 + dy)], fill=RED)
    # sombra en el borde derecho y brillo arriba a la izquierda
    d.line((X(39), 23 + dy, CX + 1, 39 + dy), fill=RED_D, width=2)
    d.ellipse((X(12), 12 + dy, X(16), 16 + dy), fill=RED_L)
    d.point((X(18), 12 + dy), fill=RED_L)


def face(d, dy=0, eyes="open", mouth="smile", sx=1.0):
    def X(x):
        return round(CX + (x - CX) * sx)
    if eyes == "open":
        for ex in (19, 29):
            d.ellipse((X(ex) - 3, 19 + dy, X(ex) + 3, 27 + dy), fill=WHITE, outline=OUT)
            d.ellipse((X(ex) - 1, 22 + dy, X(ex) + 2, 26 + dy), fill=BLACK)
            d.point((X(ex), 22 + dy), fill=WHITE)
    elif eyes == "closed":
        for ex in (19, 29):
            d.arc((X(ex) - 3, 21 + dy, X(ex) + 3, 26 + dy), 200, 340, fill=BLACK)
    elif eyes == "happy":
        for ex in (19, 29):
            d.arc((X(ex) - 3, 22 + dy, X(ex) + 3, 28 + dy), 200, 340, fill=BLACK)
            d.arc((X(ex) - 3, 23 + dy, X(ex) + 3, 29 + dy), 200, 340, fill=BLACK)
    elif eyes == "dizzy":
        for ex in (19, 29):
            d.line((X(ex) - 2, 21 + dy, X(ex) + 2, 25 + dy), fill=BLACK)
            d.line((X(ex) + 2, 21 + dy, X(ex) - 2, 25 + dy), fill=BLACK)
    # mejillas
    d.ellipse((X(13), 27 + dy, X(16), 29 + dy), fill=PINK)
    d.ellipse((X(32), 27 + dy, X(35), 29 + dy), fill=PINK)
    if mouth == "smile":
        d.chord((X(20), 26 + dy, X(28), 34 + dy), 0, 180, fill=MOUTH)
        d.ellipse((X(22), 30 + dy, X(26), 33 + dy), fill=TONGUE)
    elif mouth == "big":
        d.chord((X(19), 25 + dy, X(29), 36 + dy), 0, 180, fill=MOUTH)
        d.ellipse((X(21), 31 + dy, X(27), 35 + dy), fill=TONGUE)
    elif mouth == "o":
        d.ellipse((X(22), 29 + dy, X(26), 33 + dy), fill=MOUTH)
    elif mouth == "flat":
        d.line((X(21), 31 + dy, X(27), 31 + dy), fill=MOUTH)


def headphones(d, dy=0, sx=1.0):
    def X(x):
        return round(CX + (x - CX) * sx)
    d.arc((X(7), 3 + dy, X(41), 33 + dy), 185, 355, fill=BLUE_D, width=3)
    d.arc((X(8), 4 + dy, X(40), 32 + dy), 200, 340, fill=BLUE_L)
    for x0 in (4, 39):
        d.rounded_rectangle((X(x0), 15 + dy, X(x0) + 5, 26 + dy), radius=2, fill=BLUE, outline=BLUE_D)
        d.line((X(x0) + 1, 17 + dy, X(x0) + 1, 23 + dy), fill=BLUE_L)


def glove(d, x, y, r=3):
    d.ellipse((x - r, y - r, x + r, y + r), fill=WHITE, outline=OUT)
    d.point((x + 1, y + 1), fill=GLOVE_D)


def arm(d, x0, y0, x1, y1, glove_r=3):
    d.line((x0, y0, x1, y1), fill=OUT, width=4)
    d.line((x0, y0, x1, y1), fill=RED, width=2)
    glove(d, x1, y1, glove_r)


def legs(d, step=0, dy=0, dangle=False):
    """step: 0 quieto, 1 y -1 caminando. Los tenis miran a la izquierda."""
    for i, lx in enumerate((20, 28)):
        # al caminar levanta un pie y luego el otro
        lift = 2 if (step == 1 and i == 0) or (step == -1 and i == 1) else 0
        top = 39 + dy
        bottom = 49 + dy - lift - (1 if dangle else 0) + (1 if dangle and i == 1 else 0)
        foot = lx - (1 if lift else 0)
        d.line((lx, top, foot, bottom), fill=OUT, width=4)
        d.line((lx, top, foot, bottom), fill=RED, width=2)
        # tenis
        d.rounded_rectangle((foot - 6, bottom - 1, foot + 3, bottom + 4), radius=2, fill=BLUE, outline=BLUE_D)
        d.line((foot - 6, bottom + 4, foot + 3, bottom + 4), fill=WHITE)
        d.point((foot - 2, bottom), fill=WHITE)
        d.point((foot, bottom), fill=WHITE)


def char(step=0, dy=0, arms="down", eyes="open", mouth="smile", dangle=False, phones=True):
    img = Image.new("RGBA", (W, H), TRANS)
    d = ImageDraw.Draw(img)
    legs(d, step, dy, dangle)
    # Los brazos salen de detrás del corazón: se dibujan antes y el corazón tapa los hombros.
    # Las manos (guantes) se vuelven a dibujar encima al final para que se vean.
    body = Image.new("RGBA", (W, H), TRANS)
    b = ImageDraw.Draw(body)
    heart(b, dy)
    face(b, dy, eyes, mouth)
    if phones:
        headphones(b, dy)
    L, R = (12, 29 + dy), (36, 29 + dy)   # hombros (dentro del corazón)
    hands = []
    front = []
    def arm(d_, x0, y0, x1, y1, glove_r=3):
        if y1 < 30:
            # Brazo levantado: por delante, saliendo del borde del corazón (si no, lo tapa el cuerpo)
            ex = 9 if x1 < CX else 39
            front.append((ex, 26 + dy, x1, y1))
        else:
            d_.line((x0, y0, x1, y1), fill=OUT, width=4)
            d_.line((x0, y0, x1, y1), fill=RED, width=2)
        hands.append((x1, y1, glove_r))
    if arms == "down":
        arm(d, *L, 4, 38 + dy); arm(d, *R, 44, 38 + dy)
    elif arms == "salute":      # saludo militar: el codo afuera y la mano en la frente
        front.append((9, 27 + dy, 3, 20 + dy))
        front.append((3, 20 + dy, 12, 15 + dy))
        hands.append((13, 15 + dy, 3))
        arm(d, *R, 44, 38 + dy)
    elif arms == "wave1":
        arm(d, *L, 4, 38 + dy); arm(d, *R, 44, 15 + dy)
    elif arms == "wave2":
        arm(d, *L, 4, 38 + dy); arm(d, *R, 46, 19 + dy)
    elif arms == "up":
        arm(d, *L, 3, 15 + dy); arm(d, *R, 45, 15 + dy)
    elif arms == "dance1":
        arm(d, *L, 3, 20 + dy); arm(d, *R, 44, 38 + dy)
    elif arms == "dance2":
        arm(d, *L, 4, 38 + dy); arm(d, *R, 45, 20 + dy)
    elif arms == "phones":      # manos en los audífonos
        arm(d, *L, 6, 21 + dy); arm(d, *R, 42, 21 + dy)
    img.alpha_composite(body)
    for x0, y0, x1, y1 in front:
        d.line((x0, y0, x1, y1), fill=OUT, width=4)
        d.line((x0, y0, x1, y1), fill=RED, width=2)
    for x, y, r in hands:
        glove(d, x, y, r)
    return img


def notes(img, k):
    d = ImageDraw.Draw(img)
    for i, (x, y) in enumerate(((2, 8), (42, 4))):
        y -= (k + i) % 2 * 3
        d.line((x + 2, y, x + 2, y + 5), fill=NOTE)
        d.line((x + 2, y, x + 4, y + 1), fill=NOTE)
        d.ellipse((x, y + 4, x + 2, y + 6), fill=NOTE)
    return img


def zz(img, big):
    d = ImageDraw.Draw(img)
    for x, y, s in ((38, 6, 3),) + (((42, 1, 4),) if big else ()):
        d.line((x, y, x + s, y), fill=ZZ); d.line((x + s, y, x, y + s), fill=ZZ); d.line((x, y + s, x + s, y + s), fill=ZZ)
    return img


def stars(img):
    d = ImageDraw.Draw(img)
    for x, y in ((8, 4), (24, 1), (39, 5)):
        d.line((x - 2, y, x + 2, y), fill=STAR); d.line((x, y - 2, x, y + 2), fill=STAR)
    return img


def scaled(img, f):
    w, h = max(2, round(W * f)), max(2, round(H * f))
    s = img.resize((w, h), Image.NEAREST)
    out = Image.new("RGBA", (W, H), TRANS)
    out.alpha_composite(s, ((W - w) // 2, H - h))
    return out


def squash_x(img, f):
    w = max(2, round(W * f))
    out = Image.new("RGBA", (W, H), TRANS)
    out.alpha_composite(img.resize((w, H), Image.NEAREST), ((W - w) // 2, 0))
    return out


def shift(img, dy):
    out = Image.new("RGBA", (W, H), TRANS)
    out.alpha_composite(img, (0, dy))
    return out


def sparkle_heart(img):
    d = ImageDraw.Draw(img)
    for x, y in ((10, 30), (38, 26), (24, 18), (14, 44), (34, 46)):
        d.point((x, y), fill=STAR); d.point((x - 1, y), fill=STAR); d.point((x + 1, y), fill=STAR)
        d.point((x, y - 1), fill=STAR); d.point((x, y + 1), fill=STAR)
    return img


def frames():
    f = []
    f.append(char())                                   # 0 quieto
    f.append(char(step=1, dy=-1))                      # 1 caminar
    f.append(char(step=-1, dy=-1))                     # 2
    f.append(squash_x(char(), 0.45))                   # 3 dar la vuelta
    f.append(char(eyes="closed"))                      # 4 parpadear
    f.append(char(arms="up", dangle=True, mouth="o"))  # 5 arrastrar
    f.append(shift(char(arms="up", dangle=True, mouth="o"), 1))  # 6
    f.append(char(arms="up", dangle=True, mouth="o", eyes="closed"))  # 7 cayendo
    f.append(char(dy=3, eyes="happy"))                 # 8 aterrizar (agachado)
    f.append(char(arms="salute", mouth="big"))         # 9 saludo militar
    f.append(char(arms="wave1", mouth="big"))          # 10 saludar con la mano
    f.append(char(arms="wave2", mouth="big"))          # 11
    f.append(notes(char(arms="dance1", dy=-1, eyes="happy", mouth="big", step=1), 0))   # 12 bailar
    f.append(notes(char(arms="dance2", dy=1, eyes="happy", mouth="big", step=-1), 1))   # 13
    f.append(char(dy=3, eyes="happy", mouth="big"))    # 14 saltar: agacharse
    f.append(shift(char(arms="up", eyes="happy", mouth="big"), -6))  # 15 en el aire
    f.append(shift(char(arms="up", eyes="happy", mouth="big"), -3))  # 16
    f.append(zz(char(eyes="closed", mouth="flat"), False))           # 17 dormir
    f.append(zz(char(eyes="closed", mouth="flat"), True))            # 18
    f.append(scaled(char(arms="wave1", mouth="big"), 0.7))           # 19 despedirse: se encoge
    f.append(scaled(char(arms="wave2", mouth="big"), 0.45))          # 20
    f.append(sparkle_heart(scaled(char(eyes="happy"), 0.25)))        # 21 corazoncito y brillos
    f.append(stars(char(dy=4, eyes="dizzy", mouth="o")))             # 22 golpe fuerte: mareado
    f.append(char(arms="phones", eyes="closed", mouth="smile"))      # 23 escuchar música
    # 24-26 dar la vuelta como una moneda: se estrecha hasta quedar de canto
    f.append(squash_x(char(), 0.7))
    f.append(squash_x(char(), 0.4))
    f.append(squash_x(char(), 0.15))
    return f


def build_sheet():
    sheet = Image.new("RGBA", (W * COLS, H * ROWS), (255, 0, 255, 255))
    for i, fr in enumerate(frames()):
        tile = Image.new("RGBA", (W, H), (255, 0, 255, 255))
        solid = fr.copy()
        solid.putalpha(solid.getchannel("A").point(lambda v: 255 if v >= 128 else 0))
        tile.paste(solid, (0, 0), solid)
        sheet.paste(tile, ((i % COLS) * W, (i // COLS) * H))
    return sheet.convert("RGB")


def icon(sheet):
    tile = sheet.crop((0, 0, W, H)).convert("RGBA")
    px = tile.load()
    for y in range(H):
        for x in range(W):
            if px[x, y][:3] == (255, 0, 255):
                px[x, y] = (0, 0, 0, 0)
    out = Image.new("RGBA", (48, 48), (0, 0, 0, 0))
    out.alpha_composite(tile.resize((41, 48), Image.NEAREST), (3, 0))
    return out


def b64(img):
    buf = io.BytesIO()
    img.save(buf, "PNG", optimize=True)
    return base64.b64encode(buf.getvalue()).decode()


def anim(id, name, frames, sx=0, sy=0, ex=None, ey=None, si=150, ei=None, repeat="0", repeatfrom=0,
         nexts=(), border=(), gravity=(), action="none", so=1, eo=1):
    ex = sx if ex is None else ex
    ey = sy if ey is None else ey
    ei = si if ei is None else ei
    def mv(tag, x, y, op, iv):
        return f"<{tag}><x>{x}</x><y>{y}</y><offsety>0</offsety><opacity>{op}</opacity><interval>{iv}</interval></{tag}>"
    def nx(lst):
        return "".join(f'<next probability="{p}" only="{o}">{i}</next>' for i, p, o in lst)
    s = f'<animation id="{id}"><name>{name}</name>{mv("start", sx, sy, so, si)}{mv("end", ex, ey, eo, ei)}'
    s += f'<sequence repeatfrom="{repeatfrom}" repeat="{repeat}">' + "".join(f"<frame>{f}</frame>" for f in frames)
    s += nx(nexts) + f"<action>{action}</action></sequence>"
    if border:
        s += "<border>" + nx(border) + "</border>"
    if gravity:
        s += "<gravity>" + nx(gravity) + "</gravity>"
    return s + "</animation>"


def xml(sheet_png, icon_png):
    N = "none"
    G = [(5, 100, N)]  # sin suelo: cae
    A = [
        anim(1, "walk", [1, 0, 2, 0], sx=-2, si=150, repeat="4+random/15",
             nexts=[(1, 35, N), (7, 15, N), (8, 8, N), (9, 8, N), (10, 10, N), (11, 8, N), (12, 4, N), (16, 8, N)],
             border=[(2, 100, N)], gravity=G),
        anim(2, "rotate_a", [0, 24, 25, 26], si=90, nexts=[(3, 100, N)], action="flip"),
        anim(3, "rotate_b", [26, 25, 24, 0], si=90, nexts=[(1, 100, N)]),
        anim(4, "drag", [5, 6], si=180, nexts=[(1, 100, N)]),
        anim(5, "fall", [7], sy=2, ey=10, si=60, ei=40, repeat="25", nexts=[(5, 100, N)], border=[(6, 100, N)]),
        anim(6, "fall soft", [8, 8, 0], si=120, nexts=[(7, 50, N), (1, 50, N)]),
        anim(7, "idle", [0, 0, 0, 4, 0, 0], si=250, repeat="random/30+1",
             nexts=[(1, 40, N), (8, 12, N), (9, 12, N), (10, 14, N), (11, 10, N), (12, 5, N), (16, 7, N)], gravity=G),
        anim(8, "salute", [0, 9, 9, 9, 9, 9, 0], si=220, nexts=[(7, 50, N), (1, 50, N)], gravity=G),
        anim(9, "wave", [10, 11, 10, 11, 10, 11, 0], si=180, nexts=[(7, 50, N), (1, 50, N)], gravity=G),
        anim(10, "dance", [12, 13], si=250, repeat="4+random/20", nexts=[(7, 40, N), (1, 60, N)], gravity=G),
        anim(11, "jump", [14, 15, 16, 0, 14, 0], si=130, nexts=[(1, 60, N), (11, 15, N), (7, 25, N)], gravity=G),
        anim(12, "sleep", [4, 17, 18, 17, 18], si=500, repeat="random/10+2", repeatfrom=1,
             nexts=[(7, 70, N), (9, 30, N)], gravity=G),
        anim(13, "kill", [10, 11, 10, 19, 20, 21, 21], si=200, so=1, eo=0),
        anim(14, "sync", [0], si=100, nexts=[(7, 100, N)]),
        anim(15, "fall hard", [22, 22, 22, 22, 8, 0], si=180, nexts=[(7, 100, N)]),
        anim(16, "music", [23, 12, 13, 23], si=300, repeat="1", nexts=[(10, 60, N), (7, 40, N)], gravity=G),
    ]
    spawns = ('<spawn id="1" probability="60"><x>screenW+10</x><y>areaH-imageH</y><next probability="100">1</next></spawn>'
              '<spawn id="2" probability="40"><x>random*(screenW-imageW-50)/100+25</x><y>-imageH-20</y>'
              '<next probability="100">5</next></spawn>')
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<animations xmlns="https://esheep.petrucci.ch/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">\n'
            '<header><author>billkevin91</author><title>Corazón</title><petname>Corazón</petname>'
            '<version>1.0</version><info>Corazón con audífonos dibujado para Mascotas en pantalla. '
            'Camina, saluda, hace el saludo militar, baila con su música, salta y se duerme.</info>'
            f'<application>1</application><icon><![CDATA[{icon_png}]]></icon></header>\n'
            f'<image><tilesx>{COLS}</tilesx><tilesy>{ROWS}</tilesy><png><![CDATA[{sheet_png}]]></png>'
            '<transparency>Magenta</transparency></image>\n'
            f'<spawns>{spawns}</spawns>\n<animations>\n' + "\n".join(A) + '\n</animations>\n</animations>\n')


def preview(sheet):
    big = sheet.resize((sheet.width * 4, sheet.height * 4), Image.NEAREST).convert("RGBA")
    px = big.load()
    for y in range(big.height):
        for x in range(big.width):
            if px[x, y][:3] == (255, 0, 255):
                px[x, y] = (200, 230, 255, 255)
    d = ImageDraw.Draw(big)
    for i in range(COLS * ROWS):
        x, y = (i % COLS) * W * 4, (i // COLS) * H * 4
        d.rectangle((x, y, x + W * 4 - 1, y + H * 4 - 1), outline=(150, 170, 200, 255))
        d.text((x + 3, y + 2), str(i), fill=(0, 0, 0, 255))
    return big


def demo(sheet, path):
    def fr(i):
        t = sheet.crop(((i % COLS) * W, (i // COLS) * H, (i % COLS) * W + W, (i // COLS) * H + H)).convert("RGBA")
        px = t.load()
        for y in range(H):
            for x in range(W):
                if px[x, y][:3] == (255, 0, 255):
                    px[x, y] = (0, 0, 0, 0)
        return t
    S, BW, BH = 3, 220, 70
    seq, pos = [], [160.0]
    def add(i, ms, dx=0, flip=False):
        pos[0] += dx
        bg = Image.new("RGBA", (BW, BH), (210, 232, 250, 255))
        for gx in range(0, BW, 6):
            bg.putpixel((gx, BH - 3), (120, 150, 90, 255))
        t = fr(i).transpose(Image.FLIP_LEFT_RIGHT) if flip else fr(i)
        bg.alpha_composite(t, (int(pos[0]), BH - 2 - H))
        seq.append((bg.resize((BW * S, BH * S), Image.NEAREST).convert("RGB"), ms))
    for k in range(16): add((1, 0, 2, 0)[k % 4], 150, -2)
    for i in (0, 9, 9, 9, 9, 9, 0): add(i, 220)
    for i in (10, 11, 10, 11, 10, 11, 0): add(i, 180)
    for i in (23, 12, 13, 12, 13, 12, 13, 23): add(i, 260)
    for i in (14, 15, 16, 0, 14, 15, 16, 0): add(i, 130)
    for i in (0, 24, 25, 26): add(i, 90)
    for i in (26, 25, 24, 0): add(i, 90, flip=True)
    for k in range(12): add((1, 0, 2, 0)[k % 4], 150, 2, flip=True)
    for i in (4, 17, 18, 17, 18): add(i, 400)
    for i in (22, 22, 22, 22, 8, 0): add(i, 180)
    for i in (10, 11, 10, 19, 20, 21, 21): add(i, 220)
    strip = Image.new("RGB", (seq[0][0].width, seq[0][0].height * len(seq)))
    for k, (im, _) in enumerate(seq):
        strip.paste(im, (0, k * im.height))
    pal = strip.quantize(colors=64, method=Image.MEDIANCUT)
    frames_ = [im.quantize(palette=pal, dither=Image.Dither.NONE) for im, _ in seq]
    frames_[0].save(path, save_all=True, append_images=frames_[1:], duration=[d for _, d in seq], loop=0)


if __name__ == "__main__":
    sheet = build_sheet()
    sheet.save(os.path.join(HERE, "sheet.png"))
    ic = icon(sheet)
    ic.save(os.path.join(HERE, "icon.png"))
    with open(os.path.join(HERE, "animations.xml"), "w", encoding="utf-8") as f:
        f.write(xml(b64(sheet), b64(ic)))
    preview(sheet).save(os.path.join(HERE, "preview.png"))
    demo(sheet, os.path.join(HERE, "demo.gif"))
    print("listo:", sheet.size)
