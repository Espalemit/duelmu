#!/usr/bin/env python3
"""
Validator 80 soal MINI-GAME + laporan QA gabungan 560 soal.

Memeriksa (independen dari generator, memakai cermin mesin di tools/game_content/engines.py):
  1. Struktur: 80 soal, 20/mapel, 10 SMP + 10 SMA, ID XXX-YYY-061..070, metadata lengkap,
     durasi sesuai kesulitan, distribusi kesulitan 6/8/6 per mapel, jenis game sesuai mapel.
  2. Solusi: setiap solusi referensi menyelesaikan game; Fisika: ada solusi tetapi tidak trivial;
     Matematika: tidak ada pengecoh yang setara dengan jawaban (evaluasi numerik); Biologi: tap
     referensi tepat, target tidak menutupi preparat; Kimia: target lab tercapai, kekekalan massa.
  3. Duplikasi: instruksi tidak sama persis (setelah normalisasi) dengan 400 soal klasik maupun 80
     soal bergambar; kemiripan token tinggi dilaporkan sebagai peringatan untuk ditinjau.
  4. Bank gabungan (app/src/main/assets/questions.json): 560 soal, 140/mapel, 70/mapel/jenjang,
     400/80/80, dan sinkron dengan builder.

Penggunaan: python tools/validate_game_pack.py [--report docs/CONTENT_560_QA.md]
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
import subprocess
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools" / "game_content"))
import engines  # noqa: E402

GAME = ROOT / "content" / "game" / "game_questions_80.json"
VISUAL = ROOT / "content" / "visual" / "questions_extra_80.json"
CLASSIC = ROOT / "kickstart" / "bank_soal_duel_400.json"
ASSET = ROOT / "app" / "src" / "main" / "assets" / "questions.json"
SUBJECTS = {"Fisika": ("FIS", "trajectory"), "Biologi": ("BIO", "microscope"), "Kimia": ("KIM", "lab"), "Matematika": ("MAT", "asteroid")}
TIME = {"mudah": 60, "sedang": 90, "sulit": 120}
STOP = set("dan yang di ke dari untuk adalah ini itu dengan pada agar lalu atau the a".split())


def norm(s: str) -> str:
    return re.sub(r"\s+", " ", re.sub(r"[^\w\s]", " ", s.lower())).strip()


def tokens(s: str) -> set[str]:
    return {t for t in norm(s).split() if t not in STOP and len(t) > 1}


def jaccard(a: set, b: set) -> float:
    return len(a & b) / len(a | b) if a and b else 0.0


class Rep:
    def __init__(self):
        self.errors, self.warnings, self.checks = [], [], []

    def check(self, name, ok):
        self.checks.append((name, "LULUS" if ok else "GAGAL"))


def validate(rep: Rep) -> dict:
    data = json.loads(GAME.read_text(encoding="utf-8"))
    qs = data["questions"]
    n0 = len(rep.errors)
    if len(qs) != 80 or data.get("questionCount") != 80:
        rep.errors.append(f"jumlah soal game {len(qs)} != 80")
    ids = [q["id"] for q in qs]
    if len(set(ids)) != len(ids):
        rep.errors.append("ID game duplikat")
    for q in qs:
        qid = q["id"]
        prefix, kind = SUBJECTS.get(q.get("subject"), ("?", "?"))
        m = re.match(r"^(FIS|BIO|KIM|MAT)-(SMP|SMA)-(06[1-9]|070)$", qid)
        if not m or m.group(1) != prefix or m.group(2) != q.get("gradeBand"):
            rep.errors.append(f"{qid}: ID/mapel/jenjang tidak konsisten")
        if q.get("questionType") != "mini_game":
            rep.errors.append(f"{qid}: questionType harus mini_game")
        if q.get("timeLimitSeconds") != TIME.get(q.get("difficulty")):
            rep.errors.append(f"{qid}: durasi tidak sesuai kesulitan")
        for f in ("topic", "stem", "explanation", "solutionText"):
            if not str(q.get(f) or "").strip():
                rep.errors.append(f"{qid}: {f} kosong")
        if q["game"].get("kind") != kind or q["solution"].get("kind") != kind:
            rep.errors.append(f"{qid}: jenis game {q['game'].get('kind')} bukan {kind}")
        if not q["game"].get("hint"):
            rep.warnings.append(f"{qid}: tanpa hint")
    per = Counter((q["subject"], q["gradeBand"]) for q in qs)
    for s in SUBJECTS:
        for g in ("SMP", "SMA"):
            if per[(s, g)] != 10:
                rep.errors.append(f"{s} {g}: {per[(s, g)]} soal game (harus 10)")
        d = Counter(q["difficulty"] for q in qs if q["subject"] == s)
        if (d["mudah"], d["sedang"], d["sulit"]) != (6, 8, 6):
            rep.errors.append(f"{s}: distribusi kesulitan {dict(d)} != 6/8/6")
    rep.check("Struktur 80 soal game (ID 061-070, metadata, durasi, jenis game per mapel, 6/8/6)", len(rep.errors) == n0)

    # ---- solusi per mesin
    n1 = len(rep.errors)
    stats = {}
    for q in qs:
        g, sol, qid = q["game"], q["solution"], q["id"]
        kind = g["kind"]
        if kind == "asteroid":
            if not engines.asteroid_progress(g, sol["hits"])[2]:
                rep.errors.append(f"{qid}: solusi asteroid gagal")
            for a in g["answerTokens"]:
                for dtok in g["distractorTokens"]:
                    if engines.equivalent(a, dtok):
                        rep.errors.append(f"{qid}: pengecoh {dtok!r} setara jawaban {a!r}")
            if any(t in g["answerTokens"] for t in g["distractorTokens"]):
                rep.errors.append(f"{qid}: pengecoh sama dengan jawaban")
        elif kind == "trajectory":
            shot = sol["shots"][0]
            if not engines.simulate(g, shot["a"], shot["b"])[0]:
                rep.errors.append(f"{qid}: tembakan referensi meleset")
            sols = engines.trajectory_solutions(g)
            total = engines.param_count(g["a"]) * engines.param_count(g["b"])
            stats[qid] = f"{len(sols)}/{total} kombinasi kena"
            if total > 20 and len(sols) / total > 0.15:
                rep.errors.append(f"{qid}: terlalu mudah ditebak ({len(sols)}/{total})")
        elif kind == "microscope":
            if not engines.micro_evaluate(g, sol["taps"], sol.get("assist", False)):
                rep.errors.append(f"{qid}: tap referensi tidak tepat")
            if not any(r["target"] for r in g["regions"]) or all(r["target"] for r in g["regions"]):
                rep.errors.append(f"{qid}: daerah target/pengecoh tidak lengkap")
            stats[qid] = f"{sum(len(r['polygons']) for r in g['regions'] if r['target'])} daerah target, {len(g['shapes'])} bentuk"
        elif kind == "lab":
            if not engines.lab_evaluate(g, sol["actions"]):
                rep.errors.append(f"{qid}: solusi lab tidak mencapai target")
            if g.get("reaction"):
                st = engines.lab_replay(g, sol["actions"])
                after = engines.lab_react(g, st)
                stats[qid] = "reaksi " + g["reaction"]["equation"] + "; sisa " + ", ".join(
                    f"{k}={v:.3f}" for k, v in sorted(after["sp"].items()))
    rep.check("Solusi referensi 80 game valid (asteroid/lintasan/mikroskop/lab) + pengecoh tidak setara jawaban", len(rep.errors) == n1)

    # ---- duplikasi & kemiripan
    n2 = len(rep.errors)
    others = [(q["id"], q["question"]) for q in json.loads(CLASSIC.read_text(encoding="utf-8"))["questions"]]
    others += [(q["id"], q["stem"]) for q in json.loads(VISUAL.read_text(encoding="utf-8"))["questions"]]
    norms = {norm(t): i for i, t in others}
    sims = []
    for q in qs:
        n = norm(q["stem"])
        if n in norms:
            rep.errors.append(f"{q['id']}: instruksi sama persis dengan {norms[n]}")
        tq = tokens(q["stem"])
        best = max(((jaccard(tq, tokens(t)), i) for i, t in others), default=(0, None))
        sims.append((q["id"], best[0], best[1]))
        if best[0] >= 0.6:
            rep.warnings.append(f"{q['id']}: mirip {best[1]} (Jaccard {best[0]:.2f}) — tinjau, konsep boleh sama")
    stems = [norm(q["stem"]) for q in qs]
    if len(set(stems)) != len(stems):
        rep.errors.append("instruksi duplikat di antara soal game")
    rep.check("Tidak ada instruksi duplikat persis vs 400 klasik + 80 bergambar (kemiripan dilaporkan)", len(rep.errors) == n2)

    # ---- bank gabungan
    n3 = len(rep.errors)
    bank = json.loads(ASSET.read_text(encoding="utf-8"))
    bq = bank["questions"]
    bt = Counter(q["questionType"] for q in bq)
    if len(bq) != 560 or (bt["classic_mcq"], bt["visual_mcq"], bt["mini_game"]) != (400, 80, 80):
        rep.errors.append(f"bank gabungan: {len(bq)} soal, tipe {dict(bt)}")
    for s in SUBJECTS:
        if sum(q["subject"] == s for q in bq) != 140:
            rep.errors.append(f"bank: {s} bukan 140")
        for g in ("SMP", "SMA"):
            if sum(q["subject"] == s and q["gradeBand"] == g for q in bq) != 70:
                rep.errors.append(f"bank: {s} {g} bukan 70")
    game_in_bank = {q["id"]: q for q in bq if q["questionType"] == "mini_game"}
    for q in qs:
        if game_in_bank.get(q["id"]) != q:
            rep.errors.append(f"{q['id']}: berbeda antara content/game dan bank aplikasi (jalankan builder)")
    chk = subprocess.run([sys.executable, "-I", str(ROOT / "tools" / "build_question_asset.py"), "--check"], capture_output=True, text=True)
    if chk.returncode != 0:
        rep.errors.append("builder --check gagal: " + chk.stdout.strip()[:200])
    gen = subprocess.run([sys.executable, "-I", str(ROOT / "tools" / "game_content" / "generate_game_questions.py"), "--check"], capture_output=True, text=True)
    if gen.returncode != 0:
        rep.errors.append("generator game --check gagal: " + gen.stdout.strip()[:200])
    rep.check("Bank aplikasi 560 soal (140/mapel, 70/mapel/jenjang, 400/80/80) sinkron dengan builder & generator", len(rep.errors) == n3)
    return {"qs": qs, "bank": bank, "stats": stats, "sims": sims}


def write_report(rep: Rep, ctx: dict, path: Path) -> None:
    qs, bank = ctx["qs"], ctx["bank"]
    bq = bank["questions"]
    lines = [
        "# QA Konten 560 Soal Duelmu",
        "",
        "Dihasilkan otomatis oleh `python tools/validate_game_pack.py --report docs/CONTENT_560_QA.md`. Jangan disunting manual.",
        "",
        f"- Bank aplikasi: `app/src/main/assets/questions.json` — bankVersionName `{bank['bankVersionName']}`, "
        f"SHA-256 `{hashlib.sha256(ASSET.read_bytes()).hexdigest()}`",
        "- Laporan pendukung: [QA_DATASET_REPORT.md](QA_DATASET_REPORT.md) (400 klasik vs PDF/CSV), "
        "[QA_VISUAL_REPORT.md](QA_VISUAL_REPORT.md) (80 bergambar vs ZIP/PDF, termasuk pasangan piksel gambar), "
        "[DATA_TRANSFORM_LOG.md](DATA_TRANSFORM_LOG.md) (koreksi & normalisasi tipografi).",
        "",
        "## Status pemeriksaan",
        "",
        "| Pemeriksaan | Status |",
        "|---|---|",
        *[f"| {n} | {s} |" for n, s in rep.checks],
        "",
        "## Distribusi",
        "",
        "| Mapel | Jenjang | Klasik | Bergambar | Mini-game | Total |",
        "|---|---|---|---|---|---|",
    ]
    for s in SUBJECTS:
        for g in ("SMP", "SMA"):
            c = Counter(q["questionType"] for q in bq if q["subject"] == s and q["gradeBand"] == g)
            lines.append(f"| {s} | {g} | {c['classic_mcq']} | {c['visual_mcq']} | {c['mini_game']} | {sum(c.values())} |")
    tc = Counter(q["questionType"] for q in bq)
    lines += [f"| **Total** | | **{tc['classic_mcq']}** | **{tc['visual_mcq']}** | **{tc['mini_game']}** | **{len(bq)}** |", "",
              "Kesulitan per mapel (paket baru):", "",
              "| Mapel | Bergambar (mudah/sedang/sulit) | Mini-game (mudah/sedang/sulit) |", "|---|---|---|"]
    for s in SUBJECTS:
        v = Counter(q["difficulty"] for q in bq if q["subject"] == s and q["questionType"] == "visual_mcq")
        gm = Counter(q["difficulty"] for q in bq if q["subject"] == s and q["questionType"] == "mini_game")
        lines.append(f"| {s} | {v['mudah']}/{v['sedang']}/{v['sulit']} | {gm['mudah']}/{gm['sedang']}/{gm['sulit']} |")
    lines += ["", "## Daftar 80 soal mini-game", "", "| ID | Kesulitan | Topik | Game | Solusi | Catatan validator |", "|---|---|---|---|---|---|"]
    for q in qs:
        lines.append(f"| {q['id']} | {q['difficulty']} | {q['topic']} | {q['game']['kind']} | {q['solutionText']} | {ctx['stats'].get(q['id'], '')} |")
    lines += ["", f"## Error ({len(rep.errors)})", "", *([f"- {e}" for e in rep.errors] or ["- (tidak ada)"]), "",
              f"## Peringatan / tinjauan editorial ({len(rep.warnings)})", "", *([f"- {w}" for w in rep.warnings] or ["- (tidak ada)"]), "",
              "### Flag editorial di bank", ""]
    flagged = [q for q in bq if q.get("editorialFlags")]
    lines += [f"- {len(flagged)} soal memiliki `editorialFlags` (ilustrasi preparat Biologi skematik orisinal; "
              "ditinjau visual saat pengembangan, disarankan tinjauan guru biologi sebelum rilis produksi).", ""]
    path.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--report", type=Path)
    args = ap.parse_args()
    rep = Rep()
    ctx = validate(rep)
    out = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
    for n, s in rep.checks:
        out.write(f"[{s}] {n}\n")
    for e in rep.errors:
        out.write(f"ERROR {e}\n")
    for w in rep.warnings:
        out.write(f"WARN  {w}\n")
    if args.report:
        write_report(rep, ctx, args.report)
        out.write(f"Laporan: {args.report}\n")
    out.flush()
    return 1 if rep.errors else 0


if __name__ == "__main__":
    sys.exit(main())
