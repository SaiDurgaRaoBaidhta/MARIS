import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { HomeYardStatusPage, YardStatusPage } from "./StatusReportPage";

const OPTIONS = { yards: ["CY", "HY"], statuses: [{ status: "GO", description: "GO CAR" }], narrChoices: ["N", "Y"], narr: "N", carFrom: "0096", carTo: "5000",
  titles: ["Base", "Status", "NOGO", "Summary"], printers: [{ description: "DUBLIN TMZ", vtam: "DUBLNHP1" }] };
const checked = (option: string) => ({ option, yard: "CY", status: "", narr: "Y", carFrom: "0096", carTo: "5000", title: `VHYS0xI - ${option}` });
const base = { "GET /api/v1/vehicle-queries/home-yard-status/options": () => ({ body: OPTIONS }), "GET /api/v1/auth/csrf": () => ({ status: 204 }) };
const page = async () => { render(<AppProvider><HomeYardStatusPage /></AppProvider>); await screen.findByLabelText("Yard"); };

describe("Home yard status (old VHYS01I .. VHYS05I)", () => {
  it("shows the Base report: cars, a car's incidents and an incident's narrative", async () => {
    const mw = fakeMiddleware({ ...base,
      "GET /api/v1/vehicle-queries/home-yard-status/cars": () => ({ body: { criteria: checked("BASE"), cars: [{ car: "1101", status: "NO GO  T", location: "CY01", ato: "Y", hours: 12, minutes: 5, mtbi: 7, homeYard: "CY" }] } }),
      "GET /api/v1/vehicle-queries/home-yard-status/cars/1101/incidents": () => ({ body: { car: "1101", incidents: [{ incident: "29990010", symptom: "B1", location: "CY01", date: "2026-10-02", time: "10:15", status: "OPN OPEN", direction: "N" }] } }),
      "GET /api/v1/vehicle-queries/home-yard-status/narrative": () => ({ body: ["DOOR STUCK"] }) });
    await page();
    await userEvent.type(screen.getByLabelText("Yard"), "CY");
    await userEvent.selectOptions(screen.getByLabelText("Narr (Y/N)"), "Y");
    await userEvent.click(screen.getByRole("button", { name: "Display" }));
    const table = await screen.findByRole("table", { name: "Incidents" });
    expect(Object.fromEntries(mw.calls("/api/v1/vehicle-queries/home-yard-status/cars")[0].query)).toMatchObject({ option: "base", yard: "CY", narr: "Y", carFrom: "0096" });
    await userEvent.click(within(table).getByText("29990010"));
    expect(await screen.findByLabelText("Narrative")).toHaveTextContent("DOOR STUCK");
    expect(Object.fromEntries(mw.calls("/api/v1/vehicle-queries/home-yard-status/narrative")[0].query)).toEqual({ car: "1101", incident: "29990010", homeYard: "CY" });
  });

  it("shows the old screen's message", async () => {
    fakeMiddleware({ ...base, "GET /api/v1/vehicle-queries/home-yard-status/cars": () => ({ status: 400, body: { detail: "You Have Entered an Invalid Yard" } }) });
    await page();
    await userEvent.type(screen.getByLabelText("Yard"), "ZZ");
    await userEvent.click(screen.getByRole("button", { name: "Display" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("You Have Entered an Invalid Yard");
  });

  it("shows the Summary report and its summary by car type", async () => {
    fakeMiddleware({ ...base,
      "GET /api/v1/vehicle-queries/home-yard-status/summary": () => ({ body: { criteria: checked("SUMMARY"), header: "CONCORD HOME YARD", cars: [{ car: "1101", atoMissing: true, status: "GO", location: "CY01", homeYard: "CY" }] } }),
      "GET /api/v1/vehicle-queries/home-yard-status/type-summary": () => ({ body: { yard: "CY", header: "CONCORD HOME YARD", types: ["A"], headings: ["A", "Total"],
        rows: [{ row: 1, name: "Total  Home Yard Cars", byType: [3], total: 3 }] } }) });
    await page();
    await userEvent.click(screen.getByLabelText("Home Yard Summary"));
    await userEvent.type(screen.getByLabelText("Yard"), "CY");
    await userEvent.click(screen.getByRole("button", { name: "Display" }));
    expect(await screen.findByText("1101 *")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Summary" }));
    expect(await screen.findByRole("table", { name: "Summary by car type" })).toHaveTextContent(/Total\s+Home Yard Cars/);
  });

  it("prints the chosen report", async () => {
    const mw = fakeMiddleware({ ...base, "POST /api/v1/vehicle-queries/home-yard-status/print": () => ({ body: { message: "Submitted" } }) });
    await page();
    await userEvent.click(screen.getByLabelText("Home Yard NOGO"));
    await userEvent.type(screen.getByLabelText("Yard"), "HY");
    await userEvent.selectOptions(screen.getByLabelText("Printer"), "DUBLNHP1");
    await userEvent.click(screen.getByRole("button", { name: "Print" }));
    await waitFor(() => expect(mw.calls("/api/v1/vehicle-queries/home-yard-status/print")).toHaveLength(1));
    expect(mw.calls("/api/v1/vehicle-queries/home-yard-status/print")[0].body).toMatchObject({ option: "nogo", yard: "HY", printer: "DUBLNHP1" });
  });
});

describe("Yard status (old VHYS06I .. VHYS10I)", () => {
  const yardBase = { "GET /api/v1/vehicle-queries/yard-status/options": () => ({ body: OPTIONS }), "GET /api/v1/auth/csrf": () => ({ status: 204 }) };

  it("asks for the narrative with the car's location as the yard", async () => {
    const mw = fakeMiddleware({ ...yardBase,
      "GET /api/v1/vehicle-queries/yard-status/cars": () => ({ body: { criteria: checked("BASE"), cars: [{ car: "1101", status: "GO", location: "HY01", ato: "Y",
        hours: 1, minutes: 2, mtbi: 3, homeYard: "CY", narrativeYard: "HY01" }] } }),
      "GET /api/v1/vehicle-queries/yard-status/cars/1101/incidents": () => ({ body: { car: "1101", incidents: [{ incident: "29990010", symptom: "B1", location: "CY01",
        date: "2026-10-02", time: "10:15", status: "OPN", direction: "N" }] } }),
      "GET /api/v1/vehicle-queries/yard-status/narrative": () => ({ body: ["FIXED"] }) });
    render(<AppProvider><YardStatusPage /></AppProvider>);
    await userEvent.type(await screen.findByLabelText("Yard"), "HY");
    await userEvent.selectOptions(screen.getByLabelText("Narr (Y/N)"), "Y");
    await userEvent.click(screen.getByRole("button", { name: "Display" }));
    await userEvent.click(within(await screen.findByRole("table", { name: "Incidents" })).getByText("29990010"));
    expect(await screen.findByLabelText("Narrative")).toHaveTextContent("FIXED");
    expect(Object.fromEntries(mw.calls("/api/v1/vehicle-queries/yard-status/narrative")[0].query)).toEqual({ car: "1101", incident: "29990010", yard: "HY01" });
  });

  it("shows the Summary report with MTBI", async () => {
    fakeMiddleware({ ...yardBase, "GET /api/v1/vehicle-queries/yard-status/summary": () => ({ body: { criteria: checked("SUMMARY"), header: "HAYWARD YARD",
      cars: [{ car: "1101", atoMissing: false, status: "GO", mtbi: 42, homeYard: "CY" }] } }) });
    render(<AppProvider><YardStatusPage /></AppProvider>);
    await userEvent.click(await screen.findByLabelText("Yard Summary"));
    await userEvent.type(screen.getByLabelText("Yard"), "HY");
    await userEvent.click(screen.getByRole("button", { name: "Display" }));
    const table = await screen.findByRole("table", { name: "Cars" });
    expect(within(table).getByText("42")).toBeInTheDocument();
    expect(screen.getByText("HAYWARD YARD")).toBeInTheDocument();
  });
});
