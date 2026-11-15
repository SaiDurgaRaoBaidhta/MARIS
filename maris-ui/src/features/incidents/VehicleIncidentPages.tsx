/* Vehicle incidents of one car: file one (old VHINC08I), change one (VHINC09I), maintain any incident (VHINC10I).
 * Narratives are added in the narrative window (VHNAR01I), opened with the narrative shown as query params. */
import { useEffect, useState, type ReactNode } from "react";
import { useApp } from "@/shared/lib/app-state";
import { PageHead } from "@/shared/ui/PageHead";
import {
  changeIncident, createOptions, fileIncident, maintainIncident, maintainOptions, maintained, narratives, updatable,
  type CreateOptions, type Details, type Editing, type MaintainOptions, type Narrative,
} from "./vehicleApi";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 disabled:bg-slate-100 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const panel = "mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark";
const message = (e: unknown) => (e instanceof Error ? e.message : String(e));
const PENDING = "maris.incidents.narrative";

function Input({ name, value, set, width = "w-24", max, disabled }: { name: string; value: string; set: (v: string) => void; width?: string; max?: number; disabled?: boolean }) {
  return <label className={label}>{name}<input className={`${field} ${width}`} value={value} maxLength={max} disabled={disabled} onChange={e => set(e.target.value)} /></label>;
}

function Messages({ error, info }: { error: string; info: string }) {
  return <>
    {error && <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{error}</p>}
    {info && <p role="status" className="mt-3 rounded-lg bg-emerald-50 px-3 py-2 text-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-200">{info}</p>}
  </>;
}

/** the narrative window for an incident, with the narrative shown (contract: VHNAR01I query params) */
function useNarrativeWindow(screen: string) {
  const app = useApp();
  return (incident: string, list: Narrative[], index: number, text: string, userGroup: string) => {
    const n = list[index];
    const q = new URLSearchParams({ incdNo: incident, narrNo: String(n?.number ?? 1), currentRow: String(index + 1), maxRows: String(list.length),
      narr: n?.text ?? text, dateNarr: n?.date ?? "", timeNarr: n?.time ?? "", userGrp: n?.userGroup ?? userGroup, storeUserGrp: userGroup });
    try { sessionStorage.setItem(PENDING, JSON.stringify({ screen, incident })); } catch { /* storage unavailable */ }
    app.open({ kind: "screen", key: `egl:VHNAR01I?${q}`, label: "Narrative" });
  };
}

/** the incident a page sent to the narrative window before it closed, read once */
function pendingIncident(screen: string): string {
  try {
    const p = JSON.parse(sessionStorage.getItem(PENDING) || "null") as { screen: string; incident: string } | null;
    if (p?.screen === screen) { sessionStorage.removeItem(PENDING); return p.incident; }
  } catch { /* storage unavailable */ }
  return "";
}

function NarrativeBox({ list, index, setIndex, text, setText, editable, onAdd, canAdd }: {
  list: Narrative[]; index: number; setIndex: (i: number) => void; text: string; setText: (t: string) => void; editable: boolean; onAdd: () => void; canAdd: boolean;
}) {
  const n = list[index];
  return (
    <div className="mt-4">
      <div className="mb-1 flex flex-wrap items-center gap-3 text-[13px]">
        <span>Id <b>{n?.userGroup ?? ""}</b></span><span>Date <b>{n?.date ?? ""}</b></span><span>Time <b>{n?.time ?? ""}</b></span>
        <span>Narr No <b>{n?.number ?? 1}</b>{list.length > 1 && ` of ${list.length}`}</span>
        <button type="button" className={`${button} border border-slate-300`} disabled={index <= 0} onClick={() => { setIndex(index - 1); setText(list[index - 1].text); }}>Prv</button>
        <button type="button" className={`${button} border border-slate-300`} disabled={index >= list.length - 1} onClick={() => { setIndex(index + 1); setText(list[index + 1].text); }}>Nxt</button>
        {canAdd && <button type="button" className={`${button} border border-slate-300`} onClick={onAdd}>Add Narrative</button>}
      </div>
      <textarea aria-label="Narrative" className={`${field} h-32 w-full py-1 font-mono`} maxLength={1000} value={text} disabled={!editable} onChange={e => setText(e.target.value)} />
    </div>
  );
}

function Facts({ d }: { d: Details }) {
  const items: [string, ReactNode][] = [["Car No", d.car], ["Con No", d.consist], ["Incd Type", d.type], ["Incd Stat", d.status], ["Date of Incd", d.date],
    ["Time of Incd", d.time], ["Disc Dur", d.duration], ["Cum Hrs/Min", `${d.cumulativeHours}:${String(d.cumulativeMinutes).padStart(2, "0")}`],
    ["Date / Time / Term Last", `${d.lastUpdate.date} ${d.lastUpdate.time} ${d.lastUpdate.terminal}`],
    ["Date / Time / Term Maint", `${d.maintenanceUpdate.date} ${d.maintenanceUpdate.time} ${d.maintenanceUpdate.terminal}`], ["Cars in consist", d.carsInConsist]];
  return <dl className="mt-3 grid grid-cols-2 gap-x-6 gap-y-1 text-[13px] md:grid-cols-4">{items.map(([k, v]) => <div key={k}><dt className="text-slate-500">{k}</dt><dd className="font-mono">{v}</dd></div>)}</dl>;
}

/** old VHINC08I: file a vehicle incident (Incident Yard, Incident Shop - NOGO, revenue problem fix) */
export function VehicleIncidentPage({ initialType = "YRD" }: { initialType?: string }) {
  const [type, setType] = useState(initialType);
  const [opt, setOpt] = useState<CreateOptions | null>(null);
  const empty = { car: "", symptom: "", duration: "", date: "", time: "", location: "", narrative: "" };
  const [f, setF] = useState(empty);
  const [incident, setIncident] = useState(() => pendingIncident("VHINC08I"));
  const [list, setList] = useState<Narrative[]>([]);
  const [index, setIndex] = useState(0);
  const [error, setError] = useState("");
  const [info, setInfo] = useState("");
  const [busy, setBusy] = useState(false);
  const openNarrative = useNarrativeWindow("VHINC08I");
  const set = (k: keyof typeof empty) => (v: string) => setF(x => ({ ...x, [k]: v }));
  const load = (t: string) => createOptions(t).then(o => { setOpt(o); setF({ ...empty, date: o.date, duration: o.duration }); setError(""); }).catch(e => setError(message(e)));
  useEffect(() => { void load(type); }, [type]);   // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { if (incident) narratives(incident).then(l => { setList(l); setIndex(0); }).catch(e => setError(message(e))); }, []);   // eslint-disable-line react-hooks/exhaustive-deps
  const create = async () => {
    if (!opt) return;
    setBusy(true); setError(""); setInfo("");
    try {
      const r = await fileIncident({ type, ...f, narrativeDate: opt.narrativeDate, narrativeTime: opt.narrativeTime });
      setInfo([r.message, ...r.warnings].join(" ")); setIncident(r.incident); setList([]); setF({ ...empty, date: opt.date, duration: opt.duration });
    } catch (e) { setError(message(e)); } finally { setBusy(false); }
  };
  return (
    <>
      <PageHead title={opt?.title ?? "Vehicle Tracking"} code="VHINC08I" />
      <section aria-label="Incident" className={panel}>
        <div className="flex flex-wrap items-end gap-4">
          <label className={label}>Incident Type<select className={field} value={type} onChange={e => setType(e.target.value)}>
            {["YRD", "SHP", "REV"].map(t => <option key={t} value={t}>{t}</option>)}</select></label>
          <Input name="Car No" value={f.car} set={set("car")} max={4} />
          <Input name="Symptom Code" value={f.symptom} set={set("symptom")} max={4} />
          <label className={label}>Discovered Dur<input className={`${field} w-16`} list="durations" value={f.duration} maxLength={1} onChange={e => set("duration")(e.target.value)} /></label>
          <datalist id="durations">{opt?.durations.map(d => <option key={d.code} value={d.code}>{d.description}</option>)}</datalist>
          <Input name="Date of Incident" value={f.date} set={set("date")} width="w-32" max={10} />
          <Input name="Time of Incident" value={f.time} set={set("time")} width="w-20" max={4} />
          <Input name="Location" value={f.location} set={set("location")} max={4} />
          {incident && <span className="text-[13px]">Incident No <b>{incident}</b></span>}
        </div>
        {list.length > 0
          ? <NarrativeBox list={list} index={index} setIndex={setIndex} text={list[index]?.text ?? ""} setText={() => {}} editable={false} canAdd
              onAdd={() => openNarrative(incident, list, index, "", opt?.userGroup ?? "")} />
          : <NarrativeBox list={[]} index={0} setIndex={() => {}} text={f.narrative} setText={set("narrative")} editable canAdd={!!incident}
              onAdd={() => openNarrative(incident, list, 0, f.narrative, opt?.userGroup ?? "")} />}
        <div className="mt-4 flex gap-3">
          <button type="button" className={`${button} bg-bart-600 text-white`} disabled={busy || !opt} onClick={create}>Create</button>
          <button type="button" className={`${button} border border-slate-300`} onClick={() => { setIncident(""); setList([]); setInfo(""); void load(type); }}>Clear</button>
        </div>
        <Messages error={error} info={info} />
      </section>
    </>
  );
}

/** old VHINC09I (change an incident) and VHINC10I (maintain any incident): List, edit, Update */
function ChangePage({ screen, maintain }: { screen: "VHINC09I" | "VHINC10I"; maintain: boolean }) {
  const [no, setNo] = useState(() => pendingIncident(screen));
  const [d, setD] = useState<Details | null>(null);
  const [editing, setEditing] = useState<Editing | null>(null);
  const [opt, setOpt] = useState<MaintainOptions | null>(null);
  const [f, setF] = useState({ status: "", type: "", symptom: "", duration: "", speed: "", direction: "", location: "", deferredPriority: "", hours: "0", minutes: "0" });
  const [index, setIndex] = useState(0);
  const [text, setText] = useState("");
  const [error, setError] = useState("");
  const [info, setInfo] = useState("");
  const [busy, setBusy] = useState(false);
  const openNarrative = useNarrativeWindow(screen);
  const set = (k: keyof typeof f) => (v: string) => setF(x => ({ ...x, [k]: v }));
  const show = (x: Details, e: Editing | null) => {
    setD(x); setEditing(e); setIndex(0); setText(x.narratives[0]?.text ?? "");
    setF({ status: x.status, type: x.type, symptom: x.symptom, duration: x.duration, speed: x.speed, direction: x.direction, location: x.location,
      deferredPriority: x.deferredPriority, hours: String(x.cumulativeHours), minutes: String(x.cumulativeMinutes) });
  };
  const list = async (incident = no) => {
    setBusy(true); setError(""); setInfo(""); setD(null);
    try {
      if (maintain) show(await maintained(incident.trim()), null);
      else { const r = await updatable(incident.trim()); show(r.incident, r.editing); }
    } catch (e) { setError(message(e)); } finally { setBusy(false); }
  };
  useEffect(() => {
    if (maintain) maintainOptions().then(setOpt).catch(e => setError(message(e)));
    if (no) void list(no);
  }, []);   // eslint-disable-line react-hooks/exhaustive-deps
  const update = async () => {
    if (!d) return;
    setBusy(true); setError(""); setInfo("");
    try {
      const r = maintain
        ? await maintainIncident(d.incident, { status: f.status, type: f.type, symptom: f.symptom, duration: f.duration, speed: f.speed, direction: f.direction,
          location: f.location, deferredPriority: f.deferredPriority, cumulativeHours: Number(f.hours) || 0, cumulativeMinutes: Number(f.minutes) || 0,
          narrative: text, narrativeIndex: index })
        : await changeIncident(d.incident, { symptom: f.symptom, speed: f.speed, direction: f.direction, location: f.location, narrative: text, narrativeIndex: index });
      setInfo(r.message);
    } catch (e) { setError(message(e)); } finally { setBusy(false); }
  };
  const details = maintain || !!editing?.details;
  const speed = maintain || !!editing?.speedAndDirection;
  return (
    <>
      <PageHead title={maintain ? "Vehicle Incident Maintenance" : "Vehicle Tracking (Incident Veh - Update)"} code={screen} />
      <section aria-label="Incident" className={panel}>
        <div className="flex flex-wrap items-end gap-4">
          <Input name="Incident No" value={no} set={setNo} width="w-28" max={8} disabled={!!d} />
          <button type="button" className={`${button} bg-bart-600 text-white`} disabled={busy || !!d} onClick={() => list()}>List</button>
          <button type="button" className={`${button} border border-slate-300`} onClick={() => { setD(null); setNo(""); setError(""); setInfo(""); }}>Clear</button>
        </div>
        {d && <>
          <Facts d={d} />
          <div className="mt-4 flex flex-wrap items-end gap-4">
            {maintain && opt && <label className={label}>Incd Status<select className={field} value={f.status} onChange={e => set("status")(e.target.value)}>
              <option value="" />{opt.statuses.map(s => <option key={s.code} value={s.code}>{s.code} {s.description}</option>)}</select></label>}
            {maintain && <Input name="Incd Type" value={f.type} set={set("type")} max={3} />}
            <Input name="Symptom" value={f.symptom} set={set("symptom")} max={4} disabled={!details} />
            {maintain && opt && <label className={label}>Disc Dur<select className={field} value={f.duration} onChange={e => set("duration")(e.target.value)}>
              <option value="" />{opt.durations.map(x => <option key={x}>{x}</option>)}</select></label>}
            <Input name="Speed" value={f.speed} set={set("speed")} max={3} disabled={!speed} />
            <label className={label}>Direction<select className={field} value={f.direction} disabled={!speed} onChange={e => set("direction")(e.target.value)}>
              {["", "A", "E", "N", "S", "W"].map(x => <option key={x} value={x}>{x}</option>)}</select></label>
            <Input name="Loc" value={f.location} set={set("location")} max={4} disabled={!details} />
            {maintain && <>
              <Input name="Def Prty" value={f.deferredPriority} set={set("deferredPriority")} max={1} />
              <Input name="Cum Hrs" value={f.hours} set={set("hours")} max={8} />
              <Input name="Cum Min" value={f.minutes} set={set("minutes")} max={2} />
            </>}
          </div>
          <NarrativeBox list={d.narratives} index={index} setIndex={setIndex} text={text} setText={setText} editable={details} canAdd={maintain || !!editing?.narratives}
            onAdd={() => openNarrative(d.incident, d.narratives, index, text, d.narratives[index]?.userGroup ?? "")} />
          <div className="mt-4"><button type="button" className={`${button} bg-bart-600 text-white`} disabled={busy || !details} onClick={update}>Update</button></div>
        </>}
        <Messages error={error} info={info} />
      </section>
    </>
  );
}

export const VehicleIncidentUpdatePage = () => <ChangePage screen="VHINC09I" maintain={false} />;
export const VehicleIncidentMaintenancePage = () => <ChangePage screen="VHINC10I" maintain />;
