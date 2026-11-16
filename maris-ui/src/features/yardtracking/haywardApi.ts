/* The Hayward yard update map (old VHYT05I): /api/v1/yard-tracking/hayward-yard. */
import { get, post } from "@/shared/api/client";

export interface HaywardCar { track: string; position: number; code: string; car: string; consist: string; orientation: string; label: string;
  status: string; condition: "go" | "go-pm-due" | "usable" | "not-usable"; moveReason: string; homeYard: string }
export interface HaywardMap { yard: string; pseudoTrack: string; maintenance: boolean; tracks: { track: string; spots: number[] }[];
  trackButtons: { label: string; code: string }[]; cars: HaywardCar[] }
export interface Click { spot?: string; track?: string; pseudo?: boolean }
export interface Selection { open?: string | null; params: Record<string, string | number> }

export const fetchHaywardMap = () => get<HaywardMap>("/yard-tracking/hayward-yard");
export const selectOnHaywardMap = (clicks: Click[]) => post<Selection>("/yard-tracking/hayward-yard/selection", { clicks });
