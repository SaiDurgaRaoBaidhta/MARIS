/* Configuration tracking query screens that write, on the clean API. The rules (checks, messages, which programs write)
 * are the old screens' own and run in the middleware; these pages only collect the values and show what comes back.
 *  - SerialAddPage (old VHCF21I): add a component serial to stock.
 *  - TruckPage (old VHCF37I inquiry, VHCF35I build): retrieve a truck; on the build screen, type new part serials or
 *    highlight parts to take off, then Update.
 *  - AxlePage (old VHCF06I): list an axle or gear box, type the values, check the boxes, Update. */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import {
  addSerial, buildTruck, fetchAxle, fetchSerialAddOptions, fetchSerialAddStocks, fetchTruck, fetchTruckOptions, updateAxle,
  type Axle, type SerialAddOptions, type Truck, type TruckOptions,
} from "./updatesApi";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const primary = `${button} bg-bart-600 text-white`;
const secondary = `${button} border border-slate-300 bg-white text-slate-700`;
const panel = "mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark";
const grid = "grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4";
const errorText = (e: unknown) => (e instanceof Error ? e.message : String(e));

function Message({ text, notices, ok }: { text?: string; notices?: string[]; ok?: boolean }) {
  const all = [text, ...(notices ?? [])].filter(Boolean) as string[];
  if (!all.length) return null;
  return <p role={ok ? "status" : "alert"} className={`mt-3 whitespace-pre-line rounded-lg px-3 py-2 ${ok ? "bg-emerald-50 text-emerald-800" : "bg-amber-50 text-amber-900"}`}>{all.join("\n")}</p>;
}

export function SerialAddPage() {
  const [options, setOptions] = useState<SerialAddOptions | null>(null);
  const [stocks, setStocks] = useState<string[]>([]);
  const [form, setForm] = useState({ componentType: "", stock: "", serial: "", date: "" });
  const [result, setResult] = useState<{ text: string; ok: boolean; notices: string[] } | null>(null);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    fetchSerialAddOptions().then(o => { setOptions(o); setForm(f => ({ ...f, date: o.date })); }).catch(e => setResult({ text: errorText(e), ok: false, notices: [] }));
  }, []);
  const chooseType = async (componentType: string) => {
    setForm(f => ({ ...f, componentType, stock: "" }));
    setStocks(componentType ? (await fetchSerialAddStocks(componentType)).stocks : []);
  };
  const update = async () => {
    setBusy(true);
    try { const r = await addSerial(form); setResult({ text: r.message, ok: r.added, notices: r.notices }); }
    catch (e) { setResult({ text: errorText(e), ok: false, notices: [] }); } finally { setBusy(false); }
  };
  return (
    <div>
      <PageHead title="Add Component Serial" code="VHCF21I" />
      <section aria-label="Component serial" className={panel}>
        <div className={grid}>
          <label className={label}>Component Type
            <select className={field} value={form.componentType} onChange={e => chooseType(e.target.value)}>
              <option value="" />{options?.componentTypes.map(t => <option key={t}>{t}</option>)}
            </select>
          </label>
          <label className={label}>Stock No
            <select className={field} value={form.stock} onChange={e => setForm(f => ({ ...f, stock: e.target.value }))}>
              <option value="" />{stocks.map(s => <option key={s}>{s}</option>)}
            </select>
          </label>
          <label className={label}>Serial No.<input className={field} value={form.serial} maxLength={12} onChange={e => setForm(f => ({ ...f, serial: e.target.value }))} /></label>
          <label className={label}>Purchase/Rebuilt Date<input className={field} value={form.date} placeholder="MM-DD-YYYY" onChange={e => setForm(f => ({ ...f, date: e.target.value }))} /></label>
          <label className={label}>Action<select className={field} disabled>{options?.actions.map(a => <option key={a}>{a}</option>)}</select></label>
        </div>
        <div className="mt-3 flex gap-2">
          <button type="button" className={primary} disabled={busy} onClick={update}>Update</button>
          <button type="button" className={secondary} onClick={() => { setForm({ componentType: "", stock: "", serial: "", date: options?.date ?? "" }); setStocks([]); setResult(null); }}>Clear</button>
        </div>
        {result && <Message text={result.text} notices={result.notices} ok={result.ok} />}
      </section>
    </div>
  );
}

export function TruckPage({ build }: { build: boolean }) {
  const [options, setOptions] = useState<TruckOptions | null>(null);
  const [stock, setStock] = useState("");
  const [serial, setSerial] = useState("");
  const [truck, setTruck] = useState<Truck | null>(null);
  const [typed, setTyped] = useState<Record<string, string>>({});
  const [takeOff, setTakeOff] = useState<string[]>([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => { fetchTruckOptions().then(setOptions).catch(e => setError(errorText(e))); }, []);
  const run = async (f: () => Promise<Truck>) => {
    setBusy(true); setError("");
    try { setTruck(await f()); setTyped({}); setTakeOff([]); } catch (e) { setError(errorText(e)); } finally { setBusy(false); }
  };
  const toggle = (key: string) => setTakeOff(t => (t.includes(key) ? t.filter(k => k !== key) : [...t, key]));
  return (
    <div>
      <PageHead title={build ? "Truck Build" : "Truck Inquiry"} code={build ? "VHCF35I" : "VHCF37I"} />
      <section aria-label="Truck selection" className={panel}>
        <div className={grid}>
          <label className={label}>Truck Stock Number / Desc
            <select className={field} value={stock} onChange={e => setStock(e.target.value)}>
              <option value="" />{options?.stocks.map(s => <option key={s.stock} value={s.stock}>{`${s.stock} ${s.description}`}</option>)}
            </select>
          </label>
          <label className={label}>Truck<input className={field} value={serial} maxLength={12} onChange={e => setSerial(e.target.value)} /></label>
        </div>
        <div className="mt-3 flex gap-2">
          <button type="button" className={primary} disabled={busy || !stock} onClick={() => run(() => fetchTruck(stock, serial, build))}>Retrieve</button>
          {build && <button type="button" className={primary} disabled={busy || !truck?.updatable}
            onClick={() => run(() => buildTruck({ stock, serial, components: typed, takeOff, axleParts: {} }))}>Update</button>}
          <button type="button" className={secondary} onClick={() => { setTruck(null); setSerial(""); setError(""); }}>Clear</button>
        </div>
        {error && <Message text={error} />}
        {truck && <Message text={truck.message} notices={truck.notices} />}
      </section>
      {truck && (
        <section aria-label="Truck components" className={panel}>
          <p className="mb-3 text-[13px] text-slate-600">Car {truck.car || "-"} · Rebuilt {truck.dateRebuilt || "-"} · {truck.status} {truck.location}</p>
          <div className={grid}>
            {truck.components.map(c => (
              <label key={c.key} className={label}>{c.name || c.key}
                <span className="flex gap-1">
                  <input className={`${field} flex-1`} aria-label={c.name || c.key} value={typed[c.key] ?? c.serial} readOnly={!build}
                    onChange={e => setTyped(t => ({ ...t, [c.key]: e.target.value }))} />
                  {build && c.serial && <button type="button" aria-pressed={takeOff.includes(c.key)} className={secondary} onClick={() => toggle(c.key)}>
                    {takeOff.includes(c.key) ? "Off" : "On"}</button>}
                </span>
              </label>
            ))}
          </div>
          <table aria-label="Axle parts" className="mt-4 text-[13px]">
            <tbody>{truck.axleParts.filter(a => a.serial).map(a => <tr key={a.key}><th className="pr-3 text-left">{a.name || a.key}</th><td>{a.serial}</td></tr>)}</tbody>
          </table>
        </section>
      )}
    </div>
  );
}

const AXLE_INPUTS: [string, string][] = [
  ["axleRebuiltDate", "Axle - Recondition Date"], ["hubCouplingReplacedDate", "Hub Coupling Repl Date"], ["gearBoxSerial", "Gear Box - Serial No"],
  ["gearBoxRebuiltDate", "Gear Box - Rebuilt Date"], ["bBearingSerial", "B Bearing - Serial No"], ["bBearingRebuiltDate", "B Bearing - Rebuilt Date"],
  ["nBearingSerial", "N Bearing - Serial No"], ["nBearingRebuiltDate", "N Bearing - Rebuilt Date"], ["wheel1Serial", "Wheel 1 - Serial No"],
  ["wheel2Serial", "Wheel 2 - Serial No"], ["flange1", "Wheel 1 - Flange"], ["diameter1", "Wheel 1 - Diameter"], ["flange2", "Wheel 2 - Flange"], ["diameter2", "Wheel 2 - Diameter"],
];

export function AxlePage() {
  const [kind, setKind] = useState("axle");
  const [serial, setSerial] = useState("");
  const [axle, setAxle] = useState<Axle | null>(null);
  const [values, setValues] = useState<Record<string, string>>({});
  const [checks, setChecks] = useState<string[]>([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const run = async (f: () => Promise<Axle>) => {
    setBusy(true); setError("");
    try { const a = await f(); setAxle(a); setValues(a.values); setChecks([]); } catch (e) { setError(errorText(e)); } finally { setBusy(false); }
  };
  const changed = () => Object.fromEntries(AXLE_INPUTS.map(([k]) => k).filter(k => (values[k] ?? "") !== (axle?.values[k] ?? "")).map(k => [k, values[k] ?? ""]));
  return (
    <div>
      <PageHead title="Axle / Gear Box" code="VHCF06I" />
      <section aria-label="Axle selection" className={panel}>
        <div className={grid}>
          <label className={label}>Search by
            <select className={field} value={kind} onChange={e => setKind(e.target.value)}>
              <option value="axle">Axle Serial No</option><option value="gearbox">Gearbox Serial No</option>
            </select>
          </label>
          <label className={label}>Serial<input className={field} value={serial} maxLength={12} onChange={e => setSerial(e.target.value)} /></label>
        </div>
        <div className="mt-3 flex gap-2">
          <button type="button" className={primary} disabled={busy} onClick={() => run(() => fetchAxle(kind, serial))}>List</button>
          <button type="button" className={primary} disabled={busy || !axle?.updatable}
            onClick={() => run(() => updateAxle({ kind, serial, values: changed(), checks }))}>Update</button>
          <button type="button" className={secondary} onClick={() => { setAxle(null); setSerial(""); setError(""); }}>Clear</button>
        </div>
        {error && <Message text={error} />}
        {axle && <Message text={axle.message} notices={axle.notices} />}
      </section>
      {axle && (
        <section aria-label="Axle parts" className={panel}>
          <p className="mb-3 text-[13px] text-slate-600">Axle {axle.values.axleSerial} ({axle.values.axleStatus}) · Car {axle.values.car || "-"} · Home yard {axle.values.homeYard}</p>
          <div className={grid}>
            {AXLE_INPUTS.map(([k, t]) => (
              <label key={k} className={label}>{t}<input className={field} value={values[k] ?? ""} onChange={e => setValues(v => ({ ...v, [k]: e.target.value }))} /></label>
            ))}
          </div>
          <fieldset className="mt-4 flex flex-wrap gap-3 text-[13px]"><legend className="font-semibold">Update / Off / On</legend>
            {axle.checkable.map(c => (
              <label key={c} className="flex items-center gap-1">
                <input type="checkbox" checked={checks.includes(c)} onChange={() => setChecks(x => (x.includes(c) ? x.filter(y => y !== c) : [...x, c]))} />{c}
              </label>
            ))}
          </fieldset>
        </section>
      )}
    </div>
  );
}
