// Screen-by-screen comparison of the reference UI (../maris-2-v4.html) and the React app (dev server).
// Each screen's canvas is shot at zoom 1 in both, in light or dark theme, and pixel-diffed.
//   node tools/compare-screens.mjs http://localhost:5173 OUTDIR [light|dark] [key ...]
import { chromium } from 'playwright-core';
import fs from 'fs';
import { PNG } from 'pngjs';
import pixelmatch from 'pixelmatch';
const [, , BASE, OUT, THEME = 'light', ...only] = process.argv;
const REF = new URL('../../maris-2-v4.html', import.meta.url).href;
const menus = JSON.parse(fs.readFileSync(new URL('../src/features/menu/menus.json', import.meta.url)));
let keys = fs.readdirSync(new URL('../public/screens/', import.meta.url)).map(f => JSON.parse(fs.readFileSync(new URL('../public/screens/' + f, import.meta.url))).key);
// the drawn yard and shop maps
const maps = JSON.parse(fs.readFileSync(new URL('../public/data/maps.json', import.meta.url)));
keys.push(...Object.keys(maps.MAPS).map(y => 'yardmap:' + y), ...Object.keys(maps.SHOPS).map(y => 'shop:' + y));
if (only.length) keys = keys.filter(k => only.includes(k));
fs.mkdirSync(OUT, { recursive: true });
console.log('keys:', keys.length);
if (only.length && keys.length !== only.length) { console.log('unknown keys:', only.filter(k => !keys.includes(k)).join(' ')); process.exit(2); }
const b = await chromium.launch({ channel: 'chrome' });

// ---- reference ----
const r = await b.newPage({ viewport: { width: 1440, height: 900 } });
r.on('dialog', d => d.dismiss());
await r.goto(REF); await r.waitForSelector('.lg-go'); await r.selectOption('.lg-user', 'MAXADMIN'); await r.click('.lg-go');
await r.waitForFunction(() => document.querySelectorAll('.sd-sec').length > 0); await r.waitForTimeout(800);
if (THEME === 'dark') await r.evaluate(() => document.querySelector('.wv-theme').click());
const WEB = { 'web:print': 'Web.marisPrint(r)', 'web:cf': 'Web.coldFusion(r)', 'web:nogo': 'Web.nogo(r)', 'web:train': 'Web.trainSummary(r)', 'web:avail': 'Web.availability(r)',
  'web:wdms': 'Web.wdms(r)', 'web:clean': 'Web.carCleaner(r, "CY")', 'web:move': 'Web.carMove(r, "CY")', 'web:inv': 'Web.inventory(r, "CY")', screenindex: 'Screens.screenIndex(r)' };
const opener = key => {
  if (WEB[key]) return { call: WEB[key] };
  const m = /^(yardmap|shop):([A-Z]{2})$/.exec(key);
  if (m) return { call: m[1] === 'shop' ? `Screens.shopMap(r, '${m[2]}')` : `Screens.yardMap(r, '${m[2]}')` };
  if (key.startsWith('egl:')) return { call: `EglScreens.open(r, ${JSON.stringify(key.slice(4))})` };
  for (const [si, s] of menus.sections.entries()) for (const g of s.groups) for (const it of g.list) if (it.key === key) return { si, i: it.i };
  return null;
};
async function shootRef(key) {
  const o = opener(key); if (!o) return false;
  await r.evaluate(o => {
    const root = Menus.root(); [...WM.list].reverse().forEach(x => { if (x !== root && WM.list.includes(x)) WM.close(x); });
    if (o.call) new Function('r', o.call)(root); else document.querySelectorAll('.sd-sec')[o.si].querySelector(`.sd-it[data-i="${o.i}"]`).click();
  }, o);
  await r.waitForTimeout(1800);
  // a menu window is shown as a tile page (#wv-view); anything else: the window's canvas at zoom 1
  const ok = await r.evaluate(() => { const w = WM.list.at(-1); if (!w || w === Menus.root()) return false;
    if (w.frame.classList.contains('wv-gen')) { document.getElementById('wv-view').dataset.cmp = '1'; return true; }
    w.win.style.zoom = 1; w.cv.dataset.cmp = '1'; return true; });
  if (!ok) return false;
  await r.locator('[data-cmp]').last().screenshot({ path: `${OUT}/${f(key)}.ref.png`, animations: 'disabled' });
  await r.evaluate(() => document.querySelectorAll('[data-cmp]').forEach(e => delete e.dataset.cmp));
  return true;
}
// ---- React ----
const n = await b.newPage({ viewport: { width: 1440, height: 900 } });
await signIn();
async function signIn() {
  await n.goto(BASE); await n.selectOption('#lg-user', 'MAXADMIN'); await n.click('button[type=submit]'); await n.waitForFunction(() => window.__maris);
  if (THEME === 'dark') await n.evaluate(() => { document.documentElement.dataset.theme = 'dark'; });
}
async function shootNew(key) {
  if (!(await n.evaluate(() => !!window.__maris))) await signIn();   // a dev-server reload signs the page out
  await n.evaluate(key => window.__maris.go({ kind: 'screen', key, label: key }), key);
  await n.waitForSelector('[data-screen]', { timeout: 8000 }).catch(() => null);
  await n.waitForTimeout(500);
  const el = n.locator('[data-screen]'); if (!(await el.count())) return false;
  await el.evaluate(e => { e.style.zoom = 1; });
  await el.screenshot({ path: `${OUT}/${f(key)}.new.png`, animations: 'disabled' });
  return true;
}
const f = k => k.replace(/[^A-Za-z0-9_-]/g, '_');
function diff(key) {
  const A = PNG.sync.read(fs.readFileSync(`${OUT}/${f(key)}.ref.png`)), B = PNG.sync.read(fs.readFileSync(`${OUT}/${f(key)}.new.png`));
  const w = Math.max(A.width, B.width), h = Math.max(A.height, B.height);
  const pad = P => { const o = new PNG({ width: w, height: h }); o.data.fill(255); PNG.bitblt(P, o, 0, 0, P.width, P.height, 0, 0); return o; };
  // screenshots of elements at fractional positions crop 1px apart: take the best of the 1px shifts
  const shift = (P, dx, dy) => { const o = new PNG({ width: w, height: h }); o.data.fill(255); PNG.bitblt(P, o, Math.max(0, -dx), Math.max(0, -dy), P.width - Math.abs(dx), P.height - Math.abs(dy), Math.max(0, dx), Math.max(0, dy)); return o; };
  const a = pad(A); let best = null;
  for (const dx of [-1, 0, 1]) for (const dy of [-1, 0, 1]) {
    const c = shift(B, dx, dy), d = new PNG({ width: w, height: h });
    const n = pixelmatch(a.data, c.data, d.data, w, h, { threshold: 0.2 });
    if (!best || n < best.n) best = { n, d };
  }
  const px = best.n, d = best.d;
  // score against the screen's content (pixels that differ from its background), not the whole canvas
  const bg = new PNG({ width: w, height: h }); for (let i = 0; i < bg.data.length; i += 4) { bg.data[i] = a.data[0]; bg.data[i + 1] = a.data[1]; bg.data[i + 2] = a.data[2]; bg.data[i + 3] = 255; }
  const ink = Math.max(1, pixelmatch(a.data, bg.data, null, w, h, { threshold: 0.3 }));
  fs.writeFileSync(`${OUT}/${f(key)}.diff.png`, PNG.sync.write(d));
  return { key, ratio: +(px / ink).toFixed(4), ref: [A.width, A.height], new: [B.width, B.height] };
}
const rows = [];
for (const key of keys) {
  const a = await shootRef(key).catch(e => (console.log('ref fail', key, e.message.slice(0, 80)), false));
  const c = await shootNew(key).catch(e => (console.log('new fail', key, e.message.slice(0, 80)), false));
  if (!a || !c) { rows.push({ key, ratio: a ? 'NEW MISSING' : 'REF MISSING' }); continue; }
  rows.push(diff(key));
}
rows.sort((x, y) => (typeof y.ratio === 'number' ? y.ratio : 2) - (typeof x.ratio === 'number' ? x.ratio : 2));
fs.writeFileSync(`${OUT}/report.json`, JSON.stringify(rows, null, 1));
rows.slice(0, 40).forEach(x => console.log(x.key, x.ratio, JSON.stringify(x.ref || ''), JSON.stringify(x.new || '')));
console.log('screens', rows.length, 'over 15%:', rows.filter(x => typeof x.ratio !== 'number' || x.ratio > .15).length, 'over 5%:', rows.filter(x => typeof x.ratio !== 'number' || x.ratio > .05).length, 'over 2%:', rows.filter(x => typeof x.ratio !== 'number' || x.ratio > .02).length);
await b.close();
