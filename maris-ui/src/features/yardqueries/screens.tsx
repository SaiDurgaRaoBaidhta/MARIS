/* The old screens this module replaces: old screen code -> its new page. The UI finds this file by itself
 * (features/<module>/screens.tsx). */
import type { ReactNode } from "react";
import { ShopStatusPage } from "./ShopStatusPage";

export const CLEAN_SCREENS: Record<string, () => ReactNode> = {
  VHYT25I: () => <ShopStatusPage />,   // shop status report
};
