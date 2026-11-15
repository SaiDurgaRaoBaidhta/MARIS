/* Repeater cars (old VHRC01I criteria and its incident list VHRC02I). */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { useApp } from "@/shared/lib/app-state";
import { printRepeaterCars, repeaterCars, repeaterOptions, type RepeaterIncidents, type RepeaterOptions } from "./api";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const message = (e: unknown) => (e instanceof Error ? e.message : String(e));
const HEAD = ["Car", "Loc", "Status", "Incd No", "Incd Date", "Incd Time", "Hrs Bet Incds", "Hrs From Last Incd", "Symp"];

export function RepeaterCarsPage() {
  const app = useApp();
  const [options, setOptions] = useState<RepeaterOptions | null>(null);
  const [yard, setYard] = useState("");
  const [car, setCar] = useState("");
  const [printer, setPrinter] = useState("");
  const [result, setResult] = useState<RepeaterIncidents | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => { repeaterOptions().then(o => { setOptions(o); setYard(o.homeYard); }).catch(e => setError(message(e))); }, []);

  const run = async (f: () => Promise<void>) => {
    setBusy(true); setError("");
    try { await f(); } catch (e) { setError(message(e)); } finally { setBusy(false); }
  };
  const display = () => run(async () => { setResult(null); setResult(await repeaterCars(yard, car.trim())); });
  const print = () => run(async () => app.toast((await printRepeaterCars(yard, car.trim(), printer)).message, "ok"));
  const clear = () => { setYard(options?.homeYard ?? ""); setCar(""); setPrinter(""); setResult(null); setError(""); };

  if (!options) return <><PageHead title="Repeater Cars" code="VHRC01I" />
    {error ? <p role="alert" className="m-2 rounded-lg bg-red-50 px-3 py-2 text-red-800">{error}</p> : <p role="status" className="p-8 text-slate-500">Loading…</p>}</>;
  return (
    <>
      <PageHead title="Repeater Cars" code="VHRC01I" />
      <section aria-label="Criteria" className="mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark">
        <div className="flex flex-wrap items-end gap-4">
          <label className={label}>Home Yard
            <select className={field} value={yard} onChange={e => setYard(e.target.value)}>
              {options.homeYards.map(y => <option key={y} value={y}>{y}</option>)}
            </select>
          </label>
          <label className={label}>Cars<input className={`${field} w-24`} value={car} maxLength={4} onChange={e => setCar(e.target.value)} /></label>
          <button type="button" className={`${button} bg-bart-600 text-white`} disabled={busy} onClick={display}>Display</button>
          <label className={label}>Printer
            <select className={field} value={printer} onChange={e => setPrinter(e.target.value)}>
              <option value="">Choose a printer for Hard Copy</option>
              {options.printers.map(p => <option key={p.vtam} value={p.vtam}>{p.description} ({p.vtam})</option>)}
            </select>
          </label>
          <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} disabled={busy || !printer} onClick={print}>Hard Copy</button>
          <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} onClick={clear}>Clear</button>
        </div>
        {error && <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{error}</p>}
      </section>
      {result && (
        <section className="mx-2 mb-6">
          <p className="mb-2 text-sm text-slate-600 dark:text-[#c9d6e4]">Home Yard {result.homeYard} · Car(s) {result.car || "all"} · Total incidents: {result.total}{result.footer ? ` ${result.footer}` : ""}</p>
          <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white dark:border-line-dark dark:bg-panel-dark">
            <table aria-label="Incidents" className="w-full border-collapse text-[13px]">
              <thead><tr>{HEAD.map(h => <th key={h} className="whitespace-nowrap border-b border-slate-200 bg-slate-50 px-2 py-1 text-left dark:border-line-dark dark:bg-[#12263b]">{h}</th>)}</tr></thead>
              <tbody>{result.incidents.map((r, i) => (
                <tr key={i} className="even:bg-slate-50 dark:even:bg-[#0f2032]">
                  {[r.car, r.location, r.status, r.incident, r.date, r.time, r.hoursBetween, r.hoursFromLast, r.symptom].map((v, j) => <td key={j} className="whitespace-pre px-2 py-1">{v}</td>)}
                </tr>))}
              </tbody>
            </table>
          </div>
        </section>)}
    </>
  );
}
