/* Vehicle tracking change incident (old VHCI02I): list a car's incidents for an action, mark them, and apply the action. */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { useApp } from "@/shared/lib/app-state";
import { applyChange, listIncidents, trackingChangeOptions, type Listing, type TrackingChangeOptions } from "./trackingChangeListApi";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const panel = "mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark";
const text = (e: unknown) => (e instanceof Error ? e.message : String(e));

/** the parent's params: from the screen key of the page in front ("egl:VHCI02I?carNo=...") or the address bar */
function useParams(): URLSearchParams {
  const app = useApp();
  const top = app.stack[app.stack.length - 1];
  const key = top && top.kind === "screen" ? top.key : "";
  return new URLSearchParams(key.includes("?") ? key.slice(key.indexOf("?") + 1) : window.location.search.replace(/^\?/, ""));
}

type Form = { date: string; time: string; narrative: string; pmLocation: string; pmTeam: string };

export function TrackingChangePage() {
  const app = useApp();
  const params = useParams();
  const [o, setO] = useState<TrackingChangeOptions | null>(null);
  const [action, setAction] = useState(1);
  const [status, setStatus] = useState("");
  const [car, setCar] = useState(params.get("carNo") ?? "");
  const [list, setList] = useState<Listing | null>(null);
  const [marks, setMarks] = useState<Record<string, string>>({});
  const [selected, setSelected] = useState("");
  const [f, setF] = useState<Form>({ date: "", time: "", narrative: "", pmLocation: "", pmTeam: "" });
  const [msg, setMsg] = useState("");
  const [busy, setBusy] = useState(false);
  const fresh = (x: TrackingChangeOptions) => setF({ date: x.date, time: x.time, narrative: "", pmLocation: "", pmTeam: "" });
  useEffect(() => {
    trackingChangeOptions().then(x => { setO(x); setStatus(x.defaultStatus); fresh(x); }).catch(e => setMsg(text(e)));
  }, []);
  const pick = (a: number) => { setAction(a); setList(null); setMarks({}); if (o) setStatus(a === 1 ? o.defaultStatus : ""); };
  const show = async () => {
    setBusy(true);
    try {
      const l = await listIncidents({ action, status: action === 1 ? status : "", car });
      setList(l); setMarks({}); setSelected(l.rows[0]?.incident ?? ""); setMsg(l.message);
    } catch (e) { setMsg(text(e)); } finally { setBusy(false); }
  };
  const change = async () => {
    setBusy(true);
    try {
      const r = await applyChange({ action, status: action === 1 ? status : "", car, changes: marks, ...f, selected });
      setMsg(r.message);
      if (list) setList({ ...r.listing, currentStatus: r.currentStatus || list.currentStatus, heading: list.heading });
      setMarks({});
    } catch (e) { setMsg(text(e)); } finally { setBusy(false); }
  };
  const set = (k: keyof Form, x: string) => setF(s => ({ ...s, [k]: x }));
  const open = (key: string, title: string, q?: Record<string, string>) =>
    app.open({ kind: "screen", key: `egl:${key}${q ? `?${new URLSearchParams(q)}` : ""}`, label: title });
  const pmFields = action === 1 && (status === "PM" || status === "GO");
  const noNarrative = action === 3 || action === 7;
  return (
    <>
      <PageHead title="Vehicle Tracking Change Incident" code="VHCI02I" />
      <section aria-label="Choose" className={panel}>
        <fieldset className="flex flex-wrap gap-3" aria-label="Action">
          {o?.actions.map((a, i) => (
            <label key={a} className="flex items-center gap-1 text-sm">
              <input type="radio" name="action" checked={action === i + 1} onChange={() => pick(i + 1)} />{a}
            </label>
          ))}
        </fieldset>
        <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-4">
          <label className={label}>Car No<input className={field} value={car} maxLength={4} onChange={e => setCar(e.target.value)} /></label>
          {action === 1 && (
            <label className={label}>Status<select className={field} value={status} onChange={e => { setStatus(e.target.value); setList(null); }}>
              {o?.statuses.map(s => <option key={s}>{s}</option>)}</select></label>
          )}
          <div className={label}>Current Status<span className="h-9 py-2 font-normal">{list?.currentStatus}</span></div>
        </div>
        <div className="mt-3 flex flex-wrap gap-2">
          <button className={`${button} bg-bart-600 text-white`} disabled={busy} onClick={show}>List Incds</button>
          <button className={`${button} border border-slate-300`} disabled={!o?.canChangeLocation} onClick={() => open("VHYT10I", "Change Location", car ? { carNo: car } : undefined)}>Change Location</button>
          <button className={`${button} border border-slate-300`} disabled={!o?.canChangeLocation} onClick={() => open("VHCI03I", "CI - Never Go", car ? { carNo: car } : undefined)}>CI - Never Go</button>
        </div>
      </section>
      {list && (
        <section aria-label="Incidents" className={panel}>
          <table className="w-full text-sm">
            <thead><tr className="text-left"><th>Incident</th><th>Status</th><th>Event</th><th>Type</th><th>Date</th><th>Symptom</th><th>{list.heading}</th></tr></thead>
            <tbody>
              {list.rows.map(r => (
                <tr key={r.incident} className={selected === r.incident ? "bg-slate-100 dark:bg-white/5" : ""} onClick={() => setSelected(r.incident)}
                  onDoubleClick={() => open("VHNAR02I", "Incident Narrative", { incdNo: r.incident })}>
                  <td>{r.incident}</td><td>{r.status}</td><td>{`${r.eventType} ${r.eventSubType}`.trim()}</td><td>{r.type}</td><td>{r.date}</td><td>{r.symptom}</td>
                  <td><input aria-label={`${list.heading} ${r.incident}`} className={`${field} h-7 w-24`} value={marks[r.incident] ?? r.change}
                    onChange={e => setMarks(m => ({ ...m, [r.incident]: e.target.value }))} /></td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-4">
            <label className={label}>Status Chg Date<input className={field} value={f.date} onChange={e => set("date", e.target.value)} /></label>
            <label className={label}>Status Chg Time<input className={field} value={f.time} maxLength={4} onChange={e => set("time", e.target.value)} /></label>
            <label className={label}>PM Loc<input className={field} value={f.pmLocation} disabled={!pmFields || noNarrative} maxLength={2} onChange={e => set("pmLocation", e.target.value)} /></label>
            <label className={label}>PM Team<input className={field} value={f.pmTeam} disabled={!pmFields} maxLength={1} onChange={e => set("pmTeam", e.target.value)} /></label>
            <label className={`${label} col-span-2 sm:col-span-4`}>Narrative<textarea className={`${field} h-20`} value={f.narrative} disabled={noNarrative} onChange={e => set("narrative", e.target.value)} /></label>
          </div>
          <div className="mt-3 flex flex-wrap gap-2">
            <button className={`${button} bg-bart-600 text-white`} disabled={busy || !list.changeEnabled} onClick={change}>Change</button>
            <button className={`${button} border border-slate-300`} disabled={!car || !o} onClick={() => open("VHVM11I", "Vehicle Parts Status / Mod Tracking", { carNo: car, passFromGui: o?.maintenanceFrom ?? "" })}>Maint Actions</button>
            <button className={`${button} border border-slate-300`} onClick={() => { setList(null); setMarks({}); setMsg(""); if (o) fresh(o); }}>Clear</button>
          </div>
        </section>
      )}
      {msg && <p role="status" className="mx-2 rounded-lg bg-slate-50 px-3 py-2 text-slate-800">{msg}</p>}
    </>
  );
}
