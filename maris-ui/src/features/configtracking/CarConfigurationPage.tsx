/* A car's configuration (old VHCF01I) on the clean API: List / Refresh with an action, edit the part serials or the
 * geometry readings, highlight parts to take off, Update; Print opens the report's printer list (old VFCF05I). The
 * rules and messages are the old screen's, run in the middleware. Fields are the old working record's (VHCF01IW). */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { fetchCar, fetchCarOptions, fetchPrinters, printCar, updateCar, type CarConfiguration, type CarOptions } from "./api";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const primary = `${button} bg-bart-600 text-white`;
const secondary = `${button} border border-slate-300 bg-white text-slate-700`;
const panel = "mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark";
const grid = "grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-6";
const errorText = (e: unknown) => (e instanceof Error ? e.message : String(e));

/** the part serials the screen shows, with the serial button that highlights each (TRUCK1, AXLE1 ...) */
const PARTS: [string, string, string][] = [
  ["X-TRUCK-SERIAL", "X - End Truck Serial #", "TRUCK1"], ["Y-TRUCK-SERIAL", "Y - End Truck Serial #", "TRUCK2"],
  ...[1, 2, 3, 4].map(i => [`AXLE-SERIAL-0${i}`, `Axle Ser#${i}`, `AXLE${i}`] as [string, string, string]),
  ...[1, 2, 3, 4].map(i => [`TM-SERIAL-0${i}`, `Traction Motor ${i}`, `TM${i}`] as [string, string, string]),
  ...[1, 2, 3, 4].map(i => [`DISC-SERIAL-0${i}`, `Brake Disc ${i}`, `DISC${i}`] as [string, string, string]),
];
/** the geometry readings (GEOMETRY DATA) */
const READINGS = [
  ...[1, 2, 3, 4, 5, 6, 7, 8].flatMap(i => [`FLANGE-${i}`, `DIAMETER-${i}`]),
  ...[1, 2, 3, 4].flatMap(i => [`DISC-${i}-ANVIL`, `DISC-${i}-PISTON`]),
  ...[1, 2, 3, 4].flatMap(i => ["A", "B", "C"].flatMap(c => [`DC-READING-TIR-${i}${c}`, `DC-READING-MTBT-${i}${c}`])),
];

export function CarConfigurationPage() {
  const [options, setOptions] = useState<CarOptions | null>(null);
  const [car, setCar] = useState("");
  const [action, setAction] = useState("");
  const [truck, setTruck] = useState("X");
  const [data, setData] = useState<CarConfiguration | null>(null);
  const [values, setValues] = useState<Record<string, string>>({});
  const [highlight, setHighlight] = useState<string[]>([]);
  const [employee, setEmployee] = useState("");
  const [location, setLocation] = useState("");
  const [printers, setPrinters] = useState<{ description: string; vtamName: string }[] | null>(null);
  const [printer, setPrinter] = useState("");
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => { fetchCarOptions().then(o => { setOptions(o); setAction(o.actions[0] ?? ""); }).catch(e => setNote(errorText(e))); }, []);
  const show = (c: CarConfiguration) => { setData(c); setValues(c.values); setHighlight([]); };
  const run = async (f: () => Promise<CarConfiguration>) => {
    setBusy(true); setNote("");
    try { show(await f()); } catch (e) { setNote(errorText(e)); } finally { setBusy(false); }
  };
  const changed = () => Object.fromEntries([...PARTS.map(p => p[0]), ...READINGS].filter(k => (values[k] ?? "") !== (data?.values[k] ?? "")).map(k => [k, values[k] ?? ""]));
  const input = (k: string, t: string) => (
    <label key={k} className={label}>{t}<input className={field} value={values[k] ?? ""} onChange={e => setValues(v => ({ ...v, [k]: e.target.value }))} /></label>
  );
  return (
    <div>
      <PageHead title="Car Configuration" code="VHCF01I" />
      <section aria-label="Car" className={panel}>
        <div className={grid}>
          <label className={label}>Car No<input className={field} value={car} maxLength={4} onChange={e => setCar(e.target.value)} /></label>
          <label className={label}>Action
            <select className={field} value={action} onChange={e => setAction(e.target.value)}>{options?.actions.map(a => <option key={a}>{a}</option>)}</select>
          </label>
          <label className={label}>Truck
            <select className={field} value={truck} onChange={e => setTruck(e.target.value)}><option value="X">{options?.trucks[0] ?? "X"}</option><option value="Y">{options?.trucks[1] ?? "Y"}</option></select>
          </label>
          <label className={label}>Employee No<input className={field} value={employee} maxLength={5} onChange={e => setEmployee(e.target.value)} /></label>
          <label className={label}>Location
            <select className={field} value={location} onChange={e => setLocation(e.target.value)}><option value="" />{options?.locations.map(l => <option key={l}>{l}</option>)}</select>
          </label>
        </div>
        <div className="mt-3 flex flex-wrap gap-2">
          <button type="button" className={primary} disabled={busy || !car} onClick={() => run(() => fetchCar(car, action, truck))}>List / Refresh</button>
          <button type="button" className={primary} disabled={busy || !data?.updatable}
            onClick={() => run(() => updateCar(car, { action, truck, values: changed(), highlight, employee, location }))}>Update</button>
          <button type="button" className={secondary} disabled={!car} onClick={() => fetchPrinters().then(p => setPrinters(p.printers)).catch(e => setNote(errorText(e)))}>Print</button>
          <button type="button" className={secondary} onClick={() => { setData(null); setCar(""); setNote(""); }}>Clear</button>
        </div>
        {note && <p role="alert" className="mt-3 rounded-lg bg-amber-50 px-3 py-2 text-amber-900">{note}</p>}
        {data && [data.message, ...data.notices].some(Boolean) &&
          <p role="status" className="mt-3 whitespace-pre-line rounded-lg bg-slate-50 px-3 py-2">{[data.message, ...data.notices].filter(Boolean).join("\n")}</p>}
      </section>
      {printers && (
        <section aria-label="Printers" className={panel}>
          <label className={label}>Printer
            <select className={field} value={printer} onChange={e => setPrinter(e.target.value)}>
              <option value="" />{printers.map(p => <option key={p.vtamName} value={p.vtamName}>{`${p.description} (${p.vtamName})`}</option>)}
            </select>
          </label>
          <button type="button" className={`${primary} mt-3`} disabled={!printer}
            onClick={() => printCar(car, printer).then(() => { setNote("Sent to the printer."); setPrinters(null); }).catch(e => setNote(errorText(e)))}>Hard Copy</button>
        </section>
      )}
      {data && (
        <>
          <section aria-label="Parts" className={panel}>
            <p className="mb-3 text-[13px] text-slate-600">Home yard {data.homeYard || "-"}</p>
            <div className={grid}>
              {PARTS.map(([k, t, part]) => (
                <div key={k} className="flex items-end gap-1">
                  {input(k, t)}
                  <button type="button" aria-label={`highlight ${t}`} aria-pressed={highlight.includes(part)} className={secondary}
                    onClick={() => setHighlight(h => (h.includes(part) ? h.filter(x => x !== part) : [...h, part]))}>{highlight.includes(part) ? "●" : "○"}</button>
                </div>
              ))}
            </div>
          </section>
          <section aria-label="Geometry readings" className={panel}>
            <div className={grid}>{READINGS.map(k => input(k, k))}</div>
          </section>
        </>
      )}
    </div>
  );
}
