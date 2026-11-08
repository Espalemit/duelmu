# Kebijakan Privasi Duelmu

*Berlaku sejak: 8 November 2026 · Versi aplikasi: 0.2.0*

Duelmu adalah permainan edukasi kuis SMP–SMA yang berjalan **offline**. Kebijakan ini menjelaskan data
apa yang diproses dan di mana data tersebut disimpan.

## Ringkasan

- **Tanpa akun.** Tidak ada login, registrasi, atau profil. Semua pemain adalah *Guest*.
- **Tanpa server.** Aplikasi tidak mengirim data ke server developer maupun pihak ketiga dan tidak
  memakai kuota internet. Permainan bersama berjalan langsung antar-HP di jaringan lokal (Wi-Fi yang sama
  atau hotspot salah satu HP) atau lewat Bluetooth.
- **Tanpa iklan, analitik, atau pelacakan.** Tidak ada SDK iklan, Firebase Analytics, Crashlytics, atau
  layanan sejenis di dalam aplikasi.
- **Semua data tersimpan di perangkatmu** dan dapat dihapus kapan saja.

## Data yang diproses

| Data | Untuk apa | Disimpan di | Dibagikan ke |
|---|---|---|---|
| Nama panggilan (nickname) yang kamu ketik sebelum bermain | Ditampilkan ke pemain lain di room (lobi, papan skor, hasil) | Perangkatmu (riwayat & isian nama terakhir, bila diaktifkan) | HP pemain lain di room yang sama (maks. 3), **langsung lewat Wi-Fi/hotspot lokal atau Bluetooth**. Bila kamu Host room Wi-Fi, nickname juga terlihat di daftar room oleh HP lain di jaringan yang sama |
| ID sesi acak (`playerSessionId`) | Membedakan pemain dalam satu room (nama boleh sama) | Memori sementara selama sesi | HP pemain lain di room |
| Jawaban, waktu jawab, skor | Menghitung skor dan menampilkan hasil | Perangkatmu (riwayat lokal) | HP pemain lain di room |
| Pengaturan (ukuran teks, suara, getar) | Kenyamanan penggunaan | Perangkatmu | Tidak dibagikan |
| Alamat IP lokal Host / nama perangkat Bluetooth Host | Menyambungkan HP ke room | Tidak disimpan | Hanya terlihat oleh HP di jaringan/sekitar yang sama |

Duelmu **tidak** meminta email, nomor telepon, tanggal lahir, sekolah, lokasi, foto, atau kontak.
Jangan memakai data pribadi (nama lengkap, sekolah, nomor HP) sebagai nickname.

## Izin perangkat

- **Akses jaringan (`INTERNET`)**: Android mewajibkan izin ini untuk membuka socket apa pun, termasuk
  socket lokal antar-HP di Wi-Fi/hotspot yang sama. Duelmu tidak menghubungi server internet mana pun.
- **Perangkat sekitar / Bluetooth** (Android 12+): untuk mencari dan menghubungkan HP lewat Bluetooth. Hasil
  pemindaian tidak pernah dipakai untuk menentukan lokasi.
- **Lokasi** (hanya Android 8–11): sistem Android versi tersebut mewajibkan izin lokasi untuk memindai
  perangkat Bluetooth. Diminta hanya saat kamu menekan *Pindai HP sekitar*. Duelmu tidak membaca atau
  menyimpan lokasimu.
- **Getar**: umpan balik haptik opsional.

## Penyimpanan, cadangan, dan penghapusan

- Riwayat disimpan dalam database lokal aplikasi. **Dikecualikan dari cadangan cloud** dan transfer
  perangkat (Android backup dinonaktifkan).
- Hapus kapan saja: **Pengaturan → Hapus riwayat** dan **Hapus nama terakhir**, atau hapus data/uninstall
  aplikasi.
- Nickname yang sudah dikirim ke HP pemain lain tersimpan di riwayat lokal HP mereka sebagai catatan
  pertandingan dan berada di luar kendali perangkatmu.

## Anak dan pelajar

Duelmu ditujukan untuk pelajar. Karena tidak ada akun, tidak ada pengumpulan data oleh developer, dan tidak
ada iklan, aplikasi dirancang agar tidak memproses data pribadi anak di luar perangkat. Orang tua/guru
dapat menghapus riwayat lewat menu Pengaturan.

## Layanan Firebase

Developer memakai Firebase (paket Spark) **hanya** untuk:
- **Firebase App Distribution**: mengirim versi beta kepada penguji yang diundang (di luar aplikasi).
- **Firebase Hosting** (opsional): menayangkan halaman kebijakan privasi ini.

Tidak ada Firebase SDK di dalam aplikasi, jadi aplikasi tidak mengirim data apa pun ke Firebase.
Penguji beta yang menerima undangan App Distribution tunduk pada ketentuan Firebase/Google untuk proses
undangan tersebut; ini bukan login di dalam Duelmu.

## Keamanan

Jalur Bluetooth memakai RFCOMM *secure* (memerlukan pairing dan terenkripsi). Jalur Wi-Fi/hotspot memakai
TCP di jaringan lokal **tanpa enkripsi**; isinya hanya nickname, jawaban, dan skor. Untuk masuk room Wi-Fi,
pemain harus mengetik kode room 4 digit yang tampil di HP Host, dan Host dapat mengeluarkan pemain. Gunakan
jaringan yang kamu percayai (mis. hotspot HP Host). Nickname tidak membuktikan identitas siapa pun; tidak ada
leaderboard online.

## Perubahan kebijakan

Bila suatu versi menambahkan layanan yang memproses data (mis. pelaporan crash opsional), kebijakan ini
akan diperbarui **sebelum** versi tersebut dirilis dan fitur tersebut akan bersifat opt-in.

## Kontak

Pertanyaan tentang privasi: *[isi email pengelola rilis/sekolah sebelum publikasi]*.
