# Rencana Implementasi Duelmu (ringkas)

## Audit input (7 Nov 2026)

| File | Temuan |
|---|---|
| `kickstart/bank_soal_duel_400.json` | 400 soal, schema 1.0.0, 150 soal parametrik berpembahasan, 250 soal konsep tanpa pembahasan. |
| `kickstart/bank_soal_duel_400.csv` | 400 baris, UTF-8 BOM, kolom identik JSON. |
| `kickstart/bank_soal_duel_400.pdf` | 77 halaman, teks dapat diekstrak (`pdftotext -raw`), lampiran kunci 400 baris. |
| Lingkungan | Windows 11, Android SDK `F:\AndroidSDK`, JBR 21 (Android Studio), Python 3.13 + poppler. Tidak ada perangkat fisik/emulator tersambung. |

Distribusi kesulitan tidak merata: jenjang SMA **tidak memiliki** soal *mudah*; Fisika/Biologi SMP tidak memiliki soal *sulit*.

## Arsitektur

```
app/src/main/java/id/eduduel/app/
├── core/          Validasi nickname (grapheme), jam monotonic, hash, format matematika
├── domain/        Model tipe-kuat, filter, pemilih soal ber-seed, mesin skor, sesi solo
├── data/          Bank soal dari asset (verifikasi SHA-256), Room (riwayat), DataStore (pengaturan)
├── bluetooth/     Protokol JSON + frame 4-byte, koneksi stream, RFCOMM, state machine duel
└── presentation/  Compose Material 3: navigasi, tema, layar, ViewModel
```

- **Tanpa Firebase SDK, tanpa server.** (Sejak v0.2: izin INTERNET hanya untuk socket lokal room Wi-Fi.) Firebase Spark hanya untuk App Distribution dan Hosting statis opsional (di luar APK).
- **Guest-only:** tidak ada layar akun. Nickname diminta tepat sebelum tiap duel; `playerSessionId` acak per sesi.
- **Host otoritatif:** Host memilih soal (seed acak), urutan opsi, menutup ronde dan menghitung skor; Join memverifikasi bank, ID, dan konsistensi skor.
- **State machine duel** berbasis antrean event tunggal (tanpa race), dapat diuji dengan koneksi in-memory dan waktu virtual.

## Urutan kerja

1. Validator dataset + laporan QA (`tools/validate_question_bank.py`, `docs/QA_DATASET_REPORT.md`).
2. Build step asset reproduksibel + koreksi terdokumentasi (`tools/build_question_asset.py`, `docs/DATA_ISSUES.md`).
3. Bootstrap Gradle (AGP 9.5.1, Gradle 9.8.2, Kotlin 2.4.21, compileSdk 37/targetSdk 36/minSdk 26).
4. Domain + solo + skor + timer.
5. Protokol Bluetooth, framing, sesi duel end-to-end, reconnect best-effort.
6. UI duel/lobi/hasil, Room/DataStore.
7. Alur Guest-only final; dokumentasi Firebase Spark (distribusi saja).
8. Unit test (dataset, skor, nickname, framing, protokol, duel 10 ronde dua sesi).
9. Dokumentasi go-live, privasi, rilis.
10. Jalankan build/test/lint; laporkan hasil nyata.

## Perubahan v0.2.0 (8 Nov 2026): room 2–4 pemain

Permintaan: (1) 10 soal acak per pertandingan dari mapel yang dipilih; (2) full offline lewat Bluetooth atau
Wi-Fi yang sama; (3) room maks. 4 pemain, mulai setelah semua siap; (4) jenjang & kesulitan hanya label.

| Keputusan | Alasan |
|---|---|
| `MatchConfig` hanya `subject` (null = Campuran), jumlah soal tetap 10 | Sesuai permintaan; Campuran dipertahankan sebagai opsi tambahan |
| Kesulitan tetap menentukan durasi & poin soal | Spesifikasi awal (60/90/120 dtk, 100/200/300 poin); hanya bukan filter lagi |
| Topologi bintang, Host = pemain + pusat | Satu sumber kebenaran untuk soal, waktu, dan skor; mudah diverifikasi Tamu |
| Wi-Fi/hotspot: TCP + NSD, port 47830, IP manual sebagai cadangan | Tanpa internet; NSD kadang diblokir router/hotspot sehingga IP selalu ditampilkan |
| Kode room 4 digit (Wi-Fi) + tombol Keluarkan | Mencegah orang lain di jaringan yang sama ikut masuk |
| Bluetooth: satu server socket, accept berulang | RFCOMM mendukung beberapa koneksi; praktis 2–3 pemain |
| Protokol v2 (`ROOM_STATE`, hasil per `playerSessionId`) | v1 khusus 1 lawan 1; versi lama ditolak jelas |
| Tamu putus: lanjut tanpa dia, 0 poin selama absen, reconnect 30 dtk | Satu pemain tidak boleh menghentikan 3 pemain lain; tetap tanpa kemenangan otomatis |
| Migrasi Room v1→v2 (kolom peringkat & peserta) | Riwayat pengguna 0.1.0 tidak hilang |
| `bluetooth/` → `net/`, `presentation/duel` → `presentation/room` | Paket kini mencakup Wi-Fi juga |
