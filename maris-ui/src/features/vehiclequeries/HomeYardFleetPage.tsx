/* Home yard fleet summary (old VHHF01I, with its printer window VHHF02I folded into Print). */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { useApp } from "@/shared/lib/app-state";
import { homeYardFleet, printHomeYardFleet, printers, type Fleet, type Printer } from "./api";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const cell = "whitespace-nowrap border-b border-slate-100 px-1.5 py-0.5 text-right dark:border-line-dark";
const message = (e: unknown) => (e instanceof Error ? e.message : String(e));

export function HomeYardFleetPage() {
  const app = useApp();
  const [fleet, setFleet] = useState<Fleet | null>(null);
  const [printerList, setPrinterList] = useState<Printer[]>([]);
  const [printer, setPrinter] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    homeYardFleet().then(setFleet).catch(e => setError(message(e)));
    printers().then(setPrinterList).catch(() => setPrinterList([]));
  }, []);

  const print = async () => {
    setBusy(true); setError("");
    try { app.toast((await printHomeYardFleet(printer)).message, "ok"); } catch (e) { setError(message(e)); } finally { setBusy(false); }
  };

  return (
    <>
      <PageHead title="Home Yard Fleet" code="VHHF01I" />
      <section aria-label="Print" className="mx-2 mb-4 flex flex-wrap items-end gap-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark">
        <label className={label}>Printer
          <select className={field} value={printer} onChange={e => setPrinter(e.target.value)}>
            <option value="">Choose a printer for Print</option>
            {printerList.map(p => <option key={p.vtam} value={p.vtam}>{p.description} ({p.vtam})</option>)}
          </select>
        </label>
        <button type="button" className={`${button} bg-bart-600 text-white`} disabled={busy || !printer} onClick={print}>Print</button>
      </section>
      {error && <p role="alert" className="mx-2 mb-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{error}</p>}
      {fleet?.message && <p className="mx-2 mb-3 rounded-lg bg-amber-50 px-3 py-2 text-amber-900 dark:bg-amber-950/40 dark:text-amber-200">{fleet.message}</p>}
      {!fleet && !error && <p role="status" className="p-8 text-slate-500">Loading…</p>}
      {fleet && fleet.rows.length === 0 && <p className="mx-2 text-slate-500">No cars in the home yards.</p>}
      {fleet && fleet.rows.length > 0 && (
        <div className="mx-2 mb-6 overflow-x-auto rounded-xl border border-slate-200 bg-white dark:border-line-dark dark:bg-panel-dark">
          <table aria-label="Home yard fleet" className="w-full border-collapse text-[12px]">
            <thead>
              <tr>
                <th rowSpan={2} className="border-b border-slate-200 bg-slate-50 px-2 py-1 text-left dark:border-line-dark dark:bg-[#12263b]">Car Type</th>
                {fleet.yards.map(y => <th key={y} colSpan={fleet.headings.length} className="border-b border-l border-slate-200 bg-slate-50 px-2 py-1 dark:border-line-dark dark:bg-[#12263b]">{y}</th>)}
              </tr>
              <tr>{fleet.yards.flatMap(y => fleet.headings.map((h, i) => (
                <th key={`${y}${i}`} className={`border-b border-slate-200 bg-slate-50 px-1.5 py-1 text-right dark:border-line-dark dark:bg-[#12263b] ${i === 0 ? "border-l" : ""}`}>{h}</th>)))}
              </tr>
            </thead>
            <tbody>{fleet.rows.map(r => (
              <tr key={r.row}>
                <th scope="row" className="whitespace-nowrap border-b border-slate-100 px-2 py-0.5 text-left font-medium dark:border-line-dark">{r.name}</th>
                {fleet.yards.flatMap(y => [...r.yards[y].byType, r.yards[y].total].map((n, i) => (
                  <td key={`${y}${i}`} className={`${cell} ${i === 0 ? "border-l" : ""} ${i === fleet.types.length ? "font-semibold" : ""}`}>{r.name ? n : ""}</td>)))}
              </tr>))}
            </tbody>
          </table>
        </div>)}
    </>
  );
}
