/* Vehicle incidents of one car: the middleware's clean API (/api/v1/incidents, old VHINC08I / VHINC09I / VHINC10I). */
import { get, post } from "@/shared/api/client";

export interface Duration { code: string; description: string }
export interface CreateOptions {
  type: string; title: string; date: string; durations: Duration[]; duration: string; narrativeDate: string; narrativeTime: string; userGroup: string;
}
export interface CreateRequest {
  type: string; car: string; symptom: string; duration: string; date: string; time: string; location: string; narrative: string;
  narrativeDate: string; narrativeTime: string;
}
export interface Narrative { number: number; userGroup: string; date: string; time: string; text: string }
export interface Update { date: string; time: string; terminal: string }
export interface Details {
  incident: string; car: string; consist: string; type: string; status: string; symptom: string; duration: string; date: string; time: string;
  speed: string; direction: string; location: string; deferredPriority: string; cumulativeHours: number; cumulativeMinutes: number;
  lastUpdate: Update; maintenanceUpdate: Update; carsInConsist: string; narratives: Narrative[];
}
export interface Editing { details: boolean; speedAndDirection: boolean; deferredPriority: boolean; narratives: boolean }
export interface MaintainOptions { statuses: { code: string; description: string }[]; durations: string[]; revenueDate: string }

export const createOptions = (type: string) => get<CreateOptions>(`/incidents/vehicle-incident/options?${new URLSearchParams({ type })}`);
export const fileIncident = (r: CreateRequest) => post<{ incident: string; message: string; warnings: string[] }>("/incidents/vehicle-incident", r);
export const narratives = (incident: string) => get<Narrative[]>(`/incidents/vehicle-incident/${encodeURIComponent(incident)}/narratives`);

export const updatable = (incident: string) => get<{ incident: Details; editing: Editing }>(`/incidents/vehicle-update/${encodeURIComponent(incident)}`);
export const changeIncident = (incident: string, c: { symptom: string; speed: string; direction: string; location: string; narrative: string; narrativeIndex: number }) =>
  post<{ message: string }>(`/incidents/vehicle-update/${encodeURIComponent(incident)}`, c);

export const maintainOptions = () => get<MaintainOptions>("/incidents/vehicle-maintenance/options");
export const maintained = (incident: string) => get<Details>(`/incidents/vehicle-maintenance/${encodeURIComponent(incident)}`);
export interface MaintainChange {
  status: string; type: string; symptom: string; duration: string; speed: string; direction: string; location: string; deferredPriority: string;
  cumulativeHours: number; cumulativeMinutes: number; narrative: string; narrativeIndex: number;
}
export const maintainIncident = (incident: string, c: MaintainChange) => post<{ message: string }>(`/incidents/vehicle-maintenance/${encodeURIComponent(incident)}`, c);
