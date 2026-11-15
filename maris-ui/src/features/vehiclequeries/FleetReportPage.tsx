/* Fleet report (old VHFR01I): all cars by type and state, the available GO cars by yard, and Hard Copy. */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { useApp } from "@/shared/lib/app-state";
import { fleetReport, printFleetReport, printers, type FleetReport, type FleetReportCells, type Printer } from "./api";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const head = "border-b border-slate-200 bg-slate-50 px-2 py-1 text-right dark:border-line-dark dark:bg-[#12263b]";
const message = (e: unknown) => (e instanceof Error ? e.message : String(e));

function Table({ name, first, headings, rows }: { name: string; first: string; headings: string[]; rows: { label: string; cells: FleetReportCells }[] }) {
  return (
    <div className="mx-2 mb-6 overflow-x-auto rounded-xl border border-slate-200 bg-white dark:border-line-dark dark:bg-panel-dark">
      <table aria-label={name} className="w-full border-collapse text-[13px]">
        <thead><tr><th className={`${head} text-left`}>{first}</th>{headings.map((h, i) => <th key={i} className={head}>{h}</th>)}</tr></thead>
        <tbody>{rows.map((r, i) => (
          <tr key={i}>
            <th scope="row" className="whitespace-nowrap border-b border-slate-100 px-2 py-0.5 text-left font-medium dark:border-line-dark">{r.label}</th>
            {[...r.cells.byType, r.cells.total].map((n, j) => (
              <td key={j} className={`border-b border-slate-100 px-2 py-0.5 text-right dark:border-line-dark ${j === r.cells.byType.length ? "font-semibold" : ""}`}>{r.label ? n : ""}</td>))}
          </tr>))}
        </tbody>
      </table>
    </div>
  );
}

export function FleetReportPage() {
  const app = useApp();
  const [report, setReport] = useState<FleetReport | null>(null);
  const [printerList, setPrinterList] = useState<Printer[]>([]);
  const [printer, setPrinter] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    fleetReport().then(setReport).catch(e => setError(message(e)));
    printers().then(setPrinterList).catch(() => setPrinterList([]));
  }, []);

  const print = async () => {
    setBusy(true); setError("");
    try { app.toast((await printFleetReport(printer)).message, "ok"); } catch (e) { setError(message(e)); } finally { setBusy(false); }
  };

  return (
    <>
      <PageHead title="Fleet Report" code="VHFR01I" />
      <section aria-label="Print" className="mx-2 mb-4 flex flex-wrap items-end gap-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark">
        <label className={label}>Printer
          <select className={field} value={printer} onChange={e => setPrinter(e.target.value)}>
            <option value="">Choose a printer for Hard Copy</option>
            {printerList.map(p => <option key={p.vtam} value={p.vtam}>{p.description} ({p.vtam})</option>)}
          </select>
        </label>
        <button type="button" className="h-9 rounded-lg bg-bart-600 px-4 font-semibold text-white disabled:opacity-50" disabled={busy || !printer} onClick={print}>Hard Copy</button>
      </section>
      {error && <p role="alert" className="mx-2 mb-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{error}</p>}
      {report?.message && <p className="mx-2 mb-3 rounded-lg bg-amber-50 px-3 py-2 text-amber-900 dark:bg-amber-950/40 dark:text-amber-200">{report.message}</p>}
      {!report && !error && <p role="status" className="p-8 text-slate-500">Loading…</p>}
      {report && report.rows.length === 0 && !report.message && <p className="mx-2 text-slate-500">No cars found.</p>}
      {report && report.rows.length > 0 && <>
        <Table name="Fleet" first="Car Type" headings={report.headings} rows={report.rows.map(r => ({ label: r.name, cells: r.cells }))} />
        <h2 className="mx-2 mb-2 text-sm font-semibold text-slate-700 dark:text-[#c9d6e4]">Available GO cars in yards</h2>
        <Table name="Available GO cars by yard" first="Yards" headings={report.headings} rows={report.goCarsByYard.map(r => ({ label: r.yard, cells: r.cells }))} />
      </>}
    </>
  );
}
