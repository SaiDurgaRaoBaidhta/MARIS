/* Car wash updates (old screens VHCWA2I wash of a consist, VHCWA3I wash of single cars). */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { carsStart, checkTime, consistStart, findConsist, invalidBoxes, washCars, washConsist, type Consist } from "./updateApi";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const panel = "mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark";
const errText = (e: unknown) => e instanceof Error ? e.message : String(e);

function Messages({ error, message, done }: { error: string; message: string; done: string }) {
  return <>
    {error && <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{error}</p>}
    {done && <p role="status" className="mt-3 rounded-lg bg-emerald-50 px-3 py-2 text-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-200">{done}</p>}
    {!error && !done && message && <p className="mt-3 text-sm text-slate-600 dark:text-[#c9d6e4]">{message}</p>}
  </>;
}

function YardSelect({ yards, value, onChange }: { yards: string[]; value: string; onChange: (v: string) => void }) {
  return (
    <label className={label}>Wash location
      <select className={`${field} w-24`} value={value} onChange={e => onChange(e.target.value)}>
        <option value="" />
        {yards.map(y => <option key={y} value={y}>{y}</option>)}
      </select>
    </label>);
}

/** VHCWA2I: the consist's cars on a revenue date, then the yard and time it was washed */
export function ConsistWashPage() {
  const [yards, setYards] = useState<string[]>([]), [startDate, setStartDate] = useState(""), [first, setFirst] = useState("");
  const [consist, setConsist] = useState(""), [date, setDate] = useState(""), [yard, setYard] = useState(""), [time, setTime] = useState("");
  const [found, setFound] = useState<Consist | null>(null);
  const [error, setError] = useState(""), [done, setDone] = useState(""), [busy, setBusy] = useState(false);

  useEffect(() => {
    consistStart().then(s => { setYards(s.yards); setStartDate(s.washDate); setDate(s.washDate); setFirst(s.message); }).catch(e => setError(errText(e)));
  }, []);

  const lookup = async () => {
    setBusy(true); setError(""); setDone("");
    try { setFound(await findConsist(consist, date)); } catch (e) { setFound(null); setError(errText(e)); } finally { setBusy(false); }
  };
  const update = async () => {
    setBusy(true); setError(""); setDone("");
    try { setDone((await washConsist({ consist, date, time, yard })).message); } catch (e) { setError(errText(e)); } finally { setBusy(false); }
  };
  const clear = () => { setConsist(""); setDate(startDate); setYard(""); setTime(""); setFound(null); setError(""); setDone(""); };
  const canUpdate = !!found && found.found && !found.alreadyWashed && !done && !busy;

  return (
    <>
      <PageHead title="Car Wash - Consist" code="VHCWA2I" />
      <section aria-label="Consist wash" className={panel}>
        <form className="flex flex-wrap items-end gap-4" onSubmit={e => { e.preventDefault(); lookup(); }}>
          <label className={label}>Consist No.<input className={`${field} w-20`} maxLength={3} value={consist} onChange={e => setConsist(e.target.value)} /></label>
          <label className={label}>Wash date<input type="date" className={field} value={date} onChange={e => setDate(e.target.value)} /></label>
          <button type="submit" className={`${button} border border-slate-300 dark:border-line-dark`} disabled={busy}>Get cars</button>
        </form>
        {found?.found && (
          <div className="mt-4 flex flex-wrap items-end gap-4">
            <p className="text-[13px]">Dash No <b>{found.dashNo}</b></p>
            <ul aria-label="Cars in consist" className="flex flex-wrap gap-2">{found.cars.map(c => <li key={c} className="rounded bg-slate-100 px-2 py-1 dark:bg-[#12263b]">{c}</li>)}</ul>
          </div>)}
        <div className="mt-4 flex flex-wrap items-end gap-4">
          <YardSelect yards={yards} value={yard} onChange={setYard} />
          <label className={label}>Wash time<input className={`${field} w-28`} maxLength={8} value={time} onChange={e => setTime(e.target.value)} /></label>
          <button type="button" className={`${button} bg-bart-600 text-white`} disabled={!canUpdate} onClick={update}>Update</button>
          <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} onClick={clear}>Clear</button>
        </div>
        <Messages error={error} done={done} message={found?.message ?? first} />
      </section>
    </>
  );
}

const BOXES = 10;

/** VHCWA3I: up to 10 cars washed in a yard at a date and time, under the dummy consist */
export function CarsWashPage() {
  const [yards, setYards] = useState<string[]>([]), [start, setStart] = useState({ date: "", consist: "", message: "" });
  const [yard, setYard] = useState(""), [date, setDate] = useState(""), [time, setTime] = useState(""), [consist, setConsist] = useState("");
  const [cars, setCars] = useState<string[]>(Array(BOXES).fill("")), [bad, setBad] = useState<number[]>([]);
  const [message, setMessage] = useState(""), [timeOk, setTimeOk] = useState(false);
  const [error, setError] = useState(""), [done, setDone] = useState(""), [busy, setBusy] = useState(false);

  useEffect(() => {
    carsStart().then(s => {
      setYards(s.yards); setStart({ date: s.washDate, consist: s.consist, message: s.message });
      setDate(s.washDate); setConsist(s.consist); setMessage(s.message);
    }).catch(e => setError(errText(e)));
  }, []);

  const leaveTime = async () => {
    try { const t = await checkTime(time); if (t.time) setTime(t.time); setTimeOk(t.valid); setMessage(t.message); } catch (e) { setError(errText(e)); }
  };
  const update = async () => {
    setBusy(true); setError(""); setDone(""); setBad([]);
    try {
      setDone((await washCars({ yard, date, time, consist, cars })).message);
      setCars(Array(BOXES).fill("")); setTimeOk(false);
    } catch (e) { setError(errText(e)); setBad(invalidBoxes(e)); } finally { setBusy(false); }
  };
  const clear = () => {
    setYard(""); setDate(start.date); setTime(""); setConsist(start.consist); setCars(Array(BOXES).fill("")); setBad([]);
    setMessage(start.message); setTimeOk(false); setError(""); setDone("");
  };

  return (
    <>
      <PageHead title="Car Wash - Cars" code="VHCWA3I" />
      <section aria-label="Cars wash" className={panel}>
        <div className="flex flex-wrap items-end gap-4">
          <YardSelect yards={yards} value={yard} onChange={setYard} />
          <label className={label}>Date washed<input type="date" className={field} value={date} onChange={e => setDate(e.target.value)} /></label>
          <label className={label}>Time washed<input className={`${field} w-28`} maxLength={8} value={time} onChange={e => setTime(e.target.value)} onBlur={leaveTime} /></label>
          <label className={label}>Dummy consist No.<input className={`${field} w-20`} maxLength={3} value={consist} onChange={e => setConsist(e.target.value)} /></label>
        </div>
        <fieldset className="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-5">
          <legend className="mb-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]">Cars</legend>
          {cars.map((c, i) => (
            <label key={i} className="flex items-center gap-1 text-[13px]">
              <span className="sr-only">Car {i + 1}</span>
              <input aria-label={`Car ${i + 1}`} aria-invalid={bad.includes(i + 1)} className={`${field} w-20 ${bad.includes(i + 1) ? "border-red-500" : ""}`} maxLength={4}
                value={c} onChange={e => setCars(cs => cs.map((x, j) => j === i ? e.target.value : x))} />
              {bad.includes(i + 1) && <b className="text-red-700 dark:text-red-300">B</b>}
            </label>))}
        </fieldset>
        <div className="mt-4 flex gap-3">
          <button type="button" className={`${button} bg-bart-600 text-white`} disabled={!timeOk || busy} onClick={update}>Update</button>
          <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} onClick={clear}>Clear</button>
        </div>
        <Messages error={error} done={done} message={message} />
      </section>
    </>
  );
}
