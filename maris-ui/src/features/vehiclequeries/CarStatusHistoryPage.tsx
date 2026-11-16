/* Car status history (old VHCSH1I, with its printer window VHCSH2I folded into Print). */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { useApp } from "@/shared/lib/app-state";
import { carStatusHistory, printCarStatusHistory, printers, type History, type Printer } from "./api";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const message = (e: unknown) => (e instanceof Error ? e.message : String(e));
const HEAD = ["Loc", "Stat Date", "Time", "Hrs", "Min", "Stat New", "Stat Prv", "Incd No", "Symp", "Action", "DVS Impact",
  "Date Last Upd", "Time Last Upd", "Term Last Upd", "Date Maint Upd", "Time Maint Upd", "Term Maint Upd", "Func"];

export function CarStatusHistoryPage() {
  const app = useApp();
  const [car, setCar] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [printer, setPrinter] = useState("");
  const [printerList, setPrinterList] = useState<Printer[]>([]);
  const [result, setResult] = useState<History | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => { printers().then(setPrinterList).catch(() => setPrinterList([])); }, []);

  const run = async (f: () => Promise<void>) => {
    setBusy(true); setError("");
    try { await f(); } catch (e) { setError(message(e)); } finally { setBusy(false); }
  };
  const list = () => run(async () => { setResult(null); const h = await carStatusHistory(car.trim(), from, to); setResult(h); if (h.fromDate !== from) setFrom(h.fromDate); });
  const print = () => run(async () => app.toast((await printCarStatusHistory(car.trim(), from, to, printer)).message, "ok"));
  const clear = () => { setCar(""); setFrom(""); setTo(""); setResult(null); setError(""); };

  return (
    <>
      <PageHead title="Car Status History" code="VHCSH1I" />
      <section aria-label="Criteria" className="mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark">
        <div className="flex flex-wrap items-end gap-4">
          <label className={label}>Car No<input className={`${field} w-24`} value={car} maxLength={4} onChange={e => setCar(e.target.value)} /></label>
          <label className={label}>From<input className={field} type="date" value={from} onChange={e => setFrom(e.target.value)} /></label>
          <label className={label}>To<input className={field} type="date" value={to} onChange={e => setTo(e.target.value)} /></label>
          <button type="button" className={`${button} bg-bart-600 text-white`} disabled={busy} onClick={list}>List</button>
          <label className={label}>Printer
            <select className={field} value={printer} onChange={e => setPrinter(e.target.value)}>
              <option value="">Choose a printer for Print</option>
              {printerList.map(p => <option key={p.vtam} value={p.vtam}>{p.description} ({p.vtam})</option>)}
            </select>
          </label>
          <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} disabled={busy || !printer} onClick={print}>Print</button>
          <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} onClick={clear}>Clear</button>
        </div>
        {error && <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{error}</p>}
        {result?.message && <p className="mt-3 rounded-lg bg-amber-50 px-3 py-2 text-amber-900 dark:bg-amber-950/40 dark:text-amber-200">{result.message}</p>}
      </section>
      {result && result.changes.length > 0 && (
        <section className="mx-2 mb-6">
          <p className="mb-2 text-sm text-slate-600 dark:text-[#c9d6e4]">Car {result.car} · Home Yrd {result.homeYard} · {result.changes.length} changes{result.footer ? ` ${result.footer}` : ""}</p>
          <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white dark:border-line-dark dark:bg-panel-dark">
            <table aria-label="Status changes" className="w-full border-collapse text-[13px]">
              <thead><tr>{HEAD.map(h => <th key={h} className="whitespace-nowrap border-b border-slate-200 bg-slate-50 px-2 py-1 text-left dark:border-line-dark dark:bg-[#12263b]">{h}</th>)}</tr></thead>
              <tbody>{result.changes.map((c, i) => (
                <tr key={i} className="even:bg-slate-50 dark:even:bg-[#0f2032]">
                  {[c.location, c.date, c.time.slice(0, 5), c.hours, c.minutes, c.statusNew, c.statusPrevious, c.incident, c.symptom, c.action, c.dvsImpact,
                    c.lastUpdateDate, c.lastUpdateTime.slice(0, 5), c.lastUpdateTerminal, c.maintUpdateDate, c.maintUpdateTime.slice(0, 5), c.maintUpdateTerminal, c.function]
                    .map((v, j) => <td key={j} className="whitespace-nowrap px-2 py-1">{v}</td>)}
                </tr>))}
              </tbody>
            </table>
          </div>
        </section>)}
    </>
  );
}
