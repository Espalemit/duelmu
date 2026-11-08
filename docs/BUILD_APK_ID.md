# Cara Build APK Duelmu

Panduan untuk siapa pun yang ingin menghasilkan file APK Duelmu dan memasangnya ke HP. Tidak perlu
Firebase, akun, atau `google-services.json`.

Ada dua jenis APK:

| Jenis | Untuk apa | Butuh keystore? |
|---|---|---|
| **Debug** | Coba sendiri, uji bersama teman/kelas | Tidak (ditandatangani otomatis dengan kunci debug) |
| **Release** | Dibagikan lebih luas, App Distribution, Google Play (pakai AAB) | Ya, keystore milikmu sendiri |

APK debug dan release bisa terpasang berdampingan: paket debug `id.eduduel.app.debug`, release `id.eduduel.app`.
**Semua HP dalam satu room harus memakai APK dari build yang sama** (versi bank soal & protokol dicek).

---

## 1. Siapkan sekali saja

1. Pasang **Android Studio** versi stabil terbaru (https://developer.android.com/studio). Sudah termasuk JDK
   (JBR) dan Android SDK.
2. Buka Android Studio → **File > Open** → pilih folder proyek `Duelmu` → tunggu **Gradle sync** selesai
   (pertama kali bisa 5–15 menit karena mengunduh Gradle, plugin, dan SDK API 37).
3. Bila diminta menyetujui lisensi SDK, setujui.

> **Drive C: hampir penuh?** Cache Gradle bisa mencapai beberapa GB. Arahkan ke drive lain sebelum build:
> PowerShell: `$env:GRADLE_USER_HOME="F:\gradle-home"; $env:TMP="F:\tmp"; $env:TEMP="F:\tmp"`.

## 2. APK debug

### Cara A: lewat Android Studio
1. Menu **Build > Generate App Bundles or APKs > Generate APKs** (di versi lama: *Build > Build Bundle(s) /
   APK(s) > Build APK(s)*).
2. Setelah selesai, klik **locate** pada notifikasi. File: `app/build/outputs/apk/debug/app-debug.apk`.

### Cara B: lewat terminal
Di folder proyek:

**Windows (PowerShell)**
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"   # sesuaikan lokasi Android Studio
.\gradlew.bat :app:assembleDebug
```
**Linux/macOS**
```bash
./gradlew :app:assembleDebug
```
Hasil: `app/build/outputs/apk/debug/app-debug.apk` (±21 MB; ukuran besar karena tanpa R8).

## 3. APK release (bertanda tangan)

### 3.1 Buat keystore (sekali saja)
```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v `
  -keystore eduduel-release.jks -keyalg RSA -keysize 4096 -validity 10000 -alias eduduel
```
Isi password dan data yang diminta. **Simpan `eduduel-release.jks` dan password-nya di tempat aman dan buat
cadangan offline.** Bila hilang, kamu tidak bisa menerbitkan update untuk aplikasi yang sudah terpasang.

### 3.2 Hubungkan keystore tanpa memasukkannya ke Git
Salin `keystore.properties.example` menjadi `keystore.properties` di folder proyek, lalu isi:
```properties
storeFile=eduduel-release.jks
storePassword=PASSWORD_KEYSTORE
keyAlias=eduduel
keyPassword=PASSWORD_KEY
```
`keystore.properties`, `*.jks`, dan `*.keystore` sudah diabaikan oleh `.gitignore`.

### 3.3 Build
```powershell
.\gradlew.bat :app:assembleRelease        # APK  -> app\build\outputs\apk\release\app-release.apk
.\gradlew.bat :app:bundleRelease          # AAB  -> app\build\outputs\bundle\release\app-release.aab (untuk Google Play)
```
Tanpa `keystore.properties`, hasilnya `app-release-unsigned.apk` yang **tidak bisa dipasang** sebelum
ditandatangani. APK release ±2,7 MB (R8 aktif).

Alternatif Android Studio: **Build > Generate Signed App Bundle or APK… > APK** → pilih keystore → varian
`release`.

Sebelum membagikan versi baru, naikkan `versionCode` dan `versionName` di `app/build.gradle.kts`.

## 4. Pasang ke HP

### Lewat kabel USB (adb)
1. Di HP: **Pengaturan > Tentang ponsel** → ketuk **Nomor build** 7× → kembali → **Opsi pengembang** →
   aktifkan **Debugging USB**.
2. Sambungkan kabel, lalu **terima dialog "Izinkan debugging USB?"** di layar HP (bila tidak diterima, `adb
   devices` menampilkan `unauthorized`).
3. Pasang:
   ```powershell
   adb devices
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```
   (`adb` ada di `<Android SDK>\platform-tools\`.) Atau cukup tekan **Run ▶** di Android Studio.

### Tanpa kabel
Kirim file APK lewat Google Drive/kabel data/aplikasi berbagi file → buka file di HP → izinkan **Instal
aplikasi tidak dikenal** untuk aplikasi pembuka file → **Instal**. Untuk banyak penguji, gunakan Firebase App
Distribution (lihat [GO_LIVE_FIREBASE_SPARK_ID.md](GO_LIVE_FIREBASE_SPARK_ID.md) bagian 9).

Bila muncul "Aplikasi tidak terpasang" karena tanda tangan berbeda (mis. sebelumnya terpasang versi lain
dengan kunci lain), hapus dulu versi lama.

## 5. Cek cepat setelah terpasang

1. Buka Duelmu → langsung Beranda, tanpa login.
2. **Main Bareng** → isi nama → **Coba dengan 3 Bot (demo)** → pilih mapel → **Mulai Pertandingan** →
   soal 1 dari 10 dengan label mapel yang dipilih.
3. Room sungguhan: semua HP di Wi-Fi yang sama atau hotspot HP Host. HP Host: **Buat Room → Wi-Fi /
   Hotspot**. HP lain: **Gabung Room → Wi-Fi / Hotspot** → pilih room (atau ketik IP Host) → ketik kode room 4
   digit → **Saya SIAP**. Host menekan **Mulai Pertandingan**. Detail & troubleshooting: [KONEKSI_ID.md](KONEKSI_ID.md).

## 6. Uji otomatis (opsional, sebelum membagikan)
```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest    # butuh emulator/HP tersambung
python tools\validate_question_bank.py
```

## 7. Masalah umum

| Masalah | Solusi |
|---|---|
| `JAVA_HOME is not set` | Set `JAVA_HOME` ke folder `jbr` di instalasi Android Studio (lihat 2B). |
| `SDK location not found` | Buat `local.properties` berisi `sdk.dir=C\:/Users/<nama>/AppData/Local/Android/Sdk` (sesuaikan), atau buka sekali di Android Studio. |
| `There is not enough space on the disk` | Kosongkan drive C: atau pindahkan `GRADLE_USER_HOME`/`TMP` (lihat bagian 1). |
| Build release gagal menandatangani | Periksa `keystore.properties`: path `storeFile` relatif ke folder proyek, alias, password. Cek: `keytool -list -keystore eduduel-release.jks`. |
| `adb devices` menampilkan `unauthorized` | Buka kunci HP dan terima dialog izin debugging USB; cabut-colok kabel bila dialog tidak muncul. |
| HP lain tidak bisa bergabung | Pastikan APK sama di semua HP dan jaringan sama; lihat [KONEKSI_ID.md](KONEKSI_ID.md) §9. |
