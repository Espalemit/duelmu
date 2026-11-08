# Log Transformasi Dataset -> Asset Aplikasi

Dihasilkan otomatis oleh `tools/build_question_asset.py`. Jangan disunting manual.

- Sumber: `kickstart/bank_soal_duel_400.json` (tidak diubah)
- Asset: `app/src/main/assets/questions.json`
- bankVersionName: `2026.11.07-r1`
- bankSha256 (SHA-256 file asset): `32cf858e77fd4565eb5d8a79401418a8c75f74d4b3dbfe85eccf98e39f405d8a`
- Koreksi eksplisit: 26; normalisasi tipografi: 161 field

## Koreksi eksplisit (dengan guard teks lama)

| ID | Field | Sebelum | Sesudah | Alasan |
|---|---|---|---|---|
| FIS-SMP-030 | question | `Robot bergerak lurus bergerak lurus sejauh 72 m dalam 9 s dengan kelajuan tetap. Kelajuannya adalah ...` | `Robot bergerak lurus sejauh 72 m dalam 9 s dengan kelajuan tetap. Kelajuannya adalah ...` | Frasa 'bergerak lurus' tertulis dua kali (juga di PDF/CSV). |
| BIO-SMA-045 | question | `Pada populasi setimbang Hardy-Weinberg untuk dua alel, jumlah p2 + 2pq + q2 adalah ...` | `Pada populasi setimbang Hardy-Weinberg untuk dua alel, jumlah p² + 2pq + q² adalah ...` | Pangkat dua hilang; 'p2' dan 'q2' seharusnya p² dan q² (persamaan Hardy-Weinberg). |
| FIS-SMA-031 | explanation | `Ek = 1/2 mv² = 1/2 x 2 x 42 = 16 J.` | `Ek = 1/2 mv² = 1/2 x 2 x 4² = 16 J.` | Pangkat hilang: '42' seharusnya 4² (Ek = ½mv²). |
| FIS-SMA-032 | explanation | `Ek = 1/2 mv² = 1/2 x 4 x 52 = 50 J.` | `Ek = 1/2 mv² = 1/2 x 4 x 5² = 50 J.` | Pangkat hilang: '52' seharusnya 5² (Ek = ½mv²). |
| FIS-SMA-033 | explanation | `Ek = 1/2 mv² = 1/2 x 6 x 62 = 108 J.` | `Ek = 1/2 mv² = 1/2 x 6 x 6² = 108 J.` | Pangkat hilang: '62' seharusnya 6² (Ek = ½mv²). |
| FIS-SMA-034 | explanation | `Ek = 1/2 mv² = 1/2 x 8 x 72 = 196 J.` | `Ek = 1/2 mv² = 1/2 x 8 x 7² = 196 J.` | Pangkat hilang: '72' seharusnya 7² (Ek = ½mv²). |
| FIS-SMA-035 | explanation | `Ek = 1/2 mv² = 1/2 x 10 x 82 = 320 J.` | `Ek = 1/2 mv² = 1/2 x 10 x 8² = 320 J.` | Pangkat hilang: '82' seharusnya 8² (Ek = ½mv²). |
| MAT-SMP-036 | explanation | `c = akar(32 + 42) = 5 cm.` | `c = √(3² + 4²) = 5 cm.` | Pangkat hilang: '32 + 42' seharusnya 3² + 4². |
| MAT-SMP-037 | explanation | `c = akar(52 + 122) = 13 cm.` | `c = √(5² + 12²) = 13 cm.` | Pangkat hilang: '52 + 122' seharusnya 5² + 12². |
| MAT-SMP-038 | explanation | `c = akar(82 + 152) = 17 cm.` | `c = √(8² + 15²) = 17 cm.` | Pangkat hilang: '82 + 152' seharusnya 8² + 15². |
| MAT-SMP-039 | explanation | `c = akar(72 + 242) = 25 cm.` | `c = √(7² + 24²) = 25 cm.` | Pangkat hilang: '72 + 242' seharusnya 7² + 24². |
| MAT-SMP-040 | explanation | `c = akar(202 + 212) = 29 cm.` | `c = √(20² + 21²) = 29 cm.` | Pangkat hilang: '202 + 212' seharusnya 20² + 21². |
| MAT-SMA-026 | explanation | `Un = a1 + (n-1)d = 3 + (6-1)x² = 13.` | `Un = a1 + (n-1)d = 3 + (6-1) x 2 = 13.` | Tanda kali dan beda barisan menempel; '2' keliru menjadi superscript '²' (terbaca pangkat). |
| MAT-SMA-027 | explanation | `Un = a1 + (n-1)d = 4 + (7-1)x³ = 22.` | `Un = a1 + (n-1)d = 4 + (7-1) x 3 = 22.` | Tanda kali dan beda barisan menempel; '3' keliru menjadi superscript '³' (terbaca pangkat). |
| MAT-SMA-028 | explanation | `Un = a1 + (n-1)d = 5 + (8-1)x⁴ = 33.` | `Un = a1 + (n-1)d = 5 + (8-1) x 4 = 33.` | Tanda kali dan beda barisan menempel; '4' keliru menjadi superscript '⁴' (terbaca pangkat). |
| MAT-SMA-029 | explanation | `Un = a1 + (n-1)d = 6 + (9-1)x5 = 46.` | `Un = a1 + (n-1)d = 6 + (9-1) x 5 = 46.` | Tanda kali dan beda barisan menempel. |
| MAT-SMA-030 | explanation | `Un = a1 + (n-1)d = 7 + (10-1)x6 = 61.` | `Un = a1 + (n-1)d = 7 + (10-1) x 6 = 61.` | Tanda kali dan beda barisan menempel. |
| MAT-SMA-046 | explanation | `Integral ax dx = ax²/2. Nilai 0..4 = 2 x 42 /2 = 16.` | `Integral ax dx = ax²/2. Nilai 0..4 = 2 x 4²/2 = 16.` | Pangkat hilang: '42' seharusnya 4². |
| MAT-SMA-047 | explanation | `Integral ax dx = ax²/2. Nilai 0..6 = 3 x 62 /2 = 54.` | `Integral ax dx = ax²/2. Nilai 0..6 = 3 x 6²/2 = 54.` | Pangkat hilang: '62' seharusnya 6². |
| MAT-SMA-048 | explanation | `Integral ax dx = ax²/2. Nilai 0..8 = 4 x 82 /2 = 128.` | `Integral ax dx = ax²/2. Nilai 0..8 = 4 x 8²/2 = 128.` | Pangkat hilang: '82' seharusnya 8². |
| MAT-SMA-049 | explanation | `Integral ax dx = ax²/2. Nilai 0..10 = 5 x 102 /2 = 250.` | `Integral ax dx = ax²/2. Nilai 0..10 = 5 x 10²/2 = 250.` | Pangkat hilang: '102' seharusnya 10². |
| MAT-SMA-050 | explanation | `Integral ax dx = ax²/2. Nilai 0..12 = 6 x 122 /2 = 432.` | `Integral ax dx = ax²/2. Nilai 0..12 = 6 x 12²/2 = 432.` | Pangkat hilang: '122' seharusnya 12². |
| KIM-SMA-021 | option:A | `CnH(2n+2)` | `CₙH₂ₙ₊₂` | Notasi subskrip rumus umum hidrokarbon. |
| KIM-SMA-021 | option:B | `CnH(2n)` | `CₙH₂ₙ` | Notasi subskrip rumus umum hidrokarbon. |
| KIM-SMA-021 | option:C | `CnH(2n-2)` | `CₙH₂ₙ₋₂` | Notasi subskrip rumus umum hidrokarbon. |
| KIM-SMA-021 | option:D | `CnH(n)` | `CₙHₙ` | Notasi subskrip rumus umum hidrokarbon. |

## Normalisasi tipografi (makna tidak berubah)

| ID | Field | Sebelum | Sesudah |
|---|---|---|---|
| FIS-SMP-031 | explanation | `F = ma = 2 x 3 = 6 N.` | `F = ma = 2 × 3 = 6 N.` |
| FIS-SMP-032 | explanation | `F = ma = 3 x 4 = 12 N.` | `F = ma = 3 × 4 = 12 N.` |
| FIS-SMP-033 | explanation | `F = ma = 4 x 5 = 20 N.` | `F = ma = 4 × 5 = 20 N.` |
| FIS-SMP-034 | explanation | `F = ma = 5 x 6 = 30 N.` | `F = ma = 5 × 6 = 30 N.` |
| FIS-SMP-035 | explanation | `F = ma = 6 x 7 = 42 N.` | `F = ma = 6 × 7 = 42 N.` |
| FIS-SMP-041 | explanation | `W = F s = 10 x 2 = 20 J.` | `W = F s = 10 × 2 = 20 J.` |
| FIS-SMP-042 | explanation | `W = F s = 15 x 3 = 45 J.` | `W = F s = 15 × 3 = 45 J.` |
| FIS-SMP-043 | explanation | `W = F s = 20 x 4 = 80 J.` | `W = F s = 20 × 4 = 80 J.` |
| FIS-SMP-044 | explanation | `W = F s = 25 x 5 = 125 J.` | `W = F s = 25 × 5 = 125 J.` |
| FIS-SMP-045 | explanation | `W = F s = 30 x 6 = 180 J.` | `W = F s = 30 × 6 = 180 J.` |
| FIS-SMP-046 | explanation | `V = I R = 2 x 3 = 6 V.` | `V = I R = 2 × 3 = 6 V.` |
| FIS-SMP-047 | explanation | `V = I R = 3 x 4 = 12 V.` | `V = I R = 3 × 4 = 12 V.` |
| FIS-SMP-048 | explanation | `V = I R = 4 x 5 = 20 V.` | `V = I R = 4 × 5 = 20 V.` |
| FIS-SMP-049 | explanation | `V = I R = 5 x 6 = 30 V.` | `V = I R = 5 × 6 = 30 V.` |
| FIS-SMP-050 | explanation | `V = I R = 6 x 7 = 42 V.` | `V = I R = 6 × 7 = 42 V.` |
| FIS-SMA-026 | explanation | `v = v0 + at = 3 + 2 x 4 = 11 m/s.` | `v = v₀ + at = 3 + 2 × 4 = 11 m/s.` |
| FIS-SMA-027 | explanation | `v = v0 + at = 4 + 3 x 5 = 19 m/s.` | `v = v₀ + at = 4 + 3 × 5 = 19 m/s.` |
| FIS-SMA-028 | explanation | `v = v0 + at = 5 + 4 x 6 = 29 m/s.` | `v = v₀ + at = 5 + 4 × 6 = 29 m/s.` |
| FIS-SMA-029 | explanation | `v = v0 + at = 6 + 5 x 7 = 41 m/s.` | `v = v₀ + at = 6 + 5 × 7 = 41 m/s.` |
| FIS-SMA-030 | explanation | `v = v0 + at = 7 + 6 x 8 = 55 m/s.` | `v = v₀ + at = 7 + 6 × 8 = 55 m/s.` |
| FIS-SMA-031 | explanation | `Ek = 1/2 mv² = 1/2 x 2 x 4² = 16 J.` | `Ek = 1/2 mv² = 1/2 × 2 × 4² = 16 J.` |
| FIS-SMA-032 | explanation | `Ek = 1/2 mv² = 1/2 x 4 x 5² = 50 J.` | `Ek = 1/2 mv² = 1/2 × 4 × 5² = 50 J.` |
| FIS-SMA-033 | explanation | `Ek = 1/2 mv² = 1/2 x 6 x 6² = 108 J.` | `Ek = 1/2 mv² = 1/2 × 6 × 6² = 108 J.` |
| FIS-SMA-034 | explanation | `Ek = 1/2 mv² = 1/2 x 8 x 7² = 196 J.` | `Ek = 1/2 mv² = 1/2 × 8 × 7² = 196 J.` |
| FIS-SMA-035 | explanation | `Ek = 1/2 mv² = 1/2 x 10 x 8² = 320 J.` | `Ek = 1/2 mv² = 1/2 × 10 × 8² = 320 J.` |
| FIS-SMA-036 | explanation | `v = f x lambda = 5 x 2 = 10 m/s.` | `v = f × λ = 5 × 2 = 10 m/s.` |
| FIS-SMA-037 | explanation | `v = f x lambda = 7 x 3 = 21 m/s.` | `v = f × λ = 7 × 3 = 21 m/s.` |
| FIS-SMA-038 | explanation | `v = f x lambda = 9 x 4 = 36 m/s.` | `v = f × λ = 9 × 4 = 36 m/s.` |
| FIS-SMA-039 | explanation | `v = f x lambda = 11 x 5 = 55 m/s.` | `v = f × λ = 11 × 5 = 55 m/s.` |
| FIS-SMA-040 | explanation | `v = f x lambda = 13 x 6 = 78 m/s.` | `v = f × λ = 13 × 6 = 78 m/s.` |
| FIS-SMA-041 | question | `Sebanyak 2 kg air (kalor jenis 4.200 J/kg C) dipanaskan sehingga suhunya naik 7 C tanpa perubahan fase. Kalor yang diserap adalah ...` | `Sebanyak 2 kg air (kalor jenis 4.200 J/(kg·°C)) dipanaskan sehingga suhunya naik 7 °C tanpa perubahan fase. Kalor yang diserap adalah ...` |
| FIS-SMA-041 | explanation | `Q = mc deltaT = 2 x 4.200 x 7 = 58800 J.` | `Q = mc ΔT = 2 × 4.200 × 7 = 58800 J.` |
| FIS-SMA-042 | question | `Sebanyak 3 kg air (kalor jenis 4.200 J/kg C) dipanaskan sehingga suhunya naik 8 C tanpa perubahan fase. Kalor yang diserap adalah ...` | `Sebanyak 3 kg air (kalor jenis 4.200 J/(kg·°C)) dipanaskan sehingga suhunya naik 8 °C tanpa perubahan fase. Kalor yang diserap adalah ...` |
| FIS-SMA-042 | explanation | `Q = mc deltaT = 3 x 4.200 x 8 = 100800 J.` | `Q = mc ΔT = 3 × 4.200 × 8 = 100800 J.` |
| FIS-SMA-043 | question | `Sebanyak 4 kg air (kalor jenis 4.200 J/kg C) dipanaskan sehingga suhunya naik 9 C tanpa perubahan fase. Kalor yang diserap adalah ...` | `Sebanyak 4 kg air (kalor jenis 4.200 J/(kg·°C)) dipanaskan sehingga suhunya naik 9 °C tanpa perubahan fase. Kalor yang diserap adalah ...` |
| FIS-SMA-043 | explanation | `Q = mc deltaT = 4 x 4.200 x 9 = 151200 J.` | `Q = mc ΔT = 4 × 4.200 × 9 = 151200 J.` |
| FIS-SMA-044 | question | `Sebanyak 5 kg air (kalor jenis 4.200 J/kg C) dipanaskan sehingga suhunya naik 10 C tanpa perubahan fase. Kalor yang diserap adalah ...` | `Sebanyak 5 kg air (kalor jenis 4.200 J/(kg·°C)) dipanaskan sehingga suhunya naik 10 °C tanpa perubahan fase. Kalor yang diserap adalah ...` |
| FIS-SMA-044 | explanation | `Q = mc deltaT = 5 x 4.200 x 10 = 210000 J.` | `Q = mc ΔT = 5 × 4.200 × 10 = 210000 J.` |
| FIS-SMA-045 | question | `Sebanyak 6 kg air (kalor jenis 4.200 J/kg C) dipanaskan sehingga suhunya naik 11 C tanpa perubahan fase. Kalor yang diserap adalah ...` | `Sebanyak 6 kg air (kalor jenis 4.200 J/(kg·°C)) dipanaskan sehingga suhunya naik 11 °C tanpa perubahan fase. Kalor yang diserap adalah ...` |
| FIS-SMA-045 | explanation | `Q = mc deltaT = 6 x 4.200 x 11 = 277200 J.` | `Q = mc ΔT = 6 × 4.200 × 11 = 277200 J.` |
| FIS-SMA-046 | explanation | `R seri = R1+R2+R3 = 10 ohm.` | `R seri = R₁+R₂+R₃ = 10 ohm.` |
| FIS-SMA-047 | explanation | `R seri = R1+R2+R3 = 13 ohm.` | `R seri = R₁+R₂+R₃ = 13 ohm.` |
| FIS-SMA-048 | explanation | `R seri = R1+R2+R3 = 16 ohm.` | `R seri = R₁+R₂+R₃ = 16 ohm.` |
| FIS-SMA-049 | explanation | `R seri = R1+R2+R3 = 19 ohm.` | `R seri = R₁+R₂+R₃ = 19 ohm.` |
| FIS-SMA-050 | explanation | `R seri = R1+R2+R3 = 22 ohm.` | `R seri = R₁+R₂+R₃ = 22 ohm.` |
| BIO-SMP-040 | question | `Pada rantai rumput -> belalang -> katak -> ular, katak adalah ...` | `Pada rantai rumput → belalang → katak → ular, katak adalah ...` |
| BIO-SMA-007 | option:B | `fase G1 interfase` | `fase G₁ interfase` |
| BIO-SMA-010 | question | `Pada persilangan Aa x Aa dengan dominansi penuh, rasio fenotipe dominan : resesif adalah ...` | `Pada persilangan Aa × Aa dengan dominansi penuh, rasio fenotipe dominan : resesif adalah ...` |
| BIO-SMA-011 | question | `Pada persilangan Aa x aa, peluang keturunan bergenotipe aa adalah ...` | `Pada persilangan Aa × aa, peluang keturunan bergenotipe aa adalah ...` |
| BIO-SMA-012 | question | `Untuk dua sifat bebas dengan dominansi penuh, persilangan AaBb x AaBb memberi rasio fenotipe klasik ...` | `Untuk dua sifat bebas dengan dominansi penuh, persilangan AaBb × AaBb memberi rasio fenotipe klasik ...` |
| BIO-SMA-021 | option:B | `fiksasi CO2 pada siklus Calvin` | `fiksasi CO₂ pada siklus Calvin` |
| BIO-SMA-034 | question | `Dalam satu siklus normal, pompa Na+/K+-ATPase memindahkan ...` | `Dalam satu siklus normal, pompa Na⁺/K⁺-ATPase memindahkan ...` |
| BIO-SMA-034 | option:A | `2 Na+ masuk dan 2 K+ keluar` | `2 Na⁺ masuk dan 2 K⁺ keluar` |
| BIO-SMA-034 | option:B | `2 Na+ keluar dan 3 K+ masuk` | `2 Na⁺ keluar dan 3 K⁺ masuk` |
| BIO-SMA-034 | option:C | `3 Na+ masuk dan 2 K+ keluar` | `3 Na⁺ masuk dan 2 K⁺ keluar` |
| BIO-SMA-034 | option:D | `3 Na+ keluar dan 2 K+ masuk` | `3 Na⁺ keluar dan 2 K⁺ masuk` |
| KIM-SMP-004 | question | `Air murni (H2O) digolongkan sebagai ...` | `Air murni (H₂O) digolongkan sebagai ...` |
| KIM-SMP-012 | question | `Larutan dengan pH 3 pada suhu sekitar 25 C bersifat ...` | `Larutan dengan pH 3 pada suhu sekitar 25 °C bersifat ...` |
| KIM-SMP-031 | explanation | `Persen massa = 10/100 x 100% = 10%.` | `Persen massa = 10/100 × 100% = 10%.` |
| KIM-SMP-032 | explanation | `Persen massa = 20/100 x 100% = 20%.` | `Persen massa = 20/100 × 100% = 20%.` |
| KIM-SMP-033 | explanation | `Persen massa = 25/100 x 100% = 25%.` | `Persen massa = 25/100 × 100% = 25%.` |
| KIM-SMP-034 | explanation | `Persen massa = 40/100 x 100% = 40%.` | `Persen massa = 40/100 × 100% = 40%.` |
| KIM-SMP-035 | explanation | `Persen massa = 50/100 x 100% = 50%.` | `Persen massa = 50/100 × 100% = 50%.` |
| KIM-SMP-036 | explanation | `rho = m/V = 20/10 = 2 g/cm³.` | `ρ = m/V = 20/10 = 2 g/cm³.` |
| KIM-SMP-037 | explanation | `rho = m/V = 45/15 = 3 g/cm³.` | `ρ = m/V = 45/15 = 3 g/cm³.` |
| KIM-SMP-038 | explanation | `rho = m/V = 80/20 = 4 g/cm³.` | `ρ = m/V = 80/20 = 4 g/cm³.` |
| KIM-SMP-039 | explanation | `rho = m/V = 125/25 = 5 g/cm³.` | `ρ = m/V = 125/25 = 5 g/cm³.` |
| KIM-SMP-040 | explanation | `rho = m/V = 180/30 = 6 g/cm³.` | `ρ = m/V = 180/30 = 6 g/cm³.` |
| KIM-SMP-041 | explanation | `Kadar = 5/100 x 100% = 5%.` | `Kadar = 5/100 × 100% = 5%.` |
| KIM-SMP-042 | explanation | `Kadar = 20/200 x 100% = 10%.` | `Kadar = 20/200 × 100% = 10%.` |
| KIM-SMP-043 | explanation | `Kadar = 45/300 x 100% = 15%.` | `Kadar = 45/300 × 100% = 15%.` |
| KIM-SMP-044 | explanation | `Kadar = 80/400 x 100% = 20%.` | `Kadar = 80/400 × 100% = 20%.` |
| KIM-SMP-045 | explanation | `Kadar = 125/500 x 100% = 25%.` | `Kadar = 125/500 × 100% = 25%.` |
| KIM-SMP-046 | question | `Pada 25 C, suatu larutan memiliki konsentrasi ion H+ sebesar 10^(-2) mol/L. Nilai pH larutan itu adalah ...` | `Pada 25 °C, suatu larutan memiliki konsentrasi ion H⁺ sebesar 10⁻² mol/L. Nilai pH larutan itu adalah ...` |
| KIM-SMP-046 | explanation | `pH = -log[H+] = 2.` | `pH = -log[H⁺] = 2.` |
| KIM-SMP-047 | question | `Pada 25 C, suatu larutan memiliki konsentrasi ion H+ sebesar 10^(-3) mol/L. Nilai pH larutan itu adalah ...` | `Pada 25 °C, suatu larutan memiliki konsentrasi ion H⁺ sebesar 10⁻³ mol/L. Nilai pH larutan itu adalah ...` |
| KIM-SMP-047 | explanation | `pH = -log[H+] = 3.` | `pH = -log[H⁺] = 3.` |
| KIM-SMP-048 | question | `Pada 25 C, suatu larutan memiliki konsentrasi ion H+ sebesar 10^(-4) mol/L. Nilai pH larutan itu adalah ...` | `Pada 25 °C, suatu larutan memiliki konsentrasi ion H⁺ sebesar 10⁻⁴ mol/L. Nilai pH larutan itu adalah ...` |
| KIM-SMP-048 | explanation | `pH = -log[H+] = 4.` | `pH = -log[H⁺] = 4.` |
| KIM-SMP-049 | question | `Pada 25 C, suatu larutan memiliki konsentrasi ion H+ sebesar 10^(-5) mol/L. Nilai pH larutan itu adalah ...` | `Pada 25 °C, suatu larutan memiliki konsentrasi ion H⁺ sebesar 10⁻⁵ mol/L. Nilai pH larutan itu adalah ...` |
| KIM-SMP-049 | explanation | `pH = -log[H+] = 5.` | `pH = -log[H⁺] = 5.` |
| KIM-SMP-050 | question | `Pada 25 C, suatu larutan memiliki konsentrasi ion H+ sebesar 10^(-6) mol/L. Nilai pH larutan itu adalah ...` | `Pada 25 °C, suatu larutan memiliki konsentrasi ion H⁺ sebesar 10⁻⁶ mol/L. Nilai pH larutan itu adalah ...` |
| KIM-SMP-050 | explanation | `pH = -log[H+] = 6.` | `pH = -log[H⁺] = 6.` |
| KIM-SMA-005 | question | `Pada molekul H2, ikatan kovalen terjadi akibat ...` | `Pada molekul H₂, ikatan kovalen terjadi akibat ...` |
| KIM-SMA-005 | option:C | `pembentukan ion H2+` | `pembentukan ion H₂⁺` |
| KIM-SMA-006 | question | `Bentuk geometri molekul CH4 ideal menurut VSEPR adalah ...` | `Bentuk geometri molekul CH₄ ideal menurut VSEPR adalah ...` |
| KIM-SMA-007 | option:A | `6,02 x 10^20 entitas` | `6,02 × 10²⁰ entitas` |
| KIM-SMA-007 | option:B | `1,00 x 10^6 entitas` | `1,00 × 10⁶ entitas` |
| KIM-SMA-007 | option:C | `3,01 x 10^12 entitas` | `3,01 × 10¹² entitas` |
| KIM-SMA-007 | option:D | `6,02 x 10^23 entitas` | `6,02 × 10²³ entitas` |
| KIM-SMA-015 | question | `Untuk reaksi gas N2 + 3H2 <=> 2NH3, kenaikan tekanan pada suhu tetap menggeser kesetimbangan ke ...` | `Untuk reaksi gas N₂ + 3H₂ ⇌ 2NH₃, kenaikan tekanan pada suhu tetap menggeser kesetimbangan ke ...` |
| KIM-SMA-015 | option:A | `arah pembentukan NH3` | `arah pembentukan NH₃` |
| KIM-SMA-015 | option:C | `arah pembentukan N2 dan H2` | `arah pembentukan N₂ dan H₂` |
| KIM-SMA-015 | option:D | `arah penguraian NH3 karena jumlah mol lebih kecil` | `arah penguraian NH₃ karena jumlah mol lebih kecil` |
| KIM-SMA-016 | question | `Pada suhu 25 C, pH larutan netral ideal adalah ...` | `Pada suhu 25 °C, pH larutan netral ideal adalah ...` |
| KIM-SMA-017 | question | `Campuran CH3COOH dan CH3COONa dapat bertindak sebagai ...` | `Campuran CH₃COOH dan CH₃COONa dapat bertindak sebagai ...` |
| KIM-SMA-026 | question | `Diberikan massa 18 g H2O dengan Mr=18. Jumlah mol H2O adalah ...` | `Diberikan massa 18 g H₂O dengan Mr=18. Jumlah mol H₂O adalah ...` |
| KIM-SMA-027 | question | `Diberikan massa 88 g CO2 dengan Mr=44. Jumlah mol CO2 adalah ...` | `Diberikan massa 88 g CO₂ dengan Mr=44. Jumlah mol CO₂ adalah ...` |
| KIM-SMA-028 | question | `Diberikan massa 48 g CH4 dengan Mr=16. Jumlah mol CH4 adalah ...` | `Diberikan massa 48 g CH₄ dengan Mr=16. Jumlah mol CH₄ adalah ...` |
| KIM-SMA-029 | question | `Diberikan massa 68 g NH3 dengan Mr=17. Jumlah mol NH3 adalah ...` | `Diberikan massa 68 g NH₃ dengan Mr=17. Jumlah mol NH₃ adalah ...` |
| KIM-SMA-036 | explanation | `M1V1=M2V2, M2=4 x 50/100=2 M.` | `M₁V₁=M₂V₂, M₂=4 × 50/100=2 M.` |
| KIM-SMA-037 | explanation | `M1V1=M2V2, M2=6 x 50/100=3 M.` | `M₁V₁=M₂V₂, M₂=6 × 50/100=3 M.` |
| KIM-SMA-038 | explanation | `M1V1=M2V2, M2=8 x 50/100=4 M.` | `M₁V₁=M₂V₂, M₂=8 × 50/100=4 M.` |
| KIM-SMA-039 | explanation | `M1V1=M2V2, M2=10 x 50/100=5 M.` | `M₁V₁=M₂V₂, M₂=10 × 50/100=5 M.` |
| KIM-SMA-040 | explanation | `M1V1=M2V2, M2=12 x 50/100=6 M.` | `M₁V₁=M₂V₂, M₂=12 × 50/100=6 M.` |
| KIM-SMA-041 | question | `Gas ideal sebanyak 0.5 mol berada pada STP (0 C, 1 atm). Jika volume molar ideal 22,4 L/mol, volumenya adalah ...` | `Gas ideal sebanyak 0.5 mol berada pada STP (0 °C, 1 atm). Jika volume molar ideal 22,4 L/mol, volumenya adalah ...` |
| KIM-SMA-041 | explanation | `V = n x 22,4 = 0.5 x 22,4 = 11.2 L.` | `V = n × 22,4 = 0.5 × 22,4 = 11.2 L.` |
| KIM-SMA-042 | question | `Gas ideal sebanyak 1 mol berada pada STP (0 C, 1 atm). Jika volume molar ideal 22,4 L/mol, volumenya adalah ...` | `Gas ideal sebanyak 1 mol berada pada STP (0 °C, 1 atm). Jika volume molar ideal 22,4 L/mol, volumenya adalah ...` |
| KIM-SMA-042 | explanation | `V = n x 22,4 = 1 x 22,4 = 22.4 L.` | `V = n × 22,4 = 1 × 22,4 = 22.4 L.` |
| KIM-SMA-043 | question | `Gas ideal sebanyak 1.5 mol berada pada STP (0 C, 1 atm). Jika volume molar ideal 22,4 L/mol, volumenya adalah ...` | `Gas ideal sebanyak 1.5 mol berada pada STP (0 °C, 1 atm). Jika volume molar ideal 22,4 L/mol, volumenya adalah ...` |
| KIM-SMA-043 | explanation | `V = n x 22,4 = 1.5 x 22,4 = 33.6 L.` | `V = n × 22,4 = 1.5 × 22,4 = 33.6 L.` |
| KIM-SMA-044 | question | `Gas ideal sebanyak 2 mol berada pada STP (0 C, 1 atm). Jika volume molar ideal 22,4 L/mol, volumenya adalah ...` | `Gas ideal sebanyak 2 mol berada pada STP (0 °C, 1 atm). Jika volume molar ideal 22,4 L/mol, volumenya adalah ...` |
| KIM-SMA-044 | explanation | `V = n x 22,4 = 2 x 22,4 = 44.8 L.` | `V = n × 22,4 = 2 × 22,4 = 44.8 L.` |
| KIM-SMA-045 | question | `Gas ideal sebanyak 2.5 mol berada pada STP (0 C, 1 atm). Jika volume molar ideal 22,4 L/mol, volumenya adalah ...` | `Gas ideal sebanyak 2.5 mol berada pada STP (0 °C, 1 atm). Jika volume molar ideal 22,4 L/mol, volumenya adalah ...` |
| KIM-SMA-045 | explanation | `V = n x 22,4 = 2.5 x 22,4 = 56 L.` | `V = n × 22,4 = 2.5 × 22,4 = 56 L.` |
| KIM-SMA-046 | question | `Dalam reaksi 2H2 + O2 -> 2H2O, tersedia 1 mol O2 dan H2 berlebih. Jumlah H2O maksimum yang terbentuk adalah ...` | `Dalam reaksi 2H₂ + O₂ → 2H₂O, tersedia 1 mol O₂ dan H₂ berlebih. Jumlah H₂O maksimum yang terbentuk adalah ...` |
| KIM-SMA-046 | explanation | `Perbandingan O2 : H2O = 1 : 2, maka dihasilkan 2 mol H2O.` | `Perbandingan O₂ : H₂O = 1 : 2, maka dihasilkan 2 mol H₂O.` |
| KIM-SMA-047 | question | `Dalam reaksi 2H2 + O2 -> 2H2O, tersedia 2 mol O2 dan H2 berlebih. Jumlah H2O maksimum yang terbentuk adalah ...` | `Dalam reaksi 2H₂ + O₂ → 2H₂O, tersedia 2 mol O₂ dan H₂ berlebih. Jumlah H₂O maksimum yang terbentuk adalah ...` |
| KIM-SMA-047 | explanation | `Perbandingan O2 : H2O = 1 : 2, maka dihasilkan 4 mol H2O.` | `Perbandingan O₂ : H₂O = 1 : 2, maka dihasilkan 4 mol H₂O.` |
| KIM-SMA-048 | question | `Dalam reaksi 2H2 + O2 -> 2H2O, tersedia 3 mol O2 dan H2 berlebih. Jumlah H2O maksimum yang terbentuk adalah ...` | `Dalam reaksi 2H₂ + O₂ → 2H₂O, tersedia 3 mol O₂ dan H₂ berlebih. Jumlah H₂O maksimum yang terbentuk adalah ...` |
| KIM-SMA-048 | explanation | `Perbandingan O2 : H2O = 1 : 2, maka dihasilkan 6 mol H2O.` | `Perbandingan O₂ : H₂O = 1 : 2, maka dihasilkan 6 mol H₂O.` |
| KIM-SMA-049 | question | `Dalam reaksi 2H2 + O2 -> 2H2O, tersedia 4 mol O2 dan H2 berlebih. Jumlah H2O maksimum yang terbentuk adalah ...` | `Dalam reaksi 2H₂ + O₂ → 2H₂O, tersedia 4 mol O₂ dan H₂ berlebih. Jumlah H₂O maksimum yang terbentuk adalah ...` |
| KIM-SMA-049 | explanation | `Perbandingan O2 : H2O = 1 : 2, maka dihasilkan 8 mol H2O.` | `Perbandingan O₂ : H₂O = 1 : 2, maka dihasilkan 8 mol H₂O.` |
| KIM-SMA-050 | question | `Dalam reaksi 2H2 + O2 -> 2H2O, tersedia 5 mol O2 dan H2 berlebih. Jumlah H2O maksimum yang terbentuk adalah ...` | `Dalam reaksi 2H₂ + O₂ → 2H₂O, tersedia 5 mol O₂ dan H₂ berlebih. Jumlah H₂O maksimum yang terbentuk adalah ...` |
| KIM-SMA-050 | explanation | `Perbandingan O2 : H2O = 1 : 2, maka dihasilkan 10 mol H2O.` | `Perbandingan O₂ : H₂O = 1 : 2, maka dihasilkan 10 mol H₂O.` |
| MAT-SMP-002 | question | `Hasil dari 5 - 2 x 3 adalah ...` | `Hasil dari 5 - 2 × 3 adalah ...` |
| MAT-SMP-017 | question | `Dengan pi = 22/7, keliling lingkaran berjari-jari 7 cm adalah ...` | `Dengan π = 22/7, keliling lingkaran berjari-jari 7 cm adalah ...` |
| MAT-SMP-019 | question | `Volume balok 3 cm x 4 cm x 5 cm adalah ...` | `Volume balok 3 cm × 4 cm × 5 cm adalah ...` |
| MAT-SMP-026 | explanation | `Harga akhir = 80000 x (100-10)/100 = 72000 rupiah.` | `Harga akhir = 80000 × (100-10)/100 = 72000 rupiah.` |
| MAT-SMP-027 | explanation | `Harga akhir = 120000 x (100-20)/100 = 96000 rupiah.` | `Harga akhir = 120000 × (100-20)/100 = 96000 rupiah.` |
| MAT-SMP-028 | explanation | `Harga akhir = 160000 x (100-25)/100 = 120000 rupiah.` | `Harga akhir = 160000 × (100-25)/100 = 120000 rupiah.` |
| MAT-SMP-029 | explanation | `Harga akhir = 200000 x (100-30)/100 = 140000 rupiah.` | `Harga akhir = 200000 × (100-30)/100 = 140000 rupiah.` |
| MAT-SMP-030 | explanation | `Harga akhir = 250000 x (100-40)/100 = 150000 rupiah.` | `Harga akhir = 250000 × (100-40)/100 = 150000 rupiah.` |
| MAT-SMP-041 | explanation | `Q = 6 x 4/2 = 12 buku.` | `Q = 6 × 4/2 = 12 buku.` |
| MAT-SMP-042 | explanation | `Q = 12 x 5/3 = 20 buku.` | `Q = 12 × 5/3 = 20 buku.` |
| MAT-SMP-043 | explanation | `Q = 20 x 6/4 = 30 buku.` | `Q = 20 × 6/4 = 30 buku.` |
| MAT-SMP-044 | explanation | `Q = 30 x 7/5 = 42 buku.` | `Q = 30 × 7/5 = 42 buku.` |
| MAT-SMP-045 | explanation | `Q = 42 x 8/6 = 56 buku.` | `Q = 42 × 8/6 = 56 buku.` |
| MAT-SMP-046 | explanation | `L = (a+b) x t / 2 = (4+8) x 4 / 2 = 24 cm².` | `L = (a+b) × t / 2 = (4+8) × 4 / 2 = 24 cm².` |
| MAT-SMP-047 | explanation | `L = (a+b) x t / 2 = (5+9) x 6 / 2 = 42 cm².` | `L = (a+b) × t / 2 = (5+9) × 6 / 2 = 42 cm².` |
| MAT-SMP-048 | explanation | `L = (a+b) x t / 2 = (6+10) x 8 / 2 = 64 cm².` | `L = (a+b) × t / 2 = (6+10) × 8 / 2 = 64 cm².` |
| MAT-SMP-049 | explanation | `L = (a+b) x t / 2 = (7+11) x 10 / 2 = 90 cm².` | `L = (a+b) × t / 2 = (7+11) × 10 / 2 = 90 cm².` |
| MAT-SMP-050 | explanation | `L = (a+b) x t / 2 = (8+12) x 12 / 2 = 120 cm².` | `L = (a+b) × t / 2 = (8+12) × 12 / 2 = 120 cm².` |
| MAT-SMA-006 | question | `Solusi 3^(x+1) = 81 adalah ...` | `Solusi 3ˣ⁺¹ = 81 adalah ...` |
| MAT-SMA-008 | option:B | `sqrt(3)/2` | `√3/2` |
| MAT-SMA-009 | option:B | `sqrt(3)/2` | `√3/2` |
| MAT-SMA-026 | explanation | `Un = a1 + (n-1)d = 3 + (6-1) x 2 = 13.` | `Uₙ = a₁ + (n-1)d = 3 + (6-1) × 2 = 13.` |
| MAT-SMA-027 | explanation | `Un = a1 + (n-1)d = 4 + (7-1) x 3 = 22.` | `Uₙ = a₁ + (n-1)d = 4 + (7-1) × 3 = 22.` |
| MAT-SMA-028 | explanation | `Un = a1 + (n-1)d = 5 + (8-1) x 4 = 33.` | `Uₙ = a₁ + (n-1)d = 5 + (8-1) × 4 = 33.` |
| MAT-SMA-029 | explanation | `Un = a1 + (n-1)d = 6 + (9-1) x 5 = 46.` | `Uₙ = a₁ + (n-1)d = 6 + (9-1) × 5 = 46.` |
| MAT-SMA-030 | explanation | `Un = a1 + (n-1)d = 7 + (10-1) x 6 = 61.` | `Uₙ = a₁ + (n-1)d = 7 + (10-1) × 6 = 61.` |
| MAT-SMA-031 | explanation | `Un = a1 r^(n-1) = 2 x 2^(3-1) = 8.` | `Uₙ = a₁ rⁿ⁻¹ = 2 × 2³⁻¹ = 8.` |
| MAT-SMA-032 | explanation | `Un = a1 r^(n-1) = 3 x 3^(4-1) = 81.` | `Uₙ = a₁ rⁿ⁻¹ = 3 × 3⁴⁻¹ = 81.` |
| MAT-SMA-033 | explanation | `Un = a1 r^(n-1) = 4 x 2^(3-1) = 16.` | `Uₙ = a₁ rⁿ⁻¹ = 4 × 2³⁻¹ = 16.` |
| MAT-SMA-034 | explanation | `Un = a1 r^(n-1) = 5 x 3^(4-1) = 135.` | `Uₙ = a₁ rⁿ⁻¹ = 5 × 3⁴⁻¹ = 135.` |
| MAT-SMA-035 | explanation | `Un = a1 r^(n-1) = 6 x 2^(3-1) = 24.` | `Uₙ = a₁ rⁿ⁻¹ = 6 × 2³⁻¹ = 24.` |
| MAT-SMA-046 | explanation | `Integral ax dx = ax²/2. Nilai 0..4 = 2 x 4²/2 = 16.` | `Integral ax dx = ax²/2. Nilai 0..4 = 2 × 4²/2 = 16.` |
| MAT-SMA-047 | explanation | `Integral ax dx = ax²/2. Nilai 0..6 = 3 x 6²/2 = 54.` | `Integral ax dx = ax²/2. Nilai 0..6 = 3 × 6²/2 = 54.` |
| MAT-SMA-048 | explanation | `Integral ax dx = ax²/2. Nilai 0..8 = 4 x 8²/2 = 128.` | `Integral ax dx = ax²/2. Nilai 0..8 = 4 × 8²/2 = 128.` |
| MAT-SMA-049 | explanation | `Integral ax dx = ax²/2. Nilai 0..10 = 5 x 10²/2 = 250.` | `Integral ax dx = ax²/2. Nilai 0..10 = 5 × 10²/2 = 250.` |
| MAT-SMA-050 | explanation | `Integral ax dx = ax²/2. Nilai 0..12 = 6 x 12²/2 = 432.` | `Integral ax dx = ax²/2. Nilai 0..12 = 6 × 12²/2 = 432.` |
