/* The old screens this module replaces: old screen code -> its new page. The UI finds this file by itself
 * (features/<module>/screens.tsx); there is no shared list to edit. */
import type { ReactNode } from "react";
import { IcsReportPage } from "./IcsReportPage";

export const CLEAN_SCREENS: Record<string, () => ReactNode> = {
  VHIC30I: () => <IcsReportPage />,   // ICS reports: criteria
  VHIC31I: () => <IcsReportPage />,   // ICS reports: the report (shown on the same page)
  VHIC32I: () => <IcsReportPage />,   // ICS reports menu: its only button opens VHIC30I
};
