/* The old screens this module replaces: old screen code -> its new page (found by features/screens/cleanPages.ts). */
import type { ReactNode } from "react";
import { AddCarPage, ChangeCarPage, StatusHistoryPage } from "./CarPages";
import { NarrativePage } from "./NarrativePage";
import { TrackingPage } from "./TrackingPage";
import { CarHoursTestPage, ConsistHistoryPage, ConsistStatusPage } from "./VehicleMaintUpdatePages";
import { CarEventReportPage, IncidentNarrativePage, VehicleEventReportPage } from "./VehicleMaintPages";

export const CLEAN_SCREENS: Record<string, () => ReactNode> = {
  VHNAR02I: () => <IncidentNarrativePage />,    // Incident narrative
  VHNAR01I: () => <NarrativePage />,            // Narrative window (opened by incident / repair screens)
  VHVM12I: () => <VehicleEventReportPage />,    // Vehicle event report (hard copy)
  VHVM13I: () => <CarEventReportPage />,        // Car event (MOD) report batch parameters
  VHVM02I: () => <AddCarPage />,                // Add a car to the vehicle table
  VHVM03I: () => <ChangeCarPage />,             // Update a car of the vehicle table
  VHVM06I: () => <StatusHistoryPage />,         // Incident status history maintenance
  VHVM08I: () => <CarHoursTestPage />,          // Car hours after a test (update)
  VHVM10I: () => <ConsistStatusPage />,         // Consist status maintenance (update)
  VHVM19I: () => <ConsistHistoryPage />,        // Consist history maintenance (update)
  VHVM11I: () => <TrackingPage />,              // Vehicle parts status / mod tracking (update)
};
