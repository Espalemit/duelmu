# Laporan QA Paket 80 Soal Bergambar

Dihasilkan otomatis oleh `python tools/validate_visual_pack.py --report docs/QA_VISUAL_REPORT.md`.

- Sumber: `content/visual/questions_extra_80.json` + `content/visual/images/*.png` (salinan utuh dari `kickstart/Duelmu_Aset_Gambar_80_Soal_Baru.zip`)
- Pembanding: `kickstart/Duelmu_80_Soal_Bergambar_SMP_SMA.pdf`
- SHA-256 JSON sumber: `ff17d86e7caf63085d258dbb1382e6574ec1094df6d7b0bb060753df3c88f69e`

| Pemeriksaan | Status |
|---|---|
| Struktur 80 soal visual (ID, mapel, jenjang, opsi, kunci, waktu, imageAsset) | LULUS |
| 80 PNG valid & dapat didekode (CRC, zlib, filter), 1250x600, unik, tanpa yatim | LULUS |
| JSON + 80 PNG identik byte-per-byte dengan ZIP kickstart | LULUS |
| Teks 80 soal (topik, pertanyaan, opsi, label) + kunci & pembahasan == PDF | LULUS |
| PASANGAN GAMBAR: piksel ilustrasi di halaman soal PDF == PNG dengan ID sama (80/80) | LULUS |

## Error (0)

- (tidak ada)

## Peringatan (0)

- (tidak ada)
