import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { ConsistLookupPage, IncidentLogPage } from "./IncidentEventsPages";

const INCIDENT = { revenueDate: "2026-11-10", incident: "29990010", car: "4377", status: "OPN", symptom: "", time: "07:15", speed: "", direction: "", location: "", consist: "197-03", narrative: "BRAKES" };
const base = { "GET /api/v1/incident-events/revenue-date": () => ({ body: { revenueDate: "2026-11-12" } }), "GET /api/v1/incident-events/printers": () => ({ body: [{ description: "DUBLIN TMZ", vtam: "DUBLNHP1" }] }) };

describe("Event and incident log (old VHIE01I / VHIE02I)", () => {
  const page = async () => { render(<AppProvider><IncidentLogPage /></AppProvider>); await waitFor(() => expect(screen.getByLabelText("From")).toHaveValue("2026-11-12")); };

  it("starts on MARIS's revenue date and lists the incidents", async () => {
    const mw = fakeMiddleware({ ...base, "GET /api/v1/incident-events/incidents": () => ({ body: { from: "2026-11-01", to: "2026-11-12", count: 1, incidents: [INCIDENT], message: null } }) });
    await page();
    await userEvent.clear(screen.getByLabelText("From"));
    await userEvent.type(screen.getByLabelText("From"), "2026-11-01");
    await userEvent.click(screen.getByRole("button", { name: "List" }));
    const table = await screen.findByRole("table", { name: "Incidents" });
    expect(within(table).getByText("29990010")).toBeInTheDocument();
    expect(Object.fromEntries(mw.calls("/api/v1/incident-events/incidents")[0].query)).toEqual({ from: "2026-11-01", to: "2026-11-12" });
  });

  it("shows the old messages", async () => {
    fakeMiddleware({ ...base, "GET /api/v1/incident-events/incidents": () => ({ status: 400, body: { detail: "Future TO Date is Not Allowed" } }) });
    await page();
    await userEvent.click(screen.getByRole("button", { name: "List" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Future TO Date is Not Allowed");
  });

  it("prints the log", async () => {
    const mw = fakeMiddleware({ ...base, "GET /api/v1/auth/csrf": () => ({ status: 204 }), "POST /api/v1/incident-events/incidents/print": () => ({ body: { message: "Submitted" } }) });
    await page();
    await userEvent.selectOptions(screen.getByLabelText("Printer"), "DUBLNHP1");
    await userEvent.click(screen.getByRole("button", { name: "Print" }));
    await waitFor(() => expect(mw.calls("/api/v1/incident-events/incidents/print")).toHaveLength(1));
    expect(mw.calls("/api/v1/incident-events/incidents/print")[0].body).toEqual({ from: "2026-11-12", to: "2026-11-12", printer: "DUBLNHP1" });
  });
});

describe("Train or car look-up (old VHIE06I)", () => {
  const page = async () => { render(<AppProvider><ConsistLookupPage /></AppProvider>); await screen.findByLabelText("Car No"); };

  it("finds the train of a car, then its cars", async () => {
    fakeMiddleware({ "GET /api/v1/incident-events/cars/123/consist": () => ({ body: { car: "0123", consist: "532-01", con: "532", message: null } }),
      "GET /api/v1/incident-events/consists/532-01/cars": () => ({ body: { consist: "532-01", cars: [" 3198}", "{4074"], message: null } }) });
    await page();
    await userEvent.type(screen.getByLabelText("Car No"), "123");
    await userEvent.click(screen.getByRole("button", { name: "Find Train" }));
    await waitFor(() => expect(screen.getByLabelText("Train No")).toHaveValue("532-01"));
    await userEvent.click(screen.getByRole("button", { name: "Find Cars" }));
    const list = await screen.findByRole("list", { name: "Cars" });
    expect(within(list).getAllByRole("listitem")).toHaveLength(2);
  });

  it("shows the old message when nothing is found", async () => {
    fakeMiddleware({ "GET /api/v1/incident-events/cars/9999/consist": () => ({ body: { car: "9999", consist: "", con: "", message: "No Consit Found For That Car Number" } }) });
    await page();
    await userEvent.type(screen.getByLabelText("Car No"), "9999");
    await userEvent.click(screen.getByRole("button", { name: "Find Train" }));
    expect(await screen.findByText("No Consit Found For That Car Number")).toBeInTheDocument();
  });
});
