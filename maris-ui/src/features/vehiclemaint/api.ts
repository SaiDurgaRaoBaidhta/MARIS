/* Vehicle maintenance: the middleware's clean API (/api/v1/vehicle-maintenance): programs VHNARX, WS70A01, VHVM13B, VHVM12A, VHVM13A. */
import { get, post } from "@/shared/api/client";

export interface Printer { description: string; vtam: string }
export interface ReportOptions { printers: Printer[]; carTypes: string[]; homeYards: string[]; eventTypes: string[] }
export interface Narrative { incident: string; lines: string[] }
export interface EventsPrint {
  dateFrom: string; dateTo: string; car: string; carType: string; homeYard: string; eventType: string; eventSubType: string;
  eventStatus: string; location: string; trackingRefNo: string; sortOrder: string; printer: string;
}
export interface CarEventsPrint { carType: string; date: string; eventType: string; eventStatus: string; trackingRefNo: string; comments: string; homeYard: string; printer: string }

export const narrative = (incident: string) => get<Narrative>(`/vehicle-maintenance/incidents/${encodeURIComponent(incident)}/narrative`);
export const reportOptions = () => get<ReportOptions>("/vehicle-maintenance/reports/options");
export const printEvents = (r: EventsPrint) => post<{ message: string }>("/vehicle-maintenance/reports/events/print", r);
export const printCarEvents = (r: CarEventsPrint) => post<{ message: string }>("/vehicle-maintenance/reports/car-events/print", r);
