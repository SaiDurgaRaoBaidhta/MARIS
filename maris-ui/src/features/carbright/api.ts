/* Car bright: the middleware's clean API (/api/v1/car-bright): programs VHCBR1X, VHCBR1A, WS70A01, VHCBR02. */
import { get, post } from "@/shared/api/client";

export interface Car { car: string; location: string; status: string; trainIncident: string; lastThoroughClean: string }
export interface Cleaning { homeYard: string; option: string; cleaningDate: string; overdueDate: string; count: number; cars: Car[]; message: string | null }
export interface Printer { description: string; vtam: string }
export type Option = "last" | "overdue";

export const homeYards = () => get<string[]>("/car-bright/home-yards");
export const printers = () => get<Printer[]>("/car-bright/printers");
export const listCleaning = (homeYard: string, option: Option) => get<Cleaning>(`/car-bright/cleaning?${new URLSearchParams({ homeYard, option })}`);
export const printCleaning = (homeYard: string, option: Option, printer: string) =>
  post<{ message: string }>("/car-bright/cleaning/print", { homeYard, option, printer });

/* Adding thorough cleanings (old VHCBP1I): programs VHIE01B, VHCBR2C, VHCBP1A */
export interface CleaningDefaults { yards: string[]; yard: string; date: string; time: string; maxCars: number }
export const cleaningDefaults = () => get<CleaningDefaults>("/car-bright/thorough-cleanings/defaults");
export const addCleaning = (yard: string, date: string, time: string, cars: string[]) =>
  post<{ message: string; cars: number }>("/car-bright/thorough-cleanings", { yard, date, time, cars });
