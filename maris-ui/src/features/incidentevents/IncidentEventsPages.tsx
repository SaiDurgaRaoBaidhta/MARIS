/* Event and incident log (old screens VHIE01I and its printer selection VHIE02I) and train or car look-up (VHIE06I). */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { useApp } from "@/shared/lib/app-state";
import { consistCars, consistOfCar, listIncidents, printIncidents, printers, revenueDate, type IncidentLog, type Printer } from "./api";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const errText = (e: unknown) => e instanceof Error ? e.message : String(e);
const Alert = ({ text }: { text: string }) => text ? <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{text}</p> : null;
const Note = ({ text }: { text?: string | null }) => text ? <p className="mt-3 rounded-lg bg-amber-50 px-3 py-2 text-amber-900 dark:bg-amber-950/40 dark:text-amber-200">{text}</p> : null;

export function IncidentLogPage() {
  const app = useApp();
  const [from, setFrom] = useState(""), [to, setTo] = useState("");
  const [printerList, setPrinterList] = useState<Printer[]>([]), [printer, setPrinter] = useState("");
  const [log, setLog] = useState<IncidentLog | null>(null);
  const [error, setError] = useState(""), [busy, setBusy] = useState(false);
  useEffect(() => {
    revenueDate().then(r => { setFrom(f => f || r.revenueDate); setTo(t => t || r.revenueDate); }).catch(e => setError(errText(e)));
    printers().then(setPrinterList).catch(() => setPrinterList([]));
  }, []);
  const run = async (f: () => Promise<void>) => { setBusy(true); setError(""); try { await f(); } catch (e) { setError(errText(e)); } finally { setBusy(false); } };
  return (
    <>
      <PageHead title="Event and Incident Log" code="VHIE01I" />
      <section aria-label="Criteria" className="mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark">
        <div className="flex flex-wrap items-end gap-4">
          <label className={label}>From<input type="date" className={field} value={from} onChange={e => setFrom(e.target.value)} /></label>
          <label className={label}>To<input type="date" className={field} value={to} onChange={e => setTo(e.target.value)} /></label>
          <button type="button" className={`${button} bg-bart-600 text-white`} disabled={busy} onClick={() => run(async () => { setLog(null); setLog(await listIncidents(from, to)); })}>List</button>
          <label className={label}>Printer<select className={field} value={printer} onChange={e => setPrinter(e.target.value)}>
            <option value="">Choose a printer for Print</option>{printerList.map(p => <option key={p.vtam} value={p.vtam}>{p.description} ({p.vtam})</option>)}</select></label>
          <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} disabled={busy || !printer}
            onClick={() => run(async () => app.toast((await printIncidents(from, to, printer)).message, "ok"))}>Print</button>
        </div>
        <Alert text={error} /><Note text={log?.message} />
      </section>
      {log && log.count > 0 && <div className="mx-2 mb-6 overflow-x-auto rounded-xl border border-slate-200 bg-white dark:border-line-dark dark:bg-panel-dark">
        <table aria-label="Incidents" className="w-full border-collapse text-[13px]">
          <thead><tr>{["Rev Date", "Incd No", "Car", "Incd Stat", "Symp", "Time", "Speed", "Dir", "Loc", "Con No", "Narrative"].map(h =>
            <th key={h} className="whitespace-nowrap border-b border-slate-200 bg-slate-50 px-2 py-1 text-left dark:border-line-dark dark:bg-[#12263b]">{h}</th>)}</tr></thead>
          <tbody>{log.incidents.map((i, n) => <tr key={n} className="even:bg-slate-50 dark:even:bg-[#0f2032]">
            {[i.revenueDate, i.incident, i.car, i.status, i.symptom, i.time, i.speed, i.direction, i.location, i.consist, i.narrative].map((v, k) => <td key={k} className="px-2 py-1">{v}</td>)}</tr>)}</tbody>
        </table></div>}
    </>
  );
}

export function ConsistLookupPage() {
  const [car, setCar] = useState(""), [consist, setConsist] = useState("");
  const [cars, setCars] = useState<string[]>([]);
  const [message, setMessage] = useState<string | null>(null), [error, setError] = useState(""), [busy, setBusy] = useState(false);
  const run = async (f: () => Promise<void>) => { setBusy(true); setError(""); setMessage(null); try { await f(); } catch (e) { setError(errText(e)); } finally { setBusy(false); } };
  return (
    <>
      <PageHead title="Train or Car Look Up" code="VHIE06I" />
      <section aria-label="Search" className="mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark">
        <div className="flex flex-wrap items-end gap-4">
          <label className={label}>Car No<input className={`${field} w-24`} maxLength={4} value={car} onChange={e => setCar(e.target.value)} /></label>
          <button type="button" className={`${button} bg-bart-600 text-white`} disabled={busy || !car}
            onClick={() => run(async () => { const r = await consistOfCar(car); setConsist(r.consist); setMessage(r.message); setCars([]); })}>Find Train</button>
          <label className={label}>Train No<input className={`${field} w-28`} maxLength={6} value={consist} onChange={e => setConsist(e.target.value)} /></label>
          <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} disabled={busy || !consist}
            onClick={() => run(async () => { const r = await consistCars(consist); setCars(r.cars); setMessage(r.message); if (!r.message) setConsist(r.consist); })}>Find Cars</button>
          <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} onClick={() => { setCar(""); setConsist(""); setCars([]); setMessage(null); setError(""); }}>Clear</button>
        </div>
        <Alert text={error} /><Note text={message} />
      </section>
      {cars.length > 0 && <ol aria-label="Cars" className="mx-2 mb-6 flex flex-wrap gap-2">
        {cars.map((c, i) => <li key={i} className="rounded-lg border border-slate-200 bg-white px-3 py-1 font-mono dark:border-line-dark dark:bg-panel-dark">{c}</li>)}</ol>}
    </>
  );
}
