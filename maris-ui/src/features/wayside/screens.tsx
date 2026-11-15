/* The old screens this module replaces: old screen code -> its new page (found by features/screens/cleanPages.ts). */
import type { ReactNode } from "react";
import { NarrativePage } from "./NarrativePage";
import { StockSearchPage } from "./StockSearchPage";

export const CLEAN_SCREENS: Record<string, () => ReactNode> = {
  WS17I: () => <StockSearchPage />,   // Search Stock No Table
  WS13I: () => <NarrativePage />,     // narratives of an incident, repair or PM (update)
};
