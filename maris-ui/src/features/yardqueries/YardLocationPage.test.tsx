import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { CLEAN_PAGES } from "@/features/screens/cleanPages";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { YardLocationPage } from "./YardLocationPage";

const table = (headings: string[], rows: string[][]) => ({ headings, rows });
export const REPORT = {
  title: "YARD LOCATION STATUS REPORT FOR:", yardName: "HAYWARD YARD", runDate: "11-15-2026", runTime: "14:30:00",
  totalCaption: "STORAGE TRACKS / YARD TOTAL", totalCars: "2", testTracksNote: "(Includes Test Tracks)",
  cars: table(["Sub Loc", "Pos", "Car"], [["T-1", "1", "<1101"], ["T-24", "2", "1102>"]]),
  reasons: table(["Summary By Out of Service Reason", "Total Cars"], [["BR  BRAKES", "2"]]),
  homeYards: table(["Out of Town Summary By Car Type", "CY"], [["A CARS", "1"]]),
  exchangeTitle: "SHOP / YARD EXCHANGE", exchange: table(["Origin", "Car"], [["YARD", "2001>"]]),
  homeYardsButton: "Hm Yrd Sum", reasonsButton: "Out Srv Sum",
};
const page = (yard?: string) => render(<AppProvider><YardLocationPage yard={yard} /></AppProvider>);

describe("yard location status report (old VHYT26I)", () => {
  it("asks for the yard's report and shows its cars with the old headings, the total and the exchange", async () => {
    const mw = fakeMiddleware({ "GET /api/v1/yard-queries/yard-location": () => ({ body: REPORT }) });
    page("HY");
    const cars = await screen.findByRole("table", { name: "Cars in HAYWARD YARD" });
    expect(mw.calls("/api/v1/yard-queries/yard-location")[0].query.get("yard")).toBe("HY");
    expect(within(cars).getByRole("columnheader", { name: "Sub Loc" })).toBeInTheDocument();
    expect(within(cars).getByText("<1101")).toBeInTheDocument();
    expect(screen.getByText("(Includes Test Tracks)")).toBeInTheDocument();
    expect(within(screen.getByRole("table", { name: "SHOP / YARD EXCHANGE" })).getByText("2001>")).toBeInTheDocument();
  });

  it("switches between the reason summary and the home yard summary with the old buttons", async () => {
    fakeMiddleware({ "GET /api/v1/yard-queries/yard-location": () => ({ body: REPORT }) });
    page("HY");
    expect(await screen.findByRole("table", { name: "Summary By Out of Service Reason" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Out Srv Sum" })).toBeDisabled();
    await userEvent.click(screen.getByRole("button", { name: "Hm Yrd Sum" }));
    expect(within(screen.getByRole("table", { name: "Out of Town Summary By Car Type" })).getByText("A CARS")).toBeInTheDocument();
  });

  it("shows MARIS's error", async () => {
    fakeMiddleware({ "GET /api/v1/yard-queries/yard-location": () => ({ status: 502, body: { detail: "MARIS is not answering" } }) });
    page("CY");
    expect(await screen.findByRole("alert")).toHaveTextContent("MARIS is not answering");
  });

  it("is routed from VHYT26I", () => {
    expect(CLEAN_PAGES.VHYT26I).toBeTypeOf("function");
  });
});
