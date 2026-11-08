const puppeteer = require('puppeteer-core');
const path = require('path');
const { spawn } = require('child_process');
const fs = require('fs');
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const FPS = 30;
(async () => {
  const mode = process.argv[2] || 'preview';
  const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new',
    args: ['--allow-file-access-from-files', '--force-color-profile=srgb', '--hide-scrollbars'] });
  const page = await browser.newPage();
  await page.setViewport({ width: 1080, height: 1920, deviceScaleFactor: 1 });
  await page.goto(require('url').pathToFileURL(path.resolve(__dirname, 'promo.html')).href, { waitUntil: 'networkidle0' });
  await page.evaluate(async () => { await document.fonts.ready; });
  const fontsOk = await page.evaluate(() => document.fonts.check('800 40px "Baloo 2"') && document.fonts.check('900 40px Nunito'));
  console.log('fonts loaded:', fontsOk);
  if (mode === 'preview') {
    fs.mkdirSync('preview', { recursive: true });
    const times = (process.argv[3] || '1.6,4.9,6.2,8.7,11.4,12.6,16.4,19.5,23.6,28').split(',').map(Number);
    for (const t of times) {
      await page.evaluate(t => renderAt(t), t);
      await page.screenshot({ path: `preview/t_${t.toFixed(2)}.png` });
    }
  } else {
    const dur = await page.evaluate(() => window.DURATION);
    const n = Math.round(dur * FPS);
    const ff = spawn('ffmpeg', ['-y', '-f', 'image2pipe', '-framerate', String(FPS), '-c:v', 'mjpeg', '-i', '-',
      '-c:v', 'libx264', '-preset', 'slow', '-crf', '18', '-pix_fmt', 'yuv420p', '-r', String(FPS), 'video_silent.mp4'], { stdio: ['pipe', 'ignore', 'inherit'] });
    for (let i = 0; i < n; i++) {
      await page.evaluate(t => renderAt(t), i / FPS);
      const buf = await page.screenshot({ type: 'jpeg', quality: 95 });
      if (!ff.stdin.write(buf)) await new Promise(r => ff.stdin.once('drain', r));
      if (i % 60 === 0) console.log(`frame ${i}/${n}`);
    }
    ff.stdin.end();
    await new Promise(r => ff.on('close', r));
  }
  await browser.close();
})();
