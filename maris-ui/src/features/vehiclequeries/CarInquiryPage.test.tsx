import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { CarInquiryPage } from "./CarInquiryPage";

const INQ = { title: "VHSV06I - Status/History Vehicle Report for Car 1101", moveReason: { subLocation: "CY-T12", code: "M1", description: "MOVED FOR PM" },
  car: { car: "1101", carShown: "1101", status: "NO GO  T", location: "CY01", homeYard: "CY", ato: "Y", mtbi: 12, warranty: "N", hoursFromLastPm: 0, cumulativeHours: 99 },
  incidents: [{ incident: "29990010", date: "2026-10-02", time: "10:15", symptom: "0120", location: "CY01", direction: "N", status: "OPN", hours: 3, deferredPriority: "" }] };

describe("Car inquiry (old VHSV07I / VHSV06I)", () => {
  it("shows the car, its incident's consist and narrative, and the repair", async () => {
    fakeMiddleware({
      "GET /api/v1/vehicle-queries/car-inquiry/1101": () => ({ body: INQ }),
      "GET /api/v1/vehicle-queries/car-inquiry/incidents/29990010": () => ({ body: { narrative: ["DOOR STUCK"], consist: "201-03", cars: ["<1101", "1102>"] } }),
      "GET /api/v1/vehicle-queries/car-inquiry/incidents/29990010/repairs": () => ({ body: [{ document: 2, system: "DR", subSystem: "01", disposition: "R",
        serialOn: "ON1", serialOff: "OFF1", partControl: "P1" }] }),
      "GET /api/v1/vehicle-queries/car-inquiry/incidents/29990010/repairs/2": () => ({ body: { narrative: ["ADJUSTED"], components: [] } }) });
    render(<AppProvider><CarInquiryPage /></AppProvider>);
    await userEvent.type(await screen.findByLabelText("Car Number"), "1101");
    await userEvent.click(screen.getByRole("button", { name: "Display" }));
    expect(await screen.findByLabelText("Repair narrative")).toHaveTextContent("ADJUSTED");
    expect(screen.getByLabelText("Narrative")).toHaveTextContent("DOOR STUCK");
    expect(screen.getByText(/<1101/)).toBeInTheDocument();
    expect(screen.getByText(/MOVED FOR PM/)).toBeInTheDocument();
  });

  it("shows the old message for a bad car number", async () => {
    fakeMiddleware({ "GET /api/v1/vehicle-queries/car-inquiry/11A1": () => ({ status: 400, body: { detail: "Invalid Car Number." } }) });
    render(<AppProvider><CarInquiryPage /></AppProvider>);
    await userEvent.type(await screen.findByLabelText("Car Number"), "11A1");
    await userEvent.click(screen.getByRole("button", { name: "Display" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Invalid Car Number.");
  });
});
