import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { VehicleIncidentMaintenancePage, VehicleIncidentPage, VehicleIncidentUpdatePage } from "./VehicleIncidentPages";

const OPTIONS = { type: "YRD", title: "VHINC08I - Vehicle Tracking (Incident Veh - Yard)", date: "11-14-2026", durations: [{ code: "2", description: "IN YARD" }],
  duration: "", narrativeDate: "11-14-2026", narrativeTime: "10:20", userGroup: "BRIGHTCO" };
const DETAILS = { incident: "A0012345", car: "1101", consist: "201-03", type: "YRD", status: "OPN", symptom: "0120", duration: "2", date: "11-10-2026", time: "09:15",
  speed: "", direction: "N", location: "CY01", deferredPriority: "N", cumulativeHours: 12, cumulativeMinutes: 30,
  lastUpdate: { date: "2026-11-11", time: "10:00:00", terminal: "T1" }, maintenanceUpdate: { date: "", time: "", terminal: "" }, carsInConsist: "1101 1102",
  narratives: [{ number: 1, userGroup: "SHOPCY", date: "11-10-2026", time: "09:20:00", text: "DOOR STUCK" }] };

describe("Vehicle incidents (old VHINC08I / VHINC09I / VHINC10I)", () => {
  it("files an incident and shows the old success line", async () => {
    const m = fakeMiddleware({
      "GET /api/v1/incidents/vehicle-incident/options": () => ({ body: OPTIONS }),
      "POST /api/v1/incidents/vehicle-incident": () => ({ body: { incident: "A0012345", message: "Incd No A0012345 for Car 1101 was filed succesfully", warnings: [] } }) });
    render(<AppProvider><VehicleIncidentPage /></AppProvider>);
    expect(await screen.findByDisplayValue("11-14-2026")).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText("Car No"), "1101");
    await userEvent.type(screen.getByLabelText("Symptom Code"), "0120");
    await userEvent.type(screen.getByLabelText("Discovered Dur"), "2");
    await userEvent.type(screen.getByLabelText("Time of Incident"), "0915");
    await userEvent.type(screen.getByLabelText("Location"), "CY01");
    await userEvent.type(screen.getByLabelText("Narrative"), "DOOR STUCK");
    await userEvent.click(screen.getByRole("button", { name: "Create" }));
    expect(await screen.findByRole("status")).toHaveTextContent("A0012345");
    expect(m.calls("/api/v1/incidents/vehicle-incident")[0].body).toMatchObject({ type: "YRD", car: "1101", date: "11-14-2026", time: "0915", narrative: "DOOR STUCK" });
  });

  it("shows the old check messages", async () => {
    fakeMiddleware({
      "GET /api/v1/incidents/vehicle-incident/options": () => ({ body: OPTIONS }),
      "POST /api/v1/incidents/vehicle-incident": () => ({ status: 400, body: { detail: "Invalid car no, Invalid symp cd" } }) });
    render(<AppProvider><VehicleIncidentPage /></AppProvider>);
    await screen.findByDisplayValue("11-14-2026");
    await userEvent.click(screen.getByRole("button", { name: "Create" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Invalid car no, Invalid symp cd");
  });

  it("lists an incident and changes it", async () => {
    const m = fakeMiddleware({
      "GET /api/v1/incidents/vehicle-update/A0012345": () => ({ body: { incident: DETAILS, editing: { details: true, speedAndDirection: false, deferredPriority: false, narratives: true } } }),
      "POST /api/v1/incidents/vehicle-update/A0012345": () => ({ body: { message: "Incident A0012345 updated successfully." } }) });
    render(<AppProvider><VehicleIncidentUpdatePage /></AppProvider>);
    await userEvent.type(await screen.findByLabelText("Incident No"), "A0012345");
    await userEvent.click(screen.getByRole("button", { name: "List" }));
    expect(await screen.findByText("201-03")).toBeInTheDocument();
    expect(screen.getByLabelText("Narrative")).toHaveValue("DOOR STUCK");
    expect(screen.getByLabelText("Speed")).toBeDisabled();
    await userEvent.clear(screen.getByLabelText("Loc"));
    await userEvent.type(screen.getByLabelText("Loc"), "CY02");
    await userEvent.click(screen.getByRole("button", { name: "Update" }));
    expect(await screen.findByRole("status")).toHaveTextContent("updated successfully");
    expect(m.calls("/api/v1/incidents/vehicle-update/A0012345").find(c => c.method === "POST")?.body).toMatchObject({ location: "CY02", narrativeIndex: 0 });
  });

  it("maintains an incident with its status", async () => {
    const m = fakeMiddleware({
      "GET /api/v1/incidents/vehicle-maintenance/options": () => ({ body: { statuses: [{ code: "OPN", description: "OPEN" }, { code: "CLO", description: "CLOSED" }],
        durations: ["1", "2"], revenueDate: "11-13-2026" } }),
      "GET /api/v1/incidents/vehicle-maintenance/A0012345": () => ({ body: DETAILS }),
      "POST /api/v1/incidents/vehicle-maintenance/A0012345": () => ({ body: { message: "Incident updated." } }) });
    render(<AppProvider><VehicleIncidentMaintenancePage /></AppProvider>);
    await userEvent.type(await screen.findByLabelText("Incident No"), "A0012345");
    await userEvent.click(screen.getByRole("button", { name: "List" }));
    await userEvent.selectOptions(await screen.findByLabelText("Incd Status"), "CLO");
    await userEvent.click(screen.getByRole("button", { name: "Update" }));
    expect(await screen.findByRole("status")).toHaveTextContent("Incident updated.");
    expect(m.calls("/api/v1/incidents/vehicle-maintenance/A0012345").find(c => c.method === "POST")?.body).toMatchObject({ status: "CLO", cumulativeMinutes: 30 });
  });
});
