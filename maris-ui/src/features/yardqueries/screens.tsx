/* The old screens this module replaces: old screen code -> its new page. The UI finds this file by itself
 * (features/<module>/screens.tsx). */
import type { ReactNode } from "react";
import { HistoryPage } from "./HistoryPage";
import { OutOfServicePage } from "./OutOfServicePage";
import { ReportsPage } from "./ReportsPage";
import { ShopStatusPage } from "./ShopStatusPage";
import { YardLocationPage } from "./YardLocationPage";
import { OverridesPage } from "./OverridesPage";

/** a value VHYT27I hands the report window (camelCase of its pass record field), from the page link */
const param = (name: string) => new URLSearchParams(window.location.search).get(name) ?? "";
/** VHYT27I's MM-DD-YYYY date as yyyy-MM-dd */
const iso = (mdy: string) => (/^\d{2}-\d{2}-\d{4}$/.test(mdy) ? `${mdy.slice(6)}-${mdy.slice(0, 2)}-${mdy.slice(3, 5)}` : mdy);

export const CLEAN_SCREENS: Record<string, () => ReactNode> = {
  VHYT27I: () => <ReportsPage />,               // the reports criteria window
  VHYT25I: () => <ShopStatusPage />,            // shop status report
  VHYT29I: () => <HistoryPage kind="cars" />,    // car location history
  VHYT33I: () => <HistoryPage kind="status" />,  // yard vehicle status history
  VHYT26I: () => <YardLocationPage yard={param("yrdTrkgYrd")} />,   // yard location status report
  VHYT32I: () => <OutOfServicePage yard={param("yrdTrkgYrd")} from={iso(param("startDate"))} to={iso(param("endDate"))}
                                   summary={param("guiIndex") === "7"} />,   // reason out of service, detail (6) or summary (7)
  VHYT34I: () => <OverridesPage />,                                  // make / break overrides (update)
};
