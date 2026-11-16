/* Yard tracking on the clean API: shop activity (old VHYT31I) and its prints (VHYT23I, VHYT22I), pseudo correction
 * (VHYT52I), force clear (VHYT54I). */
import { get, post } from "@/shared/api/client";

export interface Yard { code: string; name: string }
export interface ActivityOptions { yards: Yard[]; yard: string; yardName: string; date: string; message: string }
export interface ActivityCar {
  car: string; availabilityDate: string; availabilityTime: string; status: string; track: string; position: number;
  inShopDate: string; inShopTime: string; outShopDate: string; outShopTime: string; moveReason: string;
}
export interface ActivityTotals {
  arrivals: number; departures: number; releases: number; remaining: number; production: number; productionGainLoss: number;
  availabilityGainLoss: number; available0400: number; remaining3: number;
}
export interface Activity { yard: string; date: string; cars: ActivityCar[]; totals: ActivityTotals; message: string; printable: boolean }
export interface Printer { index: number; name: string; description: string }
export interface Train { consist: string; cars: string[]; status: string; noCars: string }
export interface Trains { yard: string; yardChoosable: boolean; trains: Train[]; message: string }

const q = (params: Record<string, string>) => {
  const s = new URLSearchParams(Object.entries(params).filter(([, v]) => v !== ""));
  return s.toString() ? "?" + s.toString() : "";
};

export const activityOptions = (yard = "") => get<ActivityOptions>(`/yard-tracking/shop-activity/options${q({ yard })}`);
export const activityList = (yard: string, date: string) => post<Activity>("/yard-tracking/shop-activity/list", { yard, date });
export const printers = () => get<Printer[]>("/yard-tracking/printers");
export const printActivity = (yard: string, date: string, printer: number) =>
  post<{ printer: string }>("/yard-tracking/shop-activity/print", { yard, date, printer });
export const printCarHistory = (car: string, printer: number) => post<{ printer: string }>("/yard-tracking/car-history/print", { car, printer });
export const pseudoTrains = (yard: string, whichGui: string, location = "") =>
  get<Trains>(`/yard-tracking/pseudo-correction${q({ yard, whichGui, location })}`);
export const retrieveTrain = (yard: string, whichGui: string, location: string, row: number) =>
  post<{ message: string; trains: Train[] }>("/yard-tracking/pseudo-correction/retrieve", { yard, whichGui, location, row });
export const forceClear = (train: string, location: string) => post<{ message: string }>("/yard-tracking/force-clear", { train, location });
