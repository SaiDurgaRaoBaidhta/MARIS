import type { Role } from "@/features/auth/types";

export interface MenuItem { label: string; i: number; dis: boolean; key?: string; none?: 1 }
export interface MenuGroup { name: string; list: MenuItem[] }
/** a main-menu application (Vehicle Trouble Desk …) and the functions on its menu */
export interface Section { cap: string; to: string; kind: "menu" | "screen" | "na" | "locked"; title: string; groups: MenuGroup[]; key?: string }
export interface MenuData { access: Record<Role, string[]>; sections: Section[] }
