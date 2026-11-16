// Which drawn box shows each box of the old map screens: the drawings were traced from those screens, so a box sits at
// the old widget's position plus one offset per drawing (measured on the tracks both name the same way).
//   node tools/match-map-boxes.mjs boxes.json cells.json  -> JSON { yard: { NORMALISED-RECORD: [[track, index], ...] } }
// boxes.json: tools/egl-map-boxes.mjs output; cells.json: { "MAP:CY": { screen, cells: [[track, firstIndex, [[x, y], ...]]] } }
import { readFileSync } from "node:fs";

const [boxesFile, cellsFile] = process.argv.slice(2);
const boxes = JSON.parse(readFileSync(boxesFile, "utf8"));
let drawings = JSON.parse(readFileSync(cellsFile, "utf8")); if (typeof drawings === "string") drawings = JSON.parse(drawings);
const norm = s => String(s).toUpperCase().replace(/[^A-Z0-9]/g, "");
const median = a => { const s = [...a].sort((x, y) => x - y); return s.length ? s[s.length >> 1] : 0; };
const out = {}, report = {};
for (const [key, d] of Object.entries(drawings)) {
  const yard = key.split(":")[1];
  const screen = d.screen.replace(/^VHY(\d)/, "VHYT$1");   // VHY105I is VHYT105I.egl
  const b = boxes[screen]; if (!b) { report[key] = "no old screen " + screen; continue; }
  const cells = d.cells.flatMap(([track, from, xy]) => xy.map(([x, y], i) => ({ track, index: from + i, x, y })));
  const byTrack = {}; cells.forEach(c => { (byTrack[norm(c.track)] ||= []).push(c); });
  const dx = [], dy = [];
  for (const [rec, list] of Object.entries(b)) {
    const same = byTrack[norm(rec)]; if (!same) continue;
    list.forEach(([n, x, y]) => { const c = same.find(c => c.index === n - 1); if (c) { dx.push(c.x - x); dy.push(c.y - y); } });
  }
  const ox = median(dx), oy = median(dy);
  let placed = 0, missed = [];
  for (const [rec, list] of Object.entries(b)) {
    const to = list.map(([, x, y]) => {
      let best = null, bd = 1e9;
      for (const c of cells) { const dd = Math.hypot(c.x - (x + ox), c.y - (y + oy)); if (dd < bd) { bd = dd; best = c; } }
      return best && bd <= 18 ? [best.track, best.index] : null;
    });
    if (to.some(t => !t)) missed.push(rec);
    const yardOut = (out[yard] ||= {});
    yardOut[norm(rec)] = (yardOut[norm(rec)] || []).concat(to.filter(Boolean).length ? [to] : []);
    placed += to.filter(Boolean).length;
  }
  report[key] = `${screen} offset ${ox},${oy} from ${dx.length} boxes; ${placed} boxes placed; unmatched: ${missed.join(" ")}`;
}
// a record shown on two maps (Daly City SS-06: yard map and shop exchange) keeps both placements
console.error(JSON.stringify(report, null, 1));
console.log(JSON.stringify(out));
