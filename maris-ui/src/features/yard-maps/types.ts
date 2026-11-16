/* Yard and shop maps in React (replacing the iframe viewer public/legacy/maris-map.html).
 * Shared contract between the drawing (2D), the 3D view and the map actions. */

/** a yard (UI code): HY, DY, BS, RY, CY, DP, PB, MB */
export type YardCode = string;

/** one drawing item of a map, as the old viewer's MAPS / SHOPS specs hold them: ["cells", track, ...], ["text", ...], ... */
export type DrawItem = [string, ...unknown[]];

/** one yard or shop drawing (old viewer MAPS[code] / SHOPS[code]): window ids, the drawing box, scale, cell size, items */
export interface MapSpec {
  id: string;            // inquiry screen title, e.g. "VHYT07I - Hayward Yard Map - Inquiry"
  uid?: string;          // update screen title
  x1: number; y1: number; x2: number; y2: number;
  s?: number;            // scale
  ch?: number; cw?: number; lblH?: number;
  buttons?: unknown;
  items: DrawItem[];
  [k: string]: unknown;
}

/** a car as the map shows it */
export interface MapCar {
  car: string;           // bare car number ("4271"): the key everywhere
  text: string;          // what the old map shows in its box: orientation mark and "$" ("v4271", "3254}", "$4425V"); else the number
  home: string;          // home yard (UI code)
  status: string;        // MARIS vehicle status (GO, NO GO, PM, ...)
  consist?: string;
  moveReason?: string;
  track: string;         // MARIS track (sub-location) the car is on, as the backend names it
  position: number;      // 1-based position on that MARIS track
}

/** one map, ready to draw: every drawn box filled from MARIS data, and the cars no box exists for */
export interface MapModel {
  yard: YardCode;
  shop: boolean;
  spec: MapSpec;
  /** drawn track id (as the drawing's items name it) -> its boxes, index = box index in the drawing */
  cells: Record<string, (MapCar | null)[]>;
  /** cars of this yard that no drawn box (on the yard map or the shop map) can show, e.g. T-95, Millbrae TM4-TM7: never dropped */
  others: MapCar[];
}
