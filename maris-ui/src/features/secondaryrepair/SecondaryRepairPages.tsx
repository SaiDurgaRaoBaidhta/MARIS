/* Secondary repair, read-only screens: stock inquiry (old VHSR05I) with the job history (VHSR10I), job status (VHSR11I),
 * reprint job form (VHSR15I). */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { useApp } from "@/shared/lib/app-state";
import { jobHistory, openJob, reprint, reprintOptions, stockInquiry, stockOptions, subJob, type Code, type Job, type Printer, type StockInquiry, type SubJobDetails } from "./api";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const card = "mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark";
const errText = (e: unknown) => e instanceof Error ? e.message : String(e);
const Alert = ({ text }: { text: string }) => text ? <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{text}</p> : null;
const Note = ({ text }: { text?: string | null }) => text ? <p className="mt-3 rounded-lg bg-amber-50 px-3 py-2 text-amber-900 dark:bg-amber-950/40 dark:text-amber-200">{text}</p> : null;

function Table({ name, head, rows }: { name: string; head: string[]; rows: React.ReactNode[][] }) {
  return <div className="mb-4 overflow-x-auto rounded-xl border border-slate-200 bg-white dark:border-line-dark dark:bg-panel-dark">
    <table aria-label={name} className="w-full border-collapse text-[13px]">
      <thead><tr>{head.map(h => <th key={h} className="whitespace-nowrap border-b border-slate-200 bg-slate-50 px-2 py-1 text-left dark:border-line-dark dark:bg-[#12263b]">{h}</th>)}</tr></thead>
      <tbody>{rows.map((r, i) => <tr key={i} className="even:bg-slate-50 dark:even:bg-[#0f2032]">{r.map((c, j) => <td key={j} className="px-2 py-1">{c}</td>)}</tr>)}</tbody>
    </table></div>;
}

/** a job: its header, its sub jobs, and one sub job's narratives, repairs and labor, with Previous / Next */
function JobView({ job }: { job: Job }) {
  const [index, setIndex] = useState(0);
  const [details, setDetails] = useState<SubJobDetails | null>(null);
  const [error, setError] = useState("");
  const current = job.subJobs[index];
  useEffect(() => {
    if (!current) return;
    setDetails(null);
    subJob(job.job, current.doc).then(setDetails).catch(e => setError(errText(e)));
  }, [job.job, current]);
  return <section aria-label={`Job ${job.job}`} className="mx-2 mb-6">
    <p className="mb-2 text-sm text-slate-600 dark:text-[#c9d6e4]">Job {job.job} · {job.status} · car {job.car} · {job.count} sub jobs ({job.openDocs} open)</p>
    {current && <>
      <div className="mb-2 flex items-center gap-2">
        <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} disabled={index === 0} onClick={() => setIndex(i => i - 1)}>Previous</button>
        <span className="text-sm">Sub job {index + 1} of {job.count} · doc {current.doc} · {current.stockNo} · {current.serialNo} · {current.status}</span>
        <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} disabled={index >= job.count - 1} onClick={() => setIndex(i => i + 1)}>Next</button>
      </div>
      <Alert text={error} />
      {details && <>
        {details.narrative.length > 0 && <div className="mb-3"><h3 className="font-semibold">Narrative</h3>{details.narrative.map((n, i) => <p key={i}>{n}</p>)}</div>}
        {details.repairNarrative.length > 0 && <div className="mb-3"><h3 className="font-semibold">Repair narrative</h3>{details.repairNarrative.map((n, i) => <p key={i}>{n}</p>)}</div>}
        {details.repairs.length > 0 && <Table name="Repairs" head={["Doc", "Component", "Fault", "Count", "Ser No", "Stock No"]}
          rows={details.repairs.map(r => [r.doc, r.component, r.faultCode, r.count, r.serialNo, r.stockNo])} />}
        {details.labor.length > 0 && <Table name="Labor" head={["Doc", "Date", "Employee", "Position", "Hours", "Shift", "Function", "Work order"]}
          rows={details.labor.map(l => [l.doc, l.date, l.employee, l.position, l.hours, l.shift, l.workFunction, l.workOrder])} />}
      </>}
    </>}
  </section>;
}

export function StockInquiryPage() {
  const [statuses, setStatuses] = useState<Code[]>([]), [storage, setStorage] = useState<string[]>([]);
  const [c, setC] = useState({ stockNo: "", serialNo: "", status: "", longTermStorage: "" });
  const [result, setResult] = useState<StockInquiry | null>(null), [history, setHistory] = useState<Job | null>(null);
  const [error, setError] = useState(""), [busy, setBusy] = useState(false);
  useEffect(() => { stockOptions().then(o => { setStatuses(o.statuses); setStorage(o.longTermStorage); }).catch(e => setError(errText(e))); }, []);
  const run = async (f: () => Promise<void>) => { setBusy(true); setError(""); try { await f(); } catch (e) { setError(errText(e)); } finally { setBusy(false); } };
  return <>
    <PageHead title="Secondary Repair – Inquiry Stock" code="VHSR05I" />
    <section aria-label="Criteria" className={card}>
      <div className="flex flex-wrap items-end gap-4">
        <label className={label}>Stock No<input className={`${field} uppercase`} maxLength={9} value={c.stockNo} onChange={e => setC({ ...c, stockNo: e.target.value.toUpperCase() })} /></label>
        <label className={label}>Ser No<input className={field} maxLength={12} value={c.serialNo} onChange={e => setC({ ...c, serialNo: e.target.value })} /></label>
        <label className={label}>Sub job status<select className={field} value={c.status} onChange={e => setC({ ...c, status: e.target.value })}>
          <option value="">Any</option>{statuses.map(s => <option key={s.code} value={s.code}>{s.code} – {s.description}</option>)}</select></label>
        <label className={label}>Long term storage<select className={field} value={c.longTermStorage} onChange={e => setC({ ...c, longTermStorage: e.target.value })}>
          <option value="">Any</option>{storage.map(s => <option key={s}>{s}</option>)}</select></label>
        <button type="button" className={`${button} bg-bart-600 text-white`} disabled={busy} onClick={() => run(async () => { setHistory(null); setResult(await stockInquiry(c)); })}>List</button>
        <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} onClick={() => { setC({ stockNo: "", serialNo: "", status: "", longTermStorage: "" }); setResult(null); setHistory(null); setError(""); }}>Clear</button>
      </div>
      <Alert text={error} /><Note text={result?.message} /><Note text={result?.note} />
    </section>
    {result && result.count > 0 && <section className="mx-2">
      <p className="mb-2 text-sm"><strong>{result.description}</strong> · {result.count} jobs · {result.totalQuantityIn} in</p>
      <Table name="Jobs" head={["Job No", "Doc", "Ser No", "Qty In", "From", "Type", "Date In", "EQ/Car", "Area", "Stat", "Date Out"]}
        rows={result.jobs.map(j => [<button key="j" type="button" className="text-bart-600 underline" onClick={() => run(async () => setHistory(await jobHistory(j.job)))}>{j.job}</button>,
          j.doc, j.serialNo, j.quantityIn, j.from, j.workType, j.dateIn, j.carOrEquipment, j.area, j.status, j.dateOut])} />
    </section>}
    {history && <JobView job={history} />}
  </>;
}

export function JobStatusPage() {
  const [jobNo, setJobNo] = useState(""), [job, setJob] = useState<Job | null>(null);
  const [error, setError] = useState(""), [busy, setBusy] = useState(false);
  const list = async () => { setBusy(true); setError(""); setJob(null); try { setJob(await openJob(jobNo)); } catch (e) { setError(errText(e)); } finally { setBusy(false); } };
  return <>
    <PageHead title="Secondary Repair – Status Job" code="VHSR11I" />
    <section aria-label="Criteria" className={card}>
      <form className="flex flex-wrap items-end gap-4" onSubmit={e => { e.preventDefault(); list(); }}>
        <label className={label}>Job No<input className={`${field} uppercase`} maxLength={6} value={jobNo} onChange={e => setJobNo(e.target.value.toUpperCase())} /></label>
        <button type="submit" className={`${button} bg-bart-600 text-white`} disabled={busy}>List</button>
        <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} onClick={() => { setJobNo(""); setJob(null); setError(""); }}>Clear</button>
      </form>
      <Alert text={error} />
    </section>
    {job && <JobView job={job} />}
  </>;
}

export function ReprintPage() {
  const app = useApp();
  const [printers, setPrinters] = useState<Printer[]>([]), [printer, setPrinter] = useState("");
  const [jobNo, setJobNo] = useState(""), [doc, setDoc] = useState("1");
  const [error, setError] = useState(""), [busy, setBusy] = useState(false);
  useEffect(() => { reprintOptions().then(o => { setPrinters(o.printers); setPrinter(p => p || o.defaultPrinter || ""); }).catch(e => setError(errText(e))); }, []);
  const print = async () => { setBusy(true); setError(""); try { app.toast((await reprint(jobNo, Number(doc) || 1, printer)).message, "ok"); } catch (e) { setError(errText(e)); } finally { setBusy(false); } };
  return <>
    <PageHead title="Secondary Repair – Reprint Job Form" code="VHSR15I" />
    <section aria-label="Reprint" className={card}>
      <div className="flex flex-wrap items-end gap-4">
        <label className={label}>Job No<input className={`${field} uppercase`} maxLength={6} value={jobNo} onChange={e => setJobNo(e.target.value.toUpperCase())} /></label>
        <label className={label}>Doc No<input className={`${field} w-20`} inputMode="numeric" maxLength={2} value={doc} onChange={e => setDoc(e.target.value)} /></label>
        <label className={label}>Printer<select className={field} value={printer} onChange={e => setPrinter(e.target.value)}>
          <option value="">Choose a printer</option>{printers.map(p => <option key={p.vtam} value={p.vtam}>{p.description} ({p.vtam})</option>)}</select></label>
        <button type="button" className={`${button} bg-bart-600 text-white`} disabled={busy || !printer} onClick={print}>Print</button>
      </div>
      <Alert text={error} />
    </section>
  </>;
}
