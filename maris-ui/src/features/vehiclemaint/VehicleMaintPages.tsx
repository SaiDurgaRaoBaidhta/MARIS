/* Vehicle maintenance read-only pages: incident narrative (old VHNAR02I), vehicle event report (VHVM12I), car event report (VHVM13i). */
import { useEffect, useState, type ReactNode } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { useApp } from "@/shared/lib/app-state";
import { narrative, printCarEvents, printEvents, reportOptions, type Narrative, type ReportOptions } from "./api";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const panel = "mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark";
const message = (e: unknown) => (e instanceof Error ? e.message : String(e));

function Alert({ text }: { text: string }) {
  return text ? <p role="alert" className="mt-3 whitespace-pre-line rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{text}</p> : null;
}

function Text({ name, value, set, type = "text", max }: { name: string; value: string; set: (v: string) => void; type?: string; max?: number }) {
  return <label className={label}>{name}<input className={field} type={type} value={value} maxLength={max} onChange={e => set(e.target.value)} /></label>;
}

function Choice({ name, value, set, items, blank }: { name: string; value: string; set: (v: string) => void; items: string[]; blank: string }) {
  return (
    <label className={label}>{name}
      <select className={field} value={value} onChange={e => set(e.target.value)}>
        <option value="">{blank}</option>
        {items.map(i => <option key={i} value={i}>{i}</option>)}
      </select>
    </label>
  );
}

function PrinterChoice({ value, set, options }: { value: string; set: (v: string) => void; options: ReportOptions }) {
  return (
    <label className={label}>Select Printer
      <select className={field} value={value} onChange={e => set(e.target.value)}>
        <option value="">Choose a printer</option>
        {options.printers.map(p => <option key={p.vtam} value={p.vtam}>{p.description} ({p.vtam})</option>)}
      </select>
    </label>
  );
}

/** the choices both report pages need; the page waits for them like the old screens' start-up */
function useOptions() {
  const [options, setOptions] = useState<ReportOptions | null>(null);
  const [error, setError] = useState("");
  useEffect(() => { reportOptions().then(setOptions).catch(e => setError(message(e))); }, []);
  return { options, error };
}

function Loading({ title, code, error }: { title: string; code: string; error: string }): ReactNode {
  return <><PageHead title={title} code={code} />
    {error ? <p role="alert" className="m-2 rounded-lg bg-red-50 px-3 py-2 text-red-800">{error}</p> : <p role="status" className="p-8 text-slate-500">Loading…</p>}</>;
}

/** old VHNAR02I: an incident's narrative lines */
export function IncidentNarrativePage() {
  const [incident, setIncident] = useState("");
  const [result, setResult] = useState<Narrative | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const show = async () => {
    setBusy(true); setError(""); setResult(null);
    try { setResult(await narrative(incident.trim())); } catch (e) { setError(message(e)); } finally { setBusy(false); }
  };
  return (
    <>
      <PageHead title="Incident Narrative" code="VHNAR02I" />
      <section aria-label="Criteria" className={panel}>
        <div className="flex flex-wrap items-end gap-4">
          <Text name="Incident No" value={incident} set={setIncident} max={8} />
          <button type="button" className={`${button} bg-bart-600 text-white`} disabled={busy || !incident.trim()} onClick={show}>Show</button>
        </div>
        <Alert text={error} />
      </section>
      {result && (
        <section aria-label="Narrative" className={panel}>
          {result.lines.length ? <pre className="whitespace-pre-wrap font-mono text-[13px]">{result.lines.join("\n")}</pre>
            : <p className="text-slate-500">No narrative for incident {result.incident}.</p>}
        </section>)}
    </>
  );
}

/** old VHVM12I: print the vehicle event report */
export function VehicleEventReportPage() {
  const app = useApp();
  const { options, error: loadError } = useOptions();
  const empty = { dateFrom: "", dateTo: "", car: "", carType: "", homeYard: "", eventType: "", eventSubType: "", eventStatus: "", location: "", trackingRefNo: "", sortOrder: "", printer: "" };
  const [c, setC] = useState(empty);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const set = (k: keyof typeof empty) => (v: string) => setC(x => ({ ...x, [k]: v }));
  const submit = async () => {
    setBusy(true); setError("");
    try { app.toast((await printEvents(c)).message, "ok"); } catch (e) { setError(message(e)); } finally { setBusy(false); }
  };
  if (!options) return <Loading title="Vehicle Event Report" code="VHVM12I" error={loadError} />;
  return (
    <>
      <PageHead title="Vehicle Event Report" code="VHVM12I" />
      <section aria-label="Criteria" className={panel}>
        <div className="flex flex-wrap items-end gap-4">
          <Text name="From Date" type="date" value={c.dateFrom} set={set("dateFrom")} />
          <Text name="To Date" type="date" value={c.dateTo} set={set("dateTo")} />
          <Text name="Car No" value={c.car} set={set("car")} max={4} />
          <Choice name="Stk/Mod" value={c.carType} set={set("carType")} items={options.carTypes} blank="Any" />
          <Text name="Cur Loc" value={c.location} set={set("location")} max={4} />
          <Choice name="Home Yrd" value={c.homeYard} set={set("homeYard")} items={options.homeYards} blank="Any" />
          <Choice name="Event Type" value={c.eventType} set={set("eventType")} items={options.eventTypes} blank="Any" />
          <Text name="Event Sub-Type" value={c.eventSubType} set={set("eventSubType")} max={1} />
          <Text name="Event Stat" value={c.eventStatus} set={set("eventStatus")} max={6} />
          <PrinterChoice value={c.printer} set={set("printer")} options={options} />
        </div>
        <div className="mt-4 flex gap-3">
          <button type="button" className={`${button} bg-bart-600 text-white`} disabled={busy || !c.printer} onClick={submit}>Hard Copy</button>
          <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} onClick={() => { setC(empty); setError(""); }}>Clear</button>
        </div>
        <Alert text={error} />
      </section>
    </>
  );
}

/** old VHVM13i: submit the batch parameters of the car event (MOD) report */
export function CarEventReportPage() {
  const app = useApp();
  const { options, error: loadError } = useOptions();
  const today = new Date().toISOString().slice(0, 10);                  // the old screen starts with today's date
  const empty = { carType: "", date: today, eventType: "", eventStatus: "", trackingRefNo: "", comments: "", homeYard: "", printer: "" };
  const [c, setC] = useState(empty);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const set = (k: keyof typeof empty) => (v: string) => setC(x => ({ ...x, [k]: v }));
  const submit = async () => {
    setBusy(true); setError("");
    try { app.toast((await printCarEvents(c)).message, "ok"); } catch (e) { setError(message(e)); } finally { setBusy(false); }
  };
  if (!options) return <Loading title="Car Event Report" code="VHVM13i" error={loadError} />;
  return (
    <>
      <PageHead title="Car Event Report" code="VHVM13i" />
      <section aria-label="Criteria" className={panel}>
        <div className="flex flex-wrap items-end gap-4">
          <Choice name="Car Types" value={c.carType} set={set("carType")} items={options.carTypes} blank="Choose a car type" />
          <Text name="Date Initialized" type="date" value={c.date} set={set("date")} />
          <Choice name="Event Type" value={c.eventType} set={set("eventType")} items={options.eventTypes} blank="Any" />
          <Choice name="Home Yard" value={c.homeYard} set={set("homeYard")} items={options.homeYards} blank="Any" />
          <Text name="Tracking Ref No" value={c.trackingRefNo} set={set("trackingRefNo")} max={9} />
          <PrinterChoice value={c.printer} set={set("printer")} options={options} />
        </div>
        <label className={`${label} mt-4`}>Comment:
          <textarea className={`${field} h-20 py-1`} value={c.comments} maxLength={200} onChange={e => set("comments")(e.target.value)} />
        </label>
        <div className="mt-4 flex gap-3">
          <button type="button" className={`${button} bg-bart-600 text-white`} disabled={busy || !c.printer} onClick={submit}>Submit</button>
          <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} onClick={() => { setC(empty); setError(""); }}>Clear</button>
        </div>
        <Alert text={error} />
      </section>
    </>
  );
}
