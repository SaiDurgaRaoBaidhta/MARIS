import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { SymptomHistoryPage, WorksheetPage } from "./WorksheetPages";

const W = "/api/v1/incident-events/worksheet";
const EVENT = { number: 1, eventId: "2026-11-12-01", hasIncident: false, consist: "197-03", location: "CY01", direction: "N", speed: "35", time: "10:15:00", symptom: "0120",
  car: "4377", technician: "T1", narrative: "DOOR", trackingCode: "K1", actionCode: "A1", actionText: "ADJU", incident: "", dateInServiceRev: "2026-11-12",
  dateInService: "2026-11-12", timeInService: "05:00:00", binDash: 7, userGroup: "" };
const OPTIONS = { revenueDate: "2026-11-12", today: "2026-11-12", symptoms: [{ code: "0120", description: "DOORS WILL NOT CLOSE" }], technicians: [], actions: [], trackingCodes: [],
  createAllowed: true, message: null };
const base = {
  "GET /api/v1/auth/csrf": () => ({ status: 204 }),
  [`GET ${W}/options`]: () => ({ body: OPTIONS }),
  [`GET ${W}/events`]: () => ({ body: { date: "2026-11-12", events: [EVENT], message: null } }),
  [`GET ${W}/events/2026-11-12/1/corrective-action`]: () => ({ body: { hasCorrectiveAction: true, correctiveAction: "RESET BREAKER", narrative: "DOORS WILL NOT CLOSE ON 4377" } }),
};

describe("Vehicle desk work sheet (old VHIE03I / VHIE04I)", () => {
  const listed = async () => {
    render(<AppProvider><WorksheetPage /></AppProvider>);
    await waitFor(() => expect(screen.getByLabelText("For revenue date")).toHaveValue("2026-11-12"));
    await userEvent.click(screen.getByRole("button", { name: "List" }));
    return screen.findByRole("table", { name: "Events" });
  };

  it("lists the revenue date's events and shows a picked event with its corrective action", async () => {
    const mw = fakeMiddleware(base);
    const table = await listed();
    expect(Object.fromEntries(mw.calls(`${W}/events`)[0].query)).toEqual({ date: "2026-11-12" });
    await userEvent.click(within(table).getByText("2026-11-12-01"));
    await waitFor(() => expect(screen.getByLabelText("Corrective action text")).toHaveValue("RESET BREAKER"));
    expect(screen.getByLabelText("Car No")).toHaveValue("4377");
    expect(screen.getByLabelText("Symptom code text & user input")).toHaveValue("DOORS WILL NOT CLOSE ON 4377");
  });

  it("saves a picked event's changes and shows the old message", async () => {
    const mw = fakeMiddleware({ ...base, [`POST ${W}/events/2026-11-12/1`]: () => ({ body: { message: "Data was Updated  Successfully" } }) });
    const table = await listed();
    await userEvent.click(within(table).getByText("2026-11-12-01"));
    await waitFor(() => expect(screen.getByLabelText("Car No")).toHaveValue("4377"));
    await userEvent.clear(screen.getByLabelText("Loc"));
    await userEvent.type(screen.getByLabelText("Loc"), "CY02");
    await userEvent.click(screen.getByRole("button", { name: "Update" }));
    expect(await screen.findByRole("status")).toHaveTextContent("Data was Updated");
    expect(mw.calls(`${W}/events/2026-11-12/1`)[0].body).toMatchObject({ car: "4377", location: "CY02", consist: "197-03" });
  });

  it("files an incident for a picked event and shows the incident number", async () => {
    const mw = fakeMiddleware({ ...base, [`POST ${W}/events/2026-11-12/1/incident`]: () => ({ body: { message: "Incd No G000101  for Car 4377 was filed succesfully" } }) });
    const table = await listed();
    await userEvent.click(within(table).getByText("2026-11-12-01"));
    await waitFor(() => expect(screen.getByRole("button", { name: "CreMaint" })).toBeEnabled());
    await userEvent.click(screen.getByRole("button", { name: "CreMaint" }));
    expect(await screen.findByRole("status")).toHaveTextContent("G000101");
    expect(mw.calls(`${W}/events/2026-11-12/1/incident`)).toHaveLength(1);
  });

  it("deletes only after the user confirms, and shows the server's check messages", async () => {
    const mw = fakeMiddleware({ ...base, [`POST ${W}/events/2026-11-12/1/delete`]: () => ({ status: 400, body: { detail: "INVALID Direction" } }) });
    const table = await listed();
    await userEvent.click(within(table).getByText("2026-11-12-01"));
    await waitFor(() => expect(screen.getByRole("button", { name: "Delete" })).toBeEnabled());
    const confirm = vi.spyOn(window, "confirm").mockReturnValueOnce(false).mockReturnValueOnce(true);
    await userEvent.click(screen.getByRole("button", { name: "Delete" }));
    expect(mw.calls(`${W}/events/2026-11-12/1/delete`)).toHaveLength(0);
    await userEvent.click(screen.getByRole("button", { name: "Delete" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("INVALID Direction");
    confirm.mockRestore();
  });

  it("adds a new event", async () => {
    const mw = fakeMiddleware({ ...base, [`POST ${W}/events`]: () => ({ body: { message: "Data Added Successfully" } }) });
    render(<AppProvider><WorksheetPage /></AppProvider>);
    await waitFor(() => expect(screen.getByLabelText("For revenue date")).toHaveValue("2026-11-12"));
    await userEvent.type(screen.getByLabelText("Car No"), "4377");
    await userEvent.click(screen.getByRole("button", { name: "Add" }));
    expect(await screen.findByRole("status")).toHaveTextContent("Data Added Successfully");
    expect(mw.calls(`${W}/events`).find(c => c.method === "POST")?.body).toMatchObject({ car: "4377" });
  });
});

describe("Symptom code history (old VHIE07I)", () => {
  it("lists the events and incidents of a symptom code", async () => {
    const mw = fakeMiddleware({ [`GET ${W}/symptom-history/0120`]: () => ({ body: { symptom: "0120", days: 30, from: "2026-10-15", to: "2026-11-14", events: 1, incidents: 0,
      rows: [{ type: "E", car: "4377", symptom: "0120", dateInService: "11-01-2026", timeInService: "", consist: "197-03", incident: "", cars: ["<4377"], narrative: "DOOR" }],
      message: "No INCIDENTS  Found for That Symptom Code" } }) });
    render(<AppProvider><SymptomHistoryPage /></AppProvider>);
    await userEvent.type(await screen.findByLabelText("Symptom code"), "0120");
    await userEvent.type(screen.getByLabelText("Days"), "30");
    await userEvent.click(screen.getByRole("button", { name: "List" }));
    const table = await screen.findByRole("table", { name: "Symptom events and incidents" });
    expect(within(table).getByText("197-03")).toBeInTheDocument();
    expect(screen.getByText(/No INCIDENTS/)).toBeInTheDocument();
    expect(Object.fromEntries(mw.calls(`${W}/symptom-history/0120`)[0].query)).toEqual({ days: "30" });
  });
});
