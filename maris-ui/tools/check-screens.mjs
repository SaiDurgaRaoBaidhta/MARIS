// Opens every screen in the running dev server (npm run dev) and reports render errors and empty screens.
//   node tools/check-screens.mjs http://localhost:5173 [shot-dir]
import { chromium } from 'playwright-core';
import fs from 'fs';
const [, , BASE = 'http://localhost:5173', SHOTS] = process.argv;
const keys = fs.readdirSync(new URL('../public/screens/', import.meta.url)).map(f => JSON.parse(fs.readFileSync(new URL('../public/screens/' + f, import.meta.url))).key);
// 1. every way into a screen resolves: side-bar items, menu tiles, button wiring
const index = JSON.parse(fs.readFileSync(new URL('../src/features/screens/screenIndex.json', import.meta.url)));
const menus = JSON.parse(fs.readFileSync(new URL('../src/features/menu/menus.json', import.meta.url)));
const files = new Set(keys);
const ciKey = k => Object.keys(index).find(x => x.toUpperCase() === k.toUpperCase()) || k;
const resolves = k => /^(yardmap|shop):[A-Z]{2}/.test(k) || files.has(k) || files.has(index[k]?.alias) || !!index[k]?.map;
const links = [];
for (const sec of menus.sections) for (const g of sec.groups || []) for (const it of g.list) if (it.key) links.push(['side bar ' + sec.cap + ' / ' + it.label, it.key]);
for (const k of keys) { const d = JSON.parse(fs.readFileSync(new URL('../public/screens/' + k.replace(/[^A-Za-z0-9_-]/g, '_') + '.json', import.meta.url)));
  for (const g of d.menu || []) for (const it of g.list) if (it.to?.kind === 'screen') links.push([k + ' tile ' + it.label, it.to.key]);
  for (const [w, c] of Object.entries(d.wiring || {})) for (const code of [...c.to, c.it].filter(Boolean)) links.push([k + ' ' + w, ciKey('egl:' + code)]); }
const never = links.filter(([, k]) => !resolves(k) && index[k]?.missing), broken = links.filter(([, k]) => !resolves(k) && !index[k]?.missing);
console.log(`links: ${links.length}, never migrated in the source: ${never.length}, broken: ${broken.length}`);
broken.slice(0, 20).forEach(([from, k]) => console.log('BROKEN', from, '->', k));

const b = await chromium.launch({ channel: 'chrome' });
const p = await b.newPage({ viewport: { width: 1440, height: 900 } });
let errs = []; p.on('pageerror', e => errs.push(e.message)); p.on('console', m => { if (m.type() === 'error') errs.push(m.text().slice(0, 160)); });
await p.goto(BASE); await p.selectOption('#lg-user', 'MAXADMIN'); await p.click('button[type=submit]'); await p.waitForFunction(() => window.__maris);
let bad = 0;
for (const key of keys) {
  errs = [];
  await p.evaluate(key => window.__maris.go({ kind: 'screen', key, label: key }), key);
  await p.waitForFunction(() => !document.querySelector('main [role=status]'), null, { timeout: 8000 }).catch(() => errs.push('still loading'));
  const n = await p.evaluate(() => document.querySelectorAll('main input, main button, main select, main table, main [class*=absolute]').length);
  if (errs.length || n < 3) { bad++; console.log('FAIL', key, n, errs.slice(0, 2).join(' | ')); }
  if (SHOTS) await p.screenshot({ path: `${SHOTS}/${key.replace(/[^A-Za-z0-9_-]/g, '_')}.png` });
}
console.log(`${keys.length - bad}/${keys.length} screens OK`);
await b.close();
process.exit(bad || broken.length ? 1 : 0);
