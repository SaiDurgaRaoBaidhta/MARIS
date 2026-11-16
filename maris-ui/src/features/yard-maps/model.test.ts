/* Car placement on the drawn maps: every car the old viewer showed is shown in the same box set, and no car is dropped —
 * the ones no drawn box holds are in `others`. Data: real cars of every yard (fleet VHSW01A) and a test car on every track
 * position the old screens know, with what the old viewer showed for each (captured from it in a browser). */
import { describe, expect, it } from "vitest";
import allPositions from "./__fixtures__/fleet-all-positions.json";
import realFleet from "./__fixtures__/fleet-real.json";
import labelsHy from "./__fixtures__/labels-hy.json";
import viewerShownAll from "./__fixtures__/viewer-shown-all-positions.json";
import viewerShownReal from "./__fixtures__/viewer-shown-real.json";
import { drawnCells, loadDrawing, loadSpec } from "./drawing";
import { buildModel, cellKey, type FleetTrack } from "./model";
import type { MapModel } from "./types";

const YARDS = ["HY", "DY", "BS", "RY", "CY", "DP", "PB", "MB"];
const digits = (s: string) => s.replace(/\D/g, "");

async function modelsOf(yard: string, fleet: FleetTrack[], labels: Record<string, string> = {}) {
  const yd = await loadDrawing(yard, false), sd = await loadDrawing(yard, true);
  const drawnAll = new Set([...drawnCells(yd), ...drawnCells(sd)]);
  const out: { model: MapModel; drawn: Set<string> }[] = [];
  for (const [shop, d] of [[false, yd], [true, sd]] as const) {
    if (!d) continue;
    const spec = (await loadSpec(yard, shop))!;
    out.push({ model: buildModel(yard, shop, spec, fleet, labels, drawnAll), drawn: drawnCells(d) });
  }
  return out;
}

/** the cars a map's drawn boxes show */
function shown(m: MapModel, drawn: Set<string>): Set<string> {
  const s = new Set<string>();
  for (const [track, cars] of Object.entries(m.cells)) cars.forEach((c, i) => { if (c && drawn.has(cellKey(track, i))) s.add(c.car); });
  return s;
}

describe("car placement on the drawn yard / shop maps", () => {
  for (const [name, fleetAll, viewer] of [["real cars", realFleet, viewerShownReal], ["a car on every position", allPositions, viewerShownAll]] as const) {
    it(`${name}: shows every car the old viewer showed, and keeps every other car in 'others'`, async () => {
      for (const y of YARDS) {
        const fleet = ((fleetAll as Record<string, FleetTrack[]>)[y] ?? []);
        const all = new Set(fleet.flatMap(t => t.cars).filter(Boolean).map(c => digits(c!.car)));
        const maps = await modelsOf(y, fleet);
        const visible = new Set<string>(); maps.forEach(({ model, drawn }) => shown(model, drawn).forEach(c => visible.add(c)));
        const others = new Set(maps[0].model.others.map(c => c.car));
        // the same cars as the old viewer
        const old = new Set(((viewer as Record<string, string[]>)[y] ?? []).filter(c => all.has(c)));
        expect([...visible].filter(c => !old.has(c)), `${y}: shown now, not in the old viewer`).toEqual([]);
        expect([...old].filter(c => !visible.has(c)), `${y}: shown in the old viewer, not now`).toEqual([]);
        // no car dropped: shown or listed, never both
        expect([...all].filter(c => !visible.has(c) && !others.has(c)), `${y}: dropped`).toEqual([]);
        expect([...others].filter(c => visible.has(c)), `${y}: listed and shown`).toEqual([]);
      }
    });
  }

  it("lists the cars no map can show: T-95 everywhere and Millbrae TM4-TM7", async () => {
    const tracks = new Set<string>();
    for (const y of YARDS) {
      const maps = await modelsOf(y, (realFleet as Record<string, FleetTrack[]>)[y] ?? []);
      maps[0].model.others.forEach(c => tracks.add(y + ":" + c.track));
    }
    expect([...tracks].sort()).toEqual(["CY:T-95", "DP:T-95", "DY:T-95", "HY:T-95", "MB:T-95", "MB:TM4", "MB:TM5", "MB:TM6", "MB:TM7", "PB:T-95", "RY:T-95"]);
  });

  it("puts the Hayward shop's lettered tracks in their boxes with the old map's orientation text", async () => {
    const [, shop] = await modelsOf("HY", (realFleet as Record<string, FleetTrack[]>).HY, (labelsHy as Record<string, Record<string, string>>).HY);
    const box = (t: string, i: number) => shop.model.cells[t]?.[i];
    expect(box("S1", 0)).toMatchObject({ car: "3142", text: "3142}", track: "1A", position: 1 });
    expect(box("2S", 0)).toMatchObject({ car: "4230", text: "v4230", track: "2S-1" });
    expect(box("3N", 2)).toMatchObject({ car: "3130", text: "{3130", track: "3N-3" });
  });
});
