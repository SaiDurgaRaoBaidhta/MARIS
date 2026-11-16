/* Make / break overrides (old VHYT34I) on the clean API. Every call opens the old window, which writes (VHYT34B), so all are POSTs. */
import { post } from "@/shared/api/client";

export interface SingleTrain { train: string; arrival: string; breakScheduled: string; breakCancelled: string; makeScheduled: string; makeCancelled: string }
export interface OverridesState {
  yard: string; revenueDate: string; schedule: string; message: string; times: Record<string, string[]>;
  statuses: Record<string, string>; trains: string[]; singleTrains: SingleTrain[];
}
export interface TrainTimes { train: string; arrivals: string[]; scheduledFor: string }
export interface SingleOverride { train: string; arrival: string; breakCancel: boolean; makeCancel: boolean; breakScheduled: boolean; makeScheduled: boolean }

const base = "/yard-queries/make-break-overrides";
export const openOverrides = (yard: string, date = "") => post<OverridesState>(`${base}/window`, { yard, date });
export const saveOverrides = (yard: string, date: string, times: Record<string, string>, boxes: Record<string, boolean>) =>
  post<OverridesState>(base, { yard, date, overrides: { times, boxes } });
export const trainTimes = (yard: string, date: string, train: string, arrival = "") => post<TrainTimes>(`${base}/train`, { yard, date, train, arrival });
export const saveSingle = (yard: string, date: string, override: SingleOverride) => post<OverridesState>(`${base}/single-train`, { yard, date, override });
