import { chromium } from 'playwright-core';
import fs from 'fs'; import { PNG } from 'pngjs'; import pixelmatch from 'pixelmatch';
const OUT = process.argv[2];
const b = await chromium.launch({ channel: 'chrome' });
for (const theme of ['light', 'dark']) {
  const ctx = await b.newContext({ viewport: { width: 1440, height: 900 }, colorScheme: theme });
  const r = await ctx.newPage(), n = await ctx.newPage();
  await r.goto(new URL('../../maris-2-v4.html', import.meta.url).href); await r.waitForSelector('.lg-go'); await r.waitForTimeout(1200);
  await n.goto('http://localhost:5179'); await n.waitForSelector('#lg-pw'); await n.waitForTimeout(800);
  await r.screenshot({ path: `${OUT}/${theme}-login.ref.png` }); await n.screenshot({ path: `${OUT}/${theme}-login.new.png` });
  await r.click('.lg-go'); await r.waitForFunction(() => document.querySelectorAll('.sd-sec').length > 0); await r.waitForTimeout(2500);
  await n.click('button[type=submit]'); await n.waitForTimeout(1500);
  await r.evaluate(() => document.querySelectorAll('.tb-clock').forEach(e => e.style.visibility = 'hidden')); await n.evaluate(() => document.querySelectorAll('header .tabular-nums').forEach(e => e.style.visibility = 'hidden'));
  await r.screenshot({ path: `${OUT}/${theme}-home.ref.png` }); await n.screenshot({ path: `${OUT}/${theme}-home.new.png` });
  await r.locator('.sd-sec .sd-row').first().click(); await r.waitForTimeout(1500);
  await n.getByText('Vehicle Trouble Desk', { exact: true }).first().click(); await n.waitForTimeout(1200);
  await r.screenshot({ path: `${OUT}/${theme}-menu.ref.png` }); await n.screenshot({ path: `${OUT}/${theme}-menu.new.png` });
  await ctx.close();
}
for (const f of fs.readdirSync(OUT).filter(f => f.endsWith('.ref.png'))) {
  const a = PNG.sync.read(fs.readFileSync(`${OUT}/${f}`)), c = PNG.sync.read(fs.readFileSync(`${OUT}/${f.replace('.ref', '.new')}`)), d = new PNG({ width: a.width, height: a.height });
  const px = pixelmatch(a.data, c.data, d.data, a.width, a.height, { threshold: .2 }); fs.writeFileSync(`${OUT}/${f.replace('.ref', '.diff')}`, PNG.sync.write(d));
  console.log(f.replace('.ref.png', ''), (px / (a.width * a.height) * 100).toFixed(1) + '%');
}
await b.close();
