import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { CLEAN_PAGES } from "@/features/screens/cleanPages";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { OutOfServicePage } from "./OutOfServicePage";
import { ReportsPage } from "./ReportsPage";

const DETAIL = {
  heading: "REASON OUT OF SERVICE DETAIL FOR:", shop: "HAYWARD SHOP", from: "11-01-2026", to: "11-10-2026", runTime: "14:30:00",
  daysCaption: "OUT OF SERVICE DAYS", totalDays: "3", timeCaption: "OUT OF SERVICE HRS:MINS", totalTime: "04 : 15",
  detailGroups: ["--Reason - Changed--", "-Out-Of-Service-"], detail: { headings: ["Car", "Date"], rows: [["1201", "11-03-2026"]] },
};
const SUMMARY = { ...DETAIL, heading: "REASON OUT OF SERVICE SUMMARY FOR:", summaryNote: "Sorted By Occurences High to Low",
  summary: { headings: ["Reason", "Total Occurences"], rows: [["BR", "2"]] } };

describe("reason out of service report (old VHYT32I)", () => {
  it("shows the detail report with its totals", async () => {
    const mw = fakeMiddleware({ "GET /api/v1/yard-queries/out-of-service": () => ({ body: DETAIL }) });
    render(<AppProvider><OutOfServicePage yard="HY" from="2026-11-01" to="2026-11-10" summary={false} /></AppProvider>);
    const t = await screen.findByRole("table", { name: "Detail" });
    expect(within(t).getByText("1201")).toBeInTheDocument();
    const q = mw.calls("/api/v1/yard-queries/out-of-service")[0].query;
    expect(q.get("from")).toBe("2026-11-01");
    expect(q.get("summary")).toBe("false");
    expect(screen.getByText("3")).toBeInTheDocument();
  });

  it("shows the summary by reason, highest first", async () => {
    fakeMiddleware({ "GET /api/v1/yard-queries/out-of-service": () => ({ body: SUMMARY }) });
    render(<AppProvider><OutOfServicePage from="2026-11-01" to="2026-11-10" summary /></AppProvider>);
    expect(within(await screen.findByRole("table", { name: "Summary" })).getByText("BR")).toBeInTheDocument();
    expect(screen.getByText("Sorted By Occurences High to Low")).toBeInTheDocument();
    expect(screen.queryByRole("table", { name: "Detail" })).not.toBeInTheDocument();
  });

  it("is opened by VHYT27I's Display for the out of service summary", async () => {
    const mw = fakeMiddleware({
      "GET /api/v1/auth/csrf": () => ({ status: 204 }),
      "GET /api/v1/yard-queries/report-options": () => ({ body: { printers: [], yards: [], from: "2026-11-01", to: "2026-11-12", warnings: [] } }),
      "GET /api/v1/yard-queries/out-of-service": () => ({ body: SUMMARY }),
    });
    render(<AppProvider><ReportsPage /></AppProvider>);
    await userEvent.selectOptions(await screen.findByLabelText("Report"), "OOSSUM");
    await userEvent.click(screen.getByRole("button", { name: "Display" }));
    await screen.findByRole("table", { name: "Summary" });
    expect(mw.calls("/api/v1/yard-queries/out-of-service")[0].query.get("summary")).toBe("true");
  });

  it("is routed from VHYT32I", () => {
    expect(CLEAN_PAGES.VHYT32I).toBeTypeOf("function");
  });
});
