/* Wayside: the middleware's clean API (/api/v1/wayside), which calls MARIS program WS17A05. */
import { get, post } from "@/shared/api/client";

export interface StockNumber { dept: string; stockNo: string; description: string }
export interface StockSearch { count: number; stockNumbers: StockNumber[]; message: string | null }

export const searchStock = (description: string, dept: string) =>
  get<StockSearch>(`/wayside/stock-numbers?${new URLSearchParams({ description, dept })}`);

/* Narratives of an incident, a repair or a PM (old WS13I): WS13A08/WSX2A01/WSX3A, WS13A06/WS13A02/WS13A01, WS13A07/WS13A04/WS13A05 */
export interface NarrativeContext { incident: string; repairDoc: string; pmDept: string; pmEquipment: string; pmLocation: string; pmCycle: string; pmScheduled: string }
export interface Narrative { text: string; date: string; time: string; userGroup: string; row: number; maxRows: number; hasPrev: boolean; hasNext: boolean }
const ctx = (c: NarrativeContext) => Object.fromEntries(Object.entries(c).filter(([, v]) => v !== ""));
export const latestNarrative = (c: NarrativeContext) => get<Narrative>(`/wayside/narratives/latest?${new URLSearchParams(ctx(c))}`);
export const stepNarrative = (c: NarrativeContext, direction: "prev" | "next", row: number, maxRows: number) =>
  get<Narrative>(`/wayside/narratives/step?${new URLSearchParams({ ...ctx(c), direction, row: String(row), maxRows: String(maxRows) })}`);
export const addNarrative = (c: NarrativeContext, text: string) =>
  post<Narrative>("/wayside/narratives", { ...ctx(c), repairDoc: c.repairDoc ? Number(c.repairDoc) : undefined, text });
