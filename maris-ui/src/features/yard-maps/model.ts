/* Where every car of a yard goes on its drawn maps: the old viewer's placement (applyYard, MAP_BOXES, aliasTrack, placeOf),
 * ported as it was, plus the one thing it lacked: a car no drawn box can show (T-95, Millbrae TM4-TM7 …) is kept in
 * `others` instead of vanishing. */
import boxesJson from "./data/boxes.json";
import tracksJson from "./data/tracks.json";
import type { MapCar, MapModel, MapSpec, YardCode } from "./types";

/** a car as the fleet (VHSW01A) sends it: bare number, home yard, status, ... */
export interface FleetCar { car: string; home?: string; stat?: string; from?: string; reason?: string; consist?: string; label?: string }
export interface FleetTrack { id: string; cap?: number; cars: (FleetCar | null)[] }

type Box = [string, number] | null;
const BOXES = boxesJson as unknown as Record<YardCode, Record<string, Box[][]>>;
const TRACKS = tracksJson as unknown as Record<YardCode, { ids: string[]; caps: Record<string, number> }>;

const norm = (s: string) => String(s).toUpperCase().replace(/[^A-Z0-9]/g, "");

/** a MARIS track name as the drawing names it: exactly, ignoring punctuation / case, or with a leading S (station) added or dropped */
export function aliasTrack(yard: YardCode, id: string): string {
  const ids = TRACKS[yard]?.ids ?? [];
  if (ids.includes(id)) return id;
  const n = norm(id);
  for (const t of ids) {
    const nt = norm(t);
    if (nt === n) return t;
    const ns = n.replace(/^S(?=[A-Z])/, ""), nts = nt.replace(/^S(?=[A-Z])/, "");
    if ((nts === ns || ns === nt || n === nts) && (ns !== n || nts !== nt)) return t;   // SSTOR1 (MARIS) = STOR1 (drawing)
  }
  return id;
}

/** where a MARIS track goes on the drawing: its own drawn track, or a box of a drawn row ("S-1A" -> row S-1, box 0) */
export function placeOf(yard: YardCode, id: string): [string, number] {
  const ids = TRACKS[yard]?.ids ?? [];
  const a = aliasTrack(yard, id);
  if (ids.includes(a)) return [a, 0];
  const m = /^(.*\D)(\d+)([A-Z])$/.exec(String(id).toUpperCase());
  if (m) for (const t of ids) {
    const r = /^(.*\D)(\d+)$/.exec(t);
    if (r && r[1] === m[1] && +r[2] === +m[2]) return [t, m[3].charCodeAt(0) - 65];
  }
  return [a, 0];
}

interface Placed { id: string; cap: number; cars: (MapCar | null)[] }

/**
 * Every car of one yard on the drawing's tracks (the yard map's and the shop map's together, as the old viewer kept them):
 * drawn track id -> its positions. `labels` gives each car's old map text (orientation marks, "$").
 */
export function placeYard(yard: YardCode, fleet: FleetTrack[], labels: Record<string, string> = {}): Record<string, (MapCar | null)[]> {
  const byId: Record<string, Placed> = {};
  for (const [id, cap] of Object.entries(TRACKS[yard]?.caps ?? { "T-95": 30 })) byId[id] = { id, cap, cars: new Array(cap).fill(null) };
  for (const t of fleet) {
    const cars = (t.cars ?? []).map((c, i): MapCar | null => {
      if (!c) return null;
      const no = String(c.label ?? c.car).replace(/\D/g, "") || String(c.car).trim();
      return { car: no, text: labels[no] ?? (c.label ? String(c.car) : no), home: c.home ?? "", status: c.stat ?? "",
               consist: c.consist || undefined, moveReason: c.reason || undefined, track: t.id, position: i + 1 };
    });
    // the boxes the old screens show this track's positions in (every map that shows it)
    const boxes = BOXES[yard]?.[norm(t.id)];
    if (boxes && boxes.length) {
      let all = true;
      boxes.forEach(list => cars.forEach((c, i) => {
        const to = list[i], row = to && byId[to[0]];
        if (row && to[1] < row.cap) { if (c) row.cars[to[1]] = c; } else if (c) all = false;
      }));
      if (all) continue;
    }
    const [id, at] = placeOf(yard, t.id);
    const drawn = byId[id];
    // a whole drawn track takes MARIS's track as it is; a sub-location fills its boxes of the drawn row
    if (!drawn || (at === 0 && id === aliasTrack(yard, t.id))) { byId[id] = { id, cap: t.cap ?? cars.length, cars }; continue; }
    cars.forEach((c, i) => { if (c) drawn.cars[Math.min(at + i, drawn.cap - 1)] = c; });
  }
  const out: Record<string, (MapCar | null)[]> = {};
  for (const p of Object.values(byId)) out[p.id] = p.cars;
  return out;
}

/** the drawn boxes of a map: "track|index" for every car box of its drawing */
export type DrawnCells = Set<string>;
export const cellKey = (track: string, idx: number) => track + "|" + idx;

/**
 * One map ready to draw. `drawnAll` holds the car boxes of every map of this yard (yard map and shop map): a car shown on
 * neither goes to `others`, so no car of the yard is ever dropped.
 */
export function buildModel(yard: YardCode, shop: boolean, spec: MapSpec, fleet: FleetTrack[], labels: Record<string, string>,
                           drawnAll: DrawnCells): MapModel {
  const cells = placeYard(yard, fleet, labels);
  const shown = new Set<string>();
  for (const [track, cars] of Object.entries(cells)) cars.forEach((c, i) => { if (c && drawnAll.has(cellKey(track, i))) shown.add(c.car + "@" + track + "|" + i); });
  const others: MapCar[] = [];
  const seen = new Set<string>();
  for (const t of fleet) for (const c of t.cars ?? []) {
    if (!c) continue;
    const no = String(c.label ?? c.car).replace(/\D/g, "") || String(c.car).trim();
    if (seen.has(no)) continue;
    const visible = Object.entries(cells).some(([track, cars]) => cars.some((x, i) => x && x.car === no && shown.has(no + "@" + track + "|" + i)));
    if (!visible) {
      seen.add(no);
      const pos = t.cars.indexOf(c) + 1;
      others.push({ car: no, text: labels[no] ?? no, home: c.home ?? "", status: c.stat ?? "", consist: c.consist || undefined,
                    moveReason: c.reason || undefined, track: t.id, position: pos });
    }
  }
  return { yard, shop, spec, cells, others };
}

/** the old viewer's car colour: home yard everywhere, except Dublin and Pittsburg, which colour by status as MARIS does */
export function carClass(c: MapCar, yard: YardCode): string {
  if (yard === "DP" || yard === "PB") {
    if (c.status === "USABLE") return "c-USE";
    if (c.status === "GO") return "c-GO";
    if (/^(HIGH|HPM)$/.test(c.status)) return "c-HPM";
    return "c-NOGO";
  }
  return "c-" + (["CY", "DY", "RY", "HY"].includes(c.home) ? c.home : "XX");
}
