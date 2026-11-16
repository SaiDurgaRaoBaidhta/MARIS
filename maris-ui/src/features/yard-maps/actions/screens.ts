/* The old map screens of each yard and shop, and what they pass to the screens they open (the old <screen>Connections and
 * statement groups, e.g. VHYT71IS-PSEUDO, VHYT10IS-SHOP, VHYT61IS-CK-1A). */
import type { YardCode } from "../types";

export type MapMode = "inquiry" | "update";

export interface MapScreens {
  /** the yard code the old maps pass to every child (YRD-TRKG-YRD): "HY", "DPX", "SVB", ... */
  legacy: string;
  yard: { inquiry: string; update: string };
  /** the yard's shop maps (only yards with a shop) */
  shop?: { inquiry: string; update: string };
  /** the clean API that answers the old Maint access check (VH01A02 for the update map's GUI name), when one exists */
  maintenanceMap?: string;
}

export const MAP_SCREENS: Record<YardCode, MapScreens> = {
  HY: { legacy: "HY", yard: { inquiry: "VHYT07I", update: "VHYT05I" }, shop: { inquiry: "VHYT04I", update: "VHYT10I" } },
  DY: { legacy: "DY", yard: { inquiry: "VHYT105I", update: "VHYT106I" }, shop: { inquiry: "VHYT100I", update: "VHYT101I" } },
  RY: { legacy: "RY", yard: { inquiry: "VHYT90I", update: "VHYT91I" }, shop: { inquiry: "VHYT96I", update: "VHYT95I" } },
  CY: { legacy: "CY", yard: { inquiry: "VHYT70I", update: "VHYT71I" }, shop: { inquiry: "VHYT60I", update: "VHYT61I" } },
  DP: { legacy: "DPX", yard: { inquiry: "VHYT40I", update: "VHYT41I" }, maintenanceMap: "dublin-update" },
  PB: { legacy: "PBX", yard: { inquiry: "VHYT50I", update: "VHYT51I" }, maintenanceMap: "pittsburg-update" },
  BS: { legacy: "SVB", yard: { inquiry: "VHIC01", update: "VHIC02" } },
  MB: { legacy: "MBX", yard: { inquiry: "VHYT80I", update: "VHYT81I" } },
};

/** the pseudo track every old map's PSEUDO TRACK button opens (VHYT..IS-PSEUDO: YRD-SUB-LOC = "T-95") */
export const PSEUDO_TRACK = "T-95";
/** the report the shop maps' Print asks VHYT27I for (VHYT..IS-SHOP: WHICH-REPORT = "SHOPSTAT") */
export const SHOP_REPORT = "SHOPSTAT";
/** what the shop update maps put in the car move's WHICH-GUI (VHYT..IS-INIT-S) */
export const SHOP_MOVE_GUI = "VHYT03I";

/** the old screen a map is: e.g. Hayward shop update -> VHYT10I */
export function mapScreen(yard: YardCode, shop: boolean, mode: MapMode): string {
  const s = MAP_SCREENS[yard];
  if (!s) return "";
  const pair = shop ? s.shop : s.yard;
  return pair ? pair[mode] : "";
}
