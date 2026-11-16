/* The yard location (old VHYT26I) and reason out of service (old VHYT32I) reports: /api/v1/yard-queries. Each table comes with
 * the old screen's own column headings; the rows are the old cells. */
import { get } from "@/shared/api/client";

export interface ReportTable { headings: string[]; rows: string[][] }

export interface YardLocation {
  title: string; yardName: string; runDate: string; runTime: string; totalCaption: string; totalCars: string; testTracksNote?: string;
  cars: ReportTable; reasons: ReportTable; homeYards: ReportTable; exchangeTitle: string; exchange: ReportTable;
  homeYardsButton: string; reasonsButton: string;
}

export interface OutOfService {
  heading: string; shop: string; from: string; to: string; runTime: string; daysCaption: string; totalDays?: string; timeCaption: string;
  totalTime?: string; detailGroups: string[]; detail: ReportTable; summaryNote?: string; summary?: ReportTable;
}

const q = (p: Record<string, string>) => new URLSearchParams(Object.entries(p).filter(([, v]) => v !== "")).toString();

export const fetchYardLocation = (yard: string) => get<YardLocation>(`/yard-queries/yard-location?${q({ yard })}`);

/** dates yyyy-MM-dd; summary = the out of service summary report (VHYT27I report 7), else the detail report (6) */
export const fetchOutOfService = (yard: string, from: string, to: string, summary: boolean) =>
  get<OutOfService>(`/yard-queries/out-of-service?${q({ yard, from, to, summary: String(summary) })}`);
