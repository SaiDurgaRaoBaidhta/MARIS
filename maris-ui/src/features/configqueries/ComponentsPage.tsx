/* Config component inquiry (old screen VHCF16I), on the clean API: components by stock number, serial number, status,
 * home yard and equipment type, in one of the old two orders. The old screen's rule kept here: a home yard needs a stock
 * number; its messages (no data, data limit of 213) come from the middleware. */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { fetchComponentOptions, fetchComponents, type Component, type ComponentOptions, type ComponentSearch } from "./api";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";
const th = "whitespace-nowrap border-b border-slate-200 bg-slate-50 px-2 py-1 text-left dark:border-line-dark dark:bg-[#12263b]";
const td = "whitespace-nowrap px-2 py-1";
const SORTS: [string, string][] = [["1", "Car No, Stock No., Serial No."], ["2", "Stock No., Component Hours descending"]];
const COLUMNS: [string, (c: Component) => string | number][] = [
  ["Car", c => c.car], ["Home yd", c => c.homeYard], ["Type", c => c.equipmentType], ["Stock", c => c.stock], ["Serial", c => c.serial],
  ["Posn", c => c.position], ["Stat", c => c.status], ["Rebuilt", c => c.dateRebuilt], ["Hours", c => c.hours], ["Trk", c => c.trackingCode],
  ["Configd", c => c.configured], ["Parent serial", c => c.parentSerial], ["Parent posn", c => c.parentPosition], ["Shop", c => c.shopLocation],
];

export function ComponentsPage() {
  const [options, setOptions] = useState<ComponentOptions | null>(null);
  const [s, setS] = useState<ComponentSearch>({ stock: "", serial: "", status: "", homeYard: "", equipmentType: "", sort: "1" });
  const [result, setResult] = useState<{ message?: string; rows: Component[] } | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => { fetchComponentOptions().then(setOptions).catch(e => setError(e instanceof Error ? e.message : String(e))); }, []);
  const set = (k: keyof ComponentSearch, v: string) => setS(x => ({ ...x, [k]: v }));
  const needsStock = !!s.homeYard && !s.stock;
  const display = async () => {
    setBusy(true); setError("");
    try { setResult(await fetchComponents(s)); } catch (e) { setResult(null); setError(e instanceof Error ? e.message : String(e)); } finally { setBusy(false); }
  };
  const select = (name: keyof ComponentSearch, text: string, values: [string, string][]) => (
    <label className={label}>{text}
      <select className={field} value={s[name]} onChange={e => set(name, e.target.value)}>
        <option value="">Any</option>
        {values.map(([v, t]) => <option key={v} value={v}>{t}</option>)}
      </select>
    </label>
  );

  return (
    <>
      <PageHead title="Config Component Inquiry" code="VHCF16I" />
      <section aria-label="Criteria" className="mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark">
        {!options && !error && <p className="text-slate-500">Loading the lists from MARIS…</p>}
        {options?.warnings.map(w => <p key={w} className="mb-3 rounded-lg bg-amber-50 px-3 py-2 text-amber-900">{w}</p>)}
        {options && <>
          <div className="grid gap-4 md:grid-cols-3">
            {select("stock", "Stock number", options.stocks.map(x => [x.stock, `${x.stock} ${x.description}`]))}
            {select("equipmentType", "Equipment type", options.equipmentTypes.map(x => [x, x]))}
            {select("status", "Status", options.serialStatuses.map(x => [x, x]))}
            {select("homeYard", "Home yard", options.homeYards.map(x => [x, x]))}
            <label className={label}>Serial number<input className={field} maxLength={12} value={s.serial} onChange={e => set("serial", e.target.value)} /></label>
            <fieldset className="flex flex-col gap-1 text-[13px]"><legend className="font-semibold text-slate-600 dark:text-[#c9d6e4]">Order</legend>
              {SORTS.map(([v, t]) => <label key={v}><input type="radio" name="sort" checked={s.sort === v} onChange={() => set("sort", v)} /> {t}</label>)}
            </fieldset>
          </div>
          {needsStock && <p className="mt-3 text-amber-800">With a home yard, also choose a stock number.</p>}
          <button type="button" className="mt-4 h-9 rounded-lg bg-bart-600 px-4 font-semibold text-white disabled:opacity-50" disabled={busy || needsStock}
                  onClick={() => void display()}>Display</button>
        </>}
        {error && <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{error}</p>}
      </section>
      {result && (
        <section aria-label="Result" className="mx-2 mb-6">
          {result.message && <p role="status" className="mb-2 rounded-lg bg-amber-50 px-3 py-2 text-amber-900">{result.message}</p>}
          <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white dark:border-line-dark dark:bg-panel-dark">
            <table aria-label="Components" className="w-full border-collapse text-[12px]">
              <thead><tr>{COLUMNS.map(([h]) => <th key={h} className={th}>{h}</th>)}</tr></thead>
              <tbody>{result.rows.map((c, i) => <tr key={i} className="even:bg-slate-50 dark:even:bg-[#0f2032]">{COLUMNS.map(([h, f]) => <td key={h} className={td}>{f(c)}</td>)}</tr>)}</tbody>
            </table>
          </div>
        </section>)}
    </>
  );
}
