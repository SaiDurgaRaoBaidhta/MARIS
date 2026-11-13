import type { Page } from "@/shared/lib/app-state";

/** Quick access pages (side bar, header bell, footer links) */
export const QUICK: { icon: string; label: string; page: Page }[] = [
  { icon: "map", label: "Yard Track Inventory", page: { kind: "screen", key: "web:inv", label: "Yard Track Inventory" } },
  { icon: "print", label: "MARIS Print", page: { kind: "screen", key: "web:print", label: "MARIS Print" } },
  { icon: "chart", label: "ColdFusion Reports", page: { kind: "screen", key: "web:cf", label: "ColdFusion Reports" } },
  { icon: "bell", label: "NOGO Notifications", page: { kind: "screen", key: "web:nogo", label: "NOGO Notifications" } },
  { icon: "grid", label: "Screen Index", page: { kind: "screen", key: "screenindex", label: "Screen Index" } },
];
