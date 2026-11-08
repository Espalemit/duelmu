# EduDuel — Paket Bank Soal dan Prompt Claude Code

**Tanggal:** 7 November 2026  
**Bahasa:** Indonesia  
**Target:** Aplikasi Android offline-first dengan duel 1 vs 1 via Bluetooth Classic dan layanan akun opsional Firebase Spark Plan.

## Isi paket

| File | Kegunaan |
|---|---|
| `bank_soal_duel_400.pdf` | 400 soal pilihan ganda dan lampiran kunci jawaban dalam format baca/cetak. |
| `bank_soal_duel_400.json` | Dataset siap impor Android dengan ID, jenjang, mapel, topik, 4 opsi, kunci, timer, dan penjelasan soal numerik. |
| `bank_soal_duel_400.csv` | Pemeriksaan dan pengeditan data menggunakan spreadsheet. |
| `PROMPT_CLAUDE_CODE_EDUDUEL.md` | Instruksi implementasi penuh untuk Claude Code, termasuk Bluetooth, alur Guest-only (tanpa login), Firebase Spark, testing, dan panduan go-live. |

## Ringkasan dataset

- **Total:** 400 soal, terdiri dari 100 Fisika + 100 Biologi + 100 Kimia + 100 Matematika.
- **Jenjang:** masing-masing mapel memuat 50 SMP dan 50 SMA.
- **Setiap soal:** satu pertanyaan, tepat empat pilihan A/B/C/D, dan satu kunci jawaban.
- **Batas waktu:** 60 detik (mudah), 90 detik (sedang), 120 detik (sulit).
- **Sumber:** pertanyaan baru yang disusun berdasarkan materi umum dan kerangka sumber terbuka; lihat daftar referensi di PDF. Bukan kompilasi soal asli yang disalin dari situs.
- **Validasi:** struktur dataset, ID, jumlah, opsi, dan kesesuaian kunci diperiksa otomatis. Soal hitungan memakai rumus terprogram. Validasi kurikulum dan konsep oleh guru tetap disarankan sebelum rilis publik.

## Cara menjalankan Claude Code

1. Ekstrak ZIP paket ini ke folder proyek baru. Pastikan keempat file utama di atas berada dalam satu folder.
2. Buka folder tersebut menggunakan VS Code atau terminal.
3. Jalankan `claude` di direktori ini (setelah Claude Code terpasang dan akun diotorisasi).
4. Tempel seluruh isi `PROMPT_CLAUDE_CODE_EDUDUEL.md` ke Claude Code, atau instruksikan Claude Code: **"Baca dan jalankan seluruh instruksi di PROMPT_CLAUDE_CODE_EDUDUEL.md menggunakan bank soal PDF, JSON, dan CSV di direktori ini."**
5. Setelah source code tersedia, buka project Android di Android Studio dan jalankan build Android utama yang **tidak memerlukan Firebase atau login**.
6. Uji pada dua HP fisik: tiap pemain memilih Tantang Teman, mengisi/mengedit nickname, kemudian memilih Host atau Join dan mulai duel.
7. Buat project Firebase Spark untuk mendistribusikan beta via App Distribution. **Tidak perlu `google-services.json` jika tidak mengintegrasikan Firebase SDK ke aplikasi.** Untuk publik, gunakan Google Play Console secara terpisah.

## Catatan Firebase dan distribusi

- Firebase Spark gratis untuk layanan dan kuota tertentu, tetapi bukan VPS untuk proses Android dan tidak mem-publish APK melalui Firebase Hosting.
- Duel via Bluetooth Classic berjalan langsung antardua HP; **seluruh pemain Guest**, tanpa Firebase Auth, login, Firestore, atau kuota database. Nickname tiap duel dan riwayat disimpan lokal.
- Firebase App Distribution adalah untuk penguji/beta. Publikasi umum di Google Play memakai Google Play Console dan persyaratannya sendiri.
- Jika hendak menambah fitur skor online anti-curang atau pertandingan jarak jauh, diperlukan arsitektur tepercaya tambahan yang belum termasuk MVP Guest-only Spark.

## Referensi resmi

- https://firebase.google.com/pricing
- https://firebase.google.com/docs/android/setup
- https://firebase.google.com/docs/app-distribution/android/distribute-console
- https://firebase.google.com/docs/firestore/pricing
- https://developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices
- https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
- https://support.google.com/googleplay/android-developer/answer/6112435?hl=id-ID

