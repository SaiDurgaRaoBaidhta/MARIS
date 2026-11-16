/* The yard reports criteria window (old screen VHYT27I), on the clean API: printers, yards and MARIS's proposed dates;
 * Display shows the report below; Hard Copy prints it. The old screen's rules: dates for the history and out of service
 * reports, a car range for car history, no yard for the 04XX report, the 0400 and 04XX reports are printed only, the out
 * of service reports have no hard copy, Hard Copy needs a printer. Display opens the yard location report (VHYT26I) and the
 * out of service detail / summary reports (VHYT32I) below, as the old window opened them. */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { fetchReportOptions, printReport, type PrintRequest, type ReportOptions } from "./api";
import { HistoryPage } from "./HistoryPage";
import { OutOfServicePage } from "./OutOfServicePage";
import { ShopStatusPage } from "./ShopStatusPage";
import { YardLocationPage } from "./YardLocationPage";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 disabled:bg-slate-100 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";

/** the menu's report codes (WHICH-REPORT) and what the old window offers for each */
const REPORTS: { code: string; name: string; dates: boolean; display: boolean; print: boolean }[] = [
  { code: "SHOPSTAT", name: "Shop status", dates: false, display: true, print: true },
  { code: "YARD-LOC", name: "Yard location", dates: false, display: true, print: true },
  { code: "CAR-HIST", name: "Car location history", dates: true, display: true, print: true },
  { code: "YARDHIST", name: "Yard vehicle status history", dates: true, display: true, print: true },
  { code: "0400-RPT", name: "0400 report", dates: true, display: false, print: true },
  { code: "OSDETAIL", name: "Out of service detail", dates: true, display: true, print: false },
  { code: "OOSSUM", name: "Out of service summary", dates: true, display: true, print: false },
  { code: "04XX-RPT", name: "04XX report", dates: true, display: false, print: true },
];

export function ReportsPage() {
  const [options, setOptions] = useState<ReportOptions | null>(null);
  const [r, setR] = useState<PrintRequest>({ report: "", yard: "", printer: "", from: "", to: "", fromCar: "", toCar: "" });
  const [shown, setShown] = useState<PrintRequest | null>(null);
  const [done, setDone] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    fetchReportOptions().then(o => { setOptions(o); setR(x => ({ ...x, from: o.from, to: o.to })); }).catch(e => setError(e instanceof Error ? e.message : String(e)));
  }, []);
  const set = (k: keyof PrintRequest, v: string) => { setR(x => ({ ...x, [k]: v })); setDone(""); };
  const report = REPORTS.find(x => x.code === r.report);
  const hardCopy = async () => {
    setBusy(true); setError(""); setDone("");
    try { setDone((await printReport(r)).message); } catch (e) { setError(e instanceof Error ? e.message : String(e)); } finally { setBusy(false); }
  };

  return (
    <>
      <PageHead title="Yard Reports" code="VHYT27I" />
      <section aria-label="Report criteria" className="mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark">
        {!options && !error && <p className="text-slate-500">Loading the report options from MARIS…</p>}
        {options?.warnings.map(w => <p key={w} className="mb-3 rounded-lg bg-amber-50 px-3 py-2 text-amber-900">{w}</p>)}
        {options && <div className="grid gap-4 md:grid-cols-3">
          <label className={label}>Report
            <select className={field} value={r.report} onChange={e => { set("report", e.target.value); setShown(null); }}>
              <option value="">Choose a report</option>
              {REPORTS.map(x => <option key={x.code} value={x.code}>{x.name}</option>)}
            </select>
          </label>
          {r.report !== "04XX-RPT" && <label className={label}>Yard
            <select className={field} value={r.yard} onChange={e => set("yard", e.target.value)}>
              <option value="">Hayward (HY)</option>
              {options.yards.map(y => <option key={y.code} value={y.code}>{y.code} – {y.description}</option>)}
            </select>
          </label>}
          <label className={label}>Printer
            <select className={field} value={r.printer} onChange={e => set("printer", e.target.value)}>
              <option value="">Choose a printer for Hard Copy</option>
              {options.printers.map(p => <option key={p.vtam} value={p.vtam}>{p.description} ({p.vtam})</option>)}
            </select>
          </label>
          {report?.dates && <>
            <label className={label}>From date<input type="date" className={field} value={r.from} onChange={e => set("from", e.target.value)} /></label>
            <label className={label}>To date<input type="date" className={field} value={r.to} onChange={e => set("to", e.target.value)} /></label>
          </>}
          {r.report === "CAR-HIST" && <>
            <label className={label}>From car<input className={field} maxLength={4} value={r.fromCar} onChange={e => set("fromCar", e.target.value)} /></label>
            <label className={label}>To car<input className={field} maxLength={4} value={r.toCar} onChange={e => set("toCar", e.target.value)} /></label>
          </>}
        </div>}
        <div className="mt-4 flex gap-2">
          <button type="button" className={`${button} bg-bart-600 text-white`} disabled={!report?.display} onClick={() => setShown({ ...r })}>Display</button>
          <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} disabled={busy || !report?.print || !r.printer} onClick={() => void hardCopy()}>Hard Copy</button>
        </div>
        {done && <p role="status" className="mt-3 rounded-lg bg-emerald-50 px-3 py-2 text-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-200">{done}</p>}
        {error && <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{error}</p>}
      </section>
      {shown?.report === "SHOPSTAT" && <ShopStatusPage key={JSON.stringify(shown)} yard={shown.yard || "HY"} />}
      {shown?.report === "YARD-LOC" && <YardLocationPage key={JSON.stringify(shown)} yard={shown.yard} />}
      {(shown?.report === "OSDETAIL" || shown?.report === "OOSSUM") &&
        <OutOfServicePage key={JSON.stringify(shown)} yard={shown.yard} from={shown.from} to={shown.to} summary={shown.report === "OOSSUM"} />}
      {(shown?.report === "CAR-HIST" || shown?.report === "YARDHIST") &&
        <HistoryPage key={JSON.stringify(shown)} kind={shown.report === "CAR-HIST" ? "cars" : "status"}
                     initial={{ yard: shown.yard, from: shown.from, to: shown.to, fromCar: shown.fromCar, toCar: shown.toCar }} />}
    </>
  );
}
