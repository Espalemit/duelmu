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

## v0.03 — 8 November 2026 (Asia/Jakarta)

Permintaan: menambah elemen dinamis/bergerak dan beberapa animasi, terutama pada
menu utama, kemudian membangun ulang APK release.

### Cadangan dan lingkup

Dua file lama dicadangkan sebelum modifikasi di `.rollback/v0.03/` dengan struktur
jalur yang sama: `app/build.gradle.kts` dan `presentation/home/HomeScreen.kt`.
Catatan dan cadangan versi sebelumnya dipertahankan. Perubahan gerak dibatasi pada
beranda; logika permainan, timer, koneksi, suara, bank soal, dan data tidak diubah.
APK yang sudah ada di folder output sebelum packaging juga disalin ke
`.rollback/v0.03/artifacts/` agar tetap tersedia setelah build ulang.

### Perubahan terperinci

1. `presentation/home/HomeMotion.kt` (baru): satu clock animasi bersama dengan
   siklus 12 detik untuk elemen dekoratif beranda. Clock hanya berjalan ketika
   lifecycle halaman RESUMED, dan tidak dibuat jika animasi sistem dinonaktifkan.
   Preview serta mode motionEnabled=false menampilkan tampilan statis.
2. `HomeHeroBackdrop`: glow lavender, 18 partikel/confetti dan bintang berdenyut
   yang melayang pelan. Gerak diletakkan di tepi panel sambutan agar teks terbaca,
   serta di-clip pada radius panel. Seluruhnya digambar Canvas lokal tanpa aset
   unduhan/dependensi baru.
3. `FloatingTrophy`: medali piala melayang dan berayun ringan dengan titik yang
   mengorbit. Elemen bersifat dekoratif dan tidak menambah node interaktif.
4. `HomeEntrance`: animasi masuk fade, geser 20 dp, dan skala 0,98 → 1 dalam
   360 ms, dengan jeda bertahap 45 ms per bagian. Layout tetap menyimpan ukuran
   akhir sehingga tidak menggeser susunan menu ketika animasi berjalan.
5. `presentation/home/HomeScreen.kt`: menerapkan animasi masuk pada header,
   panel sambutan, judul pilihan mode, kartu Main Bareng/Latihan Solo dan shortcut.
   Logo berayun ringan; ikon kartu melayang/miring sedikit; panah bergerak ringan;
   ikon Riwayat dan Pengaturan berayun. Teks dan area klik kartu tetap stabil.
   Efek tekan, suara, state loading/error/retry, navigasi, dan label lama tetap.
6. Gerak ambient membaca state hanya di tahap draw/graphicsLayer, sehingga tidak
   memicu recomposition seluruh menu pada setiap frame. Tidak ada musik/suara
   tambahan yang diputar oleh animasi.
7. `app/src/androidTest/java/id/eduduel/app/HomeMotionTest.kt` (baru): memeriksa
   perubahan pixel panel pada waktu berbeda, kestabilan area menu dan klik,
   serta mode animasi nonaktif yang statis dan langsung dapat digunakan.
8. `app/build.gradle.kts`: versionCode 4 → 5 dan versionName 0.3.1 → 0.3.2;
   signing tetap menggunakan konfigurasi keystore pengguna yang sudah ada.

Jalur `presentation/` berada di `app/src/main/java/id/eduduel/app/`.

### Cara memulihkan v0.03

Salin kedua file dari `.rollback/v0.03/` ke jalur relatif yang sama di root.
HomeMotion.kt yang baru dapat tetap disimpan sebagai kode tidak terpakai; nonaktifkan
HomeMotionTest.kt (misalnya ubah ekstensi menjadi `.kt.disabled`) sebelum build
androidTest versi lama karena parameter motionEnabled belum ada di versi tersebut.
Penghapusan file/cadangan memerlukan konfirmasi pemilik. Build ulang dengan signing
yang sama; jangan menghapus instalasi/data untuk downgrade tanpa konfirmasi.

### Verifikasi v0.03

Hasil build release, pemeriksaan statis, dan pengujian UI ditambahkan setelah selesai.

### Hasil akhir verifikasi v0.03

- `assembleRelease`, `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug`:
  **BUILD SUCCESSFUL**. Build memakai satu worker dan compiler Kotlin in-process,
  tanpa perubahan konfigurasi build untuk lingkungan pengujian.
- Lint: **0 error, 5 warning**, sama seperti v0.02 (target SDK, versi Gradle,
  tiga resource teks lama yang dipertahankan).
- Emulator Android 15/API 35: **OK (7 tests)**, 46,744 detik, tanpa kegagalan.
  Dua HomeMotionTest membuktikan pixel panel berubah antar-frame, area klik menu
  tetap stabil, klik berfungsi, serta mode tanpa animasi statis/langsung dapat dipakai.
  GuestFlowTest (room demo 4 pemain), dua VisualExperienceTest (teks terbesar dan
  solo 10 soal), serta dua GameFeedbackEffectsTest (native audio dan pemulihan
  state suara) semuanya lulus. Pengujian suara yang tertunda pada v0.02 kini juga
  berhasil dijalankan. Log: `.tmp/ui-instrumentation-v0.3.2.log`.
- APK release versi **0.3.2**, versionCode **5**, ukuran **2.778.630 byte (2,65 MiB)**.
  Output Gradle: `app/build/outputs/apk/release/app-release.apk`.
  Salinan dengan nama versi: `build/outputs/releases/duelmu-v0.3.2-release.apk`.
- `apksigner verify --verbose`: berhasil, Signature Scheme v2, satu penanda tangan,
  menggunakan konfigurasi keystore milik pengguna yang sama.
- Release berhasil dipasang dan dibuka di emulator: cold launch status `ok`,
  TotalTime 1578 ms. Dua tangkapan layar release sudah diperiksa secara visual;
  piala, dekorasi tepi, kartu menu, dan teks tetap rapi.
- Pratinjau rekaman layar menu release:
  `build/outputs/ui-preview-v0.3.2/menu-animation.mp4`.
  Screenshot: `menu-first.png` (saat animasi masuk), `menu-second.png` (setelah
  animasi masuk), di direktori yang sama. Emulator QA dihentikan setelah verifikasi.
- Metadata versi, ukuran, SHA-256 APK, dan ringkasan hasil:
  `build/outputs/duelmu-v0.3.2-artifacts.json`.
- Tidak ada pengujian unit/domain yang diulang untuk perubahan visual ini;
  aturan permainan dan kode jaringan tidak berubah. Perangkat fisik belum diuji.

## v0.04 — 8 November 2026 (Asia/Jakarta)

Permintaan: musik latar tenang untuk menu, musik kompetitif ketika bermain,
sakelar mematikan musik latar di Pengaturan, lalu build APK debug dan release.

### Cadangan dan lingkup

Delapan file lama dicadangkan sebelum modifikasi di `.rollback/v0.04/`, dengan
jalur relatif yang sama. APK debug/release yang ada sebelum build disimpan di
`.rollback/v0.04/artifacts/`. Catatan dan cadangan versi sebelumnya dipertahankan.
Bank soal, skor, timer, protokol/koneksi room, riwayat, manifest/izin, efek suara
jawaban/tombol, dan animasi menu tidak dimodifikasi.

### Perubahan terperinci

1. `tools/generate_background_music.py` (baru): komposisi musik orisinal secara
   lokal, menggunakan pustaka standar Python dan encoder FFmpeg/Vorbis. Rendering
   stereo 22050 Hz; not dengan envelope halus, bass/pad/pluck dan perkusi sintetis.
   Tidak ada unduhan musik atau dependensi runtime baru pada aplikasi.
2. `app/src/main/res/raw/music_menu.ogg` (baru): loop 24,62 detik, 78 BPM,
   harmoni major seventh, pad hangat, melodi lembut, dan perkusi ringan.
3. `app/src/main/res/raw/music_competitive.ogg` (baru): loop 30 detik, 128 BPM,
   arpeggio minor, bass ritmis, kick/snare/hi-hat untuk suasana kompetitif.
   Total kedua berkas sekitar 249 KiB; semua audio tetap offline dalam APK.
4. `presentation/components/BackgroundMusic.kt` (baru): satu MediaPlayer looping
   dengan prepareAsync untuk streaming aset musik lokal, volume latar lebih rendah
   daripada efek jawaban (menu 0,30; kompetitif 0,28). Pemain lama dilepas sebelum
   lagu berbeda dimuat, sehingga musik tidak menumpuk. Callback yang terlambat dari
   pemain yang sudah dilepas diabaikan; kegagalan audio tidak mengubah permainan.
5. File yang sama mengelola audio focus: pause ketika kehilangan focus sementara,
   volume turun saat duck, resume ketika focus kembali jika aplikasi masih aktif
   dan musik diizinkan. Focus permanen yang hilang tidak langsung direbut kembali.
   Pemain dihentikan/dilepas ketika musik dimatikan, dan resource/focus dilepas pada
   dispose activity. Tidak ada service yang memutar musik ketika aplikasi ditinggal.
6. `MusicScenes` dan `BackgroundMusicScene` pada file yang sama mengatur pemilik
   musik per destination: layar yang masuk mendapat prioritas, pembaruan/dispose
   layar yang keluar tidak menimpa musik layar baru. Transisi antar-menu dengan
   lagu sama mempertahankan pemain/posisi, bukan memulai lagu berulang-ulang.
7. `MainActivity.kt`: membuat satu controller musik dan menghubungkannya dengan
   lifecycle activity. Menunggu pembacaan pengaturan tersimpan sebelum mengaktifkan
   musik, agar preferensi mute tidak sempat bocor ketika aplikasi dibuka ulang.
8. `presentation/navigation/AppNavigation.kt`: musik tenang pada Home, setup,
   masuk/buat/gabung/lobi room, hasil, riwayat, pengaturan, serta privasi.
9. `presentation/solo/SoloScreens.kt`: musik kompetitif selama latihan berjalan,
   kembali tenang ketika loading/error atau latihan selesai.
10. `presentation/room/MatchPlayScreen.kt`: musik kompetitif pada PREPARING,
    QUESTION dan REVEAL; kembali ke musik tenang ketika pertandingan selesai,
    dibatalkan, gagal, atau sedang menyambung ulang. Keputusan hanya mempengaruhi
    audio; state sesi dan navigasi tetap seperti sebelumnya.
11. `data/settings/SettingsRepository.kt`: key DataStore baru `music_enabled`,
    field musicEnabled (default true), serta setMusic. Preferensi musik terpisah
    dari sound_enabled dan haptics_enabled; preferensi lama tidak dihapus/ditimpa.
12. `presentation/settings/SettingsScreen.kt`, `res/values/strings.xml`: sakelar
    “Musik latar” dan deskripsi; label sakelar suara lama menjadi “Efek suara” agar
    pengguna bisa membedakan musik dan efek. Kontrol tetap bisa di-scroll saat teks
    diperbesar dan menggunakan feedback tombol yang sudah ada.
13. `app/build.gradle.kts`: versionCode 5 → 6, versionName 0.3.2 → 0.3.3;
    konfigurasi signing, dependensi, serta ID paket tetap.
14. `app/src/test/java/id/eduduel/app/presentation/MusicScenesTest.kt` (baru):
    uji pergantian pemilik ketika transisi, fallback, pergantian musik setelah
    selesai, dan pengaturan musik/efek yang terpisah.
15. `app/src/androidTest/java/id/eduduel/app/BackgroundMusicTest.kt` (baru):
    pemutaran native kedua track, pergantian lagu, mute, pause/resume foreground,
    kehilangan/pemulihan audio focus, duck, dan pelepasan resource.
16. `app/src/androidTest/java/id/eduduel/app/MusicSettingsTest.kt` (baru):
    perubahan sakelar musik bertahan setelah activity dibuat ulang dan tidak
    mengubah preferensi efek suara; nilai semula dipulihkan setelah tes.

Semua jalur `presentation/` dan `data/` di atas berada di
`app/src/main/java/id/eduduel/app/`; `res/` berada di `app/src/main/`.

### Cara memulihkan v0.04

Salin kembali delapan file asli di `.rollback/v0.04/` ke jalur relatif yang sama.
BackgroundMusic.kt, generator, dan dua aset musik baru dapat tetap disimpan sebagai
kode/aset tidak terpakai. Nonaktifkan tiga file tes musik baru (misalnya ekstensi
`.kt.disabled`) sebelum build tes versi lama karena field/setMusic belum ada pada
pengaturan lama. Menghapus file/cadangan memerlukan konfirmasi pemilik.
Build ulang memakai keystore yang sama; jangan menghapus instalasi/data pengguna
untuk downgrade tanpa konfirmasi.

### Verifikasi v0.04

- Rendering audio: stereo 22050 Hz, puncak PCM 0,78 tanpa clipping; RMS menu 0,185,
  kompetitif 0,139. Durasi 24,62 dan 30 detik; file Ogg Vorbis berhasil dihasilkan.
- Hasil build dan pengujian akhir ditambahkan setelah pemeriksaan selesai.

### Hasil akhir verifikasi v0.04

- Build `assembleDebug`, `assembleRelease`, `assembleDebugAndroidTest`,
  `testDebugUnitTest`, dan `lintDebug`: **BUILD SUCCESSFUL**.
- Unit test: **83 total, 82 lulus, 1 dilewati, 0 gagal/error**, termasuk lima
  MusicScenesTest baru. Lint: **0 error, 5 warning** yang sama seperti sebelumnya.
- Emulator Android 15/API 35: **OK (9 tests)**, 65,887 detik, tanpa kegagalan.
  BackgroundMusicTest membuktikan kedua track dapat disiapkan/diputar native,
  berpindah lagu, pause/resume foreground, mute/re-enable, kehilangan/pemulihan
  audio focus, duck, dan close yang aman. MusicSettingsTest membuktikan sakelar
  bertahan setelah activity recreation dan tidak mengubah preferensi efek suara.
  Tujuh pengujian lama (animasi, aksesibilitas teks, room demo, solo, suara)
  juga tetap lulus. Log: `.tmp/ui-instrumentation-v0.3.3.log`.
- FFprobe: dua file valid Ogg Vorbis stereo 22050 Hz, durasi 24,615374 dan 30 detik.
  `aapt2 dump resources` pada release memastikan music_menu dan music_competitive
  tetap dipaketkan setelah R8/resource shrinking.
- APK versi **0.3.3**, versionCode **6**:
  - Debug: `build/outputs/releases/duelmu-v0.3.3-debug.apk`, **22.312.805 byte
    (21,28 MiB)**; output Gradle tetap `app/build/outputs/apk/debug/app-debug.apk`.
  - Release bertanda tangan: `build/outputs/releases/duelmu-v0.3.3-release.apk`,
    **3.034.930 byte (2,89 MiB)**; output Gradle tetap
    `app/build/outputs/apk/release/app-release.apk`.
- `apksigner verify --verbose`: berhasil, Signature Scheme v2, satu penanda tangan,
  menggunakan keystore pengguna yang sama.
- Release berhasil dipasang dan dibuka di emulator: status `ok`, cold launch
  TotalTime 1568 ms. Pemeriksaan Audio Focus menunjukkan USAGE_GAME /
  CONTENT_TYPE_MUSIC untuk paket id.eduduel.app saat foreground; focus stack
  kosong setelah aplikasi dipindah ke background. Bukti:
  `.tmp/music-focus-foreground.txt`, `.tmp/music-focus-background.txt`.
- Screenshot pengaturan dengan ukuran teks terbesar sudah diperiksa:
  `build/outputs/ui-preview-v0.3.3/music-settings.png`; kedua sakelar musik/efek
  terbaca dan terpisah. Emulator QA dihentikan setelah verifikasi.
- Metadata versi, ukuran, SHA-256, durasi/tempo musik dan hasil tes:
  `build/outputs/duelmu-v0.3.3-artifacts.json`.
- Pengujian speaker/headphone dan panggilan pada HP fisik belum dilakukan;
  pemeriksaan decoder/pemutaran/lifecycle/focus di atas memakai emulator.

## v0.05 — 10 November 2026 (Asia/Jakarta)

Permintaan: menambah 80 soal BERGAMBAR (dari ZIP/PDF kickstart) dan 80 soal MINI-GAME baru (satu gameplay
khas per mapel) sehingga bank menjadi 560 soal, plus ghost multiplayer simetris (LIVE/FAIR) di room 2–4 pemain.
Versi aplikasi 0.3.3 (versionCode 6) → **0.4.0 (versionCode 7)**; protokol room v2 → **v3**; bank
`2026.11.07-r1` (schema 1, 400) → `2026.11.10-r560` (schema 2, 560).

### Cadangan sebelum modifikasi

Salinan asli (dari commit 4607b47) semua file yang DIUBAH disimpan di `.rollback/v0.05/` dengan struktur
direktori yang sama (36 file, termasuk `app/src/main/assets/questions.json` 400 soal). File BARU tidak memiliki
cadangan; untuk rollback hapus file berikut: `app/src/main/assets/images/`, `content/`, `domain/Grading.kt`,
`domain/game/`, `net/room/Ghosts.kt`, `presentation/components/AssetImage.kt`, `presentation/game/`,
`tools/game_content/`, `tools/pngutil.py`, `tools/validate_visual_pack.py`, `tools/validate_game_pack.py`,
`docs/GAME_AND_GHOST_ARCHITECTURE.md`, `docs/CONTENT_560_QA.md`, `docs/QA_VISUAL_REPORT.md`, serta tes baru
`GameEngineTest.kt` dan `GhostMultiplayerTest.kt`. Tidak ada data pengguna, riwayat, atau soal lama yang dihapus.

### Perubahan terperinci

1. Data: `content/visual/` = salinan utuh ZIP (JSON + 80 PNG); `content/game/game_questions_80.json` dihasilkan
   `tools/game_content/generate_game_questions.py` (konten ditulis manual; solusi dibuktikan mesin cermin Python).
   `tools/build_question_asset.py` menggabungkan 400 + 80 + 80, menyalin PNG ke `assets/images/`, menulis
   `assetManifest` (SHA-256 PNG) ke bank, dan GAGAL bila 400 soal lama berubah (sidik jari).
2. Domain: `QuestionType` + isi tersegel `McqBody`/`GameBody`; `MatchConfig` menambah `mode` (default KLASIK)
   dan `ghostMode` (default LIVE); `Grader` tunggal; `SoloEngine` mendukung game + tutorial yang menahan timer.
3. Empat mesin game deterministik (`domain/game/`) dan UI Compose Canvas (`presentation/game/`).
4. Loader bank schema 1 & 2, decoder ketat (tanpa ignoreUnknownKeys), verifikasi semua PNG saat bank dimuat.
5. Protokol v3 + sesi Host/Tamu: seed ronde, tutorial, GAME_SUBMIT (Host memutar ulang masukan), relay
   GAME_STATE_UPDATE/GAME_ACTION ke semua peserta lain, sensor FAIR berlapis, rate-limit, validasi ghost.
6. UI: pemilih jenis soal & mode ghost (lobi/solo), gambar soal + zoom, tutorial, ghost berwarna dengan label,
   pengaturan ghost ON/OFF + opasitas 15–30% + bantuan sentuh, hasil ronde/riwayat menampilkan tipe & detail game.
7. Tes: dataset 560, mesin game, mode/solo, ghost 4 pemain, protokol v3 (118 unit test).

### Cara rollback

```bash
cp -r .rollback/v0.05/. .          # kembalikan 36 file yang diubah
rm -rf app/src/main/assets/images content app/src/main/java/id/eduduel/app/domain/game \
       app/src/main/java/id/eduduel/app/presentation/game tools/game_content
rm app/src/main/java/id/eduduel/app/domain/Grading.kt app/src/main/java/id/eduduel/app/net/room/Ghosts.kt \
   app/src/main/java/id/eduduel/app/presentation/components/AssetImage.kt \
   app/src/test/java/id/eduduel/app/domain/GameEngineTest.kt app/src/test/java/id/eduduel/app/net/GhostMultiplayerTest.kt
python tools/build_question_asset.py --check   # harus OK untuk bank 400 lama
```

Semua HP dalam satu room harus memakai versi yang sama (protokol & hash bank berbeda ditolak dengan jelas).
