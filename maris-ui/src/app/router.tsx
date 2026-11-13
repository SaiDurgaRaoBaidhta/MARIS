/* Which feature draws the page in front. Pages are a stack (Home -> menu -> screen …) kept in shared/lib/app-state. */
import { HomePage } from "@/features/home/HomePage";
import { MENUS } from "@/features/menu/api";
import { MenuPage } from "@/features/menu/MenuPage";
import { ScreenIndexPage } from "@/features/screen-index/ScreenIndexPage";
import { CLEAN_PAGES } from "@/features/screens/cleanPages";
import { mapOf } from "@/features/screens/registry";
import { eglCode, ScreenPage } from "@/features/screens/ScreenPage";
import { InventoryPage } from "@/features/yard-tracking/InventoryPage";
import { useMock } from "@/shared/api/client";
import { useApp, type Page } from "@/shared/lib/app-state";

/** the page for a screen key, wherever it was reached from (side bar, menu tile, a button's wiring): map screens open the drawn map,
 *  except a map screen rebuilt on the clean API (a module's features/<module>/screens.tsx), which opens its new page */
export function resolve(p: Page): Page {
  if (p.kind !== "screen") return p;
  if (!useMock && CLEAN_PAGES[eglCode(p.key).toUpperCase()]) return p;
  const m = /^(yardmap|shop):([A-Z]{2})/.exec(p.key);
  if (m) return { kind: "map", yard: m[2], shop: m[1] === "shop", label: p.label };
  const map = mapOf(p.key);
  return map ? { kind: "map", ...map, label: p.label } : p;
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
