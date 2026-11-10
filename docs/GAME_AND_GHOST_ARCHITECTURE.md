# Arsitektur Mini-Game & Ghost Multiplayer (Duelmu 0.5.0)

Dokumen ini menjelaskan cara kerja soal bergambar, empat mesin mini-game, ghost multiplayer simetris,
protokol room v4, format menambah soal game, cara menguji di 2–4 HP, serta strategi migrasi/rollback bank.

## 1. Ringkasan

| Komponen | Lokasi | Inti |
|---|---|---|
| Model soal tipe-kuat | `domain/Models.kt` | `QuestionType` (`classic_mcq` / `visual_mcq` / `mini_game`) + isi tersegel `McqBody` (opsi A-D, kunci, `imageAsset`) atau `GameBody` (`GameSpec`, solusi referensi, teks solusi) |
| Penilaian tunggal | `domain/Grading.kt` | `Grader.grade()` dipakai Solo, Host (otoritatif) dan verifikasi Tamu |
| Mesin game (murni, deterministik) | `domain/game/GameEngines.kt` | `AsteroidEngine`, `TrajectoryEngine`, `MicroscopeEngine`, `LabEngine` + `GameEngines.evaluate/isWellFormed` |
| Konfigurasi game | `domain/game/GameSpecs.kt` | `AsteroidSpec`, `TrajectorySpec`, `MicroscopeSpec`, `LabSpec` (diskriminator JSON `kind`) |
| Ghost | `net/room/Ghosts.kt` | `GhostHub` (penyimpan per pemain), `GhostPrivacy` (sensor FAIR), `GhostValidator`, `GhostInterpolator`, `RateLimiter` |
| UI game | `presentation/game/*` | `GameArena` → `AsteroidGame` / `TrajectoryGame` / `MicroscopeGame` / `LabGame`, `GameTutorial`, `GhostLegend` |
| Soal bergambar | `presentation/components/AssetImage.kt` | Dekode malas dari `assets/`, `inSampleSize`, LRU cache ±1/16 heap, placeholder error, dialog pinch-zoom |

Semua pertandingan baru memakai **Campuran Tipe**, berstrata **6 klasik + 3 bergambar + 1 mini-game**.
Pilihan tipe kuis dihapus; nilai mode lama tetap bisa dibaca untuk riwayat. Konfigurasi route dan room
dinormalisasi ke MIXED, dan selector menerapkan komposisi baru termasuk jika menerima konfigurasi lama.
Tetap 10 ronde, durasi 60/90/120 dtk dan poin dasar 100/200/300 menurut kesulitan.

## 2. Empat mesin game

Semua penilaian memakai **masukan pemain** yang diskrit (indeks token, indeks parameter, koordinat dunia
bilangan bulat, jumlah ×1000) sehingga setiap HP menghitung hasil yang identik.

| Mapel | Mesin | Masukan (`GameSubmission`) | Berhasil bila | Kalah bila |
|---|---|---|---|---|
| Matematika | **Asteroid Solver**: geser pesawat / ketuk asteroid, tembak token berurutan | `hits`: indeks token yang terkena dan `-1` untuk tabrakan, berurutan | semua slot terisi berurutan dengan salah ≤ `maxWrong` | penalti ke-(maxWrong+1) atau waktu habis |
| Fisika | **Trajectory Lab**: atur sudut & kelajuan (atau vₓ & v_y), luncurkan | `shots`: indeks parameter per tembakan | salah satu tembakan (≤ `maxShots`) masuk radius sasaran | tembakan habis / waktu habis |
| Biologi | **Micro Hunter**: pan, zoom, fokus, tunjuk struktur | `taps`: (x, y dunia, zoom×100, fokus‰) + flag bantuan sentuh | tap tajam & cukup diperbesar di dalam/ dekat poligon target | tap salah ke-(maxWrong+1) / waktu habis |
| Kimia | **Reaction Lab**: seret reagen/alat, tetes, ulangi, kunci | `actions`: (indeks reagen/alat, jumlah×1000), `RESET` | semua `targets` terpenuhi pada state akhir (setelah reaksi pembatas) | target tidak tercapai / waktu habis |

Detail sains & toleransi:

- **Asteroid**: pemunculan asteroid deterministik dari seed ronde (`SplitMix64`, kantong token per siklus berisi setiap
  token jawaban 2× + setiap pengecoh 1×). Notasi dinormalisasi (minus Unicode, superscript); validator menolak pengecoh
  yang setara jawaban lewat evaluasi numerik di beberapa titik x. Urutan jawaban ambigu dihindari (mis. "akar lebih kecil dulu").
- **Trajectory**: satuan SI, tanpa hambatan udara, posisi analitik setiap Δt = 1/120 s (tanpa galat integrasi), uji kena
  = jarak segmen ke pusat sasaran ≤ radius (radius diumumkan di layar). `StrictMath` agar sin/cos identik di semua JVM.
  Dua sudut sah (θ dan 90° − θ) otomatis diterima bila keduanya mengenai sasaran; rintangan dipakai bila soal meminta
  lintasan tertentu. Validator memastikan selalu ada solusi tetapi ≤ 15% kombinasi yang kena.
- **Micro Hunter**: 20 ilustrasi preparat orisinal sebagai data vektor (`shapes`), daerah target/pengecoh dibuat dari
  geometri yang SAMA dengan gambar. Tap hanya dihitung bila |fokus − focusTarget| ≤ toleransi dan zoom ≥ `minTapZoom`;
  tap salah memberi umpan balik nama struktur yang disentuh. Opsi "Bantuan sentuh" menambah toleransi
  (`assistTolerance`) untuk objek kecil; tombol arah/zoom/fokus untuk kendala motorik.
- **Reaction Lab**: model ideal yang selalu ditampilkan (25 °C, larutan kuat terionisasi sempurna, volume aditif,
  ρ ≈ 1 g/mL, Kw = 10⁻¹⁴). pH dihitung `[H⁺] = (c + √(c² + 4Kw))/2` dari H⁺/OH⁻ bersih; reaksi berjalan sampai pereaksi
  pembatas habis (massa kekal — diuji). Target: konsentrasi (M atau %massa, ±toleransi relatif), rentang pH, jumlah
  mol sesudah reaksi, total reagen (titik ekuivalen ±0,1 mL), atau urutan alat pemisahan. Semua eksperimen virtual.

Skor game **sama** dengan MCQ: berhasil = poin dasar + bonus cepat ⌊½·poin·sisa/total⌋ (maks 50%); gagal/timeout = 0.
Karena itu satu ronde game tidak pernah bernilai lebih dari ronde MCQ dengan kesulitan sama.

## 3. Ghost multiplayer simetris

```
            ┌────────────── Host A (pemain + relay) ──────────────┐
            │ ghostHub: B, C, D        state lokal A: SOLID        │
            └──▲──────────┬───────────▲──────────┬───────────▲────┘
   GAME_STATE  │ relay ke │ semua     │ relay    │           │
   _UPDATE B   │ C, D     ▼ + ghost A │ ke B, D  ▼           │
            Tamu B                 Tamu C                 Tamu D
       ghostHub: A, C, D      ghostHub: A, B, D      ghostHub: A, B, C
```

1. Host memilih soal, **seed** arena/spawn per ronde, urutan, lalu menyiarkan `MATCH_SETUP` (berisi `seeds`).
2. `QUESTION_PREPARE` membawa `tutorialMs` (8 dtk pertama kali jenis game muncul, 3 dtk berikutnya). Tutorial tampil di
   SEMUA HP — termasuk Host — dan **waktu soal belum berjalan**. Setiap pemain mengirim `QUESTION_READY` saat menekan
   "Saya siap" atau tutorial habis; Host memulai saat semua siap (atau batas `prepareTimeout + tutorial`, jadi tidak deadlock).
3. `QUESTION_START` (dengan `seed`) → setiap HP memulai jam monotonic lokal. Pemain sendiri dirender **lokal-first**
   (tanpa menunggu jaringan).
4. UI memanggil `publishGhost(state)` setiap ±60 ms; sesi meng-*coalesce* dan mengirim paling banyak **8 Hz (Wi-Fi/demo)**
   atau **5 Hz (Bluetooth)**, hanya bila berubah. Event diskrit (tembak/luncur/tap/tuang) dikirim segera sebagai `GAME_ACTION`.
5. **Tamu → Host**: Host memvalidasi (ronde & match aktif, peserta, tipe game cocok, angka berhingga dalam rentang dunia,
   rate-limit 15 snapshot/s & 10 aksi/s per Tamu), **mengganti `playerSessionId` dengan identitas pengirim sebenarnya**,
   menyensor sesuai mode, menyimpan ke `ghostHub` miliknya (layar Host), lalu **me-relay ke semua peserta lain**.
6. **Host → semua Tamu**: ghost Host sendiri dikirim ke setiap Tamu — Host juga pemain yang tampil sebagai ghost.
7. Penerima membuang snapshot `seq ≤ seq terakhir` (terlambat/duplikat/out-of-order), snapshot ronde basi, dan
   ghost dirinya sendiri; lalu render dengan interpolasi 150 ms di belakang (antara dua sampel yang mengapit) agar
   jitter 100–200 ms tidak terlihat.
8. Koordinat yang dikirim selalu **dunia ternormalisasi 0..1** (bingkai mikroskop, posisi pesawat, alat lab),
   bukan piksel layar; setiap HP memetakan dunia → viewport-nya sendiri (`MicroscopeEngine.worldToScreen`).
9. Ghost **non-colliding**: digambar di kanvas tanpa penangkap sentuhan, tidak pernah mengubah state game lokal
   (tes `ghostsAreIsolated_...`). Pemain yang sudah mengunci tampil "selesai ✓" dengan ghost lebih pudar.
10. Jawaban benar/kunci/solusi **tidak pernah** ada di `GAME_STATE_UPDATE`, `GAME_ACTION`, `MATCH_SETUP`, atau
    `QUESTION_START`. Submisi semua pemain baru dikirim di `ROUND_RESULT` setelah ronde ditutup, agar setiap HP
    memverifikasi hasil dengan mesin yang sama.

### LIVE vs FAIR (diatur Host di lobi)

| | LIVE (default) | FAIR |
|---|---|---|
| Ghost terlihat (3 ghost bagi B di room 4 orang) | Ya | Ya |
| Posisi/gerak (pesawat, meriam, bingkai mikroskop, alat lab) | Ya | Ya |
| Label token yang ditembak, jumlah slot terisi/salah | Ya | **Disembunyikan** |
| Arah bidik & kelajuan meriam, lintasan proyektil lawan | Ya | **Disembunyikan** (meriam netral + efek luncur) |
| Kursor, titik tap, nilai fokus mikroskop | Ya | **Disembunyikan** (bingkai tetap) |
| Reagen yang dipegang, jumlah dituang, tingkat isi bejana | Ya | **Disembunyikan** |
| Indikator berhasil sebelum ronde ditutup | Ya | **Disembunyikan** |

Sensor FAIR diterapkan tiga lapis: pengirim (`handleGhostTick`), Host sebelum relay, dan penerima. Tes
`fairMode_hostRedactsMaliciousClient_andRejectsSpam` memakai klien mentah yang sengaja mengirim data LIVE.
Keterbatasan yang diketahui: di FAIR, posisi pesawat/bingkai mikroskop masih terlihat; pemain yang sangat jeli
bisa menebak area yang sedang diperiksa lawan. Ini sengaja (ghost tidak boleh dihapus) dan dijelaskan di UI.

### Pengaturan ghost lokal

Pengaturan → "Mini-game & ghost": **Tampilkan ghost** (ON/OFF), **opasitas 15–30 %** (default 25 %), dan
**Bantuan sentuh**. Disimpan di DataStore per HP; tidak memengaruhi HP lain maupun penilaian.

## 4. Protokol room v4 & keamanan skor

`PROTOCOL_VERSION = 3`. Klien 0.3.x (v2) ditolak di tingkat codec (`DecodeResult.VersionMismatch` →
`ERROR PROTOCOL_VERSION` → layar "versi aplikasi tidak cocok"). Bank berbeda tetap ditolak lewat `bankSha256`
saat HELLO dan BANK_CHECK.

| Pesan | Arah | Isi baru |
|---|---|---|
| `MATCH_SETUP` | Host → Tamu | `seeds` per soal; `optionOrders` kosong untuk soal game |
| `QUESTION_PREPARE` | Host → Tamu | `tutorialMs` (dibatasi maks 15 dtk di Tamu) |
| `QUESTION_START` | Host → Tamu | `seed` (diverifikasi sama dengan setup) |
| `GAME_SUBMIT` | Tamu → Host | masukan game (`GameSubmission?`) + `elapsedMs`; `null` = menyerah |
| `GAME_STATE_UPDATE` | dua arah (Host relay) | `playerSessionId`, `gameType`, `seq`, `tMs`, `GhostState` |
| `GAME_ACTION` | dua arah (Host relay) | event aksi (`FireAction`, `LaunchAction`, `TapAction`, `LabStepAction`) |
| `ROUND_RESULT` | Host → Tamu | `PlayerRound.submission` + `detail`; `correctOptionId` null untuk game |

Otoritas skor: Host **tidak menerima flag benar**. Ia memutar ulang submisi dengan mesin + seed yang sama
(`GameEngines.isWellFormed` menolak ukuran/indeks di luar batas → 0 poin), membatasi klaim waktu dengan RTT +
toleransi (sama seperti MCQ), lalu menyiarkan hasil. Setiap Tamu memeriksa ulang poin & keberhasilan seluruh pemain;
selisih → `INCONSISTENT_RESULT`. Batas: frame 64 KiB, `MAX_ASTEROID_HITS 64`, `MAX_SHOTS 8`, `MAX_TAPS 32`,
`MAX_LAB_ACTIONS 240`; snapshot ghost < 400 byte (diuji).

Ketahanan: putus koneksi saat game → pemain absen 0 poin, ghost-nya dihapus, pemain lain lanjut; reconnect seperti
sebelumnya (ikut lagi mulai ronde berikutnya); tutorial dan ronde selalu punya batas waktu Host (tidak deadlock);
pertandingan bisa dibatalkan seperti biasa.

## 5. Menambah soal game baru

1. Tulis entri di `tools/game_content/content_math_phys_chem.py` (Matematika/Fisika/Kimia) atau
   `tools/game_content/biology_scenes.py` (Biologi: fungsi scene + entri `BIO`). ID mengikuti `XXX-YYY-0NN`
   (rentang game saat ini 061–070; tambahan berikutnya pakai 071+ dan perbarui `TYPE_RANGE` builder + tes).
2. Field wajib per soal (`content/game/game_questions_80.json` dihasilkan otomatis):

```json
{
  "id": "MAT-SMA-061", "questionType": "mini_game", "subject": "Matematika", "gradeBand": "SMA",
  "topic": "Turunan fungsi aljabar", "difficulty": "mudah", "timeLimitSeconds": 60,
  "stem": "f(x) = x² + 7x. Lengkapi turunannya ...", "explanation": "Aturan pangkat ...",
  "game": {"kind": "asteroid", "template": "f'(x) = {0} + {1}", "answerTokens": ["2x", "7"],
           "distractorTokens": ["x²", "14x", "−7", "x"], "maxWrong": 3, "fallSpeed": 0.07,
           "spawnInterval": 1.6, "hint": "..."},
  "solution": {"kind": "asteroid", "hits": [0, 1]},
  "solutionText": "f'(x) = 2x + 7", "editorialFlags": []
}
```

3. Jalankan `python tools/game_content/generate_game_questions.py` (gagal bila solusi referensi tidak terbukti),
   lalu `python tools/build_question_asset.py`, `python tools/validate_game_pack.py --report docs/CONTENT_560_QA.md`,
   dan `./gradlew :app:testDebugUnitTest`. Pratinjau preparat biologi: `--svg <folder>`.
4. Naikkan `BANK_VERSION_NAME` di builder bila isi bank berubah; hash bank otomatis berubah sehingga HP dengan
   bank lama menolak room (pesan bank berbeda) sampai semua memasang APK yang sama.

## 6. Menguji offline di 2–4 HP

1. Pasang APK yang **sama persis** di semua HP (`./gradlew :app:assembleDebug`, lalu `adb install -r`).
2. Satu HP: Main Bareng → isi nama → **Buat Room** (Wi-Fi/Hotspot atau Bluetooth). Di lobi pilih mapel,
   **Jenis soal = Mini-Game** (atau Campuran Tipe) dan **Mode ghost** LIVE/FAIR.
3. HP lain: **Gabung Room** (masukkan kode room Wi-Fi atau pilih perangkat Bluetooth) → SIAP.
4. Host: Mulai. Periksa di SETIAP HP: tutorial muncul tanpa memotong waktu; saat main, tiga pemain lain tampil
   sebagai ghost berwarna dengan label nama (Tamu juga melihat Host); ghost tidak menghalangi sentuhan; status
   "selesai ✓" muncul setelah pemain mengunci; pada FAIR label/parameter tersembunyi.
5. Uji gangguan: matikan Wi-Fi satu Tamu di tengah ronde (pemain lain lanjut; Tamu menyambung ulang ≤30 dtk),
   jalankan room Bluetooth (ghost lebih lambat 5 Hz tetapi tetap mulus), ubah opasitas/OFF ghost di Pengaturan.
6. Tanpa HP kedua: Main Bareng → **Coba dengan 3 Bot (demo)**; bot menyelesaikan tutorial, bergerak sebagai ghost,
   dan mengirim jawaban game sungguhan lewat protokol yang sama.

## 7. Migrasi & rollback bank

- Bank v1 (schema 1, 400 soal, `2026.11.07-r1`) → bank v2 (schema 2, 560 soal, `2026.11.10-r560`). Loader menerima
  schema 1 dan 2; decoder ketat menolak field asing. `assetManifest` (SHA-256 setiap PNG) ada di dalam
  `questions.json`, jadi hash bank juga mengunci gambar; aplikasi memverifikasi semua PNG saat bank dimuat.
- 400 soal klasik dikunci sidik jari (builder + `classic400_identicalToOriginalV1Asset`): build gagal bila berubah.
- Riwayat: `RoundRecord` menambah field opsional (`questionType`, `correct`, `detail`) di JSON; tidak perlu migrasi DB.
- Rollback: salinan asli semua file yang diubah ada di `.rollback/v0.05/` (lihat `rollback.md`). Mengembalikan bank
  lama = memulihkan `app/src/main/assets/questions.json` dan menghapus `app/src/main/assets/images/`, lalu build ulang;
  karena protokol berbeda, semua HP dalam satu room harus memakai versi yang sama.

## Efek layar dan tabrakan (0.5.0)

`GameEffectsHost` berada di atas navigasi: animasi tidak terpotong arena, bertahan ketika jawaban
dikunci, dan tidak menghalangi kontrol. Hasil mini-game yang benar memicu confetti 2 detik sekali
per ronde, bersama suara perayaan. Penalti memicu tepi merah berkedip dan −1 dengan fade out 0,5 detik:
salah tembak/tabrakan asteroid, tap Biologi yang salah, tembakan Fisika yang meleset, dan pemakaian
kesempatan Ulangi pada lab. Tap ditolak karena fokus/zoom tidak mengurangi kesempatan.

Deteksi asteroid memakai sapuan posisi asteroid dan pesawat antarframe, sehingga frame yang terlewat
tetap mendeteksi benturan. Asteroid yang bertabrakan dihapus sekali; tiap benturan menambahkan satu
`AsteroidEngine.COLLISION` (`-1`) ke urutan submisi. Host dan Tamu memutar ulang penalti dalam urutan
yang sama dengan tembakan. Protokol v4 menolak versi v3 agar aturan nyawa tidak berbeda antar-HP.
