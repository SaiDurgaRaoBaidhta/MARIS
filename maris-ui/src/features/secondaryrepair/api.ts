/* Secondary repair (read-only screens): the middleware's clean API (/api/v1/secondary-repair). */
import { get, post } from "@/shared/api/client";

export interface Code { code: string; description: string }
export interface StockJob { job: string; doc: number; serialNo: string; quantityIn: number; from: string; workType: string; dateIn: string; carOrEquipment: string; area: string; status: string; dateOut: string }
export interface StockInquiry { stockNo: string; description: string; count: number; totalQuantityIn: number; jobs: StockJob[]; note: string | null; message: string | null }
export interface SubJob { doc: number; stockNo: string; serialNo: string; faultCode: string; quantityIn: number; quantityOut: number; quantityScrapped: number; dateIn: string; dateOut: string; area: string; workType: string; status: string }
export interface Job { job: string; status: string; car: string; dept: string; equipmentType: string; equipmentLocation: string; equipmentId: string; incident: string; printControlNo: string; openDocs: number; count: number; subJobs: SubJob[] }
export interface Repair { doc: number; component: string; faultCode: string; count: number; serialNo: string; stockNo: string }
export interface Labor { doc: number; date: string; employee: string; position: string; hours: number; shift: string; workFunction: string; workOrder: string }
export interface SubJobDetails { job: string; doc: number; narrative: string[]; repairNarrative: string[]; repairs: Repair[]; labor: Labor[] }
export interface Printer { description: string; vtam: string }

const q = (o: Record<string, string>) => new URLSearchParams(Object.entries(o).filter(([, v]) => v !== ""));
export const stockOptions = () => get<{ statuses: Code[]; longTermStorage: string[] }>("/secondary-repair/stock-inquiry/options");
export const stockInquiry = (c: { stockNo: string; serialNo: string; status: string; longTermStorage: string }) => get<StockInquiry>(`/secondary-repair/stock-inquiry?${q(c)}`);
export const openJob = (job: string) => get<Job>(`/secondary-repair/jobs/${encodeURIComponent(job)}`);
export const jobHistory = (job: string) => get<Job>(`/secondary-repair/jobs/${encodeURIComponent(job)}/history`);
export const subJob = (job: string, doc: number) => get<SubJobDetails>(`/secondary-repair/jobs/${encodeURIComponent(job)}/sub-jobs/${doc}`);
export const reprintOptions = () => get<{ printers: Printer[]; defaultPrinter: string | null }>("/secondary-repair/reprint/options");
export const reprint = (job: string, doc: number, printer: string) => post<{ message: string }>(`/secondary-repair/jobs/${encodeURIComponent(job)}/reprint`, { doc, printer });
