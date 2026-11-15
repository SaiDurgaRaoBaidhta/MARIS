/* A secondary repair sub job logged out the door (old screen VHSR04I). */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { findSubJob, goOut, outOptions, type Disposition, type OutOptions, type SubJob } from "./outTheDoorApi";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const button = "h-9 rounded-lg px-4 font-semibold disabled:opacity-50";
const errText = (e: unknown) => e instanceof Error ? e.message : String(e);
const DISPOSITIONS: [Disposition, string][] = [["LONG_TERM_STORAGE", "Long Term Storage"], ["OUT_TO_VENDOR", "Out To Vendor"],
  ["DEFERRED", "Defer Sub Job"], ["AWAITING_PARTS", "Sub Job Awaiting Part"]];

export function OutTheDoorPage() {
  const [options, setOptions] = useState<OutOptions | null>(null);
  const [jobNo, setJobNo] = useState(""), [doc, setDoc] = useState("1");
  const [sub, setSub] = useState<SubJob | null>(null);
  const [dateOut, setDateOut] = useState(""), [qtyOut, setQtyOut] = useState("0"), [qtyScrapped, setQtyScrapped] = useState("0");
  const [faultCode, setFaultCode] = useState(""), [workType, setWorkType] = useState(""), [inspector, setInspector] = useState("");
  const [disposition, setDisposition] = useState<Disposition | null>(null), [vendorId, setVendorId] = useState("");
  const [error, setError] = useState(""), [done, setDone] = useState(""), [busy, setBusy] = useState(false);

  useEffect(() => { outOptions().then(o => { setOptions(o); setDoc(String(o.doc)); }).catch(e => setError(errText(e))); }, []);

  const lookup = async () => {
    setBusy(true); setError(""); setDone("");
    try {
      const s = await findSubJob(jobNo, Number(doc) || 0);
      setSub(s);
      if (s.canGoOut) { setDateOut(s.dateOut ?? ""); setQtyOut(String(s.qtyOut)); setQtyScrapped(String(s.qtyScrapped)); }
    } catch (e) { setSub(null); setError(errText(e)); } finally { setBusy(false); }
  };
  const clearDetails = () => {
    setSub(null); setDateOut(""); setQtyOut("0"); setQtyScrapped("0"); setFaultCode(""); setWorkType(""); setInspector(""); setDisposition(null); setVendorId("");
  };
  const update = async () => {
    setBusy(true); setError(""); setDone("");
    try {
      const r = await goOut({ jobNo, doc: Number(doc) || 0, dateOut, qtyOut: Number(qtyOut) || 0, qtyScrapped: Number(qtyScrapped) || 0, faultCode, workType,
        inspector, disposition, vendorId: disposition === "OUT_TO_VENDOR" ? vendorId : "" });
      setDone(r.message); clearDetails(); setJobNo(""); setDoc(String(options?.doc ?? 1));
    } catch (e) { setError(errText(e)); } finally { setBusy(false); }
  };
  const clear = () => { clearDetails(); setJobNo(""); setDoc(String(options?.doc ?? 1)); setError(""); setDone(""); };
  const editable = !!sub?.canGoOut;

  return (
    <>
      <PageHead title="Sub Job Out The Door" code="VHSR04I" />
      <section aria-label="Sub job" className="mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark">
        <form className="flex flex-wrap items-end gap-4" onSubmit={e => { e.preventDefault(); lookup(); }}>
          <label className={label}>Job No.<input className={`${field} w-24`} maxLength={6} value={jobNo} disabled={editable} onChange={e => setJobNo(e.target.value)} /></label>
          <label className={label}>Doc No.<input className={`${field} w-16`} inputMode="numeric" value={doc} disabled={editable} onChange={e => setDoc(e.target.value)} /></label>
          <button type="submit" className={`${button} border border-slate-300 dark:border-line-dark`} disabled={busy || editable}>Find</button>
          {sub && <p className="text-[13px]">Status <b>{sub.status}</b></p>}
        </form>
        <fieldset disabled={!editable} className="mt-4 flex flex-wrap items-end gap-4 disabled:opacity-60">
          <label className={label}>Date Out<input type="date" className={field} value={dateOut} onChange={e => setDateOut(e.target.value)} /></label>
          <label className={label}>Qty Out<input className={`${field} w-16`} inputMode="numeric" value={qtyOut} onChange={e => setQtyOut(e.target.value)} /></label>
          <label className={label}>Qty Scrap<input className={`${field} w-16`} inputMode="numeric" value={qtyScrapped} onChange={e => setQtyScrapped(e.target.value)} /></label>
          <label className={label}>Diagnostic Fault
            <select className={`${field} w-28`} value={faultCode} onChange={e => setFaultCode(e.target.value)}>
              <option value="" />{options?.faultCodes.map(f => <option key={f} value={f}>{f}</option>)}
            </select></label>
          <label className={label}>Diagnostic Work Type
            <select className={`${field} w-40`} value={workType} onChange={e => setWorkType(e.target.value)}>
              <option value="" />{options?.workTypes.map(w => <option key={w.code} value={w.code}>{w.code} {w.description}</option>)}
            </select></label>
          <label className={label}>Q. A. Inspector<input className={`${field} w-20`} maxLength={4} value={inspector} onChange={e => setInspector(e.target.value)} /></label>
          <div role="radiogroup" aria-label="Goes out as" className="flex flex-wrap gap-3 text-[13px]">
            {DISPOSITIONS.map(([d, text]) => (
              <label key={d} className="flex items-center gap-1">
                <input type="checkbox" checked={disposition === d} onChange={e => setDisposition(e.target.checked ? d : null)} />{text}
              </label>))}
          </div>
          {disposition === "OUT_TO_VENDOR" &&
            <label className={label}>Vendor Id<input className={`${field} w-28`} maxLength={9} value={vendorId} onChange={e => setVendorId(e.target.value)} /></label>}
        </fieldset>
        <div className="mt-4 flex gap-3">
          <button type="button" className={`${button} bg-bart-600 text-white`} disabled={!editable || busy} onClick={update}>Update</button>
          <button type="button" className={`${button} border border-slate-300 dark:border-line-dark`} onClick={clear}>Clear</button>
        </div>
        {error && <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{error}</p>}
        {done && <p role="status" className="mt-3 rounded-lg bg-emerald-50 px-3 py-2 text-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-200">{done}</p>}
        {!error && !done && sub?.message && <p className="mt-3 rounded-lg bg-amber-50 px-3 py-2 text-amber-900 dark:bg-amber-950/40 dark:text-amber-200">{sub.message}</p>}
      </section>
    </>
  );
}
