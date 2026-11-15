/* The old screens this module replaces: old screen code -> its new page (found by features/screens/cleanPages.ts). */
import type { ReactNode } from "react";
import { ComponentsPage, RepairPage } from "./RepairPages";

export const CLEAN_SCREENS: Record<string, () => ReactNode> = {
  VHRV01I: () => <RepairPage screen="VHRV01I" />,   // Vehicle repair
  VHRV02I: () => <ComponentsPage />,                // Repair components (opened from VHRV01I / VHRV04I with incdNo, reprDocNo)
  VHRV03I: () => <RepairPage screen="VHRV03I" />,   // Event repair
  VHRV04I: () => <RepairPage screen="VHRV04I" />,   // Repair maintenance (change / delete)
};
