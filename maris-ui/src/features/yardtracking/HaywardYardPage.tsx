/* The Hayward yard update map (old VHYT05I), on the clean API: the cars on each track with the old colour rule (GiveAColor),
 * and the old three clicks (from car, to position, to track) that open the car move detail (VHYT11I). A track button or the
 * pseudo track opens it at once. The other buttons open the screens the old map opened, with the yard it passed. */
import { useCallback, useEffect, useState } from "react";
import { useApp } from "@/shared/lib/app-state";
import { PageHead } from "@/shared/ui/PageHead";
import { fetchHaywardMap, selectOnHaywardMap, type Click, type HaywardMap } from "./haywardApi";

const button = "h-9 rounded-lg border border-slate-300 bg-white px-3 font-semibold text-slate-700 disabled:opacity-50";
/** GiveAColor's colours: GO black (brown when PM is due), USABLE blue, anything else red */
const COLOUR: Record<string, string> = { go: "text-black", "go-pm-due": "text-amber-800", usable: "text-blue-700", "not-usable": "text-red-700" };
const query = (p: Record<string, string | number>) => "?" + new URLSearchParams(Object.entries(p).map(([k, v]) => [k, String(v)])).toString();

export function HaywardYardPage() {
  const app = useApp();
  const [map, setMap] = useState<HaywardMap | null>(null);
  const [clicks, setClicks] = useState<Click[]>([]);
  const [error, setError] = useState("");
  const load = useCallback(() => { setClicks([]); fetchHaywardMap().then(setMap).catch(e => setError(e instanceof Error ? e.message : String(e))); }, []);
  useEffect(load, [load]);
  const open = (screen: string, params: Record<string, string | number> = {}) =>
    app.open({ kind: "screen", key: "egl:" + screen + (Object.keys(params).length ? query(params) : ""), label: screen });
  const click = async (c: Click) => {
    const next = [...clicks, c];
    setError("");
    try {
      const s = await selectOnHaywardMap(next);
      if (s.open) { setClicks([]); open(s.open, s.params); } else setClicks(next);
    } catch (e) { setError(e instanceof Error ? e.message : String(e)); setClicks([]); }
  };
  const yard = map?.yard ?? "";
  return (
    <>
      <PageHead title="Hayward Yard Map (Update)" code="VHYT05I" />
      <section aria-label="Map buttons" className="mx-2 mb-3 flex flex-wrap gap-2">
        <button type="button" className={button} onClick={() => open("VHYT04I")}>Shop Map</button>
        <button type="button" className={button} onClick={() => open("VHYT09I", { yrdTrkgYrd: yard })}>Pseudo Status Train</button>
        <button type="button" className={button} onClick={() => open("VHMTOI")}>Tower Menu</button>
        <button type="button" className={button} onClick={() => open("VHYTM3I", { yrdTrkgYrd: yard })}>Reports</button>
        <button type="button" className={button} onClick={() => open("VHYT24I", { yrdTrkgYrd: yard })}>Print</button>
        <button type="button" className={button} disabled={!map?.maintenance} onClick={() => open("VHYTM6I", { yrdTrkgYrd: yard })}>Maint</button>
        <button type="button" className={button} onClick={load}>Refresh</button>
      </section>
      {error && <p role="alert" className="mx-2 mb-3 rounded-lg bg-red-50 px-3 py-2 text-red-800">{error}</p>}
      {clicks.length > 0 && <p role="status" className="mx-2 mb-3 text-slate-600">Selected: {clicks.map(c => c.spot ?? c.track ?? "pseudo").join(" → ")}</p>}
      {!map && !error && <p className="mx-2 text-slate-500">Loading the Hayward yard from MARIS…</p>}
      {map && <section aria-label="Hayward yard" className="mx-2 grid gap-2">
        <button type="button" className={button} onClick={() => click({ pseudo: true })}>PSEUDO TRACK</button>
        {map.tracks.map(t => {
          const btn = map.trackButtons.find(b => b.code.replace("-", "") === t.track.replace("-", ""));
          return (
            <div key={t.track} className="flex flex-wrap items-center gap-1" role="group" aria-label={`Track ${t.track}`}>
              <button type="button" className={`${button} w-20`} onClick={() => btn && click({ track: btn.code })} disabled={!btn}>{btn?.label ?? t.track}</button>
              {(t.spots.length ? t.spots : [1]).map(s => {
                const car = map.cars.find(c => c.track === t.track && c.position === s);
                return <button key={s} type="button" disabled={!car?.code} onClick={() => car && click({ spot: car.code })}
                  className={`h-8 min-w-14 rounded border border-slate-300 bg-white px-1 font-mono text-[12px] ${car ? COLOUR[car.condition] : "text-slate-300"}`}>
                  {car?.label ?? "-"}</button>;
              })}
            </div>
          );
        })}
      </section>}
    </>
  );
}
