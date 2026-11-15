/* Secondary repair job updates: in the door (old VHSR01I) -> job split (VHSR08I) -> comments (VHSR02I); repair detail
 * (VHSR09I) -> labor detail and close (VHSR03I) -> comments. A page the old screen opened is shown in place, with the
 * fields the old parent passed (jobNo, subJobDocNo, jobIndicator); a page opened from elsewhere takes the same names
 * from the screen key's query (egl:VHSR02I?jobNo=...). */
import { useEffect, useState, type ReactNode } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { useApp } from "@/shared/lib/app-state";
import * as api from "./jobsApi";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const primary = `${button} bg-bart-600 text-white`;
const plain = `${button} border border-slate-300 dark:border-line-dark`;
const card = "mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark";
const errText = (e: unknown) => e instanceof Error ? e.message : String(e);
const Alert = ({ text }: { text: string }) => text ? <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{text}</p> : null;
const Done = ({ text }: { text?: string | null }) => text ? <p role="status" className="mt-3 rounded-lg bg-emerald-50 px-3 py-2 text-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-200">{text}</p> : null;

export interface JobParams { jobNo?: string; subJobDocNo?: number; jobIndicator?: string }

/** the parent's fields: props first, else the query of the screen key in front (egl:VHSR02I?jobNo=A1&subJobDocNo=2) */
function useParams(p: JobParams): JobParams {
  const app = useApp();
  const top = app.stack[app.stack.length - 1];
  const key = top && top.kind === "screen" ? top.key : "";
  const query = new URLSearchParams(key.includes("?") ? key.slice(key.indexOf("?") + 1) : "");
  const doc = p.subJobDocNo ?? (query.get("subJobDocNo") ? Number(query.get("subJobDocNo")) : undefined);
  return { jobNo: p.jobNo ?? query.get("jobNo") ?? undefined, subJobDocNo: doc, jobIndicator: p.jobIndicator ?? query.get("jobIndicator") ?? undefined };
}

/** runs an action with busy / error / message state */
function useRun() {
  const [busy, setBusy] = useState(false), [error, setError] = useState(""), [done, setDone] = useState("");
  const run = async (f: () => Promise<string | void>) => {
    setBusy(true); setError(""); setDone("");
    try { const m = await f(); if (m) setDone(m); } catch (e) { setError(errText(e)); } finally { setBusy(false); }
  };
  return { busy, error, done, run, setError };
}

/** a child page shown in place of its parent until Return */
function Child({ children, onReturn }: { children: ReactNode; onReturn: () => void }) {
  return <>{children}<div className="mx-2 mb-6"><button type="button" className={plain} onClick={onReturn}>Return</button></div></>;
}

function Table({ name, head, rows }: { name: string; head: string[]; rows: ReactNode[][] }) {
  return <div className="mb-3 overflow-x-auto rounded-xl border border-slate-200 dark:border-line-dark">
    <table aria-label={name} className="w-full border-collapse text-[13px]">
      <thead><tr>{head.map(h => <th key={h} className="border-b border-slate-200 bg-slate-50 px-2 py-1 text-left dark:border-line-dark dark:bg-[#12263b]">{h}</th>)}</tr></thead>
      <tbody>{rows.map((r, i) => <tr key={i}>{r.map((c, j) => <td key={j} className="px-2 py-1">{c}</td>)}</tr>)}</tbody>
    </table></div>;
}

const Select = ({ name, value, onChange, codes }: { name: string; value: string; onChange: (v: string) => void; codes: api.Code[] }) =>
  <label className={label}>{name}<select className={field} value={value} onChange={e => onChange(e.target.value)}>
    <option value="">Select…</option>{codes.map(c => <option key={c.code} value={c.code}>{c.code}{c.description ? ` – ${c.description}` : ""}</option>)}</select></label>;
const Input = ({ name, value, onChange, type = "text", max }: { name: string; value: string; onChange: (v: string) => void; type?: string; max?: number }) =>
  <label className={label}>{name}<input className={field} type={type} maxLength={max} value={value} onChange={e => onChange(e.target.value)} /></label>;

// ---- VHSR02I: comments --------------------------------------------------------------------------------------------

export function CommentsPage(props: JobParams) {
  const p = useParams(props);
  const [jobNo, setJobNo] = useState(p.jobNo ?? ""), [doc, setDoc] = useState(String(p.subJobDocNo ?? 1)), [kind, setKind] = useState(p.jobIndicator ?? "JOB");
  const [kinds, setKinds] = useState<string[]>(["JOB", "REPAIR"]);
  const [list, setList] = useState<api.Comments | null>(null), [current, setCurrent] = useState<api.Comment | null>(null);
  const [edit, setEdit] = useState(""), [text, setText] = useState("");
  const r = useRun();
  const fixed = !!p.jobNo;
  useEffect(() => { api.commentKinds().then(setKinds).catch(() => { /* keep the defaults */ }); }, []);
  const show = (c: api.Comments) => { setList(c); setCurrent(c.comment); setEdit(c.comment?.text ?? ""); };
  const load = () => r.run(async () => { show(await api.listComments(jobNo, Number(doc) || 0, kind)); });
  useEffect(() => { if (fixed) void load(); }, []);   // eslint-disable-line react-hooks/exhaustive-deps
  const page = (n: number) => r.run(async () => { const c = await api.comment(jobNo, Number(doc), kind, n); setCurrent(c); setEdit(c.text); });
  return <>
    <PageHead title="Secondary Repair – Comments" code="VHSR02I" />
    <section aria-label="Sub job" className={card}>
      <div className="flex flex-wrap items-end gap-4">
        <Input name="Job No" value={jobNo} onChange={v => setJobNo(v.toUpperCase())} max={6} />
        <Input name="Doc No" value={doc} onChange={setDoc} max={4} />
        <fieldset className="flex gap-3" aria-label="Kind">{kinds.map(k =>
          <label key={k} className="flex items-center gap-1 text-sm"><input type="radio" name="kind" checked={kind === k} disabled={fixed} onChange={() => setKind(k)} />{k === kinds[0] ? "Sub Job" : "Repr"}</label>)}</fieldset>
        {!fixed && <button type="button" className={primary} disabled={r.busy} onClick={load}>List</button>}
      </div>
      <Alert text={r.error} /><Done text={r.done} />
    </section>
    {list && <section aria-label="Comments" className={card}>
      {current ? <>
        <div className="mb-2 flex items-center gap-2 text-sm">
          <button type="button" className={plain} disabled={current.number <= 1} onClick={() => page(current.number - 1)}>Prv</button>
          <span>Comment {current.number} of {list.count} · {current.userGroup} · {current.date} {current.time}</span>
          <button type="button" className={plain} disabled={current.number >= list.count} onClick={() => page(current.number + 1)}>Nxt</button>
        </div>
        <label className={label}>Current comment<textarea className={`${field} h-24`} value={edit} onChange={e => setEdit(e.target.value)} /></label>
        <button type="button" className={`${plain} mt-2`} disabled={r.busy} onClick={() => r.run(async () => api.updateComment(jobNo, Number(doc), kind, current.number, edit).then(x => x.message))}>Update</button>
      </> : <p className="text-sm text-slate-500">No comments yet.</p>}
      <label className={`${label} mt-4`}>New comment<textarea className={`${field} h-24`} maxLength={1000} value={text} onChange={e => setText(e.target.value)} /></label>
      <button type="button" className={`${primary} mt-2`} disabled={r.busy} onClick={() => r.run(async () => {
        const a = await api.addComment(jobNo, Number(doc), kind, text); setText(""); show(a.comments); return a.message;
      })}>Add</button>
    </section>}
  </>;
}

// ---- VHSR08I: job split -------------------------------------------------------------------------------------------

export function JobSplitPage(props: JobParams) {
  const p = useParams(props);
  const [opts, setOpts] = useState<{ workTypes: api.Code[]; repairAreas: api.Code[]; workType: string } | null>(null);
  const [jobNo, setJobNo] = useState(p.jobNo ?? ""), [job, setJob] = useState<api.SplitJob | null>(null);
  const [f, setF] = useState({ workType: "", dateIn: "", repairArea: "", stockNo: "", serialNo: "", quantity: "1", comment: "" });
  const [comments, setComments] = useState<number | null>(null);
  const r = useRun();
  useEffect(() => { api.splitOptions().then(o => { setOpts(o); setF(x => ({ ...x, workType: o.workType })); }).catch(e => r.setError(errText(e))); }, []);   // eslint-disable-line react-hooks/exhaustive-deps
  const load = (j = jobNo) => r.run(async () => { const s = await api.splitJob(j); setJob(s); setF(x => ({ ...x, repairArea: s.repairArea })); });
  useEffect(() => { if (p.jobNo) void load(p.jobNo); }, []);   // eslint-disable-line react-hooks/exhaustive-deps
  if (job && comments !== null) return <Child onReturn={() => { setComments(null); void load(job.job); }}><CommentsPage jobNo={job.job} subJobDocNo={comments} jobIndicator="JOB" /></Child>;
  return <>
    <PageHead title="Secondary Repair – Job Split" code="VHSR08I" />
    <section aria-label="Job" className={card}>
      <div className="flex flex-wrap items-end gap-4">
        <Input name="Job No" value={jobNo} onChange={v => setJobNo(v.toUpperCase())} max={6} />
        <button type="button" className={primary} disabled={r.busy} onClick={() => load()}>Find</button>
      </div>
      <Alert text={r.error} /><Done text={r.done} />
    </section>
    {job && opts && <>
      <section className="mx-2"><p className="mb-2 text-sm">Job {job.job} · {job.status} {job.car ? `· car ${job.car}` : `· ${job.dept} ${job.equipmentType} ${job.equipmentId}`}</p>
        <Table name="Previous sub job actions" head={["Doc No", "Area", "Comments", ""]} rows={job.subJobs.map(s => [s.doc, s.repairArea, s.comment,
          <button key={s.doc} type="button" className="text-bart-600 underline" onClick={() => setComments(s.doc)}>Comments</button>])} /></section>
      <section aria-label={`New sub job ${job.nextDoc}`} className={card}>
        <h3 className="mb-2 font-semibold">New sub job — doc {job.nextDoc}</h3>
        <div className="flex flex-wrap items-end gap-4">
          <Select name="Work Type" value={f.workType} onChange={v => setF({ ...f, workType: v })} codes={opts.workTypes} />
          <Input name="Date In" type="date" value={f.dateIn} onChange={v => setF({ ...f, dateIn: v })} />
          <Select name="Rep Area" value={f.repairArea} onChange={v => setF({ ...f, repairArea: v })} codes={opts.repairAreas} />
          <Input name="Stock No" value={f.stockNo} onChange={v => setF({ ...f, stockNo: v.toUpperCase() })} max={9} />
          <Input name="Ser No" value={f.serialNo} onChange={v => setF({ ...f, serialNo: v })} max={12} />
          <Input name="Batch Qty" value={f.quantity} onChange={v => setF({ ...f, quantity: v })} max={4} />
        </div>
        <label className={`${label} mt-3`}>Comment<textarea className={`${field} h-20`} value={f.comment} onChange={e => setF({ ...f, comment: e.target.value })} /></label>
        <button type="button" className={`${primary} mt-2`} disabled={r.busy} onClick={() => r.run(async () => {
          const c = await api.createSubJob(job.job, { ...f, quantity: Number(f.quantity) || 0 }); setJob(c.job);
          setF(x => ({ ...x, stockNo: "", serialNo: "", quantity: "1", comment: "" })); return c.message;
        })}>Create</button>
      </section>
    </>}
  </>;
}

// ---- VHSR01I: in the door -----------------------------------------------------------------------------------------

export function InDoorPage() {
  const [opts, setOpts] = useState<api.InDoorOptions | null>(null), [types, setTypes] = useState<api.Code[]>([]);
  const empty = { workType: "", dateIn: "", repairArea: "", car: "", dept: "", equipmentType: "", equipmentId: "", incident: "", sentFrom: "", stockNo: "", serialNo: "", quantity: "1", comment: "" };
  const [f, setF] = useState(empty), [created, setCreated] = useState(""), [split, setSplit] = useState(false);
  const r = useRun();
  useEffect(() => { api.inDoorOptions().then(o => { setOpts(o); setF(x => ({ ...x, workType: o.workType })); }).catch(e => r.setError(errText(e))); }, []);   // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { if (f.dept && opts && !opts.equipmentIdDepartments.includes(f.dept)) api.equipmentTypes(f.dept).then(setTypes).catch(() => setTypes([])); }, [f.dept, opts]);
  if (split && created) return <Child onReturn={() => setSplit(false)}><JobSplitPage jobNo={created} /></Child>;
  const byId = !!opts && opts.equipmentIdDepartments.includes(f.dept);
  return <>
    <PageHead title="Secondary Repair – In the Door" code="VHSR01I" />
    {opts && <section aria-label="New job" className={card}>
      <div className="flex flex-wrap items-end gap-4">
        <Select name="Work Type" value={f.workType} onChange={v => setF({ ...f, workType: v })} codes={opts.workTypes} />
        <Input name="Date In" type="date" value={f.dateIn} onChange={v => setF({ ...f, dateIn: v })} />
        <Select name="Rep Area" value={f.repairArea} onChange={v => setF({ ...f, repairArea: v })} codes={opts.repairAreas} />
        <Input name="Sent From" value={f.sentFrom} onChange={v => setF({ ...f, sentFrom: v })} max={4} />
        <Input name="Car" value={f.car} onChange={v => setF({ ...f, car: v, ...(v ? { dept: "", equipmentType: "", equipmentId: "" } : {}) })} max={4} />
        <label className={label}>Dept<select className={field} value={f.dept} disabled={!!f.car} onChange={e => setF({ ...f, dept: e.target.value, equipmentType: "", equipmentId: "" })}>
          <option value="">—</option>{opts.departments.map(d => <option key={d}>{d}</option>)}</select></label>
        {f.dept && (byId ? <Input name="Eq ID" value={f.equipmentId} onChange={v => setF({ ...f, equipmentId: v.toUpperCase() })} max={7} />
          : <Select name="Eq Type" value={f.equipmentType} onChange={v => setF({ ...f, equipmentType: v })} codes={types} />)}
        <Input name="Incd No" value={f.incident} onChange={v => setF({ ...f, incident: v })} max={8} />
        <Input name="Stock No" value={f.stockNo} onChange={v => setF({ ...f, stockNo: v.toUpperCase() })} max={9} />
        <Input name="Serial No" value={f.serialNo} onChange={v => setF({ ...f, serialNo: v })} max={12} />
        <Input name="Batch Qty" value={f.quantity} onChange={v => setF({ ...f, quantity: v })} max={2} />
      </div>
      <label className={`${label} mt-3`}>Narrative<textarea className={`${field} h-20`} value={f.comment} onChange={e => setF({ ...f, comment: e.target.value })} /></label>
      <div className="mt-2 flex gap-2">
        <button type="button" className={primary} disabled={r.busy || !!created} onClick={() => r.run(async () => {
          const c = await api.createJob({ ...f, quantity: Number(f.quantity) || 0 }); setCreated(c.job); return c.message;
        })}>Create</button>
        <button type="button" className={plain} disabled={!created} onClick={() => setSplit(true)}>Job Split</button>
        <button type="button" className={plain} onClick={() => { setF({ ...empty, workType: opts.workType }); setCreated(""); r.setError(""); }}>Clear</button>
      </div>
      <Alert text={r.error} /><Done text={r.done} />
    </section>}
  </>;
}

// ---- VHSR03I: labor detail and close --------------------------------------------------------------------------------

const blankLabor = (): api.LaborLine[] => Array.from({ length: 6 }, () => ({ date: "", employee: "", hours: null }));

export function LaborPage(props: JobParams) {
  const p = useParams(props);
  const [jobNo, setJobNo] = useState(p.jobNo ?? ""), [doc, setDoc] = useState(String(p.subJobDocNo ?? 1));
  const [d, setD] = useState<api.LaborDetails | null>(null), [lines, setLines] = useState(blankLabor()), [comment, setComment] = useState("");
  const [comments, setComments] = useState(false);
  const r = useRun();
  const load = (j = jobNo, n = Number(doc) || 0) => r.run(async () => { setD(await api.laborDetails(j, n)); });
  useEffect(() => { if (p.jobNo) void load(p.jobNo, p.subJobDocNo ?? 1); }, []);   // eslint-disable-line react-hooks/exhaustive-deps
  if (d && comments) return <Child onReturn={() => { setComments(false); void load(d.job, d.doc); }}><CommentsPage jobNo={d.job} subJobDocNo={d.doc} jobIndicator="REPAIR" /></Child>;
  const set = (i: number, k: keyof api.LaborLine, v: string) => setLines(ls => ls.map((l, j) => j !== i ? l : { ...l, [k]: k === "hours" ? (v === "" ? null : Number(v)) : v }));
  return <>
    <PageHead title="Secondary Repair – Labor Detail" code="VHSR03I" />
    <section aria-label="Sub job" className={card}>
      <div className="flex flex-wrap items-end gap-4">
        <Input name="Job No" value={jobNo} onChange={v => setJobNo(v.toUpperCase())} max={6} />
        <Input name="Doc No" value={doc} onChange={setDoc} max={2} />
        <button type="button" className={primary} disabled={r.busy} onClick={() => load()}>List</button>
      </div>
      <Alert text={r.error} /><Done text={r.done || d?.note} />
    </section>
    {d && <section aria-label="Labor" className={card}>
      <p className="mb-2 text-sm">Sub job {d.job}-{d.doc} · {d.status} · in {d.dateIn} · out {d.dateOut}</p>
      <Table name="Labor details" head={["Date", "Empl No", "Hours"]} rows={d.labor.map(l => [l.date, l.employee, l.hours])} />
      {d.comments.length > 0 ? <p className="mb-2 text-sm">Repair comment: {d.comments[0].text}</p>
        : <label className={label}>Repair comment<textarea className={`${field} h-16`} value={comment} onChange={e => setComment(e.target.value)} /></label>}
      <h3 className="mt-3 font-semibold">New labor</h3>
      {lines.map((l, i) => <div key={i} className="mt-1 flex gap-2" aria-label={`Line ${i + 1}`}>
        <input aria-label={`Date ${i + 1}`} type="date" className={field} value={l.date} onChange={e => set(i, "date", e.target.value)} />
        <input aria-label={`Empl No ${i + 1}`} className={field} maxLength={5} value={l.employee} onChange={e => set(i, "employee", e.target.value)} />
        <input aria-label={`Hours ${i + 1}`} className={field} inputMode="decimal" value={l.hours ?? ""} onChange={e => set(i, "hours", e.target.value)} />
      </div>)}
      <div className="mt-3 flex gap-2">
        <button type="button" className={primary} disabled={r.busy} onClick={() => r.run(async () => {
          const a = await api.addLabor({ jobNo: d.job, doc: d.doc, lines: lines.filter(l => l.date || l.employee || l.hours), comment });
          setD(a.details); setLines(blankLabor()); setComment(""); setComments(true); return a.message;
        })}>Add Labor</button>
        <button type="button" className={plain} onClick={() => setComments(true)}>Comments</button>
        <button type="button" className={plain} disabled={r.busy || !d.canClose} onClick={() => r.run(async () => (await api.closeSubJob(d.job, d.doc)).message)}>Close Job</button>
      </div>
    </section>}
  </>;
}

// ---- VHSR09I: repair detail ------------------------------------------------------------------------------------------

const blankRepairs = (): api.RepairLine[] => Array.from({ length: 6 }, () => ({ stockNo: "", serialNo: "", component: "", faultCode: "", quantity: 1 }));

export function RepairDetailPage() {
  const [jobNo, setJobNo] = useState(""), [doc, setDoc] = useState("1"), [kit, setKit] = useState("");
  const [d, setD] = useState<api.RepairDetail | null>(null), [lines, setLines] = useState(blankRepairs()), [labor, setLabor] = useState(false);
  const r = useRun();
  if (d && labor) return <Child onReturn={() => setLabor(false)}><LaborPage jobNo={d.job} subJobDocNo={d.doc} /></Child>;
  const set = (i: number, k: keyof api.RepairLine, v: string) => setLines(ls => ls.map((l, j) => j !== i ? l : { ...l, [k]: k === "quantity" ? (v === "" ? null : Number(v)) : v.toUpperCase() }));
  const add = (noRepair: boolean) => r.run(async () => {
    const a = await api.addRepairs({ jobNo: d!.job, doc: d!.doc, kitNo: kit, noRepair, lines: lines.filter(l => l.stockNo || l.serialNo || l.component || l.faultCode) });
    setD(a.detail); setLines(blankRepairs()); setKit(""); if (a.message) setLabor(true); return a.message;
  });
  return <>
    <PageHead title="Secondary Repair – Repair Detail" code="VHSR09I" />
    <section aria-label="Sub job" className={card}>
      <div className="flex flex-wrap items-end gap-4">
        <Input name="Job No" value={jobNo} onChange={v => setJobNo(v.toUpperCase())} max={6} />
        <Input name="Doc No" value={doc} onChange={setDoc} max={4} />
        <button type="button" className={primary} disabled={r.busy} onClick={() => r.run(async () => { setD(await api.repairDetail(jobNo, Number(doc) || 0)); })}>List</button>
      </div>
      <Alert text={r.error} /><Done text={r.done} />
    </section>
    {d && <section aria-label="Repairs" className={card}>
      <Table name="Repair details" head={["Stock No", "Ser No", "Part", "Fault", "Qty"]} rows={d.repairs.map(x => [x.stockNo, x.serialNo, x.component, x.faultCode, x.quantity])} />
      <Input name="Kit" value={kit} onChange={v => setKit(v.toUpperCase())} max={9} />
      {lines.map((l, i) => <div key={i} className="mt-1 flex gap-2" aria-label={`Line ${i + 1}`}>
        {(["stockNo", "serialNo", "component", "faultCode", "quantity"] as const).map(k =>
          <input key={k} aria-label={`${k} ${i + 1}`} className={field} value={l[k] ?? ""} onChange={e => set(i, k, e.target.value)} />)}
      </div>)}
      <div className="mt-3 flex gap-2">
        <button type="button" className={primary} disabled={r.busy} onClick={() => add(false)}>Add Repair</button>
        <button type="button" className={plain} disabled={r.busy} onClick={() => add(true)}>No Repair</button>
        <button type="button" className={plain} onClick={() => setLabor(true)}>Labor Detail</button>
      </div>
    </section>}
  </>;
}
