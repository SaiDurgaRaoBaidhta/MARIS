/* What a yard or shop map's buttons and clicks do, as the old map screens wire them (their <screen>Connections and statement
 * groups). Every other screen opens by its screen code with the old pass values as query parameters ("egl:VHYT08I?..."),
 * so the React pages already built for those screens take over. Nothing here shows made-up data. */
import { get } from "@/shared/api/client";
import type { Page } from "@/shared/lib/app-state";
import type { MapCar, YardCode } from "../types";
import { MAP_SCREENS, PSEUDO_TRACK, SHOP_MOVE_GUI, SHOP_REPORT, mapScreen, type MapMode } from "./screens";

export type Params = Record<string, string | number>;
/** a screen to open: its code and the old pass values */
export interface Target { screen: string; params: Params }

export const screenPage = ({ screen, params }: Target): Page => {
  const q = Object.keys(params).length ? "?" + new URLSearchParams(Object.entries(params).map(([k, v]) => [k, String(v)])) : "";
  return { kind: "screen", key: "egl:" + screen + q, label: screen };
};

/* ------------------------------------------------------------------------------------------------------------------ *
 * click rules                                                                                                         *
 * ------------------------------------------------------------------------------------------------------------------ */

/** the update yard maps' three clicks (VHYT71IS-CLICK / VHYT41IS-CLK-..): from car, to position on the same track, to track */
export interface YardMove { clicked: number; firstTrack: string; fromTrack: string; fromPos: number; toPos: number }
export const NO_YARD_MOVE: YardMove = { clicked: 0, firstTrack: "", fromTrack: "", fromPos: 0, toPos: 0 };

/** the update shop maps' two clicks (VHYT61IS-CK-.. / VHYT10IS-CK-..): the car to move, then the place it goes to */
export interface ShopPick { track: string; position: number; car: string }

/** the selection a map shows while a move is being picked */
export type Selection =
  | { kind: "none" }
  | { kind: "yard"; track: string; from: number; to: number; clicks: number }
  | { kind: "shop"; track: string; position: number; car: string };

export interface ClickResult<S> { state: S; open?: Target }

/** one click on a box of an update yard map (an empty box counts too: the old cells are buttons) */
export function yardMoveClick(s: YardMove, yard: string, track: string, position: number): ClickResult<YardMove> {
  const clicked = s.clicked + 1;
  if (clicked === 1) return { state: { clicked, firstTrack: track, fromTrack: track, fromPos: position, toPos: position } };
  if (clicked === 2) return { state: { ...s, clicked, toPos: track === s.firstTrack ? position : s.toPos } };
  return {
    state: NO_YARD_MOVE,
    open: { screen: "VHYT11I", params: { yrdTrkgYrd: yard, yrdSubLoc: s.fromTrack, fromYrdSubLoc: s.fromTrack, toYrdSubLoc: track,
      positionNoFrom: s.fromPos, positionNoTo: s.toPos } },
  };
}

/** a track tag on an update yard map (VHYT71IS-TRACK-1): the car move detail of that track, with the positions picked so far */
export function yardTrackClick(s: YardMove, yard: string, track: string): ClickResult<YardMove> {
  const params: Params = { yrdTrkgYrd: yard, yrdSubLoc: track, fromYrdSubLoc: track };
  if (s.clicked > 0) Object.assign(params, { positionNoFrom: s.fromPos, positionNoTo: s.toPos });
  return { state: NO_YARD_MOVE, open: { screen: "VHYT11I", params } };
}

/** one click on a box of an update shop map: the first picks the car, a second on another box opens the car move (VHYT03I);
 *  a second click on the same box drops the pick */
export function shopMoveClick(pick: ShopPick | null, yard: string, from: string, track: string, position: number, car: string)
  : ClickResult<ShopPick | null> {
  if (!pick) return { state: { track, position, car } };
  if (pick.track === track && pick.position === position) return { state: null };
  return {
    state: null,
    open: { screen: "VHYT03I", params: { carNo: pick.car, yrdTrkgYrd: yard, yrdSubLoc: pick.track, newYrdLoc: track, carNo2: car,
      posnNoFrom: pick.position, posnNoTo: position, whichGui: SHOP_MOVE_GUI, whichGuiFrom: from } },
  };
}

/* ------------------------------------------------------------------------------------------------------------------ *
 * buttons                                                                                                             *
 * ------------------------------------------------------------------------------------------------------------------ */

export interface MapButton {
  id: string;
  label: string;          // the old screen's caption
  key?: string;           // Alt + this letter presses it (the viewer's underlined letter)
  disabled?: boolean;
  run(): void;
}

export interface ButtonContext {
  yard: YardCode;
  shop: boolean;
  mode: MapMode;
  open(p: Page): void;
  refresh(): void;
  exit(): void;
  maint(): void;
  maintDenied?: boolean;
  /** the car picked on an update shop map (SV / HV, Change Reason and the CI buttons use it) */
  pick?: ShopPick | null;
}

/** the map's buttons, in the old screen's order */
export function mapButtons(c: ButtonContext): MapButton[] {
  const s = MAP_SCREENS[c.yard];
  if (!s) return [];
  const yd = s.legacy, from = mapScreen(c.yard, c.shop, c.mode);
  const go = (screen: string, params: Params = {}) => () => c.open(screenPage({ screen, params }));
  const b = (id: string, label: string, key: string | undefined, run: () => void, disabled?: boolean): MapButton => ({ id, label, key, run, disabled });

  if (!c.shop && c.mode === "inquiry") return [
    b("refresh", "Refresh Map", "M", c.refresh),
    b("pseudo", "Pseudo Status Train", "P", go("VHYT09I", { yrdTrkgYrd: yd })),
    b("print", "Print", "T", go("VHYT24I", { yrdTrkgYrd: yd })),
    b("exit", "Return", "R", c.exit),
  ];
  if (!c.shop) return [
    ...(s.shop ? [b("shop", "Shop Map", "S", go(s.shop.inquiry))] : []),          // the yard update maps open the shop's inquiry map
    b("pseudo", "Pseudo Stat Trn", "P", go("VHYT09I", { yrdTrkgYrd: yd })),
    b("tower", "Tower Menu", "T", go("VHMTOI")),
    b("reports", "Reports", "R", go("VHYTM3I", { yrdTrkgYrd: yd })),
    b("print", "Print", undefined, go("VHYT24I", { yrdTrkgYrd: yd })),
    b("maint", "Maint", "M", c.maint, c.maintDenied),
    b("refresh", "ReFresh", "F", c.refresh),
    b("exit", "Exit", "X", c.exit),
  ];
  if (c.mode === "inquiry") return [
    b("refresh", "ReFresh", "F", c.refresh),
    b("print", "Print", "P", go("VHYT27I", { whichReport: SHOP_REPORT, yrdTrkgYrd: yd })),
    b("exit", "Return", "R", c.exit),
  ];
  const p = c.pick ?? null;
  const carParams: Params = { carNo: p?.car ?? "", whichGuiFrom: from, yrdTrkgYrd: yd };
  return [
    b("refresh", "ReFresh", "F", c.refresh),
    b("reason", "Change Reason", "C", go("VHYT06I", p ? { yrdTrkgYrd: yd, yrdSubLoc: p.track, carNo: p.car, posnNo: p.position } : { yrdTrkgYrd: yd })),
    b("svhv", "SV / HV", "H", go("VHSV06I", { carNo: p?.car ?? "" }), !p?.car),       // the old map enables it only on a car
    b("never-go", "CI - Never Go", "N", go("VHCI03I", carParams)),
    b("service", "CI - Service", "I", go("VHCI02I", carParams)),
    b("yard", "Yard Map", "Y", go(s.yard.inquiry)),                                 // the shop update maps open the yard's inquiry map
    b("shop-menu", "Shop Menu", "S", go("VHMVSI")),
    b("reports", "Reports", "R", go("VHYTM3I", { yrdTrkgYrd: yd })),
    b("print", "Print", "P", go("VHYT27I", { whichReport: SHOP_REPORT, yrdTrkgYrd: yd })),
    b("exit", "Exit", "X", c.exit),
  ];
}

/* ------------------------------------------------------------------------------------------------------------------ *
 * the whole map: buttons + clicks                                                                                     *
 * ------------------------------------------------------------------------------------------------------------------ */

/** the old Maint access check (VH01A02), where a clean API answers it; true when there is none to ask */
export async function maintenanceAllowed(yard: YardCode): Promise<boolean> {
  const m = MAP_SCREENS[yard]?.maintenanceMap;
  if (!m) return true;
  return (await get<{ allowed: boolean }>(`/yard-tracking/maps/${encodeURIComponent(m)}/maintenance-access`)).allowed;
}

/** a click on a car box (car null: an empty box) */
export function carClickTarget(_yard: YardCode, shop: boolean, mode: MapMode, car: MapCar | null): Target | null {
  if (mode === "inquiry" && shop && car) return { screen: "VHSV06I", params: { carNo: car.car } };   // the shop inquiry maps: a car's status
  return null;                                                                                        // the yard inquiry maps' cars open nothing
}

/** a click on a track tag of an inquiry map (or its PSEUDO TRACK): the track's cars (VHYT08I) */
export function trackClickTarget(yard: YardCode, track: string): Target {
  return { screen: "VHYT08I", params: { yrdSubLoc: track, yrdTrkgYrd: MAP_SCREENS[yard]?.legacy ?? yard } };
}

export { PSEUDO_TRACK };
