#!/usr/bin/env python3
"""
Validator bank soal Duelmu.

Memeriksa:
  1. Struktur JSON: tepat 400 soal, 100/mapel, 50 SMP + 50 SMA per mapel, ID unik,
     4 opsi A-D yang teksnya berbeda, kunci valid, correctOptionId == correctAnswer,
     waktu sesuai kesulitan, tidak ada pertanyaan kosong.
  2. Kesetaraan JSON <-> CSV untuk SEMUA baris dan kolom.
  3. Kesetaraan JSON <-> PDF (teks hasil ekstraksi) untuk SEMUA soal: metadata header,
     pertanyaan, 4 opsi, serta SELURUH lampiran kunci (huruf + teks jawaban).
     Ekstraksi memakai `pdftotext -raw` (poppler) bila tersedia, atau `pypdf` bila terpasang.
     Jika tidak ada parser PDF, pemeriksaan PDF DILAPORKAN DILEWATI (bukan dinyatakan lulus).
  4. Pemeriksaan editorial heuristik (kata/frasa berulang, stem duplikat, pembahasan
     yang menyebut jawaban berbeda dari kunci, rekomputasi soal parametrik yang dikenal).

Penggunaan:
  python tools/validate_question_bank.py [--json PATH] [--csv PATH] [--pdf PATH]
                                         [--report docs/QA_DATASET_REPORT.md]
Exit code 0 = tidak ada ERROR (WARNING boleh ada), 1 = ada ERROR.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import io
import json
import re
import shutil
import subprocess
import sys
from collections import Counter, defaultdict
from fractions import Fraction
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_JSON = ROOT / "kickstart" / "bank_soal_duel_400.json"
DEFAULT_CSV = ROOT / "kickstart" / "bank_soal_duel_400.csv"
DEFAULT_PDF = ROOT / "kickstart" / "bank_soal_duel_400.pdf"

SUBJECTS = ["Fisika", "Biologi", "Kimia", "Matematika"]
SUBJECT_PREFIX = {"Fisika": "FIS", "Biologi": "BIO", "Kimia": "KIM", "Matematika": "MAT"}
GRADES = ["SMP", "SMA"]
TIME_BY_DIFFICULTY = {"mudah": 60, "sedang": 90, "sulit": 120}
OPTION_IDS = ["A", "B", "C", "D"]
ID_RE = re.compile(r"^(FIS|BIO|KIM|MAT)-(SMP|SMA)-(\d{3})$")


class Report:
    def __init__(self) -> None:
        self.errors: list[str] = []
        self.warnings: list[str] = []
        self.info: list[str] = []
        self.checks: list[tuple[str, str]] = []  # (nama, status)

    def error(self, msg: str) -> None:
        self.errors.append(msg)

    def warn(self, msg: str) -> None:
        self.warnings.append(msg)

    def note(self, msg: str) -> None:
        self.info.append(msg)

    def check(self, name: str, ok: bool | None) -> None:
        status = "LULUS" if ok else ("DILEWATI" if ok is None else "GAGAL")
        self.checks.append((name, status))


def norm(s: str) -> str:
    """Normalisasi whitespace untuk perbandingan teks lintas format."""
    return re.sub(r"\s+", " ", (s or "").replace(" ", " ")).strip()


def loose(s: str) -> str:
    """Perbandingan longgar: abaikan spasi sama sekali (PDF kadang menghapus/menambah spasi)."""
    return re.sub(r"\s+", "", (s or "").replace(" ", ""))


# --------------------------------------------------------------------------- JSON


def validate_structure(data: dict, rep: Report) -> list[dict]:
    qs = data.get("questions")
    if not isinstance(qs, list):
        rep.error("Field 'questions' tidak ada atau bukan list.")
        rep.check("Struktur JSON", False)
        return []

    ok = True
    if len(qs) != 400:
        rep.error(f"Jumlah soal {len(qs)}, seharusnya 400.")
        ok = False
    rep.check("Tepat 400 soal", len(qs) == 400)

    ids = [q.get("id") for q in qs]
    dup = [i for i, c in Counter(ids).items() if c > 1]
    if dup:
        rep.error(f"ID duplikat: {dup}")
    rep.check("ID unik", not dup)

    per_subject = Counter(q.get("subject") for q in qs)
    per_sg = Counter((q.get("subject"), q.get("gradeBand")) for q in qs)
    dist_ok = True
    for s in SUBJECTS:
        if per_subject[s] != 100:
            rep.error(f"Mapel {s}: {per_subject[s]} soal, seharusnya 100.")
            dist_ok = False
        for g in GRADES:
            if per_sg[(s, g)] != 50:
                rep.error(f"{s} {g}: {per_sg[(s, g)]} soal, seharusnya 50.")
                dist_ok = False
    extra = set(per_subject) - set(SUBJECTS)
    if extra:
        rep.error(f"Mapel tak dikenal: {extra}")
        dist_ok = False
    rep.check("100 soal/mapel, 50 SMP + 50 SMA per mapel", dist_ok)

    field_ok = True
    for idx, q in enumerate(qs):
        qid = q.get("id", f"#{idx}")
        m = ID_RE.match(str(qid))
        if not m:
            rep.error(f"{qid}: format ID tidak valid.")
            field_ok = False
        else:
            if m.group(1) != SUBJECT_PREFIX.get(q.get("subject")):
                rep.error(f"{qid}: prefix ID tidak cocok dengan subject {q.get('subject')}.")
                field_ok = False
            if m.group(2) != q.get("gradeBand"):
                rep.error(f"{qid}: jenjang di ID tidak cocok dengan gradeBand {q.get('gradeBand')}.")
                field_ok = False
        if q.get("gradeBand") not in GRADES:
            rep.error(f"{qid}: gradeBand tidak valid: {q.get('gradeBand')}")
            field_ok = False
        diff = q.get("difficulty")
        if diff not in TIME_BY_DIFFICULTY:
            rep.error(f"{qid}: difficulty tidak valid: {diff}")
            field_ok = False
        elif q.get("timeLimitSeconds") != TIME_BY_DIFFICULTY[diff]:
            rep.error(f"{qid}: timeLimitSeconds {q.get('timeLimitSeconds')} tidak sesuai {diff}.")
            field_ok = False
        if not norm(q.get("question", "")):
            rep.error(f"{qid}: pertanyaan kosong.")
            field_ok = False
        if not norm(q.get("topic", "")):
            rep.error(f"{qid}: topik kosong.")
            field_ok = False
        opts = q.get("options") or []
        if [o.get("id") for o in opts] != OPTION_IDS:
            rep.error(f"{qid}: opsi harus tepat A,B,C,D berurutan, ditemukan {[o.get('id') for o in opts]}")
            field_ok = False
        texts = [norm(o.get("text", "")) for o in opts]
        if any(not t for t in texts):
            rep.error(f"{qid}: ada opsi kosong.")
            field_ok = False
        if len(set(t.casefold() for t in texts)) != len(texts):
            rep.error(f"{qid}: teks opsi tidak unik: {texts}")
            field_ok = False
        cid = q.get("correctOptionId")
        if cid not in OPTION_IDS:
            rep.error(f"{qid}: correctOptionId tidak valid: {cid}")
            field_ok = False
        else:
            correct_text = texts[OPTION_IDS.index(cid)] if len(texts) == 4 else None
            if correct_text != norm(q.get("correctAnswer", "")):
                rep.error(
                    f"{qid}: correctAnswer '{q.get('correctAnswer')}' != teks opsi {cid} '{correct_text}'."
                )
                field_ok = False
            # tepat satu benar: teks jawaban benar tidak boleh muncul di opsi lain
            if texts.count(correct_text) != 1:
                rep.error(f"{qid}: teks jawaban benar muncul lebih dari satu kali.")
                field_ok = False
        expl = q.get("explanation")
        if expl is not None and not isinstance(expl, str):
            rep.error(f"{qid}: explanation harus string atau null.")
            field_ok = False
        if isinstance(expl, str) and not expl.strip():
            rep.warn(f"{qid}: explanation berupa string kosong (akan diperlakukan sebagai null).")
    rep.check("Field per soal (ID, jenjang, kesulitan, waktu, 4 opsi unik, kunci valid)", field_ok)

    # Nomor urut dalam ID: 001..050 per (mapel, jenjang)
    seq_ok = True
    by_sg = defaultdict(list)
    for q in qs:
        m = ID_RE.match(str(q.get("id")))
        if m:
            by_sg[(m.group(1), m.group(2))].append(int(m.group(3)))
    for key, nums in by_sg.items():
        if sorted(nums) != list(range(1, 51)):
            rep.error(f"Nomor ID {key} tidak 001..050 lengkap.")
            seq_ok = False
    rep.check("Penomoran ID 001-050 per mapel-jenjang lengkap", seq_ok)

    meta = data.get("metadata", {})
    if meta.get("questionCount") not in (None, len(qs)):
        rep.warn(f"metadata.questionCount={meta.get('questionCount')} berbeda dari jumlah aktual {len(qs)}.")
    return qs


# --------------------------------------------------------------------------- CSV


def validate_csv(qs: list[dict], csv_path: Path, rep: Report) -> None:
    if not csv_path.exists():
        rep.warn(f"CSV tidak ditemukan: {csv_path}")
        rep.check("JSON == CSV (semua kolom)", None)
        return
    with csv_path.open(encoding="utf-8-sig", newline="") as f:
        rows = list(csv.DictReader(f))
    ok = True
    if len(rows) != len(qs):
        rep.error(f"CSV memiliki {len(rows)} baris, JSON {len(qs)}.")
        ok = False
    by_id = {r["id"]: r for r in rows}
    for q in qs:
        r = by_id.get(q["id"])
        if r is None:
            rep.error(f"{q['id']}: tidak ada di CSV.")
            ok = False
            continue
        pairs = [
            ("subject", q["subject"], r.get("subject")),
            ("gradeBand", q["gradeBand"], r.get("gradeBand")),
            ("topic", q["topic"], r.get("topic")),
            ("difficulty", q["difficulty"], r.get("difficulty")),
            ("timeLimitSeconds", str(q["timeLimitSeconds"]), r.get("timeLimitSeconds")),
            ("question", q["question"], r.get("question")),
            ("correctOptionId", q["correctOptionId"], r.get("correctOptionId")),
            ("correctAnswer", q["correctAnswer"], r.get("correctAnswer")),
            ("explanation", q.get("explanation") or "", r.get("explanation") or ""),
        ]
        for o in q["options"]:
            pairs.append((f"option{o['id']}", o["text"], r.get(f"option{o['id']}")))
        for name, a, b in pairs:
            if norm(a) != norm(b or ""):
                rep.error(f"{q['id']}: CSV.{name} berbeda. JSON='{a}' CSV='{b}'")
                ok = False
    extra = set(by_id) - {q["id"] for q in qs}
    if extra:
        rep.error(f"ID hanya ada di CSV: {sorted(extra)}")
        ok = False
    rep.check("JSON == CSV (semua 400 baris, semua kolom)", ok)


# --------------------------------------------------------------------------- PDF


def extract_pdf_text(pdf_path: Path, rep: Report) -> str | None:
    exe = shutil.which("pdftotext")
    if exe:
        try:
            out = subprocess.run(
                [exe, "-raw", "-enc", "UTF-8", str(pdf_path), "-"],
                check=True,
                capture_output=True,
            ).stdout.decode("utf-8")
            rep.note("Ekstraksi PDF: poppler `pdftotext -raw`.")
            return out
        except Exception as e:  # pragma: no cover
            rep.warn(f"pdftotext gagal: {e}")
    try:
        from pypdf import PdfReader  # type: ignore

        reader = PdfReader(str(pdf_path))
        rep.note("Ekstraksi PDF: pypdf (hasil bisa berbeda dari poppler).")
        return "\n".join(p.extract_text() or "" for p in reader.pages)
    except ImportError:
        pass
    return None


HEADER_RE = re.compile(
    r"^(?P<topic>.+?) · (?P<grade>SMP|SMA) · (?P<diff>MUDAH|SEDANG|SULIT) · (?P<sec>\d+) DETIK · (?P<id>[A-Z]{3}-(?:SMP|SMA)-\d{3})$"
)
QNUM_RE = re.compile(r"^(?P<num>\d{3})\. (?P<text>.*)$")
OPT_RE = re.compile(r"^(?P<oid>[ABCD])\. (?P<text>.*)$")
KEY_RE = re.compile(r"^(?P<num>\d{3}) (?P<oid>[ABCD])(?: (?P<text>.*))?$")
NOISE_RE = [
    re.compile(r"^BANK SOAL DUEL / 400 PILIHAN GANDA$"),
    re.compile(r"^Fisika • Biologi • Kimia • Matematika \| 2026 \d+$"),
    re.compile(r"^(Fisika|Biologi|Kimia|Matematika)$"),
    re.compile(r"^100 SOAL \|.*$"),
    re.compile(r"^Jenjang (SMP|SMA)$"),
    re.compile(r"^NO\. KUNCI JAWABAN BENAR$"),
    re.compile(r"^\f"),
]


def is_noise(line: str) -> bool:
    return any(r.match(line) for r in NOISE_RE)


def parse_pdf(text: str) -> tuple[dict, dict]:
    """Kembalikan (soal_by_id, kunci_by_(subject,num))."""
    lines = [l.replace("\f", "").rstrip() for l in text.splitlines()]
    try:
        key_start = next(i for i, l in enumerate(lines) if l.strip() == "Lampiran: kunci jawaban")
    except StopIteration:
        key_start = len(lines)
    body, keys_part = lines[:key_start], lines[key_start:]

    questions: dict[str, dict] = {}
    cur: dict | None = None
    field: str | None = None
    for raw in body:
        line = raw.strip()
        if not line or is_noise(line):
            continue
        h = HEADER_RE.match(line)
        if h:
            cur = {
                "id": h["id"],
                "topic": h["topic"],
                "gradeBand": h["grade"],
                "difficulty": h["diff"].lower(),
                "timeLimitSeconds": int(h["sec"]),
                "num": None,
                "question": "",
                "options": {},
            }
            questions[h["id"]] = cur
            field = "header"
            continue
        if cur is None:
            continue
        mq = QNUM_RE.match(line)
        if mq and field == "header":
            cur["num"] = int(mq["num"])
            cur["question"] = mq["text"]
            field = "question"
            continue
        mo = OPT_RE.match(line)
        if mo and field in ("question", "A", "B", "C") and OPTION_IDS.index(mo["oid"]) == len(cur["options"]):
            cur["options"][mo["oid"]] = mo["text"]
            field = mo["oid"]
            continue
        # baris lanjutan (teks terbungkus)
        if field == "question":
            cur["question"] += " " + line
        elif field in OPTION_IDS:
            cur["options"][field] += " " + line

    keys: dict[tuple[str, int], dict] = {}
    subject = None
    last = None
    for raw in keys_part:
        line = raw.strip()
        if not line:
            continue
        ms = re.match(r"^Kunci - (Fisika|Biologi|Kimia|Matematika)$", line)
        if ms:
            subject = ms.group(1)
            last = None
            continue
        if line.startswith("Rujukan dan metodologi"):
            break
        if subject is None or is_noise(line):
            continue
        mk = KEY_RE.match(line)
        if mk:
            last = {"oid": mk["oid"], "text": mk["text"] or ""}
            keys[(subject, int(mk["num"]))] = last
        elif last is not None:
            last["text"] += " " + line
    return questions, keys


def validate_pdf(qs: list[dict], pdf_path: Path, rep: Report) -> None:
    if not pdf_path.exists():
        rep.warn(f"PDF tidak ditemukan: {pdf_path}")
        rep.check("JSON == PDF (soal & opsi)", None)
        rep.check("JSON == PDF (lampiran kunci)", None)
        return
    text = extract_pdf_text(pdf_path, rep)
    if text is None:
        rep.warn(
            "Tidak ada parser PDF (pdftotext/pypdf). Pemeriksaan PDF DILEWATI, bukan lulus. "
            "Pasang poppler-utils atau `pip install pypdf` lalu jalankan ulang."
        )
        rep.check("JSON == PDF (soal & opsi)", None)
        rep.check("JSON == PDF (lampiran kunci)", None)
        return
    pq, pk = parse_pdf(text)
    rep.note(f"PDF: {len(pq)} blok soal dan {len(pk)} baris kunci berhasil diparse.")

    ok_q = True
    loose_only = 0
    for q in qs:
        p = pq.get(q["id"])
        if p is None:
            rep.error(f"{q['id']}: tidak ditemukan di PDF.")
            ok_q = False
            continue
        for name in ("topic", "gradeBand", "difficulty", "timeLimitSeconds"):
            if norm(str(p[name])) != norm(str(q[name])):
                rep.error(f"{q['id']}: PDF.{name}='{p[name]}' != JSON '{q[name]}'.")
                ok_q = False
        expected_num = int(q["id"][-3:]) + (50 if q["gradeBand"] == "SMA" else 0)
        if p["num"] != expected_num:
            rep.error(f"{q['id']}: nomor di PDF {p['num']} != {expected_num}.")
            ok_q = False
        cmp_pairs = [("question", q["question"], p["question"])]
        for o in q["options"]:
            cmp_pairs.append((f"opsi {o['id']}", o["text"], p["options"].get(o["id"], "")))
        for name, a, b in cmp_pairs:
            if norm(a) == norm(b):
                continue
            if loose(a) == loose(b):
                loose_only += 1  # hanya beda spasi akibat ekstraksi
                continue
            rep.error(f"{q['id']}: PDF {name} berbeda.\n      JSON: {a}\n      PDF : {b}")
            ok_q = False
    if loose_only:
        rep.note(f"{loose_only} teks hanya berbeda spasi akibat ekstraksi PDF (dianggap cocok).")
    rep.check("JSON == PDF (400 soal: metadata, pertanyaan, 4 opsi)", ok_q)

    ok_k = True
    checked = 0
    for q in qs:
        num = int(q["id"][-3:]) + (50 if q["gradeBand"] == "SMA" else 0)
        k = pk.get((q["subject"], num))
        if k is None:
            rep.error(f"{q['id']}: kunci no. {num} {q['subject']} tidak ditemukan di lampiran PDF.")
            ok_k = False
            continue
        checked += 1
        if k["oid"] != q["correctOptionId"]:
            rep.error(f"{q['id']}: kunci PDF {k['oid']} != JSON {q['correctOptionId']}.")
            ok_k = False
        if loose(k["text"]) != loose(q["correctAnswer"]):
            rep.error(f"{q['id']}: teks kunci PDF '{k['text']}' != JSON '{q['correctAnswer']}'.")
            ok_k = False
    rep.note(f"Lampiran kunci PDF diperiksa untuk {checked}/{len(qs)} soal.")
    rep.check(f"JSON == PDF (lampiran kunci, {checked}/{len(qs)} soal)", ok_k and checked == len(qs))


# --------------------------------------------------------------------------- Editorial


def _num(s: str) -> Fraction | None:
    s = s.strip().replace(",", ".").replace("−", "-")
    m = re.fullmatch(r"(-?\d+)/(\d+)", s)
    if m:
        return Fraction(int(m.group(1)), int(m.group(2)))
    try:
        return Fraction(s)
    except (ValueError, ZeroDivisionError):
        return None


def _leading_number(s: str) -> Fraction | None:
    m = re.match(r"\s*(-?\d+(?:[.,]\d+)?(?:/\d+)?)", s)
    return _num(m.group(1)) if m else None


_SUPMAP = str.maketrans("⁰¹²³⁴⁵⁶⁷⁸⁹⁻⁺⁽⁾", "0123456789-+()")
_ARITH_OK = re.compile(r"^[\d\s.,+\-−x×*/()√⁰¹²³⁴⁵⁶⁷⁸⁹⁻%]+$")
_UNIT_TAIL = re.compile(r"\s*(?:[A-Za-z][A-Za-z/²³]*|%)?\s*\.?\s*$")


def _eval_arith(expr: str) -> float:
    s = expr.strip()
    s = re.sub(r"\b(\d{1,3})\.(\d{3})\b", r"\1\2", s)  # 4.200 (ribuan) -> 4200
    s = s.replace(",", ".").replace("−", "-").replace("×", "*")
    s = re.sub(r"(?<=[\d)\s])x(?=[\s\d(])", "*", s)
    s = re.sub(r"[⁰¹²³⁴⁵⁶⁷⁸⁹⁻⁺⁽⁾]+", lambda m: "**(" + m.group(0).translate(_SUPMAP) + ")", s)
    s = s.replace("%", "*0.01")
    s = re.sub(r"√\(", "sqrt(", s)
    s = re.sub(r"√(\d+(?:\.\d+)?)", r"sqrt(\1)", s)
    if not re.fullmatch(r"[\d\s.+\-*/()sqrt]+", s):
        raise ValueError(s)
    return float(eval(s, {"__builtins__": {}}, {"sqrt": lambda v: v ** 0.5}))  # noqa: S307 (input disaring)


def arithmetic_chain_problems(text: str) -> tuple[list[str], int]:
    """Periksa ruas-ruas 'A = B = C' yang murni numerik. Kembalikan (masalah, jumlah ruas numerik)."""
    problems: list[str] = []
    checked = 0
    for sentence in re.split(r"(?<=[^\d])\.\s+|,\s+(?=[A-Za-z])", text):
        parts = sentence.split("=")
        values: list[tuple[str, float]] = []
        for i, part in enumerate(parts):
            p = part.strip()
            if i == len(parts) - 1:
                p = p.rstrip(". ")
                p = _UNIT_TAIL.sub("", p) if not p.endswith("%") else p
            if not p or not _ARITH_OK.match(p) or not re.search(r"\d", p):
                continue
            if re.search(r"\)x[⁰¹²³⁴⁵⁶⁷⁸⁹]", p):
                problems.append(f"ruas '{p}' memuat 'x' menempel superscript (tanda kali/pangkat rusak)")
                continue
            if re.search(r"\Sx|x\S", p):
                continue  # ekspresi aljabar dengan variabel x (mis. '4x+3'), bukan aritmetika murni
            if re.search(r"\d\s+\d", re.sub(r"\s*[x×*/+\-−]\s*", "#", p)):
                problems.append(f"ruas '{p}' berisi angka tanpa operator")
                continue
            try:
                values.append((p, _eval_arith(p)))
            except Exception:
                problems.append(f"ruas '{p}' tidak dapat dievaluasi (notasi rusak?)")
        checked += len(values)
        for (pa, va), (pb, vb) in zip(values, values[1:]):
            if abs(va - vb) > 1e-6 * max(1.0, abs(va), abs(vb)):
                problems.append(f"'{pa}' = {va:g} tetapi ditulis sama dengan '{pb}' = {vb:g}")
    return problems, checked


strict_arith = False


def editorial_checks(qs: list[dict], rep: Report) -> None:
    # 1. kata berulang berurutan / frasa ganda
    for q in qs:
        for name, t in [("pertanyaan", q["question"])] + [(f"opsi {o['id']}", o["text"]) for o in q["options"]]:
            m = re.search(r"\b(\w+(?:\s+\w+){0,2})\s+\1\b", t, flags=re.IGNORECASE)
            if m and not re.fullmatch(r"\d+", m.group(1)):
                rep.warn(f"{q['id']}: {name} mengandung frasa berulang '{m.group(0)}' -> '{t}'")

    # 2. stem duplikat persis
    seen: dict[str, str] = {}
    for q in qs:
        key = norm(q["question"]).casefold()
        if key in seen:
            same_opts = True
            rep.warn(f"{q['id']}: pertanyaan identik dengan {seen[key]}.")
        else:
            seen[key] = q["id"]

    # 3. pembahasan menyebut hasil akhir yang berbeda dari kunci
    for q in qs:
        e = q.get("explanation")
        if not e:
            continue
        tail = re.split(r"=|dihasilkan", e.rstrip(". "))[-1].strip()
        exp_val = _leading_number(tail)
        key_val = _leading_number(q["correctAnswer"])
        if exp_val is not None and key_val is not None and exp_val != key_val:
            rep.error(
                f"{q['id']}: pembahasan berakhir '{tail}' tetapi kunci '{q['correctAnswer']}'."
            )

    # 3b. evaluasi rantai aritmetika pada pembahasan: 'a x b = c' harus benar secara numerik
    arith_checked = 0
    for q in qs:
        e = q.get("explanation")
        if not e:
            continue
        problems, n = arithmetic_chain_problems(e)
        arith_checked += n
        for p in problems:
            (rep.error if strict_arith else rep.warn)(f"{q['id']}: pembahasan {p} -> '{e}'")
    rep.note(f"Evaluasi rantai aritmetika pembahasan: {arith_checked} ruas numerik diperiksa.")

    # 4. rekomputasi soal parametrik yang polanya dikenali
    recomputed = 0
    for q in qs:
        t = q["question"]
        ans = _leading_number(q["correctAnswer"])
        expect = None
        m = re.search(r"sejauh (\d+) m dalam (\d+) s", t)
        if m:
            expect = Fraction(int(m.group(1)), int(m.group(2)))
        m = re.search(r"bermassa (\d+) kg dipercepat (\d+) m/s²", t)
        if m:
            expect = Fraction(int(m.group(1)) * int(m.group(2)))
        m = re.search(r"integral tentu dari 0 sampai (\d+) untuk f\(x\)=(\d+)x dx", t)
        if m:
            b, a = int(m.group(1)), int(m.group(2))
            expect = Fraction(a * b * b, 2)
        m = re.search(r"Jumlah dua akar persamaan x² - (\d+)x \+ (\d+) = 0", t)
        if m:
            expect = Fraction(int(m.group(1)))
        if expect is not None and ans is not None:
            recomputed += 1
            if expect != ans:
                rep.error(f"{q['id']}: rekomputasi = {expect}, kunci = {q['correctAnswer']}.")
    rep.note(f"Rekomputasi otomatis pola parametrik: {recomputed} soal (sisanya diverifikasi via pembahasan/kunci PDF).")


# --------------------------------------------------------------------------- Asset aplikasi


def validate_asset(src_qs: list[dict], asset_path: Path, rep: Report) -> str | None:
    """Validasi asset hasil build: struktur, kunci & metadata identik dengan sumber, aritmetika ketat."""
    global strict_arith
    if not asset_path.exists():
        rep.warn(f"Asset belum dibuat: {asset_path}. Jalankan tools/build_question_asset.py")
        rep.check("Asset aplikasi valid", None)
        return None
    raw = asset_path.read_bytes()
    data = json.loads(raw.decode("utf-8"))
    # Bank aplikasi berisi 560 soal (400 klasik + 80 bergambar + 80 game). Validator ini mengunci
    # 400 soal KLASIK terhadap sumber kickstart; paket tambahan divalidasi validate_visual_pack.py,
    # validate_game_pack.py, dan validasi gabungan di build_question_asset.py.
    aqs = [dict(q, question=q["stem"]) for q in data.get("questions", [])
           if q.get("questionType", "classic_mcq") == "classic_mcq"]
    sub = Report()
    validate_structure({"questions": aqs}, sub)
    ok = not sub.errors
    for e in sub.errors:
        rep.error(f"[asset] {e}")
    src_by_id = {q["id"]: q for q in src_qs}
    for q in aqs:
        s = src_by_id.get(q["id"])
        if s is None:
            rep.error(f"[asset] {q['id']} tidak ada di sumber.")
            ok = False
            continue
        for f in ("subject", "gradeBand", "topic", "difficulty", "timeLimitSeconds", "correctOptionId"):
            if q[f] != s[f]:
                rep.error(f"[asset] {q['id']}.{f} berubah dari sumber ({s[f]} -> {q[f]}).")
                ok = False
        if (q.get("explanation") is None) != (not s.get("explanation")):
            rep.error(f"[asset] {q['id']}: keberadaan pembahasan berubah dari sumber.")
            ok = False
    if len(aqs) != len(src_qs):
        ok = False
    rep.check("Asset: struktur valid, ID/kunci/metadata identik dengan sumber", ok)
    strict_arith = True
    before = len(rep.errors)
    sub2 = Report()
    editorial_checks(aqs, sub2)
    strict_arith = False
    for e in sub2.errors:
        rep.error(f"[asset] {e}")
    for w in sub2.warnings:
        rep.warn(f"[asset] {w}")
    rep.check("Asset: pembahasan konsisten dengan kunci & aritmetika benar", not sub2.errors)
    sha = hashlib.sha256(raw).hexdigest()
    rep.note(f"Asset `{asset_path.relative_to(ROOT).as_posix() if asset_path.is_relative_to(ROOT) else asset_path}` "
             f"bankSha256 = `{sha}`, bankVersionName = `{data.get('bankVersionName')}`.")
    return sha


# --------------------------------------------------------------------------- Laporan


def write_report(path: Path, rep: Report, qs: list[dict], sha: str) -> None:
    dist = Counter((q["subject"], q["gradeBand"], q["difficulty"]) for q in qs)
    lines = [
        "# Laporan QA Dataset Bank Soal",
        "",
        "Dihasilkan otomatis oleh `tools/validate_question_bank.py`. Jangan disunting manual;",
        "jalankan ulang validator setelah mengubah dataset.",
        "",
        f"- SHA-256 file JSON sumber: `{sha}`",
        f"- Total soal: **{len(qs)}**",
        f"- ERROR: **{len(rep.errors)}**, WARNING: **{len(rep.warnings)}**",
        "",
        "## Ringkasan pemeriksaan",
        "",
        "| Pemeriksaan | Status |",
        "|---|---|",
    ]
    lines += [f"| {n} | {s} |" for n, s in rep.checks]
    lines += ["", "## Distribusi (mapel × jenjang × kesulitan)", "", "| Mapel | Jenjang | Mudah | Sedang | Sulit | Total |", "|---|---|---:|---:|---:|---:|"]
    for s in SUBJECTS:
        for g in GRADES:
            a, b, c = (dist[(s, g, d)] for d in ("mudah", "sedang", "sulit"))
            lines.append(f"| {s} | {g} | {a} | {b} | {c} | {a + b + c} |")
    lines += [
        "",
        "> Catatan: tidak semua kombinasi mapel–jenjang memiliki ketiga tingkat kesulitan "
        "(mis. jenjang SMA tidak memiliki soal *mudah*). Aplikasi menampilkan jumlah soal "
        "tersedia per filter dan mencegah permintaan yang mustahil.",
        "",
        "## Catatan proses",
        "",
    ]
    lines += [f"- {m}" for m in rep.info] or ["- (tidak ada)"]
    lines += ["", "## ERROR", ""]
    lines += [f"- {m}" for m in rep.errors] or ["- Tidak ada."]
    lines += ["", "## WARNING (editorial, tidak memblokir)", ""]
    lines += [f"- {m}" for m in rep.warnings] or ["- Tidak ada."]
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--json", type=Path, default=DEFAULT_JSON)
    ap.add_argument("--csv", type=Path, default=DEFAULT_CSV)
    ap.add_argument("--pdf", type=Path, default=DEFAULT_PDF)
    ap.add_argument("--report", type=Path, default=ROOT / "docs" / "QA_DATASET_REPORT.md")
    ap.add_argument("--no-pdf", action="store_true", help="Lewati pemeriksaan PDF (dilaporkan DILEWATI).")
    ap.add_argument("--asset", type=Path, default=ROOT / "app" / "src" / "main" / "assets" / "questions.json")
    ap.add_argument("--no-asset", action="store_true", help="Lewati validasi asset aplikasi.")
    args = ap.parse_args(argv)

    rep = Report()
    raw = args.json.read_bytes()
    sha = hashlib.sha256(raw).hexdigest()
    data = json.loads(raw.decode("utf-8"))
    qs = validate_structure(data, rep)
    validate_csv(qs, args.csv, rep)
    if args.no_pdf:
        rep.check("JSON == PDF", None)
    else:
        validate_pdf(qs, args.pdf, rep)
    src_rep = Report()
    editorial_checks(qs, src_rep)
    # Temuan editorial pada SUMBER dicatat sebagai WARNING karena dikoreksi oleh build_question_asset.py
    # dan asset diverifikasi ketat di bawah. Kesalahan kunci tetap ERROR.
    for e in src_rep.errors:
        rep.error(e)
    for w in src_rep.warnings:
        rep.warn(f"[sumber] {w}")
    rep.info += src_rep.info
    if not args.no_asset:
        validate_asset(qs, args.asset, rep)
    write_report(args.report, rep, qs, sha)

    out = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    for n, s in rep.checks:
        out.write(f"[{s:8}] {n}\n")
    for m in rep.errors:
        out.write(f"ERROR   {m}\n")
    for m in rep.warnings:
        out.write(f"WARNING {m}\n")
    out.write(f"\nERROR={len(rep.errors)} WARNING={len(rep.warnings)} -> laporan: {args.report}\n")
    out.flush()
    return 1 if rep.errors else 0


if __name__ == "__main__":
    sys.exit(main())
