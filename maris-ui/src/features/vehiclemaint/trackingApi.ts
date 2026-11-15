/* Vehicle parts status / mod tracking (old VHVM11I): /api/v1/vehicle-maintenance/tracking-events. */
import { get, post } from "@/shared/api/client";

export interface EventChoice { type: string; subType: string; label: string }
export interface TrackingOptions {
  runDate: string; dateFrom: string; dateTo: string; searchEvents: EventChoice[]; entryEvents: EventChoice[]; statuses: string[]; entryStatuses: string[];
  locations: string[]; carTypes: string[]; homeYards: string[]; batteryLocations: string[]; sortOrders: string[]; defaultSortOrder: string; queryOnly: boolean;
  questions: Record<string, string>;
}
export interface TrackingEvent {
  car: string; eventType: string; eventSubType: string; status: string; dateInitialized: string; timeInitialized: string; trackingRefNo: string;
  dateClosed: string; timeClosed: string; vehicleStatus: string; dateLastUpdated: string; homeYard: string; incident: string; comment: string;
  location: string; locationCompleted: string; active: string;
}
export interface Criteria {
  dateFrom: string; dateTo: string; car: string; carType: string; homeYard: string; eventType: string; eventSubType: string; status: string;
  location: string; trackingRefNo: string; sortOrder: string;
}
export interface Listing { events: TrackingEvent[]; message: string }
export interface Selection { carType: string; batteryReplaced: string; status: string; location: string; entryEventType: string; entryEventSubType: string; canWorkAssign: boolean; battery: boolean }
export interface Pass { from: string; car: string; incident: string; status: string; eventType: string; dateInitialized: string }
export interface Form { car: string; eventType: string; eventSubType: string; status: string; dateInitialized: string; trackingRefNo: string; dateClosed: string; incident: string; comment: string; location: string }
export interface Linked { events: TrackingEvent[]; message: string; form: (Form & Partial<TrackingEvent>) | null; editing: boolean }
export interface Entry { car: string; status: string; eventType: string; eventSubType: string; trackingRefNo: string; dateInitialized: string; dateClosed: string; incident: string; comment: string }
export interface Change {
  event: TrackingEvent; status?: string; dateInitialized?: string; trackingRefNo?: string; dateClosed?: string; car?: string; incident?: string; comment?: string;
  location?: string; entryEventType?: string; entryEventSubType?: string; answer?: boolean;
}
export interface Result { message: string | null; question: string | null; incident: string | null }

const BASE = "/vehicle-maintenance/tracking-events";
export const trackingOptions = (from?: string) => get<TrackingOptions>(`${BASE}/options${from ? `?from=${encodeURIComponent(from)}` : ""}`);
export const searchEvents = (c: Criteria) => post<Listing>(`${BASE}/search`, c);
export const linkedEvents = (p: Pass) => post<Linked>(`${BASE}/linked`, p);
export const selectEvent = (e: TrackingEvent) => post<Selection>(`${BASE}/selection`, e);
export const checkCar = (car: string) => get<{ message: string }>(`${BASE}/cars/${encodeURIComponent(car)}/check`);
export const addEvent = (e: Entry) => post<Result>(BASE, e);
export const updateEvent = (c: Change) => post<Result>(`${BASE}/update`, c);
export const closeEvent = (c: Change) => post<Result>(`${BASE}/close`, c);
export const removeEvent = (c: Change) => post<Result>(`${BASE}/remove`, c);
export const workAssign = (c: Change) => post<Result>(`${BASE}/work-assign`, c);
