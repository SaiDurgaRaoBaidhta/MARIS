import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { PrevMaintPage } from "./PrevMaintPage";

const OPTIONS = { revenueDate: "2026-11-12", fromDate: "2026-01-01",
  reports: ["HPR - PM Hours by Home Yard or ALL", "HPM - PM Hours with MTBI by Home Yard or ALL", "PMR - PM Hours by PM Type", "HPD - PM Hours descending order", "HNP - Hours to Next PM"],
  homeYards: ["ALL", "CY", "DY", "HY", "RY"], locations: ["ALL", "CY", "DY", "HY", "RY"], pmTypes: ["ALL", "1", "2", "A"], printers: [{ description: "DUBLIN TMZ", vtam: "DUBLNHP1" }] };
const page = async () => { render(<AppProvider><PrevMaintPage /></AppProvider>); await screen.findByLabelText("Home yard"); };

describe("Preventive maintenance reports (old VHHPM1I and its five reports)", () => {
  it("shows the hours report for a home yard", async () => {
    const mw = fakeMiddleware({ "GET /api/v1/preventive-maintenance/options": () => ({ body: OPTIONS }),
      "GET /api/v1/preventive-maintenance/reports/hours-by-yard": () => ({ body: { report: "HOURS_BY_YARD", name: OPTIONS.reports[0], revenueDate: "2026-11-12",
        fromDate: "2026-01-01", count: 1, cars: [{ car: "1101", status: "GO", location: "", homeYard: "CY", hoursSinceLastPm: 120, nextPm: "2", trainIncident: "TI" }] } }) });
    await page();
    await userEvent.selectOptions(screen.getByLabelText("Home yard"), "CY");
    await userEvent.click(screen.getByRole("button", { name: "Display" }));
    const table = await screen.findByRole("table", { name: "Cars" });
    expect(within(table).getByText("1101")).toBeInTheDocument();
    expect(within(table).getByRole("cell", { name: "TI" })).toBeInTheDocument();
    expect(Object.fromEntries(mw.calls("/api/v1/preventive-maintenance/reports/hours-by-yard")[0].query)).toMatchObject({ homeYard: "CY" });
  });

  it("report 5 asks for a location instead of a home yard; report 3 shows the PM criteria", async () => {
    fakeMiddleware({ "GET /api/v1/preventive-maintenance/options": () => ({ body: OPTIONS }) });
    await page();
    expect(screen.queryByLabelText("PM type")).not.toBeInTheDocument();
    await userEvent.click(screen.getByLabelText(OPTIONS.reports[4]));
    expect(screen.getByLabelText("Current location")).toBeInTheDocument();
    expect(screen.queryByLabelText("Home yard")).not.toBeInTheDocument();
    await userEvent.click(screen.getByLabelText(OPTIONS.reports[2]));
    expect(screen.getByLabelText("PM type")).toBeInTheDocument();
    expect(screen.getByLabelText("Car from")).toHaveAttribute("maxLength", "4");
  });

  it("shows the MTBI summary per yard", async () => {
    fakeMiddleware({ "GET /api/v1/preventive-maintenance/options": () => ({ body: OPTIONS }),
      "GET /api/v1/preventive-maintenance/reports/mtbi": () => ({ body: { report: "MTBI", name: OPTIONS.reports[1], count: 0, cars: [],
        mtbi: [{ shop: "CY", hours: 1000, incidents: 4, cars: 2, average: 250 }] } }) });
    await page();
    await userEvent.click(screen.getByLabelText(OPTIONS.reports[1]));
    await userEvent.selectOptions(screen.getByLabelText("Home yard"), "ALL");
    await userEvent.click(screen.getByRole("button", { name: "Display" }));
    const table = await screen.findByRole("table", { name: "MTBI" });
    expect(within(table).getByText("250")).toBeInTheDocument();
  });

  it("shows the middleware's message", async () => {
    fakeMiddleware({ "GET /api/v1/preventive-maintenance/options": () => ({ body: OPTIONS }),
      "GET /api/v1/preventive-maintenance/reports/hours-by-yard": () => ({ status: 400, body: { detail: "You Must Enter a Home Yard or ALL" } }) });
    await page();
    await userEvent.click(screen.getByRole("button", { name: "Display" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("You Must Enter a Home Yard or ALL");
  });

  it("prints the chosen report", async () => {
    const mw = fakeMiddleware({ "GET /api/v1/preventive-maintenance/options": () => ({ body: OPTIONS }), "GET /api/v1/auth/csrf": () => ({ status: 204 }),
      "POST /api/v1/preventive-maintenance/reports/hours-descending/print": () => ({ body: { message: "The report is submited" } }) });
    await page();
    await userEvent.click(screen.getByLabelText(OPTIONS.reports[3]));
    await userEvent.selectOptions(screen.getByLabelText("Home yard"), "RY");
    await userEvent.selectOptions(screen.getByLabelText("Printer"), "DUBLNHP1");
    await userEvent.click(screen.getByRole("button", { name: "Print" }));
    await waitFor(() => expect(mw.calls("/api/v1/preventive-maintenance/reports/hours-descending/print")).toHaveLength(1));
    expect(mw.calls("/api/v1/preventive-maintenance/reports/hours-descending/print")[0].body).toMatchObject({ homeYard: "RY", printer: "DUBLNHP1" });
  });
});
