/* Consist maintenance: the middleware's clean API (/api/v1/consist). Old screens VHMT01I make train, VHCM01I combine,
 * VHCT01I clear train, VHCC01I change consist, VHTS01I train swap, VHSC01I split, VHCY01I move a car. */
import { get, post } from "@/shared/api/client";

export interface Question { name: string; text: string }
export interface ConsistResult {
  message: string; notices: string[]; question: Question | null; fields: Record<string, string>;
  cars: Record<string, string>[]; swaps: Record<string, string>[];
}
export interface CarLocation { car: string; currentLocation: string; track: string; position: string; movable: boolean; message: string }
export interface CarMove { message: string; notices: string[]; moved: boolean }

export const consistPost = (path: string, body: object) => post<ConsistResult>(`/consist/${path}`, body);
export const clearTrainStart = () => get<ConsistResult>("/consist/clear-train");
export const carLocation = (car: string) => get<CarLocation>(`/consist/car-location/${encodeURIComponent(car)}`);
export const moveCar = (car: string, location: string) => post<CarMove>(`/consist/car-location/${encodeURIComponent(car)}/move`, { location });
