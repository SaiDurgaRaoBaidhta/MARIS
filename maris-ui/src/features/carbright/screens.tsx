/* The old screens this module replaces: old screen code -> its new page (found by features/screens/cleanPages.ts). */
import type { ReactNode } from "react";
import { CarCleaningPage } from "./CarCleaningPage";
import { ThoroughCleanPage } from "./ThoroughCleanPage";

export const CLEAN_SCREENS: Record<string, () => ReactNode> = {
  VHCBR1I: () => <CarCleaningPage />,   // Car Thorough Cleaning Report
  VHCBR2I: () => <CarCleaningPage />,   // its printer selection (Print on the same page)
  VHCBP1I: () => <ThoroughCleanPage />, // add thorough cleanings (update)
};
