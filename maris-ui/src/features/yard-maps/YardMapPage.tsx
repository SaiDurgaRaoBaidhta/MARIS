/* A yard or shop map in React (it replaces the iframe viewer public/legacy/maris-map.html): the old drawing with MARIS's cars
 * (fleet VHSW01A + the clean map APIs' orientation marks), the map's own buttons, 2D / 3D, full screen and Return.
 * Every car of the yard is shown: a car no drawn box can hold is listed under "Other cars on this yard". */
import { lazy, Suspense, useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { fetchFleet, fetchLabels } from "@/features/yard-tracking/api";
import { useApp } from "@/shared/lib/app-state";
import { PageHead } from "@/shared/ui/PageHead";
import { MapButtons, useMapActions, type MapMode } from "./actions";
import { drawnCells, loadDrawing, loadSpec, type Drawing } from "./drawing";
import { MapPlan2D } from "./MapPlan2D";
import { buildModel, type FleetTrack } from "./model";
import type { MapCar, MapModel, YardCode } from "./types";

const Map3D = lazy(() => import("./three/Map3D"));

export interface MapTarget { yard: YardCode; shop: boolean; mode?: MapMode }

interface Loaded { drawing: Drawing; model: MapModel }

const btn = "inline-flex h-8 items-center gap-1.5 rounded-lg border px-3 text-[13px] font-semibold shadow-sm transition-colors disabled:opacity-50 "
  + "border-slate-300 bg-white text-slate-800 hover:bg-slate-50 dark:border-[#2c4a68] dark:bg-[#16304a] dark:text-[#e6edf5] dark:hover:bg-[#1d3b5a]";

/** reads the map and its cars; `fresh` asks the backend again */
async function load(t: MapTarget, fresh: boolean): Promise<Loaded> {
  const [drawing, spec, other, fleet, labels] = await Promise.all([
    loadDrawing(t.yard, t.shop), loadSpec(t.yard, t.shop), loadDrawing(t.yard, !t.shop),
    fetchFleet(fresh), fetchLabels(t.yard),
  ]);
  if (!drawing || !spec) throw new Error(`There is no ${t.shop ? "shop" : "yard"} map for ${t.yard}.`);
  const drawnAll = new Set([...drawnCells(drawing), ...drawnCells(other)]);
  const tracks = (fleet.fleet?.[t.yard] ?? []) as unknown as FleetTrack[];
  return { drawing, model: buildModel(t.yard, t.shop, spec, tracks, labels, drawnAll) };
}

export function YardMapPage({ map, navWidth }: { map: MapTarget; navWidth: number }) {
  const app = useApp();
  const mode: MapMode = map.mode ?? "inquiry";
  const key = `${map.yard}:${map.shop}:${mode}`;
  const [data, setData] = useState<Loaded | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [is3d, setIs3d] = useState(false);
  const [full, setFull] = useState(true);
  const [othersOpen, setOthersOpen] = useState(false);
  const [note, setNote] = useState("");
  const gen = useRef(0);

  const read = useCallback((fresh: boolean) => {
    const g = ++gen.current;
    setBusy(true); setError("");
    load(map, fresh)
      .then(d => { if (g === gen.current) setData(d); })
      .catch(e => { if (g === gen.current) setError(e instanceof Error ? e.message : String(e)); })
      .finally(() => { if (g === gen.current) setBusy(false); });
  }, [map]);

  // every map opens in 2D and full screen, with its cars read fresh
  useEffect(() => { setData(null); setIs3d(false); setFull(true); setOthersOpen(false); setNote(""); read(true); }, [key]);   // eslint-disable-line react-hooks/exhaustive-deps

  const actions = useMapActions({ yard: map.yard, shop: map.shop, mode, refresh: () => read(true) });
  const exit = actions.buttons.find(b => b.id === "exit");
  const others = actions.buttons.filter(b => b.id !== "exit");

  // the boxes of the move being picked
  const selected = useMemo(() => {
    const s = new Set<string>(); const sel = actions.selection; const m = data?.model;
    if (!m || sel.kind === "none") return s;
    for (const [track, cars] of Object.entries(m.cells)) cars.forEach((c, i) => {
      const t = c ? c.track : track, p = c ? c.position : i + 1;
      if (sel.kind === "yard" && t === sel.track && p >= sel.from && p <= sel.to) s.add(track + "|" + i);
      if (sel.kind === "shop" && t === sel.track && p === sel.position) s.add(track + "|" + i);
    });
    return s;
  }, [actions.selection, data]);

  // fit the drawing to the space it has (the old focus mode: whole map on screen, at most 125 %)
  const body = useRef<HTMLDivElement>(null);
  const [room, setRoom] = useState({ w: 0, h: 0 });
  useLayoutEffect(() => {
    const e = body.current; if (!e) return;
    const measure = () => setRoom({ w: e.clientWidth, h: e.clientHeight });
    measure();
    const ro = typeof ResizeObserver !== "undefined" ? new ResizeObserver(measure) : null;
    ro?.observe(e);
    return () => ro?.disconnect();
  }, [full, data]);
  const scale = useMemo(() => {
    if (!data) return 1;
    const w = +(/width:\s*([\d.]+)px/.exec(data.drawing.bxStyle)?.[1] ?? 1200), h = +(/height:\s*([\d.]+)px/.exec(data.drawing.bxStyle)?.[1] ?? 700);
    if (!room.w || !room.h) return 1;
    return Math.max(.2, Math.min((room.w - 36) / w, (room.h - 36 - 64) / h, 1.25));
  }, [data, room]);

  // Esc leaves full screen, F toggles it, V toggles 3D / 2D (the old viewer's keys)
  useEffect(() => {
    const on = (e: KeyboardEvent) => {
      if (e.target instanceof HTMLElement && /input|select|textarea/i.test(e.target.tagName)) return;
      if (e.altKey || e.ctrlKey || e.metaKey) return;
      if (e.key === "Escape" && full) setFull(false);
      else if (e.key === "f" || e.key === "F") setFull(f => !f);
      else if (e.key === "v" || e.key === "V") setIs3d(v => !v);
    };
    addEventListener("keydown", on);
    return () => removeEventListener("keydown", on);
  }, [full]);

  const onLink = useCallback((which: "inventory" | "move") => {
    app.open(which === "inventory"
      ? { kind: "screen", key: "web:inv", label: "Daily Yard Track Inventory", yard: map.yard }
      : { kind: "screen", key: "web:move", label: "Car Move Requests", yard: map.yard });
  }, [app, map.yard]);

  const onCar3d = useCallback((c: MapCar) => actions.onCar(c, c.track, c.position), [actions]);
  const title = data?.drawing.title ?? (map.shop ? "Shop Map" : "Yard Map");
  const code = data?.drawing.code ?? "";
  const message = actions.message || note;

  const stage = (
    <div className="ym-scope h-full" data-testid="yard-map">
      <div className="frame mvx-frame h-full">
        <section className="view mvx-view mv-view h-full">
          <section className={`mv fs fs-css h-full${is3d ? " is3d" : ""}`} aria-label={`${title} ${code}`}>
            <div ref={body} className="mv-body relative h-full overflow-hidden">
              {error && <p role="alert" className="absolute left-1/2 top-6 z-10 -translate-x-1/2 rounded-lg bg-red-50 px-4 py-2 text-[13px] font-semibold text-red-800">{error}</p>}
              {!data && !error && <p role="status" className="absolute left-1/2 top-1/3 -translate-x-1/2 text-[14px] font-semibold text-slate-600">Loading the map…</p>}
              {data && !is3d && (
                <MapPlan2D drawing={data.drawing} model={data.model} scale={scale} selected={selected}
                           onCar={actions.onCar} onTrack={t => (t === "T-95" ? actions.onPseudo() : actions.onTrack(t))} onLink={onLink} />
              )}
              {data && is3d && (
                <Suspense fallback={<p role="status" className="p-6 text-[14px] font-semibold text-slate-600">Loading the 3D map…</p>}>
                  <Map3D model={data.model} update={mode === "update"} onCarClick={onCar3d} onTrackClick={t => actions.onTrack(t)} onMessage={setNote} className="absolute inset-0" />
                </Suspense>
              )}
              {data && data.model.others.length > 0 && (
                <div className="absolute bottom-[60px] left-3 z-20 max-w-[min(560px,90%)] rounded-xl border border-slate-300 bg-white/95 shadow-lg dark:border-[#2c4a68] dark:bg-[#0f1e2e]/95">
                  <button type="button" className="flex w-full items-center justify-between gap-3 px-3 py-2 text-left text-[13px] font-semibold text-slate-800 dark:text-[#e6edf5]"
                          aria-expanded={othersOpen} onClick={() => setOthersOpen(o => !o)}>
                    Other cars on this yard ({data.model.others.length})<span aria-hidden>{othersOpen ? "▾" : "▸"}</span>
                  </button>
                  {othersOpen && (
                    <ul aria-label="Other cars on this yard" className="max-h-56 overflow-auto px-3 pb-2 text-[12px]">
                      {data.model.others.map(c => (
                        <li key={c.car + c.track}>
                          <button type="button" className="flex w-full gap-3 rounded px-1 py-0.5 text-left font-mono hover:bg-slate-100 dark:hover:bg-[#16304a]"
                                  onClick={() => actions.onCar(c, c.track, c.position)} title={`${c.car} · ${c.status}`}>
                            <b className="w-16">{c.text}</b><span className="w-20">{c.track}</span><span className="w-10">pos {c.position}</span><span>{c.status}</span>
                          </button>
                        </li>))}
                    </ul>)}
                </div>)}
              {message && <p role="status" className="absolute bottom-[60px] right-3 z-20 rounded-lg bg-amber-50 px-3 py-1.5 text-[13px] font-semibold text-amber-900">{message}</p>}
              <div className="absolute inset-x-3 bottom-3 z-30 flex items-center justify-between gap-3">
                <MapButtons buttons={others} className="flex flex-wrap items-center gap-2" buttonClassName={btn} />
                <div className="flex items-center gap-2">
                  {busy && <span role="status" className="text-[12px] text-slate-600 dark:text-slate-300">Refreshing…</span>}
                  <div role="group" aria-label="Map view" className="flex rounded-lg border border-slate-300 bg-white p-0.5 dark:border-[#2c4a68] dark:bg-[#13263b]">
                    {(["3d", "2d"] as const).map(v => (
                      <button key={v} type="button" aria-pressed={(v === "3d") === is3d} title={v === "3d" ? "3D view (V)" : "2D view, the classic flat map (V)"}
                              onClick={() => setIs3d(v === "3d")}
                              className={`h-7 rounded-md px-3.5 text-[13px] font-bold ${(v === "3d") === is3d ? "bg-[#1e3a5f] text-white dark:bg-[#2b6cb0]" : "text-slate-700 dark:text-[#e6edf5]"}`}>
                        {v.toUpperCase()}
                      </button>))}
                  </div>
                  <button type="button" className={btn + " w-8 justify-center px-0"} aria-label={full ? "Exit full screen" : "Full screen"}
                          title={full ? "Exit full screen (F or Esc)" : "Full screen (F)"} onClick={() => setFull(f => !f)}>
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden style={{ width: 16, height: 16, flex: "none", overflow: "visible" }}>
                      {full ? <path d="M9 4v5H4M15 4v5h5M9 20v-5H4M15 20v-5h5" /> : <path d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5" />}
                    </svg>
                  </button>
                  {exit && <button type="button" className={btn} onClick={() => exit.run()} title={exit.key ? `Alt+${exit.key.toUpperCase()}` : undefined}>← {exit.label}</button>}
                </div>
              </div>
            </div>
          </section>
        </section>
      </div>
    </div>
  );

  return full
    ? <div className="fixed inset-0 z-[6000] bg-[#eef3f8] dark:bg-[#08121d]">{stage}</div>
    : (
      <>
        <div className="fixed right-6 top-16 z-[40] px-6" style={{ left: navWidth }}><PageHead title={title} code={code || undefined} /></div>
        <div className="fixed bottom-0 right-4 top-[166px] z-[40] overflow-hidden rounded-t-2xl" style={{ left: navWidth + 16 }}>{stage}</div>
      </>);
}
export default YardMapPage;
