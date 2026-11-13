import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { CLEAN_PAGES } from "@/features/screens/cleanPages";
import { AppProvider } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { SystemWidePage } from "./SystemWidePage";
import { YardMapPage } from "./YardMapPage";

const car = (track: string, position: number, label: string, extra: object = {}) => ({
  track, position, car: label.replace(/\D/g, ""), consist: "532", orientation: "<", label, status: "GO", condition: "go",
  moveReason: "", homeYard: "HY", colour: "GREEN", background: "GREY55", ...extra,
});
const BERRYESSA = {
  map: "berryessa", yard: "SVB",
  tracks: [{ track: "S-1", spots: [1, 2, 3] }, { track: "TM3", spots: [1, 2] }],
  cars: [car("S-1", 1, "<1234"), car("S-1", 2, "2345>", { condition: "usable", colour: "DARK ORANGE", background: "LIGHT YELLOW" }),
    car("TM3", 2, "$<3456", { condition: "not-usable" })],
};

const page = (map = "berryessa", colours: "home" | "condition" | "none" = "home") =>
  render(<AppProvider><YardMapPage map={map} code="VHIC01" title="Berryessa Station" colours={colours} /></AppProvider>);

describe("yard map page (old VHIC01 / VHIC02 and the other yard maps)", () => {
  it("asks for the map and draws every track's spots, with the cars on theirs", async () => {
    const mw = fakeMiddleware({ "GET /api/v1/yard-reports/maps/berryessa": () => ({ body: BERRYESSA }) });
    page();
    const s1 = await screen.findByRole("group", { name: "Track S-1" });
    expect(within(s1).getAllByRole("listitem")).toHaveLength(3);                  // spots 1-3, the third empty
    expect(within(s1).getByText("<1234")).toBeInTheDocument();
    expect(within(screen.getByRole("group", { name: "Track TM3" })).getByText("$<3456")).toBeInTheDocument();
    expect(mw.calls("/api/v1/yard-reports/maps/berryessa")).toHaveLength(1);
    expect(screen.getByText(/3 cars/)).toBeInTheDocument();
  });

  it("colours each car by its home yard on the inquiry map, as the old screen does", async () => {
    fakeMiddleware({ "GET /api/v1/yard-reports/maps/berryessa": () => ({ body: BERRYESSA }) });
    page();
    expect(await screen.findByText("2345>")).toHaveStyle({ color: "rgb(255, 140, 0)", backgroundColor: "rgb(255, 255, 224)" });  // DARK ORANGE on LIGHT YELLOW
    expect(screen.getByText("<1234")).toHaveStyle({ color: "rgb(0, 128, 0)", backgroundColor: "rgb(140, 140, 140)" });           // GREEN on GREY55
  });

  it("colours each car by its condition on the update map (GO black, USABLE blue, otherwise red)", async () => {
    fakeMiddleware({ "GET /api/v1/yard-reports/maps/berryessa": () => ({ body: BERRYESSA }) });
    page("berryessa", "condition");
    expect(await screen.findByText("<1234")).toHaveStyle({ color: "rgb(0, 0, 0)" });
    expect(screen.getByText("2345>")).toHaveStyle({ color: "rgb(0, 0, 255)" });
    expect(screen.getByText("$<3456")).toHaveStyle({ color: "rgb(255, 0, 0)" });
  });

  it("leaves cars uncoloured on a map whose old screen colours none (VHYT81I)", async () => {
    fakeMiddleware({ "GET /api/v1/yard-reports/maps/berryessa": () => ({ body: BERRYESSA }) });
    page("berryessa", "none");
    expect(getComputedStyle(await screen.findByText("2345>")).color).not.toBe("rgb(255, 140, 0)");
  });

  it("colours by home yard without a background when the map has none (VHYT61I)", async () => {
    const shop = { ...BERRYESSA, cars: [car("S-1", 1, "<1234", { colour: "RED", background: undefined })] };
    fakeMiddleware({ "GET /api/v1/yard-reports/maps/berryessa": () => ({ body: shop }) });
    page();
    expect(await screen.findByText("<1234")).toHaveStyle({ color: "rgb(255, 0, 0)" });
  });

  it("shows the middleware's message when MARIS refuses", async () => {
    fakeMiddleware({ "GET /api/v1/yard-reports/maps/berryessa": () => ({ status: 422, body: { code: "BUSINESS_ERROR", detail: "YARD Is Not Found" } }) });
    page();
    expect(await screen.findByRole("alert")).toHaveTextContent("YARD Is Not Found");
  });

  it("Refresh asks MARIS again", async () => {
    const mw = fakeMiddleware({ "GET /api/v1/yard-reports/maps/berryessa": () => ({ body: BERRYESSA }) });
    page();
    await screen.findByRole("group", { name: "Track S-1" });
    await userEvent.click(screen.getByRole("button", { name: "Refresh" }));
    await waitFor(() => expect(mw.calls("/api/v1/yard-reports/maps/berryessa")).toHaveLength(2));
  });
});

describe("system-wide map page (old VHSW01I)", () => {
  it("marks the places that have cars and lists them", async () => {
    fakeMiddleware({ "GET /api/v1/yard-reports/system-wide": () => ({ body: { yard: "SW", locations: [
      { location: "MACART", cars: [] }, { location: "BAYFAIR", cars: ["4001", "4002"] }] } }) });
    render(<AppProvider><SystemWidePage /></AppProvider>);
    const bayfair = await screen.findByRole("group", { name: "BAYFAIR" });
    expect(within(bayfair).getByText("4001")).toBeInTheDocument();
    expect(within(bayfair).getByText(/2 cars/)).toBeInTheDocument();
    expect(within(screen.getByRole("group", { name: "MACART" })).getByText(/No cars/)).toBeInTheDocument();
  });
});

describe("routing", () => {
  it("routes every converted yard report screen to its new page", () => {
    for (const code of ["VHIC01", "VHIC02", "VHYT80I", "VHYT81I", "VHYT105I", "VHYT106I", "VHY105I", "VHY106I", "VHYT70I", "VHYT71I", "VHYT90I", "VHYT91I", "VHYT100I", "VHYT101I", "VHYT61I", "VHSW01I"]) {
      expect(CLEAN_PAGES[code], code).toBeTypeOf("function");
    }
  });
});
