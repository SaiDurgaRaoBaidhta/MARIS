import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { CLEAN_PAGES } from "@/features/screens/cleanPages";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { HistoryPage } from "./HistoryPage";

const MOVES = { yard: "HY", from: "2026-11-01", to: "2026-11-10", fromCar: "0300", toCar: "2999", maxRowsReached: false, rows: [
  { car: "1001", track: "S-1", position: 3, status: "GO", orientation: "<", dateMoved: "11-05-2026", timeMoved: "10:20:00", previousTrack: "T-24", previousPosition: 2, reason: "BRAKES", reasonCode: "B01" },
  { car: "1001", track: "S-2", position: 1, status: "GO", orientation: "<", dateMoved: "11-06-2026", timeMoved: "11:00:00", previousTrack: "S-1", previousPosition: 3, reason: "TEST", reasonCode: "T01" },
] };
const CHANGES = { yard: "HY", from: "2026-11-01", to: "2026-11-10", maxRowsReached: true, rows: [
  { car: "1001", dateChanged: "11-05-2026", timeChanged: "10:20:00", track: "S-1", newStatus: "NO GO", previousStatus: "GO", reason: "BRAKES", reasonCode: "B01" },
] };

async function fill(yard: string, from: string, to: string) {
  await userEvent.type(await screen.findByLabelText("Yard"), yard);
  await userEvent.type(screen.getByLabelText("From date"), from);
  await userEvent.type(screen.getByLabelText("To date"), to);
}

describe("car location history (old VHYT29I)", () => {
  it("asks for the moves with the yard, dates and car range, and lists them; a car is named once", async () => {
    const mw = fakeMiddleware({ "GET /api/v1/yard-queries/car-history": () => ({ body: MOVES }) });
    render(<AppProvider><HistoryPage kind="cars" /></AppProvider>);
    await fill("hy", "2026-11-01", "2026-11-10");
    await userEvent.type(screen.getByLabelText("From car"), "1001");
    await userEvent.click(screen.getByRole("button", { name: "Show" }));
    const table = await screen.findByRole("table", { name: "Car location history" });
    expect(Object.fromEntries(mw.calls("/api/v1/yard-queries/car-history")[0].query))
      .toEqual({ yard: "HY", from: "2026-11-01", to: "2026-11-10", fromCar: "1001", toCar: "" });
    const rows = within(table).getAllByRole("row");
    expect(within(rows[1]).getByText("1001")).toBeInTheDocument();
    expect(within(rows[2]).queryByText("1001")).not.toBeInTheDocument();          // as the old report: the car once
    expect(within(rows[1]).getByText("T-24")).toBeInTheDocument();
  });

  it("shows the old message for a bad date range", async () => {
    fakeMiddleware({ "GET /api/v1/yard-queries/car-history": () => ({ status: 400, body: { code: "INVALID_INPUT", detail: "You Have Entered an Invalid Date Range" } }) });
    render(<AppProvider><HistoryPage kind="cars" /></AppProvider>);
    await fill("hy", "2026-11-10", "2026-11-01");
    await userEvent.click(screen.getByRole("button", { name: "Show" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("You Have Entered an Invalid Date Range");
  });
});

describe("yard vehicle status history (old VHYT33I)", () => {
  it("asks for the status changes and flags the maximum number of rows", async () => {
    const mw = fakeMiddleware({ "GET /api/v1/yard-queries/status-history": () => ({ body: CHANGES }) });
    render(<AppProvider><HistoryPage kind="status" /></AppProvider>);
    await fill("hy", "2026-11-01", "2026-11-10");
    expect(screen.queryByLabelText("From car")).not.toBeInTheDocument();              // no car range on this report
    await userEvent.click(screen.getByRole("button", { name: "Show" }));
    const table = await screen.findByRole("table", { name: "Vehicle status history" });
    expect(within(table).getByText("NO GO")).toBeInTheDocument();
    expect(screen.getByText(/most rows/)).toBeInTheDocument();
    await waitFor(() => expect(Object.fromEntries(mw.calls("/api/v1/yard-queries/status-history")[0].query)).toEqual({ yard: "HY", from: "2026-11-01", to: "2026-11-10" }));
  });

  it("are routed from VHYT29I and VHYT33I", () => {
    expect(CLEAN_PAGES.VHYT29I).toBeTypeOf("function");
    expect(CLEAN_PAGES.VHYT33I).toBeTypeOf("function");
  });
});
