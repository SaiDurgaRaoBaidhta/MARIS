/* Yard location status report (old screen VHYT26I), on the clean API: the yard's cars with their time in the yard, the shop /
 * yard exchange, and either the summary by out of service reason or the out of town summary by home yard (the old window's two
 * buttons switch between them). Headings and captions are the old screen's own, sent by the middleware. */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { fetchYardLocation, type ReportTable, type YardLocation } from "./reportsApi";

const th = "whitespace-nowrap border-b border-slate-200 bg-slate-50 px-2 py-1 text-left dark:border-line-dark dark:bg-[#12263b]";
const td = "whitespace-pre px-2 py-1";
const button = "h-9 rounded-lg border border-slate-300 px-4 font-semibold disabled:opacity-50 dark:border-line-dark";

export function Table({ name, table }: { name: string; table: ReportTable }) {
  return (
    <div className="overflow-x-auto rounded-2xl border border-slate-200 bg-white dark:border-line-dark dark:bg-panel-dark">
      <table aria-label={name} className="w-full text-sm">
        <thead><tr>{table.headings.map((h, i) => <th key={i} className={th}>{h}</th>)}</tr></thead>
        <tbody>{table.rows.map((r, i) => <tr key={i} className="odd:bg-white even:bg-slate-50 dark:odd:bg-transparent dark:even:bg-[#0f2133]">
          {r.map((c, j) => <td key={j} className={td}>{c}</td>)}</tr>)}</tbody>
      </table>
    </div>
  );
}

export function YardLocationPage({ yard }: { yard?: string }) {
  const [report, setReport] = useState<YardLocation | null>(null);
  const [error, setError] = useState("");
  const [homeYards, setHomeYards] = useState(false);
  useEffect(() => {
    setReport(null); setError("");
    fetchYardLocation(yard ?? "").then(setReport).catch(e => setError(e instanceof Error ? e.message : String(e)));
  }, [yard]);

  return (
    <>
      <PageHead title="Yard Location Status Report" code="VHYT26I" />
      <section aria-label="Report" className="mx-2 mb-6 flex flex-col gap-4">
        {!report && !error && <p className="text-slate-500">Loading the report from MARIS…</p>}
        {error && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{error}</p>}
        {report && <>
          <p className="font-semibold">{report.title} {report.yardName} <span className="font-normal text-slate-500">· {report.runDate} {report.runTime}</span></p>
          <Table name={`Cars in ${report.yardName}`} table={report.cars} />
          <p>{report.totalCaption}: <strong>{report.totalCars}</strong>{report.testTracksNote && <span className="text-slate-500"> {report.testTracksNote}</span>}</p>
          <div className="flex gap-2">
            <button type="button" className={button} disabled={homeYards} onClick={() => setHomeYards(true)}>{report.homeYardsButton}</button>
            <button type="button" className={button} disabled={!homeYards} onClick={() => setHomeYards(false)}>{report.reasonsButton}</button>
          </div>
          {homeYards ? <Table name={report.homeYards.headings[0] ?? "Home yards"} table={report.homeYards} />
            : <Table name={report.reasons.headings[0] ?? "Reasons"} table={report.reasons} />}
          <p className="font-semibold">{report.exchangeTitle}</p>
          <Table name={report.exchangeTitle} table={report.exchange} />
        </>}
      </section>
    </>
  );
}
