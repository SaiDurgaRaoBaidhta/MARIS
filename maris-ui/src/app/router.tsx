/* Which feature draws the page in front. Pages are a stack (Home -> menu -> screen …) kept in shared/lib/app-state. */
import { HomePage } from "@/features/home/HomePage";
import { MENUS } from "@/features/menu/api";
import { MenuPage } from "@/features/menu/MenuPage";
import { ScreenIndexPage } from "@/features/screen-index/ScreenIndexPage";
import { CLEAN_PAGES } from "@/features/screens/cleanPages";
import { KNOWN, mapOf } from "@/features/screens/registry";
import { eglCode, ScreenPage } from "@/features/screens/ScreenPage";
import { InventoryPage } from "@/features/yard-tracking/InventoryPage";
import { useMock } from "@/shared/api/client";
import { useApp, type Page } from "@/shared/lib/app-state";

const rebuilt = (key: string) => !!CLEAN_PAGES[eglCode(key).toUpperCase()];

/** the screen key of a rebuilt map (the update or inquiry screen, as the user may update), or null when it is not rebuilt */
function rebuiltMap(yard: string, shop: boolean, update: boolean): string | null {
  const base = `${shop ? "shop" : "yardmap"}:${yard}`;
  const keys = update ? [`${base}:u`, base] : [base, `${base}:u`];
  // maps with no yardmap: / shop: key (Berryessa, Millbrae inquiry): the screen index entries that carry the map
  const isUpdate = (k: string) => /update/i.test(KNOWN[k].title);
  const egl = Object.keys(KNOWN).filter(k => k.startsWith("egl:") && KNOWN[k].map?.yard === yard && KNOWN[k].map?.shop === shop)
    .sort((a, b) => Number(isUpdate(b) === update) - Number(isUpdate(a) === update));      // the user's kind of screen first
  return [...keys.filter(k => KNOWN[k]), ...egl].find(rebuilt) ?? null;
}

/** the page for a screen key, wherever it was reached from (side bar, menu tile, a button's wiring): map screens open the drawn map,
 *  except a map rebuilt on the clean API (a module's features/<module>/screens.tsx), which opens its new page; a map opened
 *  directly (Home, footer) too. update: the user may update (the old maps opened their update screen then) */
export function resolve(p: Page, update = false): Page {
  if (p.kind === "map") {
    const key = useMock ? null : rebuiltMap(p.yard, p.shop, update);
    return key ? { kind: "screen", key, label: p.label } : p;
  }
  if (p.kind !== "screen") return p;
  if (!useMock && rebuilt(p.key)) return p;
  const m = /^(yardmap|shop):([A-Z]{2})/.exec(p.key);
  if (m) return { kind: "map", yard: m[2], shop: m[1] === "shop", label: p.label };
  const map = mapOf(p.key);
  return map ? { kind: "map", ...map, label: p.label } : p;
}

export function Router() {
  const app = useApp();
  const page = resolve(app.stack[app.stack.length - 1], !!app.session?.update);
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
