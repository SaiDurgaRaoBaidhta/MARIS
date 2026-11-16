/* Vehicle maintenance updates: the middleware's clean API (/api/v1/vehicle-maintenance): car hours after a test (old VHVM08I:
   VHVM1A5, VHVM8A1), consist status (VHVM10I: VHVM10A, VHVM10C), consist history (VHVM19I: VHYT34A, VHVM19A, VHVM19B). */
import { get, post } from "@/shared/api/client";

export interface CarHours { car: string; location: string; cumulativeHours: number; hoursToNextPm: number; hoursFromLastPm: number; whereIsCar: string }
export interface Hours { cumulativeHours: number; hoursToNextPm: number; hoursFromLastPm: number }
export interface Column { heading: string; field: string }
export type Row = Record<string, string | number | null>;
export interface ConsistStatusOptions { statuses: string[]; columns: Column[] }
export interface ConsistHistoryOptions extends ConsistStatusOptions { revDate: string }
export interface Rows { rows: Row[]; message: string }
export interface StatusChange { conNo: string; status: string; dateInsrvIcs: string; timeInsrvIcs: string; noCarsInd: string; dateOutsrv: string; dateInsrvRev: string }
export interface HistoryChange {
  conNo: string; dashNo: number; dateInsrv: string; timeInsrv: string; dateInsrvRev: string; status: string; dateInsrvIcs: string; timeInsrvIcs: string; noCarsInd: string;
}

const q = (p: Record<string, string>) => new URLSearchParams(p).toString();
export const carHours = (car: string) => get<CarHours>(`/vehicle-maintenance/car-hours/${encodeURIComponent(car)}`);
export const addTestHours = (car: string, testHours: string) =>
  post<{ message: string; hours: Hours }>(`/vehicle-maintenance/car-hours/${encodeURIComponent(car)}/test-hours`, { testHours });
export const consistStatusOptions = () => get<ConsistStatusOptions>("/vehicle-maintenance/consist-status/options");
export const consistStatus = (from: string, to: string) => get<Rows>(`/vehicle-maintenance/consist-status?${q({ from, to })}`);
export const updateConsistStatus = (c: StatusChange) => post<{ message: string }>("/vehicle-maintenance/consist-status/update", c);
export const consistHistoryOptions = () => get<ConsistHistoryOptions>("/vehicle-maintenance/consist-history/options");
export const consistHistory = (from: string, to: string, revDate: string) => get<Rows>(`/vehicle-maintenance/consist-history?${q({ from, to, revDate })}`);
export const updateConsistHistory = (c: HistoryChange) => post<{ message: string }>("/vehicle-maintenance/consist-history/update", c);
