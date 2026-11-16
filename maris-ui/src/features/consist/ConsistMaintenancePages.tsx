/* Consist maintenance pages on the clean API. The rules are the old screens' own (the middleware runs them); a page
 * collects what the user typed, sends it, shows the screen's message and cars, and asks the old screen's question
 * when there is one (then sends the same request again with the answer). */
import { useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { useApp } from "@/shared/lib/app-state";
import { carLocation, consistPost, moveCar, type CarLocation, type ConsistResult } from "./maintenanceApi";

const input = "h-9 w-28 rounded-lg border border-slate-300 bg-white px-2 uppercase text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const primary = `${button} bg-bart-600 text-white`;
const secondary = `${button} border border-slate-300 bg-white text-slate-700`;
const panel = "mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark";
const th = "whitespace-nowrap border-b border-slate-200 bg-slate-50 px-2 py-1 text-left";
const td = "whitespace-nowrap px-2 py-1";
const message = (e: unknown) => (e instanceof Error ? e.message : String(e));

interface Spec {
  code: string; title: string;
  fields: { key: string; caption: string }[];
  cars?: { key: string; caption: string };          // ten car boxes sent as a list
  list?: { path: string; caption: string };          // the lookup step (no writing)
  action: { path: string; caption: string };          // the step that writes
  extra?: Record<string, unknown>;
}

const CARS10 = Array.from({ length: 10 }, () => "");

const SPECS: Record<string, Spec> = {
  makeTrain: { code: "VHMT01I", title: "Make Train", fields: [{ key: "consist", caption: "Consist No" }, { key: "date", caption: "Date in Service" },
    { key: "time", caption: "Time in Service" }], cars: { key: "cars", caption: "Cars" }, list: { path: "make-train/check", caption: "Check" },
    action: { path: "make-train", caption: "Create" } },
  combine: { code: "VHCM01I", title: "Combine Trains", fields: [{ key: "combineTrain", caption: "Combine Train" }, { key: "train", caption: "Train" },
    { key: "beforeAfter", caption: "Before / After" }, { key: "newTrain", caption: "New Train" }, { key: "time", caption: "In Service Time" },
    { key: "location", caption: "Combine Location" }], list: { path: "combine/list", caption: "List" }, action: { path: "combine", caption: "Combine" } },
  clear: { code: "VHCT01I", title: "Clear Train", fields: [{ key: "consist", caption: "Consist No" }, { key: "time", caption: "Time out of Service" },
    { key: "yard", caption: "Yard Code" }, { key: "madeInError", caption: "Made In Error" }], action: { path: "clear-train", caption: "Clear Train" } },
  change: { code: "VHCC01I", title: "Change Consist", fields: [{ key: "consist", caption: "Consist No" }], cars: { key: "newCars", caption: "Change Cars To" },
    list: { path: "change/list", caption: "List" }, action: { path: "change", caption: "Update" } },
  split: { code: "VHSC01I", title: "Split Consist", fields: [{ key: "consist", caption: "Consist No" }, { key: "atCar", caption: "Split at Car (3-9)" },
    { key: "leftConsist", caption: "Left Consist" }, { key: "rightConsist", caption: "Right Consist" }, { key: "splitTime", caption: "Split Time" },
    { key: "splitLocation", caption: "Split Location" }], list: { path: "split/list", caption: "List" }, action: { path: "split", caption: "Update" } },
};

export function ConsistMaintenancePage({ kind }: { kind: keyof typeof SPECS }) {
  const spec = SPECS[kind];
  const app = useApp();
  const [values, setValues] = useState<Record<string, string>>({});
  const [cars, setCars] = useState<string[]>(CARS10);
  const [result, setResult] = useState<ConsistResult | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const body = (answers: Record<string, boolean>) => {
    const b: Record<string, unknown> = { ...values, answers };
    if (spec.fields.some(f => f.key === "atCar")) b.atCar = Number(values.atCar || 0);
    if (spec.cars) b[spec.cars.key] = cars.map(c => c.trim());
    return b;
  };
  const send = async (path: string, answers: Record<string, boolean> = {}) => {
    setBusy(true); setError("");
    try {
      const r = await consistPost(path, body(answers));
      if (r.question) {
        const yes = await app.confirm(r.question.text);
        await send(path, { ...answers, [r.question.name]: yes });
        return;
      }
      setResult(r);
    } catch (e) { setError(message(e)); }
    finally { setBusy(false); }
  };
  const columns = result?.cars.length ? Object.keys(result.cars[0]).filter(c => c !== "position") : [];

  return (
    <div>
      <PageHead title={spec.title} code={spec.code} />
      <section aria-label="Criteria" className={panel}>
        <div className="flex flex-wrap items-end gap-3">
          {spec.fields.map(f => (
            <label key={f.key} className={label}>{f.caption}
              <input className={input} value={values[f.key] ?? ""} onChange={e => setValues({ ...values, [f.key]: e.target.value })} />
            </label>))}
        </div>
        {spec.cars && (
          <fieldset className="mt-3 flex flex-wrap gap-2" aria-label={spec.cars.caption}>
            <legend className="text-[13px] font-semibold text-slate-600">{spec.cars.caption}</legend>
            {cars.map((c, i) => <input key={i} aria-label={`${spec.cars!.caption} ${i + 1}`} className={`${input} w-20`} value={c}
              onChange={e => setCars(cars.map((x, j) => (j === i ? e.target.value : x)))} />)}
          </fieldset>)}
        <div className="mt-3 flex gap-2">
          {spec.list && <button type="button" className={secondary} disabled={busy} onClick={() => void send(spec.list!.path)}>{spec.list.caption}</button>}
          <button type="button" className={primary} disabled={busy} onClick={() => void send(spec.action.path)}>{spec.action.caption}</button>
        </div>
        {error && <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-red-800">{error}</p>}
      </section>
      {result && (
        <section aria-label="Result" className={panel}>
          {result.message && <p role="status" className="mb-2 font-semibold">{result.message}</p>}
          {result.notices.map((n, i) => <p key={i} className="mb-1 text-slate-700">{n}</p>)}
          {result.cars.length > 0 && (
            <table aria-label="Cars" className="text-[13px]">
              <thead><tr><th className={th}>#</th>{columns.map(c => <th key={c} className={th}>{c}</th>)}</tr></thead>
              <tbody>{result.cars.map(r => <tr key={r.position}><td className={td}>{r.position}</td>{columns.map(c => <td key={c} className={td}>{r[c]}</td>)}</tr>)}</tbody>
            </table>)}
        </section>)}
    </div>
  );
}

/* train swap (old VHTS01I): from / to pairs make the swap list, Update swaps them */
export function TrainSwapPage() {
  const app = useApp();
  const [pairs, setPairs] = useState<[string, string][]>([["", ""]]);
  const [result, setResult] = useState<ConsistResult | null>(null);
  const [error, setError] = useState("");
  const send = async (update: boolean, answers: Record<string, boolean> = {}) => {
    setError("");
    try {
      const r = await consistPost("swap", { pairs: pairs.filter(p => p[0] || p[1]), update, answers });
      if (r.question) { await send(update, { ...answers, [r.question.name]: await app.confirm(r.question.text) }); return; }
      setResult(r);
    } catch (e) { setError(message(e)); }
  };
  return (
    <div>
      <PageHead title="Train Swap" code="VHTS01I" />
      <section aria-label="Swaps" className={panel}>
        {pairs.map((p, i) => (
          <div key={i} className="mb-2 flex gap-3">
            <label className={label}>From Consist<input className={input} value={p[0]} onChange={e => setPairs(pairs.map((x, j) => (j === i ? [e.target.value, x[1]] : x)))} /></label>
            <label className={label}>To Consist<input className={input} value={p[1]} onChange={e => setPairs(pairs.map((x, j) => (j === i ? [x[0], e.target.value] : x)))} /></label>
          </div>))}
        <div className="flex gap-2">
          <button type="button" className={secondary} onClick={() => setPairs([...pairs, ["", ""]])}>Add pair</button>
          <button type="button" className={secondary} onClick={() => void send(false)}>Swap Consists</button>
          <button type="button" className={primary} onClick={() => void send(true)}>Update</button>
        </div>
        {error && <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-red-800">{error}</p>}
      </section>
      {result && (
        <section aria-label="Result" className={panel}>
          {result.message && <p role="status" className="mb-2 font-semibold">{result.message}</p>}
          {result.swaps.length > 0 && (
            <table aria-label="List of Swapped Consists" className="text-[13px]"><tbody>
              {result.swaps.map((s, i) => <tr key={i}>{Object.values(s).filter(Boolean).map((v, j) => <td key={j} className={td}>{v}</td>)}</tr>)}
            </tbody></table>)}
        </section>)}
    </div>
  );
}

/* move a car that is not in a consist (old VHCY01I) */
export function CarLocationPage() {
  const [car, setCar] = useState("");
  const [location, setLocation] = useState("");
  const [found, setFound] = useState<CarLocation | null>(null);
  const [text, setText] = useState("");
  const [error, setError] = useState("");
  const look = async () => {
    setError("");
    try { const r = await carLocation(car.trim()); setFound(r); setText(r.message); } catch (e) { setError(message(e)); }
  };
  const move = async () => {
    setError("");
    try { const r = await moveCar(car.trim(), location.trim()); setText(r.message); if (r.moved) setFound(null); } catch (e) { setError(message(e)); }
  };
  return (
    <div>
      <PageHead title="Change Car Location" code="VHCY01I" />
      <section aria-label="Car" className={panel}>
        <div className="flex flex-wrap items-end gap-3">
          <label className={label}>Car<input className={input} value={car} onChange={e => setCar(e.target.value)} onBlur={() => car.trim() && void look()} /></label>
          {found && <p className="pb-2">Current Location: <b>{found.currentLocation}</b> {found.track && `Track ${found.track} Posn ${found.position}`}</p>}
          <label className={label}>New Location<input className={input} value={location} onChange={e => setLocation(e.target.value)} /></label>
          <button type="button" className={primary} disabled={!found} onClick={() => void move()}>Update</button>
        </div>
        {text && <p role="status" className="mt-3 font-semibold">{text}</p>}
        {error && <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-red-800">{error}</p>}
      </section>
    </div>
  );
}
