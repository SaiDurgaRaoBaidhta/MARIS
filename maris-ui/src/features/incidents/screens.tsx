/* The old screens this module replaces: old screen code -> its new page (found by features/screens/cleanPages.ts). */
import type { ReactNode } from "react";
import { CarIncidentPage, RevenueIncidentPage, TrainIncidentPage } from "./IncidentEntryPages";
import { NeverGoPage } from "./NeverGoPage";
import { TrackingChangePage } from "./TrackingChangePage";
import { VehicleIncidentMaintenancePage, VehicleIncidentPage, VehicleIncidentUpdatePage } from "./VehicleIncidentPages";

export const CLEAN_SCREENS: Record<string, () => ReactNode> = {
  VHINC01I: () => <TrainIncidentPage />,        // Train incident (add / update)
  VHINC03I: () => <CarIncidentPage />,          // Event / shop / miscellaneous incident of a car
  VHINC06I: () => <RevenueIncidentPage />,      // Revenue incident of a car
  VHINC08I: () => <VehicleIncidentPage />,      // New vehicle incident (YRD / SHP / REV)
  VHINC09I: () => <VehicleIncidentUpdatePage />, // Vehicle incident update
  VHINC10I: () => <VehicleIncidentMaintenancePage />, // Vehicle incident maintenance
  VHCI02I: () => <TrackingChangePage />,        // Tracking change incident: list and apply an action (update)
  VHCI03I: () => <NeverGoPage />,               // Tracking change incident never go (update)
};
