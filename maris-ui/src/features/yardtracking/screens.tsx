/* The old screens this module replaces: old screen code -> its new page (found by features/screens/cleanPages.ts). */
import type { ReactNode } from "react";
import { HaywardYardPage } from "./HaywardYardPage";
import { CarMovePage, OrientationPage, YardPrintPage } from "./MovePages";
import { ConsistVerifyPage, HaywardYardMapPage, PseudoTrainPage } from "./PseudoTrainPages";
import { ShopAdminMapPage, ShopMovePage } from "./ShopAdminPages";
import { TrackingMapPage } from "./TrackingMapPage";
import { FixOrderPage, MoveReasonPage, TrackPage, TrackReportPage } from "./TrackPages";
import { CarHistoryPrintPage, ForceClearPage, PseudoCorrectionPage, ShopActivityPage } from "./YardTrackingMiscPages";

export const CLEAN_SCREENS: Record<string, () => ReactNode> = {
  VHYT40I: () => <TrackingMapPage map="dublin" code="VHYT40I" title="Dublin/Pleasanton Yard Map" kind="inquiry" />,
  VHYT41I: () => <TrackingMapPage map="dublin-update" code="VHYT41I" title="Dublin/Pleasanton Yard Map (Update)" kind="update" />,
  VHYT50I: () => <TrackingMapPage map="pittsburg" code="VHYT50I" title="Pittsburg/Bay Point Yard Map" kind="inquiry" />,
  VHYT51I: () => <TrackingMapPage map="pittsburg-update" code="VHYT51I" title="Pittsburg/Bay Point Yard Map (Update)" kind="update" />,
  VHYT04I: () => <TrackingMapPage map="hayward-shop" code="VHYT04I" title="Hayward Shop Map" kind="shop" />,
  VHYT05I: () => <HaywardYardPage />,           // Hayward yard update map
  VHYT08I: () => <TrackPage />,                 // Yard detail inquiry: one track's cars
  VHYT02I: () => <TrackReportPage />,           // yard tracking report printer window
  VHYT06I: () => <MoveReasonPage />,            // change a car's move reason
  VHYT14I: () => <FixOrderPage />,              // fix the order of cars on a track
  VHYT11I: () => <CarMovePage />,               // move cars on a track
  VHYT13I: () => <OrientationPage />,           // a car's orientation
  VHYT24I: () => <YardPrintPage />,             // yard print
  VHYT10I: () => <ShopAdminMapPage map="hayward" code="VHYT10I" title="Hayward Shop Map (Update)" />,
  VHYT95I: () => <ShopAdminMapPage map="richmond" code="VHYT95I" title="Richmond Shop Map (Update)" />,
  VHYT03I: () => <ShopMovePage />,              // shop car move
  VHYT42I: () => <ShopMovePage />,              // its "reason not changed" question (on the same page)
  VHYT09I: () => <PseudoTrainPage />,           // Pseudo status train
  VHYT01I: () => <PseudoTrainPage />,           // its printer window (Print on the same page)
  VHYT07I: () => <HaywardYardMapPage />,        // Hayward yard map
  VHYT12I: () => <ConsistVerifyPage />,         // Verify / reset a pseudo train (update)
  VHYT60I: () => <TrackingMapPage map="concord-shop" code="VHYT60I" title="Concord Shop Map" kind="shop" />,
  VHYT96I: () => <TrackingMapPage map="richmond-shop" code="VHYT96I" title="Richmond Shop Map" kind="shop" />,
  VHYT31I: () => <ShopActivityPage />,          // shop activity list / print
  VHYT23I: () => <ShopActivityPage />,          // its print (on the same page)
  VHYT22I: () => <CarHistoryPrintPage />,       // car history print
  VHYT52I: () => <PseudoCorrectionPage />,      // pseudo train correction (update)
  VHYT54I: () => <ForceClearPage />,            // force clear (update)
};
