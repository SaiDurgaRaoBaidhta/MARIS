/* Vehicle queries: consist orientation (VHCO01I), D and C sheet reprint (VHDC01I), SCRAM (VHSCM1I), car status change (VHCHG1I). */
import { get, post } from "@/shared/api/client";

export interface OrientationPrinter { description: string; vtam: string }
export interface OrientationOptions { title: string; fromDate: string; toDate: string; all: string; printers: OrientationPrinter[] }
export interface OrientationPrint { from: string; to: string; fromDate: string; toDate: string; printer: string }
export const orientationOptions = () => get<OrientationOptions>("/vehicle-queries/consist-orientation/options");
export const printOrientation = (r: OrientationPrint) => post<{ message: string }>("/vehicle-queries/consist-orientation/print", r);

export interface DncPrinter { location: string; vtam: string }
export interface DncOptions { title: string; yes: string; no: string; printers: DncPrinter[] }
export interface DncPrint { blankForm: string; car: string; incident: string; printer: string }
export const dncOptions = () => get<DncOptions>("/vehicle-queries/dnc-sheet/options");
export const printDnc = (r: DncPrint) => post<{ message: string }>("/vehicle-queries/dnc-sheet/print", r);

export interface ScramOptions {
  title: string; day: string; time: string; days: string[]; times: string[]; homeYards: string[]; homeYardHeading: string; groups: string[]; columns: string[];
}
export interface ScramRow { homeYard: string; cells: number[] }
export interface ScramTotal { scheduled: number; actual: number; difference: number }
export interface ScramYard { homeYard: string; rows: ScramRow[]; totals: ScramTotal[]; message: string | null }
export interface ScramReport { day: string; time: string; yards: ScramYard[] }
export const scramOptions = () => get<ScramOptions>("/vehicle-queries/scram/options");
export const scramReport = (day: string, time: string) => get<ScramReport>(`/vehicle-queries/scram?${new URLSearchParams({ day, time })}`);

export interface StatusChangeOptions { title: string; statuses: string[]; message: string; question: string }
export interface CarStatus { car: string; status: string; previousStatus: string; message: string }
export const statusChangeOptions = () => get<StatusChangeOptions>("/vehicle-queries/car-status-change/options");
export const carStatus = (car: string) => get<CarStatus>(`/vehicle-queries/car-status-change/cars/${encodeURIComponent(car)}`);
export const changeCarStatus = (car: string, status: string) =>
  post<{ message: string }>(`/vehicle-queries/car-status-change/cars/${encodeURIComponent(car)}`, { status });
