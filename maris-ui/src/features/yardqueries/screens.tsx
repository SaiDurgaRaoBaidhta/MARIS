/* The old screens this module replaces: old screen code -> its new page. The UI finds this file by itself
 * (features/<module>/screens.tsx). */
import type { ReactNode } from "react";
import { HistoryPage } from "./HistoryPage";
import { ReportsPage } from "./ReportsPage";
import { ShopStatusPage } from "./ShopStatusPage";

export const CLEAN_SCREENS: Record<string, () => ReactNode> = {
  VHYT27I: () => <ReportsPage />,               // the reports criteria window
  VHYT25I: () => <ShopStatusPage />,            // shop status report
  VHYT29I: () => <HistoryPage kind="cars" />,    // car location history
  VHYT33I: () => <HistoryPage kind="status" />,  // yard vehicle status history
};
