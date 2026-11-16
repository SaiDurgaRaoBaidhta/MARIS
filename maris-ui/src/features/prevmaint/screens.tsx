/* The old screens this module replaces: old screen code -> its new page (found by features/screens/cleanPages.ts). */
import type { ReactNode } from "react";
import { PmSchedulePage } from "./PmSchedulePage";
import { PrevMaintPage } from "./PrevMaintPage";

export const CLEAN_SCREENS: Record<string, () => ReactNode> = {
  VHHPM1I: () => <PrevMaintPage />,   // criteria; its reports open on the same page:
  VHHPR1I: () => <PrevMaintPage />,   // 1 PM hours by home yard
  VHHPM2I: () => <PrevMaintPage />,   // 2 PM hours with MTBI
  VHPMR1I: () => <PrevMaintPage />,   // 3 PM hours by PM type
  VHHPD1I: () => <PrevMaintPage />,   // 4 PM hours descending
  VHHPD2I: () => <PrevMaintPage />,   // 5 hours to next PM
  VHPMS01I: () => <PmSchedulePage />, // schedule / reschedule a PM (update)
};
