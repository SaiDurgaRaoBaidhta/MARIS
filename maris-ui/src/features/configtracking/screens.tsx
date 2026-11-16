/* The old screens this module replaces: old screen code -> its new page. The UI finds this file by itself
 * (features/<module>/screens.tsx). */
import type { ReactNode } from "react";
import { CarConfigurationPage } from "./CarConfigurationPage";

export const CLEAN_SCREENS: Record<string, () => ReactNode> = {
  VHCF01I: () => <CarConfigurationPage />,   // car configuration (update)
  VFCF05I: () => <CarConfigurationPage />,   // its report printer window (Print on the same page)
};
