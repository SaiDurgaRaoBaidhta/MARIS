/* Configuration tracking on the clean API (/api/v1/config-tracking): a car's configuration (old VHCF01I) and its
 * report printer window (VFCF05I). */
import { get, post } from "@/shared/api/client";

export interface CarOptions { actions: string[]; trucks: string[]; locations: string[] }
export interface CarConfiguration { message: string; homeYard: string; values: Record<string, string>; highlighted: Record<string, string>; updatable: boolean; notices: string[] }
export interface CarChange { action: string; truck: string; values: Record<string, string>; highlight: string[]; employee: string; location: string }

export const fetchCarOptions = () => get<CarOptions>("/config-tracking/cars/options");
export const fetchCar = (car: string, action: string, truck: string) =>
  get<CarConfiguration>(`/config-tracking/cars/${encodeURIComponent(car)}?${new URLSearchParams({ action, truck })}`);
export const updateCar = (car: string, c: CarChange) => post<CarConfiguration>(`/config-tracking/cars/${encodeURIComponent(car)}`, c);
export const fetchPrinters = () => get<{ printers: { description: string; vtamName: string }[]; warnings: string[] }>("/config-tracking/printers");
export const printCar = (car: string, printer: string) => post<void>(`/config-tracking/cars/${encodeURIComponent(car)}/print`, { printer });
