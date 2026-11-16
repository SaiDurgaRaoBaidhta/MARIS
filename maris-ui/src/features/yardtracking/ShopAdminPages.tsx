/* The shop administration maps (old VHYT10I Hayward shop, VHYT95I Richmond shop) and the shop car move they open (old
 * VHYT03I, with its reason check window VHYT42I) on the clean API.
 * - the map: every track and spot of the old map, the cars with the old label and home-yard colour. Two clicks move a
 *   car: the first picks it, the second the place it goes to (the server replays the old map's own click rules and
 *   says when the move window opens, and with what). SV/HV, CI - Service, CI - Never Go, Yard Map, Reports, Change
 *   Reason and Print open their old screens by screen code.
 * - the move: the car, its current reason, a new reason and a new shop location; Update. When the reason did not
 *   change the old reason check asks first (Reason Is Not Changing / Change Reason). */
import { useCallback, useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { useApp } from "@/shared/lib/app-state";
import {
  fetchShopMap, openMove, selectSpots, updateMove, type MoveContext, type MoveOutcome, type MoveView, type ShopCar, type ShopMap,
} from "./shopAdminApi";

const button = "h-9 rounded-lg border border-slate-300 px-4 font-semibold disabled:opacity-50 dark:border-line-dark";
const primary = "h-9 rounded-lg bg-bart-600 px-4 font-semibold text-white disabled:opacity-50";
const panel = "mx-2 mb-6 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark";
const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 dark:border-line-dark dark:bg-[#0a1624]";
const message = (e: unknown) => (e instanceof Error ? e.message : String(e));
const query = (params: Record<string, string | number>) => "?" + new URLSearchParams(Object.entries(params).map(([k, v]) => [k, String(v)])).toString();

/** an old screen colour name as CSS ("DARK ORANGE" -> darkorange) */
const css = (name: string | null) => (name ? name.replace(/\s+/g, "").toLowerCase() : undefined);

function Note({ text, error }: { text: string; error?: boolean }) {
  if (!text) return null;
  return <p role={error ? "alert" : "status"} className={`mb-3 rounded-lg px-3 py-2 ${error ? "bg-red-50 text-red-800" : "bg-slate-50 text-slate-700"}`}>{text}</p>;
}

export function ShopAdminMapPage({ map, code, title }: { map: string; code: string; title: string }) {
  const app = useApp();
  const [data, setData] = useState<ShopMap | null>(null);
  const [error, setError] = useState("");
  const [note, setNote] = useState("");
  const [clicks, setClicks] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setBusy(true); setError(""); setClicks([]); setNote("");
    try { setData(await fetchShopMap(map)); } catch (e) { setError(message(e)); } finally { setBusy(false); }
  }, [map]);
  useEffect(() => { void load(); }, [load]);

  const open = (screen: string, params: Record<string, string | number> = {}) =>
    app.open({ kind: "screen", key: "egl:" + screen + (Object.keys(params).length ? query(params) : ""), label: screen });
  const yard = data?.yard ?? "";
  const picked = clicks.length ? data?.cars.find(c => `${c.track}/${c.position}` === clicks[0]) : undefined;

  const click = async (spot: string) => {
    const next = [...clicks, spot];
    try {
      const s = await selectSpots(map, next);
      setNote(s.message);
      if (s.open) { setClicks([]); open(s.open, s.params); }
      else setClicks(next.length >= 2 ? [] : next);
    } catch (e) { setError(message(e)); }
  };

  const byKey = new Map<string, ShopCar>();
  for (const c of data?.cars ?? []) byKey.set(`${c.track}/${c.position}`, c);
  const tracks = new Map<string, number[]>();
  for (const s of data?.spots ?? []) tracks.set(s.track, [...(tracks.get(s.track) ?? []), s.position]);
  const carParams = { carNo: picked?.car ?? "", whichGuiFrom: code, yrdTrkgYrd: yard };

  return (
    <>
      <PageHead title={title} code={code} />
      <section aria-label="Shop map" className={panel}>
        <div className="mb-3 flex flex-wrap items-center gap-2">
          <button type="button" className={button} disabled={busy} onClick={() => void load()}>Refresh</button>
          <button type="button" className={button} disabled={!picked} onClick={() => open("VHSV06I", { carNo: picked?.car ?? "" })}>SV / HV</button>
          <button type="button" className={button} onClick={() => open("VHCI02I", carParams)}>CI - Service</button>
          <button type="button" className={button} onClick={() => open("VHCI03I", carParams)}>CI - Never Go</button>
          <button type="button" className={button} onClick={() => open("VHYT06I", { yrdTrkgYrd: yard })}>Change Reason</button>
          <button type="button" className={button} onClick={() => open("VHYT07I")}>Yard Map</button>
          <button type="button" className={button} onClick={() => open("VHYTM3I", { yrdTrkgYrd: yard })}>Reports</button>
          {data && <button type="button" className={button} onClick={() => open("VHYT27I", { whichReport: data.report.whichReport, yrdTrkgYrd: data.report.yard })}>Print</button>}
          {data && <p className="text-sm text-slate-600">{data.cars.length} car{data.cars.length === 1 ? "" : "s"} in yard {yard}</p>}
          {!data && !error && <p role="status" className="text-slate-500">Loading the map from MARIS…</p>}
        </div>
        <Note text={error} error />
        <Note text={note} />
        {clicks.length === 1 && <p className="mb-2 text-sm text-slate-600">Now choose where {picked ? `car ${picked.car}` : "the car"} goes.</p>}
        <div className="flex flex-col gap-2 overflow-x-auto">
          {[...tracks.entries()].map(([t, positions]) => (
            <div key={t} role="group" aria-label={`Track ${t}`} className="flex items-center gap-2">
              <span className="w-20 shrink-0 text-[13px] font-semibold text-slate-600">{t}</span>
              <ol className="flex gap-1">
                {positions.map(p => {
                  const key = `${t}/${p}`, car = byKey.get(key);
                  const clickable = data?.spots.find(s => s.track === t && s.position === p)?.clickable ?? false;
                  return (
                    <li key={p} className="min-w-14">
                      <button type="button" disabled={!clickable} aria-pressed={clicks.includes(key)}
                              aria-label={car ? `Car ${car.car}` : `${t} spot ${p}`} onClick={() => void click(key)}
                              style={car ? { color: css(car.colour) } : undefined}
                              title={car ? `Car ${car.car}, consist ${car.consist || "-"}, ${car.status}, home yard ${car.homeYard}` : undefined}
                              className={`h-7 w-full rounded border px-1 text-center font-mono text-[12px] font-bold ${car ? "border-slate-400" : "border-dashed border-slate-300"} ${clicks.includes(key) ? "ring-2 ring-bart-600" : ""}`}>
                        {car?.label ?? ""}
                      </button>
                    </li>);
                })}
              </ol>
            </div>))}
        </div>
      </section>
    </>
  );
}

/** the pass values in the key of the page in front (egl:CODE?field=value) */
function useLinkParams(): URLSearchParams {
  const { stack } = useApp();
  const top = stack[stack.length - 1];
  const key = top?.kind === "screen" ? top.key : "";
  return new URLSearchParams(key.includes("?") ? key.slice(key.indexOf("?") + 1) : "");
}

export function ShopMovePage() {
  const app = useApp();
  const q = useLinkParams();
  const context: MoveContext = {
    carNo: q.get("carNo") ?? "", yrdTrkgYrd: q.get("yrdTrkgYrd") ?? "", yrdSubLoc: q.get("yrdSubLoc") ?? "", newYrdLoc: q.get("newYrdLoc") ?? "",
    carNo2: q.get("carNo2") ?? "", posnNoFrom: Number(q.get("posnNoFrom") ?? 0), posnNoTo: Number(q.get("posnNoTo") ?? 0), whichGui: q.get("whichGui") ?? "",
  };
  const ctxKey = JSON.stringify(context);
  const [view, setView] = useState<MoveView | null>(null);
  const [car, setCar] = useState("");
  const [reason, setReason] = useState("");
  const [location, setLocation] = useState("");
  const [compress, setCompress] = useState("");
  const [outcome, setOutcome] = useState<MoveOutcome | null>(null);
  const [error, setError] = useState("");

  const load = useCallback(async (typed?: string) => {
    setError(""); setOutcome(null);
    try {
      const v = await openMove(JSON.parse(ctxKey) as MoveContext, typed);
      setView(v); setCar(v.carNo); setReason(v.selectedReason ?? ""); setLocation(v.selectedLocation ?? "");
    } catch (e) { setError(message(e)); }
  }, [ctxKey]);
  useEffect(() => { void load(); }, [load]);

  const update = async (reasonUnchanged: boolean) => {
    setError("");
    try {
      setOutcome(await updateMove(context, { carNo: car !== view?.carNo ? car : undefined, reason, location, compress, reasonUnchanged }));
    } catch (e) { setError(message(e)); }
  };
  const q42 = outcome?.question;

  return (
    <>
      <PageHead title="Vehicle Shop Location Change" code="VHYT03I" />
      <section aria-label="Car move" className={panel}>
        <Note text={error} error />
        {view && <>
          <div className="mb-3 grid grid-cols-2 gap-3 sm:grid-cols-4">
            <label className="flex flex-col gap-1 text-[13px] font-semibold">Car No.
              <input className={field} value={car} maxLength={4} onChange={e => setCar(e.target.value)} onBlur={() => { if (car !== view.carNo) void load(car); }} />
            </label>
            <p className="text-[13px]"><b>Orientation</b><br />{view.orientation}</p>
            <p className="text-[13px]"><b>Status</b><br />{view.status}</p>
            <p className="text-[13px]"><b>Yard</b><br />{view.yard}</p>
            <p className="text-[13px]"><b>Shop Location</b><br />{view.location}</p>
            <p className="text-[13px]"><b>Reason Code</b><br />{view.currentReason}</p>
            <label className="flex flex-col gap-1 text-[13px] font-semibold">New Reason Code
              <select className={field} value={reason} onChange={e => setReason(e.target.value)}>
                <option value="" />
                {view.reasons.map(r => <option key={r.code} value={r.code}>{r.text}</option>)}
              </select>
            </label>
            <label className="flex flex-col gap-1 text-[13px] font-semibold">New Shop Location
              <select className={field} value={location} onChange={e => setLocation(e.target.value)}>
                <option value="" />
                {view.locations.map(l => <option key={l.code} value={l.code}>{l.text}</option>)}
              </select>
            </label>
          </div>
          {view.compressAsked && (
            <fieldset className="mb-3 text-[13px]"><legend className="font-semibold">{view.insertLabel.join(" ")}</legend>
              {["North", "South"].map(d => (
                <label key={d} className="mr-4"><input type="radio" name="compress" value={d} checked={compress === d.toUpperCase()} onChange={() => setCompress(d.toUpperCase())} /> {d}</label>))}
            </fieldset>)}
          <div className="flex flex-wrap gap-2">
            <button type="button" className={primary} onClick={() => void update(false)}>Update</button>
            <button type="button" className={button} onClick={() => void load()}>Clear</button>
            <button type="button" className={button} disabled={!view.carNo} onClick={() => app.open({ kind: "screen", key: `egl:VHSV06I?carNo=${view.carNo}`, label: "VHSV06I" })}>SV/HV</button>
            <button type="button" className={button} onClick={() => app.open({ kind: "screen", key: `egl:VHCI02I${query({ carNo: view.carNo, whichGuiFrom: "VHYT03I", yrdTrkgYrd: context.yrdTrkgYrd })}`, label: "VHCI02I" })}>CI - Service</button>
            <button type="button" className={button} onClick={() => app.open({ kind: "screen", key: `egl:VHCI03I${query({ carNo: view.carNo, whichGuiFrom: "VHYT03I", yrdTrkgYrd: context.yrdTrkgYrd })}`, label: "VHCI03I" })}>CI - Never Go</button>
          </div>
          <div className="mt-3"><Note text={outcome?.message ?? view.message} /></div>
          {q42 && (
            <div role="dialog" aria-label={q42.title} className="mt-3 rounded-xl border border-amber-300 bg-amber-50 p-3">
              <p className="mb-2">{q42.text}</p>
              <p className="mb-2 text-[13px]"><b>Reason Code</b> {q42.reason}</p>
              <button type="button" className={primary} onClick={() => void update(true)}>{q42.keepButton}</button>{" "}
              <button type="button" className={button} onClick={() => setOutcome(null)}>{q42.changeButton}</button>
            </div>)}
        </>}
        {!view && !error && <p role="status">Loading the car from MARIS…</p>}
      </section>
    </>
  );
}
