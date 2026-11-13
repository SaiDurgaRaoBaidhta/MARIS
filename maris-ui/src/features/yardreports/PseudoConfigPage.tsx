/* Configure pseudo trains (old screen VHYT53I), on the clean API. The page builds the new pseudo consist as the old screen
 * did (Include a consist's cars, Add cars from a track, Insert one before / after the selected car, Move the selected car
 * out to a track); the middleware checks it with the old rules and writes (Create VHYT53F, Create partial VHYT53G, Move
 * VHYT53H, Reset VHYT53K). The old screen's client rules kept here: at most 10 cars, no car twice, the cars asked for must
 * exist, a train with an incident is pointed out before Include, Reset only for a consist marked C or D. */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import {
  createPseudo, fetchMoveYards, fetchPseudoConfig, fetchPseudoTrackCars, movePseudoCar, resetPseudo,
  type PseudoCar, type PseudoConfig, type PseudoConsist,
} from "./api";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg border border-slate-300 px-3 font-semibold disabled:opacity-50 dark:border-line-dark";
const panel = "rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark";
const MAX_CARS = 10;

const tag = (c: PseudoCar) => `${c.leftEnd}${c.car}${c.rightEnd}`;
const message = (e: unknown) => (e instanceof Error ? e.message : String(e));

export function PseudoConfigPage() {
  const [yards, setYards] = useState<string[] | null>(null);
  const [yard, setYard] = useState("");
  const [config, setConfig] = useState<PseudoConfig | null>(null);
  const [picked, setPicked] = useState<PseudoConsist | null>(null);
  const [includeCount, setIncludeCount] = useState(0);
  const [cars, setCars] = useState<PseudoCar[]>([]);                 // the new consist
  const [selected, setSelected] = useState(-1);
  const [consistNo, setConsistNo] = useState("");
  const [addTrack, setAddTrack] = useState(""); const [addCars, setAddCars] = useState<PseudoCar[]>([]); const [addCount, setAddCount] = useState(0);
  const [insTrack, setInsTrack] = useState(""); const [insCars, setInsCars] = useState<PseudoCar[]>([]); const [insCar, setInsCar] = useState("");
  const [where, setWhere] = useState<"" | "before" | "after">("");
  const [moveTrack, setMoveTrack] = useState("");
  const [info, setInfo] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => { fetchMoveYards().then(setYards).catch(e => setError(message(e))); }, []);
  const load = async (y: string) => {
    setError(""); setPicked(null);
    try { const c = await fetchPseudoConfig(y); setConfig(c); setInfo(c.message ?? ""); }
    catch (e) { setConfig(null); setError(message(e)); }
  };
  const chooseYard = (y: string) => {
    setYard(y); setCars([]); setSelected(-1); setConsistNo(""); setAddTrack(""); setAddCars([]); setInsTrack(""); setInsCars([]);
    if (y) void load(y);
  };

  /** the old Include / Add / Insert checks; returns the message, or "" when the cars may go in */
  const refuse = (more: PseudoCar[]) =>
    cars.length + more.length > MAX_CARS ? `The new pseudo consist would have more than ${MAX_CARS} cars.`
      : more.some(m => cars.some(c => c.car === m.car)) ? "Car(s) already in the new consist. Please check and re-select." : "";
  const put = (more: PseudoCar[], at = cars.length) => {
    const why = refuse(more);
    if (why) { setError(why); return; }
    setError(""); setCars(c => [...c.slice(0, at), ...more, ...c.slice(at)]); setSelected(-1);
  };

  const include = () => {
    if (!picked) return;
    if (includeCount < 1 || includeCount > picked.cars.length) { setError("The number of cars asked for is more than the pseudo train has."); return; }
    put(picked.cars.slice(0, includeCount));
  };
  const pickConsist = (c: PseudoConsist) => { setPicked(c); setIncludeCount(c.cars.length); setError(""); };

  const chooseAddTrack = async (t: string) => {
    setAddTrack(t); setAddCars([]);
    if (t) try { const list = await fetchPseudoTrackCars(yard, t); setAddCars(list); setAddCount(list.length); } catch (e) { setError(message(e)); }
  };
  const add = () => {
    if (addCount < 1 || addCount > addCars.length) { setError("The number of cars asked for is more than the track has."); return; }
    put(addCars.slice(0, addCount));
  };
  const chooseInsTrack = async (t: string) => {
    setInsTrack(t); setInsCars([]); setInsCar("");
    if (t) try { const list = await fetchPseudoTrackCars(yard, t, true); setInsCars(list); } catch (e) { setError(message(e)); }
  };
  const insert = () => {
    const c = insCars.find(x => x.car === insCar);
    if (!c) { setError("Choose a car to insert."); return; }
    if (selected < 0) { setError("No car selected. Please select the car to insert before or after."); return; }
    if (!where) { setError("Please choose before or after before inserting a car."); return; }
    put([c], where === "before" ? selected : selected + 1);
  };

  const run = async (f: () => Promise<void>) => { setBusy(true); setError(""); try { await f(); } catch (e) { setError(message(e)); } finally { setBusy(false); } };
  const create = (partial: boolean) => run(async () => {
    const r = await createPseudo(yard, consistNo, partial, cars);
    setCars([]); setConsistNo(""); setSelected(-1);
    await load(yard);
    setInfo(r.message ?? "");
  });
  const move = () => run(async () => {
    const c = cars[selected];
    if (!c || !moveTrack) { setError("No car / track selected. Please select a car and a track before Move."); return; }
    const r = await movePseudoCar(yard, c.car, moveTrack);
    if (r.moved) { setCars(list => list.filter((_, i) => i !== selected)); setSelected(-1); setInfo(r.message); } else setError(r.message);
  });
  const reset = () => run(async () => { if (picked) { await resetPseudo(yard, picked); await load(yard); } });

  const tracks = config?.tracks ?? [];
  const trackSelect = (text: string, value: string, set: (v: string) => void) => (
    <label className={label}>{text}
      <select className={field} value={value} onChange={e => set(e.target.value)}>
        <option value="">Choose a track</option>
        {tracks.map(t => <option key={t.track} value={t.track}>{t.track}{t.description ? ` – ${t.description}` : ""}</option>)}
      </select>
    </label>
  );
  return (
    <>
      <PageHead title="Configure Pseudo(s)" code="VHYT53I" />
      <div className="mx-2 mb-6 flex flex-col gap-4">
        <section aria-label="Choose the yard" className={panel}>
          {yards ? (
            <label className={`${label} max-w-xs`}>Yard
              <select className={field} value={yard} onChange={e => chooseYard(e.target.value)}>
                <option value="">Choose a yard</option>
                {yards.map(y => <option key={y} value={y}>{y}</option>)}
              </select>
            </label>
          ) : !error && <p className="text-slate-500">Loading the yards from MARIS…</p>}
          {info && <p role="status" className="mt-3 rounded-lg bg-sky-50 px-3 py-2 text-sky-900 dark:bg-sky-950/40 dark:text-sky-200">{info}</p>}
          {error && <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{error}</p>}
          {config?.warnings.map(w => <p key={w} className="mt-3 rounded-lg bg-amber-50 px-3 py-2 text-amber-900">{w}</p>)}
        </section>

        {config && <>
          <section aria-label="Consists" className={panel}>
            <div className="overflow-x-auto">
              <table aria-label={`Consists in ${config.yard}`} className="w-full border-collapse text-[13px]">
                <thead><tr>{["", "Consist", "Track", "Cars", "Ind"].map(h => <th key={h} className="border-b border-slate-200 px-2 py-1 text-left dark:border-line-dark">{h}</th>)}</tr></thead>
                <tbody>{config.consists.map(c => (
                  <tr key={c.display} className="even:bg-slate-50 dark:even:bg-[#0f2032]">
                    <td className="px-2"><input type="radio" name="consist" aria-label={c.display} checked={picked?.display === c.display} onChange={() => pickConsist(c)} /></td>
                    <td className="px-2 font-semibold">{c.display}</td><td className="px-2">{c.track}</td>
                    <td className="px-2 font-mono">{c.cars.map((x, i) => <span key={x.car} className={x.serviceable === false ? "text-red-600" : ""}>{i ? " " : ""}{tag(x)}</span>)}</td>
                    <td className="px-2">{c.indicator}</td>
                  </tr>))}
                </tbody>
              </table>
            </div>
            {picked?.cars[0]?.incident && <p className="mt-2 text-amber-800">Train {picked.display} has an incident. Include it only if you still want this train.</p>}
            <div className="mt-3 flex flex-wrap items-end gap-2">
              <label className={label}>Cars to include
                <input type="number" min={1} className={`${field} w-24`} value={includeCount} onChange={e => setIncludeCount(Number(e.target.value))} />
              </label>
              <button type="button" className={button} disabled={!picked} onClick={include}>Include</button>
              <button type="button" className={button} disabled={busy || !picked?.resettable} onClick={() => void reset()}>Reset</button>
            </div>
          </section>

          <section aria-label="Cars from tracks" className={`${panel} grid gap-4 md:grid-cols-2`}>
            <div className="flex flex-wrap items-end gap-2">
              {trackSelect("Add from track", addTrack, t => void chooseAddTrack(t))}
              <label className={label}>Number of cars
                <input type="number" min={1} className={`${field} w-24`} value={addCount} onChange={e => setAddCount(Number(e.target.value))} />
              </label>
              <button type="button" className={button} disabled={!addCars.length} onClick={add}>Add</button>
            </div>
            <div className="flex flex-wrap items-end gap-2">
              {trackSelect("Insert from track", insTrack, t => void chooseInsTrack(t))}
              <label className={label}>Car to insert
                <select className={field} value={insCar} onChange={e => setInsCar(e.target.value)}>
                  <option value="">Choose a car</option>
                  {insCars.map(c => <option key={c.car} value={c.car}>{tag(c)}</option>)}
                </select>
              </label>
              <fieldset className="flex gap-2 text-[13px]"><legend className="sr-only">Where</legend>
                <label><input type="radio" name="where" checked={where === "before"} onChange={() => setWhere("before")} /> Before</label>
                <label><input type="radio" name="where" checked={where === "after"} onChange={() => setWhere("after")} /> After</label>
              </fieldset>
              <button type="button" className={button} disabled={!insCar} onClick={insert}>Insert</button>
            </div>
          </section>

          <section aria-label="New consist" className={panel}>
            <ol aria-label="New pseudo consist" className="mb-3 flex min-h-9 flex-wrap gap-1">
              {cars.map((c, i) => (
                <li key={c.car}>
                  <button type="button" aria-label={`Select ${tag(c)}`} aria-pressed={selected === i} onClick={() => setSelected(s => (s === i ? -1 : i))}
                          className={`h-8 rounded border px-2 font-mono text-[12px] font-bold ${selected === i ? "border-amber-500 bg-yellow-200" : "border-slate-400 bg-slate-100 dark:bg-[#12263b]"} ${c.serviceable === false || !["GO", "USABLE"].includes(c.status) ? "text-red-600" : ""}`}>{tag(c)}</button>
                </li>))}
            </ol>
            <div className="flex flex-wrap items-end gap-2">
              {trackSelect("Move to track", moveTrack, setMoveTrack)}
              <button type="button" className={button} disabled={busy || selected < 0} onClick={() => void move()}>Move</button>
              <label className={label}>New consist number
                <input className={`${field} w-24`} maxLength={3} value={consistNo} onChange={e => setConsistNo(e.target.value)} />
              </label>
              <button type="button" className={`${button} bg-bart-600 text-white`} disabled={busy} onClick={() => void create(false)}>Create</button>
              <button type="button" className={button} disabled={busy} onClick={() => void create(true)}>Create partial</button>
              <button type="button" className={button} onClick={() => { setCars([]); setConsistNo(""); setSelected(-1); setError(""); }}>Clear</button>
            </div>
          </section>
        </>}
      </div>
    </>
  );
}
