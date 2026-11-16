/* Stock number search (old screen WS17I). The old field sizes stay: 10 characters of description, 1 of department. */
import { useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { searchStock, type StockSearch } from "./api";

const field = "h-9 rounded-lg border border-slate-300 bg-white px-2 text-slate-900 dark:border-line-dark dark:bg-[#0a1624] dark:text-ink-dark";
const label = "flex flex-col gap-1 text-[13px] font-semibold text-slate-600 dark:text-[#c9d6e4]";

export function StockSearchPage() {
  const [description, setDescription] = useState("");
  const [dept, setDept] = useState("");
  const [result, setResult] = useState<StockSearch | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const search = async () => {
    setBusy(true); setError("");
    try { setResult(await searchStock(description, dept)); }
    catch (e) { setResult(null); setError(e instanceof Error ? e.message : String(e)); }
    finally { setBusy(false); }
  };
  const clear = () => { setDescription(""); setDept(""); setResult(null); setError(""); };

  return (
    <>
      <PageHead title="Search Stock No Table" code="WS17I" />
      <section aria-label="Search" className="mx-2 mb-4 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark">
        <form className="flex flex-wrap items-end gap-4" onSubmit={e => { e.preventDefault(); search(); }}>
          <label className={label}>Description
            <input className={field} maxLength={10} value={description} onChange={e => setDescription(e.target.value)} />
          </label>
          <label className={label}>Department
            <input className={`${field} w-16 uppercase`} maxLength={1} value={dept} onChange={e => setDept(e.target.value.toUpperCase())} />
          </label>
          <button type="submit" className="h-9 rounded-lg bg-bart-600 px-4 font-semibold text-white disabled:opacity-50" disabled={busy}>Search</button>
          <button type="button" className="h-9 rounded-lg border border-slate-300 px-4 font-semibold dark:border-line-dark" onClick={clear}>Clear</button>
        </form>
        {error && <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{error}</p>}
        {result?.message && <p className="mt-3 rounded-lg bg-amber-50 px-3 py-2 text-amber-900 dark:bg-amber-950/40 dark:text-amber-200">{result.message}</p>}
      </section>
      {result && result.count > 0 && (
        <section className="mx-2 mb-6 overflow-x-auto rounded-xl border border-slate-200 bg-white dark:border-line-dark dark:bg-panel-dark">
          <table aria-label="Stock numbers" className="w-full border-collapse text-[13px]">
            <thead><tr>{["Dept Cd", "Stock No", "Stock Description"].map(h => <th key={h} className="border-b border-slate-200 bg-slate-50 px-2 py-1 text-left dark:border-line-dark dark:bg-[#12263b]">{h}</th>)}</tr></thead>
            <tbody>{result.stockNumbers.map((s, i) => (
              <tr key={i} className="even:bg-slate-50 dark:even:bg-[#0f2032]"><td className="px-2 py-1">{s.dept}</td><td className="px-2 py-1">{s.stockNo}</td><td className="px-2 py-1">{s.description}</td></tr>))}
            </tbody>
          </table>
        </section>)}
    </>
  );
}
