/* Sub job out the door (old VHSR04I): the middleware's clean API (/api/v1/secondary-repair/out-the-door). */
import { get, post } from "@/shared/api/client";

export type Disposition = "LONG_TERM_STORAGE" | "OUT_TO_VENDOR" | "DEFERRED" | "AWAITING_PARTS";
export interface OutOptions { doc: number; faultCodes: string[]; workTypes: { code: string; description: string }[] }
export interface SubJob { jobNo: string; doc: number; status: string; canGoOut: boolean; message: string; dateIn: string; qtyIn: number;
  dateOut: string | null; qtyOut: number; qtyScrapped: number }
export interface OutTheDoor { jobNo: string; doc: number; dateOut: string; qtyOut: number; qtyScrapped: number; faultCode: string; workType: string;
  inspector: string; disposition: Disposition | null; vendorId: string }

const base = "/secondary-repair/out-the-door";
export const outOptions = () => get<OutOptions>(`${base}/options`);
export const findSubJob = (jobNo: string, doc: number) => get<SubJob>(`${base}/sub-job?${new URLSearchParams({ jobNo, doc: String(doc) })}`);
export const goOut = (o: OutTheDoor) => post<{ message: string; status: string }>(base, o);
