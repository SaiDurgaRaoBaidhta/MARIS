/* Yard reports: the middleware's clean API (/api/v1/yard-reports). Maps call MARIS programs VHIC01A, VHYT80A, VHYTX5A,
 * VHYT70A, VHYT90A, VHYTX0A and VHYT60A; the system-wide map calls VHSW01A. */
import { get } from "@/shared/api/client";

export interface YardTrack { track: string; spots: number[] }
/** condition: the old update maps' colour rule (absent when the map's update screen colours no car) */
export interface YardCar {
  track: string; position: number; car: string; consist: string; orientation: string; label: string; status: string;
  condition?: "go" | "go-pm-due" | "usable" | "not-usable"; moveReason: string; homeYard: string; colour: string; background?: string;
}
export interface YardMap { map: string; yard: string; tracks: YardTrack[]; cars: YardCar[] }
export interface SystemWide { yard: string; locations: { location: string; cars: string[] }[] }

export const fetchMap = (map: string) => get<YardMap>(`/yard-reports/maps/${encodeURIComponent(map)}`);
export const fetchSystemWide = () => get<SystemWide>("/yard-reports/system-wide");
