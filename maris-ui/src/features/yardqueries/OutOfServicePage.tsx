/* Reason out of service report (old screen VHYT32I), on the clean API: each car's out of service periods in the date range
 * with their times and the car's total (detail, VHYT27I report 6), and for the summary (report 7) the totals by reason,
 * highest first. Headings and captions are the old screen's own. */
import { useEffect, useState } from "react";
import { PageHead } from "@/shared/ui/PageHead";
import { fetchOutOfService, type OutOfService } from "./reportsApi";
import { Table } from "./YardLocationPage";

export function OutOfServicePage({ yard, from, to, summary }: { yard?: string; from: string; to: string; summary: boolean }) {
  const [report, setReport] = useState<OutOfService | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    setReport(null); setError("");
    fetchOutOfService(yard ?? "", from, to, summary).then(setReport).catch(e => setError(e instanceof Error ? e.message : String(e)));
  }, [yard, from, to, summary]);

  return (
    <>
      <PageHead title="Reason Out Of Service" code="VHYT32I" />
      <section aria-label="Report" className="mx-2 mb-6 flex flex-col gap-4">
        {!report && !error && <p className="text-slate-500">Loading the report from MARIS…</p>}
        {error && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-red-800 dark:bg-red-950/40 dark:text-red-200">{error}</p>}
        {report && <>
          <p className="font-semibold">{report.heading} {report.shop} <span className="font-normal text-slate-500">· {report.from} – {report.to} · {report.runTime}</span></p>
          {report.summary ? <>
            {report.summaryNote && <p className="text-slate-500">{report.summaryNote}</p>}
            <Table name="Summary" table={report.summary} />
          </> : <>
            <p className="text-sm text-slate-500">{report.detailGroups.filter(Boolean).join(" · ")}</p>
            <Table name="Detail" table={report.detail} />
            {report.totalDays !== undefined &&
              <p>{report.daysCaption}: <strong>{report.totalDays}</strong> · {report.timeCaption}: <strong>{report.totalTime}</strong></p>}
          </>}
        </>}
      </section>
    </>
  );
}
