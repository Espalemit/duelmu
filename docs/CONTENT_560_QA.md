# QA Konten 560 Soal Duelmu

Dihasilkan otomatis oleh `python tools/validate_game_pack.py --report docs/CONTENT_560_QA.md`. Jangan disunting manual.

- Bank aplikasi: `app/src/main/assets/questions.json` — bankVersionName `2026.11.10-r560`, SHA-256 `67217b6d58d047f702b82aa4f22bfad80ff81294f7c2380b8a22ff8d4b9861da`
- Laporan pendukung: [QA_DATASET_REPORT.md](QA_DATASET_REPORT.md) (400 klasik vs PDF/CSV), [QA_VISUAL_REPORT.md](QA_VISUAL_REPORT.md) (80 bergambar vs ZIP/PDF, termasuk pasangan piksel gambar), [DATA_TRANSFORM_LOG.md](DATA_TRANSFORM_LOG.md) (koreksi & normalisasi tipografi).

## Status pemeriksaan

| Pemeriksaan | Status |
|---|---|
| Struktur 80 soal game (ID 061-070, metadata, durasi, jenis game per mapel, 6/8/6) | LULUS |
| Solusi referensi 80 game valid (asteroid/lintasan/mikroskop/lab) + pengecoh tidak setara jawaban | LULUS |
| Tidak ada instruksi duplikat persis vs 400 klasik + 80 bergambar (kemiripan dilaporkan) | LULUS |
| Bank aplikasi 560 soal (140/mapel, 70/mapel/jenjang, 400/80/80) sinkron dengan builder & generator | LULUS |

## Distribusi

| Mapel | Jenjang | Klasik | Bergambar | Mini-game | Total |
|---|---|---|---|---|---|
| Fisika | SMP | 50 | 10 | 10 | 70 |
| Fisika | SMA | 50 | 10 | 10 | 70 |
| Biologi | SMP | 50 | 10 | 10 | 70 |
| Biologi | SMA | 50 | 10 | 10 | 70 |
| Kimia | SMP | 50 | 10 | 10 | 70 |
| Kimia | SMA | 50 | 10 | 10 | 70 |
| Matematika | SMP | 50 | 10 | 10 | 70 |
| Matematika | SMA | 50 | 10 | 10 | 70 |
| **Total** | | **400** | **80** | **80** | **560** |

Kesulitan per mapel (paket baru):

| Mapel | Bergambar (mudah/sedang/sulit) | Mini-game (mudah/sedang/sulit) |
|---|---|---|
| Fisika | 6/8/6 | 6/8/6 |
| Biologi | 6/8/6 | 6/8/6 |
| Kimia | 6/8/6 | 6/8/6 |
| Matematika | 6/8/6 | 6/8/6 |

## Daftar 80 soal mini-game

| ID | Kesulitan | Topik | Game | Solusi | Catatan validator |
|---|---|---|---|---|---|
| FIS-SMP-061 | mudah | Jangkauan terjauh | trajectory | θ = 45° | 6/81 kombinasi kena |
| FIS-SMP-062 | mudah | Kelajuan dan jarak | trajectory | v = 10 m/s | 2/33 kombinasi kena |
| FIS-SMP-063 | mudah | Gerak lurus beraturan horizontal | trajectory | v = 6 m/s | 2/23 kombinasi kena |
| FIS-SMP-064 | mudah | Sudut dan tinggi lintasan | trajectory | θ = 60° (lintasan tinggi) | 4/71 kombinasi kena |
| FIS-SMP-065 | sedang | Lemparan ke rak tinggi | trajectory | θ ≈ 34° atau θ ≈ 73° | 10/71 kombinasi kena |
| FIS-SMP-066 | sedang | Kelajuan lemparan miring | trajectory | v = 14 m/s | 3/41 kombinasi kena |
| FIS-SMP-067 | sedang | Tembakan ke ring basket | trajectory | v ≈ 8,5 m/s | 4/101 kombinasi kena |
| FIS-SMP-068 | sedang | Dua sudut, jangkauan sama | trajectory | θ = 15° (lintasan rendah) | 5/81 kombinasi kena |
| FIS-SMP-069 | sulit | Gravitasi Bulan | trajectory | θ ≈ 15° atau θ ≈ 75° | 5/81 kombinasi kena |
| FIS-SMP-070 | sulit | Mengatur sudut dan kelajuan | trajectory | contoh: θ = 50°, v = 15,5 m/s | 52/2485 kombinasi kena |
| FIS-SMA-061 | mudah | Gerak parabola horizontal | trajectory | v₀ = 15 m/s | 2/51 kombinasi kena |
| FIS-SMA-062 | mudah | Jangkauan parabola | trajectory | θ = 30° atau θ = 60° | 3/81 kombinasi kena |
| FIS-SMA-063 | sedang | Tinggi maksimum | trajectory | θ = 60° | 5/81 kombinasi kena |
| FIS-SMA-064 | sedang | Komponen vektor kecepatan | trajectory | vₓ = 8 m/s, v_y = 20 m/s | 69/1887 kombinasi kena |
| FIS-SMA-065 | sedang | Sudut 53° dan jangkauan | trajectory | v = 25 m/s | 1/51 kombinasi kena |
| FIS-SMA-066 | sedang | Parabola dari ketinggian | trajectory | v = 10 m/s | 2/33 kombinasi kena |
| FIS-SMA-067 | sulit | Melewati rintangan | trajectory | contoh: θ = 60°, v = 18,5 m/s | 30/2911 kombinasi kena |
| FIS-SMA-068 | sulit | Komponen kecepatan dan waktu tempuh | trajectory | contoh: vₓ = 12 m/s, v_y = 13 m/s (waktu tempuh 2 s) | 15/2109 kombinasi kena |
| FIS-SMA-069 | sulit | Gerak parabola di Mars | trajectory | θ ≈ 25° atau θ ≈ 65° | 6/81 kombinasi kena |
| FIS-SMA-070 | sulit | Sasaran di atap gedung | trajectory | v ≈ 20,5 m/s | 2/41 kombinasi kena |
| BIO-SMP-061 | mudah | Sel tumbuhan | microscope | Struktur yang benar: kloroplas. | 32 daerah target, 94 bentuk |
| BIO-SMP-062 | mudah | Sel hewan | microscope | Struktur yang benar: nukleus. | 5 daerah target, 75 bentuk |
| BIO-SMP-063 | mudah | Sel darah | microscope | Struktur yang benar: leukosit. | 3 daerah target, 114 bentuk |
| BIO-SMP-064 | mudah | Jaringan otot | microscope | Struktur yang benar: otot lurik. | 1 daerah target, 609 bentuk |
| BIO-SMP-065 | sedang | Struktur daun | microscope | Struktur yang benar: jaringan palisade. | 28 daerah target, 295 bentuk |
| BIO-SMP-066 | sedang | Jaringan batang dikotil | microscope | Struktur yang benar: kambium. | 64 daerah target, 205 bentuk |
| BIO-SMP-067 | sedang | Struktur akar | microscope | Struktur yang benar: tudung akar. | 1 daerah target, 202 bentuk |
| BIO-SMP-068 | sedang | Sistem ekskresi: kulit | microscope | Struktur yang benar: kelenjar keringat. | 1 daerah target, 75 bentuk |
| BIO-SMP-069 | sulit | Struktur ginjal | microscope | Struktur yang benar: glomerulus. | 3 daerah target, 219 bentuk |
| BIO-SMP-070 | sulit | Reproduksi tumbuhan | microscope | Struktur yang benar: bakal biji. | 6 daerah target, 23 bentuk |
| BIO-SMA-061 | mudah | Fase mitosis | microscope | Struktur yang benar: sel metafase. | 1 daerah target, 124 bentuk |
| BIO-SMA-062 | mudah | Sistem peredaran darah | microscope | Struktur yang benar: arteri. | 1 daerah target, 37 bentuk |
| BIO-SMA-063 | sedang | Fase mitosis | microscope | Struktur yang benar: sel anafase. | 1 daerah target, 128 bentuk |
| BIO-SMA-064 | sedang | Sel khusus: spermatozoa | microscope | Struktur yang benar: akrosom. | 4 daerah target, 65 bentuk |
| BIO-SMA-065 | sedang | Jaringan tulang | microscope | Struktur yang benar: kanal Havers. | 5 daerah target, 318 bentuk |
| BIO-SMA-066 | sedang | Sel prokariot | microscope | Struktur yang benar: plasmid. | 3 daerah target, 90 bentuk |
| BIO-SMA-067 | sulit | Fase meiosis | microscope | Struktur yang benar: sel metafase I. | 1 daerah target, 84 bentuk |
| BIO-SMA-068 | sulit | Sitokinesis sel tumbuhan | microscope | Struktur yang benar: lempeng sel. | 1 daerah target, 124 bentuk |
| BIO-SMA-069 | sulit | Struktur kloroplas | microscope | Struktur yang benar: granum. | 7 daerah target, 67 bentuk |
| BIO-SMA-070 | sulit | Struktur neuron | microscope | Struktur yang benar: nodus Ranvier. | 6 daerah target, 42 bentuk |
| KIM-SMP-061 | mudah | Pemisahan campuran: penyaringan | lab | Penyaringan |  |
| KIM-SMP-062 | mudah | Pemisahan campuran: magnet | lab | Magnet |  |
| KIM-SMP-063 | mudah | Kadar larutan (% massa) | lab | 20 g gula + 180 g (180 mL) air |  |
| KIM-SMP-064 | mudah | Pemisahan campuran: penguapan | lab | Penguapan (kristalisasi) |  |
| KIM-SMP-065 | sedang | Pemisahan bertahap | lab | Penyaringan → Penguapan |  |
| KIM-SMP-066 | sedang | Mencampur dua larutan | lab | 100 g larutan 5% + 50 g larutan 20% |  |
| KIM-SMP-067 | sedang | Netralisasi dengan indikator universal | lab | 20 mL NaOH 0,1 M |  |
| KIM-SMP-068 | sedang | Pengenceran larutan | lab | tambah 150 g (150 mL) air |  |
| KIM-SMP-069 | sulit | Pemisahan tiga komponen | lab | Magnet → Pelarutan → Penyaringan → Penguapan |  |
| KIM-SMP-070 | sulit | Netralisasi beda konsentrasi | lab | 20 mL NaOH 0,1 M |  |
| KIM-SMA-061 | mudah | Molaritas larutan | lab | 2,0 g NaOH, tera sampai 500 mL |  |
| KIM-SMA-062 | mudah | Pengenceran (M₁V₁ = M₂V₂) | lab | 25 mL HCl 2 M + 75 mL air |  |
| KIM-SMA-063 | sedang | Titrasi asam kuat-basa kuat | lab | 25,0 mL NaOH |  |
| KIM-SMA-064 | sedang | Titrasi asam diprotik | lab | 20,0 mL NaOH |  |
| KIM-SMA-065 | sedang | Perbandingan mol reaksi | lab | 1,5 mol O₂ (terbentuk 3 mol H₂O) | reaksi 2H₂ + O₂ → 2H₂O; sisa H2=0.000, H2O=3.000, O2=0.000 |
| KIM-SMA-066 | sedang | Pereaksi pembatas | lab | 4,5 mol H₂ (H₂ pembatas, sisa N₂ 0,5 mol) | reaksi N₂ + 3H₂ → 2NH₃; sisa H2=0.000, N2=0.500, NH3=3.000 |
| KIM-SMA-067 | sulit | Stoikiometri massa | lab | 1,2 g Mg (0,05 mol; H₂ 0,05 mol) | reaksi Mg + 2HCl → MgCl₂ + H₂; sisa H2=0.050, HCl=0.000, Mg=0.000, MgCl2=0.050 |
| KIM-SMA-068 | sulit | Titrasi dengan basa divalen | lab | 50,0 mL Ba(OH)₂ |  |
| KIM-SMA-069 | sulit | Mengatur pH larutan | lab | ≈ 40,9 mL NaOH (pH 2,00) |  |
| KIM-SMA-070 | sulit | Mencampur larutan berbeda konsentrasi | lab | 50 mL NaCl 0,5 M + 150 mL NaCl 0,1 M |  |
| MAT-SMP-061 | mudah | Operasi hitung campuran | asteroid | 3 × (4 + 5) = 3 × 9 = 27 |  |
| MAT-SMP-062 | mudah | Penjumlahan pecahan | asteroid | 1/2 + 1/3 = 3/6 + 2/6 = 5/6 |  |
| MAT-SMP-063 | mudah | Persamaan linear satu variabel | asteroid | 2x = 17 − 5 = 12, x = 12 : 2 = 6 |  |
| MAT-SMP-064 | mudah | Perbandingan senilai | asteroid | Andi = 2/5 × 25 = 10, Budi = 3/5 × 25 = 15 |  |
| MAT-SMP-065 | sedang | Operasi bilangan bulat | asteroid | (−8) + 5 = −3, −3 − (−3) = −3 + 3 = 0 |  |
| MAT-SMP-066 | sedang | Keliling dan luas persegi panjang | asteroid | K = 2 × (8 + 5) = 26 cm, L = 8 × 5 = 40 cm² |  |
| MAT-SMP-067 | sedang | Teorema Pythagoras | asteroid | c² = 81 + 144 = 225, c = √225 = 15 |  |
| MAT-SMP-068 | sedang | Diskon dan persentase | asteroid | Potongan = 25% × 80.000 = 20.000; bayar = 80.000 − 20.000 = 60.000 |  |
| MAT-SMP-069 | sulit | Sistem persamaan linear dua variabel | asteroid | Jumlahkan kedua persamaan: 2x = 14, x = 7; y = 10 − 7 = 3 |  |
| MAT-SMP-070 | sulit | Persamaan garis lurus | asteroid | m = (8 − 2)/(3 − 1) = 3; c = 2 − 3 × 1 = −1, jadi y = 3x − 1 |  |
| MAT-SMA-061 | mudah | Turunan fungsi aljabar | asteroid | f'(x) = 2x + 7 |  |
| MAT-SMA-062 | mudah | Sifat eksponen | asteroid | 2³ · 2⁴ = 2³⁺⁴ = 2⁷ = 128 |  |
| MAT-SMA-063 | sedang | Sifat logaritma | asteroid | ²log 8 + ²log 4 = ²log (8 × 4) = ²log 32 = 5 |  |
| MAT-SMA-064 | sedang | Perbandingan trigonometri | asteroid | sin θ = depan/miring = 3/5, cos θ = samping/miring = 4/5 |  |
| MAT-SMA-065 | sedang | Barisan aritmetika | asteroid | b = 7 − 3 = 4; U₁₀ = 3 + 9 × 4 = 39 |  |
| MAT-SMA-066 | sedang | Persamaan kuadrat | asteroid | x² − 5x + 6 = (x − 2)(x − 3), akar 2 dan 3 |  |
| MAT-SMA-067 | sulit | Turunan fungsi polinom | asteroid | f'(x) = 12x³ − 6x² |  |
| MAT-SMA-068 | sulit | Barisan geometri | asteroid | r = 6/2 = 3; U₆ = 2 × 3⁵ = 486 |  |
| MAT-SMA-069 | sulit | Fungsi komposisi | asteroid | (f∘g)(x) = f(x²) = 2x² + 1; (f∘g)(3) = 2·9 + 1 = 19 |  |
| MAT-SMA-070 | sulit | Trigonometri sudut berelasi | asteroid | sin 150° = sin 30° = 1/2; cos 120° = −cos 60° = −1/2 |  |

## Error (0)

- (tidak ada)

## Peringatan / tinjauan editorial (0)

- (tidak ada)

### Flag editorial di bank

- 20 soal memiliki `editorialFlags` (ilustrasi preparat Biologi skematik orisinal; ditinjau visual saat pengembangan, disarankan tinjauan guru biologi sebelum rilis produksi).
