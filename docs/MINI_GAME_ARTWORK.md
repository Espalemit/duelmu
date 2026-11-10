# Aset kartun mini-game

Ke-12 PNG berikut dibuat dengan `image_gen.imagegen`, ditampilkan satu per satu di chat,
dan disetujui sebelum diterapkan. File asli disalin tanpa mengubah piksel ke
`app/src/main/res/drawable-nodpi/`. Prompt lengkap tersedia di
[`game-artwork-prompts.json`](game-artwork-prompts.json).

| Game | PNG |
|---|---|
| Asteroid Solver | `game_spaceship`, `game_asteroid`, `game_laser`, `game_impact` |
| Trajectory Lab | `game_cannon`, `game_projectile`, `game_target`, `game_obstacle` |
| Reaction Lab | `game_reagent_bottle`, `game_beaker`, `game_pipette` |
| Micro Hunter | `game_microscope_rim` |

`presentation/game/GameArtwork.kt` memuat gambar sekali selama arena aktif, dengan
sampling maksimal 768 piksel per sisi. Rendering memakai penyaringan gambar dan
mempertahankan rasio aspek, kecuali overlay yang perlu mengikuti batas arena.

- Laras meriam dipisahkan dari dudukan menggunakan mask saat render; dudukan tetap
  diam ketika sudut bidik berubah. PNG sumber tetap satu file yang disetujui.
- Batu rintangan diulang di dalam persegi panjang rintangan, bukan diregangkan menjadi
  batu yang sangat panjang. Posisi dunia dan penilaian lintasan tetap dari mesin game.
- Token matematika dan label reagen digambar dari soal. Warna cairan botol mengikuti
  reagen; isi beker digambar di belakang kaca, mengikuti volume dan indikator eksperimen.
- Ghost memakai aset yang sama, diberi warna pemain dan opasitas pengaturan lokal.
- Bingkai lensa mengelilingi viewport preparat. Koordinat pan, zoom, kursor, dan tap
  dihitung dari viewport tersebut. Geometri serta daerah jawaban 20 preparat tetap
  berasal dari bank soal; gambar dekoratif tidak menggantikan struktur biologi.

## Verifikasi

Jalankan `bash gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
`GameArtworkTest` memakai renderer Android native melalui Robolectric API 35 untuk
memeriksa gerakan laras, dudukan yang tetap diam, opasitas ghost, serta transparansi
beker dan lensa. Hasil render pengujian disimpan di `app/build/reports/game-artwork/`.
Render ini menguji komponen kanvas, bukan tangkapan layar dari perangkat fisik.
