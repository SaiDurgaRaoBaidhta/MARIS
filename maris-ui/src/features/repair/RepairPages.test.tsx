import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { RepairPage } from "./RepairPages";

const OPTIONS = { causes: [{ code: "A", description: "ADJUSTED" }], dispositions: [{ code: "R", description: "REPLACED" }], yards: ["CY", "HY"],
  systemChangePrompt: "Change the system?", deletePrompt: "Delete this repair?", componentDeletePrompt: "Delete?" };
const INCIDENT = { incident: "29990010", car: "1101", status: "OPN", type: "REG", consist: "201-03", nextDocument: 2,
  repairs: [{ document: 1, date: "10-02-2026", time: "10:15:00", location: "HY", narrative: "DOOR ADJUSTED" }],
  defaults: { location: "HY", date: "10-02-2026", time: "1015", employee: "12345" } };
const DETAILS = { document: 1, cause: "A", serialOn: "", serialOff: "", deleted: "", partControl: "", system: "DR", subSystem: "01", disposition: "R", warranty: "N",
  laborHours: 2, employee: "12345", location: "HY", date: "10-02-2026", time: "1015", narratives: [{ number: 1, userGroup: "BRIGHTCO", date: "10-02-2026", time: "10:20:00", text: "DOOR ADJUSTED" }] };
const base = {
  "GET /api/v1/auth/csrf": () => ({ status: 204 }),
  "GET /api/v1/repair/options": () => ({ body: OPTIONS }),
  "GET /api/v1/repair/incidents/29990010": () => ({ body: INCIDENT }),
  "GET /api/v1/repair/incidents/29990010/repairs/1": () => ({ body: DETAILS }),
  "GET /api/v1/repair/incidents/29990010/repairs/1/components": () => ({ body: { title: "VHRV02I - Vehicle Tracking - Repair Component Screen", components: [], nextDocument: 1, message: null } }),
};

async function listed(screenCode: "VHRV01I" | "VHRV03I" | "VHRV04I") {
  render(<AppProvider><RepairPage screen={screenCode} /></AppProvider>);
  await userEvent.type(await screen.findByLabelText("Incident No"), "29990010");
  await userEvent.click(screen.getByRole("button", { name: "List" }));
  return screen.findByRole("table", { name: "Previous repair actions" });
}

describe("Vehicle repair (old VHRV01I / VHRV02I / VHRV03I / VHRV04I)", () => {
  it("lists the incident's repairs and starts the next repair from the last one", async () => {
    const mw = fakeMiddleware(base);
    const table = await listed("VHRV01I");
    expect(within(table).getByText("DOOR ADJUSTED")).toBeInTheDocument();
    expect(mw.calls("/api/v1/repair/incidents/29990010")[0].query.get("screen")).toBe("VHRV01I");
    expect(screen.getByLabelText("Emp No")).toHaveValue("12345");
    expect(screen.getByLabelText("Repair Time (HHMM)")).toHaveValue("1015");
  });

  it("adds a repair and shows the old screen's refusal", async () => {
    const mw = fakeMiddleware({ ...base, "POST /api/v1/repair/incidents/29990010/repairs": () => ({ status: 400, body: { detail: "Invalid Repr Cause Cd, Invalid Narr" } }) });
    await listed("VHRV01I");
    await userEvent.type(screen.getByLabelText("Repair Narrative"), "REPLACED MOTOR");
    await userEvent.click(screen.getByRole("button", { name: "Add Repr" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Invalid Repr Cause Cd, Invalid Narr");
    expect(mw.calls("/api/v1/repair/incidents/29990010/repairs")[0].body).toMatchObject({ location: "HY", employee: "12345", narrative: "REPLACED MOTOR" });
  });

  it("shows a repair with its narratives and components", async () => {
    fakeMiddleware(base);
    const table = await listed("VHRV01I");
    await userEvent.click(within(table).getByText("DOOR ADJUSTED"));
    expect(await screen.findByRole("table", { name: "Repair components" })).toBeInTheDocument();
    expect(screen.getByLabelText("Sys")).toHaveValue("DR");
    expect(screen.queryByRole("button", { name: "Add Repr" })).toBeNull();
  });

  it("changes a repair in maintenance (VHRV04I)", async () => {
    const mw = fakeMiddleware({ ...base, "POST /api/v1/repair/incidents/29990010/repairs/1": () => ({ body: { done: true, message: "Repair Record Successfully Updated", partControl: "" } }) });
    const table = await listed("VHRV04I");
    await userEvent.click(within(table).getByText("DOOR ADJUSTED"));
    await waitFor(() => expect(screen.getByLabelText("Sys")).toHaveValue("DR"));
    await userEvent.clear(screen.getByLabelText("Emp No"));
    await userEvent.type(screen.getByLabelText("Emp No"), "22222");
    await userEvent.click(screen.getByRole("button", { name: "Change" }));
    await waitFor(() => expect(mw.calls("/api/v1/repair/incidents/29990010/repairs/1").filter(c => c.method === "POST")).toHaveLength(1));
    expect(mw.calls("/api/v1/repair/incidents/29990010/repairs/1").find(c => c.method === "POST")?.body).toMatchObject({ employee: "22222", system: "DR" });
  });

  it("adds an event repair (VHRV03I) with only date, time and narrative", async () => {
    const mw = fakeMiddleware({ ...base, "GET /api/v1/repair/incidents/29990010": () => ({ body: { ...INCIDENT, type: "EVT", defaults: null } }),
      "POST /api/v1/repair/events/29990010/repairs": () => ({ body: { done: true, message: null, partControl: null } }) });
    await listed("VHRV03I");
    expect(screen.queryByLabelText("Sys")).toBeNull();
    await userEvent.type(screen.getByLabelText("Repair Narrative"), "CHECKED");
    await userEvent.click(screen.getByRole("button", { name: "Add Repr" }));
    await waitFor(() => expect(mw.calls("/api/v1/repair/events/29990010/repairs")).toHaveLength(1));
    expect(mw.calls("/api/v1/repair/events/29990010/repairs")[0].body).toEqual({ date: "", time: "", narrative: "CHECKED" });
  });
});
