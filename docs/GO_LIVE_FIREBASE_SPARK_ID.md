# Tutorial Go-Live Duelmu dengan Firebase Spark Plan

Panduan ini untuk **developer/pengelola rilis**. Ringkasnya:

- **Pemain tidak punya akun.** Semua pemain adalah Guest yang mengetik nickname sebelum bermain di room (2–4 pemain). Aplikasi
  tidak memakai Firebase Authentication, Anonymous Auth, Google Sign-In, Firestore, atau database cloud.
- **Firebase hanya untuk distribusi beta** (App Distribution) dan, bila mau, **halaman kebijakan privasi
  statis** (Hosting). Keduanya berada di luar APK.
- **APK tidak memuat Firebase SDK dan tidak butuh `google-services.json`.** Izin `INTERNET` hanya dipakai
  untuk socket lokal antar-HP (room Wi-Fi/hotspot); tidak ada koneksi ke server. Room tetap jalan walau
  project Firebase belum dibuat, kuota habis, atau tidak ada internet.

> Fakta harga/kuota di bawah diambil dari halaman resmi pada 7 November 2026. Selalu cek ulang
> https://firebase.google.com/pricing sebelum mengandalkan angka tertentu.

---

## 1. Prasyarat

| Kebutuhan | Keterangan |
|---|---|
| Android Studio | Versi stabil terbaru yang mendukung **AGP 9.5** (proyek ini: AGP 9.5.1, Gradle 9.8.2, Kotlin 2.4.21). Cek di https://developer.android.com/studio. |
| JDK | **JDK 17 atau lebih baru.** Paling mudah memakai JBR bawaan Android Studio (diuji dengan JBR 21). |
| Android SDK | Platform **Android 17 (API 37)** untuk kompilasi. Build Gradle mengunduhnya otomatis bila lisensi SDK sudah diterima. |
| Gradle | Tidak perlu dipasang; gunakan wrapper `gradlew` / `gradlew.bat` (checksum distribusi dipatok). |
| Python 3.10+ | Opsional, untuk `tools/validate_question_bank.py` dan `tools/build_question_asset.py`. Poppler (`pdftotext`) opsional untuk mencocokkan PDF. |
| 2–4 HP Android | Android 8.0 (API 26) ke atas. Untuk room Wi-Fi: Wi-Fi yang sama atau hotspot salah satu HP. Untuk room Bluetooth: Bluetooth Classic. Emulator tidak cukup untuk menguji RFCOMM. |
| Akun Google developer | Hanya untuk Firebase Console & Play Console milik **developer**. Bukan akun pemain. |
| Node.js LTS + Firebase CLI | Opsional, untuk App Distribution via CLI dan Hosting: `npm install -g firebase-tools`. |

## 2. Jalankan proyek lokal TANPA Firebase

1. Buka folder proyek di Android Studio (**File > Open**), tunggu Gradle sync.
2. Tidak ada `google-services.json`, tidak ada login, tidak ada konfigurasi server.
3. Build dan pasang versi debug:

   **Windows (PowerShell/CMD)**
   ```bat
   gradlew.bat :app:assembleDebug
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```
   **Linux/macOS**
   ```bash
   ./gradlew :app:assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
   Nama paket debug: `id.eduduel.app.debug` (bisa terpasang berdampingan dengan rilis `id.eduduel.app`).
4. Buka aplikasi: **langsung Beranda**. Latihan Solo bisa dipakai tanpa mengisi nama.
5. Demo room di 1 HP/emulator: **Main Bareng** → ketik nama → **Coba dengan 3 Bot (demo)**.
6. Room sungguhan 2–4 HP: lihat [langkah 8](#8-uji-room-di-hp-fisik). Panduan build lengkap: [BUILD_APK_ID.md](BUILD_APK_ID.md).

Uji otomatis:
```bash
./gradlew :app:testDebugUnitTest :app:lintDebug          # Linux
gradlew.bat :app:testDebugUnitTest :app:lintDebug        # Windows
./gradlew :app:connectedDebugAndroidTest                 # butuh emulator/HP tersambung
python tools/validate_question_bank.py                   # validasi dataset + laporan QA
```

> **Drive sistem penuh?** Arahkan cache Gradle ke drive lain sebelum build:
> PowerShell: `$env:GRADLE_USER_HOME="D:\gradle-home"; $env:TMP="D:\tmp"; $env:TEMP="D:\tmp"`.
> Linux: `export GRADLE_USER_HOME=/data/gradle-home`.

## 3. Buat project Firebase pada Spark Plan

1. Buka https://console.firebase.google.com/ dengan akun Google **developer**.
2. Klik **Create a project / Buat project**, beri nama mis. `eduduel-beta`.
3. Saat ditanya **Google Analytics**, pilih **nonaktif**. Duelmu tidak memakai analitik dan ditujukan
   untuk pelajar.
4. Pastikan paket yang tertera **Spark (No-cost)**. Halaman harga resmi menyatakan Spark *"No payment method
   needed"*. **Jangan** klik *Upgrade* ke Blaze dan jangan menautkan akun penagihan.

Akun ini adalah akun **developer** untuk mengelola distribusi APK. Pemain tidak pernah login ke apa pun.

## 4. Daftarkan aplikasi Android (untuk App Distribution)

1. Di halaman project, klik ikon **Android** (*Add app*).
2. **Android package name**: `id.eduduel.app` (sama dengan `applicationId` di `app/build.gradle.kts`).
   Nama paket peka huruf besar-kecil dan **tidak bisa diubah** setelah didaftarkan. Untuk build debug
   yang ingin didistribusikan, daftarkan juga `id.eduduel.app.debug` sebagai app terpisah.
3. Nickname app opsional; SHA-1 tidak diperlukan.
4. Wizard menawarkan unduhan `google-services.json` dan langkah menambah Firebase SDK. **Lewati langkah
   tersebut.** Duelmu tidak memakai SDK Firebase apa pun, jadi file itu tidak dibutuhkan.

**Perbedaan penting:** *mendaftarkan app di Firebase Console* hanya membuat entri app di project
(dibutuhkan agar App Distribution tahu ke app mana build diunggah). *Mengintegrasikan Firebase SDK*
berarti menambah plugin `com.google.gms.google-services`, `google-services.json`, dan library Firebase
ke dalam APK. Langkah kedua **tidak dilakukan** di proyek ini.

## 5. Yang TIDAK boleh diaktifkan

- **Authentication** (termasuk *Anonymous*), **Google Sign-In**, **Cloud Firestore**, **Realtime Database**,
  **Cloud Functions**, **Cloud Run**, **App Hosting**, **Analytics**, **Crashlytics**.
- Tidak ada akun pemain, database cloud, atau `firestore.rules` yang perlu dideploy.
- Nickname dan skor tidak pernah dikirim ke server mana pun, sehingga tidak ada endpoint publik yang
  perlu diamankan.

## 6. (Opsional) Kebijakan privasi di Firebase Hosting

Google Play mewajibkan URL kebijakan privasi. Halaman statis siap pakai ada di folder `hosting/`.

1. Pasang CLI dan login sebagai developer:
   ```bash
   npm install -g firebase-tools
   firebase login
   ```
2. Pilih project:
   ```bash
   cd hosting
   firebase use --add          # pilih eduduel-beta, alias "default"
   ```
   `firebase.json` sudah disediakan (folder publik `public/`). Bila ingin membuat dari nol:
   `firebase init hosting` → pilih project → public directory `public` → *single-page app* **No**.
3. Deploy:
   ```bash
   firebase deploy --only hosting
   ```
4. Halaman tersedia di `https://PROJECT_ID.web.app/privacy.html` (juga `PROJECT_ID.firebaseapp.com`).
   Pakai URL ini di Play Console.

Kuota Hosting Spark (halaman resmi, 7 Nov 2026): penyimpanan 10 GB, transfer data 360 MB/hari. Halaman
privasi ini berukuran beberapa KB.

**Hosting bukan tempat mempublikasikan APK.** Jangan mengunggah APK ke Hosting untuk diunduh publik;
gunakan App Distribution (tester) atau Google Play (publik).

## 7. Build debug & release dengan penandatanganan aman

### 7.1 Buat keystore (sekali saja, simpan cadangan offline)
```bash
keytool -genkeypair -v -keystore eduduel-release.jks -keyalg RSA -keysize 4096 -validity 10000 -alias eduduel
```
Di Windows, `keytool` ada di `"C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"` (sesuaikan
lokasi instalasi Android Studio).

### 7.2 Konfigurasi signing tanpa memasukkan rahasia ke Git
Salin `keystore.properties.example` menjadi `keystore.properties` di root proyek:
```properties
storeFile=eduduel-release.jks
storePassword=********
keyAlias=eduduel
keyPassword=********
```
`keystore.properties`, `*.jks`, dan `*.keystore` sudah ada di `.gitignore`. Untuk CI, gunakan variabel
lingkungan `EDUDUEL_STORE_FILE`, `EDUDUEL_STORE_PASSWORD`, `EDUDUEL_KEY_ALIAS`, `EDUDUEL_KEY_PASSWORD`.
Jika keystore hilang, update aplikasi dengan kunci yang sama tidak mungkin dibuat. Untuk Play, aktifkan
**Play App Signing** sehingga kunci di atas menjadi *upload key*.

### 7.3 Perintah build

| Output | Windows | Linux | Path hasil |
|---|---|---|---|
| APK debug (QA) | `gradlew.bat :app:assembleDebug` | `./gradlew :app:assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk` |
| APK release | `gradlew.bat :app:assembleRelease` | `./gradlew :app:assembleRelease` | `app/build/outputs/apk/release/app-release.apk` (tanpa keystore: `app-release-unsigned.apk`) |
| AAB release (Play) | `gradlew.bat :app:bundleRelease` | `./gradlew :app:bundleRelease` | `app/build/outputs/bundle/release/app-release.aab` |

Release memakai R8 (minify + shrink resources). Ukuran APK release ±1,9 MB.

## 8. Uji room di HP fisik

Pasang APK yang **sama** di semua HP (`adb install -r ...` atau via App Distribution). Buka aplikasi: harus
langsung Beranda, tanpa login.

**A. Room Wi-Fi/hotspot (3–4 HP)**
1. Matikan data seluler di semua HP (membuktikan internet tidak dipakai). HP A menyalakan **Hotspot
   pribadi**; HP B, C, D menyambung ke hotspot itu. (Atau semua HP di Wi-Fi yang sama.)
2. HP A: **Main Bareng** → `Rina` → **Buat Room** → **Wi-Fi / Hotspot**. Lobi menampilkan **kode room** dan
   **IP Host** (tekan **Muat ulang IP** bila kosong).
3. HP B/C/D: **Main Bareng** → nama (`Budi`, `Sari`, `Joko`) → **Gabung Room** → tab **Wi-Fi / Hotspot** →
   pilih *Room Rina* di daftar, atau ketik IP Host → masukkan kode room.
4. Semua HP menampilkan 4 pemain yang sama. HP ke-5 (bila ada) harus ditolak *"Room sudah penuh"*. Kode
   salah harus ditolak.
5. HP A memilih mapel (mis. Matematika); Tamu melihat mapel yang sama. Setiap Tamu tekan **Saya SIAP**;
   **Mulai Pertandingan** di HP A baru aktif setelah semua SIAP.
6. Selama pertandingan: 10 soal, semuanya Matematika (label mapel · jenjang · kesulitan), soal & urutan opsi
   identik, penghitung "x dari 4 pemain sudah menjawab", skor sama di semua HP setelah tiap soal.
7. Halaman hasil: peringkat 1–4 sama di semua HP. HP A tekan **Main Lagi (buka room)** → Tamu tekan
   **Kembali ke Room** → Tamu `Budi` tekan **Ubah Nama** → `Budi 2` → nama baru tampil di semua HP. Mainkan
   lagi; **Riwayat Lokal** menyimpan nama sesuai tiap pertandingan.
8. Gangguan: matikan Wi-Fi HP C di tengah soal → pemain lain tetap lanjut, C bertanda *Terputus*; nyalakan
   lagi dalam 30 dtk → C ikut lagi mulai soal berikutnya.

**B. Room Bluetooth (2–3 HP)**
1. Mode pesawat di semua HP, lalu nyalakan lagi Bluetooth.
2. HP A: **Buat Room** → **Bluetooth** → izinkan *Perangkat sekitar* (Android 12+) → **Jadikan HP terlihat**.
3. HP B: **Gabung Room** → tab **Bluetooth** → pilih HP A (atau **Pindai HP sekitar**; Android 8–11 meminta
   izin Lokasi khusus untuk memindai) → konfirmasi pairing. Ulangi untuk HP C.
4. Ulangi langkah A.5–A.7. Uji: matikan Bluetooth HP A di tengah pertandingan → pertandingan dibatalkan
   tanpa pemenang, tidak crash.

## 9. Distribusi beta dengan Firebase App Distribution

App Distribution tercantum sebagai produk **No-cost** di halaman harga Firebase. Distribusi ini untuk
**tester terbatas**, bukan rilis publik.

### 9.1 Lewat Console
1. Firebase Console → project → halaman **App Distribution** di menu samping (letak
   menu dapat berubah antarversi Console) → pilih app `id.eduduel.app` → **Get started**.
2. Tab **Testers & Groups**: buat grup, mis. `guru-beta`, tambahkan email tester.
3. Tab **Releases**: seret APK release bertanda tangan (atau APK debug) ke Console.
4. Pilih grup/tester, isi **release notes**, klik **Distribute**.
5. Tester menerima email undangan (berlaku 30 hari). Build tersimpan 150 hari.

### 9.2 Lewat CLI
App ID ada di **Project settings > General > Your apps** (format `1:1234567890:android:abc...`).
```bash
firebase login
firebase appdistribution:distribute app/build/outputs/apk/release/app-release.apk \
  --app 1:1234567890:android:0a1b2c3d4e5f67890 \
  --groups "guru-beta" \
  --release-notes "Beta 0.2.0: room 2-4 pemain via Wi-Fi/hotspot atau Bluetooth"
```
Windows (satu baris):
```bat
firebase appdistribution:distribute app\build\outputs\apk\release\app-release.apk --app 1:1234567890:android:0a1b2c3d4e5f67890 --groups "guru-beta" --release-notes "Beta 0.2.0"
```
Unggah AAB lewat App Distribution membutuhkan penautan ke Google Play; untuk beta, unggah **APK** saja.

### 9.3 Catatan untuk tester
Tester membuka email undangan, menerima undangan, lalu mengunduh APK (perlu mengizinkan *Install unknown
apps* untuk browser/aplikasi Firebase App Tester). **Undangan atau login tester di sisi App Distribution
bukan login di dalam Duelmu.** Setelah terpasang, Duelmu tetap tanpa akun.

## 10. Publikasi umum lewat Google Play Console

Biaya Play Console **terpisah** dari Firebase Spark.

1. **Daftar akun developer** di https://play.google.com/console: biaya pendaftaran sekali bayar
   **US$25** (halaman bantuan resmi, 7 Nov 2026). Selesaikan verifikasi identitas.
2. **Akun personal baru** (dibuat setelah 13 Nov 2023) wajib menjalankan **closed test dengan minimal 12
   tester yang ikut terus-menerus selama minimal 14 hari** sebelum dapat mengajukan akses produksi.
3. **Create app**: nama Duelmu, bahasa Indonesia, *App*, *Free*.
4. **Store listing**: deskripsi, ikon 512×512 (siap pakai: `app/src/main/ic_launcher-playstore.png`, dari logo Duelmu), feature graphic, tangkapan layar.
5. **Privacy policy**: URL dari langkah 6.
6. **App content**:
   - **Data safety**: lihat `docs/PRIVACY_POLICY_ID.md`. Tidak ada data yang dikumpulkan/dibagikan ke
     developer atau pihak ketiga. Nickname dikirim ke HP pemain lain di room langsung lewat jaringan lokal
     atau Bluetooth (tidak ke server) dan disimpan lokal; nyatakan sesuai formulir terbaru. Izin `INTERNET`
     ada karena Android mewajibkannya untuk socket lokal.
   - **Target audience**: bila memilih usia di bawah 13 tahun, aplikasi masuk program Families; tidak ada
     iklan, analitik, atau SDK pihak ketiga sehingga lebih mudah memenuhi kebijakan tersebut.
   - Content rating, ads (tidak ada iklan), akses aplikasi (tidak butuh login: pilih *All functionality is
     available without special access*).
7. **Target API**: sejak 31 Agustus 2026, app baru dan update wajib **target API 36 (Android 16)** atau
   lebih tinggi. Duelmu memakai `targetSdk = 36` (memenuhi). Pantau pengumuman target API berikutnya.
8. **Upload AAB** (`bundleRelease`) dengan **Play App Signing**.
9. **Release tracks**: Internal testing → Closed testing (≥12 tester, ≥14 hari bila akun personal) →
   Production. Review biasanya ≤7 hari.
10. **Rollout** bertahap (mis. 20% → 100%).

Selalu verifikasi kebijakan terbaru di https://support.google.com/googleplay/android-developer.

## 11. Pemantauan penggunaan Spark

- Console → **Usage and billing**: hanya App Distribution dan (opsional) Hosting yang aktif.
- Room (Wi-Fi lokal/Bluetooth) **tidak menyentuh** backend apa pun, jadi jumlah pemain tidak memengaruhi kuota Firebase.
- Jangan menambah Analytics/Crashlytics diam-diam. Bila suatu saat diperlukan, itu keputusan produk
  terpisah: membutuhkan SDK, `google-services.json`, izin internet, pengungkapan Data safety, persetujuan
  (opt-in) yang sesuai untuk pelajar, dan pembaruan kebijakan privasi.

## 12. Prosedur pembaruan bank soal

1. Perbarui `kickstart/bank_soal_duel_400.json` (dan PDF/CSV pendamping).
2. Validasi: `python tools/validate_question_bank.py` → `docs/QA_DATASET_REPORT.md` harus **ERROR = 0**.
3. Naikkan `BANK_VERSION_NAME` di `tools/build_question_asset.py` (mis. `2026.12.01-r2`), lalu
   `python tools/build_question_asset.py`.
4. Build ulang. `BuildConfig.BANK_SHA256` otomatis berubah (hash dari asset).
5. Naikkan `versionCode`/`versionName`, distribusikan APK baru ke **semua** tester.
6. HP dengan bank berbeda **otomatis ditolak masuk room** dengan pesan *"Versi bank soal berbeda"*.
   CI dapat memakai `python tools/build_question_asset.py --check` untuk memastikan asset sinkron.

## 13. Troubleshooting

| Masalah | Penyelesaian |
|---|---|
| Room Wi-Fi tidak terlihat / gagal terhubung | Semua HP harus di Wi-Fi/hotspot yang sama; router sekolah sering memblokir komunikasi antar-perangkat (*client isolation*) atau mDNS. Ketik IP Host manual atau pakai hotspot HP Host. Lihat [KONEKSI_ID.md](KONEKSI_ID.md) §9. |
| "Kode room salah" / "Room sudah penuh" | Ketik ulang kode 4 digit di layar Host; maksimal 4 pemain per room. |
| Izin Bluetooth ditolak | Pengaturan → Aplikasi → Duelmu → Izin → *Perangkat sekitar* (12+) / *Lokasi* (8–11, hanya untuk pindai). Aplikasi menampilkan tombol **Buka Pengaturan Aplikasi**. |
| Pairing gagal | *Lupakan* perangkat di Pengaturan Bluetooth kedua HP, matikan-nyalakan Bluetooth, ulangi. |
| HP tidak ditemukan | Host tekan **Jadikan HP terlihat**; jarak dekat; Android 8–11 nyalakan Lokasi sistem saat memindai; atau pasangkan dulu dari Pengaturan Bluetooth. |
| Pairing lama tersimpan tetapi gagal terhubung | Hapus pairing lama di kedua HP lalu pasangkan ulang. |
| "Versi bank soal berbeda" / "Versi aplikasi tidak cocok" | Pasang APK yang sama di semua HP; bandingkan SHA-256 di **Pengaturan → Tentang**. Versi 0.2.x tidak bisa bermain dengan 0.1.x. |
| Nama Guest tidak tersinkron | Nama hanya bisa diubah di lobi sebelum SIAP; batalkan SIAP, ubah, SIAP lagi. |
| Nickname sama | Diizinkan. Bedakan lewat label *Kamu*; teknisnya memakai `playerSessionId`. |
| Error `google-services.json` / plugin Google Services | Tidak relevan untuk Duelmu: proyek tidak memakai plugin tersebut. Bila muncul, ada yang menambahkannya; hapus plugin/dependency Firebase. |
| Undangan App Distribution tidak diterima | Cek folder spam, pastikan email tester benar, kirim ulang undangan dari tab *Testers & Groups*; undangan kedaluwarsa 30 hari. |
| APK gagal dipasang | Izinkan *Install unknown apps*; hapus versi lama bila tanda tangan berbeda (debug vs release); pastikan Android ≥ 8.0. |
| Release signing gagal | Cek `keystore.properties` (path relatif root proyek), alias, dan password; jalankan `keytool -list -keystore eduduel-release.jks`. |
| Ditolak Play Console | Biasanya karena privacy policy/Data safety tidak konsisten, target API kurang, atau persyaratan closed test belum terpenuhi. Baca email penolakan, perbaiki, kirim ulang. |

## 14. Referensi resmi

- Firebase Pricing: https://firebase.google.com/pricing
- Paket harga Firebase: https://firebase.google.com/docs/projects/billing/firebase-pricing-plans
- App Distribution (Console): https://firebase.google.com/docs/app-distribution/android/distribute-console
- App Distribution (CLI): https://firebase.google.com/docs/app-distribution/android/distribute-cli
- Firebase Hosting: https://firebase.google.com/docs/hosting dan https://firebase.google.com/docs/hosting/quickstart
- Menambah Firebase ke Android (tidak dipakai, untuk perbandingan): https://firebase.google.com/docs/android/setup
- Bluetooth Android: https://developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices ,
  https://developer.android.com/develop/connectivity/bluetooth/bt-permissions ,
  https://developer.android.com/develop/connectivity/bluetooth/transfer-data
- Target API Google Play: https://developer.android.com/google/play/requirements/target-sdk
- Play Console pendaftaran: https://support.google.com/googleplay/android-developer/answer/6112435
- Persyaratan pengujian akun personal: https://support.google.com/googleplay/android-developer/answer/14151465

**Ingat:** akun Google di dokumen ini hanya untuk **developer** (Console) dan **tester** (menerima
undangan). Di dalam aplikasi Duelmu **tidak ada akun pemain sama sekali**.
