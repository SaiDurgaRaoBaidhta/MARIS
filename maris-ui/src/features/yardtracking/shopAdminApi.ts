/* Shop administration maps and the shop car move: the middleware's clean API (/api/v1/yard-tracking/shop-admin, /shop-move).
 * Old screens VHYT10I (Hayward shop, MARIS programs VHYT08C + VHYT04A), VHYT95I (Richmond shop, VHYT95A) and the car move
 * they open, VHYT03I (VHYT03A/B/D/E/F, VHYT03J; Update writes VHYT03G or VHYT03H), with its reason check VHYT42I. */
import { get, post } from "@/shared/api/client";

export interface ShopCar {
  track: string; position: number; car: string; consist: string; orientation: string; label: string; status: string;
  moveReason: string; homeYard: string; colour: string | null;
}
export interface ShopSpot { track: string; position: number; clickable: boolean }
export interface ShopMap {
  map: string; screen: string; yard: string; report: { whichReport: string; yard: string };
  spots: ShopSpot[]; cars: ShopCar[];
}
/** open = the window the clicks open (VHYT03I) with the old map's pass values; message = the old TOO MANY CLICKS text */
export interface ShopSelection { open: string | null; message: string; params: Record<string, string | number> }

export const fetchShopMap = (map: string) => get<ShopMap>(`/yard-tracking/shop-admin/${encodeURIComponent(map)}`);
export const selectSpots = (map: string, spots: string[]) =>
  post<ShopSelection>(`/yard-tracking/shop-admin/${encodeURIComponent(map)}/selection`, { spots });

export interface MoveContext {
  carNo: string; yrdTrkgYrd: string; yrdSubLoc: string; newYrdLoc: string; carNo2: string; posnNoFrom: number; posnNoTo: number; whichGui: string;
}
export interface Choice { code: string; text: string }
export interface MoveView {
  carNo: string; orientation: string; status: string; location: string; yard: string; currentReason: string;
  reasons: Choice[]; selectedReason: string | null; locations: Choice[]; selectedLocation: string | null;
  compressAsked: boolean; insertLabel: string[]; message: string;
}
export interface ReasonCheck { title: string; text: string; keepButton: string; changeButton: string; reason: string | null }
export interface MoveOutcome { message: string; question: ReasonCheck | null }

export const openMove = (context: MoveContext, carNo?: string) => post<MoveView>("/yard-tracking/shop-move", { context, carNo });
export const updateMove = (context: MoveContext, choice: { carNo?: string; reason: string; location: string; compress: string; reasonUnchanged: boolean }) =>
  post<MoveOutcome>("/yard-tracking/shop-move/update", { context, ...choice });
