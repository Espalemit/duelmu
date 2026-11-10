# Duelmu

Aplikasi Android kuis edukasi **main bareng 2–4 pemain dalam satu room**, sepenuhnya offline dan **tanpa akun**.
Pemain bersaing menjawab soal yang sama (urutan dan pilihan jawaban sama) dari 400 soal Fisika, Biologi,
Kimia, dan Matematika SMP–SMA. Setiap pemain adalah Guest yang mengetik nama panggilan tepat sebelum bermain.

- **Room maks. 4 pemain**: satu HP menjadi Host, maks. 3 Tamu. Mulai setelah semua Tamu SIAP.
- **Koneksi lokal tanpa internet**: Wi-Fi yang sama / hotspot salah satu HP (TCP + NSD), atau Bluetooth Classic RFCOMM.
- **Setiap pertandingan 10 soal acak** dari mapel yang dipilih Host (atau Campuran). Jenjang & kesulitan hanya label di soal.
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
| Unit test (73) | `./gradlew :app:testDebugUnitTest` |
| UI test di emulator/HP | `./gradlew :app:connectedDebugAndroidTest` |
| Lint | `./gradlew :app:lintDebug` |
| APK/AAB release | `./gradlew :app:assembleRelease :app:bundleRelease` (signing: `keystore.properties`, lihat contoh) |
| Validasi dataset + laporan | `python tools/validate_question_bank.py` |
| Bangun ulang asset soal | `python tools/build_question_asset.py` (`--check` untuk CI) |

## Struktur

```
app/src/main/
├── assets/questions.json          # dihasilkan dari kickstart/ oleh tools/build_question_asset.py
└── java/id/eduduel/app/
    ├── core/                      # validasi nickname (grapheme), jam monotonic, hash, MathText
    ├── domain/                    # model, MatchConfig (mapel), QuestionSelector, ScoringEngine, SoloEngine
    ├── data/                      # QuestionBankLoader (cek SHA-256), Room history (migrasi v1→v2), DataStore
    ├── net/
    │   ├── protocol/              # pesan v2, envelope, FrameDecoder, MessageCodec
    │   ├── transport/             # LanController (TCP+NSD), BluetoothController (RFCOMM), StreamDuelConnection
    │   └── room/                  # RoomHostSession, RoomGuestSession, RoomCoordinator (demo bot, riwayat)
    └── presentation/              # tema, navigasi, Beranda/Solo/Room/Riwayat/Pengaturan
kickstart/                         # input asli: PDF, JSON, CSV, prompt (tidak diubah)
tools/                             # validator & builder dataset
hosting/                           # halaman privasi statis untuk Firebase Hosting (opsional)
docs/                              # dokumentasi (lihat bawah)
```

## Dokumentasi

| Dokumen | Isi |
|---|---|
| [docs/BUILD_APK_ID.md](docs/BUILD_APK_ID.md) | Cara build APK (debug & rilis), membuat keystore, memasang ke HP |
| [docs/KONEKSI_ID.md](docs/KONEKSI_ID.md) | Wi-Fi/hotspot & Bluetooth: izin per versi Android, protokol v2, aturan room, reconnect, model ancaman, troubleshooting |
| [docs/GO_LIVE_FIREBASE_SPARK_ID.md](docs/GO_LIVE_FIREBASE_SPARK_ID.md) | Tutorial go-live: Firebase Spark, App Distribution, Hosting, Google Play |
| [docs/QA_REPORT.md](docs/QA_REPORT.md) | Hasil uji nyata dan yang belum diuji |
| [docs/QA_DATASET_REPORT.md](docs/QA_DATASET_REPORT.md) | Laporan otomatis validasi 400 soal |
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
Google Play sejak 31 Agustus 2026), `compileSdk 37`. Aplikasi versi **0.2.0** (protokol room v2; tidak bisa
bermain dengan 0.1.x).

## Batasan yang perlu diketahui

- Kunci jawaban ada di dalam APK, jadi kecurangan tidak bisa dicegah sepenuhnya tanpa server tepercaya.
  Karena itu tidak ada leaderboard online; riwayat hanya catatan lokal.
- Lalu lintas room Wi-Fi tidak dienkripsi (isinya nickname, jawaban, skor); kode room 4 digit mencegah orang lain ikut masuk.
- Room di dua+ HP fisik belum diuji selama implementasi; yang sudah diuji: emulator sebagai Host Wi-Fi + Tamu
  di PC lewat TCP sungguhan, dan room demo 4 pemain (lihat [QA_REPORT](docs/QA_REPORT.md)).
