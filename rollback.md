# Catatan rollback Duelmu

## v0.01 — 8 November 2026 (Asia/Jakarta)

Permintaan: memperindah aplikasi Android dengan warna terang, tema gamifikasi ceria,
efek ketika tombol ditekan, lalu membangun APK debug dan release. Versi aplikasi
dinaikkan dari 0.2.0 (versionCode 2) menjadi 0.3.0 (versionCode 3).

### Cadangan sebelum modifikasi

Salinan asli 20 file yang diubah disimpan dengan struktur direktori yang sama di
`.rollback/v0.01/`. Salinan dibuat sebelum modifikasi pertama pada setiap file.
Tidak ada file aplikasi, bank soal, data pengguna, atau riwayat yang dihapus.

### Perubahan terperinci

1. `app/build.gradle.kts`: hanya menaikkan versionCode/versionName menjadi 3/0.3.0.
   Konfigurasi signing, ID paket, SDK, dependensi, dan aturan optimasi dipertahankan.
2. `presentation/theme/Theme.kt`: palet terang krem/putih, ungu lavender, mint,
   dan kuning; warna teks dan outline disesuaikan; tipografi judul membulat memakai
   font sistem; radius komponen 10–32 dp. Tema aplikasi secara default terang,
   termasuk saat perangkat memakai mode malam; parameter tema gelap eksplisit
   masih tersedia untuk preview/internal.
3. `MainActivity.kt`: ikon system bar disesuaikan untuk latar terang; satu instance
   umpan balik suara/getar dibagikan melalui composition local, mengikuti DataStore
   pengaturan yang sudah ada. Tidak ada perubahan skala teks atau navigasi.
4. `presentation/components/PlayfulControls.kt` (baru): tombol utama, outline,
   teks, ikon, serta chip memakai interaction source bawaan Compose, ripple,
   animasi spring skala 0,96 saat ditekan dan suara/getar opsional. Status disabled,
   semantik aksesibilitas, dan perilaku scroll tetap memakai komponen Material.
5. `presentation/components/Components.kt`: scaffold berwarna terang; kartu info
   putih dengan outline lembut; badge berbentuk pil; pilihan jawaban membulat dan
   mempunyai animasi tekan; warna benar/salah dan ikon pembeda dipertahankan.
   Feedback.tap menambahkan nada singkat 35 ms. ToneGenerator hanya dialokasikan
   jika suara aktif, volume 35%, serta dilepas ketika preferensi berubah/dispose.
6. `presentation/components/GameDecor.kt` (baru): confetti yang digambar Canvas
   dan kartu hasil dengan piala/bintang. Animasi hasil hanya sekali selama 750 ms,
   tanpa animasi terus-menerus, unduhan gambar, atau dependensi tambahan.
7. `presentation/home/HomeScreen.kt`: beranda baru dengan logo asli, badge offline,
   panel sambutan, kartu Main Bareng/Latihan Solo, shortcut Riwayat/Pengaturan,
   dan ringkasan bank soal. Jumlah soal berasal dari bank aktual. Tampilan memuat,
   error, serta tombol coba ulang tetap tersedia.
8. `presentation/components/QuestionViews.kt`: kartu soal putih, progress ronde,
   dan metadata menggunakan FlowRow agar membungkus pada layar sempit/teks besar.
   Teks pertanyaan, urutan opsi, dan rendering rumus tidak diubah.
9. `presentation/components/ConfigEditor.kt`: chip mapel menggunakan kontrol
   dengan animasi tekan dan feedback. Aturan pemilihan mapel tidak diubah.
10. `presentation/room/RoomEntryScreen.kt`: panel sambutan masuk arena, kartu pilihan
    membulat dengan ikon beralas terang, dan efek tekan; metadata kartu membungkus.
    Validasi nama dan alur pembuatan/gabung/demo room dipertahankan.
11. `presentation/room/RoomCommon.kt`, `LobbyScreen.kt`, `ConnectScreens.kt`:
    tombol/dialog memakai kontrol bersama; kartu room Wi-Fi dan perangkat Bluetooth
    menambahkan animasi tekan serta feedback. Protokol, izin, discovery, koneksi,
    readiness, dan sesi pertandingan tidak dimodifikasi.
12. `presentation/room/MatchPlayScreen.kt`, `presentation/solo/SoloScreens.kt`:
    feedback memakai instance bersama sehingga ketukan jawaban tidak menggandakan
    getaran/nada. Solo setup diberi panel sambutan; hasil solo memakai kartu perayaan.
    Timer, penguncian jawaban, skor, pemilihan soal, dan penyimpanan hasil tetap.
13. `presentation/room/MatchResultScreen.kt`: panel hasil dengan piala dan confetti
    untuk pertandingan selesai; pertandingan batal memakai bintang tanpa confetti.
    Peringkat, hasil per ronde, dan aksi main lagi tetap menggunakan data sesi asli.
14. `presentation/settings/SettingsScreen.kt`: tombol/chip/dialog konsisten dengan
    tema; toggle memberi feedback sesuai pengaturan. Pengaturan suara/getar/skala
    teks, penghapusan data dengan dialog, serta halaman privasi dipertahankan.
15. `presentation/history/HistoryScreen.kt`: tombol toolbar dan dialog memakai
    kontrol bersama; seluruh data dan perhitungan riwayat dipertahankan.
16. `res/values/strings.xml`: menambahkan teks UI berbahasa Indonesia untuk tema
    baru dan menjelaskan bahwa suara juga berlaku untuk ketukan tombol.
17. `res/values/colors.xml`, `res/values-night/colors.xml`,
    `res/values-night/themes.xml`: latar awal/splash lavender terang yang selaras
    dengan Compose, termasuk ketika perangkat memakai mode malam. Logo tetap asli.
18. `app/src/androidTest/java/id/eduduel/app/VisualExperienceTest.kt` (baru):
    pengujian alur solo 10 soal, jawaban terkunci, hasil latihan, serta akses kontrol
    pada ukuran teks terbesar; ekspor screenshot aktual untuk pemeriksaan visual.

Semua jalur `presentation/` di atas berada di
`app/src/main/java/id/eduduel/app/`. Semua jalur `res/` berada di `app/src/main/`.
Kode domain, jaringan, repository/data, asset soal, manifest, dan dependensi tidak
diubah. Preferensi suara/getar lama tetap dihormati; suara dapat diaktifkan lewat
Pengaturan, sementara efek visual bekerja tanpa mengaktifkan suara.

### Cara memulihkan

1. Tutup aplikasi/build yang sedang berjalan.
2. Salin kembali setiap file dalam `.rollback/v0.01/` ke lokasi relatif yang sama
   di root proyek, menimpa versi modifikasi. Langkah ini mengembalikan seluruh
   file lama, termasuk versi APK 0.2.0.
3. File Kotlin baru dapat dibiarkan karena sudah tidak dirujuk oleh kode asli.
   Bila ingin menghapus file baru/cadangan, minta konfirmasi pemilik terlebih dahulu.
4. Build ulang APK debug/release dengan konfigurasi signing yang sama seperti
   sebelumnya. Android menolak downgrade versionCode pada instalasi normal;
   jangan menghapus instalasi/data pengguna tanpa konfirmasi.

### Verifikasi

- Kompilasi Kotlin debug: berhasil.
- Unit test: 73 total, 72 lulus, 1 dilewati, 0 gagal/error.
- Hasil build, lint, dan pengujian UI akhir dicatat setelah pemeriksaan selesai.

### Hasil akhir verifikasi v0.01

- Build final `assembleDebug`, `assembleRelease`, `assembleDebugAndroidTest`, dan
  `lintDebug`: **BUILD SUCCESSFUL**, dengan satu worker dalam proses Gradle baru.
- Lint debug: **0 error, 5 warning**. Dua warning yang sudah ada terkait target SDK
  dan versi Gradle; tiga `UnusedResources` adalah teks beranda lama yang tetap
  dipertahankan agar tidak menghapus resource tanpa konfirmasi.
- Instrumentation di emulator Android 15/API 35: **OK (3 tests)**, 35,185 detik.
  `GuestFlowTest` lama lulus (validasi nickname → demo 4 pemain → ganti nama/mapel
  → mulai pertandingan). Dua `VisualExperienceTest` lulus (ukuran teks terbesar;
  solo 10 soal → jawaban terkunci → hasil → kembali Beranda).
- Enam screenshot aktual ada di `build/outputs/ui-preview/`; beranda, hasil solo,
  serta pemilihan mapel dengan teks terbesar sudah diperiksa secara visual.
  Log instrumentation: `.tmp/ui-instrumentation-v0.3.0.log`.
- APK debug: `app/build/outputs/apk/debug/app-debug.apk`, versi `0.3.0-debug`,
  versionCode 3, sekitar **21,48 MiB**, paket `id.eduduel.app.debug`.
- APK release: `app/build/outputs/apk/release/app-release.apk`, versi `0.3.0`,
  versionCode 3, sekitar **2,57 MiB**, paket `id.eduduel.app`.
- Release memakai konfigurasi `keystore.properties` dan keystore milik pengguna
  yang ditambahkan di root saat pengerjaan. Tidak ada password/kunci privat yang
  dicatat di log ini atau dimasukkan ke cadangan. `apksigner verify --verbose`
  berhasil, APK Signature Scheme v2, satu penanda tangan.
- APK release berhasil dipasang dan cold launch di emulator: status `ok`,
  `TotalTime: 1614 ms`.
- Kendala lingkungan yang diatasi tanpa perubahan dependensi/build configuration:
  lint sempat mengalami error internal `AsyncExecutionService` pada build paralel;
  berhasil setelah dijalankan dalam proses baru dengan satu worker. Cache Android
  diarahkan ke `.tmp/android-build-home` agar tidak menulis di luar workspace.
  Saat beban build dan first boot emulator bersamaan, startup debug sempat memicu
  ANR; setelah build selesai, ketiga tes UI lulus dan cold launch release berhasil.
- Unit test sebelumnya: **73 total, 72 lulus, 1 dilewati, 0 gagal/error**.
  Pengujian perangkat fisik/audio melalui speaker belum dilakukan; uji otomatis
  dan pemasangan di atas menggunakan emulator. Suara tetap opsional melalui
  Pengaturan, dan efek visual selalu tersedia.

## v0.02 — 8 November 2026 (Asia/Jakarta)

Permintaan: menambahkan efek suara saat jawaban benar/salah dan saat pertandingan
dimulai, kemudian membangun ulang APK debug serta release.

### Cadangan dan lingkup

Tujuh file lama dicadangkan sebelum modifikasi di `.rollback/v0.02/`, dengan jalur
relatif yang sama. File versi v0.01 dan cadangannya tetap dipertahankan. Tidak ada
perubahan bank soal, perhitungan skor, timer, protokol, koneksi, maupun data riwayat.

### Perubahan terperinci

1. `tools/generate_ui_sounds.py` (baru): generator audio orisinal, memakai pustaka
   standar Python, tanpa unduhan/aset pihak ketiga. Menghasilkan empat WAV mono
   PCM 16-bit, 22050 Hz, dengan attack/release halus dan amplitude terkendali.
2. `app/src/main/res/raw/` (baru):
   - `sound_correct.wav`: chime nada naik, 0,62 detik.
   - `sound_wrong.wav`: dua nada turun yang lembut, 0,47 detik.
   - `sound_match_start.wav`: fanfare/arpeggio, 0,84 detik.
   - `sound_tap.wav`: ketukan ringan, 0,055 detik, menggantikan beep lama.
   Total keempat file sekitar 86 KiB; semuanya dipaketkan lokal dalam APK.
3. `presentation/components/GameSounds.kt` (baru): memindahkan Feedback dan
   rememberFeedback dari Components.kt, mengganti ToneGenerator dengan SoundPool
   maksimal tiga stream. Ketukan tidak memutus efek hasil/awal pertandingan.
   Audio menggunakan kategori game dan volume media perangkat; tidak ada audio
   loop atau musik latar. Sampel dimuat sekali per pemilik feedback, permintaan
   yang menunggu load hanya berlaku kurang dari satu detik, dan SoundPool dilepas
   ketika suara dimatikan atau activity dibuang. Kegagalan audio tidak membatalkan
   permainan. Gate suara/getar tetap terpisah mengikuti pengaturan.
4. File yang sama menambahkan `GameFeedbackEffects`: mengonsumsi suara awal dan
   setiap hasil sekali per ID sesi, memakai rememberSaveable agar recomposition
   dan pemulihan activity tidak menggandakan efek. Sesi baru mereset penanda efek.
5. `presentation/components/Components.kt`: implementasi Feedback/ToneGenerator
   lama dan import terkait digantikan oleh implementasi pada GameSounds.kt;
   tampilan dan animasi tombol tidak berubah.
6. `presentation/solo/SoloViewModel.kt`: getter sessionId dari SavedStateHandle
   untuk identitas efek; tidak mengubah state/aturan permainan.
7. `presentation/solo/SoloScreens.kt`: fanfare ketika soal pertama latihan benar-
   benar dimulai (termasuk Ulangi); suara hasil mengikuti jawaban yang sudah dinilai,
   termasuk ronde terakhir dan timeout, sekali per ronde.
8. `presentation/room/MatchPlayScreen.kt`: fanfare saat soal pertama mulai untuk
   Host/Tamu/demo, bukan saat tombol ditekan atau ketika setup gagal. Suara benar/
   salah mengikuti hasil resmi pemain ketika jawaban dibuka; pemain absent tidak
   mendapat efek jawaban. Efek diproses sebelum navigasi ke hasil pertandingan.
9. `data/settings/SettingsRepository.kt`: default soundEnabled menjadi true pada
   AppSettings dan ketika key DataStore belum tersedia. Preferensi false yang
   sebelumnya disimpan pengguna tetap false; tidak ada pemaksaan/migrasi preferensi.
10. `app/src/main/res/values/strings.xml`: deskripsi Pengaturan → Suara menjelaskan
    efek jawaban/awal pertandingan dan penggunaan volume media.
11. `app/build.gradle.kts`: versionCode 3 → 4, versionName 0.3.0 → 0.3.1;
    signing tetap memakai keystore milik pengguna yang sudah dikonfigurasi.
12. `app/src/test/java/id/eduduel/app/presentation/FeedbackTest.kt` (baru): uji
    default suara, pemetaan empat efek, mute/getar yang terpisah, dan kegagalan audio.
13. `app/src/androidTest/java/id/eduduel/app/GameFeedbackEffectsTest.kt` (baru):
    uji efek per sesi/ronde tidak terulang setelah pemulihan state, hasil benar dan
    salah, serta fanfare untuk pertandingan berikutnya.

Semua jalur `presentation/` dan `data/` di atas berada di
`app/src/main/java/id/eduduel/app/`.

### Cara memulihkan v0.02

Salin kembali tujuh file di `.rollback/v0.02/` ke jalur relatif yang sama pada root.
Sebelum build versi lama, nonaktifkan file baru `GameSounds.kt`, `FeedbackTest.kt`,
dan `GameFeedbackEffectsTest.kt`, misalnya dengan mengganti ekstensi `.kt` menjadi
`.kt.disabled`, agar tidak bentrok dengan Feedback lama. Empat WAV dan generator
bisa tetap disimpan. Penghapusan file/cadangan memerlukan konfirmasi pemilik.
Build ulang dengan konfigurasi signing yang sama. Jangan menghapus instalasi/data
pengguna untuk downgrade tanpa konfirmasi.

### Verifikasi v0.02

- WAV telah diperiksa: sample rate/channel/bit depth sesuai, durasi sesuai desain,
  RMS tidak senyap, puncak amplitude di bawah batas clipping.
- Hasil build dan pengujian akhir ditambahkan setelah pemeriksaan selesai.

### Hasil akhir verifikasi v0.02

- Build final `assembleDebug`, `assembleRelease`, `testDebugUnitTest`, dan
  `lintDebug`: **BUILD SUCCESSFUL**. APK instrumentation juga berhasil dikompilasi
  melalui `assembleDebugAndroidTest`.
- Unit test: **78 total, 77 lulus, 1 dilewati, 0 gagal/error**, termasuk lima
  FeedbackTest baru. Lint: **0 error, 5 warning** yang sama seperti v0.01.
- Uji awal Robolectric terkendala direktori home yang tidak writable dan path
  cache dengan spasi. Pengujian akhir berhasil memakai home JVM sementara di
  workspace dan membaca cache Maven yang sudah ada melalui `maven.repo.local`.
  Ini hanya konfigurasi lingkungan sesi build, tanpa perubahan dependensi/kode
  aplikasi untuk mengatasi lingkungan tersebut.
- APK debug: `app/build/outputs/apk/debug/app-debug.apk`, versi `0.3.1-debug`,
  versionCode 4, **22.138.828 byte (21,11 MiB)**.
- APK release bertanda tangan: `app/build/outputs/apk/release/app-release.apk`,
  versi `0.3.1`, versionCode 4, **2.778.630 byte (2,65 MiB)**.
- `apksigner verify --verbose`: berhasil; APK Signature Scheme v2, satu penanda
  tangan. Signing tetap memakai keystore pengguna yang sudah ada.
- `aapt2 dump resources` pada APK release memastikan empat resource
  sound_correct, sound_wrong, sound_match_start, dan sound_tap tetap dipaketkan
  setelah optimasi R8/resource shrinking.
- Metadata ukuran/versi/SHA-256: `build/outputs/duelmu-v0.3.1-artifacts.json`.
- Dua pengujian GameFeedbackEffectsTest baru sudah dikompilasi, termasuk uji
  pemulihan state dan uji decoder/playback SoundPool native, tetapi **belum
  dijalankan**. Pengujian emulator versi ini tertahan karena server ADB tidak
  bisa membuat direktori autentikasi dalam sandbox. Percobaan menjalankan ADB
  di luar sandbox ditolak oleh pemeriksaan persetujuan otomatis karena refresh
  token dicabut; tindakan tersebut tidak dieksekusi. Pengguna telah menyatakan
  akan login ulang, tetapi akses belum berhasil dipulihkan selama verifikasi ini.
- Pengujian UI lama pada v0.01 tidak diklaim sebagai pengujian ulang v0.02.
  Kualitas audio lewat speaker HP belum diuji; validasi audio pada v0.02 meliputi
  format, durasi, amplitude, pemetaan/gate efek, dan keberadaan resource dalam APK.
