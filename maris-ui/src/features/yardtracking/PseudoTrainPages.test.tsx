import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useEffect, useState, type ReactNode } from "react";
import { describe, expect, it } from "vitest";
import { AppProvider, useApp } from "@/shared/lib/app-state";
import { fakeMiddleware } from "@/test/fetch";
import { ConsistVerifyPage, HaywardYardMapPage, PseudoTrainPage } from "./PseudoTrainPages";

const base = { "GET /api/v1/auth/csrf": () => ({ status: 204 }) };

/** opens the page the way another screen does (egl:CODE?pass values), then shows it; the front page's key is visible */
function Opened({ k, children }: { k: string; children: ReactNode }) {
  const app = useApp();
  const [ready, setReady] = useState(false);
  useEffect(() => { app.open({ kind: "screen", key: k, label: k }); setReady(true); }, []); // eslint-disable-line react-hooks/exhaustive-deps
  const top = app.stack[app.stack.length - 1];
  return <>{ready && children}<output aria-label="front">{top.kind === "screen" ? top.key : top.kind}</output></>;
}

const consist = (n: number) => ({ consist: String(200 + n), track: "T-1" + n, yard: "HY", dateLastUpdated: "2026-11-10", timeLastUpdated: "10.00.00",
  cars: [{ position: 1, car: "<11" + String(n).padStart(2, "0"), homeYard: "HY", colour: "BLACK" }, { position: 2, car: "12" + String(n).padStart(2, "0") + ">", homeYard: "RY", colour: "RED" }] });

describe("pseudo status train (old VHYT09I + VHYT01I)", () => {
  it("lists a passed yard at once, pages by the old page size and prints on the chosen printer", async () => {
    const mw = fakeMiddleware({ ...base,
      "GET /api/v1/yard-tracking/pseudo-trains/options": () => ({ body: { yards: ["HY", "RY", "ALL"], yard: "RY", message: "Please select the Yard you want to display.", pageSize: 4 } }),
      "GET /api/v1/yard-tracking/pseudo-trains": () => ({ body: { yard: "RY", consists: [1, 2, 3, 4, 5].map(consist), message: "RY  Yard is selected.", printable: true } }),
      "GET /api/v1/yard-tracking/pseudo-trains/printers": () => ({ body: [{ description: "YARD PRINTER", vtam: "PRT2" }] }),
      "POST /api/v1/yard-tracking/pseudo-trains/print": () => ({ body: { submitted: true } }) });
    render(<AppProvider><Opened k="egl:VHYT09I?yrdTrkgYrd=RY"><PseudoTrainPage /></Opened></AppProvider>);
    await waitFor(() => expect(screen.getAllByRole("status").some(e => /^RY\s+Yard is selected\.$/.test(e.textContent ?? ""))).toBe(true));
    expect(mw.calls("/api/v1/yard-tracking/pseudo-trains")[0].query.get("yard")).toBe("RY");
    expect(screen.getAllByRole("table")).toHaveLength(4);
    const first = screen.getByRole("table", { name: "Pseudo train 201" });
    expect(within(first).getByText("1201>")).toHaveStyle({ color: "rgb(255, 0, 0)" });
    await userEvent.click(screen.getByRole("button", { name: "Nxt Page" }));
    expect(screen.getAllByRole("table")).toHaveLength(1);
    await userEvent.click(screen.getByRole("button", { name: "Print" }));
    const hard = await screen.findByRole("button", { name: "Hard Copy" });
    expect(hard).toBeDisabled();
    await userEvent.selectOptions(screen.getByLabelText("Printer"), "PRT2");
    await userEvent.click(hard);
    await waitFor(() => expect(mw.calls("/api/v1/yard-tracking/pseudo-trains/print")).toHaveLength(1));
    expect(mw.calls("/api/v1/yard-tracking/pseudo-trains/print")[0].body).toEqual({ yard: "RY", printer: "PRT2" });
  });

  it("starts with the yard list and the old prompt when no yard is passed", async () => {
    const mw = fakeMiddleware({ ...base,
      "GET /api/v1/yard-tracking/pseudo-trains/options": () => ({ body: { yards: ["HY", "ALL"], yard: "HY", message: "Please select the Yard you want to display.", pageSize: 4 } }) });
    render(<AppProvider><PseudoTrainPage /></AppProvider>);
    expect(await screen.findByText("Please select the Yard you want to display.")).toBeInTheDocument();
    expect(mw.calls("/api/v1/yard-tracking/pseudo-trains")).toHaveLength(0);
    expect(screen.getByRole("button", { name: "Print" })).toBeDisabled();
  });
});

describe("Hayward yard map (old Vhyt07i)", () => {
  it("shows cars on their tracks and opens a track's detail with its track and yard", async () => {
    fakeMiddleware({ ...base, "GET /api/v1/yard-tracking/hayward-yard-map": () => ({ body: {
      screen: "Vhyt07i", yard: "HY", pseudoTrainYard: "HY", printYard: "HY",
      tracks: [{ track: "T-10", spots: [1, 2] }], spots: [{ caption: "T-10", track: "T-10" }, { caption: "PSEUDO TRACK", track: "T-95" }],
      cars: [{ track: "T-10", position: 1, car: "1234", consist: "", orientation: "<", label: "$<1234", status: "GO", moveReason: "", homeYard: "HY", colour: "GREEN", background: "GREY90" }] } }) });
    render(<AppProvider><Opened k="egl:VHYT07I"><HaywardYardMapPage /></Opened></AppProvider>);
    expect(await screen.findByText("$<1234")).toHaveStyle({ color: "rgb(0, 128, 0)" });
    await userEvent.click(screen.getByRole("button", { name: "PSEUDO TRACK" }));
    expect(screen.getByLabelText("front")).toHaveTextContent("egl:VHYT08I?yrdSubLoc=T-95&yrdTrkgYrd=HY");
  });
});

describe("verify pseudo train (old VHYT12I)", () => {
  const state = (extra: object = {}) => ({ cars: [{ position: 1, car: "<1101", colour: "BLACK", consist: "", consistColour: "RED", location: "", homeYard: "HY" }],
    consist: "", message: "", verifyEnabled: true, resetEnabled: false, question: null, notice: null, closed: false, ...extra });

  it("sends the cars picked, asks the old question and verifies on Yes", async () => {
    let n = 0;
    const mw = fakeMiddleware({ ...base,
      "POST /api/v1/yard-tracking/consist-verify/open": () => ({ body: state() }),
      "POST /api/v1/yard-tracking/consist-verify/verify": () => ({ body: n++ === 0
        ? state({ question: { key: "override", title: "Consist number has already been identified", text: "Train 345 has already been identified: 3 cars.  Do you want to override the train number?" } })
        : state({ message: "Pseudo train is verified", verifyEnabled: false, resetEnabled: true }) }) });
    render(<AppProvider><Opened k="egl:VHYT12I?yrdTrkgYrd=HY&yrdSubLoc=T-10&fromPos=1&toPos=4"><ConsistVerifyPage /></Opened></AppProvider>);
    await screen.findByText("<1101");
    expect(mw.calls("/api/v1/yard-tracking/consist-verify/open")[0].body).toMatchObject({ yard: "HY", track: "T-10", fromPosition: 1, toPosition: 4 });
    await userEvent.type(screen.getByLabelText("Consist No"), "345");
    await userEvent.click(screen.getByRole("button", { name: "Verify" }));
    expect(await screen.findByRole("alertdialog")).toHaveTextContent("override the train number");
    await userEvent.click(screen.getByRole("button", { name: "Yes" }));
    expect(await screen.findByText("Pseudo train is verified")).toBeInTheDocument();
    const posts = mw.calls("/api/v1/yard-tracking/consist-verify/verify");
    expect(posts.map(p => (p.body as { answers: Record<string, string> }).answers)).toEqual([{}, { override: "yes" }]);
    expect(posts[1].body).toMatchObject({ consist: "345" });
    expect(screen.getByRole("button", { name: "Verify" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Reset" })).toBeEnabled();
  });
});
