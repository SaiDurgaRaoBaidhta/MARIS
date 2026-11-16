/* Vehicle repair: VHRV01I (repairs of an incident), VHRV03I (repairs of an event), VHRV04I (change or delete a repair) and
 * VHRV02I (a repair's components). The rules and messages are the middleware's (from the old screens). */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { useApp } from "@/shared/lib/app-state";
import * as api from "./api";
import type { ComponentInput, Components, IncidentRepairs, Options, RepairDetails, RepairInput, RepairScreen } from "./api";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const primary = `${button} bg-bart-600 text-white`, secondary = `${button} border border-slate-300 dark:border-line-dark`;
const panel = "mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark";
const th = "whitespace-nowrap border-b border-slate-200 bg-slate-50 px-2 py-1 text-left dark:border-line-dark dark:bg-[#12263b]";
const errText = (e: unknown) => e instanceof Error ? e.message : String(e);
const Alert = ({ text }: { text: string }) => text ? <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{text}</p> : null;

const TITLES: Record<RepairScreen, string> = { VHRV01I: "Vehicle Repair", VHRV03I: "Event Repair", VHRV04I: "Repair Maintenance" };
const EMPTY: RepairInput = { cause: "", system: "", subSystem: "", disposition: "", date: "", time: "", location: "", employee: "", warranty: "", laborHours: null,
  partControl: "", serialOn: "", serialOff: "", narrative: "" };

/** the narrative window (VHNAR01I, owned by vehicle maintenance) opened with the fields the old screens pass it */
function openNarrative(app: ReturnType<typeof useApp>, incd: string, d: RepairDetails | null, userGroup: string) {
  const n = d?.narratives[0];
  const q = new URLSearchParams(Object.entries({ incdNo: incd, reprDocNo: d?.document ?? "", narrNo: n?.number ?? "", currentRow: n?.number ?? "",
    maxRows: d?.narratives.length ?? 0, narr: n?.text ?? "", dateNarr: n?.date ?? "", timeNarr: n?.time ?? "", userGrp: n?.userGroup ?? userGroup })
    .filter(([, v]) => v !== "").map(([k, v]) => [k, String(v)]));
  app.open({ kind: "screen", key: `egl:VHNAR01I?${q}`, label: "Narrative" });
}

export function RepairPage({ screen }: { screen: RepairScreen }) {
  const app = useApp();
  const [opts, setOpts] = useState<Options | null>(null);
  const [incd, setIncd] = useState("");
  const [inc, setInc] = useState<IncidentRepairs | null>(null);
  const [sel, setSel] = useState<RepairDetails | null>(null);
  const [form, setForm] = useState<RepairInput>(EMPTY);
  const [error, setError] = useState(""), [busy, setBusy] = useState(false);
  useEffect(() => { if (screen !== "VHRV03I") api.options().then(setOpts).catch(e => setError(errText(e))); }, [screen]);
  const run = async (f: () => Promise<void>) => { setBusy(true); setError(""); try { await f(); } catch (e) { setError(errText(e)); } finally { setBusy(false); } };
  const set = (k: keyof RepairInput) => (e: { target: { value: string } }) => setForm(f => ({ ...f, [k]: k === "laborHours" ? (e.target.value === "" ? null : Number(e.target.value)) : e.target.value }));
  const list = async () => {
    setInc(null); setSel(null);
    const r = await api.incident(screen, incd);
    setInc(r);
    setForm({ ...EMPTY, ...(r.defaults ? { location: r.defaults.location, date: r.defaults.date, time: r.defaults.time, employee: r.defaults.employee } : {}) });
  };
  const choose = (doc: number) => run(async () => {
    const d = await api.repair(incd, doc);
    setSel(d);
    setForm({ cause: d.cause, system: d.system, subSystem: d.subSystem, disposition: d.disposition, date: d.date, time: d.time, location: d.location,
      employee: d.employee, warranty: d.warranty, laborHours: d.laborHours, partControl: d.partControl, serialOn: d.serialOn, serialOff: d.serialOff,
      narrative: d.narratives[0]?.text ?? "" });
  });
  const done = async (r: api.Result, relist = true) => { if (r.message) app.toast(r.message, r.done ? "ok" : "err"); if (relist && r.done) await list(); };
  const full = screen !== "VHRV03I";
  const input = (k: keyof RepairInput, text: string, max: number, disabled = false) =>
    <label className={label}>{text}<input className={field} maxLength={max} disabled={disabled} value={form[k] ?? ""} onChange={set(k)} /></label>;
  return (
    <>
      <PageHead title={TITLES[screen]} code={screen} />
      <section aria-label="Incident" className={panel}>
        <div className="flex flex-wrap items-end gap-4">
          <label className={label}>Incident No<input className={field} maxLength={8} value={incd} onChange={e => setIncd(e.target.value.toUpperCase())} /></label>
          <button type="button" className={primary} disabled={busy} onClick={() => run(list)}>List</button>
          <button type="button" className={secondary} onClick={() => { setIncd(""); setInc(null); setSel(null); setForm(EMPTY); setError(""); }}>Clear</button>
          {inc && <dl className="flex flex-wrap gap-4 text-[13px]">
            {([["Car No", inc.car], ["Incd Stat", inc.status], ["Con No", inc.consist], ["Repr Doc No", sel ? sel.document : inc.nextDocument]] as const).map(([k, v]) =>
              <div key={k}><dt className="font-semibold text-slate-500">{k}</dt><dd>{v}</dd></div>)}
          </dl>}
        </div>
        <Alert text={error} />
      </section>
      {inc && <>
        <div className="mx-2 mb-4 overflow-x-auto rounded-xl border border-slate-200 bg-white dark:border-line-dark dark:bg-panel-dark">
          <table aria-label="Previous repair actions" className="w-full border-collapse text-[13px]">
            <thead><tr>{["Repr Doc No", "Repair Date", "Narrative"].map(h => <th key={h} className={th}>{h}</th>)}</tr></thead>
            <tbody>{inc.repairs.map(r => <tr key={r.document} onClick={() => choose(r.document)} aria-selected={sel?.document === r.document}
              className="cursor-pointer even:bg-slate-50 aria-selected:bg-bart-50 dark:even:bg-[#0f2032]">
              <td className="px-2 py-1">{r.document}</td><td className="px-2 py-1">{r.date}</td><td className="px-2 py-1">{r.narrative}</td></tr>)}</tbody>
          </table>
        </div>
        <section aria-label="Repair" className={panel}>
          <div className="flex flex-wrap items-end gap-4">
            {full && <>
              <label className={label}>Repair Cause<select className={field} value={form.cause} onChange={set("cause")} disabled={screen === "VHRV01I" && !!sel}>
                <option value="" />{opts?.causes.map(c => <option key={c.code} value={c.code}>{c.code} {c.description}</option>)}</select></label>
              {input("system", "Sys", 2, screen === "VHRV01I" && !!sel)}{input("subSystem", "Sub", 2, screen === "VHRV01I" && !!sel)}
              <label className={label}>Repair Disp<select className={field} value={form.disposition} onChange={set("disposition")} disabled={screen === "VHRV01I" && !!sel}>
                <option value="" />{opts?.dispositions.map(c => <option key={c.code} value={c.code}>{c.code} {c.description}</option>)}</select></label>
            </>}
            {input("date", "Repair Date (MM-DD-YYYY)", 10, screen === "VHRV01I" && !!sel)}{input("time", "Repair Time (HHMM)", 4, screen === "VHRV01I" && !!sel)}
            {full && <>
              <label className={label}>Repr Loc<select className={field} value={form.location} onChange={set("location")} disabled={screen === "VHRV01I" && !!sel}>
                <option value="" />{opts?.yards.map(y => <option key={y}>{y}</option>)}</select></label>
              {input("employee", "Emp No", 5, screen === "VHRV01I" && !!sel)}{input("warranty", "Warranty", 1, screen === "VHRV01I" && !!sel)}
              <label className={label}>Labor Hrs<input type="number" min={0} max={999} className={field} disabled={screen === "VHRV01I" && !!sel}
                value={form.laborHours ?? ""} onChange={set("laborHours")} /></label>
              {input("partControl", "PCT No.", 8, screen === "VHRV01I" && !!sel)}{input("serialOn", "Ser. No. On", 12, screen === "VHRV01I" && !!sel)}
              {input("serialOff", "Ser. Off/Rep.", 12, screen === "VHRV01I" && !!sel)}
            </>}
          </div>
          {sel ? <div className="mt-3 text-[13px]">{sel.narratives.map(n => <p key={n.number}><b>{n.number}</b> {n.date} {n.time} {n.userGroup}: {n.text}</p>)}</div>
            : screen !== "VHRV04I" && <label className={`${label} mt-3`}>Repair Narrative<textarea className={`${field} h-24 py-1`} maxLength={1000} value={form.narrative} onChange={set("narrative")} /></label>}
          <div className="mt-4 flex flex-wrap gap-3">
            {screen === "VHRV01I" && !sel && <button type="button" className={primary} disabled={busy} onClick={() => run(async () => done(await api.addRepair(inc.incident, form)))}>Add Repr</button>}
            {screen === "VHRV03I" && <button type="button" className={primary} disabled={busy}
              onClick={() => run(async () => done(await api.addEventRepair(inc.incident, { date: form.date, time: form.time, narrative: form.narrative })))}>Add Repr</button>}
            {screen === "VHRV04I" && sel && <>
              <button type="button" className={primary} disabled={busy} onClick={() => run(async () => {
                if ((form.system !== sel.system || form.subSystem !== sel.subSystem) && !(await app.confirm(opts?.systemChangePrompt ?? ""))) return;
                await done(await api.changeRepair(inc.incident, sel.document, form));
              })}>Change</button>
              <button type="button" className={secondary} disabled={busy} onClick={() => run(async () => {
                if (!(await app.confirm(opts?.deletePrompt ?? ""))) return;
                await done(await api.deleteRepair(inc.incident, sel.document));
              })}>Delete</button>
            </>}
            {sel && <button type="button" className={secondary} onClick={() => openNarrative(app, inc.incident, sel, app.session?.group ?? "")}>Add Narr</button>}
            {screen !== "VHRV04I" && <button type="button" className={secondary} disabled={busy} onClick={() => run(async () => {
              const r = screen === "VHRV03I" ? await api.closeEvent(inc.incident) : await api.closeIncident(inc.incident);
              await done(r, false);
              if (r.done) { setInc(null); setSel(null); setForm(EMPTY); }
            })}>Close Incds</button>}
          </div>
        </section>
        {full && sel && <ComponentsPanel incident={inc.incident} document={sel.document} maintenance={screen === "VHRV04I"} />}
      </>}
    </>
  );
}

const NO_COMPONENT: ComponentInput = { component: "", type: "", fault: "", disposition: "", serialOn: "", serialOff: "" };

/** VHRV02I: the components of one repair; delete only when opened from VHRV04I (as the old screen) */
export function ComponentsPanel({ incident, document, maintenance }: { incident: string; document: number; maintenance: boolean }) {
  const app = useApp();
  const [list, setList] = useState<Components | null>(null);
  const [picked, setPicked] = useState<number | null>(null);
  const [c, setC] = useState<ComponentInput>(NO_COMPONENT);
  const [deletePrompt, setDeletePrompt] = useState("");
  const [error, setError] = useState(""), [busy, setBusy] = useState(false);
  const run = async (f: () => Promise<void>) => { setBusy(true); setError(""); try { await f(); } catch (e) { setError(errText(e)); } finally { setBusy(false); } };
  useEffect(() => { void run(async () => setList(await api.components(incident, document, maintenance))); }, [incident, document, maintenance]);
  useEffect(() => { if (maintenance) api.options().then(o => setDeletePrompt(o.componentDeletePrompt)).catch(() => undefined); }, [maintenance]);
  const after = (r: Components) => { setList(r); setPicked(null); setC(NO_COMPONENT); if (r.message) app.toast(r.message, "ok"); };
  const set = (k: keyof ComponentInput) => (e: { target: { value: string } }) => setC(x => ({ ...x, [k]: e.target.value }));
  const box = (k: keyof ComponentInput, text: string, max: number) =>
    <label className={label}>{text}<input className={field} maxLength={max} value={c[k]} onChange={set(k)} /></label>;
  return (
    <section aria-label="Components" className={panel}>
      <h2 className="mb-2 font-semibold">{list?.title ?? "Components"}</h2>
      {list && <table aria-label="Repair components" className="mb-3 w-full border-collapse text-[13px]">
        <thead><tr>{["Doc No", "Cmpnt Cd", "Type", "Fault Cd", "Disp", "Ser No On", "Ser No Off"].map(h => <th key={h} className={th}>{h}</th>)}</tr></thead>
        <tbody>{list.components.map(x => <tr key={x.document} aria-selected={picked === x.document} className="cursor-pointer aria-selected:bg-bart-50"
          onClick={() => { setPicked(x.document); setC({ component: x.component, type: x.type, fault: x.fault, disposition: x.disposition, serialOn: x.serialOn, serialOff: x.serialOff }); }}>
          {[x.document, x.component, x.type, x.fault, x.disposition, x.serialOn, x.serialOff].map((v, k) => <td key={k} className="px-2 py-1">{v}</td>)}</tr>)}</tbody>
      </table>}
      <div className="flex flex-wrap items-end gap-4">
        <span className="text-[13px]">Doc No {picked ?? list?.nextDocument}</span>
        {box("component", "Cmpnt Cd", 3)}{box("type", "Cmpnt Type", 2)}{box("fault", "Fault Cd", 4)}{box("disposition", "Repr Disp", 1)}
        {box("serialOn", "Ser No On", 12)}{box("serialOff", "Ser No Off", 12)}
      </div>
      <div className="mt-3 flex gap-3">
        {picked === null ? <button type="button" className={primary} disabled={busy} onClick={() => run(async () => after(await api.addComponent(incident, document, c, maintenance)))}>Add</button>
          : <button type="button" className={primary} disabled={busy} onClick={() => run(async () => after(await api.updateComponent(incident, document, picked, c, maintenance)))}>Update</button>}
        {maintenance && picked !== null && <button type="button" className={secondary} disabled={busy} onClick={() => run(async () => {
          if (await app.confirm(deletePrompt)) after(await api.deleteComponent(incident, document, picked));
        })}>Delete</button>}
        <button type="button" className={secondary} onClick={() => { setPicked(null); setC(NO_COMPONENT); }}>Clear</button>
      </div>
      <Alert text={error} />
    </section>
  );
}

/** VHRV02I opened on its own: the parent's fields from the screen key ("egl:VHRV02I?incdNo=…&reprDocNo=…&guiName=VHRV04I") */
export function ComponentsPage() {
  const app = useApp();
  const top = app.stack[app.stack.length - 1];
  const key = top && top.kind === "screen" ? top.key : "";
  const p = new URLSearchParams(key.includes("?") ? key.slice(key.indexOf("?") + 1) : window.location.search);
  const incd = p.get("incdNo") ?? "", doc = Number(p.get("reprDocNo") ?? 0);
  return (
    <>
      <PageHead title="Repair Components" code="VHRV02I" />
      {incd && doc ? <ComponentsPanel incident={incd} document={doc} maintenance={p.get("guiName") === "VHRV04I"} />
        : <p className="p-8 text-slate-500">Open the components from a repair (Vehicle Repair or Repair Maintenance).</p>}
    </>
  );
}
