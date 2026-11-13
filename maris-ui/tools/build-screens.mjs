// out2/ (captured) + screens.json (EGL source data) -> public/screens, src/features/*/ JSON
import fs from 'fs';
const [, , IN, SRC, FE] = process.argv;
const src = JSON.parse(fs.readFileSync(SRC));
const menus = JSON.parse(fs.readFileSync(IN + '/menus.json'));
fs.writeFileSync(FE + '/src/features/menu/menus.json', JSON.stringify(menus));
fs.rmSync(FE + '/public/screens', { recursive: true, force: true }); fs.mkdirSync(FE + '/public/screens', { recursive: true });
const index = {};
const file = k => k.replace(/[^A-Za-z0-9_-]/g, '_');
let ixDoc = null;
// map screen code -> yard / shop map (VHYT07I -> Hayward yard map)
const maps = JSON.parse(fs.readFileSync(FE + '/public/data/maps.json'));
const MAPCODE = {};
for (const [kind, set] of [['yard', maps.MAPS], ['shop', maps.SHOPS]]) for (const [y, m] of Object.entries(set)) for (const t of [m.id, m.uid]) if (t) MAPCODE[t.split(/\s/)[0].toUpperCase()] = { kind: 'map', yard: y, shop: kind === 'shop' };
Object.assign(MAPCODE, { VHYT04I: { kind: 'map', yard: 'HY', shop: true }, VHYT10I: { kind: 'map', yard: 'HY', shop: true }, VHYT60I: { kind: 'map', yard: 'CY', shop: true }, VHYT61I: { kind: 'map', yard: 'CY', shop: true },
  VHYT100I: { kind: 'map', yard: 'DY', shop: true }, VHYT101I: { kind: 'map', yard: 'DY', shop: true }, VHYT96I: { kind: 'map', yard: 'RY', shop: true }, VHYT95I: { kind: 'map', yard: 'RY', shop: true } });
const YARD_OF = { 'hayward yard': 'HY', 'daly city yard': 'DY', 'berryessa station': 'BS', 'richmond yard': 'RY', 'concord yard': 'CY', 'dublin/pleasanton': 'DP', 'pittsburg/baypoint': 'PB', 'milbrae storage': 'MB', 'millbrae storage': 'MB' };
// labels the side-bar menus already resolved by clicking them in the reference UI
const LABEL_KEY = {};
for (const sec of menus.sections) for (const g of sec.groups || []) for (const it of g.list) if (it.key) {
  const l = it.label.toLowerCase(); LABEL_KEY[l] = l in LABEL_KEY && LABEL_KEY[l] !== it.key ? null : it.key;   // a label that means different screens is not used
}
const target = (code) => { const c = String(code).toUpperCase(); return MAPCODE[c] || { kind: 'screen', key: 'egl:' + code }; };
const px = v => parseFloat(v) || 0;
const txtOf = n => typeof n === 'string' ? n : (n.k || []).map(txtOf).join('');
/* values the reference filled in at capture time (signed-on user, today, now) become tokens the UI fills from the session and clock */
const CAP = new Date(fs.statSync(IN + '/menus.json').mtime), p2 = n => String(n).padStart(2, '0');
const CAP_DATES = [[`${p2(CAP.getMonth() + 1)}-${p2(CAP.getDate())}-${CAP.getFullYear()}`, '{{date}}'], [`${CAP.getFullYear()}/${p2(CAP.getMonth() + 1)}/${p2(CAP.getDate())}`, '{{ymd}}'],
  [`${CAP.getFullYear()}-${p2(CAP.getMonth() + 1)}-${p2(CAP.getDate())}`, '{{isodate}}'], [`${p2(CAP.getMonth() + 1)}/${p2(CAP.getDate())}/${CAP.getFullYear()}`, '{{mdy}}']];
const tok = (v, field) => {
  let t = String(v);
  for (const [d, k] of CAP_DATES) t = t.split(d).join(k);
  t = t.replace(/\bMAXADMIN\b/g, '{{user}}');
  if (field && /^\d{2}:\d{2}(:\d{2})?$/.test(t)) t = t.length > 5 ? '{{hms}}' : '{{hm}}';
  return t.replace(/(Time:\s*)\d{2}:\d{2}:\d{2}/g, '$1{{hms}}');
};
function tokenize(s) {
  const walk = n => { if (typeof n === 'string') return tok(n, false);
    if (n.a && n.a.value != null && /^(input|textarea|select)$/.test(n.t)) n.a.value = tok(n.a.value, true);
    if (n.k) n.k = n.k.map(walk); return n; };
  if (s.body) s.body = s.body.map(walk);
}
function extent(body) {
  let r = 0, b = 0;
  const walk = (n, ox, oy) => { if (typeof n === 'string' || n.h || n.t === 'table') return; const x = ox + px(n.s?.left), y = oy + px(n.s?.top);
    r = Math.max(r, x + px(n.s?.width)); b = Math.max(b, y + px(n.s?.height)); (n.k || []).forEach(k => walk(k, x, y)); };
  body.forEach(n => walk(n, 0, 0));
  return { r, b };
}
/* a screen with no fields and 3+ functions is a menu: its functions as tile groups (as the reference shows it) */
function menuOf(s) {
  if (s.flow || !s.body) return null;
  const all = [];
  const walk = (n, ox, oy) => { if (typeof n === 'string') return; const x = ox + px(n.s?.left), y = oy + px(n.s?.top); all.push({ n, x, y, w: px(n.s?.width), h: px(n.s?.height) }); (n.k || []).forEach(k => walk(k, x, y)); };
  s.body.forEach(n => walk(n, 0, 0));
  if (all.some(({ n }) => /^(input|select|textarea)$/.test(n.t) && !/checkbox|radio/.test(n.a?.type || ''))) return null;
  const items = all.filter(({ n }) => /\b(mi|ms|ln)\b/.test(n.c || '') || /\bsq3d\b/.test(n.c || '') && /egl-hot/.test(n.c || ''));
  if (items.length < 3) return null;
  const groups = all.filter(({ n }) => /\b(k-grp|group)\b/.test(n.c || '') && (n.k || []).some(k => typeof k !== 'string' && /\b(k-leg|legend)\b/.test(k.c || '')))
    .map(g => ({ ...g, name: txtOf(g.n.k.find(k => typeof k !== 'string' && /\b(k-leg|legend)\b/.test(k.c || ''))).trim() }));
  const out = new Map();
  for (const it of items) {
    const n = it.n, wn = n.a?.['data-w'];
    let label = String(n.a?.title || '').trim() || txtOf(n).replace(/\s+/g, ' ').trim();
    if (!label) { const cap = all.filter(o => /\bk-lbl\b/.test(o.n.c || '') && Math.abs(o.y - it.y) < 10 && o.x > it.x).sort((a, b) => a.x - b.x)[0]; label = cap ? txtOf(cap.n).trim() : ''; }
    if (!label) continue;
    const cx = it.x + it.w / 2, cy = it.y + it.h / 2;
    const g = groups.filter(b => cx >= b.x && cx <= b.x + b.w && cy >= b.y && cy <= b.y + b.h).sort((a, b) => a.w * a.h - b.w * b.h)[0];
    const gname = g ? g.name : 'Functions';
    const w = wn && s.wiring?.[wn];
    let to = null;
    if (w && !w.off) { const c = w.to[0] || w.it; if (c) to = target(c); }
    if (!to && LABEL_KEY[label.toLowerCase()]) to = { kind: 'screen', key: LABEL_KEY[label.toLowerCase()] };
    const yard = YARD_OF[gname.toLowerCase()];
    if (!to && yard && /map$/i.test(label)) to = { kind: 'map', yard, shop: /shop/i.test(label) };
    if (!out.has(gname)) out.set(gname, []);
    if (!out.get(gname).some(x => x.label === label)) out.get(gname).push({ label, to, off: !!(w && w.off) || /egl-off/.test(n.c || '') });
  }
  return [...out].map(([name, list]) => ({ name, list }));
}
for (const f of fs.readdirSync(IN + '/screens')) {
  const s = JSON.parse(fs.readFileSync(IN + '/screens/' + f));
  const code = s.key.startsWith('egl:') ? s.key.slice(4) : (s.key.match(/^mf:(.+)$/) || [])[1];
  const e = code && src[code];
  if (e) {
    s.code = code;
    s.wiring = e.ctrls;
    s.meta = { module: e.module, progs: e.progs, reads: e.reads, writes: e.writes, inputs: e.inputs };
  }
  tokenize(s);
  const menu = menuOf(s); if (menu) s.menu = menu;
  // a screen wider / taller than its canvas (VHCF01I scrolls sideways in the reference): size it to its content
  if (s.body && !s.flow) { const ext = extent(s.body); s.cw = Math.max(s.cw, ext.r + 8); s.ch = Math.max(s.ch, ext.b + 8); }
  index[s.key] = { title: s.title, code: s.code };
  if (s.key === 'screenindex') ixDoc = s;
  if (s.body === null && /map/.test(s.cls)) continue;   // maps are drawn by MapPage from the map specs
  fs.writeFileSync(FE + '/public/screens/' + file(s.key) + '.json', JSON.stringify(s));
}
// every source screen the extractor could not open still gets its title, so links name it
for (const [c, e] of Object.entries(src)) if (!index['egl:' + c]) index['egl:' + c] = { title: e.raw, code: c, missing: true };
// a code with no source screen opens the reference's stand-in form (mf:CODE) when it has one, else "never migrated"
const written = new Set(fs.readdirSync(FE + '/public/screens').map(f => f.replace(/\.json$/, '')));
for (const k of Object.keys(index)) { const m = /^mf:(.+)$/.exec(k); if (m && !written.has(file('egl:' + m[1]))) index['egl:' + m[1]] = { ...index['egl:' + m[1]], title: index[k].title, code: m[1], alias: k, missing: undefined }; }
for (const f of fs.readdirSync(FE + '/public/screens')) { const d = JSON.parse(fs.readFileSync(FE + '/public/screens/' + f));
  for (const c of Object.values(d.wiring || {})) for (const code of [...c.to, c.it].filter(Boolean)) {
    const k = Object.keys(index).find(x => x.toUpperCase() === ('egl:' + code).toUpperCase()); if (!k) index['egl:' + code] = { title: code, code, missing: true }; } }
// a map screen code opens the drawn map, whichever way it is reached
for (const [c, m] of Object.entries(MAPCODE)) { const k = Object.keys(index).find(x => x.toUpperCase() === 'EGL:' + c); if (k) index[k].map = { yard: m.yard, shop: m.shop }; else index['egl:' + c] = { title: c, code: c, map: { yard: m.yard, shop: m.shop } }; }
fs.writeFileSync(FE + '/src/features/screens/screenIndex.json', JSON.stringify(index));
// Screen Index document: sections and rows from the captured page
const txt = n => typeof n === 'string' ? n : (n.k || []).map(txt).join('');
const all = []; const walk = n => { if (typeof n !== 'string') { all.push(n); (n.k || []).forEach(walk); } }; (ixDoc?.body || []).forEach(walk);
const secs = all.filter(n => /\bix-sec\b/.test(n.c || '')).map(sec => {
  const sh = sec.k.find(k => /\bix-sh\b/.test(k.c || ''));
  const letter = txt(sh.k.find(k => /ix-letter/.test(k.c || ''))), name = txt(sh).slice(letter.length).trim();
  const rows = sec.k.filter(k => /\bix-row\b/.test(k.c || '')).map(r => {
    const part = c => r.k.find(k => new RegExp('\\b' + c + '\\b').test(k.c || ''));
    const go = part('ix-go');
    const links = (go.k || []).filter(k => typeof k !== 'string' && /\bln\b/.test(k.c || '')).map(txt);
    return { code: txt(part('ix-code')), title: txt(part('ix-title')), id: txt(part('ix-id')), links, note: links.length ? '' : txt(go) };
  });
  return { letter, name, rows };
});
fs.writeFileSync(FE + '/src/features/screen-index/screenIndexDoc.json', JSON.stringify(secs));
console.log('screens', fs.readdirSync(FE + '/public/screens').length, 'index', Object.keys(index).length, 'ix secs', secs.length);
