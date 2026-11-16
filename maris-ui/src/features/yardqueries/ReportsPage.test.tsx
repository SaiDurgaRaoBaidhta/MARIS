import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { CLEAN_PAGES } from "@/features/screens/cleanPages";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { ReportsPage } from "./ReportsPage";

const OPTIONS = { printers: [{ description: "DUBLIN", vtam: "DUBLNHP1" }], yards: [{ code: "CY", description: "CONCORD YARD" }, { code: "HY", description: "HAYWARD YARD" }],
  from: "2026-11-01", to: "2026-11-12", warnings: [] };
const routes = (extra = {}) => fakeMiddleware({ "GET /api/v1/yard-queries/report-options": () => ({ body: OPTIONS }), "GET /api/v1/auth/csrf": () => ({ status: 204 }), ...extra });
const page = () => render(<AppProvider><ReportsPage /></AppProvider>);

describe("yard reports criteria (old VHYT27I)", () => {
  it("offers the printers and yards and proposes MARIS's dates; shows the old screen's fields per report", async () => {
    routes();
    page();
    await userEvent.selectOptions(await screen.findByLabelText("Report"), "CAR-HIST");
    expect(screen.getByLabelText("From date")).toHaveValue("2026-11-01");
    expect(screen.getByLabelText("From car")).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByLabelText("Report"), "SHOPSTAT");
    expect(screen.queryByLabelText("From date")).not.toBeInTheDocument();            // the shop status report has no dates
    await userEvent.selectOptions(screen.getByLabelText("Report"), "04XX-RPT");
    expect(screen.queryByLabelText("Yard")).not.toBeInTheDocument();                  // nor the 04XX report a yard
    expect(screen.getByRole("button", { name: "Display" })).toBeDisabled();           // printed only
  });

  it("Display shows the chosen report for the chosen yard", async () => {
    const mw = routes({ "GET /api/v1/yard-queries/shop-status/HY": () => ({ body: { yard: "HY", shop: "HAYWARD SHOP", runTime: "", cars: [], totalCars: 0, grandTotal: 0,
      outOfYard: { cars: [], totalCars: 0 } } }) });
    page();
    await userEvent.selectOptions(await screen.findByLabelText("Report"), "SHOPSTAT");
    await userEvent.selectOptions(screen.getByLabelText("Yard"), "HY");
    await userEvent.click(screen.getByRole("button", { name: "Display" }));
    await screen.findByRole("table", { name: "Cars in HAYWARD SHOP" });
    expect(mw.calls("/api/v1/yard-queries/shop-status/HY")).toHaveLength(1);
  });

  it("Display of a history report asks for it with the dates and cars", async () => {
    const mw = routes({ "GET /api/v1/yard-queries/car-history": () => ({ body: { yard: "CY", from: "2026-11-01", to: "2026-11-12", maxRowsReached: false, rows: [] } }) });
    page();
    await userEvent.selectOptions(await screen.findByLabelText("Report"), "CAR-HIST");
    await userEvent.selectOptions(screen.getByLabelText("Yard"), "CY");
    await userEvent.type(screen.getByLabelText("From car"), "1200");
    await userEvent.click(screen.getByRole("button", { name: "Display" }));
    await waitFor(() => expect(mw.calls("/api/v1/yard-queries/car-history")).toHaveLength(1));
    expect(Object.fromEntries(mw.calls("/api/v1/yard-queries/car-history")[0].query)).toEqual({ yard: "CY", from: "2026-11-01", to: "2026-11-12", fromCar: "1200", toCar: "" });
  });

  it("Hard Copy needs a printer, then prints the report there", async () => {
    const mw = routes({ "POST /api/v1/yard-queries/reports/print": () => ({ body: { message: "The Report You Requested Has Been Submitted" } }) });
    page();
    await userEvent.selectOptions(await screen.findByLabelText("Report"), "YARDHIST");
    expect(screen.getByRole("button", { name: "Hard Copy" })).toBeDisabled();
    await userEvent.selectOptions(screen.getByLabelText("Printer"), "DUBLNHP1");
    await userEvent.click(screen.getByRole("button", { name: "Hard Copy" }));
    expect(await screen.findByText("The Report You Requested Has Been Submitted")).toBeInTheDocument();
    expect(mw.calls("/api/v1/yard-queries/reports/print")[0].body).toEqual({ report: "YARDHIST", yard: "", printer: "DUBLNHP1", from: "2026-11-01", to: "2026-11-12", fromCar: "", toCar: "" });
  });

  it("offers no hard copy for the out of service reports, as the old screen", async () => {
    routes();
    page();
    await userEvent.selectOptions(await screen.findByLabelText("Report"), "OSDETAIL");
    await userEvent.selectOptions(screen.getByLabelText("Printer"), "DUBLNHP1");
    expect(screen.getByRole("button", { name: "Hard Copy" })).toBeDisabled();
  });

  it("is routed from VHYT27I", () => {
    expect(CLEAN_PAGES.VHYT27I).toBeTypeOf("function");
  });
});
