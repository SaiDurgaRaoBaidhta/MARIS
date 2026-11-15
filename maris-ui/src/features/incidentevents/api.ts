/* Incident events: the middleware's clean API (/api/v1/incident-events). */
import { get, post } from "@/shared/api/client";

export interface Incident { revenueDate: string; incident: string; car: string; status: string; symptom: string; time: string; speed: string; direction: string; location: string; consist: string; narrative: string }
export interface IncidentLog { from: string; to: string; count: number; incidents: Incident[]; message: string | null }
export interface Printer { description: string; vtam: string }
export interface ConsistOfCar { car: string; consist: string; con: string; message: string | null }
export interface ConsistCars { consist: string; cars: string[]; message: string | null }

export const revenueDate = () => get<{ revenueDate: string }>("/incident-events/revenue-date");
export const listIncidents = (from: string, to: string) => get<IncidentLog>(`/incident-events/incidents?${new URLSearchParams({ from, to })}`);
export const printers = () => get<Printer[]>("/incident-events/printers");
export const printIncidents = (from: string, to: string, printer: string) => post<{ message: string }>("/incident-events/incidents/print", { from, to, printer });
export const consistOfCar = (car: string) => get<ConsistOfCar>(`/incident-events/cars/${encodeURIComponent(car)}/consist`);
export const consistCars = (consist: string) => get<ConsistCars>(`/incident-events/consists/${encodeURIComponent(consist)}/cars`);

/* Vehicle desk work sheet (old VHIE03I / VHIE04I) and symptom history (VHIE07I): /api/v1/incident-events/worksheet */
const W = "/incident-events/worksheet";
export interface Code { code: string; description: string }
export interface WorksheetOptions { revenueDate: string; today: string; symptoms: Code[]; technicians: Code[]; actions: Code[]; trackingCodes: Code[]; createAllowed: boolean; message: string | null }
export interface WorksheetEvent { number: number; eventId: string; hasIncident: boolean; consist: string; location: string; direction: string; speed: string; time: string;
  symptom: string; car: string; technician: string; narrative: string; trackingCode: string; actionCode: string; actionText: string; incident: string;
  dateInServiceRev: string; dateInService: string; timeInService: string; binDash: number; userGroup: string }
export interface EventLog { date: string; events: WorksheetEvent[]; message: string | null }
export interface CorrectiveAction { hasCorrectiveAction: boolean; correctiveAction: string; narrative: string }
export interface EventInput { car: string; consist: string; location: string; direction: string; speed: string; time: string; symptom: string; narrative: string;
  technician: string; trackingCode: string; actionCode: string; incident: string }
export interface HistoryRow { type: string; car: string; symptom: string; dateInService: string; timeInService: string; consist: string; incident: string; cars: string[]; narrative: string }
export interface SymptomHistory { symptom: string; days: number; from: string; to: string; events: number; incidents: number; rows: HistoryRow[]; message: string | null }

export const worksheetOptions = () => get<WorksheetOptions>(`${W}/options`);
export const worksheetEvents = (date: string) => get<EventLog>(`${W}/events?${new URLSearchParams({ date })}`);
export const correctiveAction = (date: string, n: number) => get<CorrectiveAction>(`${W}/events/${date}/${n}/corrective-action`);
export const addEvent = (e: EventInput) => post<{ message: string | null }>(`${W}/events`, e);
export const updateEvent = (date: string, n: number, e: EventInput) => post<{ message: string | null }>(`${W}/events/${date}/${n}`, e);
export const deleteEvent = (date: string, n: number, e: EventInput) => post<{ message: string | null }>(`${W}/events/${date}/${n}/delete`, e);
export const fileIncident = (date: string, n: number, e: EventInput) => post<{ message: string | null }>(`${W}/events/${date}/${n}/incident`, e);
export const symptomHistory = (symptom: string, days?: string) =>
  get<SymptomHistory>(`${W}/symptom-history/${encodeURIComponent(symptom)}${days ? `?${new URLSearchParams({ days })}` : ""}`);
