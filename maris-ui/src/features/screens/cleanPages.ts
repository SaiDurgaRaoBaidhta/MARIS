/* Screens rebuilt on the clean API: old screen code -> its new page, collected from every module's
 * features/<module>/screens.tsx. Every other screen still runs live through the middleware's screen engine. */
import type { ReactNode } from "react";

const modules = import.meta.glob<{ CLEAN_SCREENS: Record<string, () => ReactNode> }>("../*/screens.tsx", { eager: true });

export const CLEAN_PAGES: Record<string, () => ReactNode> = {};
for (const [file, m] of Object.entries(modules)) {
  for (const [code, page] of Object.entries(m.CLEAN_SCREENS)) {
    if (CLEAN_PAGES[code]) throw new Error(`${code} is claimed by two modules (${file})`);
    CLEAN_PAGES[code] = page;
  }
}
