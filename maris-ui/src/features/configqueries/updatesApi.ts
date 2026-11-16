/* Configuration tracking query screens that write, on the clean API (/api/v1/config-queries): add a serial to stock
 * (old VHCF21I), truck inquiry / build (VHCF37I / VHCF35I), axle and gear box maintenance (VHCF06I). */
import { get, post } from "@/shared/api/client";

export interface SerialAddOptions { componentTypes: string[]; actions: string[]; date: string; notices: string[] }
export interface SerialAddResult { added: boolean; message: string; notices: string[] }
export interface TruckOptions { stocks: { stock: string; description: string }[]; statuses: string[]; locations: string[]; notices: string[] }
export interface TruckPart { key: string; name: string; serial: string; highlighted: boolean }
export interface Truck {
  message: string; car: string; dateRebuilt: string; status: string; location: string; comments: string;
  components: TruckPart[]; axleParts: { key: string; name: string; serial: string }[]; updatable: boolean; notices: string[];
}
export interface TruckBuild {
  stock: string; serial: string; components: Record<string, string>; takeOff: string[]; axleParts: Record<string, string>;
  status?: string; location?: string; comments?: string;
}
export interface Axle { message: string; values: Record<string, string>; checkable: string[]; updatable: boolean; notices: string[] }

const q = (o: Record<string, string>) => new URLSearchParams(o).toString();

export const fetchSerialAddOptions = () => get<SerialAddOptions>("/config-queries/serial-add/options");
export const fetchSerialAddStocks = (componentType: string) => get<{ stocks: string[]; notices: string[] }>(`/config-queries/serial-add/stocks?${q({ componentType })}`);
export const addSerial = (b: { componentType: string; stock: string; serial: string; date: string }) => post<SerialAddResult>("/config-queries/serial-add", b);

export const fetchTruckOptions = () => get<TruckOptions>("/config-queries/trucks/options");
export const fetchTruck = (stock: string, serial: string, forBuild: boolean) => get<Truck>(`/config-queries/trucks?${q({ stock, serial, forBuild: String(forBuild) })}`);
export const buildTruck = (b: TruckBuild) => post<Truck>("/config-queries/trucks/build", b);

export const fetchAxle = (kind: string, serial: string) => get<Axle>(`/config-queries/axles?${q({ kind, serial })}`);
export const updateAxle = (b: { kind: string; serial: string; values: Record<string, string>; checks: string[] }) => post<Axle>("/config-queries/axles/update", b);
