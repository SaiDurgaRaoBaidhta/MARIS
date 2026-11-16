/* Table maintenance 2: MRS messages received, the middleware's clean API (/api/v1/tables-2). Old screens VHCCS04I (list),
 * VHCCS05I (detail), VHCCS06I (cars), VHCCS07I (errors), VHCCS18I (hard copy). */
import { get, post } from "@/shared/api/client";

export interface Scanner { id: string; scannerId: string; location: string; direction: string }
export interface Options { yards: string[]; tranCodes: string[]; locations: string[]; scanners: Scanner[]; processedCodes: string[];
  fromDate: string; fromTime: string; toTime: string; warnings: string[] }
export interface Criteria { fromDate: string; fromTime: string; toTime: string; tranCode: string; conNo: string; yard: string; location: string;
  scanner: string; processed: string }
export type Message = Record<string, string | number>;
export interface Listing { messages: Message[]; message: string; compareFrom: string; compareTo: string; threshold: number; toBeProcessed: number }
export interface Saved { ok: boolean; message: string }
export interface Car { timestamp: string; carNo: string; carOrder: number; conNo: string; dashNo: number; dateInService: string; orientation: string;
  orientationDirection: string }
export interface MessageError { timestamp: string; count: number; dateLastUpdated?: string; fieldName: string; function: string; description: string;
  type: string; terminal?: string; timeLastUpdated?: string }
export interface PrintOptions { printers: { name: string; vtam: string }[]; tranCodes: string[]; yards: string[]; locations: string[]; scanners: string[];
  processedCodes: string[]; sorts: string[]; warnings: string[] }
export interface PrintRequest { printer: string; fromDate: string; fromTime: string; toTime: string; tranCode: string; conNo: string; yard: string;
  location: string; scanner: string; sort: number }

const window = (l: { ts?: string; from?: string; to?: string }) =>
  new URLSearchParams({ timestamp: l.ts ?? "", from: l.from ?? "", to: l.to ?? "" }).toString();

export const fetchOptions = () => get<Options>("/tables-2/messages/options");
export const listMessages = (c: Criteria) => post<Listing>("/tables-2/messages/list", c);
export const saveMessage = (m: Message) => post<Saved>("/tables-2/messages/update", m);
export const fetchPrintOptions = () => get<PrintOptions>("/tables-2/messages/print-options");
export const printMessages = (p: PrintRequest) => post<{ submitted: boolean }>("/tables-2/messages/print", p);
export const fetchCars = (w: { ts?: string; from?: string; to?: string }) => get<{ cars: Car[]; orientations: string[] }>(`/tables-2/messages/cars?${window(w)}`);
export const saveCar = (c: Car) => post<Saved>("/tables-2/messages/cars/update", c);
export const deleteCar = (c: Car) => post<Saved>("/tables-2/messages/cars/delete", c);
export const fetchErrors = (w: { ts?: string; from?: string; to?: string }) => get<MessageError[]>(`/tables-2/messages/errors?${window(w)}`);
export const saveError = (e: MessageError) => post<Saved>("/tables-2/messages/errors/update", e);
export const deleteError = (e: MessageError) => post<Saved>("/tables-2/messages/errors/delete", e);
