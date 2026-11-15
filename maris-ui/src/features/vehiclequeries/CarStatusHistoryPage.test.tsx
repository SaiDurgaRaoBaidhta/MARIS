import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { CarStatusHistoryPage } from "./CarStatusHistoryPage";

const change = { location: "CY01", date: "10-02-2026", time: "10:15:00", hours: 1234, minutes: 45, statusNew: "NO GO", statusPrevious: "GO", incident: "29990010",
  symptom: "B12", action: "STATUS CHANGED", dvsImpact: "Y", lastUpdateDate: "10-02-2026", lastUpdateTime: "10:15:00", lastUpdateTerminal: "T1",
  maintUpdateDate: "", maintUpdateTime: "", maintUpdateTerminal: "", function: "VHYT01" };
const base = { "GET /api/v1/vehicle-queries/printers": () => ({ body: [{ description: "DUBLIN TMZ", vtam: "DUBLNHP1" }] }), "GET /api/v1/auth/csrf": () => ({ status: 204 }) };
const page = async () => { render(<AppProvider><CarStatusHistoryPage /></AppProvider>); await screen.findByLabelText("Car No"); };
const fill = async () => {
  await userEvent.type(screen.getByLabelText("Car No"), "1101");
  await userEvent.type(screen.getByLabelText("From"), "1999-06-30");
  await userEvent.type(screen.getByLabelText("To"), "2026-10-31");
};

describe("Car status history (old VHCSH1I / VHCSH2I)", () => {
  it("lists a car's status changes and shows the date the middleware used", async () => {
    const mw = fakeMiddleware({ ...base, "GET /api/v1/vehicle-queries/car-status-history": () => ({ body: { car: "1101", homeYard: "CY", fromDate: "2000-01-14",
      toDate: "2026-10-31", changes: [change], footer: null, message: null } }) });
    await page();
    await fill();
    await userEvent.click(screen.getByRole("button", { name: "List" }));
    const table = await screen.findByRole("table", { name: "Status changes" });
    expect(within(table).getByText("NO GO")).toBeInTheDocument();
    expect(within(table).getAllByText("10:15")).toHaveLength(2);
    expect(Object.fromEntries(mw.calls("/api/v1/vehicle-queries/car-status-history")[0].query)).toEqual({ car: "1101", from: "1999-06-30", to: "2026-10-31" });
    expect(screen.getByLabelText("From")).toHaveValue("2000-01-14");
  });

  it("shows the old screen's message", async () => {
    fakeMiddleware({ ...base, "GET /api/v1/vehicle-queries/car-status-history": () => ({ status: 400, body: { detail: "Car number is not found" } }) });
    await page();
    await fill();
    await userEvent.click(screen.getByRole("button", { name: "List" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Car number is not found");
  });

  it("prints on the chosen printer", async () => {
    const mw = fakeMiddleware({ ...base, "POST /api/v1/vehicle-queries/car-status-history/print": () => ({ body: { message: "Job is submitted" } }) });
    await page();
    await fill();
    expect(screen.getByRole("button", { name: "Print" })).toBeDisabled();
    await userEvent.selectOptions(await screen.findByLabelText("Printer"), "DUBLNHP1");
    await userEvent.click(screen.getByRole("button", { name: "Print" }));
    await waitFor(() => expect(mw.calls("/api/v1/vehicle-queries/car-status-history/print")).toHaveLength(1));
    expect(mw.calls("/api/v1/vehicle-queries/car-status-history/print")[0].body).toEqual({ car: "1101", fromDate: "1999-06-30", toDate: "2026-10-31", printer: "DUBLNHP1" });
  });
});
