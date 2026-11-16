/*
 * The vehicle queries' batch print reports (old VHCP01I, VHDP01I, VHFO01I, VHMC01I, VHMR01-03I, VHNG01I, VHOT01I,
 * VHPS01I, VHRC03I, VHPT01I, VHYTOTI): dates, a yard, minutes and a printer, then one button per report the screen
 * prints. Titles, button texts, start values and messages all come from the middleware (the old screens' own).
 */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { useApp } from "@/shared/lib/app-state";
import { printReport, reportOptions, type PrintRequest, type ReportOptions } from "./api";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const message = (e: unknown) => (e instanceof Error ? e.message : String(e));

const start = (o: ReportOptions): PrintRequest =>
  ({ fromDate: o.fromDate, toDate: o.toDate, yard: o.yards[0]?.code ?? "", minutes: o.minutes, printer: "" });   // the old screens select the first yard

export function BatchReportPage({ reports, code }: { reports: string[]; code: string }) {
  const app = useApp();
  const [options, setOptions] = useState<ReportOptions[] | null>(null);
  const [form, setForm] = useState<PrintRequest | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    Promise.all(reports.map(reportOptions))
      .then(o => { setOptions(o); setForm(start(o[0])); })
      .catch(e => setError(message(e)));
  }, [reports]);

  if (!options || !form) return <><PageHead title="Report" code={code} />
    {error ? <p role="alert" className="m-2 rounded-lg bg-red-50 px-3 py-2 text-red-800">{error}</p> : <p role="status" className="p-8 text-slate-500">Loading…</p>}</>;

  const o = options[0];
  const set = (k: keyof PrintRequest) => (v: string) => setForm(f => ({ ...f!, [k]: k === "minutes" ? (v === "" ? null : Number(v)) : v }));
  const print = async (report: string) => {
    setBusy(true); setError("");
    try { app.toast((await printReport(report, form)).message, "ok"); } catch (e) { setError(message(e)); } finally { setBusy(false); }
  };
  return (
    <>
      <PageHead title={o.title} code={code} />
      <section aria-label="Criteria" className="mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark">
        <div className="flex flex-wrap items-end gap-4">
          {o.dates && <>
            <label className={label}>From date<input className={field} type="date" value={form.fromDate} onChange={e => set("fromDate")(e.target.value)} /></label>
            <label className={label}>To date<input className={field} type="date" value={form.toDate} onChange={e => set("toDate")(e.target.value)} /></label>
          </>}
          {o.yards.length > 0 && (
            <label className={label}>Yard
              <select className={field} value={form.yard} onChange={e => set("yard")(e.target.value)}>
                {!o.yardRequired && <option value="">Any</option>}
                {o.yards.map(y => <option key={y.code + y.label} value={y.code}>{y.label}</option>)}
              </select>
            </label>)}
          {o.minutes !== null && (
            <label className={label}>Minutes greater than
              <input className={`${field} w-28`} type="number" min={0} step="0.01" value={form.minutes ?? ""} onChange={e => set("minutes")(e.target.value)} />
            </label>)}
          <label className={label}>Printer
            <select className={field} value={form.printer} onChange={e => set("printer")(e.target.value)}>
              <option value="">Choose a printer</option>
              {o.printers.map(p => <option key={p.vtam} value={p.vtam}>{p.description} ({p.vtam})</option>)}
            </select>
          </label>
        </div>
        <div className="mt-4 flex flex-wrap gap-3">
          {options.map(r => (
            <button key={r.report} type="button" className={`${button} bg-bart-600 text-white`} disabled={busy || !form.printer} onClick={() => print(r.report)}>{r.button}</button>))}
          <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} onClick={() => { setForm(start(o)); setError(""); }}>Clear</button>
        </div>
        {error && <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{error}</p>}
      </section>
    </>
  );
}
