"""
Cermin Python dari mesin game Kotlin (app/.../domain/game/GameEngines.kt).

Dipakai generator konten (menghitung & membuktikan solusi referensi) dan validator independen.
Sumber kebenaran penilaian di aplikasi tetap mesin Kotlin; unit test Kotlin memeriksa ulang setiap
solusi referensi dengan mesin Kotlin sehingga kedua implementasi saling mengunci.
"""
from __future__ import annotations

import math
import re
from fractions import Fraction

# ------------------------------------------------------------------ Asteroid


def asteroid_progress(spec: dict, hits: list[int]) -> tuple[int, int, bool]:
    tokens = spec["answerTokens"] + spec["distractorTokens"]
    slot = wrong = 0
    for h in hits:
        if slot == len(spec["answerTokens"]) or wrong > spec["maxWrong"]:
            break
        if tokens[h] == spec["answerTokens"][slot]:
            slot += 1
        else:
            wrong += 1
    ok = slot == len(spec["answerTokens"]) and wrong <= spec["maxWrong"]
    return slot, wrong, ok


_SUP = str.maketrans("⁰¹²³⁴⁵⁶⁷⁸⁹", "0123456789")


def expr_value(token: str, x: float) -> float | None:
    """Evaluasi numerik token ekspresi sederhana (angka, pecahan, √, x, pangkat superscript).

    Dipakai untuk mendeteksi pengecoh yang SETARA dengan jawaban walau formatnya berbeda.
    Mengembalikan None bila token bukan ekspresi numerik (mis. teks)."""
    s = token.replace("−", "-").replace("·", "*").replace("×", "*").replace(" ", "").replace(".", "")
    s = s.replace(",", ".")
    s = re.sub(r"√(\d+)", r"(\1**0.5)", s)
    s = re.sub(r"([0-9x)])([⁰¹²³⁴⁵⁶⁷⁸⁹]+)", lambda m: m.group(1) + "**" + m.group(2).translate(_SUP), s)
    s = re.sub(r"(\d)(x|\()", r"\1*\2", s)
    s = re.sub(r"\)(\d|x|\()", r")*\1", s)
    if not re.fullmatch(r"[0-9x+\-*/().]*", s) or not s:
        return None
    try:
        return float(eval(s, {"__builtins__": {}}, {"x": x}))  # noqa: S307 - grammar dibatasi regex di atas
    except Exception:
        return None


def equivalent(a: str, b: str) -> bool:
    if a.strip() == b.strip():
        return True
    pts = [0.7, 1.3, 2.9, -1.7]
    va = [expr_value(a, p) for p in pts]
    vb = [expr_value(b, p) for p in pts]
    if any(v is None for v in va + vb):
        return False
    return all(abs(p - q) < 1e-9 * max(1.0, abs(p)) for p, q in zip(va, vb))


# ------------------------------------------------------------------ Trajectory

DT = 1.0 / 120.0
MAX_T = 40.0


def param_count(r: dict) -> int:
    return 1 if r["step"] <= 0 else int((r["max"] - r["min"]) / r["step"] + 1e-9) + 1


def param_value(r: dict, i: int) -> float:
    return r["min"] + max(0, min(i, param_count(r) - 1)) * r["step"]


def velocity(spec: dict, a: int, b: int) -> tuple[float, float]:
    va, vb = param_value(spec["a"], a), param_value(spec["b"], b)
    if spec["control"] == "polar":
        rad = math.radians(va)
        return vb * math.cos(rad), vb * math.sin(rad)
    return va, vb


def _seg_dist(x1, y1, x2, y2, cx, cy):
    dx, dy = x2 - x1, y2 - y1
    l2 = dx * dx + dy * dy
    t = 0.0 if l2 == 0 else max(0.0, min(1.0, ((cx - x1) * dx + (cy - y1) * dy) / l2))
    return math.hypot(x1 + t * dx - cx, y1 + t * dy - cy)


def simulate(spec: dict, a: int, b: int) -> tuple[bool, str, float]:
    vx, vy = velocity(spec, a, b)
    g = spec["gravity"]
    x0, y0 = spec.get("launchX", 0.0), spec.get("launchY", 0.0)
    tg = spec["target"]
    px, py = x0, y0
    closest = math.hypot(px - tg["x"], py - tg["y"])
    k = 1
    while True:
        t = k * DT
        if t > MAX_T:
            return False, "timeout", closest
        x = x0 + vx * t
        y = y0 + vy * t - 0.5 * g * t * t
        d = _seg_dist(px, py, x, y, tg["x"], tg["y"])
        closest = min(closest, d)
        if d <= tg["r"]:
            return True, "target", closest
        if y < 0:
            return False, "ground", closest
        for o in spec.get("obstacles", []):
            if min(o["x1"], o["x2"]) <= x <= max(o["x1"], o["x2"]) and min(o["y1"], o["y2"]) <= y <= max(o["y1"], o["y2"]):
                return False, "obstacle", closest
        w = spec["world"]
        if x > w["x2"] or x < w["x1"] - 1 or y > w["y2"] * 3:
            return False, "out", closest
        px, py = x, y
        k += 1


def trajectory_solutions(spec: dict) -> list[tuple[int, int]]:
    out = []
    for a in range(param_count(spec["a"])):
        for b in range(param_count(spec["b"])):
            if simulate(spec, a, b)[0]:
                out.append((a, b))
    return out


# ------------------------------------------------------------------ Microscope


def inside(poly, x, y) -> bool:
    c = False
    j = len(poly) - 1
    for i in range(len(poly)):
        a, b = poly[i], poly[j]
        if (a["y"] > y) != (b["y"] > y) and x < (b["x"] - a["x"]) * (y - a["y"]) / (b["y"] - a["y"]) + a["x"]:
            c = not c
        j = i
    return c


def edge_dist(poly, x, y) -> float:
    best = 1e18
    for i in range(len(poly)):
        a, b = poly[i], poly[(i + 1) % len(poly)]
        best = min(best, _seg_dist(a["x"], a["y"], b["x"], b["y"], x, y))
    return best


def micro_judge(spec: dict, tap: dict, assist: bool = False) -> str:
    if tap["zoom"] / 100.0 + 1e-9 < spec["minTapZoom"]:
        return "zoom"
    if abs(tap["focus"] / 1000.0 - spec["focusTarget"]) > spec["focusTolerance"] + 1e-9:
        return "blurry"
    tol = spec["tapTolerance"] + (spec["assistTolerance"] if assist else 0)
    for r in spec["regions"]:
        if r.get("target"):
            for p in r["polygons"]:
                if inside(p, tap["x"], tap["y"]) or edge_dist(p, tap["x"], tap["y"]) <= tol:
                    return "hit"
    return "wrong"


def micro_evaluate(spec: dict, taps: list[dict], assist=False) -> bool:
    wrong = 0
    for t in taps:
        v = micro_judge(spec, t, assist)
        if v == "hit":
            return True
        if v == "wrong":
            wrong += 1
            if wrong > spec["maxWrong"]:
                return False
    return False


# ------------------------------------------------------------------ Lab

KW = 1e-14


def lab_replay(spec: dict, actions: list[dict]) -> dict:
    def init():
        return {"vol": spec.get("initialVolume", 0.0), "mass": spec.get("initialMass", 0.0),
                "sp": dict(spec.get("initialSpecies", {})), "tot": {}, "seq": []}

    st = init()
    for a in actions:
        if a["r"] == -1:
            st = init()
            continue
        if spec["module"] == "separation":
            st["seq"].append(spec["tools"][a["r"]]["id"])
            continue
        r = spec["reagents"][a["r"]]
        step_milli = round(r["step"] * 1000)
        assert a["amt"] > 0 and a["amt"] % step_milli == 0, (r["id"], a)
        amt = a["amt"] / 1000.0
        st["tot"][r["id"]] = st["tot"].get(r["id"], 0.0) + amt
        assert st["tot"][r["id"]] <= r["max"] + 1e-9, f"{r['id']} melebihi max"
        for k, v in r.get("species", {}).items():
            st["sp"][k] = st["sp"].get(k, 0.0) + v * amt
        st["vol"] += r.get("volumePerUnit", 0.0) * amt
        st["mass"] += r.get("massPerUnit", 0.0) * amt
        assert st["vol"] <= spec["vesselCapacity"] + 1e-9, "bejana penuh"
    return st


def lab_react(spec: dict, st: dict) -> dict:
    rx = spec.get("reaction")
    out = {k: dict(v) if isinstance(v, dict) else (list(v) if isinstance(v, list) else v) for k, v in st.items()}
    if not rx:
        return out
    ext = min(out["sp"].get(s, 0.0) / c for s, c in rx["reactants"].items())
    if ext <= 0:
        return out
    for s, c in rx["reactants"].items():
        out["sp"][s] = max(0.0, out["sp"].get(s, 0.0) - c * ext)
    for s, c in rx["products"].items():
        out["sp"][s] = out["sp"].get(s, 0.0) + c * ext
    return out


def lab_ph(st: dict) -> float | None:
    if st["vol"] <= 0:
        return None
    c = (st["sp"].get("H+", 0.0) - st["sp"].get("OH-", 0.0)) / (st["vol"] / 1000.0)
    h = (c + math.sqrt(c * c + 4 * KW)) / 2
    return -math.log10(h)


def lab_check(spec: dict, raw: dict) -> list[bool]:
    st = lab_react(spec, raw)
    res = []
    for t in spec["targets"]:
        k = t["kind"]
        if k == "concentration":
            if t["unit"] == "M":
                c = st["sp"].get(t["species"], 0.0) / (st["vol"] / 1000.0) if st["vol"] > 0 else None
            else:
                c = 100.0 * st["sp"].get(t["species"], 0.0) / st["mass"] if st["mass"] > 0 else None
            ok = c is not None and abs(c - t["value"]) <= t["relTol"] * t["value"] + 1e-12
            if t.get("volume") is not None:
                ok = ok and abs(st["vol"] - t["volume"]) <= t.get("volumeTol", 0) + 1e-9
            if t.get("mass") is not None:
                ok = ok and abs(st["mass"] - t["mass"]) <= t.get("massTol", 0) + 1e-9
            res.append(ok)
        elif k == "ph":
            p = lab_ph(st)
            res.append(p is not None and t["min"] <= p <= t["max"])
        elif k == "amounts":
            res.append(all(abs(st["sp"].get(c["species"], 0.0) - c["value"]) <= c["tol"] + 1e-9 for c in t["conditions"]))
        elif k == "reagent_total":
            res.append(abs(raw["tot"].get(t["reagent"], 0.0) - t["value"]) <= t["tol"] + 1e-9)
        elif k == "sequence":
            res.append(raw["seq"] in t["accepted"])
        else:
            raise ValueError(k)
    return res


def lab_evaluate(spec: dict, actions: list[dict]) -> bool:
    return all(lab_check(spec, lab_replay(spec, actions)))


def mass_balance(spec: dict, raw: dict, molar_mass: dict[str, float]) -> tuple[float, float]:
    """Massa spesies yang bereaksi sebelum vs sesudah reaksi (harus sama)."""
    after = lab_react(spec, raw)
    rx = spec["reaction"]
    species = set(rx["reactants"]) | set(rx["products"])
    before = sum(raw["sp"].get(s, 0.0) * molar_mass[s] for s in species)
    aft = sum(after["sp"].get(s, 0.0) * molar_mass[s] for s in species)
    return before, aft


def frac(s: str) -> Fraction:
    return Fraction(s)
