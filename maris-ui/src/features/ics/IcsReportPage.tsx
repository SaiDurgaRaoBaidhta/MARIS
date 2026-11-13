/* ICS / MARIS messages received (old screens VHIC30I criteria and VHIC31I report), on the clean API.
 * The old screen's rules the page keeps: grouping by yard turns the location list off and the other way round;
 * consist order turns the message code list and the grouping off; Print needs a printer. */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { useApp } from "@/shared/lib/app-state";
import { fetchMessages, fetchOptions, printReport, type IcsCriteria, type IcsOptions, type IcsReport } from "./api";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 disabled:bg-slate-100 disabled:text-slate-400 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark dark:disabled:bg-[#182b40]";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";

const COLUMNS: [string, (m: IcsReport["messages"][number]) => string][] = [
  ["Received", m => m.received], ["Type", m => m.type], ["Consist", m => m.consist], ["To", m => m.consistTo],
  ["Size", m => String(m.size || "")], ["Location", m => m.location], ["ICS loc", m => m.icsLocation], ["Dir", m => m.direction],
  ["In zone", m => m.inZone], ["Created", m => m.created], ["ICS created", m => m.icsCreated], ["Seq", m => String(m.sequence || "")],
  ["CD", m => m.processed], ["Function", m => m.function], ["Completed", m => m.completed], ["Cars", m => m.cars.join(" ")],
];

export function IcsReportPage() {
  const app = useApp();
  const [options, setOptions] = useState<IcsOptions | null>(null);
  const [c, setC] = useState<IcsCriteria>({ revenueDate: "", order: "time", groupBy: "yard", yard: "ALL", location: "ALL", messageCode: "ALL" });
  const [printer, setPrinter] = useState("");
  const [report, setReport] = useState<IcsReport | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    fetchOptions().then(o => { setOptions(o); setC(x => ({ ...x, revenueDate: x.revenueDate || o.revenueDate })); })
      .catch(e => setError(e instanceof Error ? e.message : String(e)));
  }, []);

  const set = <K extends keyof IcsCriteria>(k: K, v: IcsCriteria[K]) => setC(x => ({ ...x, [k]: v }));
  const display = async () => {
    setBusy(true); setError("");
    try { setReport(await fetchMessages(c)); }
    catch (e) { setReport(null); setError(e instanceof Error ? e.message : String(e)); }
    finally { setBusy(false); }
  };
  const print = async () => {
    setBusy(true); setError("");
    try { app.toast((await printReport(c, printer)).message, "ok"); }
    catch (e) { setError(e instanceof Error ? e.message : String(e)); }
    finally { setBusy(false); }
  };

  const consist = c.order === "consist";
  if (!options) return <><PageHead title="ICS Reports" code="VHIC30I" />
    {error ? <p role="alert" className="m-2 rounded-lg bg-red-50 px-3 py-2 text-red-800">{error}</p> : <p role="status" className="p-8 text-slate-500">Loading the report options from MARIS…</p>}</>;
  return (
    <>
      <PageHead title="ICS Reports" code="VHIC30I" />
      <section aria-label="Report criteria" className="mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark">
        {options?.warnings.map(w => <p key={w} className="mb-3 rounded-lg bg-amber-50 px-3 py-2 text-amber-900 dark:bg-amber-950/40 dark:text-amber-200">{w}</p>)}
        <div className="grid gap-4 md:grid-cols-3">
          <label className={label}>Revenue date
            <input type="date" className={field} value={c.revenueDate} onChange={e => set("revenueDate", e.target.value)} />
          </label>
          <fieldset className="flex flex-col gap-1 text-[13px]">
            <legend className="font-semibold text-slate-600 dark:text-[#c9d6e4]">Order</legend>
            <label><input type="radio" name="order" checked={!consist} onChange={() => set("order", "time")} /> Date / time received</label>
            <label><input type="radio" name="order" checked={consist} onChange={() => set("order", "consist")} /> Consist, date / time received</label>
          </fieldset>
          <fieldset className="flex flex-col gap-1 text-[13px]" disabled={consist}>
            <legend className="font-semibold text-slate-600 dark:text-[#c9d6e4]">Group by</legend>
            <label><input type="radio" name="groupBy" checked={c.groupBy === "yard"} onChange={() => set("groupBy", "yard")} /> By yard tracking yard</label>
            <label><input type="radio" name="groupBy" checked={c.groupBy === "location"} onChange={() => set("groupBy", "location")} /> By location</label>
          </fieldset>
          <label className={label}>Yard
            <select className={field} value={c.yard} disabled={c.groupBy !== "yard"} onChange={e => set("yard", e.target.value)}>
              {options?.yards.map(y => <option key={y.code} value={y.code}>{y.code} – {y.description}</option>)}
            </select>
          </label>
          <label className={label}>Location
            <select className={field} value={c.location} disabled={c.groupBy !== "location"} onChange={e => set("location", e.target.value)}>
              {options?.locations.map(l => <option key={l.code} value={l.code}>{l.code} – {l.description}</option>)}
            </select>
          </label>
          <label className={label}>Message code
            <select className={field} value={c.messageCode} disabled={consist} onChange={e => set("messageCode", e.target.value)}>
              {options?.messageCodes.map(m => <option key={m.code} value={m.code}>{m.code} – {m.description}</option>)}
            </select>
          </label>
          <label className={label}>Printer
            <select className={field} value={printer} onChange={e => setPrinter(e.target.value)}>
              <option value="">Choose a printer for Print</option>
              {options?.printers.map(p => <option key={p.vtam} value={p.vtam}>{p.description} ({p.vtam})</option>)}
            </select>
          </label>
        </div>
        <div className="mt-4 flex gap-2">
          <button type="button" className={`${button} bg-bart-600 text-white`} disabled={busy || !options} onClick={display}>Display</button>
          <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} disabled={busy || !printer} onClick={print}>Print</button>
        </div>
        {error && <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{error}</p>}
      </section>
      {report && (
        <section aria-label="Report" className="mx-2 mb-6">
          <p className="mb-2 text-sm text-slate-600 dark:text-[#c9d6e4]">
            {report.count} message{report.count === 1 ? "" : "s"} for {report.revenueDate}
            {report.maxRowsReached && <strong className="ml-2 text-red-700"> Maximum number of rows (150) was reached.</strong>}
          </p>
          <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white dark:border-line-dark dark:bg-panel-dark">
            <table aria-label={`ICS messages for ${report.revenueDate}`} className="w-full border-collapse text-[12px]">
              <thead><tr>{COLUMNS.map(([h]) => <th key={h} className="whitespace-nowrap border-b border-slate-200 bg-slate-50 px-2 py-1 text-left dark:border-line-dark dark:bg-[#12263b]">{h}</th>)}</tr></thead>
              <tbody>{report.messages.map((m, i) => (
                <tr key={i} className="even:bg-slate-50 dark:even:bg-[#0f2032]">{COLUMNS.map(([h, f]) => <td key={h} className="whitespace-nowrap px-2 py-1">{f(m)}</td>)}</tr>))}
              </tbody>
            </table>
          </div>
        </section>)}
    </>
  );
}
