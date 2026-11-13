// Runs the original maris-2-v4.html in Chrome and dumps menus + every screen's final (post-layout) DOM as JSON.
import { chromium } from 'playwright-core';
import fs from 'fs';
const OUT = process.argv[2];
const PAGE = new URL('../../maris-2-v4.html', import.meta.url).href;
const b = await chromium.launch({ channel: 'chrome' });
const p = await b.newPage({ viewport: { width: 1440, height: 900 } });
p.on('pageerror', e => console.log('ERR', e.message.slice(0, 200)));
p.on('dialog', d => d.dismiss());
async function signOn(user) {
  await p.goto(PAGE); await p.waitForSelector('.lg-go');
  await p.selectOption('.lg-user', user); await p.click('.lg-go');
  await p.waitForFunction(() => document.querySelectorAll('.sd-sec').length > 0, null, { timeout: 30000 });
  await p.waitForTimeout(800);
}
const HELPERS = () => {
  window.__cur = () => WM.list.filter(x => !x.frame.classList.contains('nav-hidden') && !x.win.classList.contains('minimized')).sort((a, b) => +b.frame.style.zIndex - +a.frame.style.zIndex)[0];
  const ser = e => {
    if (e.nodeType === 3) { const t = e.textContent; return t.trim() ? t : null; }
    if (e.nodeType !== 1) return null;
    const n = { t: e.tagName.toLowerCase() };
    if (e.className && typeof e.className === 'string') n.c = e.className;
    // every inline declaration the layout passes left (position, size, padding, overflow, table layout, --lift …)
    const s = {}; for (let i = 0; i < e.style.length; i++) { const k = e.style[i]; s[k.startsWith('--') ? k : k.replace(/-([a-z])/g, (_, c) => c.toUpperCase())] = e.style.getPropertyValue(k); }
    if (Object.keys(s).length) n.s = s;
    if (getComputedStyle(e).display === 'none') n.h = 1;
    const a = {};
    ['type', 'title', 'maxlength', 'placeholder', 'data-w', 'colspan', 'rowspan', 'viewBox', 'd', 'href'].forEach(k => { const v = e.getAttribute(k); if (v != null) a[k] = v; });
    if (e.readOnly) a.readonly = 1; if (e.disabled) a.disabled = 1; if (e.checked) a.checked = 1;
    if ((e.tagName === 'INPUT' || e.tagName === 'TEXTAREA') && e.value) a.value = e.value;
    if (Object.keys(a).length) n.a = a;
    if (e.tagName === 'SELECT') { n.o = [...e.options].map(o => [o.value, o.textContent]); n.a = Object.assign(n.a || {}, { value: e.value }); return n; }
    if (e.tagName === 'svg') { n.html = e.outerHTML; return n; }
    const k = [...e.childNodes].map(ser).filter(x => x != null); if (k.length) n.k = k;
    return n;
  };
  window.__ser = w => ({ key: w.key, cls: w.win.className, title: (w.win.querySelector('.titlebar .ttl') || {}).textContent || '',
    ver: (w.win.querySelector('.titlebar .ver') || {}).textContent || '',
    cw: parseFloat(w.cv.style.width) || w.cv.scrollWidth, ch: parseFloat(w.cv.style.height) || w.cv.scrollHeight,
    body: /\bmap\b/.test(w.win.className) && !/\begl\b/.test(w.win.className) ? null : [...w.cv.childNodes].map(ser).filter(Boolean) });
  window.__home = () => { const r = Menus.root(); [...WM.list].reverse().forEach(x => { if (x !== r && WM.list.includes(x)) WM.close(x); }); };
};
// 1. sections per user group
const GROUPS = { VTD: 'TRBLDESK', TRANS: 'CENTRAL', TOWER: 'CYTOWER', SHOP: 'CYSHOP', ADMIN: 'MAXADMIN' };
const access = {};
for (const [role, u] of Object.entries(GROUPS)) {
  await signOn(u);
  access[role] = await p.evaluate(() => [...document.querySelectorAll('.sd-sec')].map(e => e._s.cap));
  console.log(role, access[role].length);
}
// 2. as admin: every menu item -> screen
await signOn('MAXADMIN'); await p.evaluate(HELPERS);
const sections = await p.evaluate(() => [...document.querySelectorAll('.sd-sec')].map(e => { const s = e._s; return { cap: s.cap, to: s.to, kind: s.kind, title: s.title, groups: s.groups }; }));
const screens = {};
const save = (sc) => { const k = sc.key; if (!screens[k]) { screens[k] = sc; } return k; };
for (const [si, s] of sections.entries()) {
  if (s.kind !== 'menu') { await p.evaluate(i => { __home(); document.querySelectorAll('.sd-sec')[i].querySelector('.sd-row').click(); }, si); await p.waitForTimeout(400);
    s.key = await p.evaluate(() => { const w = __cur(); return w && w !== Menus.root() ? w.key : null; });
    if (s.key) save(await p.evaluate(() => __ser(__cur()))); continue; }
  for (const g of s.groups) for (const it of g.list) {
    if (it.dis) continue;
    const r = await p.evaluate(([si, i]) => { __home(); const a = document.querySelectorAll('.sd-sec')[si].querySelector(`.sd-it[data-i="${i}"]`); if (!a) return null; a.click(); return true; }, [si, it.i]);
    await p.waitForTimeout(1700);
    const w = await p.evaluate(() => { const w = __cur(); if (!w || w === Menus.root()) return null; return __ser(w); });
    if (w) { it.key = save(w); } else it.none = 1;
    if (w && /egl:/.test(w.key) === false) console.log('  hand-made:', s.cap, '/', it.label, '->', w.key, w.cls);
  }
  console.log('section', s.cap, Object.keys(screens).length);
}
// 3. every remaining source screen
const codes = await p.evaluate(() => Object.keys(MARIS_SCREENS));
for (const c of codes) {
  if (screens['egl:' + c]) continue;
  const w = await p.evaluate(c => { __home(); const w = EglScreens.open(Menus.root(), c); return w ? __ser(w) : null; }, c).then(async r => { await p.waitForTimeout(1700); return r ? await p.evaluate(() => __ser(__cur())) : r; }).catch(e => (console.log('fail', c, e.message.slice(0, 100)), null));
  ;
  if (w) save(w); else console.log('no window', c);
}
// 4. quick-access pages
for (const [i, q] of ['inventory', 'print', 'coldfusion', 'nogo', 'index'].entries()) {
  await p.evaluate(i => { __home(); document.querySelectorAll('.sd-q')[i].click(); }, i); await p.waitForTimeout(600);
  const w = await p.evaluate(() => __ser(__cur())); w.quick = q; save(w); console.log('quick', q, w.key, w.cls);
}
fs.writeFileSync(OUT + '/menus.json', JSON.stringify({ access, sections }, null, 1));
fs.mkdirSync(OUT + '/screens', { recursive: true });
for (const [k, v] of Object.entries(screens)) fs.writeFileSync(OUT + '/screens/' + k.replace(/[^A-Za-z0-9_-]/g, '_') + '.json', JSON.stringify(v));
console.log('screens', Object.keys(screens).length);
await b.close();
