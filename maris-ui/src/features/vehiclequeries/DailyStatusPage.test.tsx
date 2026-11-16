import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { DailyStatusPage } from "./DailyStatusPage";

const report = (date: string, warranty: number) => ({ body: { revenueDate: date, validAsOf: "04:00",
  tables: [{ screen: "VHDVS1I", title: "From Service", columns: [{ field: "DESC", heading: "From Service" }, { field: "CY-A", heading: "A" }],
    rows: [{ DESC: "Warranty Failures", "CY-A": warranty }] },
  { screen: "VHDVS3I", title: "Reason", columns: [{ field: "DESC", heading: "Reason" }, { field: "CY-A", heading: "A" }], rows: [{ DESC: "Engr Hold", "CY-A": 3 }] }],
  gains: { concord: 1, dalyCity: -2, hayward: 0, richmond: 4, total: 3 } } });
const base = {
  "GET /api/v1/vehicle-queries/daily-status/printers": () => ({ body: [{ name: "DUBLIN TMZ", vtamName: "DUBLNHP1" }] }),
  "GET /api/v1/auth/csrf": () => ({ status: 204 }),
};

describe("Daily vehicle status (old VHDVS1I .. VHDVS4I)", () => {
  it("shows today's tables and retrieves another day", async () => {
    const mw = fakeMiddleware({ ...base, "GET /api/v1/vehicle-queries/daily-status": req => req.query.get("revenueDate") ? report("11-02-2026", 9) : report("11-14-2026", 6) });
    render(<AppProvider><DailyStatusPage /></AppProvider>);
    expect(await screen.findByText("Warranty Failures")).toBeInTheDocument();
    expect(screen.getByText("6")).toBeInTheDocument();
    expect(screen.getByText("Engr Hold")).toBeInTheDocument();
    expect(screen.getByText(/Daly City -2/)).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText("Revenue Date"), "2026-11-02");
    await userEvent.click(screen.getByRole("button", { name: "Retrieve Data" }));
    expect(await screen.findByText("9")).toBeInTheDocument();
    expect(mw.calls("/api/v1/vehicle-queries/daily-status")[1].query.get("revenueDate")).toBe("2026-11-02");
  });

  it("prints a hard copy", async () => {
    const mw = fakeMiddleware({ ...base, "GET /api/v1/vehicle-queries/daily-status": () => report("11-14-2026", 6),
      "POST /api/v1/vehicle-queries/daily-status/print": () => ({ body: { message: "Report submitted" } }) });
    render(<AppProvider><DailyStatusPage /></AppProvider>);
    await screen.findByText("Warranty Failures");
    await userEvent.selectOptions(screen.getByLabelText("Printer"), "DUBLNHP1");
    await userEvent.click(screen.getByRole("button", { name: "Hard Copy" }));
    await waitFor(() => expect(mw.calls("/api/v1/vehicle-queries/daily-status/print")).toHaveLength(1));
    expect(mw.calls("/api/v1/vehicle-queries/daily-status/print")[0].body).toEqual({ revenueDate: "", printer: "DUBLNHP1" });
  });
});
