import { useElementWidth } from "@/shared/hooks/useElementWidth";
import { useApp } from "@/shared/lib/app-state";
import { Icon } from "@/shared/ui/Icon";
import { iconFor } from "@/shared/ui/icons";

export interface Tile { label: string; i: number; open?: () => void }
export interface TileGroup { name: string; list: Tile[] }

/* Vehicle Yard Tracking menus keep MARIS's order: yards in this order, Shop Map, Yard Map, Reports, Pseudo Trains */
const YT_ORDER = ["hayward yard", "daly city yard", "berryessa station", "richmond yard", "concord yard", "system wide", "dublin/pleasanton", "pittsburg/baypoint", "milbrae storage"];
const itemRank = (t: string) => /^shop map/i.test(t) ? 0 : /map$/i.test(t) ? 1 : /^reports?$/i.test(t) ? 2 : /pseudo/i.test(t) ? 3 : 4;
const norm = (n: string) => n.replace(/\s+/g, " ").trim().toLowerCase();

function arrange(groups: TileGroup[]): { groups: TileGroup[]; fixed: boolean } {
  const fixed = groups.some(g => norm(g.name) === "hayward yard") && groups.some(g => norm(g.name) === "system wide");
  if (!fixed) return { groups, fixed };
  const rank = (g: TileGroup) => { const i = YT_ORDER.indexOf(norm(g.name)); return i < 0 ? 99 : i; };
  return { fixed, groups: [...groups].sort((a, b) => rank(a) - rank(b)).map(g => ({ ...g, list: [...g.list].sort((a, b) => itemRank(a.label) - itemRank(b.label)) })) };
}

/** tallest card first, each into the shortest column, so the columns end close together */
function masonry<T>(cards: T[], n: number, height: (c: T) => number): T[][] {
  const cols = Array.from({ length: n }, () => ({ h: 0, list: [] as T[] }));
  [...cards].sort((a, b) => height(b) - height(a)).forEach(c => { const col = cols.reduce((a, b) => b.h < a.h ? b : a); col.list.push(c); col.h += height(c); });
  return cols.map(c => c.list);
}

/** functions as tiles in cards; cards in masonry columns (or MARIS's fixed order for the yard tracking menus) */
export function MenuTiles({ groups: raw }: { groups: TileGroup[] }) {
  const app = useApp();
  const [ref, width] = useElementWidth();
  const { groups, fixed } = arrange(raw);
  const used = new Map<string, string>();
  const icons = new Map(groups.flatMap(g => g.list.map(it => [g.name + it.i, iconFor(it.label, used)] as const)));
  const n = groups.length === 1 ? 1 : Math.max(1, Math.min(groups.length, Math.floor(width / 330)));
  const cols = fixed ? null : masonry(groups, n, g => 60 + g.list.length * 50);

  const Card = ({ g }: { g: TileGroup }) => (
    <section className="rounded-[18px] border border-white/90 bg-gradient-to-br from-white/70 to-white/45 px-3.5 pb-3 pt-4 shadow-[0_0_0_1px_rgba(169,219,241,.35),0_16px_30px_-24px_rgba(11,37,69,.4)] dark:border-line-dark dark:from-[#10213a] dark:to-[#0d1b2c] dark:shadow-none">
      <h3 className="mx-1.5 mb-2.5 font-semibold text-bart-700 dark:text-[#7fd3f3]">{g.name}</h3>
      {g.list.map(it => (
        <button key={it.i} type="button" onClick={() => it.open ? it.open() : app.info("Sorry, this function is still under development")}
          className="group my-0.5 flex w-full items-center gap-2.5 rounded-[11px] p-2.5 text-left font-medium text-slate-800 hover:bg-sky/95 hover:text-bart-700 focus-visible:outline-2 focus-visible:outline-bart dark:text-slate-200 dark:hover:bg-bart/14 dark:hover:text-[#7fd3f3]">
          <span className="grid size-7 flex-none place-items-center rounded-lg bg-sky text-bart-600 group-hover:bg-bart group-hover:text-white dark:bg-bart/16 dark:text-[#4fc3f7]"><Icon name={icons.get(g.name + it.i) || "file"} size={15} /></span>
          <span className="flex-1">{it.label}</span>
          <span className="grid text-sky-2 transition group-hover:translate-x-0.5 group-hover:text-bart dark:text-line-dark"><Icon name="chev" size={14} /></span>
        </button>))}
    </section>);

  return (
    <div ref={ref} data-screen className="animate-wv-in px-2 pb-6">
      {cols ? (
        <div className="flex items-start gap-[18px]">
          {cols.map((c, i) => <div key={i} className="flex min-w-0 flex-1 flex-col gap-[18px]">{c.map(g => <Card key={g.name} g={g} />)}</div>)}
        </div>
      ) : (
        <div className="grid gap-[18px]" style={{ gridTemplateColumns: `repeat(${Math.max(1, Math.floor(width / 330))}, minmax(0, 1fr))` }}>{groups.map(g => <Card key={g.name} g={g} />)}</div>
      )}
    </div>
  );
}
