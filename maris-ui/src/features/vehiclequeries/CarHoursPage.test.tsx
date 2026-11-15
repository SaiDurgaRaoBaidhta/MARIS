import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { CarHoursPage } from "./CarHoursPage";

const base = { "GET /api/v1/vehicle-queries/printers": () => ({ body: [{ description: "DUBLIN TMZ", vtam: "DUBLNHP1" }] }), "GET /api/v1/auth/csrf": () => ({ status: 204 }) };
const page = async () => {
  render(<AppProvider><CarHoursPage /></AppProvider>);
  await userEvent.type(await screen.findByLabelText("Car No"), "1101");
  await userEvent.type(screen.getByLabelText("Requested date from"), "2026-10-01");
  await userEvent.type(screen.getByLabelText("Requested date to"), "2026-10-31");
};

describe("Car hours (old VHCHR1I)", () => {
  it("shows the car's total hours", async () => {
    const mw = fakeMiddleware({ ...base, "GET /api/v1/vehicle-queries/car-hours": () => ({ body: { car: "1101", fromDate: "2026-10-01", toDate: "2026-10-31",
      actualFrom: "10-01-2026", actualTo: "10-31-2026", hours: 321, minutes: 9 } }) });
    await page();
    await userEvent.click(screen.getByRole("button", { name: "List" }));
    expect(await screen.findByText("321:09")).toBeInTheDocument();
    expect(Object.fromEntries(mw.calls("/api/v1/vehicle-queries/car-hours")[0].query)).toEqual({ car: "1101", from: "2026-10-01", to: "2026-10-31" });
  });

  it("shows the old message when nothing is found", async () => {
    fakeMiddleware({ ...base, "GET /api/v1/vehicle-queries/car-hours": () => ({ body: { car: "1101", fromDate: "2026-10-01", toDate: "2026-10-31",
      actualFrom: "", actualTo: "", hours: 0, minutes: 0, message: "No records found." } }) });
    await page();
    await userEvent.click(screen.getByRole("button", { name: "List" }));
    expect(await screen.findByText("No records found.")).toBeInTheDocument();
    expect(screen.queryByText("Total hours")).toBeNull();
  });

  it("prints a hard copy", async () => {
    const mw = fakeMiddleware({ ...base, "POST /api/v1/vehicle-queries/car-hours/print": () => ({ body: { message: "Print complete" } }) });
    await page();
    await userEvent.selectOptions(screen.getByLabelText("Printer"), "DUBLNHP1");
    await userEvent.click(screen.getByRole("button", { name: "Hard Copy" }));
    await waitFor(() => expect(mw.calls("/api/v1/vehicle-queries/car-hours/print")).toHaveLength(1));
    expect(mw.calls("/api/v1/vehicle-queries/car-hours/print")[0].body).toEqual({ car: "1101", fromDate: "2026-10-01", toDate: "2026-10-31", printer: "DUBLNHP1" });
  });
});
