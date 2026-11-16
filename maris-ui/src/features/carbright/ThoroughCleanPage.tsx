/* Add car thorough cleanings: a yard, date, time and up to 20 cars (old screen VHCBP1I). */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { addCleaning, cleaningDefaults, type CleaningDefaults } from "./api";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";

export function ThoroughCleanPage() {
  const [defaults, setDefaults] = useState<CleaningDefaults | null>(null);
  const [yard, setYard] = useState("");
  const [date, setDate] = useState("");
  const [time, setTime] = useState("");
  const [cars, setCars] = useState<string[]>([]);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const start = async () => {            // the old screen's opening values, also after a successful add and on Clear
    const d = await cleaningDefaults();
    setDefaults(d); setYard(d.yard); setDate(d.date); setTime(d.time); setCars(Array(d.maxCars).fill(""));
  };
  useEffect(() => { start().catch(e => setError(e instanceof Error ? e.message : String(e))); }, []);

  const add = async () => {
    setBusy(true); setError(""); setMessage("");
    try {
      setMessage((await addCleaning(yard, date, time, cars)).message);
      await start();
    } catch (e) { setError(e instanceof Error ? e.message : String(e)); } finally { setBusy(false); }
  };
  const clear = () => { setMessage(""); setError(""); start().catch(e => setError(e instanceof Error ? e.message : String(e))); };

  if (!defaults) return <><PageHead title="Car Thorough Clean" code="VHCBP1I" />
    {error ? <p role="alert" className="m-2 rounded-lg bg-red-50 px-3 py-2 text-red-800">{error}</p> : <p role="status" className="p-8 text-slate-500">Loading…</p>}</>;
  return (
    <>
      <PageHead title="Car Thorough Clean" code="VHCBP1I" />
      <section aria-label="Thorough clean" className="mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark">
        <div className="flex flex-wrap items-end gap-4">
          <label className={label}>Thorough Clean Location
            <select className={field} value={yard} onChange={e => setYard(e.target.value)}>
              {defaults.yards.map(y => <option key={y} value={y}>{y}</option>)}
            </select>
          </label>
          <label className={label}>Date Clean
            <input className={`${field} w-32`} placeholder="MM-DD-YYYY" maxLength={10} value={date} onChange={e => setDate(e.target.value)} />
          </label>
          <label className={label}>Time Clean
            <input className={`${field} w-24`} placeholder="HH:MM" maxLength={5} value={time} onChange={e => setTime(e.target.value)} />
          </label>
        </div>
        <fieldset className="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-4 lg:grid-cols-5">
          <legend className="mb-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]">Cars</legend>
          {cars.map((c, i) => (
            <input key={i} aria-label={`Car ${i + 1}`} className={`${field} w-full`} maxLength={4} value={c}
              onChange={e => setCars(cs => cs.map((x, j) => (j === i ? e.target.value : x)))} />))}
        </fieldset>
        <div className="mt-4 flex gap-2">
          <button type="button" className={`${button} bg-bart-600 text-white`} disabled={busy} onClick={add}>Add</button>
          <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} onClick={clear}>Clear</button>
        </div>
        {error && <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{error}</p>}
        {message && <p role="status" className="mt-3 rounded-lg bg-emerald-50 px-3 py-2 text-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-200">{message}</p>}
      </section>
    </>
  );
}
