"""
Genera el carro rojo (dibujos y animations.xml) para Mascotas en pantalla / desktopPet.
Dibujo original hecho para esta app: un sedán rojo de los 90, visto de lado, mirando a la izquierda.
Uso: python3 generar.py   (crea sheet.png, icon.png, animations.xml y preview.png aquí mismo)
"""
import base64, io, math, os, random
from PIL import Image, ImageDraw

W, H = 72, 36          # tamaño de cada dibujo
COLS, ROWS = 9, 4      # cuadrícula de la hoja
HERE = os.path.dirname(os.path.abspath(__file__))

# Colores
OUT = (58, 6, 6, 255)        # contorno
RED = (206, 24, 24, 255)     # carrocería
RED_D = (140, 12, 12, 255)   # sombra
RED_L = (255, 96, 80, 255)   # brillo
GLASS = (120, 178, 214, 255)
GLASS_D = (60, 104, 140, 255)
GLASS_L = (210, 236, 250, 255)
CHROME = (200, 200, 210, 255)
CHROME_D = (120, 120, 132, 255)
TIRE = (26, 26, 28, 255)
RIM = (176, 178, 186, 255)
HUB = (90, 92, 100, 255)
LIGHT = (255, 244, 170, 255)
AMBER = (255, 160, 30, 255)
TAIL = (255, 40, 40, 255)
TAIL_D = (120, 0, 0, 255)
SMOKE = (150, 150, 155, 255)
SMOKE_L = (205, 205, 210, 255)
DARK = (40, 40, 44, 255)
FIRE = (255, 150, 20, 255)
FIRE_Y = (255, 230, 80, 255)
TRANS = (0, 0, 0, 0)

GROUND = H - 1
R = 6                      # radio de rueda
WY = GROUND - R            # centro de las ruedas
WHEELS = (16, 55)          # ruedas delantera y trasera

BODY = [(4, 28), (2, 26), (2, 21), (3, 19), (6, 18), (23, 16), (31, 8), (47, 8), (55, 15),
        (66, 15), (69, 17), (69, 25), (68, 28)]
WINDOWS = [(26, 15), (32, 9), (46, 9), (53, 15)]


def wheel(d, cx, cy, angle, flat=False):
    if flat:
        d.ellipse((cx - R, cy - R + 3, cx + R, cy + R), fill=TIRE)
        d.ellipse((cx - 3, cy - 1, cx + 3, cy + 4), fill=HUB)
        return
    d.ellipse((cx - R, cy - R, cx + R, cy + R), fill=TIRE)
    d.ellipse((cx - 3, cy - 3, cx + 3, cy + 3), fill=RIM)
    for k in range(3):  # radios que giran
        a = math.radians(angle + k * 60)
        dx, dy = round(math.cos(a) * 3), round(math.sin(a) * 3)
        d.line((cx - dx, cy - dy, cx + dx, cy + dy), fill=HUB)
    d.point((cx, cy), fill=CHROME_D)


def car(angle=0, bob=0, lights=False, damage=0, flat=False, wheel_drop=0, squash=0):
    """El carro entero en una capa transparente. damage 0..5 = cada vez más destrozado."""
    img = Image.new("RGBA", (W, H), TRANS)
    body = Image.new("RGBA", (W, H), TRANS)
    b = ImageDraw.Draw(body)
    pts = [(x, y - bob + squash) for x, y in BODY]
    if damage >= 5:  # techo aplastado
        pts = [(x, max(y, 13) - bob + squash) if 8 <= y <= 13 else (x, y - bob + squash) for x, y in BODY]
    b.polygon(pts, fill=RED)
    yo = -bob + squash
    # sombra baja y brillo del lateral
    b.rectangle((3, 24 + yo, 68, 27 + yo), fill=RED_D)
    b.line((6, 18 + yo, 22, 16 + yo), fill=RED_L)
    b.line((55, 16 + yo, 66, 16 + yo), fill=RED_L)
    b.line((4, 20 + yo, 67, 20 + yo), fill=RED_L)
    # moldura cromada y paragolpes
    b.line((4, 23 + yo, 67, 23 + yo), fill=CHROME)
    b.rectangle((1, 24 + yo, 4, 26 + yo), fill=CHROME)
    b.rectangle((67, 24 + yo, 70, 26 + yo), fill=CHROME)
    # ventanas
    win = [(x, (max(y, 13) if damage >= 5 else y) + yo) for x, y in WINDOWS]
    if damage < 4:
        b.polygon(win, fill=GLASS)
        b.line((29, 13 + yo, 33, 10 + yo), fill=GLASS_L)
        b.line((42, 13 + yo, 45, 10 + yo), fill=GLASS_L)
        b.line((27, 15 + yo, 52, 15 + yo), fill=GLASS_D)
    else:
        b.polygon(win, fill=DARK)  # sin cristales
    b.rectangle((39, (13 if damage >= 5 else 9) + yo, 40, 15 + yo), fill=RED_D)  # pilar B
    # puertas, manijas y espejo
    for x in (24, 39, 53):
        b.line((x, 16 + yo, x, 27 + yo), fill=RED_D)
    b.rectangle((34, 18 + yo, 36, 18 + yo), fill=CHROME)
    b.rectangle((49, 18 + yo, 51, 18 + yo), fill=CHROME)
    b.rectangle((24, 13 + yo, 26, 15 + yo), fill=RED)
    # faros delanteros y traseros
    b.rectangle((2, 20 + yo, 4, 21 + yo), fill=LIGHT)
    b.point((2, 22 + yo), fill=AMBER)
    b.rectangle((67, 18 + yo, 69, 21 + yo), fill=TAIL)
    b.point((69, 18 + yo), fill=TAIL_D)
    b.polygon(pts, outline=OUT)
    # huecos de las ruedas
    for cx in WHEELS:
        b.ellipse((cx - 8, WY - 8 + yo, cx + 8, WY + 8 + yo), fill=TRANS)  # se ve el paso oscuro de detrás
        b.arc((cx - 8, WY - 8 + yo, cx + 8, WY + 8 + yo), 180, 360, fill=OUT)
    damage_marks(b, damage, yo)
    w = ImageDraw.Draw(img)
    for cx in WHEELS:
        # paso de rueda oscuro detrás de la llanta
        w.pieslice((cx - 8, WY - 8 + yo, cx + 8, WY + 8 + yo), 180, 360, fill=DARK)
        wheel(w, cx, WY + wheel_drop, angle, flat=flat)
    img.alpha_composite(body)
    if lights:  # faros encendidos con rayos
        d = ImageDraw.Draw(img)
        d.rectangle((2, 20 + yo, 4, 21 + yo), fill=(255, 255, 230, 255))
        d.line((0, 18 + yo, 1, 17 + yo), fill=LIGHT)
        d.line((0, 20 + yo, 1, 20 + yo), fill=LIGHT)
        d.line((0, 22 + yo, 1, 23 + yo), fill=LIGHT)
    return img


def damage_marks(b, damage, yo):
    if damage >= 1:  # abolladura en el capó y parabrisas rajado
        b.line((10, 18 + yo, 14, 19 + yo), fill=RED_D)
        b.line((12, 17 + yo, 13, 20 + yo), fill=OUT)
        b.line((28, 12 + yo, 31, 14 + yo), fill=GLASS_L)
        b.line((30, 11 + yo, 29, 14 + yo), fill=GLASS_L)
    if damage >= 2:  # puerta abollada, ventana trasera rota
        b.line((30, 21 + yo, 34, 25 + yo), fill=OUT)
        b.line((33, 20 + yo, 31, 26 + yo), fill=RED_D)
        b.polygon([(42, 10 + yo), (46, 10 + yo), (51, 14 + yo), (44, 14 + yo)], fill=DARK)
    if damage >= 3:  # paragolpes colgando y abolladuras atrás
        b.rectangle((1, 24 + yo, 4, 26 + yo), fill=TRANS)
        b.line((1, 27 + yo, 5, 29 + yo), fill=CHROME_D)
        b.line((58, 18 + yo, 62, 22 + yo), fill=OUT)
        b.line((45, 22 + yo, 50, 21 + yo), fill=OUT)
    if damage >= 4:  # sin puerta delantera
        b.rectangle((25, 17 + yo, 38, 26 + yo), fill=DARK)
        b.line((25, 17 + yo, 38, 17 + yo), fill=OUT)
    if damage >= 5:  # todo destrozado
        b.line((6, 21 + yo, 20, 24 + yo), fill=OUT)
        b.line((56, 17 + yo, 66, 23 + yo), fill=OUT)
        b.rectangle((67, 18 + yo, 69, 21 + yo), fill=TAIL_D)


def smoke(img, puffs):
    d = ImageDraw.Draw(img)
    for x, y, r in puffs:
        d.ellipse((x - r, y - r, x + r, y + r), fill=SMOKE)
        d.ellipse((x - r + 1, y - r, x, y - 1), fill=SMOKE_L)
    return img


def squash_x(img, f):
    w = max(2, round(W * f))
    s = img.resize((w, H), Image.NEAREST)
    out = Image.new("RGBA", (W, H), TRANS)
    out.alpha_composite(s, ((W - w) // 2, 0))
    return out


def rotate(img, deg):
    # Sube el carro un poco antes de inclinarlo para que no se corte por abajo ni por los lados
    up = Image.new("RGBA", (W, H), TRANS)
    up.alpha_composite(img, (0, -2))
    return up.rotate(deg, resample=Image.NEAREST, center=(W // 2, H // 2))


def shift(img, dy):
    out = Image.new("RGBA", (W, H), TRANS)
    out.alpha_composite(img, (0, dy))
    return out


def dust(img, big):
    d = ImageDraw.Draw(img)
    for x, y, r in ((4, 31, 3), (9, 33, 2), (1, 33, 2), (65, 31, 3), (70, 33, 2), (60, 33, 2)) if big \
            else ((4, 32, 2), (8, 33, 1), (66, 32, 2), (70, 33, 1)):
        d.ellipse((x - r, y - r, x + r, y + r), fill=SMOKE_L)
        d.point((x, y - r), fill=SMOKE)
    return img


def text_z(img, x, y, big):
    d = ImageDraw.Draw(img)
    s = 4 if big else 3
    d.line((x, y, x + s, y), fill=GLASS_D)
    d.line((x + s, y, x, y + s), fill=GLASS_D)
    d.line((x, y + s, x + s, y + s), fill=GLASS_D)
    return img


def frames():
    f = []
    f.append(car(0))                       # 0 parado / conduciendo
    f.append(car(40, bob=1))               # 1
    f.append(car(80))                      # 2
    f.append(car(0))                       # 3 parado
    f.append(car(0, lights=True))          # 4 faros
    f.append(car(0, lights=True))          # 5
    f.append(smoke(car(0), [(71, 27, 1)]))                         # 6 humo del escape
    f.append(smoke(car(0), [(71, 26, 2), (70, 22, 1)]))            # 7
    f.append(smoke(car(0), [(70, 24, 2), (68, 19, 2), (70, 15, 1)]))  # 8
    honk = car(0, bob=1)                   # 9 bocina
    d = ImageDraw.Draw(honk)
    for i in range(3):
        d.line((0, 10 + i * 3, 2, 9 + i * 3), fill=AMBER)
    f.append(honk)
    f.append(squash_x(car(0), 0.6))        # 10 dar la vuelta
    f.append(squash_x(car(0), 0.25))       # 11
    f.append(car(0))                       # 12 (libre)
    f.append(rotate(car(0, wheel_drop=1), 4))    # 13 arrastrar (colgando)
    f.append(rotate(car(0, wheel_drop=1), -4))   # 14
    f.append(rotate(car(0, wheel_drop=1), -6))   # 15 cayendo, el morro hacia abajo
    f.append(car(0, squash=2))             # 16 aterrizar
    for dmg in range(1, 6):                # 17-21 destrozo
        img = car(0, damage=dmg, flat=dmg >= 5)
        if dmg >= 3:
            img = smoke(img, [(12, 14, 2), (16, 10, 1)] if dmg < 5 else [(12, 13, 3), (18, 8, 2), (30, 4, 2)])
        f.append(img)
    wreck = car(0, damage=5, flat=True)    # 22 restos con fuego
    d = ImageDraw.Draw(wreck)
    d.polygon([(8, 17), (11, 10), (13, 15), (15, 8), (18, 17)], fill=FIRE)
    d.polygon([(11, 17), (13, 13), (15, 17)], fill=FIRE_Y)
    f.append(smoke(wreck, [(14, 5, 3), (22, 2, 2)]))
    for k in range(2):                     # 23-24 a toda velocidad
        img = car(k * 60, bob=k)
        d = ImageDraw.Draw(img)
        for i, y in enumerate((12, 19, 25)):
            d.line((W - 3 - (i + k) % 2 * 2, y, W - 1, y), fill=SMOKE_L)
        f.append(img)
    f.append(text_z(car(0), 58, 4, False))  # 25-26 durmiendo
    f.append(text_z(text_z(car(0), 58, 4, False), 64, 0, True))
    # 27-31 aterrizajes: golpe fuerte (aplastado + polvo), rebote con el morro arriba, caer y asentarse
    f.append(dust(car(0, squash=3), True))           # 27
    f.append(shift(rotate(car(0), 4), -4))           # 28
    f.append(shift(car(0), -2))                      # 29
    f.append(dust(car(0, squash=1), False))          # 30
    f.append(dust(car(0, squash=2), False))          # 31 aterrizaje suave
    return f


def build_sheet():
    sheet = Image.new("RGBA", (W * COLS, H * ROWS), (255, 0, 255, 255))
    for i, fr in enumerate(frames()):
        x, y = (i % COLS) * W, (i // COLS) * H
        tile = Image.new("RGBA", (W, H), (255, 0, 255, 255))
        # sin semitransparencias: así el fondo magenta se quita limpio
        solid = fr.copy()
        a = solid.getchannel("A").point(lambda v: 255 if v >= 128 else 0)
        solid.putalpha(a)
        tile.paste(solid, (0, 0), solid)
        sheet.paste(tile, (x, y))
    return sheet.convert("RGB")


def icon(sheet):
    tile = sheet.crop((0, 0, W, H)).convert("RGBA")
    px = tile.load()
    for yy in range(H):
        for xx in range(W):
            if px[xx, yy][:3] == (255, 0, 255):
                px[xx, yy] = (0, 0, 0, 0)
    out = Image.new("RGBA", (48, 48), (0, 0, 0, 0))
    small = tile.resize((48, 24), Image.NEAREST)
    out.alpha_composite(small, (0, 14))
    return out


def b64(img):
    buf = io.BytesIO()
    img.save(buf, "PNG", optimize=True)
    return base64.b64encode(buf.getvalue()).decode()


def anim(id, name, frames, sx=0, sy=0, ex=None, ey=None, si=150, ei=None, repeat="0", repeatfrom=0,
         nexts=(), border=(), gravity=(), action="none", so=1, eo=1, offs=0, offe=0):
    ex = sx if ex is None else ex
    ey = sy if ey is None else ey
    ei = si if ei is None else ei
    def mv(tag, x, y, off, op, iv):
        return (f"<{tag}><x>{x}</x><y>{y}</y><offsety>{off}</offsety><opacity>{op}</opacity>"
                f"<interval>{iv}</interval></{tag}>")
    def nx(lst):
        return "".join(f'<next probability="{p}" only="{o}">{i}</next>' for i, p, o in lst)
    s = f'<animation id="{id}"><name>{name}</name>{mv("start", sx, sy, offs, so, si)}{mv("end", ex, ey, offe, eo, ei)}'
    s += f'<sequence repeatfrom="{repeatfrom}" repeat="{repeat}">' + "".join(f"<frame>{f}</frame>" for f in frames)
    s += nx(nexts) + f"<action>{action}</action></sequence>"
    if border:
        s += "<border>" + nx(border) + "</border>"
    if gravity:
        s += "<gravity>" + nx(gravity) + "</gravity>"
    return s + "</animation>"


def xml(sheet_png, icon_png):
    N, T = "none", "taskbar"
    A = [
        anim(1, "walk", [0, 1, 2], sx=-3, si=120, repeat="8+random/10",
             nexts=[(1, 35, N), (7, 15, N), (10, 12, N), (11, 12, N), (8, 10, N), (13, 3, N), (9, 10, N)],
             border=[(2, 100, N)], gravity=[(5, 100, N)]),
        anim(2, "rotate_a", [0, 10, 11], si=110, nexts=[(3, 100, N)], action="flip"),
        anim(3, "rotate_b", [11, 10, 0], si=110, nexts=[(1, 100, N)]),
        anim(4, "drag", [13, 14], si=140, nexts=[(1, 100, N)]),
        anim(5, "fall", [15], sy=2, ey=10, si=60, ei=40, repeat="25", nexts=[(5, 100, N)], border=[(6, 100, N)]),
        anim(6, "fall soft", [31, 16, 29, 3, 3], si=110, nexts=[(1, 70, N), (7, 30, N)]),
        anim(7, "idle", [3], si=300, repeat="random/10+4",
             nexts=[(1, 45, N), (8, 15, N), (9, 15, N), (13, 6, N), (10, 14, N), (7, 5, N)],
             gravity=[(5, 100, N)]),
        anim(8, "lights", [3, 4, 3, 4, 5, 3], si=150, nexts=[(7, 50, N), (1, 50, N)], gravity=[(5, 100, N)]),
        anim(9, "smoke", [6, 7, 8], si=150, repeat="2", nexts=[(7, 50, N), (1, 50, N)], gravity=[(5, 100, N)]),
        anim(10, "honk", [9, 3, 9, 3], si=150, nexts=[(1, 70, N), (11, 30, N)], gravity=[(5, 100, N)]),
        anim(11, "run", [23, 24], sx=-6, ex=-9, si=80, repeat="8+random/10",
             nexts=[(11, 30, N), (1, 70, N)], border=[(12, 100, N)], gravity=[(5, 100, N)]),
        anim(12, "bonk", [16, 3, 3], sx=3, ex=0, si=120, nexts=[(2, 100, N)]),
        anim(13, "sleep", [3, 25, 26, 25, 26], si=450, repeat="random/10+2", repeatfrom=1,
             nexts=[(8, 40, N), (7, 60, N)], gravity=[(5, 100, N)]),
        anim(14, "kill", [3, 17, 17, 18, 18, 19, 19, 20, 20, 21, 21, 22, 22, 22], si=220, so=1, eo=0),
        anim(15, "sync", [3], si=100, nexts=[(7, 100, N)]),
        anim(16, "fall hard", [27, 27, 28, 28, 29, 30, 3, 29, 3, 3], si=90, ei=140, nexts=[(1, 100, N)]),
    ]
    spawns = ('<spawn id="1" probability="60"><x>screenW+10</x><y>areaH-imageH</y><next probability="100">1</next></spawn>'
              '<spawn id="2" probability="40"><x>random*(screenW-imageW-50)/100+25</x><y>-imageH-20</y>'
              '<next probability="100">5</next></spawn>')
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<animations xmlns="https://esheep.petrucci.ch/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">\n'
            '<header><author>billkevin91</author><title>Carro rojo</title><petname>Carro rojo</petname>'
            '<version>1.0</version><info>Sedán rojo de los 90 dibujado para Mascotas en pantalla. '
            'Pasea, toca la bocina, echa humo y se duerme. Despídelo con doble toque y verás.</info>'
            f'<application>1</application><icon><![CDATA[{icon_png}]]></icon></header>\n'
            f'<image><tilesx>{COLS}</tilesx><tilesy>{ROWS}</tilesy><png><![CDATA[{sheet_png}]]></png>'
            '<transparency>Magenta</transparency></image>\n'
            f'<spawns>{spawns}</spawns>\n<animations>\n' + "\n".join(A) + '\n</animations>\n</animations>\n')


def preview(sheet):
    big = sheet.resize((sheet.width * 4, sheet.height * 4), Image.NEAREST).convert("RGBA")
    px = big.load()
    for yy in range(big.height):
        for xx in range(big.width):
            if px[xx, yy][:3] == (255, 0, 255):
                px[xx, yy] = (200, 230, 255, 255)
    d = ImageDraw.Draw(big)
    for i in range(COLS * ROWS):
        x, y = (i % COLS) * W * 4, (i // COLS) * H * 4
        d.rectangle((x, y, x + W * 4 - 1, y + H * 4 - 1), outline=(150, 170, 200, 255))
        d.text((x + 3, y + 2), str(i), fill=(0, 0, 0, 255))
    return big


def demo(sheet, path):
    """GIF de muestra: conduce, bocina, faros, humo, acelera, choca, da la vuelta, duerme y se destroza."""
    def fr(i):
        t = sheet.crop(((i % COLS) * W, (i // COLS) * H, (i % COLS) * W + W, (i // COLS) * H + H)).convert("RGBA")
        px = t.load()
        for y in range(H):
            for x in range(W):
                if px[x, y][:3] == (255, 0, 255):
                    px[x, y] = (0, 0, 0, 0)
        return t
    S, BW, BH = 3, 300, 56
    seq, pos = [], [220.0]
    def add(i, ms, dx=0, flip=False):
        pos[0] += dx
        bg = Image.new("RGBA", (BW, BH), (210, 232, 250, 255))
        for gx in range(0, BW, 6):
            bg.putpixel((gx, BH - 3), (120, 150, 90, 255))
        t = fr(i).transpose(Image.FLIP_LEFT_RIGHT) if flip else fr(i)
        bg.alpha_composite(t, (int(pos[0]), BH - 2 - H))
        seq.append((bg.resize((BW * S, BH * S), Image.NEAREST).convert("RGB"), ms))
    for k in range(18): add((0, 1, 2)[k % 3], 120, -3)
    for i in (9, 3, 9, 3): add(i, 150)
    for i in (3, 4, 3, 4, 5, 3): add(i, 150)
    for i in (6, 7, 8, 6, 7, 8): add(i, 150)
    for k in range(10): add((23, 24)[k % 2], 80, -8)
    for i in (16, 3, 3): add(i, 120, 3)
    for i in (0, 10, 11): add(i, 110)
    for i in (11, 10, 0): add(i, 110, flip=True)
    for k in range(12): add((0, 1, 2)[k % 3], 120, 3, flip=True)
    for i in (3, 25, 26, 25, 26, 25, 26): add(i, 400)
    for i in (27, 27, 28, 28, 29, 30, 3, 29, 3, 3): add(i, 110)
    for i in (3, 17, 17, 18, 18, 19, 19, 20, 20, 21, 21, 22, 22, 22): add(i, 220)
    # Una sola paleta para todos los cuadros (si no, cada cuadro sale con otros colores)
    strip = Image.new("RGB", (seq[0][0].width, seq[0][0].height * len(seq)))
    for k, (im, _) in enumerate(seq):
        strip.paste(im, (0, k * im.height))
    pal = strip.quantize(colors=64, method=Image.MEDIANCUT)
    frames = [im.quantize(palette=pal, dither=Image.Dither.NONE) for im, _ in seq]
    frames[0].save(path, save_all=True, append_images=frames[1:], duration=[d for _, d in seq], loop=0)


if __name__ == "__main__":
    sheet = build_sheet()
    sheet.save(os.path.join(HERE, "sheet.png"))
    ic = icon(sheet)
    ic.save(os.path.join(HERE, "icon.png"))
    with open(os.path.join(HERE, "animations.xml"), "w", encoding="utf-8") as f:
        f.write(xml(b64(sheet), b64(ic)))
    preview(sheet).save(os.path.join(HERE, "preview.png"))
    demo(sheet, os.path.join(HERE, "demo.gif"))
    # La copia que va dentro de la app
    app = os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "pets", "carro_rojo")
    if os.path.isdir(os.path.dirname(app)):
        os.makedirs(app, exist_ok=True)
        for name in ("animations.xml", "icon.png"):
            with open(os.path.join(HERE, name), "rb") as src, open(os.path.join(app, name), "wb") as dst:
                dst.write(src.read())
    print("listo:", sheet.size)
