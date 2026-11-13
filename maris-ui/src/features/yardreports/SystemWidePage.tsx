/* The BART system-wide map (old screen VHSW01I), on the clean API: the places the old map marks, marked when cars are there. */
import { useCallback, useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { fetchSystemWide, type SystemWide } from "./api";

export function SystemWidePage() {
  const [data, setData] = useState<SystemWide | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const load = useCallback(async () => {
    setBusy(true); setError("");
    try { setData(await fetchSystemWide()); }
    catch (e) { setError(e instanceof Error ? e.message : String(e)); }
    finally { setBusy(false); }
  }, []);
  useEffect(() => { void load(); }, [load]);

  return (
    <>
      <PageHead title="BART System Wide Map" code="VHSW01I" />
      <section aria-label="System wide map" className="mx-2 mb-6 rounded-2xl border border-slate-200 bg-white p-4 dark:border-line-dark dark:bg-panel-dark">
        <button type="button" className="mb-3 h-9 rounded-lg border border-slate-300 px-4 font-semibold disabled:opacity-50 dark:border-line-dark"
                disabled={busy} onClick={() => void load()}>Refresh</button>
        {!data && !error && <p role="status" className="text-slate-500">Loading the map from MARIS…</p>}
        {error && <p role="alert" className="mb-3 rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{error}</p>}
        <div className="grid gap-3 sm:grid-cols-2">
          {data?.locations.map(l => (
            <div key={l.location} role="group" aria-label={l.location}
                 className={`rounded-xl border p-3 ${l.cars.length ? "border-bart-600 bg-bart-50 dark:bg-[#12263b]" : "border-slate-200 dark:border-line-dark"}`}>
              <p className="font-semibold">{l.location}</p>
              <p className="text-sm text-slate-600 dark:text-[#c9d6e4]">{l.cars.length ? `${l.cars.length} car${l.cars.length === 1 ? "" : "s"}` : "No cars"}</p>
              {l.cars.length > 0 && <p className="mt-1 font-mono text-[12px]">{l.cars.map(c => <span key={c} className="mr-2">{c}</span>)}</p>}
            </div>
          ))}
        </div>
      </section>
    </>
  );
}
