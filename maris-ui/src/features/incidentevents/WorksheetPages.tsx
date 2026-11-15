/* Vehicle desk work sheet (old VHIE03I, with VHIE04I's event detail) and the symptom history (old VHIE07I). */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { useApp } from "@/shared/lib/app-state";
import { addEvent, correctiveAction, deleteEvent, fileIncident, symptomHistory, updateEvent, worksheetEvents, worksheetOptions,
  type EventInput, type EventLog, type SymptomHistory, type WorksheetEvent, type WorksheetOptions } from "./api";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const plain = `${button} border border-slate-300 dark:border-line-dark`;
const panel = "mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark";
const errText = (e: unknown) => e instanceof Error ? e.message : String(e);
const Alert = ({ text }: { text: string }) => text ? <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{text}</p> : null;
const Note = ({ text }: { text?: string | null }) => text ? <p role="status" className="mt-3 rounded-lg bg-amber-50 px-3 py-2 text-amber-900 dark:bg-amber-950/40 dark:text-amber-200">{text}</p> : null;

const EMPTY: EventInput = { car: "", consist: "", location: "", direction: "", speed: "", time: "", symptom: "", narrative: "", technician: "", trackingCode: "", actionCode: "", incident: "" };
const FIELDS: [keyof EventInput, string][] = [["car", "Car No"], ["consist", "Train"], ["time", "Time"], ["location", "Loc"], ["direction", "Dir"], ["speed", "Speed"],
  ["symptom", "Symp Cd"], ["technician", "Tech Cd"], ["actionCode", "Action Cd"], ["trackingCode", "Tracking Cd"], ["incident", "Incident #"]];

export function WorksheetPage() {
  const app = useApp();
  const [options, setOptions] = useState<WorksheetOptions | null>(null);
  const [date, setDate] = useState("");
  const [log, setLog] = useState<EventLog | null>(null);
  const [picked, setPicked] = useState<WorksheetEvent | null>(null);
  const [form, setForm] = useState<EventInput>(EMPTY), [ca, setCa] = useState("");
  const [history, setHistory] = useState<SymptomHistory | null>(null);
  const [message, setMessage] = useState<string | null>(null), [error, setError] = useState(""), [busy, setBusy] = useState(false);
  useEffect(() => { worksheetOptions().then(o => { setOptions(o); setDate(d => d || o.revenueDate); setMessage(o.message); }).catch(e => setError(errText(e))); }, []);
  const run = async (f: () => Promise<void>) => { setBusy(true); setError(""); try { await f(); } catch (e) { setError(errText(e)); } finally { setBusy(false); } };
  const list = async (d = date) => { const l = await worksheetEvents(d); setLog(l); setMessage(l.message); setPicked(null); };
  const clear = () => { setPicked(null); setForm(EMPTY); setCa(""); setMessage(null); setError(""); setHistory(null); };
  const pick = (e: WorksheetEvent) => run(async () => {
    const c = await correctiveAction(log?.date ?? date, e.number);
    setPicked(e); setCa(c.correctiveAction);
    setForm({ car: e.car, consist: e.consist, location: e.location, direction: e.direction, speed: e.speed, time: e.time, symptom: e.symptom,
      narrative: c.narrative || e.narrative, technician: e.technician, trackingCode: e.trackingCode, actionCode: e.actionCode, incident: e.incident });
  });
  const save = (f: () => Promise<{ message: string | null }>, relist = true) => run(async () => {
    const r = await f();
    if (relist) await list(log?.date ?? date);
    setMessage(r.message); setForm(EMPTY); setCa(""); setPicked(null);
  });
  const symptomText = options?.symptoms.find(s => s.code === form.symptom)?.description;
  return (
    <>
      <PageHead title="Vehicle Desk Work Sheet" code="VHIE03I" />
      <section aria-label="Revenue date" className={panel}>
        <div className="flex flex-wrap items-end gap-4">
          <label className={label}>For revenue date<input type="date" className={field} value={date} onChange={e => setDate(e.target.value)} /></label>
          <button type="button" className={`${button} bg-bart-600 text-white`} disabled={busy || !date} onClick={() => run(() => list())}>List</button>
          <button type="button" className={plain} onClick={() => app.open({ kind: "screen", key: "VHIE01I", label: "Event and Incident Log" })}>Incd Log</button>
          <button type="button" className={plain} onClick={() => app.open({ kind: "screen", key: "VHIE06I", label: "Train or Car Look Up" })}>Car&gt;Con</button>
        </div>
      </section>
      {log && log.events.length > 0 && <div className="mx-2 mb-4 overflow-x-auto rounded-xl border border-slate-200 bg-white dark:border-line-dark dark:bg-panel-dark">
        <table aria-label="Events" className="w-full border-collapse text-[13px]">
          <thead><tr>{["Incd", "Event Id", "Train", "Loc", "Dir", "Speed", "Time", "Symp Cd", "Car", "Tech", "Symptom Text", "Trk Cd", "Act Cd", "Incd No", "Date Insrv Rev", "Date Insrv", "Time Insrv"].map(h =>
            <th key={h} className="whitespace-nowrap border-b border-slate-200 bg-slate-50 px-2 py-1 text-left dark:border-line-dark dark:bg-[#12263b]">{h}</th>)}</tr></thead>
          <tbody>{log.events.map(e => <tr key={e.number} onClick={() => pick(e)} aria-selected={picked?.number === e.number}
            className={`cursor-pointer even:bg-slate-50 dark:even:bg-[#0f2032] ${picked?.number === e.number ? "outline outline-2 outline-bart-600" : ""}`}>
            {[e.hasIncident ? "X" : "", e.eventId, e.consist, e.location, e.direction, e.speed, e.time, e.symptom, e.car, e.technician, e.narrative, e.trackingCode,
              e.actionText, e.incident, e.dateInServiceRev, e.dateInService, e.timeInService].map((v, k) => <td key={k} className="whitespace-nowrap px-2 py-1">{v}</td>)}</tr>)}</tbody>
        </table></div>}
      <section aria-label="Event" className={panel}>
        <div className="flex flex-wrap items-end gap-3">
          {FIELDS.map(([k, l]) => <label key={k} className={label}>{l}<input className={`${field} w-24`} value={form[k]} disabled={k === "time" && !picked}
            onChange={e => setForm(f => ({ ...f, [k]: e.target.value }))} /></label>)}
        </div>
        <label className={`${label} mt-3`}>Symptom code text &amp; user input<textarea className={`${field} h-16 py-1`} value={form.narrative} onChange={e => setForm(f => ({ ...f, narrative: e.target.value }))} /></label>
        {symptomText && <p className="mt-1 text-[12px] text-slate-500">{symptomText}</p>}
        <label className={`${label} mt-3`}>Corrective action text<textarea className={`${field} h-12 py-1`} value={ca} readOnly /></label>
        <div className="mt-3 flex flex-wrap gap-2">
          <button type="button" className={`${button} bg-bart-600 text-white`} disabled={busy || !!picked} onClick={() => save(() => addEvent(form), false)}>Add</button>
          <button type="button" className={plain} disabled={busy || !picked} onClick={() => picked && save(() => updateEvent(log!.date, picked.number, form))}>Update</button>
          <button type="button" className={plain} disabled={busy || !picked} onClick={() => picked && window.confirm("Delete this event?") && save(() => deleteEvent(log!.date, picked.number, form))}>Delete</button>
          <button type="button" className={plain} disabled={busy || !picked || picked.hasIncident || !options?.createAllowed}
            onClick={() => picked && save(() => fileIncident(log!.date, picked.number, form))}>CreMaint</button>
          <button type="button" className={plain} disabled={busy || !form.symptom} onClick={() => run(async () => setHistory(await symptomHistory(form.symptom)))}>Search</button>
          <button type="button" className={plain} onClick={() => app.open({ kind: "screen", key: "VHST05I", label: "Consist Tracking" })}>St Train</button>
          <button type="button" className={plain} onClick={clear}>Clear</button>
        </div>
        <Alert text={error} /><Note text={message} />
      </section>
      {history && <HistoryTable h={history} />}
    </>
  );
}

function HistoryTable({ h }: { h: SymptomHistory }) {
  return (
    <section aria-label="Symptom history" className={panel}>
      <p className="text-[13px] text-slate-600 dark:text-[#c9d6e4]">Symptom {h.symptom}: {h.events} events, {h.incidents} incidents, {h.from} to {h.to}</p>
      <Note text={h.message} />
      {h.rows.length > 0 && <div className="mt-2 overflow-x-auto"><table aria-label="Symptom events and incidents" className="w-full border-collapse text-[13px]">
        <thead><tr>{["I/E", "Car No", "Symp Cd", "Date Insrv", "Time Insrv", "Con No", "Incident No", "Cars", "Narrative"].map(x =>
          <th key={x} className="whitespace-nowrap border-b border-slate-200 bg-slate-50 px-2 py-1 text-left dark:border-line-dark dark:bg-[#12263b]">{x}</th>)}</tr></thead>
        <tbody>{h.rows.map((r, n) => <tr key={n} className="even:bg-slate-50 dark:even:bg-[#0f2032]">
          {[r.type, r.car, r.symptom, r.dateInService, r.timeInService, r.consist, r.incident, r.cars.filter(Boolean).join(" "), r.narrative].map((v, k) =>
            <td key={k} className="whitespace-nowrap px-2 py-1">{v}</td>)}</tr>)}</tbody>
      </table></div>}
    </section>
  );
}

export function SymptomHistoryPage() {
  const [symptom, setSymptom] = useState(""), [days, setDays] = useState("");
  const [h, setH] = useState<SymptomHistory | null>(null), [error, setError] = useState(""), [busy, setBusy] = useState(false);
  return (
    <>
      <PageHead title="Symptom Code History" code="VHIE07I" />
      <section aria-label="Criteria" className={panel}>
        <div className="flex flex-wrap items-end gap-4">
          <label className={label}>Symptom code<input className={`${field} w-24`} value={symptom} onChange={e => setSymptom(e.target.value)} /></label>
          <label className={label}>Days<input className={`${field} w-20`} inputMode="numeric" placeholder="90" value={days} onChange={e => setDays(e.target.value)} /></label>
          <button type="button" className={`${button} bg-bart-600 text-white`} disabled={busy || !symptom} onClick={async () => {
            setBusy(true); setError("");
            try { setH(await symptomHistory(symptom, days || undefined)); } catch (e) { setError(errText(e)); } finally { setBusy(false); }
          }}>List</button>
        </div>
        <Alert text={error} />
      </section>
      {h && <HistoryTable h={h} />}
    </>
  );
}
