/* One track's cars and the windows it opens, on the middleware's clean API (/api/v1/yard-tracking):
 * old VHYT08I track (VHYT08A/B/C/D/F/H, VH01A02; Move Car writes with VHYT08G), VHYT02I print (WS70A01, VHYT02B; VHYT02A),
 * VHYT06I move reason (VHYT06A/B; VHYT06C/D/E write), VHYT14I fix order (VHYT14A; VHYT14B/C/D/E/F write). */
import { get, post } from "@/shared/api/client";

export interface Position { slot: number; position: number; carShown: string; car: string; pseudoTrain: string; status: string; previousStatus: string;
  homeYard: string; usable: boolean; alert: boolean }
export interface Track { yard: string; yardDescription: string; subLocation: string; subLocationDescription: string; pseudoCaption: string; page: number;
  previous: boolean; next: boolean; positions: Position[]; locations: { code: string; description: string }[]; userMayChange: boolean; canMoveCar: boolean;
  canMoveYard: boolean; canFixOrder: boolean; message: string }
export type TrackAction = "change-reason" | "fix-order" | "move-car" | "move-yard";
export interface ActionResult { message: string; opens?: string; with: Record<string, string | number>; track: Track }

const qs = (p: Record<string, string | number | undefined>) =>
  new URLSearchParams(Object.entries(p).filter(([, v]) => v !== undefined && v !== "").map(([k, v]) => [k, String(v)])).toString();

export const fetchTrack = (yard: string, subLocation: string, page = 1) => get<Track>(`/yard-tracking/track?${qs({ yard, subLocation, page })}`);
export const trackAction = (yard: string, subLocation: string, page: number, slots: number[], action: TrackAction, toLocation?: string) =>
  post<ActionResult>("/yard-tracking/track/actions", { yard, subLocation, page, slots, action, toLocation });

export interface ReportOptions { printers: { description: string; vtam: string }[]; yards: string[]; yard: string; subLocations: string[] }
export const fetchReportOptions = (yard: string) => get<ReportOptions>(`/yard-tracking/track-report/options?${qs({ yard })}`);
export const printTrackReport = (yard: string, subLocation: string, printer: string) =>
  post<{ message: string }>("/yard-tracking/track-report/print", { yard, subLocation, printer });

export interface ReasonCar { car: string; position: number; orientation: string; status: string; yard: string; subLocation: string; reasonCode: string;
  reasonDescription: string }
export interface ReasonView { reasons: { code: string; description: string }[]; car?: ReasonCar; message: string }
export const fetchReason = (yard: string, subLocation: string, car: string, position: number) =>
  get<ReasonView>(`/yard-tracking/move-reasons/car?${qs({ yard, subLocation, car, position })}`);
export const changeReason = (yard: string, subLocation: string, car: string, position: number, reasonCode: string) =>
  post<{ message: string; updated: boolean; car: ReasonCar }>("/yard-tracking/move-reasons/change", { yard, subLocation, car, position, reasonCode });

export interface FixCar { index: number; car: string; position: number; pseudoTrain: string; partial: boolean }
export interface FixView { cars: FixCar[]; selected: number; message: string }
export interface Fixed { message: string; question?: string; moved: boolean; track: FixView }
export const fetchFixOrder = (yard: string, subLocation: string, position: number) =>
  get<FixView>(`/yard-tracking/fix-order?${qs({ yard, subLocation, position })}`);
export const fixOrder = (f: { yard: string; subLocation: string; position: number; carIndex: number; relativeIndex: number; after: boolean; confirm: boolean }) =>
  post<Fixed>("/yard-tracking/fix-order", f);
