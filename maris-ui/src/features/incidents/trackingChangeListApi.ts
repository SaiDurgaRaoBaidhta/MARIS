/* Vehicle tracking change incident (old VHCI02I): /api/v1/incidents/tracking-change options, incidents, changes. */
import { get, post } from "@/shared/api/client";

export interface TrackingChangeOptions {
  date: string; time: string; actions: string[]; statuses: string[]; defaultStatus: string; canChangeLocation: boolean; maintenanceFrom: string;
}
export interface IncidentRow { incident: string; eventType: string; eventSubType: string; status: string; type: string; date: string; symptom: string; change: string }
export interface Listing { message: string; currentStatus: string; heading: string; rows: IncidentRow[]; changeEnabled: boolean }
export interface ListRequest { action: number; status: string; car: string }
export interface ChangeRequest extends ListRequest {
  changes: Record<string, string>; date: string; time: string; narrative: string; pmLocation: string; pmTeam: string; selected?: string;
}
export interface ChangeResult { message: string; currentStatus: string; listing: Listing }

export const trackingChangeOptions = () => get<TrackingChangeOptions>("/incidents/tracking-change/options");
export const listIncidents = (r: ListRequest) => post<Listing>("/incidents/tracking-change/incidents", r);
export const applyChange = (r: ChangeRequest) => post<ChangeResult>("/incidents/tracking-change/changes", r);
