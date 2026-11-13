/* The yard history reports (old VHYT29I car location history, VHYT33I yard vehicle status history), on the clean API.
 * The old reports were opened from VHYT27I with a yard, a date range and (car history) a car range; the middleware checks
 * the dates as VHYT27I did. As the old reports, a car is named once for its run of rows (and a date once, on VHYT33I). */
import { useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { fetchCarHistory, fetchStatusHistory, type History, type HistoryCriteria, type Move, type StatusChange } from "./api";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const th = "whitespace-nowrap border-b border-slate-200 bg-slate-50 px-2 py-1 text-left dark:border-line-dark dark:bg-[#12263b]";
const td = "whitespace-nowrap px-2 py-1";

/** a value shown only where it changes from the row before, as the old reports print the car (and date) */
const once = <R,>(rows: R[], i: number, key: (r: R) => string) => (i > 0 && key(rows[i - 1]) === key(rows[i]) ? "" : key(rows[i]));

export function HistoryPage({ kind }: { kind: "cars" | "status" }) {
  const cars = kind === "cars";
  const [c, setC] = useState<HistoryCriteria>({ yard: "", from: "", to: "", fromCar: "", toCar: "" });
  const [moves, setMoves] = useState<History<Move> | null>(null);
  const [changes, setChanges] = useState<History<StatusChange> | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const set = (k: keyof HistoryCriteria, v: string) => setC(x => ({ ...x, [k]: v }));
  const show = async () => {
    setBusy(true); setError("");
    const q = { ...c, yard: c.yard.trim().toUpperCase() };
    try { if (cars) setMoves(await fetchCarHistory(q)); else setChanges(await fetchStatusHistory(q)); }
    catch (e) { setMoves(null); setChanges(null); setError(e instanceof Error ? e.message : String(e)); }
    finally { setBusy(false); }
  };
  const report = cars ? moves : changes;

  return (
    <>
      <PageHead title={cars ? "Car Location History" : "Yard Vehicle Status History"} code={cars ? "VHYT29I" : "VHYT33I"} />
      <section aria-label="Criteria" className="mx-2 mb-4 flex flex-wrap items-end gap-3 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark">
        <label className={label}>Yard<input className={`${field} w-20 uppercase`} maxLength={3} value={c.yard} onChange={e => set("yard", e.target.value)} /></label>
        <label className={label}>From date<input type="date" className={field} value={c.from} onChange={e => set("from", e.target.value)} /></label>
        <label className={label}>To date<input type="date" className={field} value={c.to} onChange={e => set("to", e.target.value)} /></label>
        {cars && <>
          <label className={label}>From car<input className={`${field} w-24`} maxLength={4} value={c.fromCar} onChange={e => set("fromCar", e.target.value)} /></label>
          <label className={label}>To car<input className={`${field} w-24`} maxLength={4} value={c.toCar} onChange={e => set("toCar", e.target.value)} /></label>
        </>}
        <button type="button" className="h-9 rounded-lg bg-bart-600 px-4 font-semibold text-white disabled:opacity-50" disabled={busy} onClick={() => void show()}>Show</button>
      </section>
      {error && <p role="alert" className="mx-2 mb-4 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{error}</p>}
      {report && (
        <section aria-label="Report" className="mx-2 mb-6">
          <p className="mb-2 text-sm text-slate-600 dark:text-[#c9d6e4]">
            {report.rows.length} rows for {report.yard}, {report.from} to {report.to}
            {report.maxRowsReached && <strong className="ml-2 text-red-700">The report shows the most rows it can (324); narrow the dates.</strong>}
          </p>
          <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white dark:border-line-dark dark:bg-panel-dark">
            {cars && moves && (
              <table aria-label="Car location history" className="w-full border-collapse text-[12px]">
                <thead><tr>{["Car", "Track", "Pos", "Status", "Or", "Date moved", "Time", "From", "From pos", "Reason", "Code"].map(h => <th key={h} className={th}>{h}</th>)}</tr></thead>
                <tbody>{moves.rows.map((m, i) => (
                  <tr key={i} className="even:bg-slate-50 dark:even:bg-[#0f2032]">
                    <td className={`${td} font-semibold`}>{once(moves.rows, i, r => r.car)}</td><td className={td}>{m.track}</td><td className={td}>{m.position}</td>
                    <td className={td}>{m.status}</td><td className={td}>{m.orientation}</td><td className={td}>{m.dateMoved}</td><td className={td}>{m.timeMoved}</td>
                    <td className={td}>{m.previousTrack}</td><td className={td}>{m.previousPosition || ""}</td><td className={td}>{m.reason}</td><td className={td}>{m.reasonCode}</td>
                  </tr>))}
                </tbody>
              </table>)}
            {!cars && changes && (
              <table aria-label="Vehicle status history" className="w-full border-collapse text-[12px]">
                <thead><tr>{["Car", "Date", "Time", "Track", "New status", "Previous", "Reason", "Code"].map(h => <th key={h} className={th}>{h}</th>)}</tr></thead>
                <tbody>{changes.rows.map((s, i) => (
                  <tr key={i} className="even:bg-slate-50 dark:even:bg-[#0f2032]">
                    <td className={`${td} font-semibold`}>{once(changes.rows, i, r => r.car)}</td><td className={td}>{once(changes.rows, i, r => r.car + r.dateChanged) && s.dateChanged}</td>
                    <td className={td}>{s.timeChanged}</td><td className={td}>{s.track}</td><td className={td}>{s.newStatus}</td><td className={td}>{s.previousStatus}</td>
                    <td className={td}>{s.reason}</td><td className={td}>{s.reasonCode}</td>
                  </tr>))}
                </tbody>
              </table>)}
          </div>
        </section>)}
    </>
  );
}
