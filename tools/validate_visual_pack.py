#!/usr/bin/env python3
"""
Validator paket 80 soal BERGAMBAR (content/visual/).

Memeriksa:
  1. Struktur: tepat 80 soal, 20/mapel, 10 SMP + 10 SMA per mapel, ID XXX-YYY-051..060,
     4 opsi A-D unik, correctOptionId == correctAnswer, waktu sesuai kesulitan,
     imageAsset == images/<ID>.png, distribusi kesulitan per mapel.
  2. Integritas PNG: signature, CRC setiap chunk, zlib, filter baris, ukuran 1250x600,
     bukan gambar kosong, tidak ada dua soal memakai gambar identik, tidak ada PNG yatim.
  3. Kesesuaian dengan ZIP asli (kickstart/Duelmu_Aset_Gambar_80_Soal_Baru.zip): JSON dan
     setiap PNG identik byte-per-byte.
  4. Kesesuaian dengan PDF (kickstart/Duelmu_80_Soal_Bergambar_SMP_SMA.pdf), memakai poppler:
     - halaman soal: ID, topik, pertanyaan, opsi A-D, jenjang/kesulitan/durasi;
     - lampiran kunci: huruf kunci + pembahasan;
     - PASANGAN GAMBAR: piksel gambar yang tertanam di halaman soal ID X == piksel images/X.png
       (RGB + alpha), sehingga gambar tidak mungkin tertukar antarsoal.
     Tanpa poppler, pemeriksaan PDF DILAPORKAN DILEWATI (bukan lulus).

Penggunaan: python tools/validate_visual_pack.py [--no-pixels] [--report docs/QA_VISUAL_REPORT.md]
Exit 0 = tidak ada ERROR.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import pngutil  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
VISUAL_DIR = ROOT / "content" / "visual"
VISUAL_JSON = VISUAL_DIR / "questions_extra_80.json"
ZIP = ROOT / "kickstart" / "Duelmu_Aset_Gambar_80_Soal_Baru.zip"
PDF = ROOT / "kickstart" / "Duelmu_80_Soal_Bergambar_SMP_SMA.pdf"

SUBJECTS = {"Fisika": "FIS", "Biologi": "BIO", "Kimia": "KIM", "Matematika": "MAT"}
TIME = {"mudah": 60, "sedang": 90, "sulit": 120}
ID_RE = re.compile(r"^(FIS|BIO|KIM|MAT)-(SMP|SMA)-(05[1-9]|060)$")
EXPECTED_SIZE = (1250, 600)


class Report:
    def __init__(self) -> None:
        self.errors: list[str] = []
        self.warnings: list[str] = []
        self.checks: list[tuple[str, str]] = []

    def check(self, name: str, ok: bool | None, detail: str = "") -> None:
        self.checks.append((name, ("LULUS" if ok else "DILEWATI" if ok is None else "GAGAL") + (f" — {detail}" if detail else "")))


def squash(s: str) -> str:
    return re.sub(r"\s+", "", s or "")


def validate_structure(qs: list[dict], rep: Report) -> None:
    n0 = len(rep.errors)
    if len(qs) != 80:
        rep.errors.append(f"jumlah soal visual {len(qs)} != 80")
    ids = [q.get("id") for q in qs]
    for i, c in Counter(ids).items():
        if c > 1:
            rep.errors.append(f"ID duplikat {i}")
    for q in qs:
        qid = q.get("id", "?")
        m = ID_RE.match(qid)
        if not m:
            rep.errors.append(f"{qid}: format ID harus XXX-YYY-051..060")
            continue
        if SUBJECTS.get(q.get("subject")) != m.group(1):
            rep.errors.append(f"{qid}: subject {q.get('subject')} tidak cocok prefix")
        if q.get("gradeBand") != m.group(2):
            rep.errors.append(f"{qid}: gradeBand tidak cocok ID")
        if q.get("difficulty") not in TIME or q.get("timeLimitSeconds") != TIME.get(q.get("difficulty")):
            rep.errors.append(f"{qid}: waktu/kesulitan tidak valid")
        for f in ("topic", "stem", "explanation"):
            if not str(q.get(f) or "").strip():
                rep.errors.append(f"{qid}: {f} kosong")
        opts = q.get("options") or []
        if [o.get("id") for o in opts] != ["A", "B", "C", "D"]:
            rep.errors.append(f"{qid}: opsi harus A-D")
        texts = [str(o.get("text", "")).strip() for o in opts]
        if any(not t for t in texts) or len({t.lower() for t in texts}) != len(texts):
            rep.errors.append(f"{qid}: teks opsi kosong/duplikat")
        key = q.get("correctOptionId")
        if key not in ("A", "B", "C", "D"):
            rep.errors.append(f"{qid}: kunci tidak valid")
        elif q.get("correctAnswer") != texts["ABCD".index(key)]:
            rep.errors.append(f"{qid}: correctAnswer != teks opsi {key}")
        if q.get("imageAsset") != f"images/{qid}.png":
            rep.errors.append(f"{qid}: imageAsset harus images/{qid}.png")
    per = Counter((q.get("subject"), q.get("gradeBand")) for q in qs)
    for s in SUBJECTS:
        for g in ("SMP", "SMA"):
            if per[(s, g)] != 10:
                rep.errors.append(f"{s} {g}: {per[(s, g)]} soal (harus 10)")
    for s in SUBJECTS:
        d = Counter(q["difficulty"] for q in qs if q.get("subject") == s)
        if (d["mudah"], d["sedang"], d["sulit"]) != (6, 8, 6):
            rep.warnings.append(f"{s}: distribusi kesulitan {dict(d)} (target 6/8/6) — metadata ZIP dipertahankan")
    rep.check("Struktur 80 soal visual (ID, mapel, jenjang, opsi, kunci, waktu, imageAsset)", len(rep.errors) == n0)


def validate_images(qs: list[dict], rep: Report) -> dict[str, pngutil.Png]:
    n0 = len(rep.errors)
    decoded: dict[str, pngutil.Png] = {}
    seen: dict[str, str] = {}
    for q in qs:
        path = VISUAL_DIR / q["imageAsset"]
        if not path.exists():
            rep.errors.append(f"{q['id']}: file gambar hilang {path.relative_to(ROOT)}")
            continue
        try:
            png = pngutil.decode(path.read_bytes())
        except pngutil.PngError as e:
            rep.errors.append(f"{q['id']}: PNG rusak ({e})")
            continue
        if (png.width, png.height) != EXPECTED_SIZE:
            rep.errors.append(f"{q['id']}: ukuran {png.width}x{png.height} != 1250x600")
        if png.distinct_sample_colors() < 4:
            rep.errors.append(f"{q['id']}: gambar tampak kosong/polos")
        h = png.pixel_sha256()
        if h in seen:
            rep.errors.append(f"{q['id']}: gambar identik dengan {seen[h]}")
        seen[h] = q["id"]
        decoded[q["id"]] = png
    orphans = sorted({p.name for p in (VISUAL_DIR / "images").glob("*.png")} - {Path(q["imageAsset"]).name for q in qs})
    for o in orphans:
        rep.errors.append(f"PNG yatim tanpa soal: images/{o}")
    rep.check("80 PNG valid & dapat didekode (CRC, zlib, filter), 1250x600, unik, tanpa yatim", len(rep.errors) == n0)
    return decoded


def validate_against_zip(rep: Report) -> None:
    if not ZIP.exists():
        rep.check("Identik dengan ZIP kickstart", None, f"{ZIP.relative_to(ROOT)} tidak ditemukan")
        return
    n0 = len(rep.errors)
    with zipfile.ZipFile(ZIP) as z:
        names = set(z.namelist())
        for name in names:
            if name.endswith("/"):
                continue
            local = VISUAL_DIR / name
            if name == "README_IMPORT.txt" and local.exists():
                continue
            if not local.exists():
                rep.errors.append(f"ZIP memuat {name} yang tidak ada di content/visual")
            elif z.read(name) != local.read_bytes():
                rep.errors.append(f"{name} berbeda dari isi ZIP")
    rep.check("JSON + 80 PNG identik byte-per-byte dengan ZIP kickstart", len(rep.errors) == n0)


def pdf_text_pages() -> list[str] | None:
    if not shutil.which("pdftotext") or not PDF.exists():
        return None
    out = subprocess.run(["pdftotext", "-raw", str(PDF), "-"], capture_output=True, check=True).stdout.decode("utf-8")
    return out.split("\f")


def validate_against_pdf(qs: list[dict], decoded: dict[str, pngutil.Png], rep: Report, pixels: bool) -> None:
    pages = pdf_text_pages()
    if pages is None:
        rep.check("Kesesuaian teks dengan PDF", None, "pdftotext/PDF tidak tersedia")
        rep.check("Pasangan gambar PDF <-> PNG", None, "pdftotext/PDF tidak tersedia")
        return
    n0 = len(rep.errors)
    page_of: dict[str, int] = {}
    for i, text in enumerate(pages):
        m = re.search(r"\b(FIS|BIO|KIM|MAT)-(SMP|SMA)-0(5[1-9]|60)\b", text)
        # Halaman soal (bukan lampiran kunci, yang footernya juga memuat "BANK SOAL VISUAL").
        if m and "\nSOAL VISUAL\n" in text and "KUNCI & PEMBAHASAN" not in text:
            page_of.setdefault(m.group(0), i + 1)
    appendix = "\n".join(p for p in pages if "KUNCI & PEMBAHASAN" in p)
    for q in qs:
        qid = q["id"]
        if qid not in page_of:
            rep.errors.append(f"{qid}: halaman soal tidak ditemukan di PDF")
            continue
        text = pages[page_of[qid] - 1]
        flat = squash(text)
        for label, value in [("topik", q["topic"]), ("pertanyaan", q["stem"])]:
            if squash(value) not in flat:
                rep.errors.append(f"{qid}: {label} berbeda dari PDF")
        for o in q["options"]:
            if not re.search(rf"(^|\n){o['id']} {re.escape(o['text'])}\s*(\n|$)", text):
                if squash(o["id"] + o["text"]) not in flat:
                    rep.errors.append(f"{qid}: opsi {o['id']} berbeda dari PDF")
        footer = f"{q['gradeBand']} / {q['difficulty'].upper()} / {q['timeLimitSeconds']} DETIK"
        if footer not in text:
            rep.errors.append(f"{qid}: label jenjang/kesulitan/durasi PDF bukan '{footer}'")
        km = re.search(rf"\n([ABCD])\n{qid}\n(.*?)(?=\n[ABCD]\n(?:FIS|BIO|KIM|MAT)-|\nDUELMU /)", appendix, re.S)
        if not km:
            rep.errors.append(f"{qid}: entri kunci tidak ditemukan di lampiran PDF")
        else:
            if km.group(1) != q["correctOptionId"]:
                rep.errors.append(f"{qid}: kunci PDF {km.group(1)} != JSON {q['correctOptionId']}")
            if squash(km.group(2)) != squash(q["explanation"]):
                rep.errors.append(f"{qid}: pembahasan berbeda dari lampiran PDF")
    rep.check("Teks 80 soal (topik, pertanyaan, opsi, label) + kunci & pembahasan == PDF", len(rep.errors) == n0)

    if not pixels:
        rep.check("Pasangan gambar PDF <-> PNG (piksel)", None, "dilewati dengan --no-pixels")
        return
    if not shutil.which("pdfimages"):
        rep.check("Pasangan gambar PDF <-> PNG (piksel)", None, "pdfimages tidak tersedia")
        return
    n1 = len(rep.errors)
    with tempfile.TemporaryDirectory() as tmp:
        for q in qs:
            qid = q["id"]
            if qid not in page_of or qid not in decoded:
                continue
            p = page_of[qid]
            prefix = Path(tmp) / qid
            subprocess.run(["pdfimages", "-png", "-f", str(p), "-l", str(p), str(PDF), str(prefix)], check=True)
            files = sorted(Path(tmp).glob(f"{qid}-*.png"))
            imgs = [pngutil.decode(f.read_bytes()) for f in files]
            color = [im for im in imgs if im.channels == 3 and (im.width, im.height) == EXPECTED_SIZE]
            masks = [im for im in imgs if im.channels == 1 and (im.width, im.height) == EXPECTED_SIZE]
            png = decoded[qid]
            if len(color) != 1:
                rep.errors.append(f"{qid}: halaman PDF {p} memuat {len(color)} ilustrasi 1250x600 (harus 1)")
                continue
            if color[0].pixels != png.rgb():
                rep.errors.append(f"{qid}: piksel gambar PDF halaman {p} != images/{qid}.png (gambar tertukar/berbeda)")
            alpha = png.alpha()
            if alpha is not None and masks and masks[0].pixels != alpha:
                rep.errors.append(f"{qid}: alpha gambar berbeda dari soft-mask PDF")
            for f in files:
                f.unlink()
    rep.check("PASANGAN GAMBAR: piksel ilustrasi di halaman soal PDF == PNG dengan ID sama (80/80)", len(rep.errors) == n1)


def run(pixels: bool = True) -> Report:
    rep = Report()
    data = json.loads(VISUAL_JSON.read_text(encoding="utf-8"))
    qs = data["questions"]
    if data.get("questionCount") != len(qs):
        rep.errors.append("questionCount tidak sama dengan jumlah soal")
    validate_structure(qs, rep)
    decoded = validate_images(qs, rep)
    validate_against_zip(rep)
    validate_against_pdf(qs, decoded, rep, pixels)
    return rep


def write_report(rep: Report, path: Path) -> None:
    lines = [
        "# Laporan QA Paket 80 Soal Bergambar",
        "",
        "Dihasilkan otomatis oleh `python tools/validate_visual_pack.py --report docs/QA_VISUAL_REPORT.md`.",
        "",
        f"- Sumber: `content/visual/questions_extra_80.json` + `content/visual/images/*.png` (salinan utuh dari `kickstart/{ZIP.name}`)",
        f"- Pembanding: `kickstart/{PDF.name}`",
        f"- SHA-256 JSON sumber: `{hashlib.sha256(VISUAL_JSON.read_bytes()).hexdigest()}`",
        "",
        "| Pemeriksaan | Status |",
        "|---|---|",
        *[f"| {n} | {s} |" for n, s in rep.checks],
        "",
        f"## Error ({len(rep.errors)})",
        "",
        *([f"- {e}" for e in rep.errors] or ["- (tidak ada)"]),
        "",
        f"## Peringatan ({len(rep.warnings)})",
        "",
        *([f"- {w}" for w in rep.warnings] or ["- (tidak ada)"]),
        "",
    ]
    path.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--no-pixels", action="store_true", help="lewati perbandingan piksel PDF (lebih cepat)")
    ap.add_argument("--report", type=Path)
    args = ap.parse_args()
    rep = run(pixels=not args.no_pixels)
    out = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
    for n, s in rep.checks:
        out.write(f"[{s.split(' ')[0]}] {n}{s[len(s.split(' ')[0]):]}\n")
    for e in rep.errors:
        out.write(f"ERROR {e}\n")
    for w in rep.warnings:
        out.write(f"WARN  {w}\n")
    if args.report:
        write_report(rep, args.report)
        out.write(f"Laporan: {args.report}\n")
    out.flush()
    return 1 if rep.errors else 0


if __name__ == "__main__":
    sys.exit(main())
