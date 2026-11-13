/* Yard tracking queries: the middleware's clean API (/api/v1/yard-queries). Shop status: MARIS programs VHYT25A, VHYT30A. */
import { get, post } from "@/shared/api/client";

export interface ShopCar {
  track: string; position: number; car: string; leftEnd: string; rightEnd: string; status: string; incident: boolean;
  dateIn: string; timeIn: string; reason: string; elapsedDays?: number; elapsedHours?: number;
}
export interface OutCar extends ShopCar { origin: "YARD" | "SHOP"; elapsedMinutes: number }
export interface ShopStatus {
  yard: string; shop: string; runTime: string; cars: ShopCar[]; averageDays?: number; averageHours?: number; totalCars: number; grandTotal: number;
  outOfYard: { transferTrack?: string; cars: OutCar[]; averageDays?: number; averageHours?: number; totalCars: number };
}

export const fetchShopStatus = (yard: string) => get<ShopStatus>(`/yard-queries/shop-status/${encodeURIComponent(yard)}`);

/* history reports (old VHYT29I: VHYT29A; VHYT33I: VHYT33A); dates yyyy-MM-dd */
export interface Move { car: string; track: string; position: number; status: string; orientation: string; dateMoved: string; timeMoved: string;
  previousTrack: string; previousPosition: number; reason: string; reasonCode: string }
export interface StatusChange { car: string; dateChanged: string; timeChanged: string; track: string; newStatus: string; previousStatus: string;
  reason: string; reasonCode: string }
export interface History<R> { yard: string; from: string; to: string; maxRowsReached: boolean; rows: R[] }
export interface HistoryCriteria { yard: string; from: string; to: string; fromCar?: string; toCar?: string }

export const fetchCarHistory = (c: HistoryCriteria) =>
  get<History<Move>>(`/yard-queries/car-history?${new URLSearchParams({ yard: c.yard, from: c.from, to: c.to, fromCar: c.fromCar ?? "", toCar: c.toCar ?? "" })}`);
export const fetchStatusHistory = (c: HistoryCriteria) =>
  get<History<StatusChange>>(`/yard-queries/status-history?${new URLSearchParams({ yard: c.yard, from: c.from, to: c.to })}`);

/* the reports criteria window (old VHYT27I): options (WS70A01, VHYT27B, VHRT04A) and Hard Copy (VHYT27A / F / C / D / E / G) */
export interface ReportOptions { printers: { description: string; vtam: string }[]; yards: { code: string; description: string }[]; from: string; to: string; warnings: string[] }
export interface PrintRequest { report: string; yard: string; printer: string; from: string; to: string; fromCar: string; toCar: string }
export const fetchReportOptions = () => get<ReportOptions>("/yard-queries/report-options");
export const printReport = (r: PrintRequest) => post<{ message: string }>("/yard-queries/reports/print", r);
