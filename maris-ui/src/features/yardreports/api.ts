/* Yard reports: the middleware's clean API (/api/v1/yard-reports). Maps call MARIS programs VHIC01A, VHYT80A, VHYTX5A,
 * VHYT70A, VHYT90A, VHYTX0A and VHYT60A; the system-wide map calls VHSW01A. */
import { get, post } from "@/shared/api/client";

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

/** VHIC03I: the cars at fromPosition..toPosition of the pseudo train in fromYard go to toYard (VHIC03A, writes) */
export interface MoveRequest { fromYard: string; fromPosition: number; toPosition: number; toYard: string }
export const fetchMoveYards = () => get<string[]>("/yard-reports/pseudo-train-move/yards");
export const movePseudoTrain = (r: MoveRequest) => post<{ moved: boolean; message: string }>("/yard-reports/pseudo-train-move", r);

/* VHYT53I configure pseudo trains: reading (VHYT53D, B, C, E / M) and the writes (VHYT53F / G create, H move a car out, K reset) */
export interface PseudoCar { car: string; leftEnd: string; rightEnd: string; type: string; ato: boolean; incident: boolean; status: string; homeYard?: string; serviceable?: boolean }
export interface PseudoConsist { consist: string; dash: number; display: string; track: string; indicator: string; resettable: boolean; cars: PseudoCar[] }
export interface PseudoConfig { yard: string; tracks: { track: string; description: string }[]; consists: PseudoConsist[]; message?: string; warnings: string[] }
const pc = (yard: string) => `/yard-reports/pseudo-config/${encodeURIComponent(yard)}`;
export const fetchPseudoConfig = (yard: string) => get<PseudoConfig>(pc(yard));
export const fetchPseudoTrackCars = (yard: string, track: string, insert = false) =>
  get<PseudoCar[]>(`${pc(yard)}/cars?${new URLSearchParams(insert ? { track, list: "insert" } : { track })}`);
export const createPseudo = (yard: string, consist: string, partial: boolean, cars: PseudoCar[]) =>
  post<{ done: boolean; message?: string }>(`${pc(yard)}/consists`, {
    consist, partial, cars: cars.map(({ car, leftEnd, rightEnd, type, ato, incident, status }) => ({ car, leftEnd, rightEnd, type, ato, incident, status })),
  });
export const movePseudoCar = (yard: string, car: string, track: string) => post<{ moved: boolean; message: string }>(`${pc(yard)}/car-moves`, { car, track });
export const resetPseudo = (yard: string, c: PseudoConsist) => post<{ done: boolean }>(`${pc(yard)}/resets`, { consist: c.consist, track: c.track, indicator: c.indicator });
