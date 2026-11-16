/* Car moves on a track (old VHYT11I), a car's orientation (VHYT13I) and the yard car list hard copy (VHYT24I): the
 * middleware's clean API under /api/v1/yard-tracking. */
import { get, post } from "@/shared/api/client";

export interface MovePosition {
  slot: number; position: number; carShown: string; car: string; pseudoTrain: string; status: string; previousStatus: string;
  homeYard: string; color: string; statusColor: string; enabled: boolean;
}
export interface MoveTrack {
  yard: string; yardDescription: string; subLocation: string; subLocationDescription: string; toSubLocation: string; pseudoCaption: string;
  page: number; previous: boolean; next: boolean; positions: MovePosition[]; moveTo: { code: string; description: string }[];
  canPseudoTrain: boolean; canMoveYard: boolean; message: string;
}
export type MoveAction = "move" | "pseudo-train" | "reverse" | "fix-order" | "orientation" | "move-yard" | "print";
export interface MoveResult { message: string; question: string | null; opens: string | null; with: Record<string, unknown>; track: MoveTrack }
export interface Orientation {
  yard: string; subLocation: string; cars: string[]; car: string; orientations: string[]; current: string; canChange: boolean; message: string;
}
export interface PrintOptions { yard: string; printers: { location: string; name: string }[] }

const query = (o: Record<string, string | number | undefined>) =>
  new URLSearchParams(Object.entries(o).filter(([, v]) => v !== undefined && v !== "").map(([k, v]) => [k, String(v)])).toString();

export const fetchMoveTrack = (yard: string, subLocation: string, toSubLocation?: string, page?: number) =>
  get<MoveTrack>(`/yard-tracking/car-moves?${query({ yard, subLocation, toSubLocation, page })}`);
export const moveAction = (yard: string, subLocation: string, toSubLocation: string, page: number, slots: number[], action: MoveAction, confirm?: boolean) =>
  post<MoveResult>("/yard-tracking/car-moves/actions", { yard, subLocation, toSubLocation, page, slots, action, confirm });
export const fetchOrientation = (yard: string, subLocation: string, carNo: string) =>
  get<Orientation>(`/yard-tracking/orientation?${query({ yard, subLocation, carNo })}`);
export const changeOrientation = (yard: string, subLocation: string, carNo: string, orientation: string) =>
  post<Orientation>("/yard-tracking/orientation", { yard, subLocation, carNo, orientation });
export const fetchYardPrint = (yard: string) => get<PrintOptions>(`/yard-tracking/yard-print?${query({ yard })}`);
export const printYard = (yard: string, printer: string) => post<{ yard: string }>("/yard-tracking/yard-print", { yard, printer });
