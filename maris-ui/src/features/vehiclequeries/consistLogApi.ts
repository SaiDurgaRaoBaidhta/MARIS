/* Vehicle queries: the consist log (old VHCL01I/VHCL02I) and the daily vehicle status (old VHDVS1I..VHDVS4I). */
import { get, post } from "@/shared/api/client";

export interface LogPrinter { name: string; vtamName: string }
export interface ConsistLogOptions { revenueDate: string; printers: LogPrinter[] }
export interface ConsistCar { car: string; shown: string; homeYard: string; warning: boolean }
export interface Consist {
  consist: string; origin: string; outOfServiceLocation: string; timeIn: string; timeOut: string; runTime: string; swap: string; split: string;
  washed: string; carCount: number; warning: boolean; cars: ConsistCar[];
}
export interface ConsistLog {
  criteria: { from: string; to: string; revenueDate: string; revenueDateDb2: string };
  consists: Consist[]; consistCount: number; carCount: number; pages: number; message: string;
}

export const consistLogOptions = () => get<ConsistLogOptions>("/vehicle-queries/consist-log/options");
export const consistLog = (from: string, to: string, revenueDate: string) =>
  get<ConsistLog>(`/vehicle-queries/consist-log?${new URLSearchParams({ from, to, revenueDate })}`);
export const printConsistLog = (from: string, to: string, revenueDate: string, printer: string) =>
  post<{ message: string }>("/vehicle-queries/consist-log/print", { from, to, revenueDate, printer });

export interface StatusColumn { field: string; heading: string }
export interface StatusTable { screen: string; title: string; columns: StatusColumn[]; rows: Record<string, string | number>[] }
export interface DailyStatus {
  revenueDate: string; validAsOf: string; tables: StatusTable[];
  gains: { concord: number; dalyCity: number; hayward: number; richmond: number; total: number };
}

export const dailyStatus = (revenueDate?: string) =>
  get<DailyStatus>(`/vehicle-queries/daily-status${revenueDate ? `?${new URLSearchParams({ revenueDate })}` : ""}`);
export const dailyStatusPrinters = () => get<LogPrinter[]>("/vehicle-queries/daily-status/printers");
export const printDailyStatus = (revenueDate: string, printer: string) =>
  post<{ message: string }>("/vehicle-queries/daily-status/print", { revenueDate, printer });
