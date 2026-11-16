/* The old screens this module replaces: old screen code -> its new page. The UI finds this file by itself
 * (features/<module>/screens.tsx). */
import type { ReactNode } from "react";
import { MessagesPage } from "./MessagesPage";

export const CLEAN_SCREENS: Record<string, () => ReactNode> = {
  VHCCS04I: () => <MessagesPage />,             // MRS messages received
  VHCCS05I: () => <MessagesPage />,             // its message detail (on the same page)
  VHCCS06I: () => <MessagesPage />,             // its cars (on the same page)
  VHCCS07I: () => <MessagesPage />,             // its errors (on the same page)
  VHCCS18I: () => <MessagesPage />,             // its hard copy (on the same page)
};
