/* Preventive maintenance: the middleware's clean API (/api/v1/preventive-maintenance). */
import { get, post } from "@/shared/api/client";

export const REPORTS = ["hours-by-yard", "mtbi", "by-pm-type", "hours-descending", "hours-to-next"] as const;
export type ReportId = typeof REPORTS[number];
export interface Printer { description: string; vtam: string }
export interface Options { revenueDate: string; fromDate: string; reports: string[]; homeYards: string[]; locations: string[]; pmTypes: string[]; printers: Printer[] }
export interface PmCar {
  car: string; status?: string; location?: string; homeYard?: string; hoursSinceLastPm?: number; nextPm?: string; trainIncident?: string;
  mtbi?: number; mtbiHours?: number; mtbiIncidents?: number; hoursToNextPm?: number; lastPm?: string; hoursAtLastPm?: number; lastPmLocation?: string;
  cumulativeHours?: number; cumulativeMinutes?: number; due?: string;
}
export interface Mtbi { shop: string; hours: number; incidents: number; cars: number; average: number }
export interface PmReport { report: string; name: string; revenueDate: string; fromDate: string; count: number; cars: PmCar[]; mtbi?: Mtbi[] }
export interface Criteria { homeYard: string; location: string; pmType: string; carFrom: string; carTo: string; hoursFrom: string; hoursTo: string }

const query = (c: Criteria) => new URLSearchParams(Object.entries(c).filter(([, v]) => v !== ""));
export const fetchOptions = () => get<Options>("/preventive-maintenance/options");
export const fetchReport = (r: ReportId, c: Criteria) => get<PmReport>(`/preventive-maintenance/reports/${r}?${query(c)}`);
export const printReport = (r: ReportId, c: Criteria, printer: string) => post<{ message: string }>(`/preventive-maintenance/reports/${r}/print`,
  { ...Object.fromEntries(query(c)), hoursFrom: c.hoursFrom ? Number(c.hoursFrom) : undefined, hoursTo: c.hoursTo ? Number(c.hoursTo) : undefined, printer });

/* PM scheduling (old VHPMS01I) */
export interface ScheduleOption { option: number; label: string; button: string }
export interface ScheduleOptions { options: ScheduleOption[]; pmTypes: string[] }
export interface PmDescription { pmType: string; description: string }
export interface Scheduled { message: string; incident: string }
export const fetchScheduleOptions = () => get<ScheduleOptions>("/preventive-maintenance/schedule/options");
export const fetchPmDescription = (car: string, pmType: string) =>
  get<PmDescription>(`/preventive-maintenance/schedule/description?${new URLSearchParams({ car, pmType })}`);
export const savePmSchedule = (car: string, pmType: string, option: number) =>
  post<Scheduled>("/preventive-maintenance/schedule", { car, pmType, option });
