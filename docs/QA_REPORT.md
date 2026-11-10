# Laporan QA Duelmu 0.4.0 (560 soal, mini-game, ghost multiplayer)

Tanggal: 10 November 2026 · Lingkungan: Linux x86_64 (ChromeOS Crostini), Temurin JDK 21.0.13, Gradle 9.8.2,
AGP 9.5.1, Kotlin 2.4.21, Android SDK cmdline-tools (platform 37 diunduh AGP) · **Tanpa emulator/HP fisik.**

| Pemeriksaan | Perintah | Hasil nyata |
|---|---|---|
| Unit test JVM/Robolectric (sebelum perubahan) | `gradlew :app:testDebugUnitTest` | 83 tes, 0 gagal (baseline commit 4607b47) |
| Unit test JVM/Robolectric (0.4.0) | `gradlew :app:testDebugUnitTest` | **118 tes: 117 lulus, 0 gagal, 1 dilewati (uji asap perangkat manual `LanDeviceSmokeTest`, butuh `EDUDUEL_LAN_TARGET`)** |
| Android Lint | `gradlew :app:lintDebug` | **0 error, 5 warning (3 string lama tidak terpakai, OldTargetApi, versi AGP)** |
| Build debug | `gradlew :app:assembleDebug` | **OK, `app-debug.apk` 25.007.741 byte (23,85 MiB), SHA-256 `be6d393d…0112d6`** |
| Build APK instrumented test | `gradlew :app:assembleDebugAndroidTest` | OK (sumber androidTest terkompilasi); **tidak dijalankan: tidak ada emulator/HP** |
| 400 soal klasik vs PDF/CSV | `python tools/validate_question_bank.py` | ERROR 0, WARNING 14 (sumber, sudah dikoreksi di asset) |
| 80 soal bergambar vs ZIP/PDF | `python tools/validate_visual_pack.py` | 5/5 LULUS, termasuk **pasangan piksel gambar PDF == PNG untuk 80/80 ID** |
| 80 soal game + bank 560 | `python tools/validate_game_pack.py` | 4/4 LULUS, 0 error, 0 peringatan kemiripan |
| Asset & generator sinkron | `build_question_asset.py --check`, `generate_game_questions.py --check` | OK, bankSha256 `67217b6d…861da` |
| Isi APK | `unzip -l app-debug.apk` | 80 PNG di `assets/images/`; `assets/questions.json` identik (SHA-256) dengan sumber |

Tes baru yang relevan:

- `QuestionBankDatasetTest` (14): 560 = 400/80/80, 140/mapel, 70/mapel/jenjang, rentang ID per tipe, **400 soal lama
  identik dengan asset v1** (sidik jari kanonik), 80 soal bergambar == sumber ZIP & PNG di APK byte-identik +
  dapat didekode ImageIO 1250×600, deteksi PNG hilang/rusak, 80 game: parse + jenis sesuai mapel + solusi valid,
  decoder ketat menolak field asing, schema 1 tetap bisa dimuat.
- `GameEngineTest` (11): setiap dari 80 game menang/kalah/timeout/replay deterministik; Asteroid urutan token &
  nyawa, spawn per seed; Trajectory radius toleransi, dua sudut berkomplemen sah, rintangan, batas tembakan,
  setiap konfigurasi punya solusi tetapi tidak trivial; Micro Hunter fokus/zoom/hotspot/bantuan sentuh,
  koordinat dunia independen resolusi, 20 ilustrasi berbeda; Reaction Lab pH ekuivalen = 7, pH 2 = 40,9 mL,
  kekekalan massa Mg + 2HCl, pereaksi pembatas NH₃, M₁V₁ = M₂V₂, RESET, batas kapasitas.
- `ModeSelectionTest` (5) & `SoloGameTest` (2): ukuran pool per mode, Campuran berstrata 4/3/3, seed ronde game,
  tutorial menahan timer, skor game = rumus kuis, snapshot.
- `GhostMultiplayerTest` (9, Host + 3 Tamu SUNGGUHAN lewat codec frame): **A melihat B/C/D, B melihat A/C/D,
  C melihat A/B/D, D melihat A/B/C** (Host ikut tampil bagi Tamu), event aksi di-relay, ghost tidak mengubah state
  lokal, Host memutar ulang submisi, FAIR menyensor tetapi tetap menampilkan ketiga ghost, klien nakal disensor
  Host + identitas palsu diganti + rate-limit spam, tutorial tidak deadlock, pertandingan Campuran penuh konsisten
  di 4 HP, putus koneksi saat game, GhostHub membuang out-of-order/stale/ghost sendiri, interpolasi jitter.
- `ProtocolCodecTest` (14, +2 baru): round-trip semua payload v3, snapshot ghost < 400 byte, v2 ditolak, setup/start tanpa
  kunci/solusi.

Belum diuji (langkah manual):

1. `connectedDebugAndroidTest` dan uji UI di emulator/HP (tidak ada emulator di lingkungan ini).
2. Room nyata 2–4 HP lewat Wi-Fi/hotspot dan Bluetooth dengan mini-game (FPS, sentuhan, ghost 5–8 Hz di perangkat
   nyata). Logika relay & ghost hanya diuji dengan sesi sungguhan di JVM (in-memory + codec yang sama).
3. Build release bertanda tangan (butuh `keystore.properties` milik pengguna).
4. Tinjauan guru untuk ilustrasi preparat biologi (sudah ditinjau visual saat pengembangan; diberi `editorialFlags`).

---

# Laporan QA Duelmu 0.2.0 (room 2–4 pemain)

Tanggal: 8 November 2026 · Lingkungan: Windows 11, JBR 21, Gradle 9.8.2, AGP 9.5.1, Kotlin 2.4.21,
compileSdk 37 / targetSdk 36 / minSdk 26 · Emulator Android 15 (API 35, x86_64).

Perubahan sejak 0.1.0: room maks. 4 pemain (Host + 3 Tamu), jalur Wi-Fi/hotspot (TCP + NSD) selain
Bluetooth, 10 soal acak per pertandingan dari mapel terpilih, jenjang/kesulitan hanya label, protokol v2,
migrasi riwayat v1→v2. Lihat [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md#perubahan-v020-8-nov-2026-room-24-pemain).

## 1. Ringkasan hasil nyata

| Pemeriksaan | Perintah | Hasil |
|---|---|---|
| Validasi dataset | `python tools/validate_question_bank.py` | **ERROR 0**, WARNING 14 (semua di sumber, sudah dikoreksi di asset) |
| Asset sinkron | `python tools/build_question_asset.py --check` | OK, bankSha256 `32cf858e…05d8a` |
| Unit test JVM/Robolectric | `gradlew :app:testDebugUnitTest` | **73 tes: 72 lulus, 0 gagal, 1 dilewati** (uji asap perangkat manual, lihat §3) |
| Instrumented UI test (emulator API 35) | `gradlew :app:connectedDebugAndroidTest` | **1 lulus, 0 gagal** |
| Android Lint | `gradlew :app:lintDebug` | 0 error, 2 warning disengaja (§5) |
| Build debug | `gradlew :app:assembleDebug` | OK, `app-debug.apk` 22,0 MB |
| Build release (R8, belum ditandatangani) | `gradlew :app:assembleRelease` | OK, `app-release-unsigned.apk` 2,66 MB (termasuk logo) |
| AAB | `gradlew :app:bundleRelease` | OK, `app-release.aab` 5,3 MB |
| Izin di APK release | `aapt2 dump permissions` | INTERNET (socket lokal), Bluetooth (+lokasi `maxSdk=30`), VIBRATE |
| Firebase/GMS/HTTP di APK | `GuestOnlyPolicyTest` + isi build | Tidak ada |

## 2. Cakupan unit test

| Kelas | Jumlah | Yang diuji |
|---|---:|---|
| `RoomSessionTest` | 21 | Host + 3 Tamu SUNGGUHAN lewat frame codec in-memory + waktu virtual: 4 pemain melihat semua nickname & `playerSessionId` acak; pemain ke-5 ditolak ROOM_FULL; kode room salah & bank berbeda ditolak tanpa mengganggu room; nama identik; Mulai butuh ≥1 Tamu dan semua SIAP; ubah nama sebelum SIAP terpropagasi ke semua (terkunci setelahnya); ganti mapel mereset SIAP, Campuran tersampaikan; Keluarkan pemain; bergabung saat main ditolak; **pertandingan penuh 4 pemain: 10 soal unik semuanya Matematika, soal/urutan opsi/hasil identik di 4 HP**, peringkat berbagi saat skor sama; rumus skor per pemain; semua timeout = 0; jawaban terkunci; Tamu putus → pemain lain lanjut, absen 0 poin, tanpa kemenangan otomatis; Tamu menyambung ulang ≤30 dtk → ikut lagi soal berikutnya dengan hasil konsisten; semua Tamu keluar → batal tanpa pemenang; Host keluar (lobi & saat main); Main Lagi → lobi, soal pertandingan kedua berbeda, nama baru tersimpan; pesan duplikat/out-of-order/telat diabaikan; frame rusak tidak crash |
| `LanTransportTest` | 2 | **Socket TCP sungguhan (loopback)** dengan `LanController` aplikasi: kode salah ditolak lewat jaringan, Tamu masuk lobi, mapel tersampaikan, Mulai → soal pertama identik di kedua sisi; parsing IP manual |
| `LanDeviceSmokeTest` | 1 | Manual (dilewati tanpa variabel lingkungan): Tamu JVM masuk room di HP/emulator sungguhan |
| `ProtocolCodecTest` | 12 | Round-trip HELLO (tanpa UID/MAC), paket terpotong, byte-per-byte, dua frame sekali baca, frame terlalu besar, batas 64 KiB, panjang 0, JSON/UTF-8 rusak, tipe tak dikenal, versi lama (v1) ditolak, QUESTION_START & MATCH_SETUP tanpa kunci, ROOM_STATE 4 pemain & Campuran selamat encode/decode, stream via pipe + EOF |
| `NicknameValidatorTest` | 9 | trim, kosong, 2–20 grapheme, kontrol/bidi, emoji ZWJ, pemotongan aman, nama dari jaringan, aksara non-Latin |
| `QuestionBankDatasetTest` | 8 | 400 soal, 100/mapel, ID konsisten, 4 opsi unik, waktu mengikuti kesulitan, pembahasan opsional, SHA-256 menolak bank yang diubah |
| `QuestionSelectorTest` | 3 | Deterministik per seed; menghindari soal lama; **setiap mapel: pool 100 soal campuran SMP/SMA & semua kesulitan, 10 soal unik dari mapel itu, seed berbeda → set berbeda; Campuran = 400** |
| `ScoringEngineTest` / `SoloEngineTest` / `MathTextTest` | 4 / 4 / 1 | Rumus skor; latihan solo (terkunci, timeout, snapshot/restore); fallback superscript |
| `HistoryRepositoryTest` | 3 | Snapshot nickname per pertandingan, statistik, **peringkat & semua peserta tersimpan**, hapus riwayat |
| `HistoryMigrationTest` | 1 | DB skema v1 (dibuat dari `schemas/.../1.json`) berisi riwayat 0.1.0 → dibuka Room dengan `MIGRATION_1_2` → data lama utuh |
| `GuestOnlyPolicyTest` | 4 | Tanpa Firebase/Google Services/Auth/Firestore/rute Login-Profil; **jaringan hanya lokal: tanpa klien HTTP, URL server, analitik, iklan**; cleartext HTTP diblokir; lokasi hanya API ≤30 + `neverForLocation` |

Instrumented `GuestFlowTest`: buka aplikasi → langsung Beranda (tanpa Login/Daftar) → Main Bareng → nama
kosong ditolak → "Uji 🚀" → Buat/Gabung Room tampil → demo 3 bot → lobi "Pemain (4/4)" → Ubah Nama →
"Uji Dua" → tidak ada pilihan jenjang/kesulitan/jumlah soal → pilih Matematika ("10 soal diambil acak dari
100 soal Matematika") → Mulai → "Soal 1 dari 10" berlabel Matematika.

## 3. Uji manual di emulator (API 35)

Diverifikasi dengan tangkapan layar (tidak disimpan di repo):
- **Room demo 4 pemain**: lobi menampilkan Host + 3 bot (SIAP), slot kosong, pemilih mapel (Fisika/Biologi/
  Kimia/Matematika/Campuran); setelah Ubah Nama dan ganti mapel, Mulai → soal 1/10 "Matematika · SMA · Sulit"
  dengan papan skor 4 pemain dan penghitung jawaban.
- **Room Wi-Fi sungguhan**: aplikasi di emulator menjadi Host (**Buat Room → Wi-Fi / Hotspot**): lobi
  menampilkan kode room, IP Host, dan tombol Muat ulang IP. Tamu = `RoomGuestSession` di PC lewat
  `adb forward tcp:47830` (socket TCP sungguhan ke server room aplikasi). Hasil: Tamu masuk dan muncul di lobi
  Host (snackbar "Tamu JVM masuk ke room", tombol Keluarkan), Tamu SIAP → Host menekan Mulai → 2 soal Fisika
  (FIS-SMP-034 Sedang, FIS-SMA-021 Sulit) dengan urutan opsi sama; skor di kedua sisi identik (280/297, 0/447);
  Tamu keluar → Host membatalkan pertandingan "Tidak ada pemain lain yang tersisa. Tidak ada pemenang" dan
  menawarkan Main Lagi.
- Bug yang ditemukan & diperbaiki selama uji: (1) Host menunggu tombol Lanjut dari Tamu yang baru tersambung
  ulang (macet sampai 6 dtk); (2) Tamu menurunkan status CANCELLED menjadi FAILED saat DISCONNECT datang
  setelah MATCH_END; (3) ganti mapel diterbitkan dalam dua pembaruan state sehingga tombol Mulai sempat tampak
  aktif lalu ditolak. Ketiganya kini tercakup tes.

## 3b. Logo Duelmu (kickstart/Duelmu Logo.png)

Diterapkan sebagai ikon adaptif launcher (5 densitas, latar putih, plus lapisan monochrome dari garis tinta
logo untuk ikon bertema Android 13+), ikon splash di atas ungu logo, logo di Beranda dan Pengaturan → Tentang,
serta ikon Play Store 512 px. Latar putih di luar logo dibuat transparan agar rapi di mode gelap. Diverifikasi
di emulator: ikon di laci aplikasi, splash, Beranda; UI test lulus. Ukuran APK release naik ±0,7 MB (gambar logo).

## 4. Fitur 0.1.0 yang tetap

Latihan Solo (kini juga hanya pilihan mapel, 10 soal acak), validasi nama, riwayat lokal, pengaturan, dark
mode, timer monotonic yang tidak reset saat rotasi/ganti tema, alur izin Bluetooth: tidak berubah perilakunya
dan tetap diuji oleh tes di atas.

## 5. Warning yang disengaja

- `OldTargetApi`: `targetSdk = 36` sesuai kebijakan Google Play saat ini. Menaikkan ke 37 butuh uji perubahan
  perilaku Android 17, termasuk perlindungan akses jaringan lokal yang berdampak pada room Wi-Fi
  (lihat [KONEKSI_ID.md](KONEKSI_ID.md) §2).
- `AndroidGradlePluginVersion`: Gradle 9.9.0 baru dirilis 6 November 2026; proyek memakai 9.8.2.
- `PluralsCandidate` dinonaktifkan di `app/lint.xml` (bahasa Indonesia tanpa bentuk jamak gramatikal).

## 6. Belum diuji (keterbatasan lingkungan): WAJIB sebelum rilis

| Item | Alasan | Cara menguji |
|---|---|---|
| Room Wi-Fi/hotspot dengan 3–4 HP fisik (termasuk penemuan room via NSD dan IP hotspot) | Hanya satu emulator (RAM ±1,3 GB bebas); emulator tidak punya Wi-Fi bersama/hotspot sungguhan, NSD antar-perangkat tidak teruji | [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) bagian C, [GO_LIVE](GO_LIVE_FIREBASE_SPARK_ID.md) langkah 8A |
| Router sekolah dengan *client isolation* / mDNS diblokir | Bergantung jaringan setempat | Coba di Wi-Fi sekolah; bila gagal, pakai hotspot HP Host |
| Room Bluetooth 2–3 HP (pairing, beberapa koneksi RFCOMM bersamaan) | Tidak ada HP fisik terotorisasi (HP USB berstatus `unauthorized`) | GO_LIVE langkah 8B |
| Alur izin Android 8–11 | Emulator hanya API 35 | HP Android 8–11 |
| Background/Doze lama di tengah pertandingan | Perilaku vendor berbeda | HP fisik, layar mati 30–60 dtk |
| Update nyata 0.1.0 → 0.2.0 di HP | Migrasi DB diuji di Robolectric, belum di perangkat | Pasang 0.1.0, main, lalu update ke 0.2.0 dengan kunci yang sama |
| TalkBack menyeluruh | Hanya label semantik yang diverifikasi | Aktifkan TalkBack |
| Release bertanda tangan & App Distribution/Play | Tidak ada keystore/akun developer | [BUILD_APK_ID.md](BUILD_APK_ID.md), GO_LIVE |

## 7. Backlog

- Enkripsi room Wi-Fi (mis. kunci turunan kode room) bila dibutuhkan untuk jaringan tidak tepercaya.
- Host berpindah otomatis bila HP Host keluar (saat ini pertandingan dibatalkan).
- Layar detail riwayat per soal (data sudah disimpan di `roundsJson`).
- Suara khusus (saat ini `ToneGenerator` sistem); lokalisasi tambahan.
