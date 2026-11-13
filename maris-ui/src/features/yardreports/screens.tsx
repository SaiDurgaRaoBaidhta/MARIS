/* The old screens this module replaces: old screen code -> its new page. The UI finds this file by itself
 * (features/<module>/screens.tsx). Map screens reach these pages once shared/clean-pages-before-maps is merged
 * (until then app/router.tsx sends every map screen to the drawn map). */
import type { ReactNode } from "react";
import { PseudoTrainMovePage } from "./PseudoTrainMovePage";
import { SystemWidePage } from "./SystemWidePage";
import { YardMapPage, type MapColours } from "./YardMapPage";

/** a map page; colours: how the old screen colours its cars */
const map = (map: string, code: string, title: string, colours: MapColours) => () => <YardMapPage map={map} code={code} title={title} colours={colours} />;

export const CLEAN_SCREENS: Record<string, () => ReactNode> = {
  VHIC01: map("berryessa", "VHIC01", "Berryessa Station", "home"),
  VHIC02: map("berryessa", "VHIC02", "Berryessa Station (Update)", "condition"),
  VHYT80I: map("millbrae", "VHYT80I", "Millbrae W-Line Storage", "home"),
  VHYT81I: map("millbrae", "VHYT81I", "Millbrae Storage (Update)", "none"),
  VHYT105I: map("daly-city-yard", "VHYT105I", "Daly City Yard Map", "home"),
  VHYT106I: map("daly-city-yard", "VHYT106I", "Daly City Yard Map (Update)", "condition"),
  VHYT70I: map("concord-yard", "VHYT70I", "Concord Yard Map", "home"),
  VHYT71I: map("concord-yard", "VHYT71I", "Concord Yard Map (Update)", "condition"),
  VHYT90I: map("richmond-yard", "VHYT90I", "Richmond Yard Map", "home"),
  VHYT91I: map("richmond-yard", "VHYT91I", "Richmond Yard Map (Update)", "condition"),
  VHYT100I: map("daly-city-shop", "VHYT100I", "Daly City Shop Map", "home"),
  VHYT101I: map("daly-city-shop", "VHYT101I", "Daly City Shop (Update)", "home"),   // the shop's update map colours by home yard too
  VHYT61I: map("concord-shop", "VHYT61I", "Concord Shop Map (Update)", "home"),
  VHSW01I: () => <SystemWidePage />,
  VHIC03I: () => <PseudoTrainMovePage />,   // move a pseudo train to another yard (writes)
};
