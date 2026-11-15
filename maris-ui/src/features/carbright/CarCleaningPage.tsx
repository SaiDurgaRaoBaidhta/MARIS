/* Car thorough cleaning report (old screens VHCBR1I and its printer selection VHCBR2I). */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { useApp } from "@/shared/lib/app-state";
import { homeYards, listCleaning, printCleaning, printers, type Cleaning, type Option, type Printer } from "./api";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";

export function CarCleaningPage() {
  const app = useApp();
  const [yards, setYards] = useState<string[]>([]);
  const [printerList, setPrinterList] = useState<Printer[]>([]);
  const [yard, setYard] = useState("");
  const [option, setOption] = useState<Option>("last");
  const [printer, setPrinter] = useState("");
  const [result, setResult] = useState<Cleaning | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    homeYards().then(setYards).catch(e => setError(e instanceof Error ? e.message : String(e)));
    printers().then(setPrinterList).catch(() => setPrinterList([]));
  }, []);

  const run = async (f: () => Promise<void>) => {
    setBusy(true); setError("");
    try { await f(); } catch (e) { setError(e instanceof Error ? e.message : String(e)); } finally { setBusy(false); }
  };
  const list = () => run(async () => { setResult(null); setResult(await listCleaning(yard, option)); });
  const print = () => run(async () => app.toast((await printCleaning(yard, option, printer)).message, "ok"));
  const clear = () => { setYard(""); setOption("last"); setResult(null); setError(""); };

  if (!yards.length) return <><PageHead title="Car Thorough Cleaning Report" code="VHCBR1I" />
    {error ? <p role="alert" className="m-2 rounded-lg bg-red-50 px-3 py-2 text-red-800">{error}</p> : <p role="status" className="p-8 text-slate-500">Loading…</p>}</>;
  return (
    <>
      <PageHead title="Car Thorough Cleaning Report" code="VHCBR1I" />
      <section aria-label="Criteria" className="mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark">
        <div className="flex flex-wrap items-end gap-4">
          <label className={label}>Home yard
            <select className={field} value={yard} onChange={e => setYard(e.target.value)}>
              <option value="">Choose a yard</option>
              {yards.map(y => <option key={y} value={y}>{y}</option>)}
            </select>
          </label>
          <fieldset className="flex flex-col gap-1 text-[13px]">
            <legend className="font-semibold text-slate-600 dark:text-[#c9d6e4]">Show</legend>
            <label><input type="radio" name="option" checked={option === "last"} onChange={() => setOption("last")} /> Last cleaning</label>
            <label><input type="radio" name="option" checked={option === "overdue"} onChange={() => setOption("overdue")} /> Cleaning over due</label>
          </fieldset>
          <label className={label}>Printer
            <select className={field} value={printer} onChange={e => setPrinter(e.target.value)}>
              <option value="">Choose a printer for Print</option>
              {printerList.map(p => <option key={p.vtam} value={p.vtam}>{p.description} ({p.vtam})</option>)}
            </select>
          </label>
          <button type="button" className={`${button} bg-bart-600 text-white`} disabled={busy || !yard} onClick={list}>List</button>
          <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} disabled={busy || !yard || !printer} onClick={print}>Print</button>
          <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} onClick={clear}>Clear</button>
        </div>
        {error && <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{error}</p>}
        {result?.message && <p className="mt-3 rounded-lg bg-amber-50 px-3 py-2 text-amber-900 dark:bg-amber-950/40 dark:text-amber-200">{result.message}</p>}
      </section>
      {result && result.count > 0 && (
        <section className="mx-2 mb-6">
          <p className="mb-2 text-sm text-slate-600 dark:text-[#c9d6e4]">{result.count} cars · cleaning date {result.cleaningDate} · over due before {result.overdueDate}</p>
          <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white dark:border-line-dark dark:bg-panel-dark">
            <table aria-label="Cars" className="w-full border-collapse text-[13px]">
              <thead><tr>{["Car", "Loc", "Stat", "Last thorough clean"].map(h => <th key={h} className="border-b border-slate-200 bg-slate-50 px-2 py-1 text-left dark:border-line-dark dark:bg-[#12263b]">{h}</th>)}</tr></thead>
              <tbody>{result.cars.map((c, i) => (
                <tr key={i} className="even:bg-slate-50 dark:even:bg-[#0f2032]">
                  <td className="px-2 py-1">{c.car}</td><td className="px-2 py-1">{c.location}</td>
                  <td className="px-2 py-1">{`${c.status} ${c.trainIncident}`.trim()}</td><td className="px-2 py-1">{c.lastThoroughClean}</td>
                </tr>))}
              </tbody>
            </table>
          </div>
        </section>)}
    </>
  );
}
