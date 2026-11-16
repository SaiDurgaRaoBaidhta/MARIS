import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useEffect } from "react";
import { describe, expect, it } from "vitest";
import { AppProvider, useApp } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { CarMovePage, OrientationPage, YardPrintPage } from "./MovePages";

const position = (slot: number, car = "", extra: object = {}) => ({
  slot, position: car ? slot : 0, carShown: car ? `<${car}` : "", car, pseudoTrain: "", status: car ? "GO" : "", previousStatus: "",
  homeYard: car ? "CY" : "", color: "GREY80", statusColor: "", enabled: true, ...extra,
});
const TRACK = {
  yard: "CY", yardDescription: "CONCORD YARD", subLocation: "T-12", subLocationDescription: "TRACK TWELVE", toSubLocation: "T-13",
  pseudoCaption: "Pseudo Train", page: 1, previous: false, next: false,
  positions: Array.from({ length: 30 }, (_, i) => position(i + 1, i < 3 ? String(1101 + i) : "")),
  moveTo: [{ code: "T-13", description: "TRACK THIRTEEN" }, { code: "T-14", description: "TRACK FOURTEEN" }],
  canPseudoTrain: true, canMoveYard: false, message: "",
};

/** opens the page under test with a key, as another page would */
function Open({ code, query, children }: { code: string; query: string; children: React.ReactNode }) {
  const app = useApp();
  useEffect(() => { app.open({ kind: "screen", key: `egl:${code}?${query}`, label: code }); }, []); // eslint-disable-line react-hooks/exhaustive-deps
  const top = app.stack[app.stack.length - 1];
  return <>{children}<p data-testid="opened">{top && top.kind === "screen" ? top.key : ""}</p></>;
}

const page = (code: string, query: string, el: React.ReactNode) => render(<AppProvider><Open code={code} query={query}>{el}</Open></AppProvider>);

describe("car move page (old VHYT11I)", () => {
  it("shows the track opened from a map and sends the clicked positions with Move", async () => {
    const mw = fakeMiddleware({
      "GET /api/v1/yard-tracking/car-moves": () => ({ body: TRACK }),
      "POST /api/v1/yard-tracking/car-moves/actions": () => ({ body: { message: "SUCESSFUL MOVE", question: null, opens: null, with: {}, track: TRACK } }),
    });
    page("VHYT11I", "yrdTrkgYrd=CY&yrdSubLoc=T-12&toYrdSubLoc=T-13", <CarMovePage />);
    expect(await screen.findByText(/TRACK TWELVE/)).toBeInTheDocument();
    expect(mw.calls("/api/v1/yard-tracking/car-moves")[0].query.get("toSubLocation")).toBe("T-13");
    await userEvent.click(screen.getByRole("button", { name: "Position 1" }));
    await userEvent.click(screen.getByRole("button", { name: "Position 3" }));
    await userEvent.click(screen.getByRole("button", { name: "Move" }));
    expect(await screen.findByText("SUCESSFUL MOVE")).toBeInTheDocument();
    const body = mw.calls("/api/v1/yard-tracking/car-moves/actions")[0].body;
    expect(body).toMatchObject({ yard: "CY", subLocation: "T-12", toSubLocation: "T-13", slots: [1, 3], action: "move" });
  });

  it("opens the orientation window with the values the old window passes", async () => {
    fakeMiddleware({
      "GET /api/v1/yard-tracking/car-moves": () => ({ body: TRACK }),
      "POST /api/v1/yard-tracking/car-moves/actions": () => ({ body: { message: "", question: null, opens: "VHYT13I",
        with: { yrdTrkgYrd: "CY", yrdSubLoc: "T-12", carNo: "1103", posnNo: 3 }, track: TRACK } }),
    });
    page("VHYT11I", "yrdTrkgYrd=CY&yrdSubLoc=T-12", <CarMovePage />);
    await userEvent.click(await screen.findByRole("button", { name: "Position 3" }));
    await userEvent.click(screen.getByRole("button", { name: "Orientation" }));
    await waitFor(() => expect(screen.getByTestId("opened")).toHaveTextContent("egl:VHYT13I?yrdTrkgYrd=CY&yrdSubLoc=T-12&carNo=1103&posnNo=3"));
  });
});

describe("orientation page (old VHYT13I)", () => {
  it("shows the car's marks and changes to the other one", async () => {
    const view = { yard: "CY", subLocation: "T-12", cars: ["<1101 ", " 1102>", "<1103 "], car: "1103", orientations: ["<", ">"], current: "<",
      canChange: false, message: "Select new orientation for car." };
    const mw = fakeMiddleware({
      "GET /api/v1/yard-tracking/orientation": () => ({ body: view }),
      "POST /api/v1/yard-tracking/orientation": () => ({ body: { ...view, current: ">", message: "Orientation for car 1103 is updated." } }),
    });
    page("VHYT13I", "yrdTrkgYrd=CY&yrdSubLoc=T-12&carNo=1103", <OrientationPage />);
    expect(await screen.findByText("Select new orientation for car.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Change" })).toBeDisabled();
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Orientation" }), ">");
    await userEvent.click(screen.getByRole("button", { name: "Change" }));
    expect(await screen.findByText("Orientation for car 1103 is updated.")).toBeInTheDocument();
    expect(mw.calls("/api/v1/yard-tracking/orientation").at(-1)!.body).toMatchObject({ carNo: "1103", orientation: ">" });
  });
});

describe("yard print page (old VHYT24I)", () => {
  it("prints the yard's car list on the printer chosen", async () => {
    const mw = fakeMiddleware({
      "GET /api/v1/yard-tracking/yard-print": () => ({ body: { yard: "CY", printers: [{ location: "DUBLIN", name: "DUBLNHP1" }] } }),
      "POST /api/v1/yard-tracking/yard-print": () => ({ body: { yard: "CY" } }),
    });
    page("VHYT24I", "yrdTrkgYrd=CY", <YardPrintPage />);
    await userEvent.click(await screen.findByRole("radio"));
    await userEvent.click(screen.getByRole("button", { name: "Hard Copy" }));
    expect(await screen.findByText(/Sent to DUBLNHP1/)).toBeInTheDocument();
    expect(mw.calls("/api/v1/yard-tracking/yard-print").at(-1)!.body).toMatchObject({ yard: "CY", printer: "DUBLNHP1" });
  });
});
