# Laporan QA Dataset Bank Soal

Dihasilkan otomatis oleh `tools/validate_question_bank.py`. Jangan disunting manual;
jalankan ulang validator setelah mengubah dataset.

- SHA-256 file JSON sumber: `c98e3b2fead4778b0aba7fe6bb51cec832f7011ddbf4252e0ed98acdb9ba352b`
- Total soal: **400**
- ERROR: **0**, WARNING: **14**

## Ringkasan pemeriksaan

| Pemeriksaan | Status |
|---|---|
| Tepat 400 soal | LULUS |
| ID unik | LULUS |
| 100 soal/mapel, 50 SMP + 50 SMA per mapel | LULUS |
| Field per soal (ID, jenjang, kesulitan, waktu, 4 opsi unik, kunci valid) | LULUS |
| Penomoran ID 001-050 per mapel-jenjang lengkap | LULUS |
| JSON == CSV (semua 400 baris, semua kolom) | LULUS |
| JSON == PDF (400 soal: metadata, pertanyaan, 4 opsi) | LULUS |
| JSON == PDF (lampiran kunci, 400/400 soal) | LULUS |
| Asset: struktur valid, ID/kunci/metadata identik dengan sumber | LULUS |
| Asset: pembahasan konsisten dengan kunci & aritmetika benar | LULUS |

## Distribusi (mapel × jenjang × kesulitan)

| Mapel | Jenjang | Mudah | Sedang | Sulit | Total |
|---|---|---:|---:|---:|---:|
| Fisika | SMP | 19 | 31 | 0 | 50 |
| Fisika | SMA | 0 | 28 | 22 | 50 |
| Biologi | SMP | 33 | 17 | 0 | 50 |
| Biologi | SMA | 0 | 33 | 17 | 50 |
| Kimia | SMP | 21 | 24 | 5 | 50 |
| Kimia | SMA | 0 | 25 | 25 | 50 |
| Matematika | SMP | 14 | 31 | 5 | 50 |
| Matematika | SMA | 0 | 17 | 33 | 50 |

> Catatan: tidak semua kombinasi mapel–jenjang memiliki ketiga tingkat kesulitan (mis. jenjang SMA tidak memiliki soal *mudah*). Aplikasi menampilkan jumlah soal tersedia per filter dan mencegah permintaan yang mustahil.

## Catatan proses

- Ekstraksi PDF: poppler `pdftotext -raw`.
- PDF: 400 blok soal dan 400 baris kunci berhasil diparse.
- Lampiran kunci PDF diperiksa untuk 400/400 soal.
- Evaluasi rantai aritmetika pembahasan: 255 ruas numerik diperiksa.
- Rekomputasi otomatis pola parametrik: 20 soal (sisanya diverifikasi via pembahasan/kunci PDF).
- Asset `app/src/main/assets/questions.json` bankSha256 = `67217b6d58d047f702b82aa4f22bfad80ff81294f7c2380b8a22ff8d4b9861da`, bankVersionName = `2026.11.10-r560`.

## ERROR

- Tidak ada.

## WARNING (editorial, tidak memblokir)

- [sumber] FIS-SMP-030: pertanyaan mengandung frasa berulang 'bergerak lurus bergerak lurus' -> 'Robot bergerak lurus bergerak lurus sejauh 72 m dalam 9 s dengan kelajuan tetap. Kelajuannya adalah ...'
- [sumber] FIS-SMA-031: pembahasan '1/2 x 2 x 42' = 42 tetapi ditulis sama dengan '16' = 16 -> 'Ek = 1/2 mv² = 1/2 x 2 x 42 = 16 J.'
- [sumber] FIS-SMA-032: pembahasan '1/2 x 4 x 52' = 104 tetapi ditulis sama dengan '50' = 50 -> 'Ek = 1/2 mv² = 1/2 x 4 x 52 = 50 J.'
- [sumber] FIS-SMA-033: pembahasan '1/2 x 6 x 62' = 186 tetapi ditulis sama dengan '108' = 108 -> 'Ek = 1/2 mv² = 1/2 x 6 x 62 = 108 J.'
- [sumber] FIS-SMA-034: pembahasan '1/2 x 8 x 72' = 288 tetapi ditulis sama dengan '196' = 196 -> 'Ek = 1/2 mv² = 1/2 x 8 x 72 = 196 J.'
- [sumber] FIS-SMA-035: pembahasan '1/2 x 10 x 82' = 410 tetapi ditulis sama dengan '320' = 320 -> 'Ek = 1/2 mv² = 1/2 x 10 x 82 = 320 J.'
- [sumber] MAT-SMA-026: pembahasan ruas '3 + (6-1)x²' memuat 'x' menempel superscript (tanda kali/pangkat rusak) -> 'Un = a1 + (n-1)d = 3 + (6-1)x² = 13.'
- [sumber] MAT-SMA-027: pembahasan ruas '4 + (7-1)x³' memuat 'x' menempel superscript (tanda kali/pangkat rusak) -> 'Un = a1 + (n-1)d = 4 + (7-1)x³ = 22.'
- [sumber] MAT-SMA-028: pembahasan ruas '5 + (8-1)x⁴' memuat 'x' menempel superscript (tanda kali/pangkat rusak) -> 'Un = a1 + (n-1)d = 5 + (8-1)x⁴ = 33.'
- [sumber] MAT-SMA-046: pembahasan '2 x 42 /2' = 42 tetapi ditulis sama dengan '16' = 16 -> 'Integral ax dx = ax²/2. Nilai 0..4 = 2 x 42 /2 = 16.'
- [sumber] MAT-SMA-047: pembahasan '3 x 62 /2' = 93 tetapi ditulis sama dengan '54' = 54 -> 'Integral ax dx = ax²/2. Nilai 0..6 = 3 x 62 /2 = 54.'
- [sumber] MAT-SMA-048: pembahasan '4 x 82 /2' = 164 tetapi ditulis sama dengan '128' = 128 -> 'Integral ax dx = ax²/2. Nilai 0..8 = 4 x 82 /2 = 128.'
- [sumber] MAT-SMA-049: pembahasan '5 x 102 /2' = 255 tetapi ditulis sama dengan '250' = 250 -> 'Integral ax dx = ax²/2. Nilai 0..10 = 5 x 102 /2 = 250.'
- [sumber] MAT-SMA-050: pembahasan '6 x 122 /2' = 366 tetapi ditulis sama dengan '432' = 432 -> 'Integral ax dx = ax²/2. Nilai 0..12 = 6 x 122 /2 = 432.'
