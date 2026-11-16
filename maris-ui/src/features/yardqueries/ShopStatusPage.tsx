/* Shop status report (old screen VHYT25I), on the clean API: the shop's cars with their time in the shop (the old
 * report's arithmetic is the middleware's), the average, and the cars out of the yard. The old report was opened from
 * VHYT27I with a yard; here the yard can also be typed. */
import { useCallback, useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { fetchShopStatus, type ShopCar, type ShopStatus } from "./api";

const field = "h-9 w-24 rounded-lg border border-slate-300 bg-white px-2 uppercase text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const th = "whitespace-nowrap border-b border-slate-200 bg-slate-50 px-2 py-1 text-left dark:border-line-dark dark:bg-[#12263b]";
const td = "whitespace-nowrap px-2 py-1";

const time = (d?: number, h?: number, m?: number) => (d === undefined ? "" : `${d} days ${h} hrs` + (m === undefined ? "" : ` ${m} min`));
const tag = (c: ShopCar) => `${c.leftEnd}${c.car}${c.rightEnd}`;
const status = (c: ShopCar) => (c.incident ? `${c.status} TI` : c.status);

export function ShopStatusPage({ yard: given }: { yard?: string }) {
  const [yard, setYard] = useState(given ?? "");
  const [report, setReport] = useState<ShopStatus | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const show = useCallback(async (y: string) => {
    if (!y.trim()) return;
    setBusy(true); setError("");
    try { setReport(await fetchShopStatus(y.trim().toUpperCase())); }
    catch (e) { setReport(null); setError(e instanceof Error ? e.message : String(e)); }
    finally { setBusy(false); }
  }, []);
  useEffect(() => { if (given) void show(given); }, [given, show]);

  return (
    <>
      <PageHead title="Shop Status Report" code="VHYT25I" />
      <section aria-label="Report" className="mx-2 mb-6 flex flex-col gap-4">
        <div className="flex items-end gap-2">
          <label className="flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]">Yard
            <input className={field} maxLength={3} value={yard} onChange={e => setYard(e.target.value)} />
          </label>
          <button type="button" className="h-9 rounded-lg bg-bart-600 px-4 font-semibold text-white disabled:opacity-50" disabled={busy || !yard.trim()}
                  onClick={() => void show(yard)}>Show</button>
        </div>
        {error && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{error}</p>}
        {report && <>
          <p className="text-sm text-slate-600 dark:text-[#c9d6e4]">Run at {report.runTime || "-"} · {report.totalCars} cars · {report.grandTotal} in all</p>
          {report.averageDays !== undefined && <p className="font-semibold">Average time in the shop: {time(report.averageDays, report.averageHours)}</p>}
          <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white dark:border-line-dark dark:bg-panel-dark">
            <table aria-label={`Cars in ${report.shop}`} className="w-full border-collapse text-[12px]">
              <thead><tr>{["Track", "Pos", "Car", "Status", "Date in", "Time in", "Reason", "In the shop"].map(h => <th key={h} className={th}>{h}</th>)}</tr></thead>
              <tbody>{report.cars.map((c, i) => (
                <tr key={i} className="even:bg-slate-50 dark:even:bg-[#0f2032]">
                  <td className={td}>{c.track}</td><td className={td}>{c.position}</td><td className={`${td} font-mono`}>{tag(c)}</td>
                  <td className={td}>{status(c)}</td><td className={td}>{c.dateIn}</td><td className={td}>{c.timeIn}</td>
                  <td className={td}>{c.reason}</td><td className={td}>{time(c.elapsedDays, c.elapsedHours)}</td>
                </tr>))}
              </tbody>
            </table>
          </div>
          {report.outOfYard.averageDays !== undefined && <p className="font-semibold">Average time out of the yard: {time(report.outOfYard.averageDays, report.outOfYard.averageHours)}</p>}
          <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white dark:border-line-dark dark:bg-panel-dark">
            <table aria-label={`Cars out of the yard${report.outOfYard.transferTrack ? ` (${report.outOfYard.transferTrack})` : ""}`} className="w-full border-collapse text-[12px]">
              <thead><tr>{["From", "Track", "Pos", "Car", "Status", "Date", "Time", "Reason", "Elapsed"].map(h => <th key={h} className={th}>{h}</th>)}</tr></thead>
              <tbody>{report.outOfYard.cars.map((c, i) => (
                <tr key={i} className="even:bg-slate-50 dark:even:bg-[#0f2032]">
                  <td className={td}>{c.origin}</td><td className={td}>{c.track}</td><td className={td}>{c.position}</td>
                  <td className={`${td} font-mono`}>{tag(c)}</td><td className={td}>{status(c)}</td><td className={td}>{c.dateIn}</td>
                  <td className={td}>{c.timeIn}</td><td className={td}>{c.reason}</td><td className={td}>{time(c.elapsedDays, c.elapsedHours, c.elapsedMinutes)}</td>
                </tr>))}
              </tbody>
            </table>
          </div>
        </>}
      </section>
    </>
  );
}
