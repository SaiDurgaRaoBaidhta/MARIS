/* Component serials (old VHCF49I list, VHCF50I detail / update, VHCF51I serial hours, VHCF52I serial geo data), on the
 * clean API. List by car, stock and serial; pick a row to view or update it, or to see its hours and geo data (no row:
 * every serial, as the old buttons did). The old screens' messages come from the middleware. */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import {
  fetchForm, fetchGeo, fetchHours, fetchOptions, fetchSerials, saveGeo, saveHours, saveSerial,
  type Choice, type Form, type Row, type Stock,
} from "./api";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 disabled:bg-slate-100 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const panel = "mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark";
const th = "whitespace-nowrap border-b border-slate-200 bg-slate-50 px-2 py-1 text-left dark:border-line-dark dark:bg-[#12263b]";
const td = "whitespace-nowrap px-2 py-1";
const primary = "h-9 rounded-lg bg-bart-600 px-4 font-semibold text-white disabled:opacity-50";
const secondary = "h-9 rounded-lg border border-slate-300 bg-white px-4 font-semibold text-slate-700 disabled:opacity-50";
const message = (e: unknown) => (e instanceof Error ? e.message : String(e));

/** the old detail screen's captions, by record field */
const CAPTIONS: Record<string, string> = {
  stockNo: "Stock No", cmpntSerialNo: "Cmpnt Ser No", carNo: "Car No", carNoPrev: "Car No Prev", homeYrd: "Home Yrd", cmpntEquipType: "CmpntEqpType",
  cmpntTrackingCd: "Cmpnt Trk Cd", configStat: "Config Stat", serialStat: "Serial Stat", serialConfigdInd: "Serial Config Ind", shopLoc: "Shop Loc",
  posnCd: "Posn Cd", dateAxle: "Date Axle", dateBuyRebuilt: "Date Buy Rebuilt", dateInstalled: "Date Installed", dateLastRemoved: "Date Last Removed",
  emplNo: "Empl No", lastRemovedBy: "Last Removed By", parentEquipType: "Parent Equip Type", parentPosnCd: "Parent Posn Cd", parentSerialNo: "Parent Serl No",
  parentStockNo: "Parent Stock No", dateHubCoupRepl: "Dt Hub Coup Repl", secCmpntStat: "Sec Cmpnt Stat", cmpntFailureCd: "Cmpnt Fail Cd", jobNo: "Job No",
  subJobDocNo: "Sub Job Doc No", armatureNo: "Armature No", dateCmpntEvaluat: "Dt Cmp Eval", dateCmpntRepr: "Dt Cmp Repr", prevTrkSerialNo: "PrevTrk Ser No",
  prevTrkPosCd: "PrevTrk Pos Cd", prevTrkStockNo: "Prev Trk Stock No", prevAxlSerialNo: "PrevAxl Ser No", prevAxlePosCd: "Prev Axle Pos Cd",
  dateTrkRemove: "Dt Trk Remove", timeTrkRemove: "Time Trk Remove", truckStat: "Truck Stat", dateLastUpd: "DateLastUpd", timeLastUpd: "TimeLastUpd",
  termLastUpd: "TermLastUpd", dateMaintUpd: "DateMaintUpd", timeMaintUpd: "TimeMaintUpd", termMaintUpd: "TermMaintUpd",
};
/** the old detail screen's drop-downs: field -> the program whose rows it lists */
const LISTS: Record<string, string> = {
  homeYrd: "VHCF50B", shopLoc: "VHCF50C", configStat: "VHCF50E", serialStat: "VHCF50F", cmpntEquipType: "VHCF50G", parentEquipType: "VHCF50H",
  posnCd: "VHCF50J", parentPosnCd: "VHCF50K", cmpntTrackingCd: "VHCF50L", parentStockNo: "VHCF50M",
};
/** fields the old detail screen never lets you change (keys and the audit fields) */
const LOCKED = new Set(["stockNo", "cmpntSerialNo", "dateLastUpd", "timeLastUpd", "termLastUpd", "dateMaintUpd", "timeMaintUpd", "termMaintUpd"]);
const COLUMNS = ["stockNo", "cmpntSerialNo", "carNo", "homeYrd", "cmpntEquipType", "posnCd", "serialStat", "configStat", "shopLoc", "dateInstalled"];

function Alert({ text, ok }: { text?: string; ok?: boolean }) {
  if (!text) return null;
  return <p role={ok ? "status" : "alert"} className={`mt-3 rounded-lg px-3 py-2 ${ok ? "bg-emerald-50 text-emerald-800" : "bg-red-50 text-red-800"}`}>{text}</p>;
}

function Table({ name, rows, columns, selected, select }: { name: string; rows: Row[]; columns: string[]; selected?: number; select?: (i: number) => void }) {
  return (
    <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white dark:border-line-dark dark:bg-panel-dark">
      <table aria-label={name} className="w-full border-collapse text-[12px]">
        <thead><tr>{columns.map(c => <th key={c} className={th}>{CAPTIONS[c] ?? c}</th>)}</tr></thead>
        <tbody>{rows.map((r, i) => (
          <tr key={i} aria-selected={selected === i} onClick={() => select?.(i)}
              className={`cursor-pointer ${selected === i ? "bg-bart-50 dark:bg-[#17324d]" : "even:bg-slate-50 dark:even:bg-[#0f2032]"}`}>
            {columns.map(c => <td key={c} className={td}>{String(r[c] ?? "")}</td>)}
          </tr>))}
        </tbody>
      </table>
    </div>
  );
}

function Detail({ row, update, close }: { row: Row; update: boolean; close: () => void }) {
  const [f, setF] = useState<Form | null>(null);
  const [form, setForm] = useState<Row>({});
  const [comments, setComments] = useState("");
  const [result, setResult] = useState<{ text: string; ok: boolean } | null>(null);
  useEffect(() => {
    fetchForm(row).then(x => { setF(x); setForm(x.form); setComments(x.comments); }).catch(e => setResult({ text: message(e), ok: false }));
  }, [row]);
  const save = async () => {
    try { const r = await saveSerial(row, form, comments); setResult({ text: r.message, ok: true }); } catch (e) { setResult({ text: message(e), ok: false }); }
  };
  const input = (k: string) => {
    const list: Choice[] | undefined = LISTS[k] ? f?.lists[LISTS[k]] : undefined;
    const disabled = !update || LOCKED.has(k);
    const v = String(form[k] ?? "");
    return (
      <label key={k} className={label}>{CAPTIONS[k] ?? k}
        {list ? (
          <select className={field} value={v} disabled={disabled} onChange={e => setForm(x => ({ ...x, [k]: e.target.value }))}>
            <option value="" />
            {!list.some(c => c.code === v) && v && <option value={v}>{v}</option>}
            {list.map(c => <option key={c.code + c.text} value={c.code}>{c.text}</option>)}
          </select>
        ) : <input className={field} value={v} disabled={disabled} onChange={e => setForm(x => ({ ...x, [k]: e.target.value }))} />}
      </label>);
  };
  return (
    <section aria-label="Serial detail" className={panel}>
      <h2 className="mb-3 font-semibold">{update ? "Update" : "Detail"} of serial {String(row.cmpntSerialNo ?? "")}</h2>
      {f?.warnings.map(w => <p key={w} className="mb-2 rounded-lg bg-amber-50 px-3 py-2 text-amber-900">{w}</p>)}
      {f && <>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">{Object.keys(form).filter(k => k in CAPTIONS).map(input)}</div>
        <label className={`${label} mt-3`}>Comments
          <textarea className="min-h-20 rounded-lg border border-slate-300 p-2" value={comments} disabled={!update} onChange={e => setComments(e.target.value)} />
        </label>
      </>}
      <div className="mt-3 flex gap-2">
        {update && <button type="button" className={primary} disabled={!f} onClick={() => void save()}>Save</button>}
        <button type="button" className={secondary} onClick={close}>Close</button>
      </div>
      <Alert text={result?.text} ok={result?.ok} />
    </section>
  );
}

function Hours({ stock, serial, close }: { stock: string; serial: string; close: () => void }) {
  const [rows, setRows] = useState<Row[]>([]);
  const [msg, setMsg] = useState<{ text: string; ok: boolean } | null>(null);
  const [sel, setSel] = useState<number>();
  const [edit, setEdit] = useState({ hours: "", minutes: "", date: "" });
  const load = () => fetchHours(stock, serial).then(r => { setRows(r.rows); if (r.message) setMsg({ text: r.message, ok: false }); }).catch(e => setMsg({ text: message(e), ok: false }));
  useEffect(() => { void load(); }, [stock, serial]);   // eslint-disable-line react-hooks/exhaustive-deps
  const pick = (i: number) => {
    setSel(i);
    const r = rows[i];
    setEdit({ hours: String(r.cmpntCumHrs ?? ""), minutes: String(r.cmpntCumMin ?? ""), date: String(r.dateHrsPosted ?? "") });
  };
  const save = async () => {
    if (sel === undefined) return;
    const r = rows[sel];
    try {
      const a = await saveHours({ stock: String(r.stockNo), serial: String(r.cmpntSerialNo), cumulativeHours: Number(edit.hours) || 0, cumulativeMinutes: Number(edit.minutes) || 0, datePosted: edit.date });
      setMsg({ text: a.message, ok: true });
      void load();
    } catch (e) { setMsg({ text: message(e), ok: false }); }
  };
  return (
    <section aria-label="Serial hours" className={panel}>
      <h2 className="mb-3 font-semibold">Serial hours</h2>
      <Table name="Hours" rows={rows} columns={["stockNo", "cmpntSerialNo", "cmpntCumHrs", "cmpntCumMin", "dateHrsPosted"]} selected={sel} select={pick} />
      {sel !== undefined && <div className="mt-3 grid grid-cols-3 gap-3">
        <label className={label}>Cum hours<input className={field} value={edit.hours} onChange={e => setEdit(x => ({ ...x, hours: e.target.value }))} /></label>
        <label className={label}>Cum minutes<input className={field} value={edit.minutes} onChange={e => setEdit(x => ({ ...x, minutes: e.target.value }))} /></label>
        <label className={label}>Date hrs posted<input className={field} value={edit.date} onChange={e => setEdit(x => ({ ...x, date: e.target.value }))} /></label>
      </div>}
      <div className="mt-3 flex gap-2">
        <button type="button" className={primary} disabled={sel === undefined} onClick={() => void save()}>Update hours</button>
        <button type="button" className={secondary} onClick={close}>Close</button>
      </div>
      <Alert text={msg?.text} ok={msg?.ok} />
    </section>
  );
}

function Geo({ stock, serial, close }: { stock: string; serial: string; close: () => void }) {
  const [rows, setRows] = useState<Row[]>([]);
  const [locations, setLocations] = useState<string[]>([]);
  const [msg, setMsg] = useState<{ text: string; ok: boolean } | null>(null);
  useEffect(() => {
    fetchGeo(stock, serial).then(r => { setRows(r.rows); setLocations(r.locations); if (r.message) setMsg({ text: r.message, ok: false }); })
      .catch(e => setMsg({ text: message(e), ok: false }));
  }, [stock, serial]);
  const [sel, setSel] = useState<number>();
  const [m, setM] = useState({ measurement: "", date: "", by: "", inspector: "", location: "" });
  const pick = (i: number) => {
    setSel(i);
    const r = rows[i];
    setM({ measurement: String(r.measurement ?? ""), date: String(r.dateMeasured ?? ""), by: String(r.measuredBy ?? ""), inspector: String(r.inspectorEmplNo ?? ""), location: String(r.loc ?? "") });
  };
  const save = async () => {
    if (sel === undefined) return;
    const r = rows[sel];
    try {
      const a = await saveGeo({ stock: String(r.stockNo), serial: String(r.cmpntSerialNo), geoDataName: String(r.geoDataName ?? ""), dateMeasured: m.date,
        timeMeasured: String(r.timeMeasured ?? ""), measurement: m.measurement, measuredBy: m.by, inspector: m.inspector, location: m.location,
        position: String(r.posnCd ?? ""), car: String(r.carNo ?? "") });
      setMsg({ text: a.message, ok: true });
    } catch (e) { setMsg({ text: message(e), ok: false }); }
  };
  return (
    <section aria-label="Serial geo data" className={panel}>
      <h2 className="mb-3 font-semibold">Serial geo data</h2>
      <Table name="Geo data" rows={rows} columns={["stockNo", "cmpntSerialNo", "geoDataName", "dateMeasured", "timeMeasured", "measurement", "measuredBy", "loc", "carNo"]}
             selected={sel} select={pick} />
      {sel !== undefined && <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-5">
        <label className={label}>Measurement<input className={field} value={m.measurement} onChange={e => setM(x => ({ ...x, measurement: e.target.value }))} /></label>
        <label className={label}>Date measured<input className={field} value={m.date} onChange={e => setM(x => ({ ...x, date: e.target.value }))} /></label>
        <label className={label}>Measured by<input className={field} value={m.by} onChange={e => setM(x => ({ ...x, by: e.target.value }))} /></label>
        <label className={label}>Inspector<input className={field} value={m.inspector} onChange={e => setM(x => ({ ...x, inspector: e.target.value }))} /></label>
        <label className={label}>Location
          <select className={field} value={m.location} onChange={e => setM(x => ({ ...x, location: e.target.value }))}>
            <option value="" />{locations.map(l => <option key={l} value={l}>{l}</option>)}
          </select>
        </label>
      </div>}
      <div className="mt-3 flex gap-2">
        <button type="button" className={primary} disabled={sel === undefined} onClick={() => void save()}>Update geo data</button>
        <button type="button" className={secondary} onClick={close}>Close</button>
      </div>
      <Alert text={msg?.text} ok={msg?.ok} />
    </section>
  );
}

export function ComponentSerialsPage() {
  const [stocks, setStocks] = useState<Stock[]>([]);
  const [crit, setCrit] = useState({ car: "", stock: "", serial: "" });
  const [rows, setRows] = useState<Row[] | null>(null);
  const [note, setNote] = useState("");
  const [error, setError] = useState("");
  const [sel, setSel] = useState<number>();
  const [open, setOpen] = useState<"view" | "update" | "hours" | "geo" | null>(null);
  useEffect(() => { fetchOptions().then(o => { setStocks(o.stocks); if (o.warning) setError(o.warning); }).catch(e => setError(message(e))); }, []);
  const list = async () => {
    setError(""); setNote(""); setSel(undefined); setOpen(null);
    try { const r = await fetchSerials(crit.car, crit.stock, crit.serial); setRows(r.rows); setNote(r.message ?? ""); } catch (e) { setRows(null); setError(message(e)); }
  };
  const row = sel === undefined || !rows ? undefined : rows[sel];
  return (
    <>
      <PageHead title="Component Serial Numbers" code="VHCF49I" />
      <section aria-label="Criteria" className={panel}>
        <div className="grid gap-4 md:grid-cols-3">
          <label className={label}>Stock Number
            <select className={field} value={crit.stock} onChange={e => setCrit(x => ({ ...x, stock: e.target.value }))}>
              <option value="" />{stocks.map(s => <option key={s.stock + s.text} value={s.stock}>{s.text}</option>)}
            </select>
          </label>
          <label className={label}>Component Serial No<input className={field} maxLength={12} value={crit.serial} onChange={e => setCrit(x => ({ ...x, serial: e.target.value }))} /></label>
          <label className={label}>Car Number<input className={field} maxLength={4} value={crit.car} onChange={e => setCrit(x => ({ ...x, car: e.target.value }))} /></label>
        </div>
        <div className="mt-4 flex flex-wrap gap-2">
          <button type="button" className={primary} onClick={() => void list()}>List</button>
          <button type="button" className={secondary} disabled={!row} onClick={() => setOpen("view")}>View Details</button>
          <button type="button" className={secondary} disabled={!row} onClick={() => setOpen("update")}>Update</button>
          <button type="button" className={secondary} disabled={!rows} onClick={() => setOpen("hours")}>Serial No Hrs</button>
          <button type="button" className={secondary} disabled={!rows} onClick={() => setOpen("geo")}>Serial Geo Data</button>
        </div>
        {note && <p role="status" className="mt-3 rounded-lg bg-amber-50 px-3 py-2 text-amber-900">{note}</p>}
        <Alert text={error} />
      </section>
      {rows && <section aria-label="Serials" className="mx-2 mb-4"><Table name="Component serials" rows={rows} columns={COLUMNS} selected={sel} select={setSel} /></section>}
      {row && (open === "view" || open === "update") && <Detail key={`${sel}-${open}`} row={row} update={open === "update"} close={() => setOpen(null)} />}
      {open === "hours" && <Hours stock={String(row?.stockNo ?? "")} serial={String(row?.cmpntSerialNo ?? "")} close={() => setOpen(null)} />}
      {open === "geo" && <Geo stock={String(row?.stockNo ?? "")} serial={String(row?.cmpntSerialNo ?? "")} close={() => setOpen(null)} />}
    </>
  );
}
