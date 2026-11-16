/* Which feature draws the page in front. Pages are a stack (Home -> menu -> screen …) kept in shared/lib/app-state. */
import { HomePage } from "@/features/home/HomePage";
import { MENUS } from "@/features/menu/api";
import { MenuPage } from "@/features/menu/MenuPage";
import { ScreenIndexPage } from "@/features/screen-index/ScreenIndexPage";
import { CLEAN_PAGES } from "@/features/screens/cleanPages";
import { mapOf } from "@/features/screens/registry";
import { eglCode, ScreenPage } from "@/features/screens/ScreenPage";
import { InventoryPage } from "@/features/yard-tracking/InventoryPage";
import { useApp, type Page } from "@/shared/lib/app-state";

const rebuilt = (key: string) => !!CLEAN_PAGES[eglCode(key).toUpperCase()];

/** the page for a screen key, wherever it was reached from (side bar, menu tile, a button's wiring): every yard / shop map
 *  opens the drawn map (the reference viewer's island), even one rebuilt on the clean API; only non-map screens that a module
 *  rebuilt (a module's features/<module>/screens.tsx) open their new page. A map opened directly (Home, footer) too. */
export function resolve(p: Page): Page {
  if (p.kind === "map") return p;
  if (p.kind !== "screen") return p;
  const m = /^(yardmap|shop):([A-Z]{2})/.exec(p.key);
  if (m) return { kind: "map", yard: m[2], shop: m[1] === "shop", label: p.label };
  const map = mapOf(p.key);
  if (map) return { kind: "map", ...map, label: p.label };
  if (rebuilt(p.key)) return p;
  return p;
}

export function Router() {
  const app = useApp();
  const page = resolve(app.stack[app.stack.length - 1]);
  switch (page.kind) {
    case "home": return <HomePage />;
    case "menu": { const s = MENUS.sections.find(x => x.cap === page.section); return s ? <MenuPage key={s.cap} section={s} /> : null; }
    case "map": return null;   // drawn full screen by <MapIsland> (app/App.tsx)
    case "screen":
      if (page.key === "web:inv") return <InventoryPage key={page.yard} initialYard={page.yard} />;
      if (page.key === "screenindex") return <ScreenIndexPage />;
      return <ScreenPage key={page.key + app.stack.length} screenKey={page.key} label={page.label} />;
  }
}
