/* The old screens this module replaces: old screen code -> its new page. The UI finds this file by itself
 * (features/<module>/screens.tsx). */
import type { ReactNode } from "react";
import { ComponentsPage } from "./ComponentsPage";
import { AxlePage, SerialAddPage, TruckPage } from "./ConfigUpdatePages";

export const CLEAN_SCREENS: Record<string, () => ReactNode> = {
  VHCF16I: () => <ComponentsPage />,   // config component inquiry
  VHCF21I: () => <SerialAddPage />,    // add a component serial to stock (update)
  VHCF37I: () => <TruckPage build={false} />,   // truck inquiry
  VHCF35I: () => <TruckPage build />,  // truck build (update)
  VHCF06I: () => <AxlePage />,         // axle / gear box maintenance (update)
};
