// Where each old map screen shows a MARIS track's cars: for every screen, the boxes (widget, left, top) that each
// sub-location's record fills, read from <SCREEN>.egl (positions) and <SCREEN>Connections.egl (bindings).
//   node tools/egl-map-boxes.mjs <EGLSource dir> VHYT100I VHYT70I ...   -> JSON { screen: { record: [[n, x, y], ...] } }
import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";

const [root, ...screens] = process.argv.slice(2);
// file names' case varies (Vhyt60i.egl): match ignoring case
const find = name => { for (const m of readdirSync(root)) { const d = join(root, m); try { const f = readdirSync(d).find(x => x.toLowerCase() === name.toLowerCase()); if (f) return join(d, f); } catch { /* not a folder */ } } return null; };
const out = {};
for (const s of screens) {
  const view = find(s + ".egl"), conn = find(s + "Connections.egl");
  if (!view || !conn) { out[s] = null; continue; }
  const pos = {};
  const v = readFileSync(view, "latin1");
  for (const m of v.matchAll(/^\s*(\w+)\s+\w+View\s*\{[\s\S]*?offsetTop\s*=\s*(-?\d+)[\s\S]*?offsetLeft\s*=\s*(-?\d+)/gm)) pos[m[1]] = [+m[3], +m[2]];
  const boxes = {};
  const c = readFileSync(conn, "latin1");
  // record "<SCREEN>W-<REC>" field "...TXT-<n> data" shown in screen.<WIDGET> "object"
  // every box shows its car's colour: "CAR-COLOR data" (one box) or "CAR-<n>-COLOR data" (box n) -> screen.<WIDGET> "color"
  const seen = new Set();
  const binds = [...c.matchAll(/\w+?I?W-([\w-]+?),\s*"CAR-(?:(\d+)-)?COLOR data",\s*screen\.(\w+),\s*"(?:color|foregroundColor)"/g),
    ...c.matchAll(/\w+?I?W-([\w-]+?),\s*"BUTTONTXT-?(\d*) data",\s*screen\.(\w+),\s*"object"/g)];
  for (const m of binds) {
    const p = pos[m[3]], n = +(m[2] || 1); if (!p || seen.has(m[1] + "#" + n)) continue;
    seen.add(m[1] + "#" + n);
    (boxes[m[1]] ||= []).push([n, p[0], p[1]]);
  }
  for (const k of Object.keys(boxes)) boxes[k].sort((a, b) => a[0] - b[0]);
  out[s] = boxes;
}
console.log(JSON.stringify(out));
