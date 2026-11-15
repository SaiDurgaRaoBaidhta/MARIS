/* Vehicle tracking change incident (old VHCI03I "never go"): /api/v1/incidents/tracking-change. */
import { get, post } from "@/shared/api/client";

export interface NeverGoOptions { date: string; time: string; statuses: string[]; canChangeLocation: boolean }
export interface NeverGoRequest { car: string; status: string; date: string; time: string; narrative: string; yard?: string }
export interface Maintenance { from: string; car: string; incident: string; status: string; eventType: string; dateInitialized: string }
export interface NeverGoResult { message: string; incident: string; status: string; statusDescription: string; maintenance: Maintenance }

export const neverGoOptions = () => get<NeverGoOptions>("/incidents/tracking-change/never-go/options");
export const neverGo = (r: NeverGoRequest) => post<NeverGoResult>("/incidents/tracking-change/never-go", r);
