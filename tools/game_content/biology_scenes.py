"""
Ilustrasi preparat ORISINAL untuk 20 soal Micro Hunter (Biologi), digambar sebagai data vektor.

Setiap daerah (target maupun pengecoh) dibentuk dari geometri yang SAMA dengan yang digambar,
sehingga titik sentuh selalu sejajar dengan gambar di resolusi layar mana pun. Koordinat DUNIA
1000 × 700 (sumbu y ke bawah). Tidak ada label teks di gambar (label = jawaban).

Skema bersifat diagramatik (bukan foto), mengikuti ciri diagnostik yang diajarkan di SMP/SMA.
"""
from __future__ import annotations

import math
import random

W, H = 1000.0, 700.0


def R(v: float) -> float:
    return round(v, 1)


def ellipse_pts(cx, cy, rx, ry, rot=0.0, n=24):
    c, s = math.cos(math.radians(rot)), math.sin(math.radians(rot))
    out = []
    for i in range(n):
        t = 2 * math.pi * i / n
        x, y = rx * math.cos(t), ry * math.sin(t)
        out.append((cx + x * c - y * s, cy + x * s + y * c))
    return out


def blob_pts(cx, cy, r, rng, irr=0.15, n=14, sx=1.0, sy=1.0):
    out = []
    for i in range(n):
        t = 2 * math.pi * i / n
        rr = r * (1 + rng.uniform(-irr, irr))
        out.append((cx + rr * math.cos(t) * sx, cy + rr * math.sin(t) * sy))
    return out


def rect_pts(x1, y1, x2, y2, rng=None, jit=0.0):
    pts = [(x1, y1), (x2, y1), (x2, y2), (x1, y2)]
    if rng and jit:
        pts = [(x + rng.uniform(-jit, jit), y + rng.uniform(-jit, jit)) for x, y in pts]
    return pts


def rounded_rect(x1, y1, x2, y2, r, n=5):
    pts = []
    for (cx, cy, a0) in [(x2 - r, y1 + r, -90), (x2 - r, y2 - r, 0), (x1 + r, y2 - r, 90), (x1 + r, y1 + r, 180)]:
        for i in range(n + 1):
            a = math.radians(a0 + 90 * i / n)
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def inset(pts, d):
    cx = sum(p[0] for p in pts) / len(pts)
    cy = sum(p[1] for p in pts) / len(pts)
    out = []
    for x, y in pts:
        dx, dy = x - cx, y - cy
        L = math.hypot(dx, dy) or 1
        out.append((x - dx / L * d, y - dy / L * d))
    return out


def rotate(pts, cx, cy, deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return [(cx + (x - cx) * c - (y - cy) * s, cy + (x - cx) * s + (y - cy) * c) for x, y in pts]


def band_around(p1, p2, half):
    """Poligon persegi panjang di sekitar segmen (untuk daerah garis tipis seperti ekor/flagel)."""
    (x1, y1), (x2, y2) = p1, p2
    L = math.hypot(x2 - x1, y2 - y1) or 1
    nx, ny = -(y2 - y1) / L * half, (x2 - x1) / L * half
    return [(x1 + nx, y1 + ny), (x2 + nx, y2 + ny), (x2 - nx, y2 - ny), (x1 - nx, y1 - ny)]


def area(pts):
    a = 0.0
    for i in range(len(pts)):
        x1, y1 = pts[i]
        x2, y2 = pts[(i + 1) % len(pts)]
        a += x1 * y2 - x2 * y1
    return abs(a) / 2


class Scene:
    def __init__(self, bg: str):
        self.bg = bg
        self.shapes: list[dict] = []
        self.regions: dict[str, dict] = {}
        self.order: list[str] = []

    def _shape(self, k, pts_or_params, fill=None, stroke=None, w=0.0):
        p = [R(v) for v in pts_or_params] if k == "e" else [R(v) for xy in pts_or_params for v in xy]
        d = {"k": k, "p": p}
        if fill:
            d["fill"] = fill
        if stroke:
            d["stroke"] = stroke
        if w:
            d["w"] = w
        self.shapes.append(d)

    def e(self, cx, cy, rx, ry, rot=0.0, fill=None, stroke=None, w=0.0):
        self._shape("e", [cx, cy, rx, ry, rot], fill, stroke, w)
        return ellipse_pts(cx, cy, rx, ry, rot)

    def poly(self, pts, fill=None, stroke=None, w=0.0):
        self._shape("p", pts, fill, stroke, w)
        return pts

    def curve(self, pts, fill=None, stroke=None, w=0.0):
        self._shape("c", pts, fill, stroke, w)
        return pts

    def line(self, pts, stroke, w=2.0):
        self._shape("l", pts, None, stroke, w)

    def oline(self, pts, stroke, w=2.0):
        self._shape("o", pts, None, stroke, w)

    def region(self, rid, name, pts, target=False):
        if rid not in self.regions:
            self.regions[rid] = {"id": rid, "name": name, "polygons": [], "target": target}
            self.order.append(rid)
        self.regions[rid]["polygons"].append([{"x": R(x), "y": R(y)} for x, y in pts])

    def export(self, decoy_order: list[str] | None = None) -> tuple[list[dict], list[dict]]:
        targets = [self.regions[r] for r in self.order if self.regions[r]["target"]]
        decoys = [self.regions[r] for r in (decoy_order or self.order) if r in self.regions and not self.regions[r]["target"]]
        for r in self.order:
            if not self.regions[r]["target"] and self.regions[r] not in decoys:
                decoys.append(self.regions[r])
        return self.shapes, targets + decoys


def scatter(rng, n, r, avoid=(), box=(40, 40, W - 40, H - 40), tries=4000):
    pts = []
    for _ in range(tries):
        if len(pts) >= n:
            break
        x, y = rng.uniform(box[0] + r, box[2] - r), rng.uniform(box[1] + r, box[3] - r)
        if all(math.hypot(x - px, y - py) > r + pr + 6 for px, py, pr in pts + list(avoid)):
            pts.append((x, y, r))
    return pts


def debris(sc: Scene, rng, n, color="#BDBDBD"):
    for _ in range(n):
        x, y = rng.uniform(20, W - 20), rng.uniform(20, H - 20)
        sc.e(x, y, rng.uniform(2, 5), rng.uniform(2, 4), rng.uniform(0, 180), fill=color)


# ============================================================================ SMP


def plant_cells(seed):
    rng = random.Random(seed)
    sc = Scene("#F7F3E8")
    cols, rows = 3, 2
    cw, ch = 300, 300
    ox, oy = 50, 50
    for r in range(rows):
        for c in range(cols):
            x1, y1 = ox + c * cw + rng.uniform(-4, 4), oy + r * ch + rng.uniform(-4, 4)
            x2, y2 = x1 + cw - 8, y1 + ch - 8
            wall = sc.poly(rect_pts(x1, y1, x2, y2, rng, 4), fill="#C5E1A5", stroke="#689F38", w=5)
            memb = rect_pts(x1 + 12, y1 + 12, x2 - 12, y2 - 12)
            sc.poly(memb, fill="#F1F8E9", stroke="#8BC34A", w=1.5)
            vac = blob_pts((x1 + x2) / 2 + rng.uniform(-10, 10), (y1 + y2) / 2 + rng.uniform(-10, 10), 82, rng, 0.12, 16, 1.15, 1.05)
            sc.curve(vac, fill="#E1F5FE", stroke="#81D4FA", w=1.5)
            # nukleus di tepi
            side = rng.choice(["l", "r", "t", "b"])
            nx = {"l": x1 + 48, "r": x2 - 48}.get(side, (x1 + x2) / 2 + rng.uniform(-40, 40))
            ny = {"t": y1 + 46, "b": y2 - 46}.get(side, (y1 + y2) / 2 + rng.uniform(-40, 40))
            nuc = sc.e(nx, ny, 26, 20, rng.uniform(0, 180), fill="#B39DDB", stroke="#5E35B1", w=2)
            sc.e(nx + 4, ny - 2, 7, 6, fill="#5E35B1")
            sc.region("nukleus", "Nukleus (inti sel)", nuc)
            sc.region("vakuola", "Vakuola sentral", vac)
            # kloroplas di sitoplasma tepi
            k = 0
            for t in [0.08, 0.22, 0.38, 0.55, 0.68, 0.85]:
                per = 2 * ((x2 - x1 - 60) + (y2 - y1 - 60))
                d = t * per + rng.uniform(-10, 10)
                a, b = (x2 - x1 - 60), (y2 - y1 - 60)
                if d < a:
                    px, py, rot = x1 + 30 + d, y1 + 30, 0
                elif d < a + b:
                    px, py, rot = x2 - 30, y1 + 30 + d - a, 90
                elif d < 2 * a + b:
                    px, py, rot = x2 - 30 - (d - a - b), y2 - 30, 0
                else:
                    px, py, rot = x1 + 30, y2 - 30 - (d - 2 * a - b), 90
                if math.hypot(px - nx, py - ny) < 50:
                    continue
                ch_ = sc.e(px, py, 15, 8, rot + rng.uniform(-20, 20), fill="#43A047", stroke="#1B5E20", w=1.5)
                sc.line([(px - 7, py), (px + 7, py)], "#2E7D32", 1)
                sc.region("kloroplas", "Kloroplas", ch_, target=True)
                k += 1
            sc.region("sitoplasma", "Sitoplasma", memb)
            sc.region("dinding", "Dinding sel", wall)
    return sc, ["nukleus", "vakuola", "sitoplasma", "dinding"]


def cheek_cells(seed):
    rng = random.Random(seed)
    sc = Scene("#FAF7F2")
    centers = [(230, 220), (520, 170), (800, 260), (330, 500), (660, 500)]
    for (cx, cy) in centers:
        cx += rng.uniform(-20, 20)
        cy += rng.uniform(-20, 20)
        cell = sc.curve(blob_pts(cx, cy, 135, rng, 0.22, 11), fill="#F3E5F5", stroke="#BA68C8", w=2)
        for _ in range(5):
            sc.e(cx + rng.uniform(-80, 80), cy + rng.uniform(-80, 80), rng.uniform(3, 6), 2, rng.uniform(0, 180), fill="#E1BEE7")
        nx, ny = cx + rng.uniform(-25, 25), cy + rng.uniform(-20, 20)
        nuc = sc.e(nx, ny, 22, 18, rng.uniform(0, 180), fill="#7B1FA2", stroke="#4A148C", w=1.5)
        sc.region("nukleus", "Nukleus (inti sel)", nuc, target=True)
        sc.region("sitoplasma", "Sitoplasma sel epitel", cell)
    for _ in range(40):  # bakteri mulut kecil
        x, y = rng.uniform(30, 970), rng.uniform(30, 670)
        sc.e(x, y, 4, 2, rng.uniform(0, 180), fill="#9575CD")
    return sc, ["sitoplasma"]


def blood_smear(seed):
    rng = random.Random(seed)
    sc = Scene("#FFF8F6")
    wbc = scatter(rng, 3, 42, box=(120, 100, 880, 600))
    plate = scatter(rng, 7, 8, avoid=wbc)
    rbc = scatter(rng, 48, 30, avoid=wbc + plate)
    for x, y, r in rbc:
        rr = r * rng.uniform(0.9, 1.05)
        e = sc.e(x, y, rr, rr * rng.uniform(0.88, 1.0), rng.uniform(0, 180), fill="#EF9A9A", stroke="#E57373", w=1.5)
        sc.e(x, y, rr * 0.42, rr * 0.38, fill="#FFCDD2")
        sc.region("eritrosit", "Eritrosit (sel darah merah)", e)
    for i, (x, y, r) in enumerate(wbc):
        cell = sc.e(x, y, r, r * 0.95, fill="#E1BEE7", stroke="#AB47BC", w=2)
        if i < 2:  # neutrofil: inti bergelambir
            for dx, dy in [(-14, -6), (2, -14), (14, 4), (-4, 12)][: 3 + i]:
                sc.e(x + dx, y + dy, 11, 8, rng.uniform(0, 180), fill="#6A1B9A", stroke="#4A148C", w=1)
        else:  # limfosit: inti bulat besar
            sc.e(x + 3, y, r * 0.75, r * 0.7, fill="#4A148C")
        sc.region("leukosit", "Leukosit (sel darah putih)", cell, target=True)
    for x, y, r in plate:
        p = sc.curve(blob_pts(x, y, 6, rng, 0.3, 7), fill="#9575CD", stroke="#5E35B1", w=1)
        sc.region("trombosit", "Trombosit (keping darah)", ellipse_pts(x, y, 9, 9))
    return sc, ["trombosit", "eritrosit"]


def muscle_types(seed):
    rng = random.Random(seed)
    sc = Scene("#FFF8FA")
    bands = {"polos": (50, 230), "jantung": (260, 440), "lurik": (470, 650)}
    # otot polos: sel gelendong
    y1, y2 = bands["polos"]
    sc.region("polos", "Otot polos (sel gelendong, tanpa lurik)", rect_pts(40, y1, 960, y2))
    for row in range(4):
        cy = y1 + 25 + row * 45
        x = 60 + rng.uniform(0, 60) - row * 30
        while x < 940:
            L = rng.uniform(150, 210)
            pts = [(x, cy)]
            for i in range(1, 8):
                t = i / 8
                pts.append((x + L * t, cy - 18 * math.sin(math.pi * t)))
            pts.append((x + L, cy))
            for i in range(7, 0, -1):
                t = i / 8
                pts.append((x + L * t, cy + 18 * math.sin(math.pi * t)))
            sc.poly([(min(max(px, 40), 960), py) for px, py in pts], fill="#F8BBD0", stroke="#EC407A", w=1)
            sc.e(x + L / 2, cy, 18, 5, fill="#AD1457")
            x += L * 0.75
    # otot jantung: bercabang, lurik halus, cakram interkalar
    y1, y2 = bands["jantung"]
    sc.region("jantung", "Otot jantung (bercabang, cakram interkalar)", rect_pts(40, y1, 960, y2))
    for row in range(3):
        cy = y1 + 32 + row * 60
        sc.poly(rect_pts(40, cy - 20, 960, cy + 20), fill="#F48FB1", stroke="#D81B60", w=1)
        for x in range(60, 960, 14):
            sc.line([(x, cy - 18), (x, cy + 18)], "#EC407A", 1)
        for x in range(140 + row * 60, 960, 210):
            sc.line([(x, cy - 20), (x + 6, cy - 6), (x, cy + 6), (x + 6, cy + 20)], "#880E4F", 4)
            if x + 92 < 960:
                sc.e(x + 80, cy, 12, 7, fill="#880E4F")
        if row < 2:
            for bx in range(250 + row * 120, 900, 330):
                sc.poly([(bx, cy + 18), (bx + 50, cy + 18), (bx + 100, cy + 42), (bx + 60, cy + 42)], fill="#F48FB1", stroke="#D81B60", w=1)
    # otot lurik (rangka): serabut panjang, lurik jelas, inti di tepi
    y1, y2 = bands["lurik"]
    band = rect_pts(40, y1, 960, y2)
    sc.region("lurik", "Otot lurik (rangka)", band, target=True)
    for row in range(3):
        cy = y1 + 32 + row * 58
        sc.poly(rect_pts(40, cy - 24, 960, cy + 24), fill="#F06292", stroke="#C2185B", w=1.5)
        for x in range(46, 960, 9):
            sc.line([(x, cy - 23), (x, cy + 23)], "#AD1457" if (x // 9) % 2 else "#F8BBD0", 3)
        for x in range(90 + row * 40, 960, 140):
            sc.e(x, cy - 21, 14, 4, fill="#4A148C")
    return sc, ["polos", "jantung"]


def leaf_section(seed):
    rng = random.Random(seed)
    sc = Scene("#FBFBF5")
    sc.line([(30, 108), (970, 108)], "#FBC02D", 4)
    for x in range(30, 970, 46):  # epidermis atas
        p = sc.poly(rect_pts(x, 112, x + 44, 152, rng, 1.5), fill="#E8F5E9", stroke="#66BB6A", w=1.5)
        sc.region("epi_atas", "Epidermis atas", p)
    for x in range(32, 965, 34):  # palisade
        p = sc.poly(rounded_rect(x, 158, x + 30, 302, 10), fill="#A5D6A7", stroke="#2E7D32", w=1.5)
        for k in range(5):
            sc.e(x + 15 + rng.uniform(-6, 6), 175 + k * 26, 5, 4, fill="#2E7D32")
        sc.region("palisade", "Jaringan palisade (mesofil pagar)", p, target=True)
    vb = (500, 405)
    sp = scatter(rng, 28, 30, avoid=[(vb[0], vb[1], 80)], box=(30, 308, 970, 500))
    for x, y, r in sp:  # bunga karang dengan ruang antarsel
        p = sc.curve(blob_pts(x, y, r * 0.95, rng, 0.25, 9), fill="#C8E6C9", stroke="#43A047", w=1.5)
        for _ in range(2):
            sc.e(x + rng.uniform(-10, 10), y + rng.uniform(-10, 10), 4, 3, fill="#388E3C")
        sc.region("spons", "Mesofil bunga karang (spons)", p)
    sheath = sc.e(vb[0], vb[1], 78, 70, fill="#F1F8E9", stroke="#7CB342", w=3)
    for i, (dx, dy) in enumerate([(-30, -30), (0, -38), (30, -30), (-16, -8), (16, -8)]):
        sc.e(vb[0] + dx, vb[1] + dy, 13, 13, fill="#E3F2FD", stroke="#1565C0", w=4)
    for dx in range(-40, 45, 14):
        for dy in (24, 38):
            sc.e(vb[0] + dx, vb[1] + dy, 6, 5, fill="#FCE4EC", stroke="#AD1457", w=1)
    sc.region("berkas", "Berkas pengangkut (xilem & floem)", ellipse_pts(*vb, 78, 70))
    for x in range(30, 970, 46):  # epidermis bawah
        if any(abs(x - s) < 50 for s in (300, 740)):
            continue
        p = sc.poly(rect_pts(x, 505, x + 44, 540, rng, 1.5), fill="#E8F5E9", stroke="#66BB6A", w=1.5)
        sc.region("epi_bawah", "Epidermis bawah", p)
    for sx in (300, 740):
        g1 = sc.e(sx - 14, 522, 13, 20, fill="#81C784", stroke="#1B5E20", w=2)
        g2 = sc.e(sx + 14, 522, 13, 20, fill="#81C784", stroke="#1B5E20", w=2)
        sc.region("stomata", "Stomata (sel penjaga)", ellipse_pts(sx, 522, 30, 22))
    sc.line([(30, 544), (970, 544)], "#FBC02D", 3)
    return sc, ["stomata", "berkas", "epi_atas", "epi_bawah", "spons"]


def dicot_stem(seed):
    rng = random.Random(seed)
    sc = Scene("#FAFAF5")
    cx, cy = 500, 350
    sc.e(cx, cy, 330, 330, fill="#F1F8E9", stroke="#558B2F", w=6)
    sc.region("epidermis", "Epidermis & korteks", ellipse_pts(cx, cy, 330, 330, n=48))
    pith = sc.e(cx, cy, 140, 140, fill="#FFFDE7", stroke="#D7CCC8", w=1)
    for x, y, r in scatter(random.Random(seed + 1), 14, 22, box=(cx - 110, cy - 110, cx + 110, cy + 110)):
        if math.hypot(x - cx, y - cy) < 115:
            sc.e(x, y, r, r * 0.9, fill="#FFF8E1", stroke="#D7CCC8", w=1)
    sc.region("empulur", "Empulur", ellipse_pts(cx, cy, 140, 140))
    nb = 8
    for i in range(nb):
        a = 2 * math.pi * i / nb + 0.2
        def at(r, da=0.0):
            return (cx + r * math.cos(a + da), cy + r * math.sin(a + da))
        xyl = [at(150, -0.16), at(150, 0.16), at(203, 0.2), at(203, -0.2)]
        sc.poly(xyl, fill="#E3F2FD", stroke="#90CAF9", w=1)
        for rr in (165, 185):
            for da in (-0.09, 0.0, 0.09):
                x, y = at(rr, da)
                sc.e(x, y, 7, 7, fill="#BBDEFB", stroke="#1565C0", w=3)
        sc.region("xilem", "Xilem", xyl)
        phl = [at(216, -0.22), at(216, 0.22), at(255, 0.17), at(255, -0.17)]
        sc.poly(phl, fill="#FCE4EC", stroke="#F48FB1", w=1)
        for rr in (226, 242):
            for da in (-0.12, -0.04, 0.04, 0.12):
                x, y = at(rr, da)
                sc.e(x, y, 4, 4, fill="#F8BBD0", stroke="#AD1457", w=1)
        sc.region("floem", "Floem", phl)
    seg = 64
    for i in range(seg):  # cincin kambium (fasikuler + interfasikuler)
        a0, a1 = 2 * math.pi * i / seg, 2 * math.pi * (i + 1) / seg
        q = [(cx + 205 * math.cos(a0), cy + 205 * math.sin(a0)), (cx + 205 * math.cos(a1), cy + 205 * math.sin(a1)),
             (cx + 214 * math.cos(a1), cy + 214 * math.sin(a1)), (cx + 214 * math.cos(a0), cy + 214 * math.sin(a0))]
        sc.poly(q, fill="#FFF176", stroke="#F9A825", w=0.8)
        sc.region("kambium", "Kambium", q, target=True)
    return sc, ["xilem", "floem", "empulur", "epidermis"]


def root_tip(seed):
    rng = random.Random(seed)
    sc = Scene("#FBF8F2")
    cx = 500
    def half_width(y):
        if y < 560:
            return 120
        return max(10.0, 120 * math.sqrt(max(0.0, 1 - ((y - 560) / 100) ** 2)))
    body = [(cx - half_width(y), y) for y in range(40, 661, 20)] + [(cx + half_width(y), y) for y in range(660, 39, -20)]
    sc.curve(body, fill="#FFF3E0", stroke="#8D6E63", w=3)
    sc.region("diferensiasi", "Daerah diferensiasi (pematangan)", rect_pts(cx - 120, 40, cx + 120, 250))
    for y in range(60, 245, 22):  # sel matang + rambut akar
        for k in range(-2, 2):
            sc.poly(rect_pts(cx + k * 58 + 2, y, cx + (k + 1) * 58 - 2, y + 20), fill="#FFECB3", stroke="#BCAAA4", w=1)
    for i, y in enumerate(range(70, 240, 24)):
        for side in (-1, 1):
            L = rng.uniform(150, 260)
            x0 = cx + side * 120
            hair = [(x0, y), (x0 + side * L * 0.5, y + rng.uniform(-14, 14)), (x0 + side * L, y + rng.uniform(-20, 20))]
            sc.oline(hair, "#A1887F", 3)
            sc.region("rambut", "Rambut akar", band_around(hair[0], hair[2], 9))
    sc.region("pemanjangan", "Daerah pemanjangan", rect_pts(cx - 120, 250, cx + 120, 430))
    for y in range(255, 425, 42):
        for k in range(-2, 2):
            sc.poly(rect_pts(cx + k * 58 + 2, y, cx + (k + 1) * 58 - 2, y + 38), fill="#FFF8E1", stroke="#BCAAA4", w=1)
    sc.region("meristem", "Meristem apikal (daerah pembelahan)", rect_pts(cx - 120, 430, cx + 120, 555))
    for y in range(432, 552, 20):
        for k in range(-5, 5):
            x1 = cx + k * 23 + 1
            sc.poly(rect_pts(x1, y, x1 + 21, y + 18), fill="#F8BBD0", stroke="#AD1457", w=0.8)
            sc.e(x1 + 10.5, y + 9, 6, 5, fill="#880E4F")
    cap = [(cx - half_width(y) + 4, y) for y in range(560, 657, 8)] + [(cx + half_width(y) - 4, y) for y in range(656, 559, -8)]
    cap = [(cx - 112, 545)] + cap + [(cx + 112, 545)]
    sc.curve(cap, fill="#FFE0B2", stroke="#E65100", w=2.5)
    for x, y, r in scatter(rng, 14, 14, box=(cx - 95, 562, cx + 95, 640)):
        sc.e(x, y, r, r * 0.8, rng.uniform(0, 180), fill="#FFCC80", stroke="#EF6C00", w=1)
    sc.region("kaliptra", "Tudung akar (kaliptra)", cap, target=True)
    return sc, ["rambut", "meristem", "pemanjangan", "diferensiasi"]


def skin_section(seed):
    rng = random.Random(seed)
    sc = Scene("#FFFAF8")
    top = [(x, 70 + 10 * math.sin(x / 60)) for x in range(0, 1001, 25)]
    epi = top + [(x, 150 + 18 * math.sin(x / 45)) for x in range(1000, -1, -25)]
    sc.curve(epi, fill="#F8BBD0", stroke="#C2185B", w=2)
    sc.oline(top, "#8D6E63", 4)
    derm = [(x, 150 + 18 * math.sin(x / 45)) for x in range(0, 1001, 25)] + [(1000, 520), (0, 520)]
    sc.poly(derm, fill="#FCE4EC", stroke="#F48FB1", w=1)
    for _ in range(26):
        x, y = rng.uniform(30, 970), rng.uniform(200, 500)
        sc.oline([(x, y), (x + 25, y + rng.uniform(-10, 10)), (x + 50, y + rng.uniform(-8, 8))], "#F48FB1", 2)
    sc.poly([(0, 520), (1000, 520), (1000, 700), (0, 700)], fill="#FFFDE7")
    for x, y, r in scatter(rng, 26, 30, box=(0, 525, 1000, 700)):
        sc.e(x, y, r, r * 0.85, fill="#FFF9C4", stroke="#FBC02D", w=1.5)
        sc.region("lemak", "Jaringan lemak (hipodermis)", ellipse_pts(x, y, r, r * 0.85))
    # folikel rambut + kelenjar minyak
    fx = 300
    fol = [(fx - 22, 120), (fx + 22, 120), (fx + 30, 470), (fx - 30, 470)]
    sc.poly(fol, fill="#D7CCC8", stroke="#6D4C41", w=2)
    sc.e(fx, 478, 34, 22, fill="#BCAAA4", stroke="#6D4C41", w=2)
    sc.line([(fx, 30), (fx, 470)], "#3E2723", 6)
    seb = sc.curve(blob_pts(fx + 62, 260, 34, rng, 0.2, 10), fill="#FFE0B2", stroke="#EF6C00", w=2)
    sc.e(fx + 66, 255, 8, 8, fill="#FFCC80")
    sc.line([(fx + 30, 330), (fx + 120, 220)], "#D81B60", 5)
    sc.region("sebasea", "Kelenjar minyak (sebasea)", seb)
    sc.region("folikel", "Folikel rambut", fol + [(fx - 30, 500)])
    # kelenjar keringat bergelung + saluran
    gx, gy = 690, 455
    for k in range(9):
        a = k * 0.8
        sc.e(gx + 30 * math.cos(a) + rng.uniform(-4, 4), gy + 22 * math.sin(a) + rng.uniform(-4, 4), 15, 12, rng.uniform(0, 180),
             fill="#E1BEE7", stroke="#8E24AA", w=2.5)
    duct = [(gx, gy - 30), (gx - 18, 380), (gx + 14, 300), (gx - 10, 220), (gx + 8, 150), (gx, 72)]
    sc.oline(duct, "#8E24AA", 5)
    sc.region("keringat", "Kelenjar keringat", ellipse_pts(gx, gy, 52, 42), target=True)
    sc.region("saluran", "Saluran kelenjar keringat", band_around((gx, 150), (gx, gy - 40), 14))
    for x, y in [(150, 380), (480, 320), (860, 260), (560, 470)]:
        sc.e(x, y, 14, 11, fill="#E53935", stroke="#B71C1C", w=2)
        sc.region("pembuluh", "Pembuluh darah", ellipse_pts(x, y, 16, 13))
    sc.region("epidermis", "Epidermis", epi)
    sc.region("dermis", "Dermis", derm)
    return sc, ["saluran", "sebasea", "folikel", "pembuluh", "lemak", "epidermis", "dermis"]


def kidney_cortex(seed):
    rng = random.Random(seed)
    sc = Scene("#FFF7FA")
    corp = [(250, 230, 72), (720, 200, 68), (520, 520, 70)]
    tub = scatter(rng, 18, 40, avoid=[(x, y, r + 12) for x, y, r in corp])
    for x, y, r in tub:
        o = sc.curve(blob_pts(x, y, r, rng, 0.1, 12), fill="#F48FB1", stroke="#C2185B", w=2)
        for k in range(8):
            a = 2 * math.pi * k / 8
            sc.e(x + r * 0.62 * math.cos(a), y + r * 0.62 * math.sin(a), 4, 4, fill="#880E4F")
        sc.e(x, y, r * 0.35, r * 0.3, rng.uniform(0, 180), fill="#FFF3F6", stroke="#F8BBD0", w=1)
        sc.region("tubulus", "Tubulus ginjal", o)
    for x, y, r in corp:
        cap = sc.e(x, y, r, r * 0.95, fill="#FFF3F6", stroke="#AD1457", w=4)
        tuft = blob_pts(x + 4, y + 2, r * 0.68, rng, 0.12, 12)
        sc.curve(tuft, fill="#EF9A9A", stroke="#C62828", w=1.5)
        for _ in range(11):
            a, d = rng.uniform(0, 2 * math.pi), rng.uniform(0, r * 0.5)
            sc.e(x + 4 + d * math.cos(a), y + 2 + d * math.sin(a), 10, 8, rng.uniform(0, 180), stroke="#C62828", w=1.5)
        sc.region("glomerulus", "Glomerulus", tuft, target=True)
        sc.region("bowman", "Kapsul & ruang Bowman", cap)
    return sc, ["bowman", "tubulus"]


def ovary_section(seed):
    rng = random.Random(seed)
    sc = Scene("#FBFBF3")
    cx, cy = 500, 350
    wall = sc.curve(blob_pts(cx, cy, 300, rng, 0.04, 18), fill="#C5E1A5", stroke="#558B2F", w=4)
    sc.e(cx, cy, 40, 40, fill="#AED581", stroke="#689F38", w=2)
    for i in range(3):
        a = 2 * math.pi * i / 3 - math.pi / 2
        lx, ly = cx + 150 * math.cos(a), cy + 150 * math.sin(a)
        loc = blob_pts(lx, ly, 105, rng, 0.06, 16, 1.0, 0.85)
        loc = rotate(loc, lx, ly, math.degrees(a) + 90)
        sc.curve(loc, fill="#F9FBE7", stroke="#7CB342", w=2)
        sc.region("lokulus", "Ruang bakal buah (lokulus)", loc)
        for side in (-1, 1):
            ox = lx + side * 34 * math.cos(a + math.pi / 2) - 30 * math.cos(a)
            oy = ly + side * 34 * math.sin(a + math.pi / 2) - 30 * math.sin(a)
            sc.line([(cx + 40 * math.cos(a + side * 0.4), cy + 40 * math.sin(a + side * 0.4)), (ox, oy)], "#9E9D24", 3)
            ov = sc.e(ox, oy, 30, 22, math.degrees(a), fill="#FFF59D", stroke="#F9A825", w=2)
            sc.e(ox, oy, 12, 9, math.degrees(a), fill="#FFB74D")
            sc.region("ovulum", "Bakal biji (ovulum)", ov, target=True)
    sc.region("dinding", "Dinding bakal buah (ovarium)", wall)
    return sc, ["lokulus", "dinding"]


# ============================================================================ SMA


def chromosome_rod(sc, x, y, rot, L=16, color="#4A148C"):
    sc.e(x, y, L, 3.5, rot, fill=color)


def mitosis_field(seed, cols, rows, phases: dict[tuple[int, int], str], target_phase: str, plate_target=False):
    rng = random.Random(seed)
    sc = Scene("#FFF5F8")
    cw, ch = (W - 40) / cols, (H - 40) / rows
    names = {"interfase": "Sel interfase", "profase": "Sel profase", "metafase": "Sel metafase",
             "anafase": "Sel anafase", "telofase": "Sel telofase"}
    for r in range(rows):
        for c in range(cols):
            # grid lurus; jitter kecil di dalam celah 6 px agar sel tidak pernah bertumpuk
            x1, y1 = 20 + c * cw + 3, 20 + r * ch + 3
            x2, y2 = x1 + cw - 6, y1 + ch - 6
            cell = sc.poly(rect_pts(x1, y1, x2, y2, rng, 2.5), fill="#FCE4EC", stroke="#AD1457", w=2)
            mx, my = (x1 + x2) / 2, (y1 + y2) / 2
            ph = phases.get((r, c), "interfase")
            vertical = (x2 - x1) < (y2 - y1)
            if ph == "interfase":
                sc.e(mx + rng.uniform(-8, 8), my + rng.uniform(-8, 8), 30, 27, fill="#F48FB1", stroke="#880E4F", w=2)
                sc.e(mx + 6, my - 4, 7, 6, fill="#880E4F")
            elif ph == "profase":
                sc.e(mx, my, 33, 30, fill="#F8BBD0", stroke="#AD1457", w=1)
                for _ in range(5):
                    sc.oline([(mx + rng.uniform(-24, 24), my + rng.uniform(-22, 22)) for _ in range(4)], "#4A148C", 3.5)
            elif ph == "metafase":
                for k in range(-3, 4):
                    chromosome_rod(sc, mx + k * 7, my + rng.uniform(-3, 3), 90 + rng.uniform(-15, 15))
                for s in (-1, 1):
                    for k in range(-3, 4, 2):
                        sc.line([(mx, my + s * 48), (mx + k * 8, my)], "#CE93D8", 1)
            elif ph == "anafase":
                for s in (-1, 1):
                    for k in range(-3, 4):
                        x0, y0 = mx + k * 7, my + s * 30
                        sc.line([(x0 - 4, y0 + s * 9), (x0, y0 - s * 4), (x0 + 4, y0 + s * 9)], "#4A148C", 3.5)
                for k in range(-2, 3):
                    sc.line([(mx + k * 9, my - 22), (mx + k * 9, my + 22)], "#CE93D8", 1)
            elif ph == "telofase":
                n1 = sc.e(mx, my - 40, 26, 18, fill="#F48FB1", stroke="#880E4F", w=2)
                n2 = sc.e(mx, my + 40, 26, 18, fill="#F48FB1", stroke="#880E4F", w=2)
                plate = rect_pts(x1 + 10, my - 5, x2 - 10, my + 5)
                sc.poly(plate, fill="#E1BEE7", stroke="#7B1FA2", w=1)
                for x in range(int(x1 + 16), int(x2 - 10), 10):
                    sc.e(x, my, 3, 3, fill="#6A1B9A")
                if plate_target:
                    sc.region("lempeng", "Lempeng sel (fragmoplas)", plate, target=True)
                    sc.region("inti_anak", "Inti sel anak", n1)
                    sc.region("inti_anak", "Inti sel anak", n2)
            rid = ph
            sc.region(rid, names[ph], cell, target=(ph == target_phase and not plate_target))
    order = ["inti_anak", "telofase", "anafase", "metafase", "profase", "interfase"]
    return sc, [o for o in order]


def artery_vein(seed):
    rng = random.Random(seed)
    sc = Scene("#FFF9FB")
    ax, ay = 320, 350
    art = sc.e(ax, ay, 165, 160, fill="#F8BBD0", stroke="#D81B60", w=2)
    sc.e(ax, ay, 140, 136, fill="#EC407A", stroke="#C2185B", w=1)
    for rr in range(70, 140, 12):
        sc.oline([(ax + rr * math.cos(t / 10), ay + rr * math.sin(t / 10) + 3 * math.sin(t)) for t in range(0, 64, 3)], "#F8BBD0", 1.5)
    sc.e(ax, ay, 62, 60, fill="#FFFFFF", stroke="#FFF59D", w=4)
    for _ in range(4):
        sc.e(ax + rng.uniform(-30, 30), ay + rng.uniform(-30, 30), 9, 8, fill="#EF5350")
    sc.region("arteri", "Arteri (dinding tebal, lumen kecil bulat)", ellipse_pts(ax, ay, 165, 160, n=32), target=True)
    vx, vy = 730, 380
    outer = blob_pts(vx, vy, 170, rng, 0.08, 18, 1.0, 0.62)
    sc.curve(outer, fill="#F48FB1", stroke="#C2185B", w=2)
    inner = inset(outer, 16)
    sc.curve(inner, fill="#FFFFFF", stroke="#F8BBD0", w=1)
    for _ in range(14):
        sc.e(vx + rng.uniform(-110, 110), vy + rng.uniform(-50, 50), 10, 8, rng.uniform(0, 180), fill="#E53935")
    sc.region("vena", "Vena (dinding tipis, lumen lebar)", outer)
    for x, y in [(560, 140), (880, 120), (620, 610), (150, 620)]:
        c = sc.e(x, y, 16, 15, fill="#FCE4EC", stroke="#C2185B", w=2)
        sc.e(x, y, 6, 6, fill="#E53935")
        sc.region("kapiler", "Kapiler", c)
    return sc, ["kapiler", "vena"]


def sperm_cells(seed):
    rng = random.Random(seed)
    sc = Scene("#F7FAFC")
    sp = [(220, 200, 20), (560, 150, 160), (760, 420, 95), (330, 520, -35)]
    for hx, hy, ang in sp:
        a = math.radians(ang)
        fx, fy = math.cos(a), math.sin(a)  # arah depan
        tail0 = (hx - fx * 44, hy - fy * 44)
        mid = band_around((hx - fx * 28, hy - fy * 28), (hx - fx * 70, hy - fy * 70), 6)
        tail = [(hx - fx * (70 + 40 * k) + (-fy) * 18 * math.sin(k * 1.4), hy - fy * (70 + 40 * k) + fx * 18 * math.sin(k * 1.4)) for k in range(7)]
        sc.oline(tail, "#78909C", 3)
        for k in range(len(tail) - 1):
            sc.region("ekor", "Ekor (flagel)", band_around(tail[k], tail[k + 1], 9))
        sc.poly(mid, fill="#FFCC80", stroke="#EF6C00", w=1.5)
        for k in range(5):
            p = (hx - fx * (32 + k * 8), hy - fy * (32 + k * 8))
            sc.line([(p[0] - fy * 6, p[1] + fx * 6), (p[0] + fy * 6, p[1] - fx * 6)], "#E65100", 1.5)
        sc.region("tengah", "Bagian tengah (berisi mitokondria)", mid)
        head = sc.e(hx, hy, 34, 22, ang, fill="#E1BEE7", stroke="#7B1FA2", w=2)
        sc.e(hx - fx * 4, hy - fy * 4, 22, 14, ang, fill="#9575CD")
        cap = []
        for t in range(-70, 71, 14):
            # busur luar tudung (sedikit di luar kepala), dalam koordinat lokal (maju = fx,fy)
            u, v = 36 * math.cos(math.radians(t)), 24 * math.sin(math.radians(t))
            cap.append((hx + u * fx - v * fy, hy + u * fy + v * fx))
        for t in range(70, -71, -14):
            cap.append((hx + 4 * fx + 22 * math.cos(math.radians(t)) * fx - 17 * math.sin(math.radians(t)) * fy,
                        hy + 4 * fy + 22 * math.cos(math.radians(t)) * fy + 17 * math.sin(math.radians(t)) * fx))
        sc.poly(cap, fill="#80DEEA", stroke="#00838F", w=1.5)
        sc.region("akrosom", "Akrosom", cap, target=True)
        sc.region("kepala", "Kepala & inti sperma", head)
    debris(sc, rng, 25, "#CFD8DC")
    return sc, ["tengah", "kepala", "ekor"]


def compact_bone(seed):
    rng = random.Random(seed)
    sc = Scene("#E8E0DA")
    ost = [(230, 220), (520, 180), (800, 250), (350, 500), (680, 520)]
    for i, (x, y) in enumerate(ost):
        x += rng.uniform(-15, 15)
        y += rng.uniform(-15, 15)
        R_ = 125 + rng.uniform(-10, 10)
        outer = sc.e(x, y, R_, R_ * 0.92, fill="#EFEBE9", stroke="#8D6E63", w=2)
        for rr in range(30, int(R_), 16):
            sc.e(x, y, rr, rr * 0.92, stroke="#BCAAA4", w=1.5)
        for rr in range(38, int(R_) - 8, 32):
            for k in range(9):
                a = 2 * math.pi * k / 9 + rr
                lx, ly = x + rr * math.cos(a), y + rr * 0.92 * math.sin(a)
                lac = sc.e(lx, ly, 6, 3.5, math.degrees(a) + 90, fill="#4E342E")
                sc.line([(lx, ly), (lx + 10 * math.cos(a), ly + 10 * math.sin(a))], "#795548", 0.8)
                sc.region("lakuna", "Lakuna (berisi osteosit)", ellipse_pts(lx, ly, 8, 6, math.degrees(a) + 90))
        canal = sc.e(x, y, 16, 15, fill="#5D4037", stroke="#3E2723", w=2)
        sc.e(x - 3, y - 2, 4, 4, fill="#E57373")
        sc.region("havers", "Kanal Havers (saluran pusat osteon)", canal, target=True)
        sc.region("lamela", "Lamela konsentris", ellipse_pts(x, y, R_, R_ * 0.92, n=32))
    sc.line([(230, 220), (350, 500)], "#A1887F", 6)
    return sc, ["lakuna", "lamela"]


def bacterium(seed):
    rng = random.Random(seed)
    sc = Scene("#F5F9F5")
    cap = rounded_rect(110, 200, 890, 500, 150)
    sc.poly(cap, fill="#E0F2F1", stroke="#80CBC4", w=8)
    wall = rounded_rect(126, 216, 874, 484, 134)
    sc.poly(wall, fill="#E8F5E9", stroke="#2E7D32", w=4)
    sc.poly(rounded_rect(138, 228, 862, 472, 122), fill="#F1F8E9", stroke="#66BB6A", w=1.5)
    nuc = blob_pts(480, 350, 105, rng, 0.18, 12, 1.4, 0.8)
    sc.curve(nuc, fill="#EDE7F6")
    for _ in range(4):
        sc.oline([(480 + rng.uniform(-120, 120), 350 + rng.uniform(-60, 60)) for _ in range(6)], "#5E35B1", 2.5)
    for x, y, r in scatter(rng, 70, 3, avoid=[(480, 350, 150), (240, 290, 25), (760, 300, 25), (720, 420, 25)], box=(170, 240, 830, 460)):
        sc.e(x, y, 3.5, 3.5, fill="#5D4037")
        sc.region("ribosom", "Ribosom", ellipse_pts(x, y, 6, 6, n=8))
    for x, y in [(240, 290), (760, 300), (720, 420)]:
        sc.e(x, y, 15, 13, rng.uniform(0, 180), stroke="#E53935", w=3.5)
        sc.region("plasmid", "Plasmid", ellipse_pts(x, y, 19, 17, n=16), target=True)
    sc.region("nukleoid", "Nukleoid (DNA kromosom)", nuc)
    fl = [(890, 350), (930, 320), (960, 360), (990, 330)]
    sc.oline(fl, "#455A64", 3)
    sc.oline([(110, 350), (60, 380), (25, 340)], "#455A64", 3)
    for k in range(len(fl) - 1):
        sc.region("flagel", "Flagel", band_around(fl[k], fl[k + 1], 10))
    for x in range(250, 800, 90):
        sc.line([(x, 200), (x + 6, 180)], "#80CBC4", 2)
    sc.region("dinding", "Dinding sel & kapsul", cap)
    return sc, ["ribosom", "nukleoid", "flagel", "dinding"]


def meiosis_field(seed):
    rng = random.Random(seed)
    sc = Scene("#FFF8F2")
    cells = scatter(rng, 9, 78, box=(30, 30, 970, 670))
    kinds = ["metafase1", "metafase2", "anafase1", "profase1", "tetrad", "interfase", "interfase", "metafase2", "interfase"]
    names = {"metafase1": "Metafase I (bivalen di ekuator)", "metafase2": "Metafase II / tanpa pasangan homolog",
             "anafase1": "Anafase I", "profase1": "Profase I", "tetrad": "Telofase II (empat sel)", "interfase": "Interfase"}
    def xchrom(x, y, rot, s=9, col="#4A148C"):
        sc.e(x, y, s, 2.5, rot + 35, fill=col)
        sc.e(x, y, s, 2.5, rot - 35, fill=col)
    for (x, y, r), kind in zip(cells, kinds):
        cell = sc.e(x, y, r, r * 0.95, fill="#FFE0B2", stroke="#E65100", w=2)
        if kind == "metafase1":
            for k in range(-3, 4, 2):
                xchrom(x - 9, y + k * 9, 0)
                xchrom(x + 9, y + k * 9, 0, col="#6A1B9A")
            for s in (-1, 1):
                for k in (-2, 0, 2):
                    sc.line([(x + s * 60, y), (x + s * 10, y + k * 12)], "#FFCC80", 1)
        elif kind == "metafase2":
            for k in range(-3, 4, 2):
                xchrom(x, y + k * 9, 0)
            for s in (-1, 1):
                sc.line([(x + s * 55, y), (x, y)], "#FFCC80", 1)
        elif kind == "anafase1":
            for s in (-1, 1):
                for k in range(-3, 4, 2):
                    xchrom(x + s * 38, y + k * 10, 0)
        elif kind == "profase1":
            sc.e(x, y, 45, 42, fill="#FFCCBC", stroke="#BF360C", w=1)
            for _ in range(4):
                sc.oline([(x + rng.uniform(-30, 30), y + rng.uniform(-30, 30)) for _ in range(5)], "#4A148C", 3)
        elif kind == "tetrad":
            for dx, dy in [(-26, -26), (26, -26), (-26, 26), (26, 26)]:
                sc.e(x + dx, y + dy, 16, 14, fill="#F48FB1", stroke="#880E4F", w=1.5)
            sc.line([(x - r, y), (x + r, y)], "#E65100", 1.5)
            sc.line([(x, y - r), (x, y + r)], "#E65100", 1.5)
        else:
            sc.e(x, y, 36, 33, fill="#F48FB1", stroke="#880E4F", w=2)
            sc.e(x + 5, y - 5, 8, 7, fill="#880E4F")
        sc.region(kind, names[kind], cell, target=(kind == "metafase1"))
    return sc, ["metafase2", "anafase1", "profase1", "tetrad", "interfase"]


def chloroplast(seed):
    rng = random.Random(seed)
    sc = Scene("#F7FBF4")
    cx, cy = 500, 350
    outer = sc.e(cx, cy, 440, 250, fill="#C5E1A5", stroke="#2E7D32", w=5)
    sc.e(cx, cy, 428, 238, fill="#DCEDC8", stroke="#558B2F", w=2)
    stacks = [(250, 300), (370, 230), (420, 420), (560, 300), (650, 210), (690, 430), (790, 330)]
    for i in range(len(stacks) - 1):
        (x1, y1), (x2, y2) = stacks[i], stacks[i + 1]
        sc.oline([(x1, y1), ((x1 + x2) / 2, (y1 + y2) / 2 + 15), (x2, y2)], "#81C784", 4)
        sc.region("lamela", "Lamela stroma (tilakoid antar-grana)", band_around((x1, y1), (x2, y2), 8))
    for x, y in stacks:
        n = rng.randint(5, 7)
        top = y - n * 7
        for k in range(n):
            sc.poly(rounded_rect(x - 34, top + k * 14, x + 34, top + k * 14 + 11, 5, 3), fill="#66BB6A", stroke="#1B5E20", w=1.5)
        sc.region("granum", "Granum (tumpukan tilakoid)", rect_pts(x - 36, top - 2, x + 36, top + n * 14 + 1), target=True)
    for x, y in [(330, 470), (600, 470), (470, 180)]:
        st = sc.e(x, y, 34, 20, rng.uniform(-20, 20), fill="#FFF9C4", stroke="#F9A825", w=2)
        sc.region("pati", "Butir pati", st)
    for _ in range(14):
        sc.e(cx + rng.uniform(-360, 360), cy + rng.uniform(-170, 170), 5, 5, fill="#F57F17")
    sc.oline([(860, 300), (875, 330), (860, 360), (875, 390)], "#6A1B9A", 2)
    sc.region("stroma", "Stroma", ellipse_pts(cx, cy, 428, 238, n=36))
    sc.region("membran", "Membran luar & dalam", ellipse_pts(cx, cy, 440, 250, n=36))
    return sc, ["pati", "lamela", "stroma", "membran"]


def myelinated_axon(seed):
    rng = random.Random(seed)
    sc = Scene("#FAF8FF")
    for ay, lengths, start in [(230, [190, 170, 200, 180, 160], 40), (500, [160, 210, 180, 190, 150], 70)]:
        sc.poly(rect_pts(0, ay - 14, 1000, ay + 14), fill="#FFE082", stroke="#FFA000", w=1.5)
        sc.region("akson", "Akson", rect_pts(0, ay - 14, 1000, ay + 14))
        x = start
        fitting = []
        for L in lengths:  # segmen yang muat; nodus hanya DI ANTARA dua segmen
            if x + L > 990:
                break
            fitting.append(L)
            x += L + 14
        x = start
        for i, L in enumerate(fitting):
            seg = rounded_rect(x, ay - 52, x + L, ay + 52, 40)
            sc.poly(seg, fill="#FFF3E0", stroke="#8D6E63", w=2)
            for k in range(1, 4):
                sc.poly(rounded_rect(x + 4 * k, ay - 52 + 7 * k, x + L - 4 * k, ay + 52 - 7 * k, max(5, 40 - 8 * k)), stroke="#D7CCC8", w=1)
            nuc = sc.e(x + L * rng.uniform(0.35, 0.65), ay - 60, 26, 10, fill="#8D6E63", stroke="#4E342E", w=1.5)
            sc.region("schwann_inti", "Inti sel Schwann", ellipse_pts(x + L / 2, ay - 60, 30, 12))
            sc.region("mielin", "Selubung mielin (sel Schwann)", seg)
            x += L
            gap = 14
            if i < len(fitting) - 1:
                node = rect_pts(x - 3, ay - 20, x + gap + 3, ay + 20)
                sc.region("nodus", "Nodus Ranvier", node, target=True)
            x += gap
    return sc, ["schwann_inti", "mielin", "akson"]


# ============================================================================ daftar soal

# (id, jenjang, kesulitan, topik, nama target, instruksi, fungsi-scene(seed), seed, minTapZoom, maxZoom,
#  tapTol, pembahasan, hint)
BIO = [
    ("BIO-SMP-061", "SMP", "mudah", "Sel tumbuhan", "kloroplas", "Fokuskan mikroskop lalu tunjuk SALAH SATU kloroplas pada sel tumbuhan.",
     lambda: plant_cells(611), 1.4, 4.0, 4.0,
     "Kloroplas adalah organel hijau (berklorofil) tempat fotosintesis; terletak di sitoplasma tepi karena vakuola sentral besar mendesaknya.",
     "Cari bulatan kecil berwarna hijau di tepi sel."),
    ("BIO-SMP-062", "SMP", "mudah", "Sel hewan", "nukleus", "Ini preparat sel epitel pipi. Tunjuk nukleus (inti sel) salah satu sel.",
     lambda: cheek_cells(622), 1.0, 3.0, 5.0,
     "Sel epitel pipi tidak memiliki dinding sel; nukleus tampak sebagai bulatan gelap karena menyerap zat warna (metilen biru).",
     "Inti sel menyerap pewarna paling kuat."),
    ("BIO-SMP-063", "SMP", "mudah", "Sel darah", "leukosit", "Preparat apus darah: tunjuk sel darah putih (leukosit).",
     lambda: blood_smear(633), 1.2, 4.0, 4.0,
     "Leukosit lebih besar dari eritrosit dan memiliki inti (bergelambir pada neutrofil). Eritrosit manusia tidak berinti dan tampak pucat di tengah.",
     "Cari sel yang memiliki inti berwarna ungu."),
    ("BIO-SMP-064", "SMP", "mudah", "Jaringan otot", "otot lurik", "Tiga jenis jaringan otot ada di preparat. Tunjuk otot lurik (otot rangka).",
     lambda: muscle_types(644), 1.5, 4.0, 3.0,
     "Otot lurik berupa serabut panjang silindris dengan pita gelap-terang melintang dan banyak inti di tepi. Otot polos berbentuk gelendong tanpa lurik; "
     "otot jantung juga lurik tetapi bercabang dan memiliki cakram interkalar.",
     "Perbesar untuk melihat garis-garis melintang dan inti di tepi serabut."),
    ("BIO-SMP-065", "SMP", "sedang", "Struktur daun", "jaringan palisade", "Penampang melintang daun: tunjuk jaringan palisade (mesofil pagar).",
     lambda: leaf_section(655), 1.3, 4.0, 3.0,
     "Palisade tersusun rapat seperti pagar tepat di bawah epidermis atas dan paling banyak mengandung kloroplas; jaringan spons di bawahnya memiliki ruang antarsel.",
     "Letaknya tepat di bawah epidermis atas."),
    ("BIO-SMP-066", "SMP", "sedang", "Jaringan batang dikotil", "kambium", "Penampang batang dikotil: tunjuk kambium (lapisan di antara xilem dan floem).",
     lambda: dicot_stem(666), 2.0, 5.0, 4.0,
     "Pada batang dikotil berkas pengangkut tersusun melingkar; kambium berada di antara xilem (sebelah dalam) dan floem (sebelah luar) dan membelah untuk pertumbuhan sekunder.",
     "Perbesar daerah lingkaran berkas pengangkut."),
    ("BIO-SMP-067", "SMP", "sedang", "Struktur akar", "tudung akar", "Sayatan memanjang ujung akar: tunjuk tudung akar (kaliptra).",
     lambda: root_tip(677), 1.0, 4.0, 4.0,
     "Kaliptra menutupi dan melindungi meristem apikal saat akar menembus tanah. Daerah pembelahan (meristem) berada tepat di atasnya.",
     "Lihat bagian paling ujung akar."),
    ("BIO-SMP-068", "SMP", "sedang", "Sistem ekskresi: kulit", "kelenjar keringat", "Penampang kulit: tunjuk kelenjar keringat.",
     lambda: skin_section(688), 1.3, 4.0, 4.0,
     "Kelenjar keringat berupa tabung bergelung di dermis bagian dalam, salurannya bermuara ke pori di permukaan kulit; kelenjar minyak menempel pada folikel rambut.",
     "Cari gelungan tabung di bagian dalam dermis."),
    ("BIO-SMP-069", "SMP", "sulit", "Struktur ginjal", "glomerulus", "Preparat korteks ginjal: tunjuk glomerulus.",
     lambda: kidney_cortex(699), 1.6, 4.5, 3.0,
     "Glomerulus adalah gulungan kapiler di dalam kapsul Bowman (badan Malpighi), tempat filtrasi darah. Tubulus tampak sebagai cincin sel dengan lumen di tengah.",
     "Glomerulus dikelilingi ruang kapsul Bowman."),
    ("BIO-SMP-070", "SMP", "sulit", "Reproduksi tumbuhan", "bakal biji", "Penampang melintang bakal buah bunga: tunjuk salah satu bakal biji (ovulum).",
     lambda: ovary_section(700), 1.6, 4.5, 3.0,
     "Bakal biji berada di dalam ruang bakal buah dan melekat pada plasenta; setelah pembuahan bakal biji menjadi biji dan bakal buah menjadi buah.",
     "Cari bulatan di dalam ruang bakal buah."),
    ("BIO-SMA-061", "SMA", "mudah", "Fase mitosis", "sel metafase", "Preparat ujung akar bawang: tunjuk sel yang sedang METAFASE.",
     lambda: mitosis_field(711, 6, 4, {(0, 4): "metafase", (1, 1): "anafase", (2, 3): "profase", (3, 0): "profase", (2, 5): "telofase"}, "metafase"),
     1.3, 4.0, 3.0,
     "Pada metafase kromosom paling terkondensasi dan berjajar di bidang ekuator, terikat benang spindel dari kedua kutub.",
     "Cari kromosom yang berbaris rapi di tengah sel."),
    ("BIO-SMA-062", "SMA", "mudah", "Sistem peredaran darah", "arteri", "Penampang pembuluh darah: tunjuk ARTERI.",
     lambda: artery_vein(722), 1.0, 3.5, 4.0,
     "Arteri berdinding tebal dengan tunika media otot polos & elastis yang tebal sehingga lumennya bulat kecil; vena berdinding tipis dengan lumen lebar yang sering tampak pipih.",
     "Bandingkan ketebalan dinding kedua pembuluh."),
    ("BIO-SMA-063", "SMA", "sedang", "Fase mitosis", "sel anafase", "Preparat ujung akar bawang: tunjuk sel yang sedang ANAFASE.",
     lambda: mitosis_field(733, 5, 4, {(0, 1): "metafase", (1, 3): "anafase", (2, 0): "metafase", (3, 2): "profase", (2, 4): "telofase", (0, 4): "profase"}, "anafase"),
     1.5, 4.0, 3.0,
     "Pada anafase kromatid saudara berpisah dan ditarik ke kutub berlawanan, sehingga tampak dua kelompok kromosom berbentuk V.",
     "Kromosom membentuk dua kelompok yang menjauh."),
    ("BIO-SMA-064", "SMA", "sedang", "Sel khusus: spermatozoa", "akrosom", "Preparat spermatozoa: tunjuk akrosom.",
     lambda: sperm_cells(744), 2.2, 5.0, 3.0,
     "Akrosom adalah tudung di ujung depan kepala sperma berisi enzim (mis. hialuronidase) untuk menembus lapisan pelindung ovum; mitokondria terdapat di bagian tengah.",
     "Perbesar ujung paling depan kepala sperma."),
    ("BIO-SMA-065", "SMA", "sedang", "Jaringan tulang", "kanal Havers", "Sayatan tulang kompak: tunjuk kanal Havers.",
     lambda: compact_bone(755), 1.5, 4.5, 3.0,
     "Setiap osteon (sistem Havers) memiliki saluran pusat berisi pembuluh darah dan saraf, dikelilingi lamela konsentris dengan lakuna berisi osteosit.",
     "Lihat pusat lingkaran-lingkaran konsentris."),
    ("BIO-SMA-066", "SMA", "sedang", "Sel prokariot", "plasmid", "Diagram sel bakteri: tunjuk salah satu plasmid.",
     lambda: bacterium(766), 2.0, 5.0, 3.0,
     "Plasmid adalah DNA sirkuler kecil di luar nukleoid yang dapat bereplikasi sendiri dan sering membawa gen resistensi antibiotik; dipakai sebagai vektor rekayasa genetika.",
     "Cari cincin kecil di sitoplasma, terpisah dari nukleoid."),
    ("BIO-SMA-067", "SMA", "sulit", "Fase meiosis", "sel metafase I", "Preparat kepala sari (meiosis): tunjuk sel yang sedang METAFASE I.",
     lambda: meiosis_field(777), 1.8, 5.0, 3.0,
     "Metafase I ditandai bivalen (pasangan kromosom homolog) berjajar di bidang ekuator, berpasangan di kedua sisi. Pada metafase II kromosom berjajar satu baris tanpa pasangan homolog.",
     "Cari kromosom yang berpasangan di kedua sisi ekuator."),
    ("BIO-SMA-068", "SMA", "sulit", "Sitokinesis sel tumbuhan", "lempeng sel", "Tunjuk lempeng sel (fragmoplas) yang terbentuk pada sel tumbuhan yang sedang telofase.",
     lambda: mitosis_field(788, 5, 4, {(1, 2): "telofase", (0, 0): "anafase", (2, 4): "metafase", (3, 1): "profase", (0, 3): "metafase"}, "telofase", plate_target=True),
     2.0, 5.0, 3.0,
     "Sel tumbuhan tidak membentuk alur pembelahan; vesikel Golgi berkumpul di ekuator membentuk lempeng sel yang kelak menjadi dinding sel baru di antara dua inti anak.",
     "Perbesar sel yang memiliki dua inti anak."),
    ("BIO-SMA-069", "SMA", "sulit", "Struktur kloroplas", "granum", "Diagram ultrastruktur kloroplas: tunjuk salah satu granum.",
     lambda: chloroplast(799), 1.4, 4.0, 3.0,
     "Granum adalah tumpukan tilakoid tempat reaksi terang (fotosistem, rantai transpor elektron); stroma di sekelilingnya tempat siklus Calvin.",
     "Granum tampak seperti tumpukan koin."),
    ("BIO-SMA-070", "SMA", "sulit", "Struktur neuron", "nodus Ranvier", "Akson bermielin: tunjuk salah satu nodus Ranvier.",
     lambda: myelinated_axon(800), 2.5, 6.0, 4.0,
     "Nodus Ranvier adalah celah di antara sel-sel Schwann tempat akson tidak berselubung mielin; impuls melompat antarnodus (konduksi saltatori) sehingga lebih cepat.",
     "Cari celah sempit di antara selubung mielin."),
]
