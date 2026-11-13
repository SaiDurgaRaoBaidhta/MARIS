/* ICS reports: the middleware's clean API (/api/v1/ics), which calls MARIS programs WS70A01, VHIC30A-C, VHIC31A, VHIC30E. */
import { get, post } from "@/shared/api/client";

export interface Code { code: string; description: string }
export interface Printer { description: string; vtam: string; name: string; server: string }
export interface IcsOptions { revenueDate: string; printers: Printer[]; yards: Code[]; locations: Code[]; messageCodes: Code[]; warnings: string[] }
export interface IcsMessage {
  received: string; type: string; consist: string; consistTo: string; size: number; location: string; icsLocation: string;
  direction: string; inZone: string; created: string; icsCreated: string; sequence: number; processed: string; function: string;
  completed: string; revenueDate: string; cars: string[];
}
export interface IcsReport { revenueDate: string; count: number; maxRowsReached: boolean; messages: IcsMessage[] }

/** what the report is for; ALL = every yard / location / message code */
export interface IcsCriteria { revenueDate: string; order: "time" | "consist"; groupBy: "yard" | "location"; yard: string; location: string; messageCode: string }

export const fetchOptions = () => get<IcsOptions>("/ics/options");
export const fetchMessages = (c: IcsCriteria) => get<IcsReport>(`/ics/messages?${new URLSearchParams({ ...c })}`);
export const printReport = (c: IcsCriteria, printer: string) => post<{ message: string }>("/ics/messages/print", { ...c, printer });
