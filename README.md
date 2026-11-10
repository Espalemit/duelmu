# Duelmu

Aplikasi Android kuis edukasi **main bareng 2–4 pemain dalam satu room**, sepenuhnya offline dan **tanpa akun**.
Pemain bersaing menjawab soal yang sama (urutan dan pilihan jawaban sama) dari **560 soal** Fisika, Biologi,
Kimia, dan Matematika SMP–SMA: 400 soal klasik, 80 soal **bergambar**, dan 80 tantangan **mini-game**
(Matematika = Asteroid Solver, Fisika = Trajectory Lab, Biologi = Micro Hunter, Kimia = Reaction Lab).
Setiap pemain adalah Guest yang mengetik nama panggilan tepat sebelum bermain.

- **Room maks. 4 pemain**: satu HP menjadi Host, maks. 3 Tamu. Mulai setelah semua Tamu SIAP.
- **Koneksi lokal tanpa internet**: Wi-Fi yang sama / hotspot salah satu HP (TCP + NSD), atau Bluetooth Classic RFCOMM.
- **Setiap pertandingan 10 soal acak** dari mapel yang dipilih Host (atau Campuran) dan **jenis soal**: Kuis Klasik
  (default), Kuis Bergambar, Mini-Game, atau Campuran Tipe. Jenjang & kesulitan hanya label di soal.
- **Ghost multiplayer simetris** di mini-game: setiap HP melihat semua pemain lain (termasuk Host) sebagai ghost
  semitransparan; Host me-relay state ke semua Tamu. Mode **LIVE** (default) atau **FAIR** (label/parameter disembunyikan).
- Kotlin + Jetpack Compose + Material 3, single-activity, Navigation Compose (rute type-safe)
- Room (riwayat lokal) + DataStore (pengaturan, nama terakhir)
- Tanpa Firebase SDK, tanpa `google-services.json`, tanpa server. Izin `INTERNET` hanya untuk socket lokal antar-HP
- Firebase Spark hanya untuk distribusi beta (App Distribution) dan halaman privasi statis (Hosting), di luar APK

## Mulai cepat

Panduan langkah demi langkah (Android Studio atau command line, APK debug & rilis bertanda tangan,
memasang ke HP): **[docs/BUILD_APK_ID.md](docs/BUILD_APK_ID.md)**.

```bat
:: Windows
gradlew.bat :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```
```bash
# Linux/macOS
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Coba di satu HP/emulator: **Main Bareng → isi nama → Coba dengan 3 Bot (demo)**.
Main sungguhan: satu HP **Buat Room** (Wi-Fi/Hotspot atau Bluetooth), HP lain **Gabung Room**.

## Perintah penting

| Tujuan | Perintah |
|---|---|
| Unit test | `./gradlew :app:testDebugUnitTest` |
| UI test di emulator/HP | `./gradlew :app:connectedDebugAndroidTest` |
| Lint | `./gradlew :app:lintDebug` |
| APK/AAB release | `./gradlew :app:assembleRelease :app:bundleRelease` (signing: `keystore.properties`, lihat contoh) |
| Validasi 400 soal klasik + laporan | `python tools/validate_question_bank.py` |
| Validasi 80 soal bergambar vs ZIP/PDF (termasuk piksel gambar) | `python tools/validate_visual_pack.py --report docs/QA_VISUAL_REPORT.md` |
| Generator 80 soal mini-game (membuktikan solusi) | `python tools/game_content/generate_game_questions.py` (`--check`, `--svg DIR`) |
| Validasi game + laporan 560 | `python tools/validate_game_pack.py --report docs/CONTENT_560_QA.md` |
| Bangun ulang asset soal (560 + gambar) | `python tools/build_question_asset.py` (`--check` untuk CI) |

## Struktur

```
app/src/main/
├── assets/questions.json          # 560 soal (schema 2), dihasilkan oleh tools/build_question_asset.py
├── assets/images/*.png            # 80 ilustrasi soal bergambar (hash di assetManifest bank)
└── java/id/eduduel/app/
    ├── core/                      # validasi nickname (grapheme), jam monotonic, hash, MathText
    ├── domain/                    # model (QuestionType, McqBody/GameBody), MatchConfig (mapel+mode+ghost), Grader, SoloEngine
    │   └── game/                  # 4 mesin mini-game deterministik + konfigurasi (Asteroid/Trajectory/Microscope/Lab)
    ├── data/                      # QuestionBankLoader (cek SHA-256), Room history (migrasi v1→v2), DataStore
    ├── net/
    │   ├── protocol/              # pesan v3 (+GAME_SUBMIT/GAME_STATE_UPDATE/GAME_ACTION), envelope, codec
    │   ├── transport/             # LanController (TCP+NSD), BluetoothController (RFCOMM), StreamDuelConnection
    │   └── room/                  # RoomHostSession (otoritas + relay ghost), RoomGuestSession, Ghosts, RoomCoordinator
    └── presentation/              # tema, navigasi, Beranda/Solo/Room/Riwayat/Pengaturan, game/ (UI 4 mini-game)
content/visual/                    # sumber 80 soal bergambar + PNG (salinan utuh ZIP kickstart)
content/game/                      # sumber 80 soal mini-game (dihasilkan tools/game_content/)
kickstart/                         # input asli: PDF, JSON, CSV, ZIP 80 gambar, prompt (tidak diubah)
tools/                             # validator & builder dataset
hosting/                           # halaman privasi statis untuk Firebase Hosting (opsional)
docs/                              # dokumentasi (lihat bawah)
```

## Dokumentasi

| Dokumen | Isi |
|---|---|
| [docs/BUILD_APK_ID.md](docs/BUILD_APK_ID.md) | Cara build APK (debug & rilis), membuat keystore, memasang ke HP |
| [docs/GAME_AND_GHOST_ARCHITECTURE.md](docs/GAME_AND_GHOST_ARCHITECTURE.md) | Mini-game, ghost simetris LIVE/FAIR, protokol v3, format soal game baru, uji 2–4 HP, migrasi/rollback bank |
| [docs/MINI_GAME_ARTWORK.md](docs/MINI_GAME_ARTWORK.md) | 12 aset kartun yang disetujui, prompt gambar, rendering dinamis, dan pengujian native |
| [docs/CONTENT_560_QA.md](docs/CONTENT_560_QA.md) | Laporan otomatis 560 soal: distribusi, 80 soal game, validasi solusi & duplikasi |
| [docs/QA_VISUAL_REPORT.md](docs/QA_VISUAL_REPORT.md) | Laporan otomatis 80 soal bergambar vs ZIP & PDF |
| [docs/KONEKSI_ID.md](docs/KONEKSI_ID.md) | Wi-Fi/hotspot & Bluetooth: izin per versi Android, protokol, aturan room, reconnect, model ancaman, troubleshooting |
| [docs/GO_LIVE_FIREBASE_SPARK_ID.md](docs/GO_LIVE_FIREBASE_SPARK_ID.md) | Tutorial go-live: Firebase Spark, App Distribution, Hosting, Google Play |
| [docs/QA_REPORT.md](docs/QA_REPORT.md) | Hasil uji nyata dan yang belum diuji |
| [docs/QA_DATASET_REPORT.md](docs/QA_DATASET_REPORT.md) | Laporan otomatis validasi 400 soal klasik |
| [docs/DATA_ISSUES.md](docs/DATA_ISSUES.md) | Temuan & koreksi data PDF/JSON/CSV |
| [docs/DATA_TRANSFORM_LOG.md](docs/DATA_TRANSFORM_LOG.md) | Log per-field koreksi dan normalisasi |
| [docs/PRIVACY_POLICY_ID.md](docs/PRIVACY_POLICY_ID.md) | Kebijakan privasi |
| [docs/RELEASE_CHECKLIST.md](docs/RELEASE_CHECKLIST.md) | Daftar periksa rilis |
| [docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md) | Rencana & arsitektur ringkas, perubahan v0.2 |

## Keputusan versi

Nama aplikasi: **Duelmu** (logo `kickstart/Duelmu Logo.png`). ID paket tetap `id.eduduel.app` (debug: `id.eduduel.app.debug`) dan
nama kelas internal tetap `EduDuel…`; ID paket sengaja tidak diubah agar update ke HP yang sudah terpasang tetap berjalan.

AGP 9.5.1, Gradle 9.8.2, Kotlin 2.4.21, KSP 2.3.13, Compose BOM 2026.09.00, Room 2.8.5, Navigation 2.10.2,
DataStore 1.3.0 (stabil terbaru per 7 November 2026). `minSdk 26` (Android 8.0), `targetSdk 36` (syarat
Google Play sejak 31 Agustus 2026), `compileSdk 37`. Aplikasi versi **0.4.2** (protokol room v3, bank 560 soal; tidak bisa
bermain dengan 0.3.x atau lebih lama: protokol berbeda ditolak dengan pesan versi).

Versi 0.4.2 memperbaiki tap dan gestur lensa Micro Hunter, menempatkan pesan di luar lensa,
serta menambahkan suara perayaan saat mini-game berhasil di Solo maupun Main Bareng.

## Batasan yang perlu diketahui

- Kunci jawaban ada di dalam APK, jadi kecurangan tidak bisa dicegah sepenuhnya tanpa server tepercaya.
  Karena itu tidak ada leaderboard online; riwayat hanya catatan lokal.
- Lalu lintas room Wi-Fi tidak dienkripsi (isinya nickname, jawaban, skor); kode room 4 digit mencegah orang lain ikut masuk.
- Room di dua+ HP fisik belum diuji selama implementasi; yang sudah diuji: emulator sebagai Host Wi-Fi + Tamu
  di PC lewat TCP sungguhan, dan room demo 4 pemain (lihat [QA_REPORT](docs/QA_REPORT.md)).
