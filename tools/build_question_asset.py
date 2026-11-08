#!/usr/bin/env python3
"""
Build step reproduksibel: kickstart/bank_soal_duel_400.json -> app/src/main/assets/questions.json

Langkah:
  1. Validasi struktur sumber (memakai validate_question_bank.validate_structure).
  2. Terapkan KOREKSI EKSPLISIT per-ID (lihat docs/DATA_ISSUES.md). Setiap koreksi memiliki
     guard: teks lama HARUS ada persis, jika tidak build gagal (mencegah koreksi diam-diam salah).
  3. Terapkan NORMALISASI TIPOGRAFI deterministik (pangkat ^ -> superscript Unicode, tanda kali,
     derajat Celsius, subskrip rumus kimia, panah reaksi). Tidak mengubah makna/kunci.
  4. Validasi ulang hasil (400 soal, opsi unik, kunci == teks opsi, dst).
  5. Tulis asset JSON kompak dan deterministik + log transformasi.

bankVersion = SHA-256 file asset yang dihasilkan. Gradle menghitung ulang hash yang sama saat build
(BuildConfig.BANK_SHA256) sehingga dua perangkat dengan APK berbeda isi bank akan menolak duel.

Penggunaan:  python tools/build_question_asset.py [--check]
  --check  : jangan menulis file; gagal (exit 1) jika asset yang ada berbeda dari hasil build.
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import io
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from validate_question_bank import Report, validate_structure  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "kickstart" / "bank_soal_duel_400.json"
OUT = ROOT / "app" / "src" / "main" / "assets" / "questions.json"
LOG = ROOT / "docs" / "DATA_TRANSFORM_LOG.md"

BANK_VERSION_NAME = "2026.11.07-r1"
SCHEMA_VERSION = 1

SUP = str.maketrans("0123456789+-()nxi=", "⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻⁽⁾ⁿˣⁱ⁼")
SUB = str.maketrans("0123456789+-()n", "₀₁₂₃₄₅₆₇₈₉₊₋₍₎ₙ")


def sup(s: str) -> str:
    out = s.translate(SUP)
    if any(c.isascii() and c.isalnum() for c in out):
        raise ValueError(f"Tidak bisa dijadikan superscript Unicode: {s!r}")
    return out


def sub(s: str) -> str:
    out = s.translate(SUB)
    if any(c.isascii() and c.isalnum() for c in out):
        raise ValueError(f"Tidak bisa dijadikan subskrip Unicode: {s!r}")
    return out


# ---------------------------------------------------------------------------
# 1. KOREKSI EKSPLISIT (id, field, teks_lama, teks_baru, alasan)
#    field: "question" | "explanation" | "option:A".."option:D"
# ---------------------------------------------------------------------------
CORRECTIONS: list[tuple[str, str, str, str, str]] = [
    ("FIS-SMP-030", "question", "Robot bergerak lurus bergerak lurus sejauh", "Robot bergerak lurus sejauh",
     "Frasa 'bergerak lurus' tertulis dua kali (juga di PDF/CSV)."),
    ("BIO-SMA-045", "question", "p2 + 2pq + q2", "p² + 2pq + q²",
     "Pangkat dua hilang; 'p2' dan 'q2' seharusnya p² dan q² (persamaan Hardy-Weinberg)."),
]
# Energi kinetik: '1/2 x m x v2' -> v² (superscript hilang saat ekspor)
for _id, m, v in [("FIS-SMA-031", 2, 4), ("FIS-SMA-032", 4, 5), ("FIS-SMA-033", 6, 6),
                  ("FIS-SMA-034", 8, 7), ("FIS-SMA-035", 10, 8)]:
    CORRECTIONS.append((_id, "explanation", f"1/2 x {m} x {v}2 =", f"1/2 x {m} x {v}² =",
                        f"Pangkat hilang: '{v}2' seharusnya {v}² (Ek = ½mv²)."))
# Pythagoras: 'akar(32 + 42)' -> √(3² + 4²)
for _id, a, b in [("MAT-SMP-036", 3, 4), ("MAT-SMP-037", 5, 12), ("MAT-SMP-038", 8, 15),
                  ("MAT-SMP-039", 7, 24), ("MAT-SMP-040", 20, 21)]:
    CORRECTIONS.append((_id, "explanation", f"akar({a}2 + {b}2)", f"√({a}² + {b}²)",
                        f"Pangkat hilang: '{a}2 + {b}2' seharusnya {a}² + {b}²."))
# Barisan aritmetika: '(6-1)x² = 13' -> '(6-1) x 2 = 13' (angka beda keliru dijadikan superscript)
for _id, n, d, sym in [("MAT-SMA-026", 6, 2, "²"), ("MAT-SMA-027", 7, 3, "³"), ("MAT-SMA-028", 8, 4, "⁴"),
                       ("MAT-SMA-029", 9, 5, "5"), ("MAT-SMA-030", 10, 6, "6")]:
    CORRECTIONS.append((_id, "explanation", f"({n}-1)x{sym} =", f"({n}-1) x {d} =",
                        "Tanda kali dan beda barisan menempel" +
                        (f"; '{d}' keliru menjadi superscript '{sym}' (terbaca pangkat)." if sym != str(d) else ".")))
# Integral tentu: '2 x 42 /2' -> 2 x 4²/2
for _id, a, b in [("MAT-SMA-046", 2, 4), ("MAT-SMA-047", 3, 6), ("MAT-SMA-048", 4, 8),
                  ("MAT-SMA-049", 5, 10), ("MAT-SMA-050", 6, 12)]:
    CORRECTIONS.append((_id, "explanation", f"{a} x {b}2 /2", f"{a} x {b}²/2",
                        f"Pangkat hilang: '{b}2' seharusnya {b}²."))
# Rumus umum hidrokarbon: tampilkan subskrip
for oid, old, new in [("A", "CnH(2n+2)", "CₙH₂ₙ₊₂"), ("B", "CnH(2n)", "CₙH₂ₙ"),
                      ("C", "CnH(2n-2)", "CₙH₂ₙ₋₂"), ("D", "CnH(n)", "CₙHₙ")]:
    CORRECTIONS.append(("KIM-SMA-021", f"option:{oid}", old, new, "Notasi subskrip rumus umum hidrokarbon."))


# ---------------------------------------------------------------------------
# 2. NORMALISASI TIPOGRAFI
# ---------------------------------------------------------------------------
def caret_to_superscript(s: str) -> str:
    s = re.sub(r"\^\(([^()]+)\)", lambda m: sup(m.group(1).replace(" ", "")), s)
    s = re.sub(r"\^(-?\d+)", lambda m: sup(m.group(1)), s)
    return s


def multiplication_sign(s: str, field: str, subject: str) -> str:
    if field == "explanation":
        return s.replace(" x ", " × ")
    # angka/kurung sebelum, angka/kurung sesudah: '5 - 2 x 3', '6,02 x 10'
    s = re.sub(r"(?<=[\d)]) x (?=[\d(])", " × ", s)
    # satuan panjang sebelum angka: '3 cm x 4 cm'
    s = re.sub(r"(?<=\bcm) x (?=\d)", " × ", s)
    if subject == "Biologi":  # persilangan genetika 'Aa x aa', 'AaBb x AaBb'
        s = re.sub(r"(?<=\b[A-Za-z]{2})( x )(?=[A-Za-z]{2}\b)", " × ", s)
        s = re.sub(r"(?<=\b[A-Za-z]{4})( x )(?=[A-Za-z]{4}\b)", " × ", s)
    return s


def celsius(s: str) -> str:
    s = s.replace("J/kg C)", "J/(kg·°C))")
    s = re.sub(r"(?<=\d) C(?=[\s,).]|$)", " °C", s)
    return s


def chemistry(s: str, subject: str) -> str:
    if subject not in ("Kimia", "Biologi"):
        return s
    # subskrip angka setelah simbol unsur/kurung: H2O, CO2, 2NH3, M1V1
    # simbol unsur maksimal 1 huruf kecil -> 'Cas9' (nama protein) tidak disubskrip
    s = re.sub(r"(?<![a-z]{2})(?<=[A-Za-z])(\d+)", lambda m: sub(m.group(1)), s)
    # muatan ion: H+, Na+, K+, H₂+
    s = re.sub(r"(?<=[A-Za-z₀-₉])\+(?=[\s/,.)\]\-]|$)", "⁺", s)
    return s


def physics(s: str, subject: str) -> str:
    if subject != "Fisika":
        return s
    s = re.sub(r"\bR([123])\b", lambda m: "R" + sub(m.group(1)), s)
    s = re.sub(r"\bv0\b", "v₀", s)
    s = re.sub(r"\bdeltaT\b", "ΔT", s)
    s = re.sub(r"\blambda\b", "λ", s)
    return s


def misc(s: str) -> str:
    s = s.replace("<=>", "⇌").replace("->", "→")
    s = re.sub(r"\bsqrt\((\d+)\)", r"√\1", s)
    s = re.sub(r"\brho\b", "ρ", s)
    s = re.sub(r"\bpi = ", "π = ", s)
    s = re.sub(r"\ba1\b", "a₁", s)
    s = re.sub(r"\bUn\b", "Uₙ", s)
    return s


def normalize(text: str, field: str, subject: str) -> str:
    s = text
    s = caret_to_superscript(s)
    s = multiplication_sign(s, field, subject)
    s = celsius(s)
    s = chemistry(s, subject)
    s = physics(s, subject)
    s = misc(s)
    s = re.sub(r"\s+", " ", s).strip()
    return s


# ---------------------------------------------------------------------------
def get_field(q: dict, field: str) -> str:
    if field.startswith("option:"):
        return next(o for o in q["options"] if o["id"] == field[7:])["text"]
    return q[field] or ""


def set_field(q: dict, field: str, value: str) -> None:
    if field.startswith("option:"):
        next(o for o in q["options"] if o["id"] == field[7:])["text"] = value
    else:
        q[field] = value


def build() -> tuple[bytes, list[str], list[str]]:
    data = json.loads(SRC.read_text(encoding="utf-8"))
    rep = Report()
    validate_structure(data, rep)
    if rep.errors:
        raise SystemExit("Sumber tidak valid:\n" + "\n".join(rep.errors))

    qs = copy.deepcopy(data["questions"])
    by_id = {q["id"]: q for q in qs}
    corr_log, norm_log = [], []

    for qid, field, old, new, why in CORRECTIONS:
        q = by_id[qid]
        cur = get_field(q, field)
        if old not in cur:
            raise SystemExit(f"Guard koreksi gagal: {qid}.{field} tidak memuat {old!r}. Isi: {cur!r}")
        updated = cur.replace(old, new, 1)
        set_field(q, field, updated)
        if field == f"option:{q['correctOptionId']}":
            q["correctAnswer"] = updated
        corr_log.append(f"| {qid} | {field} | `{cur}` | `{updated}` | {why} |")

    out_questions = []
    for q in qs:
        subj = q["subject"]
        fields = ["question"] + [f"option:{o['id']}" for o in q["options"]]
        if q.get("explanation"):
            fields.append("explanation")
        for f in fields:
            before = get_field(q, f)
            after = normalize(before, "explanation" if f == "explanation" else f, subj)
            if after != before:
                set_field(q, f, after)
                norm_log.append(f"| {q['id']} | {f} | `{before}` | `{after}` |")
        correct_text = get_field(q, f"option:{q['correctOptionId']}")
        expl = q.get("explanation")
        out_questions.append({
            "id": q["id"],
            "subject": subj,
            "gradeBand": q["gradeBand"],
            "topic": q["topic"],
            "difficulty": q["difficulty"],
            "timeLimitSeconds": q["timeLimitSeconds"],
            "stem": q["question"],
            "options": [{"id": o["id"], "text": o["text"]} for o in q["options"]],
            "correctOptionId": q["correctOptionId"],
            "correctAnswer": correct_text,
            "explanation": expl if (isinstance(expl, str) and expl.strip()) else None,
        })

    # validasi ulang hasil dengan nama field sumber
    check = {"questions": [dict(q, question=q["stem"]) for q in out_questions]}
    rep2 = Report()
    validate_structure(check, rep2)
    if rep2.errors:
        raise SystemExit("Hasil build tidak valid:\n" + "\n".join(rep2.errors))

    asset = {
        "schemaVersion": SCHEMA_VERSION,
        "bankVersionName": BANK_VERSION_NAME,
        "sourceFile": SRC.name,
        "sourceSha256": hashlib.sha256(SRC.read_bytes()).hexdigest(),
        "questionCount": len(out_questions),
        "questions": out_questions,
    }
    payload = json.dumps(asset, ensure_ascii=False, separators=(",", ":"), sort_keys=False).encode("utf-8") + b"\n"
    return payload, corr_log, norm_log


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()
    payload, corr_log, norm_log = build()
    sha = hashlib.sha256(payload).hexdigest()
    out = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
    if args.check:
        current = OUT.read_bytes() if OUT.exists() else b""
        if current != payload:
            out.write(f"ASSET TIDAK SINKRON: jalankan `python tools/build_question_asset.py`.\n")
            return 1
        out.write(f"OK asset sinkron. bankSha256={sha}\n")
        return 0
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(payload)
    LOG.parent.mkdir(parents=True, exist_ok=True)
    LOG.write_text(
        "# Log Transformasi Dataset -> Asset Aplikasi\n\n"
        "Dihasilkan otomatis oleh `tools/build_question_asset.py`. Jangan disunting manual.\n\n"
        f"- Sumber: `kickstart/{SRC.name}` (tidak diubah)\n"
        f"- Asset: `app/src/main/assets/questions.json`\n"
        f"- bankVersionName: `{BANK_VERSION_NAME}`\n"
        f"- bankSha256 (SHA-256 file asset): `{sha}`\n"
        f"- Koreksi eksplisit: {len(corr_log)}; normalisasi tipografi: {len(norm_log)} field\n\n"
        "## Koreksi eksplisit (dengan guard teks lama)\n\n"
        "| ID | Field | Sebelum | Sesudah | Alasan |\n|---|---|---|---|---|\n"
        + "\n".join(corr_log)
        + "\n\n## Normalisasi tipografi (makna tidak berubah)\n\n"
        "| ID | Field | Sebelum | Sesudah |\n|---|---|---|---|\n"
        + "\n".join(norm_log)
        + "\n",
        encoding="utf-8",
    )
    out.write(f"Asset ditulis: {OUT} ({len(payload)} byte)\nbankSha256={sha}\n"
              f"Koreksi={len(corr_log)} Normalisasi={len(norm_log)} -> {LOG}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
