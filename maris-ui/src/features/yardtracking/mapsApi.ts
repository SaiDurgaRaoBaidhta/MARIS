/* Yard tracking maps: the middleware's clean API (/api/v1/yard-tracking/maps). Old screens VHYT40I / VHYT41I
 * (Dublin-Pleasanton, MARIS program VHYT40A), VHYT50I / VHYT51I (Pittsburg-Bay Point, VHYT50A), VHYT04I (Hayward shop,
 * VHYT04A); the update maps' Maint button asks VH01A02. */
import { get } from "@/shared/api/client";
import type { YardCar, YardTrack } from "@/features/yardreports/api";

/** a track with no spots listed is a one-spot track: its last row shows, whatever its position (Hayward shop 2N-1 ...) */
export interface TrackingMap {
  map: string; screen: string; yard: string | null; pseudoTrack: string | null;
  /** the Hayward shop's Print: what it passes to VHYT27I */
  shopReport: { whichReport: string; yard: string } | null;
  tracks: YardTrack[]; cars: YardCar[];
}

export const fetchTrackingMap = (map: string) => get<TrackingMap>(`/yard-tracking/maps/${encodeURIComponent(map)}`);
export const maintenanceAccess = (map: string) => get<{ allowed: boolean }>(`/yard-tracking/maps/${encodeURIComponent(map)}/maintenance-access`);
