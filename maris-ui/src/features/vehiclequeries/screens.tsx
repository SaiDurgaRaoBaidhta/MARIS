/* The old screens this module replaces: old screen code -> its new page (found by features/screens/cleanPages.ts). */
import type { ReactNode } from "react";
import { BatchReportPage } from "./BatchReportPage";
import { CarHoursPage } from "./CarHoursPage";
import { CarInquiryPage } from "./CarInquiryPage";
import { CarStatusHistoryPage } from "./CarStatusHistoryPage";
import { ConsistLogPage } from "./ConsistLogPage";
import { ConsistSymptomPage, StatusTrainPage } from "./ConsistTrackingPage";
import { DailyStatusPage } from "./DailyStatusPage";
import { FleetReportPage } from "./FleetReportPage";
import { HomeYardFleetPage } from "./HomeYardFleetPage";
import { HomeYardStatusPage, YardStatusPage } from "./StatusReportPage";
import { CarStatusChangePage, ConsistOrientationPage, DncSheetPage, ScramPage } from "./MiscPages";
import { RepeaterCarsPage } from "./RepeaterCarsPage";
import { VehicleHistoryPage, VehicleStatusPage } from "./VehiclePages";

/** old screen -> the batch reports it prints (the keys of /api/v1/vehicle-queries/batch-reports) */
const BATCH: Record<string, string[]> = {
  VHCP01I: ["cars-pulled"],
  VHDP01I: ["trains-cleared-elsewhere"],
  VHFO01I: ["fixed-consist-orientation"],
  VHMC01I: ["clear-train-moves", "make-train-moves"],
  VHMR01I: ["reason-used-by-shop"],
  VHMR02I: ["shop-status-reason-50"],
  VHMR03I: ["shop-status-reason-099"],
  VHNG01I: ["no-go-cars-moved"],
  VHOT01I: ["combined-trains"],
  VHPS01I: ["pseudo-train-consists"],
  VHRC03I: ["revenue-car-run-time"],
  VHPT01I: ["yard-pseudo-trains"],
  VHYTOTI: ["out-of-town-no-go"],
};

export const CLEAN_SCREENS: Record<string, () => ReactNode> = {
  ...Object.fromEntries(Object.entries(BATCH).map(([code, reports]) => [code, () => <BatchReportPage code={code} reports={reports} />])),
  VHCSH1I: () => <CarStatusHistoryPage />,   // Car status history
  VHCSH2I: () => <CarStatusHistoryPage />,   // its printer window (Print on the same page)
  VHHF01I: () => <HomeYardFleetPage />,      // Home yard fleet summary
  VHHF02I: () => <HomeYardFleetPage />,      // its printer window (Print on the same page)
  VHCHR1I: () => <CarHoursPage />,           // Car hours report
  VHFR01I: () => <FleetReportPage />,        // Fleet report
  VHRC01I: () => <RepeaterCarsPage />,       // Repeater cars criteria
  VHRC02I: () => <RepeaterCarsPage />,       // its incident list (on the same page)
  VHYS01I: () => <HomeYardStatusPage />,     // Home yard status criteria
  VHYS02I: () => <HomeYardStatusPage />,     // Base / Status report (on the same page)
  VHYS03I: () => <HomeYardStatusPage />,     // NOGO report
  VHYS04I: () => <HomeYardStatusPage />,     // Summary report
  VHYS05I: () => <HomeYardStatusPage />,     // summary by car type
  VHYS06I: () => <YardStatusPage />,         // Yard status criteria
  VHYS07I: () => <YardStatusPage />,         // Base / Status report (on the same page)
  VHYS08I: () => <YardStatusPage />,         // NOGO report
  VHYS09I: () => <YardStatusPage />,         // Summary report
  VHYS10I: () => <YardStatusPage />,         // summary by car type
  VHSV01I: () => <VehicleStatusPage />,      // Vehicle status criteria
  VHSV02I: () => <VehicleStatusPage />,      // its incident report (on the same page)
  VHSV04I: () => <VehicleHistoryPage />,     // Vehicle history criteria
  VHSV05I: () => <VehicleHistoryPage />,     // its history report (on the same page)
  VHSV07I: () => <CarInquiryPage />,         // Car status / history inquiry (car)
  VHSV06I: () => <CarInquiryPage />,         // its report (on the same page)
  VHST05I: () => <StatusTrainPage />,        // Consist tracking: status train criteria
  VHST02I: () => <StatusTrainPage />,        // its summary list
  VHST04I: () => <StatusTrainPage />,        // its car details
  VHST01I: () => <ConsistSymptomPage />,     // symptom criteria (from VHIE03I)
  VHST03I: () => <ConsistSymptomPage />,     // its consist view
  VHCO01I: () => <ConsistOrientationPage />, // Consist orientation report
  VHDC01I: () => <DncSheetPage />,           // Reprint D & C sheet
  VHSCM1I: () => <ScramPage />,              // SCRAM report
  VHCHG1I: () => <CarStatusChangePage />,    // Change car status (update)
  VHCL01I: () => <ConsistLogPage />,         // Consist log criteria
  VHCL02I: () => <ConsistLogPage />,         // its consist list (on the same page)
  VHDVS1I: () => <DailyStatusPage />,        // Daily vehicle status
  VHDVS2I: () => <DailyStatusPage />,        // its 2nd page (on the same page)
  VHDVS3I: () => <DailyStatusPage />,        // its 3rd page (on the same page)
  VHDVS4I: () => <DailyStatusPage />,        // its printer window (Hard Copy on the same page)
};
