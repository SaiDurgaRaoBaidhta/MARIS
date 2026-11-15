/* Vehicle repair: the middleware's clean API (/api/v1/repair), old screens VHRV01I, VHRV02I, VHRV03I, VHRV04I. */
import { get, post } from "@/shared/api/client";

export type RepairScreen = "VHRV01I" | "VHRV03I" | "VHRV04I";
export interface Code { code: string; description: string }
export interface Options { causes: Code[]; dispositions: Code[]; yards: string[]; systemChangePrompt: string; deletePrompt: string; componentDeletePrompt: string }
export interface PreviousRepair { document: number; date: string; time: string; location: string; narrative: string }
export interface Defaults { location: string; date: string; time: string; employee: string }
export interface IncidentRepairs { incident: string; car: string; status: string; type: string; consist: string; nextDocument: number; repairs: PreviousRepair[]; defaults: Defaults | null }
export interface Narrative { number: number; userGroup: string; date: string; time: string; text: string }
export interface RepairDetails { document: number; cause: string; serialOn: string; serialOff: string; deleted: string; partControl: string; system: string; subSystem: string;
  disposition: string; warranty: string; laborHours: number; employee: string; location: string; date: string; time: string; narratives: Narrative[] }
export interface RepairInput { cause: string; system: string; subSystem: string; disposition: string; date: string; time: string; location: string; employee: string;
  warranty: string; laborHours: number | null; partControl: string; serialOn: string; serialOff: string; narrative: string }
export interface Result { done: boolean; message: string | null; partControl: string | null }
export interface Component { document: number; component: string; type: string; fault: string; disposition: string; serialOn: string; serialOff: string; date: string }
export interface ComponentInput { component: string; type: string; fault: string; disposition: string; serialOn: string; serialOff: string }
export interface Components { title: string; components: Component[]; nextDocument: number; message: string | null }

const e = encodeURIComponent;
const at = (incident: string, doc?: number) => `/repair/incidents/${e(incident)}` + (doc === undefined ? "" : `/repairs/${doc}`);

export const options = () => get<Options>("/repair/options");
export const incident = (screen: RepairScreen, incd: string) => get<IncidentRepairs>(`${at(incd)}?screen=${screen}`);
export const repair = (incd: string, doc: number) => get<RepairDetails>(at(incd, doc));
export const addRepair = (incd: string, r: RepairInput) => post<Result>(`${at(incd)}/repairs`, r);
export const closeIncident = (incd: string) => post<Result>(`${at(incd)}/close`);
export const changeRepair = (incd: string, doc: number, r: RepairInput) => post<Result>(at(incd, doc), r);
export const deleteRepair = (incd: string, doc: number) => post<Result>(`${at(incd, doc)}/delete`);
export const addEventRepair = (incd: string, r: { date: string; time: string; narrative: string }) => post<Result>(`/repair/events/${e(incd)}/repairs`, r);
export const closeEvent = (incd: string) => post<Result>(`/repair/events/${e(incd)}/close`);
const comps = (incd: string, doc: number) => `${at(incd, doc)}/components`;
export const components = (incd: string, doc: number, maintenance: boolean) => get<Components>(`${comps(incd, doc)}?maintenance=${maintenance}`);
export const addComponent = (incd: string, doc: number, c: ComponentInput, maintenance: boolean) => post<Components>(`${comps(incd, doc)}?maintenance=${maintenance}`, c);
export const updateComponent = (incd: string, doc: number, n: number, c: ComponentInput, maintenance: boolean) =>
  post<Components>(`${comps(incd, doc)}/${n}?maintenance=${maintenance}`, c);
export const deleteComponent = (incd: string, doc: number, n: number) => post<Components>(`${comps(incd, doc)}/${n}/delete?maintenance=true`);
