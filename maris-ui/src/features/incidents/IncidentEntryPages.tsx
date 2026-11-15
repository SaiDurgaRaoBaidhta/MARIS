/* Incident entry pages: train incidents (old VHINC01I), revenue incidents of a car (VHINC06I), event / shop / miscellaneous
 * incidents of a car (VHINC03I). Every check and message comes from the middleware (the old screens' rules). */
import { useEffect, useState, type ReactNode } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { useApp } from "@/shared/lib/app-state";
import {
  carOptions, changeTrain, createCar, createRevenue, createTrain, revenueDefaults, symptomNarrative, trainDefaults, trainIncident,
  type CarOptions, type TrainIncident,
} from "./api";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const panel = "mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark";
const grid = "grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4";
const message = (e: unknown) => (e instanceof Error ? e.message : String(e));

type Values = Record<string, string>;

function Text({ name, k, v, set, max, disabled }: { name: string; k: string; v: Values; set: (k: string, x: string) => void; max?: number; disabled?: boolean }) {
  return <label className={label}>{name}<input className={field} value={v[k] ?? ""} maxLength={max} disabled={disabled} onChange={e => set(k, e.target.value)} /></label>;
}

function Narrative({ v, set, disabled }: { v: Values; set: (k: string, x: string) => void; disabled?: boolean }) {
  return <label className={label + " mt-3"}>Narrative
    <textarea className={field + " h-24 py-1"} value={v.narrative ?? ""} maxLength={1000} disabled={disabled} onChange={e => set("narrative", e.target.value)} />
  </label>;
}

function Notes({ note, error }: { note: string; error: string }): ReactNode {
  return <>
    {note && <p role="status" className="mt-3 rounded-lg bg-emerald-50 px-3 py-2 text-emerald-800">{note}</p>}
    {error && <p role="alert" className="mt-3 whitespace-pre-line rounded-lg bg-red-50 px-3 py-2 text-red-800">{error}</p>}
  </>;
}

/** the parent's params, from the screen key of the page in front ("egl:VHINC03I?typeInd=SHN") or the address bar */
function useParams(): URLSearchParams {
  const app = useApp();
  const top = app.stack[app.stack.length - 1];
  const key = top && top.kind === "screen" ? top.key : "";
  return new URLSearchParams(key.includes("?") ? key.slice(key.indexOf("?") + 1) : window.location.search.replace(/^\?/, ""));
}

function useForm(initial: Values) {
  const [v, setV] = useState<Values>(initial);
  const [note, setNote] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const set = (k: string, x: string) => setV(o => ({ ...o, [k]: x }));
  const run = async (f: () => Promise<void>) => {
    setBusy(true); setError(""); setNote("");
    try { await f(); } catch (e) { setError(message(e)); } finally { setBusy(false); }
  };
  return { v, setV, set, note, setNote, error, setError, busy, run };
}

/** old VHINC01I: Add a train incident, or Update one (List, then Change) */
export function TrainIncidentPage() {
  const app = useApp();
  const [mode, setMode] = useState<"add" | "update">("add");
  const f = useForm({});
  const [incident, setIncident] = useState<TrainIncident | null>(null);
  const [narr, setNarr] = useState(0);
  const start = () => trainDefaults().then(d => f.setV({ date: d.date, time: d.time })).catch(e => f.setError(message(e)));
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { void start(); }, []);

  const list = () => f.run(async () => {
    const i = await trainIncident(f.v.incidentNo ?? "");
    setIncident(i); setNarr(0);
    f.setV(o => ({ ...o, consistNo: `${i.consistNo}-${i.dashNo}`, symptom: i.symptom, date: i.date, time: i.time, speed: i.speed, direction: i.direction,
      location: i.location, narrative: i.narratives[0]?.narr ?? "" }));
  });
  const shown = incident?.narratives[narr];
  const openNarrative = () => {
    if (!incident) return;
    const p = new URLSearchParams({ incdNo: incident.incidentNo, currentRow: String(narr + 1), maxRows: String(incident.narratives.length),
      narrNo: String(shown?.narrNo ?? 1), narr: shown?.narr ?? "", dateNarr: shown?.dateNarr ?? "", timeNarr: shown?.timeNarr ?? "", userGrp: shown?.userGrp ?? "" });
    app.open({ kind: "screen", key: `egl:VHNAR01I?${p}`, label: "Narrative" });
  };

  return (
    <>
      <PageHead title="Train Incident" code="VHINC01I" />
      <section className={panel}>
        <div className="mb-3 flex gap-4 text-[13px]" role="radiogroup" aria-label="Entry mode">
          {(["add", "update"] as const).map(m => (
            <label key={m} className="flex items-center gap-1">
              <input type="radio" name="mode" checked={mode === m} onChange={() => { setMode(m); setIncident(null); f.setNote(""); f.setError(""); if (m === "add") void start(); else f.setV({}); }} />
              {m === "add" ? "Add" : "Update"}
            </label>
          ))}
        </div>
        {mode === "update" && <div className="mb-3 flex items-end gap-2">
          <Text name="Incident No" k="incidentNo" v={f.v} set={f.set} max={8} />
          <button className={button + " bg-slate-200"} disabled={f.busy} onClick={list}>List</button>
        </div>}
        <div className={grid}>
          <Text name="Consist No" k="consistNo" v={f.v} set={f.set} max={6} disabled={mode === "update"} />
          <Text name="Symptom" k="symptom" v={f.v} set={f.set} max={4} disabled={mode === "update" && !incident} />
          <Text name="Date of Incd" k="date" v={f.v} set={f.set} max={10} disabled={mode === "update"} />
          <Text name="Time of Incd" k="time" v={f.v} set={f.set} max={5} disabled={mode === "update"} />
          <Text name="Speed" k="speed" v={f.v} set={f.set} max={3} disabled={mode === "update" && !incident} />
          <Text name="Direction" k="direction" v={f.v} set={f.set} max={1} disabled={mode === "update" && !incident} />
          <Text name="Loc" k="location" v={f.v} set={f.set} max={4} disabled={mode === "update" && !incident} />
        </div>
        <Narrative v={f.v} set={f.set} disabled={mode === "update"} />
        {incident && incident.narratives.length > 1 && <div className="mt-2 flex gap-2 text-[13px]">
          <button className={button + " bg-slate-200"} disabled={narr === 0} onClick={() => { setNarr(narr - 1); f.set("narrative", incident.narratives[narr - 1].narr); }}>Prv</button>
          <button className={button + " bg-slate-200"} disabled={narr >= incident.narratives.length - 1}
            onClick={() => { setNarr(narr + 1); f.set("narrative", incident.narratives[narr + 1].narr); }}>Nxt</button>
          <span className="self-center">Narr No {shown?.narrNo} · {shown?.userGrp}</span>
        </div>}
        <div className="mt-3 flex flex-wrap gap-2">
          {mode === "add" && <button className={button + " bg-brand-600 text-white"} disabled={f.busy} onClick={() => f.run(async () => {
            const r = await createTrain({ consistNo: f.v.consistNo ?? "", symptom: f.v.symptom ?? "", date: f.v.date ?? "", time: f.v.time ?? "", speed: f.v.speed ?? "",
              direction: f.v.direction ?? "", location: f.v.location ?? "", narrative: f.v.narrative ?? "" });
            f.setNote(r.message); void start();
          })}>Create</button>}
          {mode === "update" && <button className={button + " bg-brand-600 text-white"} disabled={f.busy || !incident} onClick={() => f.run(async () => {
            const r = await changeTrain(incident!.incidentNo, { symptom: f.v.symptom ?? "", speed: f.v.speed ?? "", direction: f.v.direction ?? "",
              location: f.v.location ?? "", narrativeIndex: narr + 1 });
            f.setNote(r.message);
          })}>Change</button>}
          {mode === "update" && <button className={button + " bg-slate-200"} disabled={!incident} onClick={openNarrative}>Add Narrative</button>}
          <button className={button + " bg-slate-200"} onClick={() => app.back()}>Return</button>
        </div>
        <Notes note={f.note} error={f.error} />
      </section>
    </>
  );
}

/** old VHINC06I: a revenue incident of one car */
export function RevenueIncidentPage() {
  const app = useApp();
  const f = useForm({});
  const start = () => revenueDefaults().then(d => f.setV({ date: d.date, time: d.time })).catch(e => f.setError(message(e)));
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { void start(); }, []);
  return (
    <>
      <PageHead title="Revenue Incident" code="VHINC06I" />
      <section className={panel}>
        <div className={grid}>
          <Text name="Car No" k="carNo" v={f.v} set={f.set} max={4} />
          <Text name="Symptom" k="symptom" v={f.v} set={f.set} max={4} />
          <Text name="Date of Incd" k="date" v={f.v} set={f.set} max={10} />
          <Text name="Time of Incd" k="time" v={f.v} set={f.set} max={5} />
          <Text name="Speed" k="speed" v={f.v} set={f.set} max={3} />
          <Text name="Direction" k="direction" v={f.v} set={f.set} max={1} />
          <Text name="Loc" k="location" v={f.v} set={f.set} max={4} />
        </div>
        <Narrative v={f.v} set={f.set} />
        <div className="mt-3 flex flex-wrap gap-2">
          <button className={button + " bg-brand-600 text-white"} disabled={f.busy} onClick={() => f.run(async () => {
            const r = await createRevenue({ carNo: f.v.carNo ?? "", symptom: f.v.symptom ?? "", date: f.v.date ?? "", time: f.v.time ?? "", speed: f.v.speed ?? "",
              direction: f.v.direction ?? "", location: f.v.location ?? "", narrative: f.v.narrative ?? "" });
            f.setNote(r.message); void start();
          })}>Create</button>
          <button className={button + " bg-slate-200"} onClick={() => { f.setNote(""); f.setError(""); void start(); }}>Clear</button>
          <button className={button + " bg-slate-200"} onClick={() => app.back()}>Return</button>
        </div>
        <Notes note={f.note} error={f.error} />
      </section>
    </>
  );
}

/** old VHINC03I: an event (EVT), shop (SHN) or miscellaneous (MSC) incident of one car; the menu passes the type (typeInd) */
export function CarIncidentPage() {
  const app = useApp();
  const params = useParams();
  const [type, setType] = useState((params.get("typeInd") || "EVT").toUpperCase());
  const [options, setOptions] = useState<CarOptions | null>(null);
  const f = useForm({});
  const start = (t: string) => carOptions(t).then(o => { setOptions(o); f.setV({ date: o.date, discovery: o.fixedDiscovery }); }).catch(e => f.setError(message(e)));
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { void start(type); }, [type]);
  const fill = () => { if (f.v.symptom) symptomNarrative(f.v.symptom).then(n => { if (n.narrative) f.set("narrative", n.narrative); }).catch(() => undefined); };
  return (
    <>
      <PageHead title={options?.title || "Vehicle Incident"} code="VHINC03I" />
      <section className={panel}>
        <label className={label + " mb-3 w-48"}>Incident type
          <select className={field} value={type} onChange={e => setType(e.target.value)}>
            {["EVT", "SHN", "MSC"].map(t => <option key={t} value={t}>{t}</option>)}
          </select>
        </label>
        <div className={grid}>
          <Text name="Car No" k="carNo" v={f.v} set={f.set} max={4} />
          <label className={label}>Symptom<input className={field} value={f.v.symptom ?? ""} maxLength={4} onChange={e => f.set("symptom", e.target.value)} onBlur={fill} /></label>
          <label className={label}>Disc Dur
            <select className={field} value={f.v.discovery ?? ""} disabled={!!options?.fixedDiscovery} onChange={e => f.set("discovery", e.target.value)}>
              <option value=""></option>
              {(options?.discoveries ?? []).map(d => <option key={d.code} value={d.code}>{d.code} {d.description}</option>)}
            </select>
          </label>
          <Text name="Date of Incd" k="date" v={f.v} set={f.set} max={10} />
          <Text name="Time of Incd" k="time" v={f.v} set={f.set} max={5} />
          <Text name="Loc" k="location" v={f.v} set={f.set} max={4} />
        </div>
        <Narrative v={f.v} set={f.set} />
        <div className="mt-3 flex flex-wrap gap-2">
          <button className={button + " bg-brand-600 text-white"} disabled={f.busy || !options} onClick={() => f.run(async () => {
            const r = await createCar({ type, carNo: f.v.carNo ?? "", symptom: f.v.symptom ?? "", discovery: f.v.discovery ?? "", date: f.v.date ?? "",
              time: f.v.time ?? "", location: f.v.location ?? "", narrative: f.v.narrative ?? "" });
            f.setNote(r.message); void start(type);
          })}>Create</button>
          <button className={button + " bg-slate-200"} onClick={() => { f.setNote(""); f.setError(""); void start(type); }}>Clear</button>
          <button className={button + " bg-slate-200"} onClick={() => app.back()}>Return</button>
        </div>
        <Notes note={f.note} error={f.error} />
      </section>
    </>
  );
}
