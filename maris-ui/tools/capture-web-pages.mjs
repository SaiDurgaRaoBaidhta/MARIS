// The web pages (MARIS Print, ColdFusion, NOGO, Train Summary, Availability, Car Cleaner, Car Move, WDMS) flow with their own CSS:
// capture them with their computed look inlined, so the React side can draw them without that CSS.
import { chromium } from 'playwright-core';
import fs from 'fs';
const OUT = process.argv[2];
const b = await chromium.launch({ channel: 'chrome' });
const p = await b.newPage({ viewport: { width: 1440, height: 900 } });
await p.goto(new URL('../../maris-2-v4.html', import.meta.url).href); await p.waitForSelector('.lg-go');
await p.selectOption('.lg-user', 'MAXADMIN'); await p.click('.lg-go'); await p.waitForTimeout(3000);
const PAGES = [['web:print', 'Web.marisPrint(r)'], ['web:cf', 'Web.coldFusion(r)'], ['web:nogo', 'Web.nogo(r)'], ['web:train', 'Web.trainSummary(r)'],
  ['web:avail', 'Web.availability(r)'], ['web:wdms', 'Web.wdms(r)'], ['web:clean', 'Web.carCleaner(r, "CY")'], ['web:move', 'Web.carMove(r, "CY")']];
for (const [key, call] of PAGES) {
  const s = await p.evaluate(async ([key, call]) => {
    const r = Menus.root(); [...WM.list].reverse().forEach(x => { if (x !== r) WM.close(x); });
    new Function('r', call)(r); await new Promise(res => setTimeout(res, 1200));
    const w = WM.list.at(-1);
    const P = ['display', 'flexDirection', 'flexWrap', 'justifyContent', 'alignItems', 'gap', 'gridTemplateColumns', 'padding', 'margin', 'borderTop', 'borderRight', 'borderBottom', 'borderLeft', 'borderRadius',
      'backgroundColor', 'backgroundImage', 'color', 'fontSize', 'fontWeight', 'fontFamily', 'fontStyle', 'textAlign', 'lineHeight', 'whiteSpace', 'verticalAlign', 'textDecorationLine', 'boxShadow', 'position', 'left', 'top', 'width', 'height', 'minWidth', 'overflow', 'borderCollapse', 'letterSpacing', 'textTransform'];
    const DEF = { display: 'block', position: 'static', overflow: 'visible', fontStyle: 'normal', textDecorationLine: 'none', textTransform: 'none', letterSpacing: 'normal', boxShadow: 'none', backgroundImage: 'none', backgroundColor: 'rgba(0, 0, 0, 0)', whiteSpace: 'normal', verticalAlign: 'baseline', gap: 'normal', borderCollapse: 'separate' };
    const ser = (e, parentCs) => {
      if (e.nodeType === 3) return e.textContent.trim() ? e.textContent : null;
      if (e.nodeType !== 1 || /^(script|style)$/i.test(e.tagName)) return null;
      const cs = getComputedStyle(e); if (cs.display === 'none') return null;
      const n = { t: e.tagName.toLowerCase(), s: {} };
      for (const k of P) { const v = cs[k]; if (v == null || v === '' || DEF[k] === v || /^0px( none)?/.test(v) && /^border/.test(k) || v === 'auto' && /^(left|top|width|height)$/.test(k)) continue;
        if (parentCs && /^(color|fontSize|fontWeight|fontFamily|lineHeight|textAlign|whiteSpace|letterSpacing)$/.test(k) && parentCs[k] === v) continue;   // inherited: leave it to the parent
        if ((k === 'width' || k === 'height') && !/^(table|img|input|select|button|svg|th|td|col)$/i.test(e.tagName) && !['absolute', 'fixed'].includes(cs.position) && cs.display !== 'inline-block' && !/flex|grid/.test(cs.display)) continue;
        n.s[k] = v; }
      if (e.className && typeof e.className === 'string') n.c = e.className;
      const a = {}; ['type', 'title', 'colspan', 'rowspan', 'value', 'href'].forEach(k => { const v = e.getAttribute(k); if (v != null) a[k] = v; }); if (e.readOnly) a.readonly = 1; if (e.checked) a.checked = 1; if (Object.keys(a).length) n.a = a;
      if (e.tagName === 'SELECT') { n.o = [...e.options].map(o => [o.value, o.textContent]); a.value = e.value; n.a = a; return n; }
      if (e.tagName === 'svg') { n.html = e.outerHTML; return n; }
      const k = [...e.childNodes].map(c => ser(c, cs)).filter(x => x != null); if (k.length) n.k = k;
      return n;
    };
    const rootCs = getComputedStyle(w.cv);
    return { key: w.key, cls: w.win.className, title: (w.win.querySelector('.titlebar .ttl') || {}).textContent || '', ver: '', cw: w.cv.offsetWidth, ch: w.cv.scrollHeight, flow: true,
      rootStyle: { color: rootCs.color, fontSize: rootCs.fontSize, fontFamily: rootCs.fontFamily, backgroundColor: rootCs.backgroundColor },
      body: [...w.cv.childNodes].map(c => ser(c, rootCs)).filter(Boolean) };
  }, [key, call]);
  fs.writeFileSync(OUT + '/screens/' + key.replace(/[^A-Za-z0-9_-]/g, '_') + '.json', JSON.stringify(s));
  console.log(key, s.title, JSON.stringify(s).length);
}
await b.close();
