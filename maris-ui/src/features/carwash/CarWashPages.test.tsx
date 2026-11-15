import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { CarWashOverduePage, CarWashesPage } from "./CarWashPages";

describe("Car washes in a date range (old VHCWR1I)", () => {
  const page = async () => { render(<AppProvider><CarWashesPage /></AppProvider>); await screen.findByLabelText("Yard"); };

  it("lists the washes for a yard and date range", async () => {
    const mw = fakeMiddleware({ "GET /api/v1/car-wash/washes": () => ({ body: { yard: "CY", from: "2026-11-01", to: "2026-11-12", count: 1,
      maxRowsReached: true, note: "(First 500 rows displayed)", message: null, washes: [{ car: "1101", date: "11-02-2026", time: "07:15" }] } }) });
    await page();
    await userEvent.type(screen.getByLabelText("Yard"), "cy");
    await userEvent.type(screen.getByLabelText("From"), "2026-11-01");
    await userEvent.type(screen.getByLabelText("To"), "2026-11-12");
    await userEvent.click(screen.getByRole("button", { name: "List" }));
    const table = await screen.findByRole("table", { name: "Washes" });
    expect(within(table).getByText("1101")).toBeInTheDocument();
    expect(screen.getByText("(First 500 rows displayed)")).toBeInTheDocument();
    expect(Object.fromEntries(mw.calls("/api/v1/car-wash/washes")[0].query)).toEqual({ yard: "CY", from: "2026-11-01", to: "2026-11-12" });
  });

  it("shows the old message for a bad input", async () => {
    fakeMiddleware({ "GET /api/v1/car-wash/washes": () => ({ status: 400, body: { detail: "Invalid Yard." } }) });
    await page();
    await userEvent.click(screen.getByRole("button", { name: "List" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Invalid Yard.");
  });

  it("keeps the old yard size (3) and Clear empties everything", async () => {
    fakeMiddleware({ "GET /api/v1/car-wash/washes": () => ({ body: { count: 0, washes: [], message: "No cars found.", maxRowsReached: false } }) });
    await page();
    expect(screen.getByLabelText("Yard")).toHaveAttribute("maxLength", "3");
    await userEvent.type(screen.getByLabelText("Yard"), "HY");
    await userEvent.click(screen.getByRole("button", { name: "List" }));
    expect(await screen.findByText("No cars found.")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Clear" }));
    expect(screen.getByLabelText("Yard")).toHaveValue("");
    expect(screen.queryByText("No cars found.")).not.toBeInTheDocument();
  });
});

describe("Car wash over due (old VHCWR2I)", () => {
  const page = async () => { render(<AppProvider><CarWashOverduePage /></AppProvider>); await screen.findByLabelText("Home yard"); };

  it("lists the cars whose wash is over due", async () => {
    const mw = fakeMiddleware({ "GET /api/v1/car-wash/overdue": () => ({ body: { homeYard: "HY", count: 1, maxRowsReached: false, note: null, message: null,
      cars: [{ car: "1101", lastWashDate: "10-01-2026", lastWashTime: "06:00", location: "HY03", daysOverdue: 12, totalWashes: 40 }] } }) });
    await page();
    await userEvent.type(screen.getByLabelText("Home yard"), "hy");
    await userEvent.click(screen.getByRole("button", { name: "List" }));
    const table = await screen.findByRole("table", { name: "Cars over due" });
    expect(within(table).getByText("HY03")).toBeInTheDocument();
    expect(within(table).getByText("12")).toBeInTheDocument();
    expect(Object.fromEntries(mw.calls("/api/v1/car-wash/overdue")[0].query)).toEqual({ homeYard: "HY" });
  });

  it("shows the middleware's error", async () => {
    fakeMiddleware({ "GET /api/v1/car-wash/overdue": () => ({ status: 400, body: { detail: "Invalid Yard." } }) });
    await page();
    await userEvent.click(screen.getByRole("button", { name: "List" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Invalid Yard.");
  });
});
