/* Car wash updates: the middleware's clean API (/api/v1/car-wash): a consist (VHCWA2I) and single cars (VHCWA3I). */
import { ApiError, get, post } from "@/shared/api/client";

export interface ConsistStart { washDate: string; yards: string[]; message: string }
export interface Consist { consist: string; date: string; dashNo: number; cars: string[]; found: boolean; alreadyWashed: boolean; message: string }
export interface ConsistWash { consist: string; date: string; time: string; yard: string }
export interface CarsStart { washDate: string; consist: string; yards: string[]; message: string }
export interface TimeCheck { time: string; valid: boolean; message: string }
export interface CarsWash { yard: string; date: string; time: string; consist: string; cars: string[] }

export const consistStart = () => get<ConsistStart>("/car-wash/consist-washes/start");
export const findConsist = (consist: string, date: string) => get<Consist>(`/car-wash/consist-washes/consist?${new URLSearchParams({ consist, date })}`);
export const washConsist = (w: ConsistWash) => post<{ message: string; consist: Consist }>("/car-wash/consist-washes", w);
export const carsStart = () => get<CarsStart>("/car-wash/car-washes/start");
export const checkTime = (time: string) => get<TimeCheck>(`/car-wash/car-washes/time?${new URLSearchParams({ time })}`);
export const washCars = (w: CarsWash) => post<{ message: string; cars: string[] }>("/car-wash/car-washes", w);

/** the boxes (1-10) the middleware marked invalid, when the error carries them */
export const invalidBoxes = (e: unknown): number[] =>
  e instanceof ApiError ? ((e as unknown as { problem?: { invalidBoxes?: number[] } }).problem?.invalidBoxes ?? []) : [];
