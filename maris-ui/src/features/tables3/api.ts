/* Table maintenance 3: the middleware's clean API (/api/v1/tables-3). Component serials: old VHCF49I (list), VHCF50I
 * (detail / update), VHCF51I (serial hours), VHCF52I (serial geo data). Rows are the old records' fields by name. */
import { get, post } from "@/shared/api/client";

export type Row = Record<string, string | number>;
export interface Stock { stock: string; description: string; text: string }
export interface Rows { rows: Row[]; message?: string }
export interface Choice { code: string; text: string }
export interface Form { form: Row; comments: string; lists: Record<string, Choice[]>; warnings: string[] }
export interface Result { ok: boolean; message: string }
export interface HoursChange { stock: string; serial: string; cumulativeHours: number; cumulativeMinutes: number; datePosted: string }
export interface GeoChange {
  stock: string; serial: string; geoDataName: string; dateMeasured: string; timeMeasured: string; measurement: string;
  measuredBy: string; inspector: string; location: string; position: string; car: string;
}

const q = (o: Record<string, string>) => new URLSearchParams(o).toString();
export const fetchOptions = () => get<{ stocks: Stock[]; warning?: string }>("/tables-3/component-serials/options");
export const fetchSerials = (car: string, stock: string, serial: string) => get<Rows>(`/tables-3/component-serials?${q({ car, stock, serial })}`);
export const fetchForm = (row: Row) => post<Form>("/tables-3/component-serials/form", row);
export const saveSerial = (original: Row, form: Row, comments: string) => post<Result>("/tables-3/component-serials/update", { original, form, comments });
export const fetchHours = (stock: string, serial: string) => get<Rows>(`/tables-3/serial-hours?${q({ stock, serial })}`);
export const saveHours = (c: HoursChange) => post<Result>("/tables-3/serial-hours/update", c);
export const fetchGeo = (stock: string, serial: string) => get<Rows & { locations: string[] }>(`/tables-3/serial-geo-data?${q({ stock, serial })}`);
export const saveGeo = (c: GeoChange) => post<Result>("/tables-3/serial-geo-data/update", c);
