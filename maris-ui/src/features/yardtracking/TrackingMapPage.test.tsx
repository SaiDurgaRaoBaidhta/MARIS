import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AppProvider, useApp } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { TrackingMapPage, type MapKind } from "./TrackingMapPage";

const car = (track: string, position: number, label: string, extra: object = {}) => ({
  track, position, car: label.replace(/\D/g, ""), consist: "532", orientation: "<", label, status: "GO", condition: "go",
  moveReason: "", homeYard: "HY", colour: "GREEN", background: "GREY80", ...extra,
});

const DUBLIN = {
  map: "dublin", screen: "VHYT40I", yard: "DPX", pseudoTrack: "T-95", shopReport: null,
  tracks: [{ track: "L1", spots: [1, 2, 3] }, { track: "L2", spots: [1, 2] }],
  cars: [car("L1", 1, "<1234"), car("L1", 2, "2345>", { condition: "usable", colour: "BLUE" }), car("L2", 2, "<3456", { condition: "not-usable" })],
};
const SHOP = {
  map: "hayward-shop", screen: "VHYT04I", yard: null, pseudoTrack: null, shopReport: { whichReport: "SHOPSTAT", yard: "HY" },
  tracks: [{ track: "1A", spots: [1] }, { track: "2N-1", spots: [] }],
  cars: [car("1A", 1, "$<2001", { background: undefined }), car("2N-1", 7, "2002<", { colour: "BROWN", background: undefined })],
};

/** shows the page the app would open on top */
function Opened() {
  const app = useApp();
  const top = app.stack[app.stack.length - 1];
  return <p data-testid="opened">{top && top.kind === "screen" ? top.key : ""}</p>;
}

const page = (map: string, kind: MapKind, code = "VHYT40I") =>
  render(<AppProvider><TrackingMapPage map={map} code={code} title="Map" kind={kind} /><Opened /></AppProvider>);

describe("yard tracking map page (old VHYT40I / 41I, VHYT50I / 51I, VHYT04I)", () => {
  it("draws every track's spots with the cars on theirs, coloured by home yard on the inquiry map", async () => {
    const mw = fakeMiddleware({ "GET /api/v1/yard-tracking/maps/dublin": () => ({ body: DUBLIN }) });
    page("dublin", "inquiry");
    const l1 = await screen.findByRole("group", { name: "Track L1" });
    expect(within(l1).getAllByRole("listitem")).toHaveLength(3);
    expect(within(l1).getByText("<1234")).toHaveStyle({ color: "rgb(0, 128, 0)", backgroundColor: "rgb(204, 204, 204)" });   // GREEN on GREY80
    expect(mw.calls("/api/v1/yard-tracking/maps/dublin")).toHaveLength(1);
    expect(screen.getByText(/3 cars in yard DPX/)).toBeInTheDocument();
  });

  it("opens a track's inventory and the pseudo status train with the yard, as the old map passes them", async () => {
    fakeMiddleware({ "GET /api/v1/yard-tracking/maps/dublin": () => ({ body: DUBLIN }) });
    page("dublin", "inquiry");
    await userEvent.click(await screen.findByRole("button", { name: "L2" }));
    expect(screen.getByTestId("opened")).toHaveTextContent("egl:VHYT08I?yrdSubLoc=L2&yrdTrkgYrd=DPX");
    await userEvent.click(screen.getByRole("button", { name: "Pseudo Status Train" }));
    expect(screen.getByTestId("opened")).toHaveTextContent("egl:VHYT09I?yrdTrkgYrd=DPX");
  });

  it("colours by condition on the update map and opens the car move after the old three clicks", async () => {
    fakeMiddleware({ "GET /api/v1/yard-tracking/maps/dublin-update": () => ({ body: { ...DUBLIN, map: "dublin-update", screen: "VHYT41I" } }) });
    page("dublin-update", "update", "VHYT41I");
    expect(await screen.findByText("2345>")).toHaveStyle({ color: "rgb(0, 0, 255)" });                // USABLE blue
    await userEvent.click(screen.getByRole("button", { name: "Car 1234" }));                         // from L1 spot 1
    await userEvent.click(screen.getByRole("button", { name: "Car 2345" }));                         // to spot 2 on the same track
    expect(screen.getByTestId("opened")).toHaveTextContent("");
    await userEvent.click(screen.getByRole("button", { name: "L2 spot 1" }));                        // to track L2
    expect(screen.getByTestId("opened")).toHaveTextContent(
      "egl:VHYT11I?yrdTrkgYrd=DPX&fromYrdSubLoc=L1&positionNoFrom=1&positionNoTo=2&toYrdSubLoc=L2&yrdSubLoc=L1");
  });

  it("opens the maintenance menu only when the access list allows it", async () => {
    let allowed = false;
    fakeMiddleware({
      "GET /api/v1/yard-tracking/maps/dublin-update": () => ({ body: { ...DUBLIN, map: "dublin-update" } }),
      "GET /api/v1/yard-tracking/maps/dublin-update/maintenance-access": () => ({ body: { allowed } }),
    });
    page("dublin-update", "update", "VHYT41I");
    const maint = await screen.findByRole("button", { name: "Maint" });
    await userEvent.click(maint);
    await waitFor(() => expect(maint).toBeDisabled());
    expect(screen.getByTestId("opened")).toHaveTextContent("");
    allowed = true;
  });

  it("the Hayward shop shows one-spot tracks, opens a car's status and prints the shop report", async () => {
    fakeMiddleware({ "GET /api/v1/yard-tracking/maps/hayward-shop": () => ({ body: SHOP }) });
    page("hayward-shop", "shop", "VHYT04I");
    const one = await screen.findByRole("group", { name: "Track 2N-1" });
    expect(within(one).getAllByRole("listitem")).toHaveLength(1);
    expect(within(one).getByText("2002<")).toHaveStyle({ color: "rgb(165, 42, 42)" });                 // BROWN
    await userEvent.click(screen.getByRole("button", { name: "Car 2001" }));
    expect(screen.getByTestId("opened")).toHaveTextContent("egl:VHSV06I?carNo=2001");
    await userEvent.click(screen.getByRole("button", { name: "Print" }));
    expect(screen.getByTestId("opened")).toHaveTextContent("egl:VHYT27I?whichReport=SHOPSTAT&yrdTrkgYrd=HY");
  });
});
