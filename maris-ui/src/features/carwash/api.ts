/* Car wash: the middleware's clean API (/api/v1/car-wash): programs VHCWR1A, VHCWR2A. */
import { get } from "@/shared/api/client";

export interface Wash { car: string; date: string; time: string }
export interface Washes { yard: string; from: string; to: string; count: number; maxRowsReached: boolean; note: string | null; washes: Wash[]; message: string | null }
export interface OverdueCar { car: string; lastWashDate: string; lastWashTime: string; location: string; daysOverdue: number; totalWashes: number }
export interface Overdue { homeYard: string; count: number; maxRowsReached: boolean; note: string | null; cars: OverdueCar[]; message: string | null }

export const listWashes = (yard: string, from: string, to: string) => get<Washes>(`/car-wash/washes?${new URLSearchParams({ yard, from, to })}`);
export const listOverdue = (homeYard: string) => get<Overdue>(`/car-wash/overdue?${new URLSearchParams({ homeYard })}`);
