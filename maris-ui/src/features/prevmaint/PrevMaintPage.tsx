/* Preventive maintenance reports (old criteria screen VHHPM1I and its reports VHHPR1I, VHHPM2I, VHPMR1I, VHHPD1I, VHHPD2I).
 * As on the old screen: report 5 asks for a current location instead of a home yard; report 3 adds PM type, cars and hours. */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { useApp } from "@/shared/lib/app-state";
import { REPORTS, fetchOptions, fetchReport, printReport, type Criteria, type Options, type PmCar, type PmReport } from "./api";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const errText = (e: unknown) => e instanceof Error ? e.message : String(e);

const COLUMNS: [string, keyof PmCar][] = [["PM Ind", "due"], ["Car", "car"], ["Stat", "status"], ["Loc", "location"], ["Home", "homeYard"], ["Hrs since PM", "hoursSinceLastPm"],
  ["Next PM", "nextPm"], ["TI", "trainIncident"], ["MTBI", "mtbi"], ["MTBI hrs", "mtbiHours"], ["Incidents", "mtbiIncidents"], ["Hrs to next PM", "hoursToNextPm"],
  ["Last PM", "lastPm"], ["Hrs at last PM", "hoursAtLastPm"], ["Last PM loc", "lastPmLocation"], ["Cum hrs", "cumulativeHours"]];

export function PrevMaintPage() {
  const app = useApp();
  const [options, setOptions] = useState<Options | null>(null);
  const [report, setReport] = useState(0);
  const [c, setC] = useState<Criteria>({ homeYard: "", location: "", pmType: "", carFrom: "", carTo: "", hoursFrom: "", hoursTo: "" });
  const [printer, setPrinter] = useState("");
  const [result, setResult] = useState<PmReport | null>(null);
  const [error, setError] = useState(""), [busy, setBusy] = useState(false);
  useEffect(() => { fetchOptions().then(setOptions).catch(e => setError(errText(e))); }, []);

  const set = (k: keyof Criteria, v: string) => setC(x => ({ ...x, [k]: v }));
  const run = async (f: () => Promise<void>) => { setBusy(true); setError(""); try { await f(); } catch (e) { setError(errText(e)); } finally { setBusy(false); } };
  const display = () => run(async () => { setResult(null); setResult(await fetchReport(REPORTS[report], c)); });
  const print = () => run(async () => app.toast((await printReport(REPORTS[report], c, printer)).message, "ok"));

  if (!options) return <><PageHead title="Preventive Maintenance Reports" code="VHHPM1I" />
    {error ? <p role="alert" className="m-2 rounded-lg bg-red-50 px-3 py-2 text-red-800">{error}</p> : <p role="status" className="p-8 text-slate-500">Loading…</p>}</>;
  const cols = result ? COLUMNS.filter(([, k]) => result.cars.some(car => car[k] !== undefined && car[k] !== null && car[k] !== "")) : [];
  return (
    <>
      <PageHead title="Preventive Maintenance Reports" code="VHHPM1I" />
      <section aria-label="Criteria" className="mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark">
        <p className="mb-3 text-sm text-slate-600 dark:text-[#c9d6e4]">Revenue date {options.revenueDate} · from {options.fromDate}</p>
        <fieldset className="mb-4 flex flex-col gap-1 text-[13px]">
          <legend className="font-semibold text-slate-600 dark:text-[#c9d6e4]">Report</legend>
          {options.reports.map((r, i) => <label key={r}><input type="radio" name="report" checked={report === i} onChange={() => { setReport(i); setResult(null); }} /> {r}</label>)}
        </fieldset>
        <div className="flex flex-wrap items-end gap-4">
          {report === 4
            ? <label className={label}>Current location<select className={field} value={c.location} onChange={e => set("location", e.target.value)}>
                <option value="">Choose</option>{options.locations.map(y => <option key={y}>{y}</option>)}</select></label>
            : <label className={label}>Home yard<select className={field} value={c.homeYard} onChange={e => set("homeYard", e.target.value)}>
                <option value="">Choose</option>{options.homeYards.map(y => <option key={y}>{y}</option>)}</select></label>}
          {report === 2 && <>
            <label className={label}>PM type<select className={field} value={c.pmType} onChange={e => set("pmType", e.target.value)}>
              <option value="">Choose</option>{options.pmTypes.map(y => <option key={y}>{y}</option>)}</select></label>
            <label className={label}>Car from<input className={`${field} w-20`} maxLength={4} value={c.carFrom} onChange={e => set("carFrom", e.target.value.toUpperCase())} /></label>
            <label className={label}>Car to<input className={`${field} w-20`} maxLength={4} value={c.carTo} onChange={e => set("carTo", e.target.value)} /></label>
            <label className={label}>Hours from<input className={`${field} w-20`} inputMode="numeric" maxLength={4} value={c.hoursFrom} onChange={e => set("hoursFrom", e.target.value)} /></label>
            <label className={label}>Hours to<input className={`${field} w-20`} inputMode="numeric" maxLength={4} value={c.hoursTo} onChange={e => set("hoursTo", e.target.value)} /></label>
          </>}
          <label className={label}>Printer<select className={field} value={printer} onChange={e => setPrinter(e.target.value)}>
            <option value="">Choose a printer for Print</option>{options.printers.map(p => <option key={p.vtam} value={p.vtam}>{p.description} ({p.vtam})</option>)}</select></label>
          <button type="button" className={`${button} bg-bart-600 text-white`} disabled={busy} onClick={display}>Display</button>
          <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} disabled={busy || !printer} onClick={print}>Print</button>
        </div>
        {error && <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{error}</p>}
      </section>
      {result && <section aria-label="Report" className="mx-2 mb-6">
        <p className="mb-2 text-sm text-slate-600 dark:text-[#c9d6e4]">{result.name} · {result.count} cars</p>
        {result.mtbi && result.mtbi.length > 0 && <div className="mb-4 overflow-x-auto rounded-xl border border-slate-200 bg-white dark:border-line-dark dark:bg-panel-dark">
          <table aria-label="MTBI" className="w-full border-collapse text-[13px]">
            <thead><tr>{["Shop", "Hours", "Incidents", "Cars", "MTBI"].map(h => <th key={h} className="border-b border-slate-200 bg-slate-50 px-2 py-1 text-left dark:border-line-dark dark:bg-[#12263b]">{h}</th>)}</tr></thead>
            <tbody>{result.mtbi.map(m => <tr key={m.shop}><td className="px-2 py-1">{m.shop}</td><td className="px-2 py-1">{m.hours}</td><td className="px-2 py-1">{m.incidents}</td><td className="px-2 py-1">{m.cars}</td><td className="px-2 py-1">{m.average}</td></tr>)}</tbody>
          </table></div>}
        {result.count > 0 && <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white dark:border-line-dark dark:bg-panel-dark">
          <table aria-label="Cars" className="w-full border-collapse text-[13px]">
            <thead><tr>{cols.map(([h]) => <th key={h} className="whitespace-nowrap border-b border-slate-200 bg-slate-50 px-2 py-1 text-left dark:border-line-dark dark:bg-[#12263b]">{h}</th>)}</tr></thead>
            <tbody>{result.cars.map((car, i) => <tr key={i} className="even:bg-slate-50 dark:even:bg-[#0f2032]">{cols.map(([h, k]) => <td key={h} className="px-2 py-1">{car[k] ?? ""}</td>)}</tr>)}</tbody>
          </table></div>}
      </section>}
    </>
  );
}
