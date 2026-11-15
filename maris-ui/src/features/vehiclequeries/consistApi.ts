/* Consist tracking (old VHST01I .. VHST05I): the middleware's clean API (/api/v1/vehicle-queries/consist-tracking). */
import { get, post } from "@/shared/api/client";

const BASE = "/vehicle-queries/consist-tracking";

export interface ConsistPrinter { description: string; vtam: string }
export interface ConsistOptions { reports: string[]; printers: ConsistPrinter[] }
export interface TrainCar { car: string; leftMark: string; rightMark: string; homeYard: string; status: string; alert: boolean; usable: boolean; warning: boolean }
export interface Train {
  consist: string; origin: string; dateInService: string; timeInService: string; mark: string; orderLocation: string; orientation: string; warning: boolean;
  cars: TrainCar[];
}
export interface Trains { from: string; to: string; trains: Train[] }
export interface DetailCar { car: string; homeYard: string; pm: string; washDays: number; clean: string }
export interface DetailTrain { consist: string; origin: string; dateInService: string; timeInService: string; cars: DetailCar[] }
export interface Details { from: string; to: string; trains: DetailTrain[] }
export interface SymptomCar { car: string; leftMark: string; rightMark: string; software: boolean; symptomCount: number; homeYard: string; alert: boolean; usable: boolean }
export interface SymptomTrain { consist: string; origin: string; dateInService: string; timeInService: string; mark: string; cars: SymptomCar[] }
export interface Symptoms { consist: string; symptom: string; dateFrom: string; dateTo: string; trains: SymptomTrain[] }

export const consistOptions = () => get<ConsistOptions>(`${BASE}/options`);
export const statusTrains = (from: string, to: string) => get<Trains>(`${BASE}/status-trains?${new URLSearchParams({ from, to })}`);
export const carDetails = (from: string, to: string) => get<Details>(`${BASE}/car-details?${new URLSearchParams({ from, to })}`);
export const printConsists = (report: number, from: string, to: string, printer: string) =>
  post<{ message: string }>(`${BASE}/print`, { report, from, to, printer });
export const consistSymptoms = (consist: string, symptom: string) => get<Symptoms>(`${BASE}/symptoms?${new URLSearchParams({ consist, symptom })}`);
export const printConsistSymptoms = (consist: string, symptom: string, printer: string) =>
  post<{ message: string }>(`${BASE}/symptoms/print`, { consist, symptom, printer });
