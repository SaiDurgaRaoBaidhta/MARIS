/* Configuration tracking queries: the middleware's clean API (/api/v1/config-queries). Component inquiry: VHCF16A-D. */
import { get } from "@/shared/api/client";

export interface ComponentOptions { equipmentTypes: string[]; stocks: { stock: string; description: string }[]; serialStatuses: string[]; homeYards: string[]; warnings: string[] }
export interface Component {
  car: string; homeYard: string; equipmentType: string; serial: string; trackingCode: string; dateRebuilt: string; employee: string; lastRemovedBy: string;
  manufacturerSerial: string; parentEquipmentType: string; parentPosition: string; parentSerial: string; parentStock: string; position: string;
  purchaseOrder: string; configured: string; status: string; shopLocation: string; stock: string; hours: number;
}
export interface ComponentSearch { stock: string; serial: string; status: string; homeYard: string; equipmentType: string; sort: string }

export const fetchComponentOptions = () => get<ComponentOptions>("/config-queries/component-options");
export const fetchComponents = (s: ComponentSearch) =>
  get<{ limitReached: boolean; message?: string; rows: Component[] }>(`/config-queries/components?${new URLSearchParams({ ...s })}`);
