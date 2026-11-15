import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { CarEventReportPage, IncidentNarrativePage, VehicleEventReportPage } from "./VehicleMaintPages";

const OPTIONS = { printers: [{ description: "DUBLIN TMZ", vtam: "DUBLNHP1" }], carTypes: ["ALL", "A2", "C2"], homeYards: ["ALL", "CY", "HY"], eventTypes: ["CE", "MOD"] };
const base = { "GET /api/v1/vehicle-maintenance/reports/options": () => ({ body: OPTIONS }), "GET /api/v1/auth/csrf": () => ({ status: 204 }) };
const show = async (ui: React.ReactNode) => { render(<AppProvider>{ui}</AppProvider>); };

describe("Incident narrative (old VHNAR02I)", () => {
  it("shows the narrative lines of an incident", async () => {
    const mw = fakeMiddleware({ "GET /api/v1/vehicle-maintenance/incidents/29990010/narrative": () => ({ body: { incident: "29990010", lines: ["DOOR STUCK", "REPAIRED"] } }) });
    await show(<IncidentNarrativePage />);
    expect(await screen.findByRole("button", { name: "Show" })).toBeDisabled();
    await userEvent.type(screen.getByLabelText("Incident No"), "29990010");
    await userEvent.click(screen.getByRole("button", { name: "Show" }));
    expect(await screen.findByText(/DOOR STUCK/)).toBeInTheDocument();
    expect(mw.calls("/api/v1/vehicle-maintenance/incidents/29990010/narrative")).toHaveLength(1);
  });

  it("shows the middleware's error", async () => {
    fakeMiddleware({ "GET /api/v1/vehicle-maintenance/incidents/12/narrative": () => ({ status: 422, body: { detail: "Incident not found." } }) });
    await show(<IncidentNarrativePage />);
    await userEvent.type(await screen.findByLabelText("Incident No"), "12");
    await userEvent.click(screen.getByRole("button", { name: "Show" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Incident not found.");
  });
});

describe("Vehicle event report (old VHVM12I)", () => {
  it("needs a printer, then sends the criteria", async () => {
    const mw = fakeMiddleware({ ...base, "POST /api/v1/vehicle-maintenance/reports/events/print": () => ({ body: { message: "Report submitted." } }) });
    await show(<VehicleEventReportPage />);
    await userEvent.selectOptions(await screen.findByLabelText("Event Type"), "MOD");
    await userEvent.type(screen.getByLabelText("Car No"), "1101");
    expect(screen.getByRole("button", { name: "Hard Copy" })).toBeDisabled();
    await userEvent.selectOptions(screen.getByLabelText("Select Printer"), "DUBLNHP1");
    await userEvent.click(screen.getByRole("button", { name: "Hard Copy" }));
    await waitFor(() => expect(mw.calls("/api/v1/vehicle-maintenance/reports/events/print")).toHaveLength(1));
    expect(mw.calls("/api/v1/vehicle-maintenance/reports/events/print")[0].body).toMatchObject({ eventType: "MOD", car: "1101", printer: "DUBLNHP1", carType: "" });
    expect(await screen.findByText("Report submitted.")).toBeInTheDocument();
  });
});

describe("Car event report (old VHVM13i)", () => {
  it("lists the event types the backend gives", async () => {
    fakeMiddleware(base);
    await show(<CarEventReportPage />);
    const types = await screen.findByLabelText("Event Type");
    expect([...(types as HTMLSelectElement).options].map(o => o.value)).toEqual(["", "CE", "MOD"]);
  });

  it("shows the old screen's missing-field messages", async () => {
    fakeMiddleware({ ...base, "POST /api/v1/vehicle-maintenance/reports/car-events/print": () => ({ status: 400, body: { detail: "Car type is missing.\nComments are missing." } }) });
    await show(<CarEventReportPage />);
    await userEvent.selectOptions(await screen.findByLabelText("Select Printer"), "DUBLNHP1");
    await userEvent.click(screen.getByRole("button", { name: "Submit" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Comments are missing.");
  });

  it("submits the batch parameters", async () => {
    const mw = fakeMiddleware({ ...base, "POST /api/v1/vehicle-maintenance/reports/car-events/print": () => ({ body: { message: "Valid." } }) });
    await show(<CarEventReportPage />);
    await userEvent.selectOptions(await screen.findByLabelText("Car Types"), "C2");
    await userEvent.type(screen.getByLabelText("Tracking Ref No"), "R123");
    await userEvent.type(screen.getByLabelText("Comment:"), "CHECK BRAKES");
    await userEvent.selectOptions(screen.getByLabelText("Select Printer"), "DUBLNHP1");
    await userEvent.click(screen.getByRole("button", { name: "Submit" }));
    await waitFor(() => expect(mw.calls("/api/v1/vehicle-maintenance/reports/car-events/print")).toHaveLength(1));
    expect(mw.calls("/api/v1/vehicle-maintenance/reports/car-events/print")[0].body).toMatchObject({ carType: "C2", trackingRefNo: "R123", comments: "CHECK BRAKES", printer: "DUBLNHP1" });
  });
});
