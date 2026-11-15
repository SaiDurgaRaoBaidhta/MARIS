import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { VehicleHistoryPage, VehicleStatusPage } from "./VehiclePages";

const OPTIONS = { reports: ["Open/Repaired Incidents", "Open/Deferred Incidents"], deferredItems: ["A", "B", "C", "ALL"], fromDate: "2026-10-01", toDate: "2026-11-13",
  printers: [{ description: "DUBLIN TMZ", vtam: "DUBLNHP1" }] };
const CAR = { car: "1101", status: "NO GO  T", location: "CY01", homeYard: "CY", ato: "Y", mtbi: 7, warranty: "N", currentPm: "04", nextPm: "05", hoursFromLastPm: 12,
  timesWashed: 3, washOverdueDays: 0, camera: "", cumulativeHours: 99, lastWash: "", softwareMarked: false };
const INC = { incident: "29990010", date: "2026-10-02", time: "10:15", symptom: "0120", location: "CY01", direction: "N", status: "DEF", hours: 3, deferredPriority: "B",
  consist: "201", dash: 3, dateInServiceRev: "2026-10-02", dateInService: "2026-10-02", timeInService: "05:00" };
const base = { "GET /api/v1/vehicle-queries/vehicle-status/options": () => ({ body: OPTIONS }), "GET /api/v1/auth/csrf": () => ({ status: 204 }) };

describe("Vehicle status (old VHSV01I / VHSV02I)", () => {
  it("starts with the old dates and shows a car's incidents, narrative and consist", async () => {
    const mw = fakeMiddleware({ ...base,
      "GET /api/v1/vehicle-queries/vehicle-status": () => ({ body: { title: "VHSV02I - Open/Deferred Incidents", car: CAR, incidents: [INC] } }),
      "GET /api/v1/vehicle-queries/vehicle-status/incidents/29990010/narrative": () => ({ body: ["DOOR STUCK"] }),
      "GET /api/v1/vehicle-queries/vehicle-status/consist": () => ({ body: { consist: "201-03", cars: ["<1101", "1102>"] } }) });
    render(<AppProvider><VehicleStatusPage /></AppProvider>);
    expect(await screen.findByLabelText("From Date")).toHaveValue("2026-10-01");
    await userEvent.click(screen.getByLabelText("Open/Deferred Incidents"));
    await userEvent.selectOptions(screen.getByLabelText("Deferred Items"), "ALL");
    await userEvent.type(screen.getByLabelText("Car Number"), "1101");
    await userEvent.click(screen.getByRole("button", { name: "Display" }));
    expect(await screen.findByLabelText("Narrative")).toHaveTextContent("DOOR STUCK");
    expect(screen.getByText(/<1101/)).toBeInTheDocument();
    expect(Object.fromEntries(mw.calls("/api/v1/vehicle-queries/vehicle-status")[0].query)).toMatchObject({ report: "2", car: "1101", deferredItems: "ALL", fromDate: "2026-10-01" });
  });

  it("shows the old screen's message", async () => {
    fakeMiddleware({ ...base, "GET /api/v1/vehicle-queries/vehicle-status": () => ({ status: 400, body: { detail: "Car no does not exist" } }) });
    render(<AppProvider><VehicleStatusPage /></AppProvider>);
    await userEvent.type(await screen.findByLabelText("Car Number"), "9999");
    await userEvent.click(screen.getByRole("button", { name: "Display" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Car no does not exist");
  });
});

describe("Vehicle history (old VHSV04I / VHSV05I)", () => {
  it("shows an incident's repairs and a repair's components", async () => {
    fakeMiddleware({ ...base,
      "GET /api/v1/vehicle-queries/vehicle-history": () => ({ body: { title: "VHSV05I - History Vehicle Report", car: CAR, incidents: [{ ...INC, pmTeam: "A" }] } }),
      "GET /api/v1/vehicle-queries/vehicle-history/incidents/29990010/narrative": () => ({ body: ["BRAKES"] }),
      "GET /api/v1/vehicle-queries/vehicle-history/incidents/29990010/repairs": () => ({ body: [{ document: 2, system: "BR", subSystem: "01", disposition: "R",
        serialOn: "ON1", serialOff: "OFF1", partControl: "P1" }] }),
      "GET /api/v1/vehicle-queries/vehicle-history/incidents/29990010/repairs/2": () => ({ body: { narrative: ["PADS REPLACED"], components: [{ document: 1,
        component: "BRK", serialOff: "S9", disposition: "R", fault: "F1" }] } }) });
    render(<AppProvider><VehicleHistoryPage /></AppProvider>);
    expect(screen.queryByLabelText("Deferred Items")).toBeNull();
    await userEvent.type(await screen.findByLabelText("Car Number"), "1101");
    await userEvent.click(screen.getByRole("button", { name: "Display" }));
    expect(await screen.findByLabelText("Repair narrative")).toHaveTextContent("PADS REPLACED");
    expect(within(screen.getByRole("table", { name: "Components" })).getByText("BRK")).toBeInTheDocument();
    expect(within(screen.getByRole("table", { name: "Repairs" })).getByText("BR/01")).toBeInTheDocument();
  });

  it("prints", async () => {
    const mw = fakeMiddleware({ ...base, "POST /api/v1/vehicle-queries/vehicle-history/print": () => ({ body: { message: "Submitted" } }) });
    render(<AppProvider><VehicleHistoryPage /></AppProvider>);
    await userEvent.type(await screen.findByLabelText("Car Number"), "1101");
    await userEvent.selectOptions(screen.getByLabelText("Printer"), "DUBLNHP1");
    await userEvent.click(screen.getByRole("button", { name: "Print" }));
    await waitFor(() => expect(mw.calls("/api/v1/vehicle-queries/vehicle-history/print")).toHaveLength(1));
    expect(mw.calls("/api/v1/vehicle-queries/vehicle-history/print")[0].body).toMatchObject({ car: "1101", printer: "DUBLNHP1", fromDate: "2026-10-01" });
  });
});
