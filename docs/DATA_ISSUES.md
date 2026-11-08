# Temuan Data Bank Soal (PDF ↔ JSON ↔ CSV)

Diperiksa 7 November 2026 dengan `tools/validate_question_bank.py` (otomatis) dan tinjauan manual
terhadap 250 soal konsep (non-parametrik). File sumber di `kickstart/` **tidak diubah**; semua koreksi
diterapkan secara reproduksibel oleh `tools/build_question_asset.py` dengan *guard* (teks lama harus ada
persis, kalau tidak build gagal). Log per-field: [`DATA_TRANSFORM_LOG.md`](DATA_TRANSFORM_LOG.md).

## 1. Konsistensi antar-format

| Pemeriksaan | Hasil |
|---|---|
| JSON vs CSV, 400 baris × semua kolom | **Identik** |
| JSON vs PDF, 400 soal: topik, jenjang, kesulitan, waktu, nomor, pertanyaan, 4 opsi | **Identik** (`pdftotext -raw`, poppler) |
| JSON vs PDF, lampiran kunci 400/400: huruf + teks jawaban | **Identik** |
| Struktur: 400 soal, 100/mapel, 50 SMP + 50 SMA, ID unik, 4 opsi unik, kunci valid, waktu 60/90/120 | **Lulus** |

Tidak ada perbedaan kunci antara PDF, JSON, dan CSV. Seluruh 400 soal dipertahankan.

## 2. Cacat yang ditemukan dan dikoreksi (ada di ketiga format)

Cacat ini identik di PDF, JSON, dan CSV, jadi berasal dari proses pembuatan bank soal (bukan dari
konversi format). Kunci jawaban **tidak** terdampak; yang terdampak adalah teks pembahasan atau notasi.

| ID | Masalah | Koreksi | Verifikasi |
|---|---|---|---|
| FIS-SMA-031…035 | Pembahasan energi kinetik: `1/2 x 2 x 42 = 16 J`; tanda pangkat hilang sehingga perhitungannya tampak salah (½·2·42 = 42) | `1/2 × 2 × 4² = 16 J` | Evaluasi aritmetika otomatis: hasil = kunci |
| MAT-SMP-036…040 | Pythagoras: `akar(32 + 42) = 5` | `√(3² + 4²) = 5` | Evaluasi otomatis |
| MAT-SMA-026…028 | Barisan aritmetika: `3 + (6-1)x² = 13`; beda barisan keliru menjadi superscript sehingga terbaca sebagai pangkat | `3 + (6-1) × 2 = 13` | Evaluasi otomatis |
| MAT-SMA-029…030 | `(9-1)x5`, tanda kali menempel | `(9-1) × 5` | Evaluasi otomatis |
| MAT-SMA-046…050 | Integral: `2 x 42 /2 = 16` | `2 × 4²/2 = 16` | Evaluasi otomatis |
| BIO-SMA-045 | Hardy-Weinberg `p2 + 2pq + q2` (pangkat hilang) | `p² + 2pq + q²` | Tinjauan manual |
| FIS-SMP-030 | Frasa ganda "Robot bergerak lurus bergerak lurus sejauh…" | "Robot bergerak lurus sejauh…" | Deteksi otomatis |
| KIM-SMA-021 | Rumus umum hidrokarbon `CnH(2n+2)` dkk. | `CₙH₂ₙ₊₂`, `CₙH₂ₙ`, `CₙH₂ₙ₋₂`, `CₙHₙ` | Tinjauan manual |

## 3. Normalisasi tipografi (makna tidak berubah)

Diterapkan deterministik agar rumus tampil benar **tanpa** renderer matematika berbasis jaringan:

- Pangkat caret → superscript Unicode: `10^(-2)` → `10⁻²`, `3^(x+1)` → `3ˣ⁺¹`, `10^23` → `10²³`.
- Tanda kali `x` di antara bilangan → `×` (variabel `x` pada aljabar tidak disentuh).
- Derajat Celsius `25 C` → `25 °C`; `J/kg C` → `J/(kg·°C)`.
- Subskrip rumus kimia (hanya Kimia/Biologi): `H2O` → `H₂O`, `CO2` → `CO₂`, ion `H+` → `H⁺`, `Na+/K+` → `Na⁺/K⁺`.
  Nama protein `Cas9` sengaja dikecualikan.
- Simbol: `->` → `→`, `<=>` → `⇌`, `sqrt(3)` → `√3`, `pi` → `π`, `lambda` → `λ`, `rho` → `ρ`, `deltaT` → `ΔT`, `R1` → `R₁`.

Total 161 field dinormalisasi dan 26 koreksi eksplisit. Lihat log lengkap.

## 4. Catatan yang TIDAK diubah (perlu keputusan editorial guru)

- **Distribusi kesulitan tidak merata**: jenjang SMA tidak memiliki soal *mudah*; Fisika SMP dan
  Biologi SMP tidak memiliki soal *sulit*; Kimia SMP *sulit* dan Matematika SMP *sulit* masing-masing
  hanya 5 soal. Aplikasi menampilkan jumlah soal tersedia dan menonaktifkan pilihan yang mustahil.
- Ringkasan distribusi di PDF hal. 2 cocok dengan data aktual (mis. Fisika: mudah 19, sedang 59, sulit 22).
- Frasa soal integral "f(x)=2x dx" kurang lazim (lebih baku: "∫₀⁴ 2x dx"). Tidak diubah karena maknanya jelas.
- Pemisah desimal campur: `22,4` (koma) dan `0.5`/`11.2` (titik); `4.200` berarti ribuan. Tidak diubah
  agar sama dengan PDF dan opsi jawaban; disarankan diseragamkan pada revisi bank berikutnya.
- 250 soal konsep tidak memiliki pembahasan (`explanation: null`). Aplikasi hanya menampilkan jawaban
  benar untuk soal tersebut; **tidak ada pembahasan karangan**.
- Kunci konseptual 250 soal non-parametrik saya tinjau satu per satu dan tidak menemukan kunci yang
  salah. Ini bukan pengganti review guru mata pelajaran sebelum rilis publik.

## 5. Keterbatasan pemeriksaan

- Perbandingan PDF memakai teks hasil ekstraksi `pdftotext -raw`. Perbedaan yang hanya berupa spasi
  dianggap cocok (dilaporkan sebagai catatan). Tata letak visual PDF (gambar, warna) tidak diperiksa.
- Rekomputasi otomatis mencakup pola parametrik yang dikenali (kelajuan, F = ma, integral, jumlah akar)
  dan evaluasi semua rantai aritmetika di pembahasan (255 ruas numerik). Soal parametrik lain
  diverifikasi lewat kesesuaian pembahasan ↔ kunci ↔ PDF.
