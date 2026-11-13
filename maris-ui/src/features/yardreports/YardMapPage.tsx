/* A yard map (old screens VHIC01 / VHIC02 Berryessa, VHYT80I / 81I Millbrae, VHYT105I / 106I Daly City yard,
 * VHYT70I / 71I Concord yard, VHYT90I / 91I Richmond yard), on the clean API. Every track with its spots, in the old map's
 * order. The old screens' colour rules: the inquiry map colours a car by its home yard (the colours come from MARIS's
 * old colour statement group, through the API); the update map colours it by condition (GiveAColor).
 * Not carried over from the old update screens: Maint, Reports, Tower Menu and the spot clicks that open other screens. */
import { useCallback, useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { fetchMap, type YardCar, type YardMap } from "./api";

const button = "h-9 rounded-lg border border-slate-300 px-4 font-semibold disabled:opacity-50 dark:border-line-dark";

/** GiveAColor: GO black (PM due brown), USABLE blue, anything else red */
const CONDITION_COLOUR: Record<string, string> = { go: "black", "go-pm-due": "brown", usable: "blue", "not-usable": "red" };

/** an old screen colour name as CSS: "DARK ORANGE" -> darkorange, X11 "GREY55" -> 55 % grey */
function cssColour(name: string): string {
  const grey = /^GRE[YA](\d{1,3})$/i.exec(name.replace(/\s+/g, ""));
  if (grey) { const v = Math.round(Number(grey[1]) * 2.55); return `rgb(${v}, ${v}, ${v})`; }
  return name.replace(/\s+/g, "").toLowerCase();
}

function Spot({ car, update }: { car?: YardCar; update: boolean }) {
  if (!car) return <li className="h-7 min-w-14 rounded border border-dashed border-slate-300 dark:border-line-dark" aria-label="empty spot" />;
  const style = update
    ? { color: CONDITION_COLOUR[car.condition ?? ""] ?? "inherit", backgroundColor: "white" }
    : { color: cssColour(car.colour), backgroundColor: cssColour(car.background) };
  return (
    <li className="h-7 min-w-14 rounded border border-slate-400 px-1 text-center font-mono text-[12px] font-bold leading-7" style={style}
        title={`Car ${car.car}, consist ${car.consist || "-"}, ${car.status}, home yard ${car.homeYard}`}>{car.label}</li>
  );
}

export function YardMapPage({ map, code, title, update }: { map: string; code: string; title: string; update: boolean }) {
  const [data, setData] = useState<YardMap | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const load = useCallback(async () => {
    setBusy(true); setError("");
    try { setData(await fetchMap(map)); }
    catch (e) { setError(e instanceof Error ? e.message : String(e)); }
    finally { setBusy(false); }
  }, [map]);
  useEffect(() => { void load(); }, [load]);

  const at = new Map((data?.cars ?? []).map(c => [`${c.track}/${c.position}`, c]));
  return (
    <>
      <PageHead title={title} code={code} />
      <section aria-label="Yard map" className="mx-2 mb-6 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark">
        <div className="mb-3 flex items-center gap-3">
          <button type="button" className={button} disabled={busy} onClick={() => void load()}>Refresh</button>
          {data && <p className="text-sm text-slate-600 dark:text-[#c9d6e4]">{data.cars.length} car{data.cars.length === 1 ? "" : "s"} in yard {data.yard}</p>}
          {!data && !error && <p role="status" className="text-slate-500">Loading the map from MARIS…</p>}
        </div>
        {error && <p role="alert" className="mb-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{error}</p>}
        <div className="flex flex-col gap-2 overflow-x-auto">
          {data?.tracks.map(t => (
            <div key={t.track} role="group" aria-label={`Track ${t.track}`} className="flex items-center gap-2">
              <span className="w-20 shrink-0 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]">{t.track}</span>
              <ol className="flex gap-1">{t.spots.map(s => <Spot key={s} car={at.get(`${t.track}/${s}`)} update={update} />)}</ol>
            </div>
          ))}
        </div>
      </section>
    </>
  );
}
