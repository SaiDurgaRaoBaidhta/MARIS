/* Schedule or reschedule a car's PM (old screen VHPMS01I). */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { fetchPmDescription, fetchScheduleOptions, savePmSchedule, type ScheduleOptions } from "./api";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";

export function PmSchedulePage() {
  const [opts, setOpts] = useState<ScheduleOptions | null>(null);
  const [option, setOption] = useState(1);
  const [car, setCar] = useState("");
  const [pmType, setPmType] = useState("");
  const [description, setDescription] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => { fetchScheduleOptions().then(setOpts).catch(e => setError(e instanceof Error ? e.message : String(e))); }, []);

  const describe = async () => {          // leaving the PM type looks up its description, like the old screen
    if (!car || !pmType) return;
    try { const d = await fetchPmDescription(car, pmType); setPmType(d.pmType); setDescription(d.description); } catch { setDescription(""); }
  };
  const save = async () => {
    setBusy(true); setError(""); setMessage("");
    try { setMessage((await savePmSchedule(car, pmType, option)).message); } catch (e) { setError(e instanceof Error ? e.message : String(e)); } finally { setBusy(false); }
  };
  const clear = () => { setCar(""); setPmType(""); setDescription(""); setOption(1); setMessage(""); setError(""); };

  if (!opts) return <><PageHead title="Schedule PM" code="VHPMS01I" />
    {error ? <p role="alert" className="m-2 rounded-lg bg-red-50 px-3 py-2 text-red-800">{error}</p> : <p role="status" className="p-8 text-slate-500">Loading…</p>}</>;
  const caption = opts.options.find(o => o.option === option)?.button || "Save";
  return (
    <>
      <PageHead title="Schedule PM" code="VHPMS01I" />
      <section aria-label="PM schedule" className="mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark">
        <div className="flex flex-wrap items-end gap-4">
          <fieldset className="flex flex-col gap-1 text-[13px]">
            <legend className="font-semibold text-slate-600 dark:text-[#c9d6e4]">Option</legend>
            {opts.options.map(o => <label key={o.option}><input type="radio" name="pm-option" checked={option === o.option} onChange={() => setOption(o.option)} /> {o.label}</label>)}
          </fieldset>
          <label className={label}>Car No
            <input className={`${field} w-24`} maxLength={4} value={car} onChange={e => setCar(e.target.value)} />
          </label>
          <label className={label}>PM Type
            <input className={`${field} w-20`} maxLength={2} value={pmType} onChange={e => setPmType(e.target.value.toUpperCase())} onBlur={describe} />
          </label>
          <p aria-label="PM description" className="min-w-48 pb-2 text-[13px] text-slate-700 dark:text-ink-dark">{description}</p>
          <button type="button" className={`${button} bg-bart-600 text-white`} disabled={busy} onClick={save}>{caption}</button>
          <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} onClick={clear}>Clear</button>
        </div>
        {error && <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{error}</p>}
        {message && <p role="status" className="mt-3 rounded-lg bg-emerald-50 px-3 py-2 text-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-200">{message}</p>}
      </section>
    </>
  );
}
