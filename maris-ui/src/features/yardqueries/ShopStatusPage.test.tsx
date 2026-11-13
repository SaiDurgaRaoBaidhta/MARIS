import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { CLEAN_PAGES } from "@/features/screens/cleanPages";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { ShopStatusPage } from "./ShopStatusPage";

const car = (no: string, extra: object = {}) => ({ track: "S-1", position: 1, car: no, leftEnd: "<", rightEnd: "", status: "NO GO", incident: false,
  dateIn: "11-10-2026", timeIn: "08:15", reason: "BRAKES", elapsedDays: 2, elapsedHours: 6, ...extra });
const REPORT = {
  yard: "HY", shop: "HAYWARD SHOP", runTime: "14:30", averageDays: 11, averageHours: 4, totalCars: 2, grandTotal: 3,
  cars: [car("1001", { incident: true }), car("1003", { track: "T-24", elapsedDays: undefined, elapsedHours: undefined })],
  outOfYard: { transferTrack: "T-24 T-23A", averageDays: 4, averageHours: 5, totalCars: 1,
    cars: [{ origin: "YARD", track: "SHOP", position: 2, car: "2001", leftEnd: "", rightEnd: ">", status: "GO", incident: false,
      dateIn: "11-09-2026", timeIn: "07:00", reason: "TEST", elapsedDays: 4, elapsedHours: 5, elapsedMinutes: 6 }] },
};
const page = (yard?: string) => render(<AppProvider><ShopStatusPage yard={yard} /></AppProvider>);

describe("shop status report (old VHYT25I)", () => {
  it("asks for the yard's report and shows the shop's cars, their time in the shop and the average", async () => {
    const mw = fakeMiddleware({ "GET /api/v1/yard-queries/shop-status/HY": () => ({ body: REPORT }) });
    page("HY");
    const shop = await screen.findByRole("table", { name: "Cars in HAYWARD SHOP" });
    expect(mw.calls("/api/v1/yard-queries/shop-status/HY")).toHaveLength(1);
    const rows = within(shop).getAllByRole("row");
    expect(within(rows[1]).getByText("<1001")).toBeInTheDocument();
    expect(within(rows[1]).getByText("NO GO TI")).toBeInTheDocument();                 // TI: train incident
    expect(within(rows[1]).getByText("2 days 6 hrs")).toBeInTheDocument();
    expect(within(rows[2]).queryByText(/days/)).not.toBeInTheDocument();                // T-24 is not timed
    expect(screen.getByText("Average time in the shop: 11 days 4 hrs")).toBeInTheDocument();
    expect(screen.getByText("Run at 14:30 · 2 cars · 3 in all")).toBeInTheDocument();
  });

  it("shows the cars out of the yard with where they came from", async () => {
    fakeMiddleware({ "GET /api/v1/yard-queries/shop-status/HY": () => ({ body: REPORT }) });
    page("HY");
    const out = await screen.findByRole("table", { name: "Cars out of the yard (T-24 T-23A)" });
    expect(within(out).getByText("YARD")).toBeInTheDocument();
    expect(within(out).getByText("2001>")).toBeInTheDocument();
    expect(within(out).getByText("4 days 5 hrs 6 min")).toBeInTheDocument();
  });

  it("asks for the yard typed when none is given", async () => {
    const mw = fakeMiddleware({ "GET /api/v1/yard-queries/shop-status/CY": () => ({ body: { ...REPORT, yard: "CY", shop: "CONCORD SHOP" } }) });
    page();
    await userEvent.type(await screen.findByLabelText("Yard"), "cy");
    await userEvent.click(screen.getByRole("button", { name: "Show" }));
    await screen.findByRole("table", { name: "Cars in CONCORD SHOP" });
    await waitFor(() => expect(mw.calls("/api/v1/yard-queries/shop-status/CY")).toHaveLength(1));
  });

  it("shows the middleware's message", async () => {
    fakeMiddleware({ "GET /api/v1/yard-queries/shop-status/HY": () => ({ status: 422, body: { code: "BUSINESS_ERROR", detail: "SHOP Is Not Found" } }) });
    page("HY");
    expect(await screen.findByRole("alert")).toHaveTextContent("SHOP Is Not Found");
  });

  it("is routed from VHYT25I", () => {
    expect(CLEAN_PAGES.VHYT25I).toBeTypeOf("function");
  });
});
