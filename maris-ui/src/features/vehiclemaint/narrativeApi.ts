/* The narrative window (old VHNAR01I): /api/v1/vehicle-maintenance/narratives, programs VHXNR1A-8A. */
import { get, post } from "@/shared/api/client";

export interface Narrative {
  incdNo: string; reprDocNo: number; currentRow: number; maxRows: number; userGrp: string; dateNarr: string; timeNarr: string;
  narr: string; ciPart: string; canPrev: boolean; canNext: boolean;
}

/** what the old parent fills in VHNARIW-PASSVALUE, as camelCase query params (see contracts/VHNAR01I.md) */
export const NARRATIVE_PARAMS = ["incdNo", "reprDocNo", "narrNo", "currentRow", "maxRows", "narr", "dateNarr", "timeNarr", "userGrp", "termLastUpd", "storeUserGrp"] as const;

const query = (o: Record<string, string | number | undefined>) =>
  new URLSearchParams(Object.entries(o).filter(([, v]) => v !== undefined && v !== "").map(([k, v]) => [k, String(v)])).toString();

export const opening = (p: URLSearchParams) =>
  get<Narrative>(`/vehicle-maintenance/narratives/opening?${query(Object.fromEntries(["incdNo", "reprDocNo", "currentRow", "maxRows", "userGrp", "dateNarr", "timeNarr", "narr"].map(k => [k, p.get(k) ?? undefined])))}`);
export const step = (n: Narrative, direction: "prev" | "next") =>
  get<Narrative>(`/vehicle-maintenance/narratives/step?${query({ incdNo: n.incdNo, reprDocNo: n.reprDocNo, currentRow: n.currentRow, maxRows: n.maxRows, direction })}`);
export const addNarrative = (n: Narrative, narrative: string) =>
  post<{ message: string; narrative: Narrative }>("/vehicle-maintenance/narratives", { incdNo: n.incdNo, reprDocNo: n.reprDocNo, maxRows: n.maxRows, narrative });
export const updateNarrative = (n: Narrative, narrative: string) =>
  post<{ message: string }>("/vehicle-maintenance/narratives/update", { incdNo: n.incdNo, reprDocNo: n.reprDocNo, currentRow: n.currentRow, narrative, ciPart: n.ciPart });
