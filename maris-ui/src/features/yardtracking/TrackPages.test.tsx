import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useEffect, useState, type ReactNode } from "react";
import { describe, expect, it } from "vitest";
import { AppProvider, useApp } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { FixOrderPage, MoveReasonPage, TrackPage, TrackReportPage } from "./TrackPages";

const pos = (slot: number, car = "", status = "") => ({ slot, position: slot, carShown: car ? `<${car}` : "", car, pseudoTrain: car ? "201" : "", status,
  previousStatus: "", homeYard: car ? "CY" : "", usable: false, alert: status === "NO GO" });
const TRACK = { yard: "SW", yardDescription: "CONCORD YARD", subLocation: "T-12", subLocationDescription: "TRACK TWELVE", pseudoCaption: "Pseudo Train", page: 1,
  previous: false, next: false, positions: Array.from({ length: 30 }, (_, i) => pos(i + 1, i < 4 ? String(1101 + i) : "", i < 4 ? "NO GO" : "")),
  locations: [{ code: "T-13", description: "TRACK THIRTEEN" }], userMayChange: true, canMoveCar: true, canMoveYard: true, canFixOrder: true, message: "" };
const base = { "GET /api/v1/auth/csrf": () => ({ status: 204 }) };

/** a page opened from another one, with the old pass values in its key; shows the key of the page in front */
function Opened({ k, children }: { k: string; children: ReactNode }) {
  const app = useApp();
  const [ready, setReady] = useState(false);
  useEffect(() => { app.open({ kind: "screen", key: k, label: k }); setReady(true); }, []); // eslint-disable-line react-hooks/exhaustive-deps
  const top = app.stack[app.stack.length - 1];
  return <>{ready && children}<output aria-label="front">{top.kind === "screen" ? top.key : top.kind}</output></>;
}

describe("track window (old VHYT08I)", () => {
  it("shows a track's positions and moves the clicked cars to a location", async () => {
    const mw = fakeMiddleware({ ...base, "GET /api/v1/yard-tracking/track": () => ({ body: TRACK }),
      "POST /api/v1/yard-tracking/track/actions": () => ({ body: { message: "  Car(s) selected are moved ", with: {}, track: TRACK } }) });
    render(<AppProvider><TrackPage /></AppProvider>);
    await userEvent.type(await screen.findByLabelText("Yard"), "sw");
    await userEvent.type(screen.getByLabelText("Track"), "t-12");
    await userEvent.click(screen.getByRole("button", { name: "Display" }));
    expect(await screen.findByText("<1102")).toBeInTheDocument();
    expect(Object.fromEntries(mw.calls("/api/v1/yard-tracking/track")[0].query)).toEqual({ yard: "SW", subLocation: "T-12", page: "1" });
    await userEvent.click(screen.getByRole("button", { name: "Position 2" }));
    await userEvent.click(screen.getByRole("button", { name: "Position 4" }));
    await userEvent.selectOptions(screen.getByLabelText("Move Car to Location"), "T-13");
    await userEvent.click(screen.getByRole("button", { name: "Move Car" }));
    expect(await screen.findByText(/Car\(s\) selected are moved/)).toBeInTheDocument();
    expect(mw.calls("/api/v1/yard-tracking/track/actions")[0].body).toEqual({ yard: "SW", subLocation: "T-12", page: 1, slots: [2, 4], action: "move-car", toLocation: "T-13" });
  });

  it("Change Reason opens the reason window with the old pass values", async () => {
    fakeMiddleware({ ...base, "GET /api/v1/yard-tracking/track": () => ({ body: TRACK }),
      "POST /api/v1/yard-tracking/track/actions": () => ({ body: { message: "", opens: "VHYT06I", with: { yrdTrkgYrd: "SW", yrdSubLoc: "T-12", carNo: "1103", posnNo: 3 }, track: TRACK } }) });
    render(<AppProvider><Opened k="egl:VHYT08I?yrdTrkgYrd=SW&yrdSubLoc=T-12"><TrackPage /></Opened></AppProvider>);
    await userEvent.click(await screen.findByRole("button", { name: "Position 3" }));
    await userEvent.click(screen.getByRole("button", { name: "Change Reason" }));
    await waitFor(() => expect(screen.getByLabelText("front")).toHaveTextContent("egl:VHYT06I?yrdTrkgYrd=SW&yrdSubLoc=T-12&carNo=1103&posnNo=3"));
  });
});

describe("move reason window (old VHYT06I)", () => {
  it("shows the car and updates it to the chosen reason", async () => {
    const car = { car: "1103", position: 3, orientation: "<", status: "USABLE", yard: "SW", subLocation: "T-12", reasonCode: "M1", reasonDescription: "MOVED FOR PM" };
    const mw = fakeMiddleware({ ...base, "GET /api/v1/yard-tracking/move-reasons/car": () => ({ body: { reasons: [{ code: "M1", description: "MOVED FOR PM" },
      { code: "M2", description: "WAITING FOR PARTS" }], car, message: "" } }),
      "POST /api/v1/yard-tracking/move-reasons/change": () => ({ body: { message: "Reason code M1   for car 1103 has been changed to M2", updated: true, car: { ...car, reasonCode: "M2" } } }) });
    render(<AppProvider><Opened k="egl:VHYT06I?yrdTrkgYrd=SW&yrdSubLoc=T-12&carNo=1103&posnNo=3"><MoveReasonPage /></Opened></AppProvider>);
    expect(await screen.findByText("USABLE")).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "M2 WAITING FOR PARTS" })).toBeInTheDocument();
    expect(Object.fromEntries(mw.calls("/api/v1/yard-tracking/move-reasons/car")[0].query)).toEqual({ yard: "SW", subLocation: "T-12", car: "1103", position: "3" });
    await userEvent.selectOptions(screen.getByLabelText("New Reason Code"), "M2");
    await userEvent.click(screen.getByRole("button", { name: "Update" }));
    expect(await screen.findByText(/has been changed to M2/)).toBeInTheDocument();
    expect(mw.calls("/api/v1/yard-tracking/move-reasons/change")[0].body).toEqual({ yard: "SW", subLocation: "T-12", car: "1103", position: 3, reasonCode: "M2" });
  });
});

describe("fix order window (old VHYT14I)", () => {
  it("asks before un-making a pseudo train, and moves on Yes", async () => {
    const view = { cars: [1101, 1102, 1103, 1104].map((c, i) => ({ index: i + 1, car: String(c), position: i + 1, pseudoTrain: i < 2 ? "201" : "", partial: false })),
      selected: 1, message: "" };
    let n = 0;
    const mw = fakeMiddleware({ ...base, "GET /api/v1/yard-tracking/fix-order": () => ({ body: view }),
      "POST /api/v1/yard-tracking/fix-order": () => ({ body: n++ === 0 ? { message: "", question: "Car being moved is part of Complete Pseudo 201 ... Continue?", moved: false, track: view }
        : { message: "Car 1101 is  inserted  before  car  1104", moved: true, track: view } }) });
    render(<AppProvider><Opened k="egl:VHYT14I?yrdTrkgYrd=SW&yrdSubLoc=T-12&posnNo=1"><FixOrderPage /></Opened></AppProvider>);
    await userEvent.selectOptions(await screen.findByLabelText("Car"), "4");
    await userEvent.click(screen.getByRole("button", { name: "Fix Order" }));
    expect(await screen.findByRole("alertdialog")).toHaveTextContent("Continue?");
    await userEvent.click(screen.getByRole("button", { name: "Yes" }));
    expect(await screen.findByText(/inserted/)).toBeInTheDocument();
    const posts = mw.calls("/api/v1/yard-tracking/fix-order").filter(c => c.method === "POST");
    expect(posts.map(c => (c.body as { confirm: boolean }).confirm)).toEqual([false, true]);
    expect(posts[1].body).toMatchObject({ carIndex: 1, relativeIndex: 4, after: false, position: 1 });
  });
});

describe("report printer window (old VHYT02I)", () => {
  it("needs a printer, then prints the track's report there", async () => {
    const mw = fakeMiddleware({ ...base, "GET /api/v1/yard-tracking/track-report/options": () => ({ body: { printers: [{ description: "DUBLIN", vtam: "DUBLNHP1" }],
      yards: ["SW", "CY"], yard: "SW", subLocations: ["T-12", "ALL"] } }), "POST /api/v1/yard-tracking/track-report/print": () => ({ body: { message: "" } }) });
    render(<AppProvider><Opened k="egl:VHYT02I?yrdTrkgYrd=SW&yrdSubLoc=T-12"><TrackReportPage /></Opened></AppProvider>);
    const hard = await screen.findByRole("button", { name: "Hard Copy" });
    await screen.findByLabelText("Printer");
    expect(hard).toBeDisabled();
    await userEvent.selectOptions(screen.getByLabelText("Printer"), "DUBLNHP1");
    await userEvent.click(hard);
    await waitFor(() => expect(mw.calls("/api/v1/yard-tracking/track-report/print")).toHaveLength(1));
    expect(mw.calls("/api/v1/yard-tracking/track-report/print")[0].body).toEqual({ yard: "SW", subLocation: "T-12", printer: "DUBLNHP1" });
  });
});
