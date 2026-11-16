/* Consist log (old VHCL01I asks for the consists and revenue date, VHCL02I lists them 4 to a page): Display or Print. */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { useApp } from "@/shared/lib/app-state";
import { consistLog, consistLogOptions, printConsistLog, type ConsistLog, type LogPrinter } from "./consistLogApi";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const warn = "bg-yellow-200 text-slate-900 dark:bg-yellow-700/60 dark:text-white";
const message = (e: unknown) => (e instanceof Error ? e.message : String(e));
/** consists per page, as on VHCL02I */
const PAGE = 4;

export function ConsistLogPage() {
  const app = useApp();
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [date, setDate] = useState("");
  const [today, setToday] = useState("");
  const [printer, setPrinter] = useState("");
  const [printerList, setPrinterList] = useState<LogPrinter[]>([]);
  const [log, setLog] = useState<ConsistLog | null>(null);
  const [page, setPage] = useState(1);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    consistLogOptions().then(o => { setPrinterList(o.printers); setDate(o.revenueDate); setToday(o.revenueDate); }).catch(e => setError(message(e)));
  }, []);

  const run = async (f: () => Promise<void>) => {
    setBusy(true); setError("");
    try { await f(); } catch (e) { setError(message(e)); } finally { setBusy(false); }
  };
  const display = () => run(async () => { setLog(null); setPage(1); setLog(await consistLog(from.trim(), to.trim(), date)); });
  const print = () => run(async () => app.toast((await printConsistLog(from.trim(), to.trim(), date, printer)).message, "ok"));
  const clear = () => { setFrom(""); setTo(""); setDate(today); setPrinter(""); setLog(null); setError(""); };

  const shown = log ? log.consists.slice((page - 1) * PAGE, page * PAGE) : [];
  return (
    <>
      <PageHead title="Consist Log" code="VHCL01I" />
      <section aria-label="Criteria" className="mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark">
        <div className="flex flex-wrap items-end gap-4">
          <label className={label}>Consist No. From<input className={`${field} w-24`} value={from} maxLength={3} onChange={e => setFrom(e.target.value)} /></label>
          <label className={label}>Consist No. To<input className={`${field} w-24`} value={to} maxLength={3} onChange={e => setTo(e.target.value)} /></label>
          <label className={label}>Revenue Date<input className={field} type="date" value={date} onChange={e => setDate(e.target.value)} /></label>
          <button type="button" className={`${button} bg-bart-600 text-white`} disabled={busy} onClick={display}>Display</button>
          <label className={label}>Printer
            <select className={field} value={printer} onChange={e => setPrinter(e.target.value)}>
              <option value="">Choose a printer to Print</option>
              {printerList.map(p => <option key={p.vtamName} value={p.vtamName}>{p.name} ({p.vtamName})</option>)}
            </select>
          </label>
          <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} disabled={busy || !printer} onClick={print}>Print</button>
          <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} onClick={clear}>Clear</button>
        </div>
        {error && <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{error}</p>}
      </section>
      {log && (
        <section aria-label="Consists" className="mx-2 mb-6 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark">
          <p className="mb-2 text-sm">Rev Date {log.criteria.revenueDate} · Consist From {log.criteria.from} To {log.criteria.to} · Number of Consist: {log.consistCount}</p>
          {log.message && <p className="mb-3 rounded-lg bg-amber-50 px-3 py-2 text-amber-900 dark:bg-amber-950/40 dark:text-amber-200">{log.message}</p>}
          {shown.map(c => (
            <div key={c.consist} className="mb-3 overflow-x-auto rounded-lg border border-slate-200 p-2 dark:border-line-dark">
              <table className="text-sm">
                <thead><tr className="text-left">
                  {["Consist", "Origin", "Term", "In-Srv", "Out-Srv", "Run Time", "Swap", "Split", "Washed", "Length"].map(h => <th key={h} className="pr-4">{h}</th>)}
                </tr></thead>
                <tbody><tr>
                  <td className={`pr-4 font-semibold ${c.warning ? warn : ""}`}>{c.consist}</td><td className="pr-4">{c.origin}</td>
                  <td className="pr-4">{c.outOfServiceLocation}</td><td className="pr-4">{c.timeIn}</td><td className="pr-4">{c.timeOut}</td>
                  <td className="pr-4">{c.runTime}</td><td className="pr-4">{c.swap}</td><td className="pr-4">{c.split}</td><td className="pr-4">{c.washed}</td>
                  <td className="pr-4">{c.carCount}</td>
                </tr></tbody>
              </table>
              <table className="mt-1 text-sm" aria-label={`Cars of ${c.consist}`}>
                <tbody>
                  <tr><th className="pr-3 text-left">Cars</th>{c.cars.map((car, i) => <td key={i} className={`pr-3 font-mono ${car.warning ? warn : ""}`}>{car.shown}</td>)}</tr>
                  <tr><th className="pr-3 text-left">Home Yard</th>{c.cars.map((car, i) => <td key={i} className="pr-3">{car.homeYard}</td>)}</tr>
                </tbody>
              </table>
            </div>))}
          {log.pages > 1 && (
            <div className="flex items-center gap-3">
              <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} disabled={page <= 1} onClick={() => setPage(page - 1)}>Prv Page</button>
              <span>Page {page} of {log.pages}</span>
              <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} disabled={page >= log.pages} onClick={() => setPage(page + 1)}>Nxt Page</button>
            </div>)}
        </section>)}
    </>
  );
}
