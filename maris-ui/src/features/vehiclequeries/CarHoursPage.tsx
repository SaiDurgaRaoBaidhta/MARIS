/* Car hours report (old VHCHR1I): a car's total revenue hours between two dates, List or Hard Copy. */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { useApp } from "@/shared/lib/app-state";
import { carHours, printCarHours, printers, type CarHours, type Printer } from "./api";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const message = (e: unknown) => (e instanceof Error ? e.message : String(e));

export function CarHoursPage() {
  const app = useApp();
  const [car, setCar] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [printer, setPrinter] = useState("");
  const [printerList, setPrinterList] = useState<Printer[]>([]);
  const [result, setResult] = useState<CarHours | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => { printers().then(setPrinterList).catch(() => setPrinterList([])); }, []);

  const run = async (f: () => Promise<void>) => {
    setBusy(true); setError("");
    try { await f(); } catch (e) { setError(message(e)); } finally { setBusy(false); }
  };
  const list = () => run(async () => { setResult(null); const h = await carHours(car.trim(), from, to); setResult(h); if (h.fromDate !== from) setFrom(h.fromDate); });
  const print = () => run(async () => app.toast((await printCarHours(car.trim(), from, to, printer)).message, "ok"));
  const clear = () => { setCar(""); setFrom(""); setTo(""); setPrinter(""); setResult(null); setError(""); };

  return (
    <>
      <PageHead title="Car Hours Report" code="VHCHR1I" />
      <section aria-label="Criteria" className="mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark">
        <div className="flex flex-wrap items-end gap-4">
          <label className={label}>Car No<input className={`${field} w-24`} value={car} maxLength={4} inputMode="numeric" onChange={e => setCar(e.target.value)} /></label>
          <label className={label}>Requested date from<input className={field} type="date" value={from} onChange={e => setFrom(e.target.value)} /></label>
          <label className={label}>Requested date to<input className={field} type="date" value={to} onChange={e => setTo(e.target.value)} /></label>
          <button type="button" className={`${button} bg-bart-600 text-white`} disabled={busy} onClick={list}>List</button>
          <label className={label}>Printer
            <select className={field} value={printer} onChange={e => setPrinter(e.target.value)}>
              <option value="">Choose a printer for Hard Copy</option>
              {printerList.map(p => <option key={p.vtam} value={p.vtam}>{p.description} ({p.vtam})</option>)}
            </select>
          </label>
          <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} disabled={busy || !printer} onClick={print}>Hard Copy</button>
          <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} onClick={clear}>Clear</button>
        </div>
        {error && <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{error}</p>}
        {result?.message && <p className="mt-3 rounded-lg bg-amber-50 px-3 py-2 text-amber-900 dark:bg-amber-950/40 dark:text-amber-200">{result.message}</p>}
      </section>
      {result && !result.message && (
        <section aria-label="Car hours" className="mx-2 mb-6 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark">
          <dl className="grid grid-cols-[max-content_1fr] gap-x-6 gap-y-1 text-sm">
            <dt className="font-semibold">Actual from date</dt><dd>{result.actualFrom}</dd>
            <dt className="font-semibold">Actual to date</dt><dd>{result.actualTo}</dd>
            <dt className="font-semibold">Total hours</dt><dd>{result.hours}:{String(result.minutes).padStart(2, "0")}</dd>
          </dl>
        </section>)}
    </>
  );
}
