/* Vehicle tracking change incident "never go" (old VHCI03I): change a car to a never-go status; "Maint. Actions" opens VHVM11I on it. */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { useApp } from "@/shared/lib/app-state";
import { neverGo, neverGoOptions, type Maintenance, type NeverGoOptions } from "./trackingChangeApi";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const panel = "mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark";
const text = (e: unknown) => (e instanceof Error ? e.message : String(e));

export function NeverGoPage() {
  const app = useApp();
  const [o, setO] = useState<NeverGoOptions | null>(null);
  const [v, setV] = useState({ car: "", status: "", date: "", time: "", narrative: "" });
  const [current, setCurrent] = useState("");
  const [msg, setMsg] = useState("");
  const [pass, setPass] = useState<Maintenance | null>(null);
  const [busy, setBusy] = useState(false);
  const reset = (x: NeverGoOptions) => setV({ car: "", status: "", date: x.date, time: x.time, narrative: "" });
  useEffect(() => { neverGoOptions().then(x => { setO(x); reset(x); }).catch(e => setMsg(text(e))); }, []);
  const set = (k: keyof typeof v, x: string) => setV(s => ({ ...s, [k]: x }));
  const change = async () => {
    setBusy(true);
    try {
      const r = await neverGo(v);
      setMsg(r.message);
      setCurrent(r.status ? `${r.status} ${r.statusDescription}` : "");
      setPass(r.maintenance);
      if (o) reset(o);
    } catch (e) { setMsg(text(e)); } finally { setBusy(false); }
  };
  const maint = () => {
    if (!pass) return;
    const q = new URLSearchParams({ carNo: pass.car, incdNo: pass.incident, eventStat: pass.status, eventType: pass.eventType, dateInitialized: pass.dateInitialized, passFromGui: pass.from });
    app.open({ kind: "screen", key: `egl:VHVM11I?${q}`, label: "Vehicle Parts Status / Mod Tracking" });
  };
  const open = (key: string, title: string) => app.open({ kind: "screen", key: `egl:${key}${v.car ? `?carNo=${encodeURIComponent(v.car)}` : ""}`, label: title });
  return (
    <>
      <PageHead title="Vehicle Tracking Change Incident Never GO" code="VHCI03I" />
      <section aria-label="Change" className={panel}>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <label className={label}>Car No<input className={field} value={v.car} maxLength={4} onChange={e => set("car", e.target.value)} /></label>
          <label className={label}>Car Status<select className={field} value={v.status} onChange={e => set("status", e.target.value)}><option value="" />{o?.statuses.map(s => <option key={s}>{s}</option>)}</select></label>
          <div className={label}>Current Status<span className="h-9 py-2 font-normal">{current}</span></div>
          <label className={label}>Status Chg Date<input className={field} value={v.date} onChange={e => set("date", e.target.value)} /></label>
          <label className={label}>Status Chg Time<input className={field} value={v.time} maxLength={4} onChange={e => set("time", e.target.value)} /></label>
          <label className={`${label} col-span-2 sm:col-span-4`}>Narrative<textarea className={`${field} h-24`} value={v.narrative} onChange={e => set("narrative", e.target.value)} /></label>
        </div>
        <div className="mt-3 flex flex-wrap gap-2">
          <button className={`${button} bg-bart-600 text-white`} disabled={busy} onClick={change}>Change</button>
          <button className={`${button} border border-slate-300`} onClick={() => { if (o) reset(o); setMsg(""); setCurrent(""); }}>Clear</button>
          <button className={`${button} border border-slate-300`} disabled={!o?.canChangeLocation} onClick={() => open("VHYT10I", "Change Location")}>Change Location</button>
          <button className={`${button} border border-slate-300`} disabled={!o?.canChangeLocation} onClick={() => open("VHYT03I", "CI - Service")}>CI - Service</button>
          <button className={`${button} border border-slate-300`} disabled={!pass} onClick={maint}>Maint. Actions</button>
        </div>
        {msg && <p role="status" className="mt-3 rounded-lg bg-slate-50 px-3 py-2 text-slate-800">{msg}</p>}
      </section>
    </>
  );
}
