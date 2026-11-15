/* The old screens this module replaces: old screen code -> its new page (found by features/screens/cleanPages.ts). */
import type { ReactNode } from "react";
import { CarWashOverduePage, CarWashesPage } from "./CarWashPages";
import { CarsWashPage, ConsistWashPage } from "./CarWashUpdatePages";

export const CLEAN_SCREENS: Record<string, () => ReactNode> = {
  VHCWR1I: () => <CarWashesPage />,        // Car wash report (date range)
  VHCWR2I: () => <CarWashOverduePage />,   // Car wash over due
  VHCWA2I: () => <ConsistWashPage />,      // Car wash of a consist (update)
  VHCWA3I: () => <CarsWashPage />,         // Car wash of single cars (update)
};
