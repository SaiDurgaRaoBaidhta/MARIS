/* Daily vehicle status (old VHDVS1I with its Next pages VHDVS2I, VHDVS3I and its printer window VHDVS4I). */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { useApp } from "@/shared/lib/app-state";
import { dailyStatus, dailyStatusPrinters, printDailyStatus, type DailyStatus, type LogPrinter, type StatusTable } from "./consistLogApi";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const message = (e: unknown) => (e instanceof Error ? e.message : String(e));

function Table({ t }: { t: StatusTable }) {
  return (
    <div className="mb-4 overflow-x-auto">
      <table aria-label={t.title || t.screen} className="text-xs">
        <thead><tr>{t.columns.map((c, i) => <th key={i} className="border-b border-slate-200 px-1 text-left dark:border-line-dark" title={c.field}>{c.heading}</th>)}</tr></thead>
        <tbody>{t.rows.map((r, i) => (
          <tr key={i}>{t.columns.map((c, j) => <td key={j} className={`px-1 ${j === 0 ? "font-semibold" : "text-right"}`}>{r[c.field] ?? ""}</td>)}</tr>))}
        </tbody>
      </table>
    </div>
  );
}

export function DailyStatusPage() {
  const app = useApp();
  const [report, setReport] = useState<DailyStatus | null>(null);
  const [date, setDate] = useState("");
  const [printer, setPrinter] = useState("");
  const [printerList, setPrinterList] = useState<LogPrinter[]>([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const run = async (f: () => Promise<void>) => {
    setBusy(true); setError("");
    try { await f(); } catch (e) { setError(message(e)); } finally { setBusy(false); }
  };
  useEffect(() => {
    void run(async () => setReport(await dailyStatus()));
    dailyStatusPrinters().then(setPrinterList).catch(() => setPrinterList([]));
  }, []);

  const retrieve = () => run(async () => setReport(await dailyStatus(date || undefined)));
  const print = () => run(async () => app.toast((await printDailyStatus(date, printer)).message, "ok"));
  const g = report?.gains;
  return (
    <>
      <PageHead title="Daily Vehicle Status" code="VHDVS1I" />
      <section aria-label="Criteria" className="mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark">
        <div className="flex flex-wrap items-end gap-4">
          <p className="text-sm">Valid As Of: {report?.validAsOf} · Revenue Date: {report?.revenueDate}</p>
          <label className={label}>Revenue Date<input className={field} type="date" value={date} onChange={e => setDate(e.target.value)} /></label>
          <button type="button" className={`${button} bg-bart-600 text-white`} disabled={busy} onClick={retrieve}>Retrieve Data</button>
          <label className={label}>Printer
            <select className={field} value={printer} onChange={e => setPrinter(e.target.value)}>
              <option value="">Choose a printer for Hard Copy</option>
              {printerList.map(p => <option key={p.vtamName} value={p.vtamName}>{p.name} ({p.vtamName})</option>)}
            </select>
          </label>
          <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} disabled={busy || !printer} onClick={print}>Hard Copy</button>
        </div>
        {error && <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{error}</p>}
      </section>
      {report && ["VHDVS1I", "VHDVS2I", "VHDVS3I"].map(s => (
        <section key={s} aria-label={s} className="mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark">
          {report.tables.filter(t => t.screen === s).map((t, i) => <Table key={i} t={t} />)}
          {s === "VHDVS2I" && g && (
            <p className="text-sm">Gain/Loss: Concord {g.concord} · Daly City {g.dalyCity} · Hayward {g.hayward} · Richmond {g.richmond} · Availability {g.total}</p>)}
        </section>))}
    </>
  );
}
