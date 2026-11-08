# Video promosi Duelmu (30 detik, 9:16)

Hasil: `Duelmu_Promo_30s_9x16.mp4` (1080×1920, 30 fps, H.264 + AAC), untuk TikTok / Reels / YouTube Shorts.

## Render ulang
Butuh: Node.js, Python 3, ffmpeg, Google Chrome.

```bash
npm install                 # sekali saja (puppeteer-core)
python audio.py             # musik + efek suara -> audio.wav
node render.js preview      # (opsional) cek beberapa frame -> preview/
node render.js full         # render 900 frame -> video_silent.mp4
ffmpeg -y -i video_silent.mp4 -i audio.wav -c:v copy -c:a aac -b:a 192k -shortest -movflags +faststart Duelmu_Promo_30s_9x16.mp4
```

Teks, soal, dan waktu scene ada di `promo.html` (array `CUT`, `QF`, dan isi tiap `<div class="scene">`).
Jika waktu scene diubah, sesuaikan juga waktu efek suara di `audio.py`.
Musik disintesis sendiri (bebas hak cipta); efek suara diambil dari `app/src/main/res/raw/`.
