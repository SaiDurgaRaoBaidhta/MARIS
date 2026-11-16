/* Secondary repair job updates: the middleware's clean API (/api/v1/secondary-repair), old screens VHSR01I, VHSR08I,
 * VHSR02I, VHSR09I, VHSR03I. */
import { get, post } from "@/shared/api/client";

export interface Code { code: string; description: string }
export interface Comment { number: number; text: string; userGroup: string; date: string; time: string }
export interface Comments { job: string; doc: number; kind: string; count: number; comment: Comment | null }
export interface InDoorOptions { workTypes: Code[]; repairAreas: Code[]; departments: string[]; workType: string; equipmentIdDepartments: string[] }
export interface SplitSubJob { doc: number; repairArea: string; comment: string }
export interface SplitJob { job: string; status: string; car: string; dept: string; equipmentType: string; equipmentId: string; nextDoc: number; repairArea: string; subJobs: SplitSubJob[] }
export interface Repair { stockNo: string; serialNo: string; component: string; faultCode: string; quantity: number }
export interface RepairDetail { job: string; doc: number; status: string; repairs: Repair[] }
export interface RepairLine { stockNo: string; serialNo: string; component: string; faultCode: string; quantity: number | null }
export interface Labor { date: string; employee: string; hours: number }
export interface LaborDetails { job: string; doc: number; status: string; dateIn: string; dateOut: string; labor: Labor[]; canClose: boolean; note: string | null; comments: Comment[] }
export interface LaborLine { date: string; employee: string; hours: number | null }

const enc = encodeURIComponent;
const q = (o: Record<string, string | number>) => new URLSearchParams(Object.entries(o).map(([k, v]) => [k, String(v)]));

export const commentKinds = () => get<string[]>("/secondary-repair/comments/kinds");
export const listComments = (jobNo: string, doc: number, kind: string) => get<Comments>(`/secondary-repair/comments?${q({ jobNo, doc, kind })}`);
export const comment = (jobNo: string, doc: number, kind: string, n: number) => get<Comment>(`/secondary-repair/comments/${n}?${q({ jobNo, doc, kind })}`);
export const addComment = (jobNo: string, doc: number, kind: string, text: string) => post<{ message: string; comments: Comments }>("/secondary-repair/comments", { jobNo, doc, kind, text });
export const updateComment = (jobNo: string, doc: number, kind: string, n: number, text: string) => post<{ message: string }>(`/secondary-repair/comments/${n}`, { jobNo, doc, kind, text });

export const inDoorOptions = () => get<InDoorOptions>("/secondary-repair/in-door/options");
export const equipmentTypes = (dept: string) => get<Code[]>(`/secondary-repair/departments/${enc(dept)}/equipment-types`);
export const createJob = (j: Record<string, unknown>) => post<{ job: string; message: string; overThreshold: boolean }>("/secondary-repair/jobs", j);

export const splitOptions = () => get<{ workTypes: Code[]; repairAreas: Code[]; workType: string }>("/secondary-repair/job-split/options");
export const splitJob = (job: string) => get<SplitJob>(`/secondary-repair/job-split/${enc(job)}`);
export const createSubJob = (job: string, s: Record<string, unknown>) => post<{ doc: number; message: string; job: SplitJob }>(`/secondary-repair/job-split/${enc(job)}/sub-jobs`, s);

export const repairDetail = (jobNo: string, doc: number) => get<RepairDetail>(`/secondary-repair/repair-detail?${q({ jobNo, doc })}`);
export const addRepairs = (r: { jobNo: string; doc: number; kitNo: string; noRepair: boolean; lines: RepairLine[] }) =>
  post<{ message: string; detail: RepairDetail }>("/secondary-repair/repair-detail", r);

export const laborDetails = (jobNo: string, doc: number) => get<LaborDetails>(`/secondary-repair/labor?${q({ jobNo, doc })}`);
export const addLabor = (r: { jobNo: string; doc: number; lines: LaborLine[]; comment: string }) => post<{ message: string; details: LaborDetails }>("/secondary-repair/labor", r);
export const closeSubJob = (jobNo: string, doc: number) => post<{ message: string }>("/secondary-repair/labor/close", { jobNo, doc });
