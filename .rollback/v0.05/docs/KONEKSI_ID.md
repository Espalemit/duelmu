# Koneksi Room Duelmu: Wi-Fi/Hotspot & Bluetooth, Izin, Protokol, Model Ancaman, Troubleshooting

Satu room berisi **2–4 pemain**. Satu HP menjadi **Host** (pusat room sekaligus pemain), maksimal 3 HP lain
menjadi **Tamu**. Semua pemain tetap Guest tanpa akun; Host/Tamu hanya peran koneksi. Tidak ada server
internet: semua lalu lintas langsung antar-HP di jaringan lokal atau Bluetooth.

## 1. Dua jalur koneksi

| Aspek | Wi-Fi / Hotspot (disarankan untuk 3–4 pemain) | Bluetooth Classic RFCOMM |
|---|---|---|
| Syarat | Semua HP di Wi-Fi yang sama, **atau** di hotspot salah satu HP (sebaiknya hotspot milik Host). Internet tidak dipakai; kuota tidak terpakai | Bluetooth menyala; setiap Tamu dipasangkan (pairing) dengan HP Host |
| Topologi | Bintang: Host membuka `ServerSocket` TCP, Tamu `connect()` | Bintang: Host membuka satu server socket RFCOMM dan `accept()` berulang (satu socket per Tamu) |
| Port / UUID | TCP **47830** (bila dipakai aplikasi lain → port acak, ditampilkan di layar Host) | UUID `6f1c3a52-8d1e-4c7b-9a35-2e0d5b7c9e41`, layanan SDP `Duelmu Room RFCOMM` |
| Menemukan room | NSD/mDNS tipe `_eduduel._tcp` (nama layanan acak `Duelmu-xxxxxx`, atribut = nickname Host). Cadangan: ketik **IP Host** yang tertera di layar Host | Daftar HP yang sudah dipasangkan, atau **Pindai HP sekitar**; Host bisa menekan **Jadikan HP terlihat** |
| Izin masuk room | **Kode room 4 digit** di layar Host, wajib diketik Tamu. Host juga bisa **Keluarkan** pemain | Pairing Bluetooth + tombol **Keluarkan** di lobi Host |
| Enkripsi | **Tidak dienkripsi** (TCP polos di jaringan lokal). Isi: nickname, jawaban, skor | Secure RFCOMM (pairing + enkripsi link). Socket insecure tidak dipakai |
| Kode | `net/transport/LanController.kt` | `net/transport/BluetoothController.kt` |

Kedua jalur memakai `StreamDuelConnection` + protokol yang sama persis (`net/protocol`), dan sesi yang sama
(`net/room/RoomHostSession.kt`, `RoomGuestSession.kt`). Mode demo memakai `InMemoryConnection` dengan 3 bot
yang menjalankan sesi Tamu sungguhan.

**Hotspot**: bila tidak ada Wi-Fi, Host menyalakan *Hotspot pribadi* di HP-nya, Tamu menyambung ke hotspot
itu, lalu Host menekan **Muat ulang IP** di lobi (IP hotspot biasanya ditandai "(hotspot)", sering
`192.168.43.1` atau `10.x.x.1`). Pencarian otomatis (mDNS) kadang diblokir oleh router sekolah atau hotspot
tertentu; karena itu IP selalu ditampilkan untuk diketik manual.

## 2. Izin per versi Android

| Aksi | Android 12+ (API 31+) | Android 8–11 (API 26–30) |
|---|---|---|
| Room Wi-Fi (Host & Tamu) | `INTERNET` (izin *normal*, tanpa dialog) | sama |
| Buat room Bluetooth | `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE` (untuk "Jadikan HP terlihat") | `BLUETOOTH`, `BLUETOOTH_ADMIN` (install-time) |
| Gabung ke HP yang sudah dipasangkan | `BLUETOOTH_CONNECT` | tanpa izin runtime |
| Pindai HP sekitar | `BLUETOOTH_CONNECT` + `BLUETOOTH_SCAN` (`neverForLocation`) | `ACCESS_FINE_LOCATION` (diwajibkan sistem untuk discovery) |

- **Mengapa INTERNET?** Android tidak punya izin terpisah untuk socket lokal; membuka socket TCP apa pun
  (termasuk antar-HP di Wi-Fi yang sama) butuh `android.permission.INTERNET`. Duelmu tidak memuat klien
  HTTP, URL server, analitik, atau iklan; `GuestOnlyPolicyTest` menggagalkan build bila hal itu ditambahkan,
  dan `network_security_config.xml` memblokir HTTP polos.
- **Android 17 (API 37)** memperkenalkan perlindungan akses jaringan lokal untuk aplikasi yang menargetkan
  API 37. Proyek ini menargetkan API 36, jadi belum terdampak. **Sebelum menaikkan `targetSdk` ke 37**, baca
  dokumentasi resmi perubahan perilaku Android 17 dan tambahkan izin/alur yang diwajibkan untuk room Wi-Fi.
- `neverForLocation` sah karena hasil pemindaian Bluetooth hanya dipakai untuk daftar HP.
- Izin lokasi dideklarasikan `maxSdkVersion="30"` dan hanya diminta saat menekan "Pindai" pada Android 8–11.
- Penolakan izin: kartu penjelasan + tombol Izinkan; ditolak permanen → **Buka Pengaturan Aplikasi**. Izin
  dicek ulang setiap layar kembali aktif. Bluetooth dimatikan di tengah room Bluetooth → room dihentikan
  (`BLUETOOTH_OFF`) tanpa pemenang otomatis.

## 3. Framing & protokol v2

- Frame: `[panjang 4 byte big-endian][JSON UTF-8]`, batas 64 KiB, decoder inkremental (paket terpotong,
  beberapa frame dalam satu read).
- Envelope: `v` (versi protokol = **2**), `seq` (monoton per koneksi), `msgId` (dedup), `matchId`, `roundIndex`.
  Aplikasi versi 0.1.x (protokol v1, duel 1 lawan 1) ditolak dengan pesan "Versi aplikasi tidak cocok".
- Pesan duplikat / `seq` lama diabaikan; pesan rusak dibalas `ERROR` non-fatal (koneksi diputus setelah 5).

| Pesan | Arah | Isi |
|---|---|---|
| `HELLO` | Tamu→Host | versi app, versi & SHA-256 bank, nickname, `playerSessionId` acak, kode room (Wi-Fi), `resumeMatchId` |
| `HELLO_ACK` | Host→Tamu | diterima/ditolak (`BANK_MISMATCH`, `ROOM_FULL`, `MATCH_IN_PROGRESS`, `WRONG_ROOM_CODE`, `RESUME_REJECTED`, …), snapshot bila menyambung ulang |
| `ROOM_STATE` | Host→semua | daftar pemain (nickname, Host?, SIAP?, tersambung?), mapel, fase room. Dikirim setiap ada perubahan |
| `PLAYER_NAME_UPDATE`, `READY` | Tamu→Host | ganti nama (hanya di lobi sebelum SIAP), SIAP/batal |
| `MATCH_SETUP` | Host→peserta | mapel, **10 ID soal** + urutan opsi, peserta (snapshot nickname), SHA-256 bank. **Tanpa kunci** |
| `BANK_CHECK` | Tamu→Host | validasi setup (ID dikenal, 10 soal unik, semua dari mapel terpilih, permutasi opsi sah) |
| `QUESTION_PREPARE` / `QUESTION_READY` / `QUESTION_START` | Host↔Tamu | siapkan → siap → mulai (durasi dari tingkat kesulitan). **Tanpa kunci** |
| `ANSWER` | Tamu→Host | id opsi kanonik (bukan posisi) + waktu jawab; `null` = waktu habis |
| `ANSWER_LOCKED` | Host→semua | "pemain X sudah menjawab" (tanpa isi jawaban) |
| `ROUND_RESULT` | Host→peserta | kunci + hasil SEMUA peserta + total. Dikirim setelah ronde ditutup |
| `NEXT_ROUND` | Tamu→Host | tombol Lanjut (Host lanjut bila semua menekan, atau otomatis 6 dtk) |
| `MATCH_END` | Host→peserta | total akhir + alasan (selesai/dibatalkan) |
| `BACK_TO_ROOM` | Host→semua | Host membuka room lagi setelah pertandingan |
| `PING`/`PONG`, `ERROR`, `DISCONNECT` | dua arah | RTT & watchdog, error, keluar/dikeluarkan/room ditutup |

## 4. Aturan pertandingan

- Host memilih **mapel saja**: Fisika, Biologi, Kimia, Matematika, atau Campuran (semua mapel).
- Setiap pertandingan = **10 soal acak** dari 100 soal mapel itu (seed acak dari `SecureRandom` di Host,
  urutan opsi juga diacak). Jenjang (SMP/SMA) dan tingkat kesulitan **bukan filter**; keduanya tampil
  sebagai label di tiap soal dan tetap menentukan durasi (60/90/120 dtk) serta poin dasar (100/200/300).
- **Mulai** hanya bisa ditekan Host bila ada minimal 1 Tamu dan **semua Tamu SIAP**. Mengganti mapel
  mereset SIAP semua Tamu. Tamu yang datang saat pertandingan berjalan ditolak (`MATCH_IN_PROGRESS`).

## 5. Alur ronde & sinkronisasi waktu

Host mengirim `QUESTION_PREPARE`, menunggu `QUESTION_READY` dari semua peserta yang tersambung (maks. 8 dtk),
lalu `QUESTION_START`. Setiap HP menghitung waktu dengan jam monotonic sendiri (`elapsedRealtime`).
Latensi (Wi-Fi umumnya < 50 ms, Bluetooth puluhan–ratusan ms) membuat awal ronde **tidak dijamin sama persis**.
Host menutup ronde begitu semua peserta tersambung menjawab, atau pada `durasi + 2,5 dtk` (toleransi latensi).
Jawaban yang tiba setelah ronde ditutup diabaikan dan dicatat sebagai waktu habis.

## 6. Skor & peringkat (identik di semua HP)

- Salah / waktu habis / terputus = 0. Benar = `poin dasar + floor(0,5 × poin dasar × sisa / durasi)`
  (aritmetika bilangan bulat).
- Peringkat = total tertinggi; **skor sama berbagi peringkat** (tanpa tie-breaker karangan). Juara bersama
  dicatat sebagai *Seri* di riwayat.
- Setiap Tamu menghitung ulang poin semua pemain dari bank soal miliknya; selisih → pertandingan dihentikan
  (`INCONSISTENT_RESULT`).

## 7. Putus koneksi & menyambung ulang

- Watchdog: tanpa pesan masuk 12 dtk → koneksi dianggap hilang.
- **Tamu putus di tengah pertandingan**: pertandingan tetap berjalan untuk pemain lain. Tamu itu mendapat 0
  poin (status *terputus*) untuk setiap ronde yang dilewatkan. Ia punya **30 dtk** untuk menyambung ulang
  otomatis (Tamu mencoba `connect()` setiap 2 dtk, Host hanya menerima `playerSessionId` + `matchId` yang sama);
  bila berhasil, ia ikut lagi mulai soal berikutnya dan menerima semua hasil yang terlewat.
- Tamu yang keluar/putus **tidak membuat siapa pun menang otomatis**; peringkat tetap dari skor. Di HP Tamu
  yang gagal kembali, pertandingan tercatat *Batal*.
- **Semua Tamu hilang** (keluar atau jendela 30 dtk habis) → pertandingan dibatalkan tanpa pemenang.
- **Host keluar / HP Host mati** → pertandingan dibatalkan untuk semua (Host adalah pusat room).

## 8. Model ancaman (jujur)

Kunci jawaban tersimpan offline di setiap APK, jadi **pencegahan kecurangan sempurna mustahil** tanpa server
tepercaya. Ini permainan belajar, bukan ujian resmi.

| Ancaman | Mitigasi | Sisa risiko |
|---|---|---|
| Menyadap kunci dari lalu lintas | Kunci tidak pernah dikirim sebelum ronde ditutup | Di Wi-Fi, isi pesan (nickname, jawaban) terlihat oleh perangkat lain di jaringan yang sama |
| Orang lain di Wi-Fi yang sama ikut masuk | Kode room 4 digit (tidak diiklankan lewat mDNS); Host bisa mengeluarkan pemain; maks. 4 pemain | Kode 4 digit bukan kata sandi kuat; room bersifat sementara |
| Membaca kunci dari APK (root/dekompilasi) | Tidak ada (data offline) | Diterima |
| Tamu memalsukan waktu jawab | Host membatasi klaim: `elapsed ≥ waktu teramati − RTT − 1 dtk` | Kecurangan kecil (≤ ~1 dtk) tidak terdeteksi |
| Host curang (mengubah skor) | Semua Tamu menghitung ulang poin & total; selisih → dihentikan | Host bisa menutup ronde lebih awal; terlihat oleh pemain lain |
| APK berbeda isi bank | SHA-256 asset ditanam di BuildConfig, dicek saat start & handshake | — |
| Pesan rusak/berlebih | Batas frame 64 KiB, parsing defensif, batas pesan rusak, batas 4 pemain | Perangkat di jaringan/terpasang bisa mengganggu room |
| Spoofing nama | Nama bebas & boleh sama; identitas teknis = `playerSessionId` per sesi | Nama tidak membuktikan identitas (by design) |

Karena itu **tidak ada leaderboard online**: riwayat hanya catatan lokal.

## 9. Troubleshooting

| Gejala | Langkah |
|---|---|
| Room Wi-Fi tidak muncul di daftar | Pastikan semua HP di Wi-Fi/hotspot yang **sama**. Router sekolah/kantor sering memblokir mDNS atau komunikasi antar-perangkat (*client isolation*/*AP isolation*): ketik IP Host secara manual, atau pakai hotspot HP Host. |
| "Gagal terhubung" (Wi-Fi) | IP salah atau HP berbeda jaringan; cek IP di lobi Host (tekan **Muat ulang IP** setelah menyalakan hotspot). Matikan VPN di HP. Wi-Fi tamu/publik dengan client isolation tidak bisa dipakai. |
| "Kode room salah" | Ketik ulang 4 digit di layar Host. |
| "Room sudah penuh" | Maksimal 4 pemain (Host + 3 Tamu). |
| Hotspot menyala tetapi IP Host kosong | Tekan **Muat ulang IP**. Beberapa HP baru menampilkan IP setelah ada perangkat yang tersambung ke hotspot. |
| Izin Bluetooth ditolak | Pengaturan → Aplikasi → Duelmu → Izin → *Perangkat sekitar* (12+) / *Lokasi* (8–11, hanya untuk pindai). |
| HP Bluetooth tidak ditemukan | Host tekan **Jadikan HP terlihat**; jarak < 5 m; Android 8–11 nyalakan Lokasi sistem saat memindai; atau pasangkan dulu lewat Pengaturan Bluetooth. |
| Pairing gagal / pairing lama bermasalah | *Lupakan* perangkat di Pengaturan Bluetooth kedua HP, matikan-nyalakan Bluetooth, pasangkan ulang. |
| Bluetooth: Tamu ke-3 sering gagal | Sebagian HP membatasi jumlah koneksi Bluetooth bersamaan. Untuk 3–4 pemain gunakan Wi-Fi/hotspot. |
| "Versi bank soal berbeda" / "Versi aplikasi tidak cocok" | Pasang APK versi yang sama di semua HP (Pengaturan → Tentang menampilkan versi bank & SHA-256). |
| Nama tidak berubah | Ganti nama hanya di lobi sebelum SIAP. Batalkan SIAP, Ubah Nama, SIAP lagi. |
| Sering terputus | Dekatkan HP ke router/Host, matikan hemat daya ekstrem, jangan tutup paksa aplikasi. Menyambung ulang otomatis 30 dtk. |
