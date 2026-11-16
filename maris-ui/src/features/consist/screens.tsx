/* The old screens this module replaces: old screen code -> its new page. The UI finds this file by itself
 * (features/<module>/screens.tsx). */
import type { ReactNode } from "react";
import { CarLocationPage, ConsistMaintenancePage, TrainSwapPage } from "./ConsistMaintenancePages";
import { LayUpPage, ReconfigurePage } from "./TrainPages";

export const CLEAN_SCREENS: Record<string, () => ReactNode> = {
  VHMT01I: () => <ConsistMaintenancePage kind="makeTrain" />,   // make train (update)
  VHCM01I: () => <ConsistMaintenancePage kind="combine" />,     // combine trains (update)
  VHCT01I: () => <ConsistMaintenancePage kind="clear" />,       // clear train (update)
  VHCC01I: () => <ConsistMaintenancePage kind="change" />,      // change consist (update)
  VHSC01I: () => <ConsistMaintenancePage kind="split" />,       // split consist (update)
  VHTS01I: () => <TrainSwapPage />,                             // train swap (update)
  VHCY01I: () => <CarLocationPage />,                           // change a car's location (update)
  VHLT01I: () => <LayUpPage />,                                 // lay-up a train (update)
  VHTR01I: () => <ReconfigurePage />,                           // reconfigure a train (update)
  VHTR02I: () => <ReconfigurePage />,                           // its confirmation, on the same page
};
