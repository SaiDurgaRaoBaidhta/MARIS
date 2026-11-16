import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { HomeYardFleetPage } from "./HomeYardFleetPage";

const cells = (n: number) => ({ byType: [n, 0, 0, 0, 0, 0, 0, 0], total: n });
const FLEET = { yards: ["CY", "HY"], types: ["A", "B", "C", "D", "E", "L", "M", "K"], headings: ["A", "B", "C", "D", "E", "L", "M", "CB", "Tot"], message: null,
  rows: [{ row: 1, name: "Cars Assigned", yards: { CY: cells(7), HY: cells(3) } }, { row: 4, name: "", yards: { CY: cells(0), HY: cells(0) } }] };
const base = { "GET /api/v1/vehicle-queries/printers": () => ({ body: [{ description: "DUBLIN TMZ", vtam: "DUBLNHP1" }] }), "GET /api/v1/auth/csrf": () => ({ status: 204 }) };

describe("Home yard fleet (old VHHF01I / VHHF02I)", () => {
  it("shows the summary by yard and car type with the old headings", async () => {
    fakeMiddleware({ ...base, "GET /api/v1/vehicle-queries/home-yard-fleet": () => ({ body: FLEET }) });
    render(<AppProvider><HomeYardFleetPage /></AppProvider>);
    const table = await screen.findByRole("table", { name: "Home yard fleet" });
    const row = within(table).getByRole("row", { name: /Cars Assigned/ });
    expect(within(row).getAllByText("7")).toHaveLength(2);           // A and the total
    expect(within(table).getAllByText("CB")).toHaveLength(2);
  });

  it("says so when no yard has cars", async () => {
    fakeMiddleware({ ...base, "GET /api/v1/vehicle-queries/home-yard-fleet": () => ({ body: { ...FLEET, rows: [] } }) });
    render(<AppProvider><HomeYardFleetPage /></AppProvider>);
    expect(await screen.findByText("No cars in the home yards.")).toBeInTheDocument();
  });

  it("prints on the chosen printer", async () => {
    const mw = fakeMiddleware({ ...base, "GET /api/v1/vehicle-queries/home-yard-fleet": () => ({ body: FLEET }),
      "POST /api/v1/vehicle-queries/home-yard-fleet/print": () => ({ body: { message: "Job is submitted" } }) });
    render(<AppProvider><HomeYardFleetPage /></AppProvider>);
    await screen.findByRole("table", { name: "Home yard fleet" });
    await userEvent.selectOptions(await screen.findByLabelText("Printer"), "DUBLNHP1");
    await userEvent.click(screen.getByRole("button", { name: "Print" }));
    await waitFor(() => expect(mw.calls("/api/v1/vehicle-queries/home-yard-fleet/print")).toHaveLength(1));
    expect(mw.calls("/api/v1/vehicle-queries/home-yard-fleet/print")[0].body).toEqual({ printer: "DUBLNHP1" });
  });
});
