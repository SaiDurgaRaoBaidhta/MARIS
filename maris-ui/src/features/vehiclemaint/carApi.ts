/* Vehicle maintenance updates owned with the vehicle table: add a car (old VHVM02I: VHVM1AA, VHVM1A3), change a car (VHVM03I:
   VHVM1A5, VHVM1A4) and an incident's status history (VHVM06I: VH01A01, VHYT15A, VHVM6A1/2/3/4, VHVM6A6). */
import { get, post } from "@/shared/api/client";

export type CarFields = Record<string, string | number | null>;
export interface Camera { code: string; description: string }
export interface CarOptions {
  carTypes: string[]; addCarTypes: string[]; statuses: string[]; previousStatuses: string[]; homeYards: string[]; yesNo: string[];
  batteryLocations: string[]; lastPmLocations: string[]; whereIsCar: string[]; locations: string[]; cameras: Camera[];
  orientations: Record<string, string[]>; schedulePms: string[]; configStatuses: string[]; configTypes: string[]; addDefaults: CarFields;
}
export interface Car { fields: CarFields; editable: string[] }
export interface HistoryOptions { yards: string[]; statuses: string[]; moveReasons: string[] }
export interface Entry {
  incident: string; timestamp: string; date: string; time: string; previousStatus: string; newStatus: string; yard: string; subLocation: string;
  pseudoConsist: string; moveReason: string; homeYard: string; function: string; action: string; userGroup: string;
  dateLastUpdated: string; terminalLastUpdated: string; timeLastUpdated: string;
}
export interface EntryChange {
  timestamp: string; date: string; time: string; previousStatus: string; newStatus: string; yard: string; subLocation: string;
  pseudoConsist: string; moveReason: string; homeYard: string;
}
export interface HistoryResult { message: string; entries: Entry[] }

const base = "/vehicle-maintenance";
const enc = encodeURIComponent;
export const carOptions = () => get<CarOptions>(`${base}/cars/options`);
export const addCar = (car: CarFields) => post<{ message: string }>(`${base}/cars`, car);
export const findCar = (car: string) => get<Car>(`${base}/cars/${enc(car)}`);
export const changeCar = (car: string, changes: CarFields) => post<{ message: string }>(`${base}/cars/${enc(car)}/changes`, changes);
export const historyOptions = () => get<HistoryOptions>(`${base}/status-history/options`);
export const subLocations = (yard: string) => get<string[]>(`${base}/status-history/sub-locations?yard=${enc(yard)}`);
export const entries = (incident: string) => get<Entry[]>(`${base}/status-history/${enc(incident)}`);
export const addEntry = (incident: string, c: EntryChange) => post<HistoryResult>(`${base}/status-history/${enc(incident)}`, c);
export const changeEntry = (incident: string, c: EntryChange) => post<HistoryResult>(`${base}/status-history/${enc(incident)}/changes`, c);
export const deleteEntry = (incident: string, timestamp: string) => post<HistoryResult>(`${base}/status-history/${enc(incident)}/delete`, { timestamp });
