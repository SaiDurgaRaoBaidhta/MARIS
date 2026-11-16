import { get } from "@/shared/api/client";
import type { Fleet } from "./types";

let fleet: Promise<Fleet> | null = null;
/** every yard with its tracks and cars (middleware: MARIS program VHSW01A). Asked once; `fresh` asks again. */
export function fetchFleet(fresh = false): Promise<Fleet> {
  if (!fleet || fresh) fleet = get<Fleet>("/fleet").catch(e => { fleet = null; throw e; });
  return fleet;
}

/**
 * The clean map APIs of each yard's old map screens (yard and shop). Their cars carry `label`: the text the old map
 * shows in a car's spot — the orientation mark before or after the number ("v4271", "3254}") and "$" for a car with
 * software ("$4425V") — built by the middleware from the old screens' own rules. VHSW01A (the fleet) has no such text.
 */
export const MAP_APIS: Record<string, string[]> = {
  HY: ["/yard-tracking/hayward-yard-map", "/yard-tracking/maps/hayward-shop"],
  DY: ["/yard-reports/maps/daly-city-yard", "/yard-reports/maps/daly-city-shop"],
  BS: ["/yard-reports/maps/berryessa"],
  RY: ["/yard-reports/maps/richmond-yard", "/yard-tracking/maps/richmond-shop"],
  CY: ["/yard-reports/maps/concord-yard", "/yard-reports/maps/concord-shop"],
  DP: ["/yard-tracking/maps/dublin"],
  PB: ["/yard-tracking/maps/pittsburg"],
  MB: ["/yard-reports/maps/millbrae"],
};

type MapCars = { cars?: { car?: string; label?: string }[] };

/** car number -> the label its old map shows, for one yard (UI code); a map that cannot be read just adds no labels */
export async function fetchLabels(yard: string): Promise<Record<string, string>> {
  const answers = await Promise.allSettled((MAP_APIS[yard] ?? []).map(path => get<MapCars>(path)));
  const labels: Record<string, string> = {};
  for (const a of answers) {
    if (a.status !== "fulfilled") continue;
    for (const c of a.value?.cars ?? []) if (c?.car && c.label) labels[c.car.trim()] = c.label;
  }
  return labels;
}

let labelled: Promise<Fleet> | null = null;
/**
 * The fleet with every yard's cars already labelled (orientation marks, "$"), so a map is drawn complete the first time
 * instead of the labels appearing a moment later. Asked once; `fresh` asks again (fleet and labels together).
 */
export function fetchLabelledFleet(fresh = false): Promise<Fleet> {
  if (!labelled || fresh) {
    const yards = Object.keys(MAP_APIS);
    labelled = Promise.all([fetchFleet(fresh), Promise.all(yards.map(y => fetchLabels(y)))])
      .then(([f, labels]) => yards.reduce((acc, y, i) => withLabels(acc, y, labels[i]), f))
      .catch(e => { labelled = null; throw e; });
  }
  return labelled;
}

/**
 * The fleet with one yard's cars written as its old maps write them. The map viewer reads a car's orientation from the car
 * text itself ("{3058", "4219V": the cab end in 3D, the mark in 2D) and takes the bare number with its own carNo(), as the
 * reference data did, so the marked text goes into `car` (the bare number stays in `label`'s place for anything else).
 */
export function withLabels(f: Fleet, yard: string, labels: Record<string, string>): Fleet {
  const tracks = f.fleet?.[yard];
  if (!tracks || !Object.keys(labels).length) return f;
  const labelled = tracks.map(t => ({ ...t, cars: t.cars.map(c => {
    const label = c ? labels[c.car?.trim() ?? ""] : undefined;
    return c && label ? { ...c, car: label, label: c.car.trim() } : c;
  }) }));
  return { ...f, fleet: { ...f.fleet, [yard]: labelled } };
}
