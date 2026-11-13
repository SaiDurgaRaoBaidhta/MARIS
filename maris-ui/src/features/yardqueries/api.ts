/* Yard tracking queries: the middleware's clean API (/api/v1/yard-queries). Shop status: MARIS programs VHYT25A, VHYT30A. */
import { get } from "@/shared/api/client";

export interface ShopCar {
  track: string; position: number; car: string; leftEnd: string; rightEnd: string; status: string; incident: boolean;
  dateIn: string; timeIn: string; reason: string; elapsedDays?: number; elapsedHours?: number;
}
export interface OutCar extends ShopCar { origin: "YARD" | "SHOP"; elapsedMinutes: number }
export interface ShopStatus {
  yard: string; shop: string; runTime: string; cars: ShopCar[]; averageDays?: number; averageHours?: number; totalCars: number; grandTotal: number;
  outOfYard: { transferTrack?: string; cars: OutCar[]; averageDays?: number; averageHours?: number; totalCars: number };
}

export const fetchShopStatus = (yard: string) => get<ShopStatus>(`/yard-queries/shop-status/${encodeURIComponent(yard)}`);
