import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { TrackingChangePage } from "./TrackingChangePage";

const row = (incident: string) => ({ incident, eventType: "AWP", eventSubType: "1", status: "OPN", type: "SHP", date: "10-02-2026", symptom: "0120", change: "" });
const base = {
  "GET /api/v1/incidents/tracking-change/options": () => ({ body: { date: "11-14-2026", time: "0930", actions: ["Car Status", "DF - Defer Incd", "DI - Delete Incd",
    "ER - Error NOGO", "UD - Undelete Incd", "FC - Force Close", "MI - Move Incd"], statuses: ["GO", "NO GO", "PM", "USABLE"], defaultStatus: "GO",
    canChangeLocation: true, maintenanceFrom: "VHCI02" } }),
  "POST /api/v1/incidents/tracking-change/incidents": () => ({ body: { message: "", currentStatus: "NO GO STATUS", heading: "Change - Yes/No",
    rows: [row("S0000101"), row("S0000102")], changeEnabled: true } }),
  "GET /api/v1/auth/csrf": () => ({ status: 204 }),
};
const show = () => render(<AppProvider><TrackingChangePage /></AppProvider>);

describe("Tracking change incident (old VHCI02I)", () => {
  it("lists a car's incidents and sends the marked ones", async () => {
    const mw = fakeMiddleware({ ...base, "POST /api/v1/incidents/tracking-change/changes": () => ({ body: { message: "Incidents changed, Status changed to GO",
      currentStatus: "GO", listing: { message: "", currentStatus: "GO", heading: "", rows: [], changeEnabled: false } } }) });
    show();
    expect(await screen.findByRole("radio", { name: "Car Status" })).toBeChecked();
    await userEvent.type(screen.getByLabelText("Car No"), "1101");
    await userEvent.click(screen.getByRole("button", { name: "List Incds" }));
    expect(await screen.findByText("S0000102")).toBeInTheDocument();
    expect(mw.calls("/api/v1/incidents/tracking-change/incidents")[0].body).toEqual({ action: 1, status: "GO", car: "1101" });
    await userEvent.type(screen.getByLabelText("Change - Yes/No S0000102"), "Y");
    await userEvent.type(screen.getByLabelText("Narrative"), "FIXED");
    await userEvent.click(screen.getByRole("button", { name: "Change" }));
    expect(await screen.findByRole("status")).toHaveTextContent("Incidents changed");
    expect(mw.calls("/api/v1/incidents/tracking-change/changes")[0].body).toMatchObject({ action: 1, status: "GO", car: "1101", changes: { S0000102: "Y" },
      date: "11-14-2026", time: "0930", narrative: "FIXED", selected: "S0000102" });   // typing in a row selects it
  });

  it("keeps Change disabled when the old screen would", async () => {
    fakeMiddleware({ ...base, "POST /api/v1/incidents/tracking-change/incidents": () => ({ body: { message: "Invalid Action for Car", currentStatus: "GO",
      heading: "Change - Yes/No", rows: [], changeEnabled: false } }) });
    show();
    await userEvent.click(await screen.findByRole("radio", { name: "DI - Delete Incd" }));
    expect(screen.queryByLabelText("Status")).toBeNull();
    await userEvent.type(screen.getByLabelText("Car No"), "1101");
    await userEvent.click(screen.getByRole("button", { name: "List Incds" }));
    expect(await screen.findByRole("status")).toHaveTextContent("Invalid Action for Car");
    expect(screen.getByRole("button", { name: "Change" })).toBeDisabled();
  });
});
