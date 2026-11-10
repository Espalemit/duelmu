#!/usr/bin/env python3
"""
Generator reproduksibel 80 soal MINI-GAME Duelmu -> content/game/game_questions_80.json

Isi soal ditulis manual di content_math_phys_chem.py dan biology_scenes.py. Generator:
  1. menyusun metadata (ID 061..070 per mapel x jenjang, durasi sesuai kesulitan);
  2. membentuk konfigurasi engine (Asteroid / Trajectory / Microscope / Lab);
  3. MEMBUKTIKAN solusi referensi dengan cermin mesin (engines.py) - gagal keras bila tidak;
  4. menulis JSON deterministik (urutan stabil, tanpa timestamp).

Penggunaan: python tools/game_content/generate_game_questions.py [--check] [--svg DIR]
  --check : gagal bila file yang ada berbeda dari hasil generator (untuk CI)
  --svg   : tulis pratinjau SVG setiap preparat biologi ke DIR (untuk tinjauan visual)
"""
from __future__ import annotations

import argparse
import json
import math
import random
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import engines  # noqa: E402
from biology_scenes import BIO, H, W, area  # noqa: E402
from content_math_phys_chem import CHEM, MATH, PHYS  # noqa: E402

ROOT = HERE.parent.parent
OUT = ROOT / "content" / "game" / "game_questions_80.json"
SUBJECT = {"MAT": "Matematika", "FIS": "Fisika", "BIO": "Biologi", "KIM": "Kimia"}
TIME = {"mudah": 60, "sedang": 90, "sulit": 120}
GAME_VERSION = "2026.11.10-game80-r1"


def base(qid, grade, diff, topic, stem, expl, game, solution, solution_text, flags=None):
    assert qid.split("-")[1] == grade
    return {
        "id": qid,
        "questionType": "mini_game",
        "subject": SUBJECT[qid[:3]],
        "gradeBand": grade,
        "topic": topic,
        "difficulty": diff,
        "timeLimitSeconds": TIME[diff],
        "stem": stem,
        "explanation": expl,
        "game": game,
        "solution": solution,
        "solutionText": solution_text,
        "editorialFlags": flags or [],
    }


def build_math():
    out = []
    speed = {"mudah": (0.07, 1.6), "sedang": (0.085, 1.4), "sulit": (0.10, 1.25)}
    for qid, grade, diff, topic, stem, tpl, ans, dis, max_wrong, sol, expl, hint in MATH:
        for d in dis:
            for a in ans:
                if engines.equivalent(a, d):
                    raise SystemExit(f"{qid}: pengecoh {d!r} setara dengan jawaban {a!r}")
        if len(set(dis)) != len(dis) or set(dis) & set(ans):
            raise SystemExit(f"{qid}: token duplikat")
        for i in range(len(ans)):
            if "{%d}" % i not in tpl:
                raise SystemExit(f"{qid}: template tanpa slot {i}")
        fs, si = speed[diff]
        game = {"kind": "asteroid", "template": tpl, "answerTokens": ans, "distractorTokens": dis, "maxWrong": max_wrong,
                "fallSpeed": fs, "spawnInterval": si, "hint": hint}
        hits = list(range(len(ans)))
        assert engines.asteroid_progress(game, hits)[2]
        out.append(base(qid, grade, diff, topic, stem, expl, game, {"kind": "asteroid", "hits": hits}, sol))
    return out


def param_index(r, value):
    if value is None:
        return None
    idx = round((value - r["min"]) / r["step"]) if r["step"] > 0 else 0
    assert abs(engines.param_value(r, idx) - value) < 1e-9, (r, value)
    return idx


def build_phys():
    out = []
    for qid, grade, diff, topic, stem, spec, ref, sol_text, expl, hint in PHYS:
        game = {"kind": "trajectory", **spec, "hint": hint}
        game.setdefault("launchX", 0.0)
        game.setdefault("launchY", 0.0)
        game.setdefault("obstacles", [])
        sols = engines.trajectory_solutions(game)
        if not sols:
            raise SystemExit(f"{qid}: tidak ada parameter yang mengenai sasaran")
        total = engines.param_count(game["a"]) * engines.param_count(game["b"])
        if total > 20 and len(sols) / total > 0.15:
            raise SystemExit(f"{qid}: terlalu mudah ditebak ({len(sols)}/{total} kombinasi kena)")
        ia, ib = param_index(game["a"], ref[0]), param_index(game["b"], ref[1])
        cands = [s for s in sols if (ia is None or s[0] == ia) and (ib is None or s[1] == ib)]
        if not cands:
            raise SystemExit(f"{qid}: solusi referensi {ref} tidak mengenai sasaran; solusi grid: "
                             f"{[(engines.param_value(game['a'], a), engines.param_value(game['b'], b)) for a, b in sols][:12]}")
        best = min(cands, key=lambda s: engines.simulate(game, *s)[2])
        game["_solutions"] = len(sols)
        out.append(base(qid, grade, diff, topic, stem, expl, game,
                        {"kind": "trajectory", "shots": [{"a": best[0], "b": best[1]}]}, sol_text))
    return out


def build_bio():
    out = []
    params = {"mudah": (0.10, 3), "sedang": (0.08, 2), "sulit": (0.06, 2)}
    for qid, grade, diff, topic, tname, stem, fn, min_tap, max_zoom, tol, expl, hint in BIO:
        sc, decoy_order = fn()
        shapes, regions = sc.export(decoy_order)
        rng = random.Random(qid)
        focus = round(rng.uniform(0.25, 0.75), 3)
        ftol, max_wrong = params[diff]
        game = {"kind": "microscope", "worldW": W, "worldH": H, "background": sc.bg, "shapes": shapes, "regions": regions,
                "targetName": tname, "focusTarget": focus, "focusTolerance": ftol, "minZoom": 1.0, "maxZoom": max_zoom,
                "minTapZoom": min_tap, "tapTolerance": tol, "assistTolerance": 6.0, "maxWrong": max_wrong, "hint": hint}
        targets = [r for r in regions if r["target"]]
        if not targets:
            raise SystemExit(f"{qid}: tidak ada daerah target")
        tarea = sum(area([(p["x"], p["y"]) for p in poly]) for r in targets for poly in r["polygons"])
        frac = tarea / (W * H)
        if frac > 0.3:
            raise SystemExit(f"{qid}: daerah target terlalu luas ({frac:.0%})")
        poly = targets[0]["polygons"][0]
        cx = sum(p["x"] for p in poly) / len(poly)
        cy = sum(p["y"] for p in poly) / len(poly)
        tap = {"x": int(round(cx)), "y": int(round(cy)), "zoom": int(math.ceil(min_tap * 100)), "focus": int(round(focus * 1000))}
        if not engines.micro_evaluate(game, [tap]):
            raise SystemExit(f"{qid}: tap referensi di pusat target tidak dinilai benar")
        # tap kabur / perbesaran kurang tidak boleh dihitung benar
        assert not engines.micro_evaluate(game, [dict(tap, focus=int(abs(focus - 0.3) * 1000) if focus > 0.5 else int((focus + 0.3) * 1000))])
        assert not engines.micro_evaluate(game, [dict(tap, zoom=int(min_tap * 100) - 20)]) or min_tap <= 1.0
        game["_targetArea"] = round(frac, 4)
        flags = ["Ilustrasi preparat skematik orisinal (vektor): disarankan ditinjau guru biologi sebelum rilis produksi."]
        out.append(base(qid, grade, diff, topic, stem, expl, game,
                        {"kind": "microscope", "taps": [tap], "assist": False},
                        f"Struktur yang benar: {tname}.", flags))
    return out


def build_chem():
    out = []
    for qid, grade, diff, topic, stem, spec, ref, sol_text, expl, hint, molar in CHEM:
        game = {"kind": "lab", **spec, "hint": hint}
        for k in ("initialVolume", "initialMass"):
            game.setdefault(k, 0.0)
        game.setdefault("initialSpecies", {})
        game.setdefault("reagents", [])
        game.setdefault("tools", [])
        game.setdefault("maxResets", 2)
        actions = []
        for rid, amount in ref:
            if game["module"] == "separation":
                actions.append({"r": [t["id"] for t in game["tools"]].index(rid), "amt": 0})
            else:
                idx = [r["id"] for r in game["reagents"]].index(rid)
                actions.append({"r": idx, "amt": int(round(amount * 1000))})
        if not engines.lab_evaluate(game, actions):
            st = engines.lab_react(game, engines.lab_replay(game, actions))
            raise SystemExit(f"{qid}: solusi referensi gagal: {st} pH={engines.lab_ph(st)}")
        if molar:
            before, after = engines.mass_balance(game, engines.lab_replay(game, actions), molar)
            if abs(before - after) > 1e-6:
                raise SystemExit(f"{qid}: kekekalan massa gagal {before} != {after}")
        # satu langkah lebih sedikit/lebih banyak dari reagen pertama tidak boleh lolos (target tidak terlalu longgar)
        if game["module"] != "separation":
            r0 = actions[0]
            step = int(round(game["reagents"][r0["r"]]["step"] * 1000))
            for delta in (-step, step):
                alt = [dict(r0, amt=r0["amt"] + delta)] + actions[1:]
                if alt[0]["amt"] > 0 and game["module"] not in ("neutralization",) and engines.lab_evaluate(game, alt) and qid not in ("KIM-SMA-069",):
                    raise SystemExit(f"{qid}: target terlalu longgar (±1 langkah juga lolos)")
        out.append(base(qid, grade, diff, topic, stem, expl, game, {"kind": "lab", "actions": actions}, sol_text))
    return out


def svg_preview(q: dict) -> str:
    g = q["game"]
    parts = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {g["worldW"]} {g["worldH"]}" width="1000" height="700">',
             f'<rect width="100%" height="100%" fill="{g["background"]}"/>']

    def style(s):
        return f'fill="{s.get("fill", "none")}" stroke="{s.get("stroke", "none")}" stroke-width="{s.get("w", 0)}"'

    def catmull(pts, closed):
        n = len(pts)
        d = f"M{pts[0][0]},{pts[0][1]}"
        rng_ = range(n) if closed else range(n - 1)
        for i in rng_:
            p0 = pts[(i - 1) % n] if closed or i > 0 else pts[i]
            p1, p2 = pts[i], pts[(i + 1) % n]
            p3 = pts[(i + 2) % n] if closed or i + 2 < n else p2
            c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6)
            c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
            d += f" C{c1[0]:.1f},{c1[1]:.1f} {c2[0]:.1f},{c2[1]:.1f} {p2[0]},{p2[1]}"
        return d + (" Z" if closed else "")

    for s in g["shapes"]:
        p = s["p"]
        pts = list(zip(p[0::2], p[1::2]))
        if s["k"] == "e":
            parts.append(f'<ellipse cx="{p[0]}" cy="{p[1]}" rx="{p[2]}" ry="{p[3]}" transform="rotate({p[4]} {p[0]} {p[1]})" {style(s)}/>')
        elif s["k"] == "p":
            parts.append('<polygon points="%s" %s/>' % (" ".join("%s,%s" % xy for xy in pts), style(s)))
        elif s["k"] == "l":
            parts.append('<polyline points="%s" %s/>' % (" ".join("%s,%s" % xy for xy in pts), style(dict(s, fill="none"))))
        elif s["k"] in ("c", "o"):
            parts.append(f'<path d="{catmull(pts, s["k"] == "c")}" {style(s)}/>')
    for r in g["regions"]:
        if r["target"]:
            for poly in r["polygons"]:
                pts_s = " ".join("%s,%s" % (pt["x"], pt["y"]) for pt in poly)
                parts.append(f'<polygon points="{pts_s}" fill="none" stroke="#00E5FF" stroke-width="2" stroke-dasharray="6 4"/>')
    parts.append("</svg>")
    return "\n".join(parts)


def build() -> dict:
    qs = build_math() + build_phys() + build_bio() + build_chem()
    qs.sort(key=lambda q: ("FIS BIO KIM MAT".split().index(q["id"][:3]), q["gradeBand"] != "SMP", q["id"]))
    for q in qs:  # bidang diagnostik internal tidak ikut dikirim ke aplikasi
        q["game"].pop("_solutions", None)
        q["game"].pop("_targetArea", None)
    return {"schemaVersion": 2, "packVersion": GAME_VERSION, "questionCount": len(qs), "questions": qs}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--svg", type=Path)
    args = ap.parse_args()
    data = build()
    payload = (json.dumps(data, ensure_ascii=False, indent=1) + "\n").encode("utf-8")
    if args.svg:
        args.svg.mkdir(parents=True, exist_ok=True)
        for q in data["questions"]:
            if q["game"]["kind"] == "microscope":
                (args.svg / f"{q['id']}.svg").write_text(svg_preview(q), encoding="utf-8")
    if args.check:
        if not OUT.exists() or OUT.read_bytes() != payload:
            print("content/game/game_questions_80.json TIDAK SINKRON dengan generator")
            return 1
        print("OK game pack sinkron")
        return 0
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(payload)
    print(f"{OUT.relative_to(ROOT)}: {data['questionCount']} soal, {len(payload)} byte")
    return 0


if __name__ == "__main__":
    sys.exit(main())
