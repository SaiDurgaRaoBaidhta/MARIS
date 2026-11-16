import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { FleetReportPage } from "./FleetReportPage";

const cells = (n: number) => ({ byType: [n, 0, 0, 0, 0, 0, 0, 0, 0], total: n });
const REPORT = { types: ["A", "B", "CW", "CO", "D", "E", "L", "M", "K"], headings: ["A", "B", "CW", "CO", "D", "E", "L", "M", "CB", "Total"],
  rows: [{ row: 1, name: "Total   Cars in Fleet", cells: cells(12) }], goCarsByYard: [{ yard: "CY", cells: cells(4) }, { yard: "OTH", cells: cells(1) }] };
const base = { "GET /api/v1/vehicle-queries/printers": () => ({ body: [{ description: "DUBLIN TMZ", vtam: "DUBLNHP1" }] }), "GET /api/v1/auth/csrf": () => ({ status: 204 }) };

describe("Fleet report (old VHFR01I)", () => {
  it("shows both tables", async () => {
    fakeMiddleware({ ...base, "GET /api/v1/vehicle-queries/fleet-report": () => ({ body: REPORT }) });
    render(<AppProvider><FleetReportPage /></AppProvider>);
    const fleet = await screen.findByRole("table", { name: "Fleet" });
    expect(within(fleet).getAllByText("12")).toHaveLength(2);
    const yards = screen.getByRole("table", { name: "Available GO cars by yard" });
    expect(within(yards).getByText("OTH")).toBeInTheDocument();
  });

  it("prints a hard copy", async () => {
    const mw = fakeMiddleware({ ...base, "GET /api/v1/vehicle-queries/fleet-report": () => ({ body: REPORT }),
      "POST /api/v1/vehicle-queries/fleet-report/print": () => ({ body: { message: "Job is submitted" } }) });
    render(<AppProvider><FleetReportPage /></AppProvider>);
    await screen.findByRole("table", { name: "Fleet" });
    await userEvent.selectOptions(await screen.findByLabelText("Printer"), "DUBLNHP1");
    await userEvent.click(screen.getByRole("button", { name: "Hard Copy" }));
    await waitFor(() => expect(mw.calls("/api/v1/vehicle-queries/fleet-report/print")).toHaveLength(1));
    expect(await screen.findByText("Job is submitted")).toBeInTheDocument();
  });
});
