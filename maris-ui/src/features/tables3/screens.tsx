/* The old screens this module replaces: old screen code -> its new page. The UI finds this file by itself
 * (features/<module>/screens.tsx). */
import type { ReactNode } from "react";
import { ComponentSerialsPage } from "./ComponentSerialsPage";

export const CLEAN_SCREENS: Record<string, () => ReactNode> = {
  VHCF49I: () => <ComponentSerialsPage />,   // component serial list
  VHCF50I: () => <ComponentSerialsPage />,   // its detail view / update (on the same page)
  VHCF51I: () => <ComponentSerialsPage />,   // its serial hours (on the same page)
  VHCF52I: () => <ComponentSerialsPage />,   // its serial geo data (on the same page)
};
