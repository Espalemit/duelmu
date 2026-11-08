# PROMPT UNTUK CLAUDE CODE: EDUDUEL ANDROID - SEMUA PEMAIN GUEST + BLUETOOTH + FIREBASE SPARK

Anda adalah **Senior Android Engineer, Android Bluetooth Engineer, Firebase Architect, dan QA Engineer**. Kerjakan implementasi lengkap aplikasi Android native bernama sementara **EduDuel** di direktori kerja ini. Output utama harus berupa **source code Android yang benar-benar bisa di-build**, bukan sekadar desain/pseudocode. Gunakan bahasa Indonesia untuk aplikasi dan dokumentasi.

**Mulailah dengan memeriksa file-file berikut yang disediakan dalam direktori kerja:**

1. `bank_soal_duel_400.pdf` — dokumen referensi yang berisi 400 soal pilihan ganda dan kunci jawaban pada lampiran.
2. `bank_soal_duel_400.json` — dataset terstruktur dari PDF: ID, mata pelajaran, jenjang, topik, kesulitan, pertanyaan, opsi A-D, kunci jawaban, waktu, dan pembahasan sebagian soal.
3. `bank_soal_duel_400.csv` — versi tabular untuk verifikasi data.
4. `README_MULAI_DISINI.md` — orientasi paket.

Jika file telah dipindahkan, cari berdasarkan nama file tersebut di workspace. **Jangan mengarang soal sendiri sebagai pengganti dataset.** PDF menjadi rujukan editorial, JSON menjadi input utama aplikasi, dan CSV membantu pemeriksaan. Jika ada perbedaan isi atau kunci antara PDF, JSON, dan CSV, catat pada `docs/DATA_ISSUES.md` dan perbaiki dengan verifikasi, bukan memilih sembarang. Pertahankan semua 400 soal yang valid.

## A. TARGET PRODUK

Buat aplikasi Android quiz multiplayer **1 lawan 1 melalui Bluetooth Classic**. Dua pengguna Android terdekat bertanding menjawab **soal yang sama dengan urutan dan pilihan jawaban yang sama**, bahkan **tanpa internet**. **SEMUA pemain selalu Guest**, tanpa login, registrasi, akun anonim Firebase, atau kewajiban membuat profil. Sebelum **setiap pertandingan baru**, tiap pemain bisa mengisi/mengubah **nama panggilan (nickname)** sendiri. Firebase Spark Plan digunakan untuk keperluan distribusi beta dan, bila dibutuhkan, hosting halaman kebijakan privasi; **bukan untuk autentikasi atau penyimpanan identitas pemain**.

Fitur inti:

- Beranda: Latihan Solo, Tantang Teman via Bluetooth, Riwayat Lokal, Pengaturan. **Tidak ada halaman Profil/Akun/Login/Register.**
- Empat mapel: **Fisika, Biologi, Kimia, Matematika**.
- Jenjang **SMP** atau **SMA**, topik, dan tingkat kesulitan.
- Latihan solo offline, umpan balik benar/salah, ringkasan hasil.
- Duel Bluetooth 1 vs 1: pemain memilih **Tantang Teman**, mengisi atau mengedit nickname, lalu memilih **Buat Pertandingan (Host)** atau **Gabung Pertandingan (Join)**; pairing dengan konfirmasi pengguna, lobi dengan kedua nickname, siap bertanding, ronde, skor sementara, hasil akhir, tantang ulang. **Host/Join adalah peran koneksi Bluetooth; keduanya tetap Guest sebagai identitas aplikasi.**
- Jumlah soal default **10 soal per pertandingan**; sediakan konfigurasi 5/10/15 sesuai jumlah soal yang tersedia dalam filter. Jika sebuah kombinasi filter hanya menyediakan sedikit soal, tampilkan jumlah tersedia, cegah permintaan yang mustahil, dan sarankan mode Campuran. **Tidak semua kombinasi mapel-jenjang memiliki soal untuk ketiga tingkat kesulitan**, jadi jangan mengasumsikan jumlah minimal per tingkat.
- Bank soal langsung tersedia setelah instalasi menggunakan asset JSON lokal. Tidak perlu mengunduh PDF/soal dari server saat bertanding.
- Dukungan Portrait, dark mode, tampilan Material 3, loading/error/empty state, aksesibilitas dasar.
- Tidak perlu chat, iklan, pembelian, real-time multiplayer online, login, registrasi, leaderboard global, atau fitur berbayar untuk MVP.

## A.1. IDENTITAS GUEST DAN NAMA PEMAIN (KEHARUSAN UTAMA)

**Prinsip produk:** tidak ada sistem akun, layar autentikasi, SSO, sign-in anonim, session login, atau identitas pengguna yang harus diverifikasi. Pengguna langsung dapat membuka beranda dan latihan solo tanpa mengisi nama. Nama hanya diperlukan saat akan mengikuti duel.

**Alur pertandingan yang wajib:**

1. Pada beranda, ketuk **Tantang Teman**.
2. Tampilkan langkah **Isi Nama untuk Pertandingan Ini** dengan kolom nickname yang jelas, misalnya `Nama kamu` dan tombol `Lanjutkan`.
3. Nama wajib untuk duel, 2-20 karakter Unicode yang terlihat setelah `trim`; larang hanya spasi, karakter kontrol, dan karakter newline. Tampilkan pesan validasi dalam bahasa Indonesia. Batasi panjang berdasarkan Unicode code point/grapheme secara aman agar emoji tidak memotong surrogate pair.
4. Boleh mengisi nama berbeda pada **setiap pertandingan**. Opsional: tawarkan nama terakhir sebagai nilai awal dengan **DataStore lokal saja**, tetapi **tetap perlihatkan kolom edit nickname setiap kali** pemain memulai pertandingan baru. Jangan mewajibkan nama permanen.
5. Setelah nama valid, tampilkan **Buat Pertandingan (Host)** dan **Cari/Gabung Pertandingan (Join)**. Keduanya menggunakan alur Guest yang sama.
6. Dalam handshake Bluetooth `HELLO`/`HELLO_ACK`, kirim nama tampilan yang sudah divalidasi beserta `playerSessionId` acak yang dibuat di perangkat untuk sesi, **bukan Firebase UID, akun, atau MAC address**. Patuhi batas ukuran dan validasi nickname yang diterima dari lawan.
7. Lobi menampilkan nickname kedua pemain, peran Host/Join, koneksi, dan status READY. Beri tombol **Ubah Nama** sebelum READY; kirim pembaruan nama secara konsisten ke lawan, dan hindari perubahan nama saat pertandingan telah dimulai.
8. Saat duel tampilkan kedua nama di papan skor; pada halaman hasil dan riwayat lokal tampilkan nama pemain yang digunakan **dalam pertandingan itu**, bukan label `Guest 1` dan `Guest 2` kecuali fallback ketika koneksi tidak memiliki nama valid.
9. Nama yang identik **diperbolehkan**. Pembedaan teknis menggunakan `playerSessionId`/peran Host-Join yang hanya berlaku pada pertandingan ini, tidak menggunakan nama sebagai primary key, tidak memerlukan identitas global.
10. Pada **Tantang Ulang**, pertahankan nama yang sama sebagai default dan sediakan opsi **Ubah Nama** sebelum ronde baru disetujui. Setelah selesai, pemain bebas kembali ke beranda tanpa logout.
11. Riwayat dan ringkasan menang/kalah/seri tersimpan **di perangkat lokal** menggunakan Room/DataStore. Simpan ID pertandingan acak, kedua nickname sebagai snapshot, waktu, konfigurasi, skor, dan hasil. Sediakan hapus riwayat. Tidak ada upload/sinkronisasi ke cloud.
12. EduDuel adalah permainan edukasi untuk pelajar: jangan minta data pribadi seperti email, nomor telepon, tanggal lahir, sekolah, lokasi, foto profil, atau kontak. Jangan gunakan nickname untuk profiling, ranking global, atau pencarian pemain.

**Acceptance criterion non-negotiable:** instal APK baru -> buka -> langsung beranda -> kedua HP pilih Tantang Teman -> masing-masing memasukkan nama bebas -> Host/Join -> nama tampil di kedua HP -> tanding tanpa internet, tanpa login, tanpa konfigurasi Firebase. Uji perubahan nama pada pertandingan selanjutnya.

## B. TUMPUKAN TEKNOLOGI

1. **Kotlin + Jetpack Compose + Material 3**, single-activity, Navigation Compose.
2. Arsitektur MVVM atau Clean Architecture ringan: `data`, `domain`, `presentation`, `bluetooth`, `core`; modul `firebase` **tidak diperlukan** kecuali ada integrasi tambahan yang benar-benar opsional.
3. Kotlin coroutines + Flow/StateFlow; lifecycle-aware collection.
4. Room atau DataStore untuk progres/riwayat pertandingan lokal; JSON asset yang diimpor/diindeks secara efisien.
5. Android Bluetooth Classic RFCOMM `BluetoothServerSocket` / `BluetoothSocket`, terpisah dari UI thread, dengan `InputStream` dan `OutputStream` serta coroutines/IO dispatcher yang benar.
6. **JANGAN tambahkan Firebase Authentication (termasuk signInAnonymously), Google Sign-In, Cloud Firestore untuk identitas/skor, atau database user.** Firebase App Distribution untuk rilis beta **dapat digunakan tanpa memasukkan Firebase SDK ke gameplay**. Jika menambahkan SDK Firebase untuk diagnostik opsional, gunakan SDK resmi dan beri keputusan opt-in yang sesuai untuk aplikasi pelajar; jangan menjadikannya syarat aplikasi berfungsi.
7. Firebase **Spark Plan** adalah batasan keras: **jangan mensyaratkan Cloud Functions, Cloud Run, Firebase App Hosting, Cloud Firestore, autentikasi nomor HP, layanan berbayar, atau project Blaze**. Layanan database cloud tidak diperlukan pada MVP ini.
8. Gunakan Android SDK, AGP, Kotlin, Compose, Gradle, dan (hanya jika relevan) Firebase SDK versi **stabil kompatibel yang mutakhir** setelah mengecek sumber/dokumentasi resmi. Jangan memakai dependency atau API deprecated tanpa alasan.
9. `minSdk` disarankan API 26 atau lebih baru, `compileSdk`/`targetSdk` sesuai kebijakan Android saat implementasi. Dokumentasikan alasan pilihan dan perilaku izin untuk Android 8-11 vs Android 12+.
10. **Build utama harus berjalan sepenuhnya tanpa `google-services.json`**, internet, Firebase SDK, akun, maupun server. Tidak perlu membuat flavor Firebase bila hanya menggunakan Firebase App Distribution. Jika menyertakan integrasi diagnostik opsional, isolasikan dalam flavor/config dan pastikan build utama tidak memerlukannya.

## C. PENGOLAHAN PDF MENJADI SOAL APLIKASI

### Validasi dataset

- Buat `tools/validate_question_bank.py` (atau alat setara) yang membaca file JSON dan, jika memungkinkan, mengekstrak isi PDF teks menggunakan pustaka PDF yang cocok.
- Validasi ada tepat **400 soal**, masing-masing **100 soal/mapel**, **50 SMP + 50 SMA per mapel**, ID unik, empat pilihan berbeda, jawaban dalam A/B/C/D, `correctOptionId` sesuai `correctAnswer`, waktu sesuai kesulitan, dan tidak ada pertanyaan kosong.
- Cocokkan contoh acak dari PDF dengan JSON serta seluruh kunci jawaban jika PDF text parsing memungkinkan; jangan diam-diam menyatakan pemeriksaan lengkap apabila parser tidak bisa memeriksa semuanya.
- Gunakan JSON sebagai asset utama di `app/src/main/assets/questions.json`. Buat skrip import / build step reproduksibel. Hindari menyalin 400 soal ke source Kotlin satu per satu.
- Model domain tipe-kuat: `Question(id, subject, gradeBand, topic, difficulty, timeLimitSeconds, stem, options, correctOptionId, explanation?)`.
- Kolom `explanation` bisa `null`. **Jangan tampilkan pembahasan palsu**; jika kosong, tampilkan jawaban yang benar saja. Pastikan soal perhitungan menampilkan pembahasan bila tersedia.
- Soal yang memiliki bentuk matematis, pangkat, satuan dan rumus harus dirender dengan benar di UI; Unicode superscript atau rendering math sederhana tanpa bergantung jaringan.
- `bankVersion` berupa SHA-256 dataset atau version string dari file yang terverifikasi. Tetapkan versi di build untuk validasi dua perangkat.

### Pilihan soal dan keamanan

- Buat mode tunggal per mapel maupun Campuran. Buat selector SMP/SMA dan topik.
- Pilih soal tanpa pengulangan dalam satu pertandingan, gunakan seed acak yang dikendalikan Host.
- Host mengirimkan daftar `questionIds` + `bankVersion` + urutan opsi A-D. Klien harus memverifikasi semua ID dan hash dataset sebelum dimulai.
- Jangan mengirim jawaban benar pada pesan `QUESTION_START` atau sebelum kedua pengguna menjawab/timeout. Namun jujur: karena jawaban juga tersimpan offline di kedua APK, **pencegahan kecurangan sempurna mustahil** tanpa server tepercaya. Jelaskan keterbatasannya dan buat validasi konsistensi dengan model ancaman realistis.

## D. GAMEPLAY, TIMER, DAN SKOR

### Mekanisme ronde

- **Sebelum Host maupun Join masuk ke koneksi/lobi, setiap pemain mengisi atau mengedit nickname untuk duel ini.** Host lalu memilih mapel, jenjang, topik, kesulitan/campuran, dan 5/10/15 soal.
- Guest melihat aturan yang dipilih Host dan memberikan READY.
- Setiap ronde menampilkan pertanyaan dan opsi yang **identik** di kedua HP.
- Durasi per soal berdasarkan field `timeLimitSeconds`:
  - mudah = **60 detik**;
  - sedang = **90 detik**;
  - sulit = **120 detik**.
- Timer per ronde menggunakan `SystemClock.elapsedRealtime()` (monotonic time), bukan jam dinding atau Firestore timestamps. Kedua perangkat berusaha menyamakan awal ronde dengan handshake READY/ACK, komunikasi START, dan toleransi latensi; dokumentasikan bahwa latensi Bluetooth membuat sinkronisasi milidetik tidak dijamin.
- Waktu terus berjalan ketika aplikasi masuk background; simpan state untuk rotasi/perubahan konfigurasi. Hindari reset waktu akibat recomposition.
- Jawaban dikunci setelah satu pilihan; tidak boleh submit ganda. Timeout = salah / 0 poin.
- Jika kedua pemain telah menjawab, atau timer habis, perlihatkan jawaban benar, status, poin, dan lanjutkan setelah konfirmasi atau jeda singkat yang terkoordinasi.

### Formula skor yang deterministik

- `basePoints` = 100 (mudah), 200 (sedang), 300 (sulit).
- Jika jawaban salah atau timeout, skor ronde = 0.
- Jika benar, `speedBonus = floor(0.5 * basePoints * remainingMs / totalMs)`, dibatasi dalam `[0, basePoints/2]`.
- Skor ronde = `basePoints + speedBonus`.
- Waktu jawaban, ID soal, dan status benar/salah ditampilkan secara konsisten pada kedua perangkat.
- Pemenang = skor total tertinggi. Skor sama = SERI; jangan mengarang pemenang. Statistik menang/kalah/seri dan snapshot kedua nickname disimpan **hanya secara lokal**, tanpa sinkronisasi akun/cloud.

## E. BLUETOOTH CLASSIC RFCOMM

**WAJIB Bluetooth Classic point-to-point**, bukan sekadar API Nearby Connections yang bisa menggunakan Wi-Fi atau LAN secara otomatis.

- Dukungan perangkat fisik Android yang memiliki Bluetooth Classic dan API RFCOMM.
- Host: `BluetoothAdapter.listenUsingRfcommWithServiceRecord(appName, APP_UUID)` lalu `accept()` pada thread IO; tutup listening socket setelah mendapat koneksi.
- Guest: discovery/perangkat berpasangan, pilih Host, `createRfcommSocketToServiceRecord(APP_UUID)`, dan `connect()` di IO thread.
- Gunakan UUID tetap khusus aplikasi dan nama service yang eksplisit, bukan UUID layanan sistem lain.
- Utamakan secure RFCOMM (pairing/enkripsi) dan konfirmasi identitas perangkat pada UI. Jangan menggunakan socket insecure secara default.
- Permissions Android 12+ sesuai fitur: `BLUETOOTH_SCAN` (mencari), `BLUETOOTH_CONNECT` (berkomunikasi), `BLUETOOTH_ADVERTISE` (jika membuat perangkat discoverable). Minta izin Nearby Devices saat runtime, tangani penolakan dan pencabutan izin.
- Compatibility Android 11 dan sebelumnya: deklarasikan permission Bluetooth lama dengan `maxSdkVersion=30`, serta izin lokasi saat scanning sesuai aturan versi Android. Gunakan `neverForLocation` hanya jika benar bahwa hasil scan tidak pernah dipakai untuk menentukan lokasi.
- Jangan memaksa pengguna memberi akses lokasi jika hanya menghubungkan perangkat yang sudah dipasangkan dan izin tersebut tidak diperlukan pada versi OS-nya.
- Gunakan message protocol berbasis JSON UTF-8 dengan **4-byte length-prefix** atau framing yang aman; jangan mengasumsikan satu `read()` = satu pesan. Batasi panjang frame, parsing defensif, dan validasi versi protokol.
- Definisikan pesan minimal: `HELLO`, `HELLO_ACK`, `PLAYER_NAME_UPDATE`, `LOBBY_CONFIG`, `READY`, `MATCH_SETUP`, `BANK_CHECK`, `QUESTION_PREPARE`, `QUESTION_READY`, `QUESTION_START`, `ANSWER`, `ROUND_RESULT`, `NEXT_ROUND`, `MATCH_END`, `ERROR`, `PING`, `PONG`, `DISCONNECT`. `HELLO`/`HELLO_ACK` wajib mengangkut nickname valid dan `playerSessionId` acak, bukan ID akun.
- Tiap pesan penting memuat `matchId`, `roundIndex`, `sequenceNumber`/UUID, protokol versi, dan payload jelas. Cegah pesan duplikat, out-of-order, jawaban telat, serta `roundIndex` salah.
- Implementasikan reconnect best-effort dengan batas waktu dan state yang jelas, serta pembatalan pertandingan/hasil parsial ketika koneksi tidak bisa pulih. Jangan diam-diam memberikan kemenangan otomatis akibat kesalahan transport tanpa aturan yang disetujui.
- Tutup socket dan batalkan coroutine secara rapi pada keluar sesi, error, atau Bluetooth dimatikan. Jangan menjalankan blocking socket di main thread.
- Bila kedua versi bank soal berbeda, **tolak pertandingan** dengan pesan yang jelas dan arahkan untuk memperbarui aplikasi, bukan menampilkan soal yang berbeda.
- Latih/test dua perangkat fisik, bukan hanya emulator. Siapkan dokumentasi troubleshooting pairing, izin, discovery, Bluetooth nonaktif, dan disconnect.

Dokumentasi acuan:
- https://developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices
- https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
- https://developer.android.com/develop/connectivity/bluetooth/transfer-data

## F. FIREBASE SPARK - DISTRIBUSI BETA, BUKAN LOGIN

**Jangan membangun login/registrasi, Firebase Authentication (termasuk anonymous auth), Google Sign-In, Cloud Firestore, Realtime Database, penyimpanan profil cloud, atau sinkronisasi statistik cloud.** Bahkan jika ada layanan Firebase yang kebetulan menyediakan mode anonim, **jangan menggunakannya**: "Guest" di sini hanya nama tampilan lokal yang diisi sendiri menjelang pertandingan, bukan akun Firebase anonim.

- Firebase Spark dipakai untuk **mendaftarkan aplikasi Android** dan menyalurkan APK beta melalui **Firebase App Distribution**, bukan menjadi server duel atau sistem user.
- Firebase App Distribution adalah saluran distribusi tester dan **tidak memerlukan Firebase Authentication untuk pemain**; autentikasi di Firebase Console hanya untuk **developer/pengelola rilis**, tidak boleh muncul pada aplikasi yang dipakai siswa.
- **Firebase SDK dan `google-services.json` tidak wajib** jika aplikasi hanya memanfaatkan App Distribution melalui Console/CLI. Jangan memasang SDK atau plugin Google Services tanpa fungsi spesifik yang memerlukannya. Jelaskan perbedaan registrasi Android app di Firebase Console dan integrasi Firebase SDK ke APK.
- Jika perlu halaman kebijakan privasi/landing page, gunakan **Firebase Hosting statis** dalam kuota Spark. Ini terpisah dari aplikasi Android.
- Firebase Analytics/Crashlytics **bukan persyaratan MVP**. Jika ingin menawarkan sebagai add-on, jelaskan persyaratan SDK, pengungkapan data, pilihan privasi, aturan untuk aplikasi yang menyasar anak/pelajar, dan panduan menonaktifkannya. Jangan menambahkan pelacakan tanpa pertimbangan kebijakan.
- Semua data pertandingan, kedua nickname, skor, dan riwayat disimpan **lokal**. Tidak boleh ada endpoint terbuka yang menerima nickname atau skor tanpa kontrol akses; untuk MVP **tidak perlu Firestore ataupun `firestore.rules`**.
- Tidak boleh ada leaderboard online atau klaim statistik global terpercaya: nama Guest bisa sama, mudah diubah, dan tidak membuktikan identitas.
- Semua permainan harus tetap berfungsi jika koneksi internet hilang, kuota Firebase habis, atau Firebase project belum dibuat.
- Tetap dokumentasikan batas Spark/layanan yang dapat digunakan; cek dokumentasi resmi sebelum membuat klaim angka kuota/harga. Cloud Functions atau backend berbayar tidak boleh diam-diam ditambahkan.

Acuan resmi:
- https://firebase.google.com/pricing
- https://firebase.google.com/docs/projects/billing/firebase-pricing-plans
- https://firebase.google.com/docs/app-distribution/android/distribute-console
- https://firebase.google.com/docs/android/setup
- https://firebase.google.com/docs/hosting

## G. RANCANGAN UI / UX

Gunakan desain game edukasi bersih dengan aksen biru-hijau, tipografi terbaca dan area tap luas.

Screen minimal:
1. Splash singkat -> **beranda langsung tanpa login dan tanpa form nama awal**; nama hanya diminta saat hendak duel.
2. Home/dashboard.
3. Mapel → jenjang → topik/difficulty → pilih jumlah soal.
4. Solo quiz: pertanyaan, 4 tombol opsi, timer progres, nomor ronde, penjelasan saat akhir.
5. **Dialog/halaman Isi Nama Pemain sebelum setiap duel**, dengan nama terakhir (jika ada) yang selalu bisa diedit, validasi nama, lalu pilih Host/Join; berikutnya Bluetooth permission & pairing: aktifkan Bluetooth, scan, perangkat berpasangan.
6. Match lobby: kedua **nickname Guest**, peran Host/Join, konfigurasi identik, status koneksi, tombol **Ubah Nama** sebelum READY, dan READY.
7. Duel play: dua skor, timer, soal/opsi sama, state jawaban terkunci, pengungkapan jawaban setelah ronde.
8. Hasil duel: dua nickname, menang/kalah/seri, breakdown tiap ronde, rematch dengan opsi mengubah nickname, kembali ke beranda.
9. Riwayat offline dan statistik perangkat (bukan profil akun); setiap entri menggunakan nickname snapshot saat duel tersebut.
10. Pengaturan: ukuran teks dasar, suara/haptics opsional, hapus riwayat/nama terakhir lokal, info versi bank soal, privacy policy.

**DILARANG** membuat screen Login, Register, Account, Profil Cloud, Logout, atau Sync. Opsi rename ada **tepat sebelum memulai duel**, bukan tersembunyi dalam menu pengaturan.

Sediakan preview Composable untuk screen penting, serta string resources bahasa Indonesia.

## H. PENGUJIAN & KRITERIA PENERIMAAN

Implementasikan unit test dan, bila lingkungan mendukung, instrumented test/UI test:

- Dataset: tepat 400, masing-masing 100/mapel, 50 SMP + 50 SMA, empat opsi unik, satu benar, ID konsisten, pembahasan opsional, waktu 60/90/120.
- Solo: pilihan terkunci, timer tetap setelah rotasi, skor benar/salah/timeout, ulang latihan tanpa duplikasi soal pada sesi.
- Duel: dua perangkat menerima daftar ID dan urutan opsi yang sama, Host/Join READY, 10 ronde, skor sama, hasil menang/seri benar, serta menampilkan dua nickname yang diinput sebelum pertandingan.
- Permission: tolak izin, cabut izin, matikan Bluetooth, tidak ada perangkat ditemukan.
- Protokol: paket terpotong, dua frame dalam satu read, frame terlalu panjang, payload JSON rusak, pesan duplikat/telat, ketidaksesuaian bankVersion.
- Disconnect di tengah pertandingan, perpindahan background, recovery vs batal.
- Guest-only: pengguna baru langsung beranda tanpa akun, dua nama custom ditampilkan dan terpropagasi via HELLO, validasi nama kosong/panjang/Unicode, edit nama sebelum READY, nama identik tetap aman melalui `playerSessionId`, perubahan nama untuk pertandingan berikutnya, dan penyimpanan riwayat lokal yang menampilkan snapshot nama saat tanding.
- Firebase: APK berjalan tanpa `google-services.json`, tanpa Firebase Auth/Firestore, tanpa internet; prosedur App Distribution terdokumentasi dan tidak meminta pemain login.
- Lakukan perintah Gradle untuk build debug, test unit, lint dan release build bila signing disediakan. Jika lingkungan tidak memiliki Android SDK/perangkat, sebutkan keterbatasan verifikasi secara jujur dan berikan command menjalankannya di Android Studio.
- Jangan tinggalkan layar inti dalam bentuk TODO/pseudocode; bila fitur sekunder belum selesai, berikan daftar backlog secara jelas.

## I. TUTORIAL GO LIVE FIREBASE SPARK PLAN (WAJIB LENGKAP)

Buat file **`docs/GO_LIVE_FIREBASE_SPARK_ID.md`** dalam bahasa Indonesia dengan langkah bernomor, label menu Console, perintah terminal, dan troubleshooting untuk Windows 10/11 serta Linux:

1. Prasyarat: Android Studio, JDK/Android SDK yang kompatibel, Gradle wrapper, dua HP Android dengan Bluetooth Classic, akun Google **milik developer** untuk Firebase Console; cek versi resmi terbaru.
2. Jalankan proyek lokal **tanpa Firebase**, tanpa `google-services.json`, tanpa login/registrasi, dan demonstrasikan duel dengan dua nama Guest yang dapat disesuaikan.
3. Buat Firebase project di `https://console.firebase.google.com/` pada **Spark Plan**; pastikan tanpa billing account. Tegaskan ini akun **developer** untuk distribusi APK, bukan akun pemain.
4. Daftarkan aplikasi Android berdasarkan `applicationId` sama dengan Gradle, misalnya `id.eduduel.app`. Jelaskan bahwa registrasi untuk App Distribution tidak otomatis mengharuskan Firebase SDK atau `google-services.json` dalam APK. Jika SDK Firebase tidak digunakan, **jangan menginstruksikan pemasangan yang tidak dibutuhkan**.
5. **Tidak boleh mengaktifkan Firebase Authentication, Anonymous Auth, atau Firestore demi pemain Guest.** Tidak ada akun user, database cloud, atau rules yang perlu dideploy pada MVP.
6. Jika ingin halaman kebijakan privasi/landing page, jelaskan pengaturan **Firebase Hosting statis** dalam kuota Spark, deploy `firebase deploy --only hosting` untuk halaman web tersebut, dan pertegas bahwa Hosting **bukan untuk mempublikasikan APK**.
7. Build aplikasi Android `debug` untuk QA dan `release` APK/AAB dengan penandatanganan aman; sertakan command `gradlew.bat` dan `./gradlew`, output path aktual sesuai konfigurasi proyek, serta cara menjaga keystore/credentials dari Git.
8. Uji dua HP fisik: instal APK, izinkan Nearby Devices/Bluetooth sesuai versi Android, buka langsung tanpa login, ketik nickname berbeda di masing-masing HP, pilih Host dan Join, pairing, duel offline dengan mode pesawat (Bluetooth dinyalakan lagi), periksa nama pada lobi/skor/hasil, ubah nama untuk pertandingan selanjutnya, dan bandingkan skor.
9. **Firebase App Distribution** untuk beta: unggah APK ke Firebase Console > App Distribution atau CLI resmi bila cocok, atur daftar penguji, undang, instal, dan distribusikan build terbaru. Jelaskan distribusi ini **untuk tester**, bukan Play Store publik. **Undangan/login Google untuk tester yang menerima APK (jika diwajibkan penyedia distribusi) bukan login di dalam aplikasi EduDuel**.
10. Publikasi umum melalui **Google Play Console**: registrasi akun developer, verifikasi, store listing, privacy policy, Data safety, target API, AAB, signing, release tracks, testing requirements, review, dan rollout. Jelaskan biaya Play Console terpisah dari Firebase Spark; verifikasi kebijakan terbaru, bukan mengandalkan angka lama.
11. Pantau penggunaan Firebase Spark hanya untuk layanan yang benar-benar diaktifkan (mis. App Distribution/Hosting); duel lokal tidak bergantung kuota backend. Jangan menambahkan Analytics atau Crashlytics diam-diam untuk aplikasi yang ditujukan ke pelajar.
12. Prosedur pembaruan soal: validasi JSON, naikkan `bankVersion`, build/distribusikan APK baru, dan tolak duel antardua versi bank yang tidak cocok.
13. Troubleshooting: Bluetooth denied, pairing gagal, tidak ditemukan perangkat, pairing sudah tersimpan, perbedaan bankVersion, nama Guest tidak tersinkron, kedua nickname sama, `google-services.json`/Google Services error **hanya jika integrasi SDK memang diaktifkan**, App Distribution invitation tidak diterima, APK gagal diinstal, release signing, dan Play Console rejection.
14. Sertakan referensi resmi Firebase Pricing, Firebase App Distribution, Firebase Hosting, Android Bluetooth, Android Publish/Google Play. Bedakan dengan tegas antara akun **developer/tester untuk distribusi** dan tidak adanya **akun pemain** di dalam aplikasi.

Sediakan juga `docs/PRIVACY_POLICY_ID.md` yang menyatakan gameplay dan nickname disimpan lokal (serta menjelaskan layanan tambahan hanya jika benar-benar dipakai), dan `docs/RELEASE_CHECKLIST.md`. Jangan menyatakan "tidak mengumpulkan data sama sekali" bila memakai SDK yang mengirim diagnostik.

## J. URUTAN PELAKSANAAN

**Jangan berhenti hanya pada perencanaan.** Kerjakan dengan urutan:

1. Audit file input dan struktur folder; buat `docs/IMPLEMENTATION_PLAN.md` yang ringkas.
2. Validasi seluruh 400 soal, simpan laporan QA dataset.
3. Bootstrap Android Kotlin/Compose dan mode offline-demo.
4. Implementasikan model domain, bank soal, filter, latihan solo, timer, scoring engine.
5. Implementasikan sesi Bluetooth 1 vs 1 secara end-to-end dengan framing pesan dan state machine.
6. Implementasikan UI duel/lobi/hasil, Room/DataStore.
7. Finalisasi alur **Guest-only** (nickname setiap duel, tanpa Auth/Firestore) serta dokumentasikan konfigurasi Firebase Spark **khusus distribusi beta** dan Hosting statis opsional.
8. Tulis unit/instrumentation tests, perbaiki error build.
9. Tulis dokumentasi go-live Spark, beta App Distribution, dan Google Play publik.
10. Jalankan tes/build yang tersedia, laporkan hasil nyata beserta TODO yang belum dapat diuji karena keterbatasan perangkat/credential.

**Deliverable wajib:** source code proyek, Gradle wrapper, assets JSON lokal, validator dataset, unit tests (termasuk alur nickname Guest), `README.md`, `docs/GO_LIVE_FIREBASE_SPARK_ID.md`, `docs/RELEASE_CHECKLIST.md`, `docs/PRIVACY_POLICY_ID.md`, serta laporan hasil QA. **Jangan membuat `firestore.rules`/Firebase Auth/Firestore hanya demi memenuhi daftar deliverable lama.** Jika memiliki keterbatasan environment, prioritaskan **aplikasi offline + Bluetooth yang bisa di-build** dan dokumentasikan sisa pekerjaan tanpa mengklaim fitur sudah selesai.

**PENGINGAT UTAMA:** tidak ada login, registrasi, anonymous Firebase auth, akun, atau sinkronisasi profil. Kedua pemain adalah Guest dengan nickname yang wajib dapat mereka ganti tepat sebelum pertandingan. Semua riwayat lokal. Firebase Spark hanya menunjang proses distribusi/halaman statis opsional.

Mulai sekarang dengan membaca PDF, JSON, dan CSV, lalu langsung kerjakan aplikasinya.
