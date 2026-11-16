/* Make / break overrides (old VHYT34I): for the yard (passed in as yrdTrkgYrd) and a revenue date, cancel or re-activate
 * the AM / PM makes and breaks between two times (Make/Break Overrides), or override one train's arrival (Single Train
 * Override). The time lists, trains, statuses and messages are the old screen's, from the middleware. */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { openOverrides, saveOverrides, saveSingle, trainTimes, type OverridesState } from "./overridesApi";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg border border-slate-300 px-4 font-semibold disabled:opacity-50 dark:border-line-dark";
const panel = "mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark";
const message = (e: unknown) => (e instanceof Error ? e.message : String(e));
const param = (name: string) => new URLSearchParams(window.location.search).get(name) ?? "";

/** the four groups of the old window: their time lists (request names) and status field */
const GROUPS = [
  { name: "AM Makes", key: "amMake", status: "AM-MAKE-STAT" }, { name: "AM Breaks", key: "amBreak", status: "AM-BREAK-STAT" },
  { name: "PM Makes", key: "pmMake", status: "PM-MAKE-STAT" }, { name: "PM Breaks", key: "pmBreak", status: "PM-BREAK-STAT" },
] as const;

export function OverridesPage() {
  const yard = param("yrdTrkgYrd");
  const [state, setState] = useState<OverridesState | null>(null);
  const [date, setDate] = useState("");
  const [times, setTimes] = useState<Record<string, string>>({});
  const [boxes, setBoxes] = useState<Record<string, boolean>>({});
  const [train, setTrain] = useState("");
  const [arrivals, setArrivals] = useState<string[]>([]);
  const [arrival, setArrival] = useState("");
  const [scheduledFor, setScheduledFor] = useState("");
  const [single, setSingle] = useState({ breakCancel: false, makeCancel: false, breakScheduled: false, makeScheduled: false });
  const [error, setError] = useState("");

  const show = (s: OverridesState) => { setState(s); setDate(s.revenueDate); setTimes({}); setBoxes({}); };
  const run = async (f: () => Promise<void>) => { setError(""); try { await f(); } catch (e) { setError(message(e)); } };
  useEffect(() => { void run(async () => show(await openOverrides(yard))); }, []);   // eslint-disable-line react-hooks/exhaustive-deps

  const box = (key: string, text: string) => (
    <label className="flex items-center gap-1 text-sm"><input type="checkbox" checked={!!boxes[key]} onChange={e => setBoxes({ ...boxes, [key]: e.target.checked })} />{text}</label>);
  const pick = (key: string) => (
    <select aria-label={key} className={field} value={times[key] ?? ""} onChange={e => setTimes({ ...times, [key]: e.target.value })}>
      <option value="" />{state?.times[key]?.map(t => <option key={t} value={t}>{t}</option>)}
    </select>);

  return (
    <>
      <PageHead title="Make / Break Overrides" code="VHYT34I" />
      <section aria-label="Overrides" className={panel}>
        <div className="mb-3 flex flex-wrap items-end gap-3">
          <p className="text-sm">Yard <b>{state?.yard || yard}</b></p>
          <label className={label}>Revenue Date<input className={field} value={date} onChange={e => setDate(e.target.value)} /></label>
          <button type="button" className={button} onClick={() => void run(async () => show(await openOverrides(yard, date)))}>Retrieve</button>
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          {GROUPS.map(g => (
            <fieldset key={g.key} className="rounded-lg border border-slate-200 p-3 dark:border-line-dark">
              <legend className="px-1 text-sm font-semibold">{g.name} <span className="font-normal text-slate-500">{state?.statuses[g.status]}</span></legend>
              <div className="flex flex-wrap items-center gap-2">{pick(g.key + "From")}{pick(g.key + "To")}{box(g.key + "Cancel", "Cancel")}{box(g.key + "Reactivate", "Re-Activate")}</div>
            </fieldset>))}
        </div>
        <div className="mt-3 flex flex-wrap items-center gap-3">
          {box("allCancel", "Cancel all")}{box("allReactivate", "Re-activate all")}
          <button type="button" className={button} disabled={!Object.values(boxes).some(Boolean)}
                  onClick={() => void run(async () => show(await saveOverrides(yard, date, times, boxes)))}>Make/Break Overrides</button>
        </div>
      </section>
      <section aria-label="Single train" className={panel}>
        <div className="flex flex-wrap items-end gap-3">
          <label className={label}>Train
            <select className={field} value={train} onChange={e => { const t = e.target.value; setTrain(t); setArrival(""); setScheduledFor("");
              if (t) void run(async () => setArrivals((await trainTimes(yard, date, t)).arrivals)); }}>
              <option value="" />{state?.trains.map(t => <option key={t} value={t}>{t}</option>)}
            </select></label>
          <label className={label}>Arrival Time
            <select className={field} value={arrival} onChange={e => { const a = e.target.value; setArrival(a);
              if (a) void run(async () => setScheduledFor((await trainTimes(yard, date, train, a)).scheduledFor)); }}>
              <option value="" />{arrivals.map(a => <option key={a} value={a}>{a}</option>)}
            </select></label>
          <p className="text-sm">Scheduled For <b>{scheduledFor}</b></p>
          {(["breakCancel", "makeCancel", "breakScheduled", "makeScheduled"] as const).map(k => (
            <label key={k} className="flex items-center gap-1 text-sm"><input type="checkbox" checked={single[k]} onChange={e => setSingle({ ...single, [k]: e.target.checked })} />
              {{ breakCancel: "Cancel break", makeCancel: "Cancel make", breakScheduled: "Schedule break", makeScheduled: "Schedule make" }[k]}</label>))}
          <button type="button" className={button} disabled={!arrival}
                  onClick={() => void run(async () => show(await saveSingle(yard, date, { train, arrival, ...single })))}>Single Train Override</button>
        </div>
        <table className="mt-3 w-full text-left text-sm">
          <thead><tr>{["Train", "Arrival", "Break sched", "Break cancel", "Make sched", "Make cancel"].map(h => <th key={h}>{h}</th>)}</tr></thead>
          <tbody>{state?.singleTrains.map((s, i) => (
            <tr key={i}><td>{s.train}</td><td>{s.arrival}</td><td>{s.breakScheduled}</td><td>{s.breakCancelled}</td><td>{s.makeScheduled}</td><td>{s.makeCancelled}</td></tr>))}
          </tbody>
        </table>
      </section>
      {state?.message && <p role="status" className="mx-2 rounded-lg bg-slate-50 px-3 py-2 dark:bg-[#0a1624]">{state.message}</p>}
      {error && <p role="alert" className="mx-2 mt-2 rounded-lg bg-red-50 px-3 py-2 text-red-800">{error}</p>}
    </>);
}
