// Export the approved #176 reference with Playwright and Sharp installed outside source control.
// Usage: NODE_PATH=<build deps>/node_modules node tools/art/Export-MenuSky.cjs <decoded reference HTML> <Edge executable>
const fs = require('fs');
const { chromium } = require('playwright');
const sharp = require('sharp');
(async () => {
  const browser = await chromium.launch({ executablePath: process.argv[3], headless: true });
  try {
    const page = await browser.newPage();
    const reference = fs.readFileSync(process.argv[2], 'utf8').replace('scene.original=original;', 'scene.sky=sky;scene.original=original;');
    await page.setContent(reference, { waitUntil: 'domcontentloaded' });
    await page.waitForFunction(() => document.querySelector('#menu-sky-fixed')?.dataset.ready === 'true');
    const scenes = await page.evaluate(() => document.querySelector('#menu-sky-fixed').skyPreview.scenes.map(s => {
      const canvas = document.createElement('canvas'); canvas.width = 640; canvas.height = 1137;
      const c = canvas.getContext('2d'); c.scale(640 / 941, 1137 / 1671); c.fillStyle = '#fff'; c.fill(s.cloudOcclusion);
      const pixels = c.getImageData(0, 0, 640, 1137).data, runs = [];
      for (let y = 0; y < 460; y++) {
        let start = -1;
        for (let x = 0; x <= 640; x++) {
          const visible = x < 640 && pixels[(y * 640 + x) * 4 + 3] > 127;
          if (visible && start < 0) start = x;
          if (!visible && start >= 0) { runs.push([start, y, x - start]); start = -1; }
        }
      }
      c.clearRect(0, 0, 941, 1671); c.fill(s.sky, 'evenodd');
      const skyPixels = c.getImageData(0, 0, 640, 1137).data, skyRuns = [];
      for (let y = 0; y < 460; y++) {
        let start = -1;
        for (let x = 0; x <= 640; x++) {
          const visible = x < 640 && skyPixels[(y * 640 + x) * 4 + 3] > 127;
          if (visible && start < 0) start = x;
          if (!visible && start >= 0) { skyRuns.push([start, y, x - start]); start = -1; }
        }
      }
      const originalCanvas = document.createElement('canvas'); originalCanvas.width = 640; originalCanvas.height = 1137;
      originalCanvas.getContext('2d').putImageData(s.original, 0, 0);
      return { skyRuns, day: s.day, base: s.base.toDataURL(), original: originalCanvas.toDataURL(),
        clouds: s.cloudSprites.map(c => c.toDataURL()), stars: s.stars, runs };
    }));
    const drawables = 'app/src/main/res/drawable-nodpi';
    fs.mkdirSync('app/src/main/res/raw', { recursive: true });
    const decode = url => Buffer.from(url.split(',')[1], 'base64');
    for (const s of scenes) {
      const theme = s.day ? 'day' : 'night';
      const clean = await sharp(decode(s.base)).ensureAlpha().raw().toBuffer();
      const original = await sharp(decode(s.original)).ensureAlpha().raw().toBuffer();
      const alpha = Buffer.alloc(640 * 1137);
      for (let i = 0; i < alpha.length; i++) {
        const k = i * 4;
        if (clean[k] !== original[k] || clean[k + 1] !== original[k + 1] || clean[k + 2] !== original[k + 2]) alpha[i] = 255;
      }
      // Keep the full-resolution original EXACTLY outside the exposed cloud pixels.
      const patch = Buffer.alloc(941 * 1671 * 4);
      const resized = await sharp(clean, { raw: { width: 640, height: 1137, channels: 4 } }).resize(941, 1671, { kernel: 'linear' }).raw().toBuffer();
      const mask = await sharp(alpha, { raw: { width: 640, height: 1137, channels: 1 } }).resize(941, 1671, { kernel: 'nearest' }).extractChannel(0).raw().toBuffer();
      let changed = 0, bottom = 0;
      for (let i = 0; i < mask.length; i++) if (mask[i]) { resized.copy(patch, i * 4, i * 4, i * 4 + 4); changed++; bottom = Math.max(bottom, Math.floor(i / 941) + 1); }
      await sharp(patch, { raw: { width: 941, height: 1671, channels: 4 } }).extract({ left: 0, top: 0, width: 941, height: bottom }).png().toFile(`${drawables}/menu_sky_${theme}.png`);
      for (let i = 0; i < s.clouds.length; i++) fs.writeFileSync(`${drawables}/menu_cloud_${theme}_${i}.png`, decode(s.clouds[i]));
      console.log(`${theme}: reconstructed ${changed} pixels; remaining native artwork preserved`);
    }
    fs.writeFileSync('app/src/main/res/raw/menu_sky_registration.json', JSON.stringify({ stars: scenes[1].stars, skyRuns: scenes[0].skyRuns, cloudRuns: scenes[0].runs }));
  } finally { await browser.close(); }
})().catch(e => { console.error(e); process.exitCode = 1; });
