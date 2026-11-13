/* The old screens this module replaces: old screen code -> its new page. The UI finds this file by itself
 * (features/<module>/screens.tsx). Map screens reach these pages once shared/clean-pages-before-maps is merged
 * (until then app/router.tsx sends every map screen to the drawn map). */
import type { ReactNode } from "react";
import { SystemWidePage } from "./SystemWidePage";
import { YardMapPage } from "./YardMapPage";

const map = (map: string, code: string, title: string, update: boolean) => () => <YardMapPage map={map} code={code} title={title} update={update} />;

export const CLEAN_SCREENS: Record<string, () => ReactNode> = {
  VHIC01: map("berryessa", "VHIC01", "Berryessa Station", false),
  VHIC02: map("berryessa", "VHIC02", "Berryessa Station (Update)", true),
  VHYT80I: map("millbrae", "VHYT80I", "Millbrae W-Line Storage", false),
  VHYT81I: map("millbrae", "VHYT81I", "Millbrae Storage (Update)", true),
  VHYT105I: map("daly-city-yard", "VHYT105I", "Daly City Yard Map", false),
  VHYT106I: map("daly-city-yard", "VHYT106I", "Daly City Yard Map (Update)", true),
  VHYT70I: map("concord-yard", "VHYT70I", "Concord Yard Map", false),
  VHYT71I: map("concord-yard", "VHYT71I", "Concord Yard Map (Update)", true),
  VHYT90I: map("richmond-yard", "VHYT90I", "Richmond Yard Map", false),
  VHYT91I: map("richmond-yard", "VHYT91I", "Richmond Yard Map (Update)", true),
  VHSW01I: () => <SystemWidePage />,
};
