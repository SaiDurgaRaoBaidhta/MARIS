/* A yard tracking map on the clean API: old screens VHYT40I / VHYT41I (Dublin-Pleasanton), VHYT50I / VHYT51I
 * (Pittsburg-Bay Point) and VHYT04I (Hayward shop). Every track with its spots, in the old map's order.
 * - inquiry maps (VHYT40I / VHYT50I): cars coloured by home yard; a track opens its inventory (VHYT08I), the pseudo
 *   spot its pseudo track; Pseudo Status Train opens VHYT09I.
 * - update maps (VHYT41I / VHYT51I): cars coloured by condition (GiveAColor); the old three-click move: a car (from
 *   track and spot), a spot on the same track (to spot), then a track (to track) opens the car move (VHYT11I); Maint
 *   asks the access list and opens VHYTM6I; Tower Menu (VHMTOI) and Reports (VHYTM3I).
 * - the Hayward shop (VHYT04I): a car opens its status / history (VHSV06I); Print opens VHYT27I with the shop report.
 * Other screens are opened by screen code with the old screen's pass values as query parameters. */
import { useCallback, useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { useApp } from "@/shared/lib/app-state";
import type { YardCar } from "@/features/yardreports/api";
import { fetchTrackingMap, maintenanceAccess, type TrackingMap } from "./mapsApi";

const button = "h-9 rounded-lg border border-slate-300 px-4 font-semibold disabled:opacity-50 dark:border-line-dark";

/** GiveAColor: GO black (PM due brown), USABLE blue, anything else red */
const CONDITION_COLOUR: Record<string, string> = { go: "black", "go-pm-due": "brown", usable: "blue", "not-usable": "red" };

/** an old screen colour name as CSS: "DARK ORANGE" -> darkorange, X11 "GREY80" -> 80 % grey */
function cssColour(name: string): string {
  const grey = /^GRE[YA](\d{1,3})$/i.exec(name.replace(/\s+/g, ""));
  if (grey) { const v = Math.round(Number(grey[1]) * 2.55); return `rgb(${v}, ${v}, ${v})`; }
  return name.replace(/\s+/g, "").toLowerCase();
}

export type MapKind = "inquiry" | "update" | "shop";

/** the old update maps' move selection (VHYT41IW-MOVE / PASSVALUE) */
interface Move { clicked: number; firstTrack: string; fromTrack: string; fromSpot: number; toSpot: number }
const NO_MOVE: Move = { clicked: 0, firstTrack: "", fromTrack: "", fromSpot: 0, toSpot: 0 };

const query = (params: Record<string, string | number>) => "?" + new URLSearchParams(Object.entries(params).map(([k, v]) => [k, String(v)])).toString();

export function TrackingMapPage({ map, code, title, kind }: { map: string; code: string; title: string; kind: MapKind }) {
  const app = useApp();
  const [data, setData] = useState<TrackingMap | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [move, setMove] = useState<Move>(NO_MOVE);
  const [maintDenied, setMaintDenied] = useState(false);

  const load = useCallback(async () => {
    setBusy(true); setError(""); setMove(NO_MOVE);
    try { setData(await fetchTrackingMap(map)); }
    catch (e) { setError(e instanceof Error ? e.message : String(e)); }
    finally { setBusy(false); }
  }, [map]);
  useEffect(() => { void load(); }, [load]);

  const open = (screen: string, params: Record<string, string | number> = {}) =>
    app.open({ kind: "screen", key: "egl:" + screen + (Object.keys(params).length ? query(params) : ""), label: screen });
  const yard = data?.yard ?? "";

  /** VHYT41IS-CLK-..: 1st click a car (from), 2nd a spot on the same track (to spot), 3rd a track (to track): open the move */
  const click = (track: string, spot: number) => {
    const n = move.clicked + 1;
    if (n === 1) { setMove({ clicked: 1, firstTrack: track, fromTrack: track, fromSpot: spot, toSpot: spot }); return; }
    if (n === 2) { setMove({ ...move, clicked: 2, toSpot: track === move.firstTrack ? spot : move.toSpot }); return; }
    setMove(NO_MOVE);
    open("VHYT11I", { yrdTrkgYrd: yard, fromYrdSubLoc: move.fromTrack, positionNoFrom: move.fromSpot, positionNoTo: move.toSpot,
      toYrdSubLoc: track, yrdSubLoc: move.fromTrack });
  };

  const maint = async () => {
    try {
      const a = await maintenanceAccess(map);
      if (a.allowed) open("VHYTM6I", { yrdTrkgYrd: yard }); else setMaintDenied(true);
    } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
  };

  const onCar = (car: YardCar | undefined, track: string, spot: number) => {
    if (kind === "update") click(track, spot);
    else if (kind === "shop" && car) open("VHSV06I", { carNo: car.car });
  };

  const style = (car: YardCar) => kind === "update"
    ? { color: CONDITION_COLOUR[car.condition ?? ""] ?? "inherit", backgroundColor: "white" }
    : { color: cssColour(car.colour), backgroundColor: car.background ? cssColour(car.background) : undefined };

  const byKey = new Map<string, YardCar>();
  for (const c of data?.cars ?? []) {
    const oneSpot = data?.tracks.find(t => t.track === c.track)?.spots.length === 0;
    byKey.set(oneSpot ? c.track : `${c.track}/${c.position}`, c);
  }
  const selected = (track: string, spot: number) => move.clicked > 0 && move.fromTrack === track && spot >= Math.min(move.fromSpot, move.toSpot) && spot <= Math.max(move.fromSpot, move.toSpot);

  return (
    <>
      <PageHead title={title} code={code} />
      <section aria-label="Yard map" className="mx-2 mb-6 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark">
        <div className="mb-3 flex flex-wrap items-center gap-2">
          <button type="button" className={button} disabled={busy} onClick={() => void load()}>Refresh</button>
          {kind !== "shop" && <button type="button" className={button} onClick={() => open("VHYT09I", { yrdTrkgYrd: yard })}>Pseudo Status Train</button>}
          {kind === "inquiry" && data?.pseudoTrack && (
            <button type="button" className={button} onClick={() => open("VHYT08I", { yrdSubLoc: data.pseudoTrack ?? "", yrdTrkgYrd: yard })}>{data.pseudoTrack}</button>)}
          {kind === "update" && <>
            <button type="button" className={button} onClick={() => open("VHMTOI")}>Tower Menu</button>
            <button type="button" className={button} onClick={() => open("VHYTM3I", { yrdTrkgYrd: yard })}>Reports</button>
            <button type="button" className={button} disabled={maintDenied} onClick={() => void maint()}>Maint</button>
          </>}
          {kind === "shop" && data?.shopReport && (
            <button type="button" className={button} onClick={() => open("VHYT27I", { whichReport: data.shopReport?.whichReport ?? "", yrdTrkgYrd: data.shopReport?.yard ?? "" })}>Print</button>)}
          {data && <p className="text-sm text-slate-600 dark:text-[#c9d6e4]">{data.cars.length} car{data.cars.length === 1 ? "" : "s"}{yard ? ` in yard ${yard}` : ""}</p>}
          {!data && !error && <p role="status" className="text-slate-500">Loading the map from MARIS…</p>}
        </div>
        {error && <p role="alert" className="mb-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{error}</p>}
        <div className="flex flex-col gap-2 overflow-x-auto">
          {data?.tracks.map(t => {
            const spots = t.spots.length ? t.spots : [0];
            return (
              <div key={t.track} role="group" aria-label={`Track ${t.track}`} className="flex items-center gap-2">
                {kind === "inquiry"
                  ? <button type="button" className="w-20 shrink-0 text-left text-[13px] font-semibold text-bart-600 underline"
                            onClick={() => open("VHYT08I", { yrdSubLoc: t.track, yrdTrkgYrd: yard })}>{t.track}</button>
                  : <span className="w-20 shrink-0 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]">{t.track}</span>}
                <ol className="flex gap-1">
                  {spots.map(s => {
                    const car = byKey.get(t.spots.length ? `${t.track}/${s}` : t.track);
                    const clickable = kind === "update" || (kind === "shop" && car);
                    const label = car ? car.label : "";
                    return (
                      <li key={s} className="min-w-14">
                        <button type="button" disabled={!clickable} aria-label={car ? `Car ${car.car}` : `${t.track} spot ${s || 1}`}
                                aria-pressed={kind === "update" ? selected(t.track, s) : undefined}
                                onClick={() => onCar(car, t.track, s)} style={car ? style(car) : undefined}
                                className={`h-7 w-full rounded border px-1 text-center font-mono text-[12px] font-bold leading-7 ${car ? "border-slate-400" : "border-dashed border-slate-300 dark:border-line-dark"} ${selected(t.track, s) ? "ring-2 ring-bart-600" : ""}`}
                                title={car ? `Car ${car.car}, consist ${car.consist || "-"}, ${car.status}, home yard ${car.homeYard}` : undefined}>{label}</button>
                      </li>);
                  })}
                </ol>
              </div>);
          })}
        </div>
      </section>
    </>
  );
}
