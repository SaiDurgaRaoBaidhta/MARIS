/* The old screens this module replaces: old screen code -> its new page (found by features/screens/cleanPages.ts). */
import type { ReactNode } from "react";
import { CommentsPage, InDoorPage, JobSplitPage, LaborPage, RepairDetailPage } from "./SecondaryRepairJobPages";
import { OutTheDoorPage } from "./OutTheDoorPage";
import { JobStatusPage, ReprintPage, StockInquiryPage } from "./SecondaryRepairPages";

export const CLEAN_SCREENS: Record<string, () => ReactNode> = {
  VHSR05I: () => <StockInquiryPage />,   // Inquiry stock (its rows open the job history, VHSR10I)
  VHSR10I: () => <StockInquiryPage />,   // History job: reached from the stock inquiry
  VHSR11I: () => <JobStatusPage />,      // Status job
  VHSR15I: () => <ReprintPage />,        // Reprint job form
  VHSR01I: () => <InDoorPage />,         // In the door: a new job (its Job Split opens VHSR08I)
  VHSR08I: () => <JobSplitPage />,       // Job split (its Comments opens VHSR02I); takes ?jobNo
  VHSR02I: () => <CommentsPage />,       // Job / repair comments; takes ?jobNo&subJobDocNo&jobIndicator
  VHSR09I: () => <RepairDetailPage />,   // Repair detail (Labor Detail opens VHSR03I)
  VHSR03I: () => <LaborPage />,          // Labor detail and close job; takes ?jobNo&subJobDocNo
  VHSR04I: () => <OutTheDoorPage />,     // Sub job out the door (update)
};
