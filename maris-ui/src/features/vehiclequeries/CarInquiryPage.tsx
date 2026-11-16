/* Car status / history inquiry (old VHSV07I asks for the car, VHSV06I shows it). */
import { useState, type ReactNode } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { carInquiry, carInquiryIncident, carInquiryRepair, carInquiryRepairs, type CarInquiry, type Repair, type RepairDetails } from "./api";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const panel = "mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark";
const pre = "whitespace-pre-wrap rounded-lg bg-slate-50 p-3 font-mono text-[13px] dark:bg-[#0a1624]";
const message = (e: unknown) => (e instanceof Error ? e.message : String(e));

function Table({ name, head, rows, onRow, selected }: { name: string; head: string[]; rows: ReactNode[][]; onRow?: (i: number) => void; selected?: number }) {
  return (
    <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white dark:border-line-dark dark:bg-panel-dark">
      <table aria-label={name} className="w-full border-collapse text-[13px]">
        <thead><tr>{head.map(h => <th key={h} className="whitespace-nowrap border-b border-slate-200 bg-slate-50 px-2 py-1 text-left dark:border-line-dark dark:bg-[#12263b]">{h}</th>)}</tr></thead>
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

export function CarInquiryPage() {
  const [car, setCar] = useState("");
  const [inq, setInq] = useState<CarInquiry | null>(null);
  const [selected, setSelected] = useState<number | undefined>();
  const [incident, setIncident] = useState<{ narrative: string[]; consist: string; cars: string[] } | null>(null);
  const [repairs, setRepairs] = useState<Repair[] | null>(null);
  const [repair, setRepair] = useState<{ index: number; details: RepairDetails } | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const run = async (f: () => Promise<void>) => { setBusy(true); setError(""); try { await f(); } catch (e) { setError(message(e)); } finally { setBusy(false); } };
  const pick = (r: CarInquiry, i: number) => run(async () => {
    setSelected(i); setRepair(null); const no = r.incidents[i].incident;
    setIncident(await carInquiryIncident(no));
    const list = await carInquiryRepairs(no);
    setRepairs(list);
    if (list.length) setRepair({ index: 0, details: await carInquiryRepair(no, list[0].document) });
  });
  const display = () => run(async () => {
    setInq(null); setIncident(null); setRepairs(null);
    const r = await carInquiry(car.trim()); setInq(r);
    if (r.incidents.length) await pick(r, 0);
  });
  return (
    <>
      <PageHead title="Car Status / History" code="VHSV07I" />
      <section aria-label="Criteria" className={panel}>
        <div className="flex flex-wrap items-end gap-4">
          <label className={label}>Car Number<input className={`${field} w-24`} value={car} maxLength={4} onChange={e => setCar(e.target.value)} /></label>
          <button type="button" className={`${button} bg-bart-600 text-white`} disabled={busy} onClick={display}>Display</button>
          <button type="button" className={`${button} border border-slate-300`} onClick={() => { setCar(""); setInq(null); setError(""); }}>Clear</button>
        </div>
        {error && <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{error}</p>}
      </section>
      {inq && (
        <section aria-label="Report" className={panel}>
          <h2 className="mb-3 font-semibold">{inq.title}</h2>
          {inq.car && <p className="mb-2 text-sm">Car <b>{inq.car.carShown}</b> · Status <span className="whitespace-pre">{inq.car.status}</span> · Curr Loc {inq.car.location} · Home Yrd {inq.car.homeYard}
            · ATO {inq.car.ato} · War {inq.car.warranty} · MTBI {inq.car.mtbi} · Cum Hrs {inq.car.cumulativeHours}</p>}
          {inq.moveReason && <p className="mb-3 text-sm">Move reason {inq.moveReason.code} {inq.moveReason.description} · {inq.moveReason.subLocation}</p>}
          {inq.incidents.length > 0 && <Table name="Incidents" head={["Incd No", "Date Incd", "Time Incd", "Symp", "Incd Loc", "Stat", "Dir", "Def", "Cum Hrs"]} selected={selected}
            onRow={i => pick(inq, i)} rows={inq.incidents.map(i => [i.incident, i.date, i.time, i.symptom, i.location, i.status, i.direction, i.deferredPriority, i.hours])} />}
          {incident && <>
            {incident.consist && <p className="mt-3 text-sm">Consist {incident.consist}: <span className="font-mono">{incident.cars.join("  ")}</span></p>}
            <pre aria-label="Narrative" className={`mt-3 ${pre}`}>{incident.narrative.join("\n")}</pre>
          </>}
          {repairs && repairs.length > 0 && selected !== undefined && <div className="mt-3">
            <Table name="Repairs" head={["Rpr Doc", "System/Sub", "Disp Cd", "Ser No ON", "Ser No OFF", "PCT No."]} selected={repair?.index}
              onRow={i => run(async () => setRepair({ index: i, details: await carInquiryRepair(inq.incidents[selected].incident, repairs[i].document) }))}
              rows={repairs.map(r => [r.document, `${r.system}/${r.subSystem}`, r.disposition, r.serialOn, r.serialOff, r.partControl])} />
          </div>}
          {repair && <div className="mt-3 grid gap-3 md:grid-cols-2">
            <pre aria-label="Repair narrative" className={pre}>{repair.details.narrative.join("\n")}</pre>
            <Table name="Components" head={["Cmp Doc No", "Cmp Cd", "Cmp Ser No OFF", "Cmp Disp", "Cmp Fault"]}
              rows={repair.details.components.map(x => [x.document, x.component, x.serialOff, x.disposition, x.fault])} />
          </div>}
        </section>)}
    </>
  );
}
