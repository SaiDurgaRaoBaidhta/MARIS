/* Yard tracking pages on the clean API:
 * - ShopActivityPage (old VHYT31I): a yard's shop arrivals / releases for a day, its totals; Print opens the shop
 *   activity report (VHYT23I) on a printer.
 * - CarHistoryPrintPage (old VHYT22I, opened by VHSV06I): a car's history report on a printer.
 * - PseudoCorrectionPage (old VHYT52I): the in-service trains of a dispatch location; Retrieve Consist asks for the
 *   selected train's cars back into the pseudo track.
 * - ForceClearPage (old VHYT54I): force clear an in-service train to a location.
 * Messages are the old screens' own, from the middleware. */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import {
  activityList, activityOptions, forceClear, printActivity, printCarHistory, printers, pseudoTrains, retrieveTrain,
  type Activity, type ActivityOptions, type Printer, type Trains,
} from "./miscApi";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg border border-slate-300 px-4 font-semibold disabled:opacity-50 dark:border-line-dark";
const panel = "mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark";
const message = (e: unknown) => (e instanceof Error ? e.message : String(e));
const param = (name: string) => new URLSearchParams(window.location.search).get(name) ?? "";

function Note({ text, error }: { text: string; error?: boolean }) {
  if (!text) return null;
  return <p role={error ? "alert" : "status"} className={`mt-3 rounded-lg px-3 py-2 ${error ? "bg-red-50 text-red-800" : "bg-slate-50 text-slate-800 dark:bg-[#0a1624] dark:text-ink-dark"}`}>{text}</p>;
}

/** the printer list of a print window and its Hard Copy */
function PrintPanel({ onPrint }: { onPrint: (printer: number) => Promise<unknown> }) {
  const [list, setList] = useState<Printer[]>([]);
  const [chosen, setChosen] = useState(0);
  const [note, setNote] = useState("");
  const [error, setError] = useState("");
  useEffect(() => { printers().then(setList).catch(e => setError(message(e))); }, []);
  const print = async () => {
    setError(""); setNote("");
    try { await onPrint(chosen); setNote(`Sent to ${list.find(p => p.index === chosen)?.name ?? ""}.`); } catch (e) { setError(message(e)); }
  };
  return (
    <section aria-label="Printers" className={panel}>
      <ul className="mb-3 flex flex-col gap-1">
        {list.map(p => (
          <li key={p.index}>
            <label className="flex items-center gap-2"><input type="radio" name="printer" checked={chosen === p.index} onChange={() => setChosen(p.index)} />
              <span className="font-mono">{p.name}</span> <span className="text-slate-500">{p.description}</span></label>
          </li>))}
      </ul>
      <button type="button" className={button} disabled={chosen === 0} onClick={() => void print()}>Hard Copy</button>
      <Note text={note} /><Note text={error} error />
    </section>);
}

export function ShopActivityPage() {
  const [options, setOptions] = useState<ActivityOptions | null>(null);
  const [yard, setYard] = useState("");
  const [date, setDate] = useState("");
  const [data, setData] = useState<Activity | null>(null);
  const [error, setError] = useState("");
  const [printing, setPrinting] = useState(false);
  useEffect(() => {
    activityOptions(param("yrdTrkgYrd")).then(o => { setOptions(o); setYard(o.yard); setDate(o.date); }).catch(e => setError(message(e)));
  }, []);
  const list = async () => {
    setError(""); setPrinting(false);
    try { setData(await activityList(yard, date)); } catch (e) { setError(message(e)); }
  };
  const t = data?.totals;
  return (
    <>
      <PageHead title="Shop Activity" code="VHYT31I" />
      <section aria-label="Criteria" className={panel}>
        <div className="flex flex-wrap items-end gap-3">
          <label className={label}>Yard being tracked
            <select className={field} value={yard} onChange={e => setYard(e.target.value)}>
              {options?.yards.map(y => <option key={y.code} value={y.code}>{y.code} {y.name}</option>)}
            </select></label>
          <label className={label}>For Date<input className={field} value={date} onChange={e => setDate(e.target.value)} /></label>
          <button type="button" className={button} disabled={!yard} onClick={() => void list()}>List</button>
          <button type="button" className={button} disabled={!data?.printable} onClick={() => setPrinting(true)}>Print</button>
        </div>
        <Note text={data?.message ?? ""} /><Note text={error} error />
      </section>
      {t && (
        <section aria-label="Totals" className={panel}>
          <dl className="grid grid-cols-2 gap-2 sm:grid-cols-3">
            {([["Arrivals", t.arrivals], ["Releases", t.releases], ["Departures", t.departures], ["Production", t.production],
               ["Production gain / loss", t.productionGainLoss], ["Availability gain / loss", t.availabilityGainLoss],
               ["04:00 availability", t.available0400], ["Remaining", t.remaining], ["Remaining (GO/USABLE)", t.remaining3]] as const).map(([k, v]) => (
              <div key={k}><dt className="text-[13px] text-slate-500">{k}</dt><dd className="font-semibold">{v}</dd></div>))}
          </dl>
        </section>)}
      {data && data.cars.length > 0 && (
        <section aria-label="Cars" className={panel}>
          <table className="w-full text-left text-sm">
            <thead><tr>{["Car", "Available", "Status", "Track", "Pos", "In shop", "Out shop", "Move reason"].map(h => <th key={h}>{h}</th>)}</tr></thead>
            <tbody>{data.cars.map((c, i) => (
              <tr key={i}><td className="font-mono">{c.car}</td><td>{c.availabilityDate} {c.availabilityTime}</td><td>{c.status}</td><td>{c.track}</td>
                <td>{c.position || ""}</td><td>{c.inShopDate} {c.inShopTime}</td><td>{c.outShopDate} {c.outShopTime}</td><td>{c.moveReason}</td></tr>))}
            </tbody>
          </table>
        </section>)}
      {printing && <PrintPanel onPrint={p => printActivity(yard, data?.date ?? date, p)} />}
    </>);
}

export function CarHistoryPrintPage() {
  const [car, setCar] = useState(param("carNo"));
  return (
    <>
      <PageHead title="Car History Report" code="VHYT22I" />
      <section aria-label="Car" className={panel}>
        <label className={label}>Car No<input className={field} value={car} maxLength={4} onChange={e => setCar(e.target.value)} /></label>
      </section>
      <PrintPanel onPrint={p => printCarHistory(car, p)} />
    </>);
}

export function PseudoCorrectionPage() {
  const yard = param("yrdTrkgYrd");
  const whichGui = param("whichGui");
  const [data, setData] = useState<Trains | null>(null);
  const [location, setLocation] = useState("");
  const [row, setRow] = useState(0);
  const [note, setNote] = useState("");
  const [error, setError] = useState("");
  const load = async (loc = "") => {
    setError(""); setRow(0);
    try { const t = await pseudoTrains(yard, whichGui, loc); setData(t); setNote(t.message); } catch (e) { setError(message(e)); }
  };
  useEffect(() => { void load(); }, []);    // eslint-disable-line react-hooks/exhaustive-deps
  const retrieve = async () => {
    setError("");
    try { const r = await retrieveTrain(yard, whichGui, location, row); setNote(r.message); setRow(0); } catch (e) { setError(message(e)); }
  };
  return (
    <>
      <PageHead title="Pseudo Correction" code="VHYT52I" />
      <section aria-label="Trains" className={panel}>
        <div className="mb-3 flex flex-wrap items-end gap-3">
          {data?.yardChoosable && <>
            <label className={label}>Dispatch Location<input className={field} value={location} maxLength={4} onChange={e => setLocation(e.target.value.toUpperCase())} /></label>
            <button type="button" className={button} disabled={!location} onClick={() => void load(location)}>List</button>
          </>}
          <button type="button" className={button} onClick={() => void load(location)}>Refresh</button>
          <button type="button" className={button} disabled={row === 0} onClick={() => void retrieve()}>Retrieve Consist</button>
        </div>
        <table className="w-full text-left text-sm">
          <thead><tr><th>Consists</th>{Array.from({ length: 10 }, (_, i) => <th key={i}>Car {i + 1}</th>)}</tr></thead>
          <tbody>{data?.trains.map((t, i) => (
            <tr key={t.consist} aria-selected={row === i + 1} onClick={() => setRow(i + 1)} className={`cursor-pointer ${row === i + 1 ? "bg-bart-50" : ""}`}>
              <td className="font-mono">{t.consist}</td>{Array.from({ length: 10 }, (_, k) => <td key={k} className="font-mono">{t.cars[k] ?? ""}</td>)}
            </tr>))}
          </tbody>
        </table>
        <Note text={note} /><Note text={error} error />
      </section>
    </>);
}

export function ForceClearPage() {
  const [train, setTrain] = useState("");
  const [location, setLocation] = useState("");
  const [note, setNote] = useState("");
  const [error, setError] = useState("");
  const clear = async () => {
    setError("");
    try { setNote((await forceClear(train, location)).message); } catch (e) { setError(message(e)); }
  };
  return (
    <>
      <PageHead title="Force Clear Train" code="VHYT54I" />
      <section aria-label="Force clear" className={panel}>
        <div className="flex flex-wrap items-end gap-3">
          <label className={label}>In Service Train<input className={field} value={train} maxLength={3} onChange={e => setTrain(e.target.value)} /></label>
          <label className={label}>Clear to Location<input className={field} value={location} maxLength={3} onChange={e => setLocation(e.target.value)} /></label>
          <button type="button" className={button} disabled={!location} onClick={() => void clear()}>Force Clear</button>
          <button type="button" className={button} onClick={() => { setTrain(""); setLocation(""); setNote(""); }}>Clear</button>
        </div>
        <Note text={note} /><Note text={error} error />
      </section>
    </>);
}
