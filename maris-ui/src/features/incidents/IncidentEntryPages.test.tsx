import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { CarIncidentPage, RevenueIncidentPage, TrainIncidentPage } from "./IncidentEntryPages";

const show = (ui: React.ReactNode) => render(<AppProvider>{ui}</AppProvider>);

describe("Train incident (old VHINC01I)", () => {
  it("starts with the revenue date and creates the incident", async () => {
    const mw = fakeMiddleware({
      "GET /api/v1/incidents/train/defaults": () => ({ body: { date: "11-13-2026", time: "1015" } }),
      "POST /api/v1/incidents/train": () => ({ body: { message: "Incd No T0001234 for Consist 201 was generated successfully", incidentNo: "T0001234" } }),
    });
    show(<TrainIncidentPage />);
    expect(await screen.findByDisplayValue("11-13-2026")).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText("Consist No"), "201");
    await userEvent.type(screen.getByLabelText("Symptom"), "0120");
    await userEvent.click(screen.getByRole("button", { name: "Create" }));
    expect(await screen.findByText(/T0001234/)).toBeInTheDocument();
    expect(mw.calls("/api/v1/incidents/train")[0].body).toMatchObject({ consistNo: "201", symptom: "0120", date: "11-13-2026", time: "1015" });
  });

  it("lists an incident in Update mode and changes it", async () => {
    const mw = fakeMiddleware({
      "GET /api/v1/incidents/train/defaults": () => ({ body: { date: "11-13-2026", time: "1015" } }),
      "GET /api/v1/incidents/train/40000001": () => ({ body: { incidentNo: "40000001", consistNo: "201", dashNo: "03", symptom: "0120", discovery: "1", date: "11-13-2026",
        time: "1015", speed: "35", direction: "N", location: "A10", narratives: [{ narrNo: 1, userGrp: "VEHSUPR", dateNarr: "", timeNarr: "", narr: "STOPPED" }] } }),
      "POST /api/v1/incidents/train/40000001": () => ({ body: { message: "Incd No 40000001 for Consist 201 was updated successfully.", incidentNo: "40000001" } }),
    });
    show(<TrainIncidentPage />);
    await userEvent.click(await screen.findByLabelText("Update"));
    await userEvent.type(screen.getByLabelText("Incident No"), "40000001");
    await userEvent.click(screen.getByRole("button", { name: "List" }));
    expect(await screen.findByDisplayValue("STOPPED")).toBeInTheDocument();
    const speed = screen.getByLabelText("Speed");
    await userEvent.clear(speed);
    await userEvent.type(speed, "40");
    await userEvent.click(screen.getByRole("button", { name: "Change" }));
    expect(await screen.findByText(/was updated successfully/)).toBeInTheDocument();
    expect(mw.calls("/api/v1/incidents/train/40000001").find(c => c.method === "POST")?.body).toMatchObject({ speed: "40", narrativeIndex: 1 });
  });
});

describe("Revenue incident (old VHINC06I)", () => {
  it("shows the old screen's refusal", async () => {
    fakeMiddleware({
      "GET /api/v1/incidents/revenue/defaults": () => ({ body: { date: "11-13-2026", time: "" } }),
      "POST /api/v1/incidents/revenue": () => ({ status: 400, body: { detail: "Invalid symp cd, Invalid time of incident" } }),
    });
    show(<RevenueIncidentPage />);
    await screen.findByDisplayValue("11-13-2026");
    await userEvent.type(screen.getByLabelText("Car No"), "1101");
    await userEvent.click(screen.getByRole("button", { name: "Create" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Invalid symp cd, Invalid time of incident");
  });
});

describe("Car incident (old VHINC03I)", () => {
  it("loads the type's options, fills the symptom's narrative and files the incident", async () => {
    const mw = fakeMiddleware({
      "GET /api/v1/incidents/car-incident/options": req => ({ body: { type: req.query.get("type"), title: "VHINC03I - Vehicle Tracking (Incident Veh - Shop)",
        date: "11-13-2026", discoveries: [{ code: "4", description: "SHOP" }], fixedDiscovery: req.query.get("type") === "SHN" ? "4" : "" } }),
      "GET /api/v1/incidents/car-incident/symptoms/0120/narrative": () => ({ body: { narrative: "DOORS DO NOT CLOSE" } }),
      "POST /api/v1/incidents/car-incident": () => ({ body: { message: "Incd No U0000001 for Car 1101 was filed succesfully", incidentNo: "U0000001" } }),
    });
    show(<CarIncidentPage />);
    await userEvent.selectOptions(await screen.findByLabelText("Incident type"), "SHN");
    await waitFor(() => expect(screen.getByLabelText("Disc Dur")).toBeDisabled());
    await userEvent.type(screen.getByLabelText("Car No"), "1101");
    await userEvent.type(screen.getByLabelText("Symptom"), "0120");
    await userEvent.tab();
    expect(await screen.findByDisplayValue("DOORS DO NOT CLOSE")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Create" }));
    expect(await screen.findByText(/U0000001/)).toBeInTheDocument();
    expect(mw.calls("/api/v1/incidents/car-incident")[0].body).toMatchObject({ type: "SHN", carNo: "1101", discovery: "4", narrative: "DOORS DO NOT CLOSE" });
  });
});
