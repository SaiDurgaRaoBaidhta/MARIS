/* The old screens this module replaces: old screen code -> its new page (found by features/screens/cleanPages.ts). */
import type { ReactNode } from "react";
import { ConsistLookupPage, IncidentLogPage } from "./IncidentEventsPages";
import { SymptomHistoryPage, WorksheetPage } from "./WorksheetPages";

export const CLEAN_SCREENS: Record<string, () => ReactNode> = {
  VHIE01I: () => <IncidentLogPage />,     // Event and incident log
  VHIE02I: () => <IncidentLogPage />,     // its printer selection (Print on the same page)
  VHIE03I: () => <WorksheetPage />,       // Vehicle desk work sheet (revenue event log)
  VHIE04I: () => <WorksheetPage />,       // its event detail (update, delete, create incident on the same page)
  VHIE06I: () => <ConsistLookupPage />,   // Train or car look up
  VHIE07I: () => <SymptomHistoryPage />,  // Symptom code history (also the work sheet's Search)
};
