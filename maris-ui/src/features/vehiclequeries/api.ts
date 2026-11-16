/* Vehicle queries: the middleware's clean API (/api/v1/vehicle-queries). */
import { get, post } from "@/shared/api/client";

export interface Yard { code: string; label: string }
export interface Printer { description: string; vtam: string }
export interface ReportOptions {
  report: string; screen: string; title: string; button: string; dates: boolean; fromDate: string; toDate: string;
  yards: Yard[]; yardRequired: boolean; minutes: number | null; printers: Printer[];
}
export interface PrintRequest { fromDate: string; toDate: string; yard: string; minutes: number | null; printer: string }

export const reportOptions = (report: string) => get<ReportOptions>(`/vehicle-queries/batch-reports/${report}/options`);
export const printReport = (report: string, r: PrintRequest) => post<{ message: string }>(`/vehicle-queries/batch-reports/${report}/print`, r);

export interface StatusChange {
  location: string; date: string; time: string; hours: number; minutes: number; statusNew: string; statusPrevious: string; incident: string;
  symptom: string; action: string; dvsImpact: string; lastUpdateDate: string; lastUpdateTime: string; lastUpdateTerminal: string;
  maintUpdateDate: string; maintUpdateTime: string; maintUpdateTerminal: string; function: string;
}
export interface History { car: string; homeYard: string; fromDate: string; toDate: string; changes: StatusChange[]; footer: string | null; message: string | null }

export const printers = () => get<Printer[]>("/vehicle-queries/printers");
export const carStatusHistory = (car: string, from: string, to: string) =>
  get<History>(`/vehicle-queries/car-status-history?${new URLSearchParams({ car, from, to })}`);
export const printCarStatusHistory = (car: string, fromDate: string, toDate: string, printer: string) =>
  post<{ message: string }>("/vehicle-queries/car-status-history/print", { car, fromDate, toDate, printer });

export interface FleetCells { byType: number[]; total: number }
export interface FleetRow { row: number; name: string; yards: Record<string, FleetCells> }
export interface Fleet { yards: string[]; types: string[]; headings: string[]; rows: FleetRow[]; message: string | null }

export const homeYardFleet = () => get<Fleet>("/vehicle-queries/home-yard-fleet");
export const printHomeYardFleet = (printer: string) => post<{ message: string }>("/vehicle-queries/home-yard-fleet/print", { printer });

export interface CarHours { car: string; fromDate: string; toDate: string; actualFrom: string; actualTo: string; hours: number; minutes: number; message?: string | null }

export const carHours = (car: string, from: string, to: string) => get<CarHours>(`/vehicle-queries/car-hours?${new URLSearchParams({ car, from, to })}`);
export const printCarHours = (car: string, fromDate: string, toDate: string, printer: string) =>
  post<{ message: string }>("/vehicle-queries/car-hours/print", { car, fromDate, toDate, printer });

export interface FleetReportCells { byType: number[]; total: number }
export interface FleetReport {
  types: string[]; headings: string[]; rows: { row: number; name: string; cells: FleetReportCells }[];
  goCarsByYard: { yard: string; cells: FleetReportCells }[]; message?: string | null;
}

export const fleetReport = () => get<FleetReport>("/vehicle-queries/fleet-report");
export const printFleetReport = (printer: string) => post<{ message: string }>("/vehicle-queries/fleet-report/print", { printer });

export interface RepeaterOptions { homeYards: string[]; homeYard: string; printers: Printer[] }
export interface RepeaterIncident {
  car: string; location: string; status: string; vehicleStatus: string; incidentIndicator: string; incident: string; date: string; time: string;
  hoursBetween: number; hoursFromLast: number; symptom: string;
}
export interface RepeaterIncidents { homeYard: string; car: string; total: number; incidents: RepeaterIncident[]; footer?: string | null }

export const repeaterOptions = () => get<RepeaterOptions>("/vehicle-queries/repeater-cars/options");
export const repeaterCars = (homeYard: string, car: string) => get<RepeaterIncidents>(`/vehicle-queries/repeater-cars?${new URLSearchParams({ homeYard, car })}`);
export const printRepeaterCars = (homeYard: string, car: string, printer: string) =>
  post<{ message: string }>("/vehicle-queries/repeater-cars/print", { homeYard, car, printer });

export type StatusOption = "base" | "status" | "nogo" | "summary";
export interface StatusCriteria { option: StatusOption; yard: string; status: string; narr: string; carFrom: string; carTo: string }
export interface StatusReportOptions {
  yards: string[]; statuses: { status: string; description: string }[]; narrChoices: string[]; narr: string; carFrom: string; carTo: string;
  titles: string[]; printers: Printer[];
}
/** @deprecated use StatusReportOptions */
export type HomeYardStatusOptions = StatusReportOptions;
export interface Checked { option: string; yard: string; status: string; narr: string; carFrom: string; carTo: string; title: string }
export interface StatusCar { car: string; status: string; location: string; ato: string; hours: number; minutes: number; mtbi: number; homeYard: string; narrativeYard?: string }
export interface StatusIncident { incident: string; symptom: string; location: string; date: string; time: string; status: string; direction: string }
export interface NogoIncident { car: string; incidentCar: string; status: string; location?: string; homeYard?: string; incident: string; incidentLocation: string; date: string; time: string; direction: string }
export interface SummaryCar { car: string; atoMissing: boolean; status: string; location?: string; mtbi?: number; homeYard: string }
export interface TypeSummary { yard: string; header: string; types: string[]; headings: string[]; rows: { row: number; name: string; byType: number[]; total: number }[]; message?: string | null }

/** one of the two status report families: home yard status (VHYS01I..05I) or yard status (VHYS06I..10I) */
export type StatusFamily = "home-yard-status" | "yard-status";
const statusQuery = (c: StatusCriteria) => new URLSearchParams({ ...c } as Record<string, string>).toString();
const sp = (f: StatusFamily) => `/vehicle-queries/${f}`;
export const statusOptions = (f: StatusFamily) => get<StatusReportOptions>(`${sp(f)}/options`);
export const statusCars = (f: StatusFamily, c: StatusCriteria) => get<{ criteria: Checked; cars: StatusCar[]; message?: string | null }>(`${sp(f)}/cars?${statusQuery(c)}`);
export const statusIncidents = (f: StatusFamily, c: StatusCriteria, car: string) =>
  get<{ car: string; incidents: StatusIncident[]; message?: string | null }>(`${sp(f)}/cars/${encodeURIComponent(car)}/incidents?${statusQuery(c)}`);
/** the narrative: home yard status asks with the car's home yard (homeYard), yard status with a yard (yard) */
export const statusNarrative = (f: StatusFamily, car: string, incident: string, yard: string) =>
  get<string[]>(`${sp(f)}/narrative?${new URLSearchParams({ car, incident, [f === "home-yard-status" ? "homeYard" : "yard"]: yard })}`);
export const statusNogo = (f: StatusFamily, c: StatusCriteria) => get<{ criteria: Checked; cars: number; incidents: NogoIncident[]; message?: string | null }>(`${sp(f)}/nogo?${statusQuery(c)}`);
export const statusSummary = (f: StatusFamily, c: StatusCriteria) =>
  get<{ criteria: Checked; header: string; cars: SummaryCar[]; footer?: string | null; message?: string | null }>(`${sp(f)}/summary?${statusQuery(c)}`);
export const statusTypeSummary = (f: StatusFamily, yard: string) => get<TypeSummary>(`${sp(f)}/type-summary?${new URLSearchParams({ yard })}`);
export const printStatus = (f: StatusFamily, c: StatusCriteria, printer: string) => post<{ message: string }>(`${sp(f)}/print`, { ...c, printer });

export interface VehicleCriteria { report?: number; car: string; fromDate: string; toDate: string; sympFrom: string; sympTo: string; deferredItems?: string }
export interface VehicleStatusOptions { reports: string[]; deferredItems: string[]; fromDate: string; toDate: string; printers: Printer[] }
export interface CarDetails {
  car: string; status: string; location: string; homeYard: string; ato: string; mtbi: number; warranty: string; currentPm: string; nextPm: string;
  hoursFromLastPm: number; timesWashed: number; washOverdueDays: number; camera: string; cumulativeHours: number; lastWash: string; softwareMarked: boolean;
}
export interface VehicleIncident {
  incident: string; date: string; time: string; symptom: string; location: string; direction: string; status: string; hours: number; deferredPriority: string;
  consist?: string; dash?: number; dateInServiceRev?: string; dateInService?: string; timeInService?: string; pmTeam?: string;
}
export interface VehicleReport { title: string; car: CarDetails | null; incidents: VehicleIncident[] }
export interface Repair { document: number; system: string; subSystem: string; disposition: string; serialOn: string; serialOff: string; partControl: string }
export interface RepairDetails { narrative: string[]; components: { document: number; component: string; serialOff: string; disposition: string; fault: string }[] }

const vq = (c: VehicleCriteria) => new URLSearchParams(Object.entries(c).filter(([, v]) => v !== undefined).map(([k, v]) => [k, String(v)])).toString();
export const vehicleStatusOptions = () => get<VehicleStatusOptions>("/vehicle-queries/vehicle-status/options");
export const vehicleStatus = (c: VehicleCriteria) => get<VehicleReport>(`/vehicle-queries/vehicle-status?${vq(c)}`);
export const vehicleStatusNarrative = (incident: string) => get<string[]>(`/vehicle-queries/vehicle-status/incidents/${incident}/narrative`);
export const vehicleStatusConsist = (i: VehicleIncident) => get<{ consist: string; cars: string[] }>(`/vehicle-queries/vehicle-status/consist?${new URLSearchParams({
  consist: i.consist ?? "", dash: String(i.dash ?? 0), dateInServiceRev: i.dateInServiceRev ?? "", dateInService: i.dateInService ?? "", timeInService: i.timeInService ?? "" })}`);
export const printVehicleStatus = (c: VehicleCriteria, printer: string) => post<{ message: string }>("/vehicle-queries/vehicle-status/print", { ...c, printer });
export const vehicleHistory = (c: VehicleCriteria) => get<VehicleReport>(`/vehicle-queries/vehicle-history?${vq(c)}`);
export const vehicleHistoryNarrative = (incident: string) => get<string[]>(`/vehicle-queries/vehicle-history/incidents/${incident}/narrative`);
export const vehicleHistoryRepairs = (incident: string) => get<Repair[]>(`/vehicle-queries/vehicle-history/incidents/${incident}/repairs`);
export const vehicleHistoryRepair = (incident: string, doc: number) => get<RepairDetails>(`/vehicle-queries/vehicle-history/incidents/${incident}/repairs/${doc}`);
export const printVehicleHistory = (c: VehicleCriteria, printer: string) => post<{ message: string }>("/vehicle-queries/vehicle-history/print", { ...c, printer });

export interface CarInquiry {
  title: string; incidents: VehicleIncident[]; moveReason: { subLocation: string; code: string; description: string } | null;
  car: { car: string; carShown: string; status: string; location: string; homeYard: string; ato: string; mtbi: number; warranty: string; hoursFromLastPm: number; cumulativeHours: number } | null;
}
export const carInquiry = (car: string) => get<CarInquiry>(`/vehicle-queries/car-inquiry/${encodeURIComponent(car)}`);
export const carInquiryIncident = (incident: string) => get<{ narrative: string[]; consist: string; cars: string[] }>(`/vehicle-queries/car-inquiry/incidents/${incident}`);
export const carInquiryRepairs = (incident: string) => get<Repair[]>(`/vehicle-queries/car-inquiry/incidents/${incident}/repairs`);
export const carInquiryRepair = (incident: string, doc: number) => get<RepairDetails>(`/vehicle-queries/car-inquiry/incidents/${incident}/repairs/${doc}`);
