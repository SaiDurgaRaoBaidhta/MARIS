/* Incidents: the middleware's clean API (/api/v1/incidents) for train incidents (old VHINC01I), revenue incidents of a car
 * (VHINC06I) and event / shop / miscellaneous incidents of a car (VHINC03I). */
import { get, post } from "@/shared/api/client";

export interface Defaults { date: string; time: string }
export interface Result { message: string; incidentNo: string }
export interface TrainCreate { consistNo: string; symptom: string; date: string; time: string; speed: string; direction: string; location: string; narrative: string }
export interface NarrativeRow { narrNo: number; userGrp: string; dateNarr: string; timeNarr: string; narr: string }
export interface TrainIncident {
  incidentNo: string; consistNo: string; dashNo: string; symptom: string; discovery: string; date: string; time: string; speed: string;
  direction: string; location: string; narratives: NarrativeRow[];
}
export interface TrainChange { symptom: string; speed: string; direction: string; location: string; narrativeIndex: number }
export interface RevenueCreate { carNo: string; symptom: string; date: string; time: string; speed: string; direction: string; location: string; narrative: string }
export interface Discovery { code: string; description: string }
export interface CarOptions { type: string; title: string; date: string; discoveries: Discovery[]; fixedDiscovery: string }
export interface CarCreate { type: string; carNo: string; symptom: string; discovery: string; date: string; time: string; location: string; narrative: string }

export const trainDefaults = () => get<Defaults>("/incidents/train/defaults");
export const createTrain = (r: TrainCreate) => post<Result>("/incidents/train", r);
export const trainIncident = (no: string) => get<TrainIncident>(`/incidents/train/${encodeURIComponent(no)}`);
export const changeTrain = (no: string, c: TrainChange) => post<Result>(`/incidents/train/${encodeURIComponent(no)}`, c);
export const revenueDefaults = () => get<Defaults>("/incidents/revenue/defaults");
export const createRevenue = (r: RevenueCreate) => post<Result>("/incidents/revenue", r);
export const carOptions = (type: string) => get<CarOptions>(`/incidents/car-incident/options?${new URLSearchParams({ type })}`);
export const symptomNarrative = (symptom: string) => get<{ narrative: string }>(`/incidents/car-incident/symptoms/${encodeURIComponent(symptom)}/narrative`);
export const createCar = (r: CarCreate) => post<Result>("/incidents/car-incident", r);
