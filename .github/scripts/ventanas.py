"""Lee `adb shell dumpsys window windows` (por la entrada estándar) para la prueba de humo.

  python3 ventanas.py contar TITULO       -> cuántas ventanas visibles tienen ese título
  python3 ventanas.py centros PAQUETE     -> centro "x y" de cada mascota visible, una por línea
"""
import re
import sys

modo, nombre = sys.argv[1], sys.argv[2]
ventanas = []
actual = None
for linea in sys.stdin:
    m = re.match(r"\s*Window #\d+ Window\{\S+ \S+ (.*)\}:", linea)
    if m:
        actual = {"titulo": m.group(1), "visible": False, "marco": None}
        ventanas.append(actual)
        continue
    if actual is None:
        continue
    if "mViewVisibility=0x0" in linea:
        actual["visible"] = True
    f = re.search(r"(?:^|\s)m?[fF]rame=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", linea)
    if f and actual["marco"] is None:
        actual["marco"] = tuple(int(v) for v in f.groups())

visibles = [v for v in ventanas if v["visible"]]
if modo == "contar":
    print(sum(1 for v in visibles if v["titulo"] == nombre))
elif modo == "centros":
    for v in visibles:
        if v["titulo"] == nombre and v["marco"]:
            l, t, r, b = v["marco"]
            print((l + r) // 2, (t + b) // 2)
