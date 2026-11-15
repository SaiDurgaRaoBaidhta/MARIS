/* Vehicle status (old VHSV01I with its report VHSV02I) and vehicle history (VHSV04I with VHSV05I). */
import { useEffect, useState, type ReactNode } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { useApp } from "@/shared/lib/app-state";
import {
  printVehicleHistory, printVehicleStatus, vehicleHistory, vehicleHistoryNarrative, vehicleHistoryRepair, vehicleHistoryRepairs, vehicleStatus,
  vehicleStatusConsist, vehicleStatusNarrative, vehicleStatusOptions,
  type Repair, type RepairDetails, type VehicleCriteria, type VehicleIncident, type VehicleReport, type VehicleStatusOptions,
} from "./api";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const panel = "mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark";
const message = (e: unknown) => (e instanceof Error ? e.message : String(e));

function Table({ name, head, rows, onRow, selected }: { name: string; head: string[]; rows: ReactNode[][]; onRow?: (i: number) => void; selected?: number }) {
  return (
    <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white dark:border-line-dark dark:bg-panel-dark">
      <table aria-label={name} className="w-full border-collapse text-[13px]">
        <thead><tr>{head.map(h => <th key={h} className="whitespace-nowrap border-b border-slate-200 bg-slate-50 px-2 py-1 text-left dark:border-line-dark dark:bg-[#12263b]">{h}</th>)}</tr></thead>
        <tbody>{rows.map((r, i) => (
          <tr key={i} onClick={onRow ? () => onRow(i) : undefined} aria-selected={selected === i}
            className={`${onRow ? "cursor-pointer hover:bg-sky-50 dark:hover:bg-[#16304a]" : ""} ${selected === i ? "bg-sky-100 dark:bg-[#1b3a57]" : "even:bg-slate-50 dark:even:bg-[#0f2032]"}`}>
            {r.map((v, j) => <td key={j} className="whitespace-pre px-2 py-1">{v}</td>)}
          </tr>))}
        </tbody>
      </table>
    </div>
  );
}

function CarPanel({ report }: { report: VehicleReport }) {
  const c = report.car;
  if (!c) return null;
  return (
    <dl className="mb-3 grid grid-cols-2 gap-x-6 gap-y-1 text-sm sm:grid-cols-4">
      {[["Car", `${c.softwareMarked ? "$" : ""}${c.car}`], ["Status", c.status], ["Curr Loc", c.location], ["Home Yrd", c.homeYard], ["ATO", c.ato], ["War", c.warranty],
        ["MTBI", c.mtbi], ["Cum Hrs", c.cumulativeHours], ["PM Curr Schd", c.currentPm], ["PM Next Schd", c.nextPm], ["Hrs Frm Last PM", c.hoursFromLastPm],
        ["Times washed", c.timesWashed], ["Wash overdue (days)", c.washOverdueDays]].map(([k, v]) => (
        <div key={String(k)} className="flex gap-2"><dt className="font-semibold">{k}</dt><dd className="whitespace-pre">{v}</dd></div>))}
    </dl>
  );
}

function useVehicleOptions() {
  const [o, setO] = useState<VehicleStatusOptions | null>(null);
  const [error, setError] = useState("");
  useEffect(() => { vehicleStatusOptions().then(setO).catch(e => setError(message(e))); }, []);
  return { o, error };
}

function Criteria({ o, c, set, status }: { o: VehicleStatusOptions; c: VehicleCriteria; set: (k: keyof VehicleCriteria) => (v: string) => void; status: boolean }) {
  return <>
    {status && (
      <fieldset className="flex flex-col gap-1 text-[13px]">
        <legend className="font-semibold text-slate-600 dark:text-[#c9d6e4]">Report</legend>
        {o.reports.map((r, i) => <label key={r}><input type="radio" name="report" checked={c.report === i + 1} onChange={() => set("report")(String(i + 1))} /> {r}</label>)}
      </fieldset>)}
    <label className={label}>Car Number<input className={`${field} w-24`} value={c.car} maxLength={4} onChange={e => set("car")(e.target.value)} /></label>
    <label className={label}>From Date<input className={field} type="date" value={c.fromDate} onChange={e => set("fromDate")(e.target.value)} /></label>
    <label className={label}>To Date<input className={field} type="date" value={c.toDate} onChange={e => set("toDate")(e.target.value)} /></label>
    <label className={label}>From Symptom<input className={`${field} w-20`} value={c.sympFrom} maxLength={4} onChange={e => set("sympFrom")(e.target.value)} /></label>
    <label className={label}>To Symptom<input className={`${field} w-20`} value={c.sympTo} maxLength={4} onChange={e => set("sympTo")(e.target.value)} /></label>
    {status && (
      <label className={label}>Deferred Items
        <select className={field} value={c.deferredItems ?? ""} disabled={c.report !== 2} onChange={e => set("deferredItems")(e.target.value)}>
          <option value="" />
          {o.deferredItems.map(d => <option key={d} value={d}>{d}</option>)}
        </select>
      </label>)}
  </>;
}

function Shell({ title, code, children, error, o, loadError }: { title: string; code: string; children: ReactNode; error: string; o: VehicleStatusOptions | null; loadError: string }) {
  if (!o) return <><PageHead title={title} code={code} />
    {loadError ? <p role="alert" className="m-2 rounded-lg bg-red-50 px-3 py-2 text-red-800">{loadError}</p> : <p role="status" className="p-8 text-slate-500">Loading…</p>}</>;
  return <><PageHead title={title} code={code} />{children}
    {error && <p role="alert" className="mx-2 mb-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{error}</p>}</>;
}

function usePage(o: VehicleStatusOptions | null, status: boolean) {
  const empty = (x: VehicleStatusOptions | null): VehicleCriteria =>
    ({ ...(status ? { report: 1, deferredItems: "" } : {}), car: "", fromDate: x?.fromDate ?? "", toDate: x?.toDate ?? "", sympFrom: "", sympTo: "" });
  const [c, setC] = useState<VehicleCriteria>(empty(o));
  useEffect(() => { if (o) setC(empty(o)); }, [o]); // eslint-disable-line react-hooks/exhaustive-deps
  const set = (k: keyof VehicleCriteria) => (v: string) => setC(x => ({ ...x, [k]: k === "report" ? Number(v) : v, ...(k === "report" ? { deferredItems: "" } : {}) }));
  return { c, set, reset: () => setC(empty(o)) };
}

/** old VHSV01I / VHSV02I */
export function VehicleStatusPage() {
  const app = useApp();
  const { o, error: loadError } = useVehicleOptions();
  const { c, set, reset } = usePage(o, true);
  const [printer, setPrinter] = useState("");
  const [report, setReport] = useState<VehicleReport | null>(null);
  const [selected, setSelected] = useState<number | undefined>();
  const [narr, setNarr] = useState<string[] | null>(null);
  const [consist, setConsist] = useState<{ consist: string; cars: string[] } | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const run = async (f: () => Promise<void>) => { setBusy(true); setError(""); try { await f(); } catch (e) { setError(message(e)); } finally { setBusy(false); } };
  const pick = (r: VehicleReport, i: number) => run(async () => {
    setSelected(i); const inc: VehicleIncident = r.incidents[i];
    setNarr(await vehicleStatusNarrative(inc.incident));
    setConsist(inc.consist ? await vehicleStatusConsist(inc) : null);
  });
  const display = () => run(async () => { setReport(null); setNarr(null); setConsist(null); const r = await vehicleStatus(c); setReport(r); if (r.incidents.length) await pick(r, 0); });
  return (
    <Shell title="Vehicle Status" code="VHSV01I" error={error} o={o} loadError={loadError}>
      {o && <section aria-label="Criteria" className={panel}>
        <div className="flex flex-wrap items-end gap-4">
          <Criteria o={o} c={c} set={set} status />
          <button type="button" className={`${button} bg-bart-600 text-white`} disabled={busy} onClick={display}>Display</button>
          <label className={label}>Printer
            <select className={field} value={printer} onChange={e => setPrinter(e.target.value)}>
              <option value="">Choose a printer for Print</option>
              {o.printers.map(p => <option key={p.vtam} value={p.vtam}>{p.description} ({p.vtam})</option>)}
            </select>
          </label>
          <button type="button" className={`${button} border border-slate-300`} disabled={busy || !printer}
            onClick={() => run(async () => app.toast((await printVehicleStatus(c, printer)).message, "ok"))}>Print</button>
          <button type="button" className={`${button} border border-slate-300`} onClick={() => { reset(); setReport(null); setError(""); }}>Clear</button>
        </div>
      </section>}
      {report && (
        <section aria-label="Report" className={panel}>
          <h2 className="mb-3 font-semibold">{report.title}</h2>
          <CarPanel report={report} />
          <p className="mb-2 text-sm">Total Incidents: {report.incidents.length}</p>
          {report.incidents.length > 0 && <Table name="Incidents" head={["Incd No", "Incd Date", "Incd Time", "Symp", "Incd Loc", "Dir", "Status", "Incd Hrs", "Def"]} selected={selected}
            onRow={i => pick(report, i)} rows={report.incidents.map(i => [i.incident, i.date, i.time, i.symptom, i.location, i.direction, i.status, i.hours, i.deferredPriority])} />}
          {consist && consist.cars.length > 0 && <p className="mt-3 text-sm">Consist {consist.consist}: {consist.cars.join("  ")}</p>}
          {narr && <pre aria-label="Narrative" className="mt-3 whitespace-pre-wrap rounded-lg bg-slate-50 p-3 font-mono text-[13px] dark:bg-[#0a1624]">{narr.join("\n")}</pre>}
        </section>)}
    </Shell>
  );
}

/** old VHSV04I / VHSV05I */
export function VehicleHistoryPage() {
  const app = useApp();
  const { o, error: loadError } = useVehicleOptions();
  const { c, set, reset } = usePage(o, false);
  const [printer, setPrinter] = useState("");
  const [report, setReport] = useState<VehicleReport | null>(null);
  const [selected, setSelected] = useState<number | undefined>();
  const [narr, setNarr] = useState<string[] | null>(null);
  const [repairs, setRepairs] = useState<Repair[] | null>(null);
  const [repair, setRepair] = useState<{ index: number; details: RepairDetails } | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const run = async (f: () => Promise<void>) => { setBusy(true); setError(""); try { await f(); } catch (e) { setError(message(e)); } finally { setBusy(false); } };
  const showRepair = (incident: string, list: Repair[], i: number) => run(async () => { setRepair({ index: i, details: await vehicleHistoryRepair(incident, list[i].document) }); });
  const pick = (r: VehicleReport, i: number) => run(async () => {
    setSelected(i); setRepair(null); const incident = r.incidents[i].incident;
    setNarr(await vehicleHistoryNarrative(incident));
    const list = await vehicleHistoryRepairs(incident);
    setRepairs(list);
    if (list.length) setRepair({ index: 0, details: await vehicleHistoryRepair(incident, list[0].document) });
  });
  const display = () => run(async () => { setReport(null); setNarr(null); setRepairs(null); const r = await vehicleHistory(c); setReport(r); if (r.incidents.length) await pick(r, 0); });
  return (
    <Shell title="Vehicle History" code="VHSV04I" error={error} o={o} loadError={loadError}>
      {o && <section aria-label="Criteria" className={panel}>
        <div className="flex flex-wrap items-end gap-4">
          <Criteria o={o} c={c} set={set} status={false} />
          <button type="button" className={`${button} bg-bart-600 text-white`} disabled={busy} onClick={display}>Display</button>
          <label className={label}>Printer
            <select className={field} value={printer} onChange={e => setPrinter(e.target.value)}>
              <option value="">Choose a printer for Print</option>
              {o.printers.map(p => <option key={p.vtam} value={p.vtam}>{p.description} ({p.vtam})</option>)}
            </select>
          </label>
          <button type="button" className={`${button} border border-slate-300`} disabled={busy || !printer}
            onClick={() => run(async () => app.toast((await printVehicleHistory(c, printer)).message, "ok"))}>Print</button>
          <button type="button" className={`${button} border border-slate-300`} onClick={() => { reset(); setReport(null); setError(""); }}>Clear</button>
        </div>
      </section>}
      {report && (
        <section aria-label="Report" className={panel}>
          <h2 className="mb-3 font-semibold">{report.title}</h2>
          <CarPanel report={report} />
          {report.incidents.length > 0 && <Table name="Incidents" head={["Incd No", "Date Incd", "Time Incd", "Symp", "Incd Loc", "Stat", "Dir", "Def", "Cum Hrs", "PM Team"]} selected={selected}
            onRow={i => pick(report, i)} rows={report.incidents.map(i => [i.incident, i.date, i.time, i.symptom, i.location, i.status, i.direction, i.deferredPriority, i.hours, i.pmTeam ?? ""])} />}
          {narr && <pre aria-label="Narrative" className="mt-3 whitespace-pre-wrap rounded-lg bg-slate-50 p-3 font-mono text-[13px] dark:bg-[#0a1624]">{narr.join("\n")}</pre>}
          {repairs && repairs.length > 0 && selected !== undefined && <div className="mt-3">
            <Table name="Repairs" head={["Rpr Doc", "System/Sub", "Disp Cd", "Ser No ON", "Ser No OFF", "PCT No."]} selected={repair?.index}
              onRow={i => showRepair(report.incidents[selected].incident, repairs, i)}
              rows={repairs.map(r => [r.document, `${r.system}/${r.subSystem}`, r.disposition, r.serialOn, r.serialOff, r.partControl])} />
          </div>}
          {repair && <div className="mt-3 grid gap-3 md:grid-cols-2">
            <pre aria-label="Repair narrative" className="whitespace-pre-wrap rounded-lg bg-slate-50 p-3 font-mono text-[13px] dark:bg-[#0a1624]">{repair.details.narrative.join("\n")}</pre>
            <Table name="Components" head={["Cmp Doc No", "Cmp Cd", "Cmp Ser No OFF", "Cmp Disp", "Cmp Fault"]}
              rows={repair.details.components.map(x => [x.document, x.component, x.serialOff, x.disposition, x.fault])} />
          </div>}
        </section>)}
    </Shell>
  );
}
