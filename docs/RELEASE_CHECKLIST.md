# Release Checklist Duelmu

Centang setiap butir sebelum distribusi beta (App Distribution) atau rilis Play.

## A. Data bank soal
- [ ] `python tools/validate_question_bank.py` → **ERROR = 0**; baca WARNING di `docs/QA_DATASET_REPORT.md`.
- [ ] Bila sumber berubah: naikkan `BANK_VERSION_NAME` di `tools/build_question_asset.py`, jalankan builder.
- [ ] `python tools/build_question_asset.py --check` → "OK asset sinkron".
- [ ] Guru mata pelajaran sudah meninjau perubahan soal/kunci (lihat `docs/DATA_ISSUES.md` bagian 4).

## B. Build & uji otomatis
- [ ] `gradlew :app:testDebugUnitTest` lulus semua.
- [ ] `gradlew :app:lintDebug` tanpa error.
- [ ] `gradlew :app:connectedDebugAndroidTest` lulus di emulator/HP.
- [ ] `versionCode` naik, `versionName` diperbarui di `app/build.gradle.kts`.
- [ ] `gradlew :app:assembleRelease` / `:app:bundleRelease` dengan keystore rilis; keystore & password **tidak** di Git.
- [ ] Izin APK sesuai: `aapt2 dump permissions app-release.apk` → INTERNET (socket lokal), Bluetooth, lokasi `maxSdk=30`, VIBRATE. Tidak ada izin lain.
- [ ] `GuestOnlyPolicyTest` lulus (tanpa klien HTTP/URL server/analitik/iklan).
- [ ] Tidak ada `google-services.json`, plugin Google Services, atau dependency Firebase (`GuestOnlyPolicyTest`).

## C. Uji manual di HP fisik (wajib; emulator tidak cukup)
Kombinasi minimal: 3–4 HP untuk Wi-Fi/hotspot, 2 HP untuk Bluetooth; sertakan satu HP Android 12+ dan satu HP Android 8–11 bila tersedia.
- [ ] Instal baru → buka → langsung Beranda, tanpa login/form nama.
- [ ] Latihan Solo: pilih mapel saja, 10 soal acak dari mapel itu; jawaban terkunci; timer tidak reset saat rotasi/ganti tema.
- [ ] Main Bareng: validasi nama (kosong, 1 karakter, 21 karakter, emoji, baris baru).
- [ ] **Wi-Fi/hotspot**: Host di hotspot sendiri, data seluler semua HP mati. Room terlihat di daftar ATAU bisa via IP manual; kode salah ditolak; HP ke-5 ditolak "Room sudah penuh".
- [ ] Wi-Fi router sekolah/kantor (bila tersedia): catat apakah mDNS/client isolation memblokir; IP manual berfungsi?
- [ ] **Bluetooth**: izin (tolak → kartu; tolak permanen → Pengaturan; cabut izin → diminta ulang); Bluetooth mati → layar "Bluetooth mati"; pairing baru & perangkat terpasang; 2 Tamu sekaligus.
- [ ] Lobi: semua nickname & peran sama di semua HP; Ubah Nama sebelum SIAP terpropagasi; terkunci setelah SIAP; Host bisa Keluarkan pemain.
- [ ] Host ganti mapel → SIAP semua Tamu direset; Mulai hanya aktif bila ≥1 Tamu dan semua SIAP; Tamu yang datang saat main ditolak.
- [ ] 10 soal semuanya dari mapel terpilih (Campuran: campur), soal & urutan opsi identik, "x dari N sudah menjawab", skor & peringkat identik di semua HP, waktu habis = 0 poin, juara bersama = Seri.
- [ ] Nickname identik di beberapa HP tetap berjalan benar.
- [ ] Main Lagi (Host) → Kembali ke Room (Tamu): nama default dipertahankan, bisa diubah; pertandingan kedua tersimpan dengan nama baru.
- [ ] Tamu putus di tengah soal → pemain lain lanjut; kembali ≤30 dtk → ikut lagi soal berikutnya; tidak kembali → 0 poin sisa soal, tanpa kemenangan otomatis.
- [ ] Semua Tamu keluar → dibatalkan tanpa pemenang. Host keluar → dibatalkan di semua HP. Bluetooth dimatikan saat room Bluetooth → dibatalkan, tidak crash.
- [ ] Aplikasi ke background 20 detik saat soal aktif → waktu tetap berjalan, pertandingan lanjut.
- [ ] HP dengan APK/bank berbeda → ditolak dengan pesan versi.
- [ ] Update dari 0.1.0 → riwayat lama tetap ada (migrasi DB v1→v2).
- [ ] Riwayat Lokal menampilkan peringkat & semua peserta; Hapus riwayat & Hapus nama terakhir berfungsi.
- [ ] Dark mode, ukuran teks Sangat besar, TalkBack membaca opsi ("Pilihan A: …"), sisa waktu, dan papan skor.

## D. Kebijakan & distribusi
- [ ] Kebijakan privasi (`hosting/public/privacy.html`) sudah diisi kontak dan dideploy: `firebase deploy --only hosting`.
- [ ] Project Firebase tetap **Spark**, tanpa akun penagihan; tidak ada Auth/Firestore/Analytics/Crashlytics aktif.
- [ ] App Distribution: release notes, grup tester benar, undangan terkirim.
- [ ] Play: Data safety konsisten dengan `docs/PRIVACY_POLICY_ID.md`; target audience & content rating; target API ≥ 36.
- [ ] Akun personal: closed test ≥12 tester selama ≥14 hari sebelum produksi.
- [ ] Tag rilis di Git dan arsipkan `app-release.aab` + `mapping.txt` (R8) untuk deobfuscation.
