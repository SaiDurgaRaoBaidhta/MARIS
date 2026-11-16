/*
 * Home yard status (old VHYS01I with its reports VHYS02I Base / Status, VHYS03I NOGO, VHYS04I Summary, VHYS05I the
 * summary by car type) and yard status (VHYS06I with VHYS07I .. VHYS10I). Messages, titles and start values come from
 * the middleware.
 */
import { useEffect, useState, type ReactNode } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { useApp } from "@/shared/lib/app-state";
import {
  printStatus, statusCars, statusIncidents, statusNarrative, statusNogo, statusOptions, statusSummary, statusTypeSummary,
  type Checked, type NogoIncident, type StatusCar, type StatusCriteria, type StatusFamily, type StatusIncident, type StatusOption, type StatusReportOptions,
  type SummaryCar, type TypeSummary,
} from "./api";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const panel = "mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark";
const message = (e: unknown) => (e instanceof Error ? e.message : String(e));
const OPTIONS: [StatusOption, string][] = [["base", "Base"], ["status", "Status"], ["nogo", "NOGO"], ["summary", "Summary"]];
const FAMILY: Record<StatusFamily, { code: string; title: string; prefix: string }> = {
  "home-yard-status": { code: "VHYS01I", title: "Home Yard Status", prefix: "Home Yard" },
  "yard-status": { code: "VHYS06I", title: "Yard Status", prefix: "Yard" },
};

function Table({ name, head, rows, onRow, selected }: { name: string; head: string[]; rows: ReactNode[][]; onRow?: (i: number) => void; selected?: number }) {
  return (
    <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white dark:border-line-dark dark:bg-panel-dark">
      <table aria-label={name} className="w-full border-collapse text-[13px]">
        <thead><tr>{head.map((h, i) => <th key={i} className="whitespace-nowrap border-b border-slate-200 bg-slate-50 px-2 py-1 text-left dark:border-line-dark dark:bg-[#12263b]">{h}</th>)}</tr></thead>
        <tbody>{rows.map((r, i) => (
          <tr key={i} onClick={onRow ? () => onRow(i) : undefined} aria-selected={selected === i}
            className={`${onRow ? "cursor-pointer hover:bg-sky-50 dark:hover:bg-[#16304a]" : ""} ${selected === i ? "bg-sky-100 dark:bg-[#1b3a57]" : "even:bg-slate-50 dark:even:bg-[#0f2032]"}`}>
            {r.map((v, j) => <td key={j} className="whitespace-pre px-2 py-1">{v}</td>)}
          </tr>))}
        </tbody>
      </table>
    </div>
  );
}

type Result =
  | { kind: "cars"; criteria: Checked; cars: StatusCar[]; message?: string | null }
  | { kind: "nogo"; criteria: Checked; incidents: NogoIncident[]; message?: string | null }
  | { kind: "summary"; criteria: Checked; header: string; cars: SummaryCar[]; footer?: string | null; message?: string | null };

export function HomeYardStatusPage() {
  return <StatusReportPage family="home-yard-status" />;
}

export function YardStatusPage() {
  return <StatusReportPage family="yard-status" />;
}

export function StatusReportPage({ family }: { family: StatusFamily }) {
  const app = useApp();
  const fam = FAMILY[family];
  const home = family === "home-yard-status";
  const [o, setO] = useState<StatusReportOptions | null>(null);
  const [c, setC] = useState<StatusCriteria>({ option: "base", yard: "", status: "", narr: "", carFrom: "", carTo: "" });
  const [printer, setPrinter] = useState("");
  const [result, setResult] = useState<Result | null>(null);
  const [carIndex, setCarIndex] = useState(0);
  const [incidents, setIncidents] = useState<{ list: StatusIncident[]; message?: string | null } | null>(null);
  const [narr, setNarr] = useState<string[] | null>(null);
  const [selected, setSelected] = useState<number | undefined>();
  const [types, setTypes] = useState<TypeSummary | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const start = (opts: StatusReportOptions): StatusCriteria => ({ option: "base", yard: "", status: "", narr: opts.narr, carFrom: opts.carFrom, carTo: opts.carTo });
  useEffect(() => { statusOptions(family).then(x => { setO(x); setC(start(x)); }).catch(e => setError(message(e))); }, [family]);

  const run = async (f: () => Promise<void>) => {
    setBusy(true); setError("");
    try { await f(); } catch (e) { setError(message(e)); } finally { setBusy(false); }
  };
  const set = (k: keyof StatusCriteria) => (v: string) => setC(x => ({ ...x, [k]: v }));
  const showCar = (cars: StatusCar[], i: number) => run(async () => {
    setCarIndex(i); setNarr(null); setSelected(undefined);
    if (!cars[i]) { setIncidents(null); return; }
    const r = await statusIncidents(family, c, cars[i].car);
    setIncidents({ list: r.incidents, message: r.message });
  });
  const display = () => run(async () => {
    setResult(null); setIncidents(null); setNarr(null); setTypes(null); setSelected(undefined);
    if (c.option === "nogo") { const r = await statusNogo(family, c); setResult({ kind: "nogo", ...r }); return; }
    if (c.option === "summary") { const r = await statusSummary(family, c); setResult({ kind: "summary", ...r }); return; }
    const r = await statusCars(family, c);
    setResult({ kind: "cars", ...r });
    if (r.cars.length) await showCar(r.cars, 0);
  });
  const narrative = (i: number, car: string, incident: string, yard: string) => run(async () => { setSelected(i); setNarr(await statusNarrative(family, car, incident, yard)); });
  const print = () => run(async () => app.toast((await printStatus(family, c, printer)).message, "ok"));

  if (!o) return <><PageHead title={fam.title} code={fam.code} />
    {error ? <p role="alert" className="m-2 rounded-lg bg-red-50 px-3 py-2 text-red-800">{error}</p> : <p role="status" className="p-8 text-slate-500">Loading…</p>}</>;
  const withNarr = c.option === "base" || c.option === "status";
  return (
    <>
      <PageHead title={fam.title} code={fam.code} />
      <section aria-label="Criteria" className={panel}>
        <fieldset className="mb-3 flex flex-wrap gap-4 text-[13px]">
          <legend className="mb-1 font-semibold text-slate-600 dark:text-[#c9d6e4]">Report</legend>
          {OPTIONS.map(([v, l]) => <label key={v}><input type="radio" name="option" checked={c.option === v} onChange={() => set("option")(v)} /> {fam.prefix} {l}</label>)}
        </fieldset>
        <div className="flex flex-wrap items-end gap-4">
          <label className={label}>Yard
            <input className={`${field} w-24`} list="hys-yards" value={c.yard} maxLength={3} onChange={e => set("yard")(e.target.value.toUpperCase())} />
            <datalist id="hys-yards">{o.yards.map(y => <option key={y} value={y} />)}</datalist>
          </label>
          {c.option === "status" && (
            <label className={label}>Status
              <select className={field} value={c.status} onChange={e => set("status")(e.target.value)}>
                <option value="">Choose a status</option>
                {o.statuses.map(s => <option key={s.status} value={s.status}>{s.status} {s.description}</option>)}
              </select>
            </label>)}
          {withNarr && (
            <label className={label}>Narr (Y/N)
              <select className={field} value={c.narr} onChange={e => set("narr")(e.target.value)}>{o.narrChoices.map(n => <option key={n} value={n}>{n}</option>)}</select>
            </label>)}
          <label className={label}>Car from<input className={`${field} w-20`} value={c.carFrom} maxLength={4} onChange={e => set("carFrom")(e.target.value)} /></label>
          <label className={label}>Car to<input className={`${field} w-20`} value={c.carTo} maxLength={4} onChange={e => set("carTo")(e.target.value)} /></label>
          <button type="button" className={`${button} bg-bart-600 text-white`} disabled={busy} onClick={display}>Display</button>
          <label className={label}>Printer
            <select className={field} value={printer} onChange={e => setPrinter(e.target.value)}>
              <option value="">Choose a printer for Print</option>
              {o.printers.map(p => <option key={p.vtam} value={p.vtam}>{p.description} ({p.vtam})</option>)}
            </select>
          </label>
          <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} disabled={busy || !printer} onClick={print}>Print</button>
          <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} onClick={() => { setC(start(o)); setResult(null); setError(""); }}>Clear</button>
        </div>
        {error && <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{error}</p>}
      </section>

      {result && (
        <section aria-label="Report" className={panel}>
          <h2 className="mb-3 font-semibold">{result.criteria.title}</h2>
          {result.message && <p className="mb-3 rounded-lg bg-amber-50 px-3 py-2 text-amber-900 dark:bg-amber-950/40 dark:text-amber-200">{result.message}</p>}

          {result.kind === "cars" && result.cars.length > 0 && (() => {
            const car = result.cars[carIndex];
            return <>
              <div className="mb-3 flex flex-wrap items-center gap-x-6 gap-y-1 text-sm">
                <button type="button" className={`${button} border border-slate-300`} disabled={busy || carIndex === 0} onClick={() => showCar(result.cars, carIndex - 1)}>Prv</button>
                <span>Car <b>{car.car}</b></span><span>Status <b className="whitespace-pre">{car.status}</b></span><span>MTBI {car.mtbi}</span>
                <span>Yard {car.location}</span><span>ATO {car.ato}</span><span>Cum Hrs {car.hours}:{String(car.minutes).padStart(2, "0")}</span><span>Home Yrd {car.homeYard}</span>
                <button type="button" className={`${button} border border-slate-300`} disabled={busy || carIndex >= result.cars.length - 1} onClick={() => showCar(result.cars, carIndex + 1)}>Nxt</button>
              </div>
              {incidents?.message && <p className="mb-2 text-slate-500">{incidents.message}</p>}
              {incidents && incidents.list.length > 0 && (
                <Table name="Incidents" head={["Incd No", "Symp", "Incd Loc", "Incd Date", "Incd Time", "Status", "Direct."]} selected={selected}
                  onRow={result.criteria.narr === "Y" ? i => narrative(i, car.car, incidents.list[i].incident, home ? car.homeYard : car.narrativeYard ?? car.location) : undefined}
                  rows={incidents.list.map(x => [x.incident, x.symptom, x.location, x.date, x.time, x.status, x.direction])} />)}
            </>;
          })()}

          {result.kind === "nogo" && result.incidents.length > 0 && (
            <Table name="NOGO incidents" head={["Car No", "Status", home ? "Curr Loc" : "Home Yrd", "Incd Date", "Incd Time", "Incd Loc", "Direct.", "Incd No"]} selected={selected}
              onRow={i => narrative(i, result.incidents[i].incidentCar, result.incidents[i].incident, result.criteria.yard)}
              rows={result.incidents.map(x => [x.car, x.status, home ? x.location : x.homeYard, x.date, x.time, x.incidentLocation, x.direction, x.incident])} />)}

          {result.kind === "summary" && <>
            <p className="mb-2 font-semibold">{result.header}{result.footer ? ` ${result.footer}` : ""}</p>
            {result.cars.length > 0 && <Table name="Cars" head={home ? ["Car No", "Status", "Curr Loc", "Home Yrd"] : ["Car No", "Status", "Home Yrd", "MTBI"]}
              rows={result.cars.map(x => home ? [`${x.car}${x.atoMissing ? " *" : ""}`, x.status, x.location, x.homeYard] : [`${x.car}${x.atoMissing ? " *" : ""}`, x.status, x.homeYard, x.mtbi])} />}
            {result.cars.length > 0 && (
              <button type="button" className={`${button} mt-3 border border-slate-300`} disabled={busy}
                onClick={() => run(async () => setTypes(await statusTypeSummary(family, result.criteria.yard)))}>Summary</button>)}
            {types && types.rows.length > 0 && <div className="mt-3"><Table name="Summary by car type" head={["Car Type", ...types.headings]}
              rows={types.rows.map(r => [r.name, ...[...r.byType, r.total].map(n => (r.name ? n : ""))])} /></div>}
          </>}

          {narr && <pre aria-label="Narrative" className="mt-3 whitespace-pre-wrap rounded-lg bg-slate-50 p-3 font-mono text-[13px] dark:bg-[#0a1624]">{narr.join("\n")}</pre>}
        </section>)}
    </>
  );
}
